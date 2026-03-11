package com.nuvio.app.features.player

import android.content.Context
import android.content.SharedPreferences

actual object PlayerSettingsStorage {
    private const val preferencesName = "nuvio_player_settings"
    private const val showLoadingOverlayKey = "show_loading_overlay"

    private var preferences: SharedPreferences? = null

    fun initialize(context: Context) {
        preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    }

    actual fun loadShowLoadingOverlay(): Boolean? =
        preferences?.let { sharedPreferences ->
            if (sharedPreferences.contains(showLoadingOverlayKey)) {
                sharedPreferences.getBoolean(showLoadingOverlayKey, true)
            } else {
                null
            }
        }

    actual fun saveShowLoadingOverlay(enabled: Boolean) {
        preferences
            ?.edit()
            ?.putBoolean(showLoadingOverlayKey, enabled)
            ?.apply()
    }
}
