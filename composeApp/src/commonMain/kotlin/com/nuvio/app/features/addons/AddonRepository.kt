package com.nuvio.app.features.addons

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

object AddonRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
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

    fun addAddon(rawUrl: String): AddAddonResult {
        val manifestUrl = try {
            normalizeManifestUrl(rawUrl)
        } catch (error: IllegalArgumentException) {
            return AddAddonResult.Error(error.message ?: "Enter a valid addon URL")
        }

        if (_uiState.value.addons.any { it.manifestUrl == manifestUrl }) {
            return AddAddonResult.Error("That addon is already installed.")
        }

        _uiState.update { current ->
            current.copy(
                addons = listOf(
                    ManagedAddon(
                        manifestUrl = manifestUrl,
                        isRefreshing = true,
                    ),
                ) + current.addons,
            )
        }
        persist()
        refreshAddon(manifestUrl)
        return AddAddonResult.Success
    }

    fun removeAddon(manifestUrl: String) {
        _uiState.update { current ->
            current.copy(
                addons = current.addons.filterNot { it.manifestUrl == manifestUrl },
            )
        }
        persist()
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
