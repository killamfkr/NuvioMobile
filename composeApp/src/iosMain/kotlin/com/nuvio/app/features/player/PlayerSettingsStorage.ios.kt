package com.nuvio.app.features.player

import platform.Foundation.NSUserDefaults

actual object PlayerSettingsStorage {
    private const val showLoadingOverlayKey = "show_loading_overlay"

    actual fun loadShowLoadingOverlay(): Boolean? {
        val defaults = NSUserDefaults.standardUserDefaults
        return if (defaults.objectForKey(showLoadingOverlayKey) != null) {
            defaults.boolForKey(showLoadingOverlayKey)
        } else {
            null
        }
    }

    actual fun saveShowLoadingOverlay(enabled: Boolean) {
        NSUserDefaults.standardUserDefaults.setBool(enabled, forKey = showLoadingOverlayKey)
    }
}
