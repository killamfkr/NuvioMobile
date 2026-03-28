package com.nuvio.app.features.settings

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Sync

internal fun LazyListScope.settingsRootContent(
    isTablet: Boolean,
    onPlaybackClick: () -> Unit,
    onAppearanceClick: () -> Unit,
    onContentDiscoveryClick: () -> Unit,
    onAccountClick: () -> Unit,
    onSyncOverviewClick: () -> Unit,
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
            SettingsNavigationRow(
                title = "Appearance",
                description = "Tune home presentation and visual preferences.",
                icon = Icons.Rounded.Palette,
                isTablet = isTablet,
                onClick = onAppearanceClick,
            )
            SettingsNavigationRow(
                title = "Content & Discovery",
                description = "Manage addons and discovery sources.",
                icon = Icons.Rounded.Extension,
                isTablet = isTablet,
                onClick = onContentDiscoveryClick,
            )
        }
    }
    item {
        SettingsSection(
            title = "ACCOUNT & SYNC",
            isTablet = isTablet,
        ) {
            SettingsNavigationRow(
                title = "Account",
                description = "Manage your account, sign out, or delete.",
                icon = Icons.Rounded.AccountCircle,
                isTablet = isTablet,
                onClick = onAccountClick,
            )
            SettingsNavigationRow(
                title = "Sync Overview",
                description = "View synced data counts per profile.",
                icon = Icons.Rounded.Sync,
                isTablet = isTablet,
                onClick = onSyncOverviewClick,
            )
        }
    }
}
