package com.nuvio.app.features.addons

import co.touchlab.kermit.Logger
import com.nuvio.app.core.network.SupabaseProvider
import com.nuvio.app.features.profiles.ProfileRepository
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
private data class AddonSyncItem(
    @SerialName("manifest_url") val manifestUrl: String,
    @SerialName("sort_order") val sortOrder: Int = 0,
    @SerialName("display_name") val displayName: String = "",
    @SerialName("is_enabled") val isEnabled: Boolean = true,
)

object AddonRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val log = Logger.withTag("AddonRepository")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val _uiState = MutableStateFlow(AddonsUiState())
    val uiState: StateFlow<AddonsUiState> = _uiState.asStateFlow()

    private var initialized = false

    fun initialize() {
        if (initialized) return
        initialized = true

        val storedUrls = AddonStorage.loadInstalledAddonUrls()
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

    suspend fun pullFromServer(profileId: Int) {
        runCatching {
            val params = buildJsonObject { put("p_profile_id", profileId) }
            val result = SupabaseProvider.client.postgrest.rpc("sync_pull_addons", params)
            val serverAddons = result.decodeList<AddonSyncItem>()
            val urls = serverAddons.sortedBy { it.sortOrder }.map { it.manifestUrl }
            if (urls.isNotEmpty()) {
                _uiState.value = AddonsUiState(
                    addons = urls.map { url ->
                        ManagedAddon(manifestUrl = url, isRefreshing = true)
                    },
                )
                persist()
                urls.forEach(::refreshAddon)
            }
            initialized = true
        }.onFailure { e ->
            log.e(e) { "Failed to pull addons from server" }
        }
    }

    suspend fun addAddon(rawUrl: String): AddAddonResult {
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
                    AddonSyncItem(
                        manifestUrl = addon.manifestUrl,
                        sortOrder = index,
                        displayName = addon.manifest?.name ?: "",
                        isEnabled = true,
                    )
                }
                val params = buildJsonObject {
                    put("p_profile_id", profileId)
                    put("p_addons", json.encodeToJsonElement(addons))
                }
                SupabaseProvider.client.postgrest.rpc("sync_push_addons", params)
            }.onFailure { e ->
                log.e(e) { "Failed to push addons to server" }
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
            _uiState.value.addons.map { it.manifestUrl },
        )
    }
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
