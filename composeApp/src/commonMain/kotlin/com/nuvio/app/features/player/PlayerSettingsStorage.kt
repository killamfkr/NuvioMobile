package com.nuvio.app.features.player

internal expect object PlayerSettingsStorage {
    fun loadShowLoadingOverlay(): Boolean?
    fun saveShowLoadingOverlay(enabled: Boolean)
}
