package com.nuvio.app.features.settings

import androidx.compose.foundation.lazy.LazyListScope
import com.nuvio.app.features.player.PlayerSettingsRepository

internal fun LazyListScope.playbackSettingsContent(
    isTablet: Boolean,
    showLoadingOverlay: Boolean,
) {
    item {
        SettingsSection(
            title = "PLAYER",
            isTablet = isTablet,
        ) {
            SettingsSwitchRow(
                title = "Show Loading Overlay",
                description = "Show the opening loading overlay while a stream starts playing.",
                checked = showLoadingOverlay,
                isTablet = isTablet,
                onCheckedChange = PlayerSettingsRepository::setShowLoadingOverlay,
            )
        }
    }
}
