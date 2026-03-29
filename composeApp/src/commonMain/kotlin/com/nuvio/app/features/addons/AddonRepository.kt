package com.nuvio.app.features.addons

import co.touchlab.kermit.Logger
import com.nuvio.app.core.network.SupabaseProvider
import com.nuvio.app.features.profiles.ProfileRepository
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put

@Serializable
private data class AddonRow(
    val url: String,
    val name: String? = null,
    val enabled: Boolean = true,
    @SerialName("sort_order") val sortOrder: Int = 0,
)

@Serializable
private data class AddonPushItem(
    val url: String,
    val name: String = "",
    val enabled: Boolean = true,
    @SerialName("sort_order") val sortOrder: Int = 0,
)

object AddonRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val log = Logger.withTag("AddonRepository")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val _uiState = MutableStateFlow(AddonsUiState())
    val uiState: StateFlow<AddonsUiState> = _uiState.asStateFlow()

    private var initialized = false
    private var pulledFromServer = false
    private var currentProfileId: Int = 1

    fun initialize() {
        if (initialized) return
        initialized = true
        currentProfileId = ProfileRepository.activeProfileId
        log.d { "initialize() — loading local addons for profile $currentProfileId" }

        val storedUrls = AddonStorage.loadInstalledAddonUrls(currentProfileId)
        log.d { "initialize() — local addon count: ${storedUrls.size}" }
        if (storedUrls.isEmpty()) return

        _uiState.value = AddonsUiState(
            addons = storedUrls.map { manifestUrl ->
                ManagedAddon(
                    manifestUrl = manifestUrl,
                    isRefreshing = true,
                )
            },
        )

        storedUrls.forEach(::refreshAddon)
    }

    fun onProfileChanged(profileId: Int) {
        if (profileId == currentProfileId && initialized) return
        currentProfileId = profileId
        initialized = false
        pulledFromServer = false
        _uiState.value = AddonsUiState()
    }

    suspend fun pullFromServer(profileId: Int) {
        currentProfileId = profileId
        log.i { "pullFromServer() — profileId=$profileId, initialized=$initialized, pulledFromServer=$pulledFromServer" }
        runCatching {
            val rows = SupabaseProvider.client.postgrest
                .from("addons")
                .select {
                    filter { eq("profile_id", profileId) }
                    order("sort_order", Order.ASCENDING)
                }
                .decodeList<AddonRow>()

            val urls = rows.map { ensureManifestSuffix(it.url) }
            log.i { "pullFromServer() — server returned ${rows.size} addons" }
            urls.forEachIndexed { i, u -> log.d { "  server[$i]: $u" } }

            if (urls.isEmpty() && !pulledFromServer) {
                val localUrls = AddonStorage.loadInstalledAddonUrls(profileId)
                log.i { "pullFromServer() — server empty, local has ${localUrls.size} addons" }
                if (localUrls.isNotEmpty()) {
                    log.i { "pullFromServer() — migrating local addons to server for profile $profileId" }
                    initialize()
                    pulledFromServer = true
                    val addons = localUrls.mapIndexed { index, addonUrl ->
                        AddonPushItem(
                            url = addonUrl,
                            name = _uiState.value.addons
                                .find { it.manifestUrl == addonUrl }?.manifest?.name ?: "",
                            enabled = true,
                            sortOrder = index,
                        )
                    }
                    val params = buildJsonObject {
                        put("p_profile_id", profileId)
                        put("p_addons", json.encodeToJsonElement(addons))
                    }
                    SupabaseProvider.client.postgrest.rpc("sync_push_addons", params)
                    log.i { "pullFromServer() — migration push done (${addons.size} addons)" }
                    return
                }
            }

            _uiState.value = AddonsUiState(
                addons = urls.map { url ->
                    ManagedAddon(manifestUrl = url, isRefreshing = true)
                },
            )
            persist()
            urls.forEach(::refreshAddon)
            pulledFromServer = true
            initialized = true
            log.i { "pullFromServer() — applied ${urls.size} addons to state" }
        }.onFailure { e ->
            log.e(e) { "pullFromServer() — FAILED" }
        }
    }

    suspend fun awaitManifestsLoaded() {
        if (_uiState.value.addons.isEmpty()) return
        uiState.first { state ->
            state.addons.isEmpty() || state.addons.any { it.manifest != null }
        }
    }

    suspend fun addAddon(rawUrl: String): AddAddonResult {
        log.i { "addAddon() — rawUrl=$rawUrl" }
        val manifestUrl = try {
            normalizeManifestUrl(rawUrl)
        } catch (error: IllegalArgumentException) {
            return AddAddonResult.Error(error.message ?: "Enter a valid addon URL")
        }

        if (_uiState.value.addons.any { it.manifestUrl == manifestUrl }) {
            return AddAddonResult.Error("That addon is already installed.")
        }

        val manifest = try {
            withContext(Dispatchers.Default) {
                val payload = httpGetText(manifestUrl)
                AddonManifestParser.parse(
                    manifestUrl = manifestUrl,
                    payload = payload,
                )
            }
        } catch (error: Throwable) {
            return AddAddonResult.Error(error.message ?: "Unable to load manifest")
        }

        _uiState.update { current ->
            current.copy(
                addons = current.addons + ManagedAddon(
                    manifestUrl = manifestUrl,
                    manifest = manifest,
                    isRefreshing = false,
                    errorMessage = null,
                ),
            )
        }
        persist()
        pushToServer()
        return AddAddonResult.Success(manifest)
    }

    fun removeAddon(manifestUrl: String) {
        log.i { "removeAddon() — $manifestUrl" }
        _uiState.update { current ->
            current.copy(
                addons = current.addons.filterNot { it.manifestUrl == manifestUrl },
            )
        }
        persist()
        pushToServer()
    }

    fun refreshAll() {
        _uiState.value.addons.forEach { addon ->
            refreshAddon(addon.manifestUrl)
        }
    }

    fun refreshAddon(manifestUrl: String) {
        markRefreshing(manifestUrl)
        scope.launch {
            val result = runCatching {
                val payload = httpGetText(manifestUrl)
                AddonManifestParser.parse(
                    manifestUrl = manifestUrl,
                    payload = payload,
                )
            }

            _uiState.update { current ->
                current.copy(
                    addons = current.addons.map { addon ->
                        if (addon.manifestUrl != manifestUrl) {
                            addon
                        } else {
                            result.fold(
                                onSuccess = { manifest ->
                                    addon.copy(
                                        manifest = manifest,
                                        isRefreshing = false,
                                        errorMessage = null,
                                    )
                                },
                                onFailure = { error ->
                                    addon.copy(
                                        isRefreshing = false,
                                        errorMessage = error.message ?: "Unable to load manifest",
                                    )
                                },
                            )
                        }
                    },
                )
            }
        }
    }

    private fun pushToServer() {
        scope.launch {
            runCatching {
                val profileId = ProfileRepository.activeProfileId
                val addons = _uiState.value.addons.mapIndexed { index, addon ->
                    AddonPushItem(
                        url = addon.manifestUrl,
                        name = addon.manifest?.name ?: "",
                        enabled = true,
                        sortOrder = index,
                    )
                }
                log.d { "pushToServer() — profileId=$profileId, pushing ${addons.size} addons" }
                val params = buildJsonObject {
                    put("p_profile_id", profileId)
                    put("p_addons", json.encodeToJsonElement(addons))
                }
                SupabaseProvider.client.postgrest.rpc("sync_push_addons", params)
                log.d { "pushToServer() — success" }
            }.onFailure { e ->
                log.e(e) { "pushToServer() — FAILED" }
            }
        }
    }

    private fun markRefreshing(manifestUrl: String) {
        _uiState.update { current ->
            current.copy(
                addons = current.addons.map { addon ->
                    if (addon.manifestUrl == manifestUrl) {
                        addon.copy(
                            isRefreshing = true,
                            errorMessage = null,
                        )
                    } else {
                        addon
                    }
                },
            )
        }
    }

    private fun persist() {
        AddonStorage.saveInstalledAddonUrls(
            currentProfileId,
            _uiState.value.addons.map { it.manifestUrl },
        )
    }
}

private fun ensureManifestSuffix(url: String): String {
    val path = url.substringBefore("?").trimEnd('/')
    val query = url.substringAfter("?", "")
    val withSuffix = if (path.endsWith("/manifest.json")) path else "$path/manifest.json"
    return if (query.isEmpty()) withSuffix else "$withSuffix?$query"
}

private fun normalizeManifestUrl(rawUrl: String): String {
    val trimmed = rawUrl.trim()
    require(trimmed.isNotEmpty()) { "Enter an addon URL." }

    val normalizedScheme = when {
        trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
        trimmed.startsWith("stremio://") -> "https://${trimmed.removePrefix("stremio://")}"
        else -> "https://$trimmed"
    }

    val withoutFragment = normalizedScheme.substringBefore("#")
    val query = withoutFragment.substringAfter("?", "")
    val path = withoutFragment.substringBefore("?").trimEnd('/')
    val manifestPath = if (path.endsWith("/manifest.json")) {
        path
    } else {
        "$path/manifest.json"
    }

    return if (query.isEmpty()) manifestPath else "$manifestPath?$query"
}
