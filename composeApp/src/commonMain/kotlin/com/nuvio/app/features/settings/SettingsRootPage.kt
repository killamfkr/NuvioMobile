package com.nuvio.app.features.settings

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme

internal fun LazyListScope.settingsRootContent(
    isTablet: Boolean,
    onPlaybackClick: () -> Unit,
    onContentDiscoveryClick: () -> Unit,
) {
    item {
        SettingsSection(
            title = "GENERAL",
            isTablet = isTablet,
        ) {
            SettingsNavigationRow(
                title = "Playback",
                description = "Control player behavior and viewing defaults.",
                icon = Icons.Rounded.PlayArrow,
                isTablet = isTablet,
                onClick = onPlaybackClick,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingsNavigationRow(
                title = "Content & Discovery",
                description = "Manage addons and discovery sources.",
                icon = Icons.Rounded.Extension,
                isTablet = isTablet,
                onClick = onContentDiscoveryClick,
            )
        }
    }
}
