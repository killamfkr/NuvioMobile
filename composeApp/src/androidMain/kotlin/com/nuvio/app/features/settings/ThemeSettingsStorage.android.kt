package com.nuvio.app.features.settings

import android.content.Context
import android.content.SharedPreferences

actual object ThemeSettingsStorage {
    private const val preferencesName = "nuvio_theme_settings"
    private const val selectedThemeKey = "selected_theme"
    private const val amoledEnabledKey = "amoled_enabled"

    private var preferences: SharedPreferences? = null

    fun initialize(context: Context) {
        preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    }

    actual fun loadSelectedTheme(): String? =
        preferences?.getString(selectedThemeKey, null)

    actual fun saveSelectedTheme(themeName: String) {
        preferences
            ?.edit()
            ?.putString(selectedThemeKey, themeName)
            ?.apply()
    }

    actual fun loadAmoledEnabled(): Boolean? =
        preferences?.let { prefs ->
            if (prefs.contains(amoledEnabledKey)) prefs.getBoolean(amoledEnabledKey, false) else null
        }

    actual fun saveAmoledEnabled(enabled: Boolean) {
        preferences
            ?.edit()
            ?.putBoolean(amoledEnabledKey, enabled)
            ?.apply()
    }
}
