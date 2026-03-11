package com.nuvio.app.features.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlayerSettingsUiState(
    val showLoadingOverlay: Boolean = true,
)

object PlayerSettingsRepository {
    private val _uiState = MutableStateFlow(PlayerSettingsUiState())
    val uiState: StateFlow<PlayerSettingsUiState> = _uiState.asStateFlow()

    private var hasLoaded = false
    private var showLoadingOverlay = true

    fun ensureLoaded() {
        if (hasLoaded) return
        hasLoaded = true
        showLoadingOverlay = PlayerSettingsStorage.loadShowLoadingOverlay() ?: true
        publish()
    }

    fun setShowLoadingOverlay(enabled: Boolean) {
        ensureLoaded()
        if (showLoadingOverlay == enabled) return
        showLoadingOverlay = enabled
        publish()
        PlayerSettingsStorage.saveShowLoadingOverlay(enabled)
    }

    private fun publish() {
        _uiState.value = PlayerSettingsUiState(
            showLoadingOverlay = showLoadingOverlay,
        )
    }
}
