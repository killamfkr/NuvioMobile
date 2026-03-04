package com.nuvio.app.features.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.NuvioIconActionButton
import com.nuvio.app.core.ui.NuvioScreen
import com.nuvio.app.core.ui.NuvioScreenHeader
import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.home.components.HomeCatalogRowSection
import com.nuvio.app.features.home.components.HomeEmptyStateCard

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) {
        AddonRepository.initialize()
    }

    val addonsUiState by AddonRepository.uiState.collectAsStateWithLifecycle()
    val homeUiState by HomeRepository.uiState.collectAsStateWithLifecycle()

    val catalogRefreshKey = remember(addonsUiState.addons) {
        addonsUiState.addons.mapNotNull { addon ->
            val manifest = addon.manifest ?: return@mapNotNull null
            buildString {
                append(manifest.transportUrl)
                append(':')
                append(manifest.catalogs.joinToString(separator = ",") { catalog ->
                    "${catalog.type}:${catalog.id}:${catalog.extra.count { it.isRequired }}"
                })
            }
        }
    }

    LaunchedEffect(catalogRefreshKey) {
        HomeRepository.refresh(addonsUiState.addons)
    }

    NuvioScreen(modifier = modifier) {
        item {
            NuvioScreenHeader(
                title = "Home",
            ) {
                NuvioIconActionButton(
                    icon = Icons.Rounded.Refresh,
                    contentDescription = "Refresh catalog rows",
                    onClick = { HomeRepository.refresh(addonsUiState.addons, force = true) },
                )
            }
        }

        when {
            addonsUiState.addons.none { it.manifest != null } -> {
                item {
                    HomeEmptyStateCard(
                        title = "No active addons",
                        message = "Install and validate at least one addon before loading catalog rows on Home.",
                    )
                }
            }

            homeUiState.isLoading && homeUiState.sections.isEmpty() -> {
                item {
                    HomeEmptyStateCard(
                        title = "Loading catalogs",
                        message = "Pulling feed-compatible catalogs from your installed addons.",
                    )
                }
            }

            homeUiState.sections.isEmpty() -> {
                item {
                    HomeEmptyStateCard(
                        title = "No home rows available",
                        message = homeUiState.errorMessage
                            ?: "Installed addons do not currently expose board-compatible catalogs without required extras.",
                    )
                }
            }

            else -> {
                items(
                    count = homeUiState.sections.size,
                    key = { index -> homeUiState.sections[index].key },
                ) { index ->
                    HomeCatalogRowSection(
                        section = homeUiState.sections[index],
                    )
                }
            }
        }
    }
}
