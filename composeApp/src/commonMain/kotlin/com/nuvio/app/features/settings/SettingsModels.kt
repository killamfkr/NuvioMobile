package com.nuvio.app.features.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.graphics.vector.ImageVector

internal enum class SettingsCategory(
    val label: String,
    val icon: ImageVector,
) {
    General("General", Icons.Rounded.Settings),
}

internal enum class SettingsPage(
    val title: String,
) {
    Root("Settings"),
    Playback("Playback"),
    Appearance("Appearance"),
    ContinueWatching("Continue Watching"),
    ContentDiscovery("Content & Discovery"),
    Addons("Addons"),
    Homescreen("Homescreen"),
    Account("Account"),
    SyncOverview("Sync Overview"),
}

internal fun SettingsPage.previousPage(): SettingsPage? =
    when (this) {
        SettingsPage.Root -> null
        SettingsPage.Playback -> SettingsPage.Root
        SettingsPage.Appearance -> SettingsPage.Root
        SettingsPage.ContinueWatching -> SettingsPage.Appearance
        SettingsPage.ContentDiscovery -> SettingsPage.Root
        SettingsPage.Addons -> SettingsPage.ContentDiscovery
        SettingsPage.Homescreen -> SettingsPage.ContentDiscovery
        SettingsPage.Account -> SettingsPage.Root
        SettingsPage.SyncOverview -> SettingsPage.Root
    }
