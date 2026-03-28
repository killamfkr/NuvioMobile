package com.nuvio.app.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.touchlab.kermit.Logger
import com.nuvio.app.core.network.SupabaseProvider
import com.nuvio.app.core.ui.NuvioSurfaceCard
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
private data class SyncOverviewItem(
    @SerialName("profile_name") val profileName: String = "",
    @SerialName("profile_index") val profileIndex: Int = 0,
    @SerialName("addon_count") val addonCount: Int = 0,
    @SerialName("library_count") val libraryCount: Int = 0,
    @SerialName("watch_progress_count") val watchProgressCount: Int = 0,
    @SerialName("watched_count") val watchedCount: Int = 0,
)

internal fun LazyListScope.syncOverviewContent(
    isTablet: Boolean,
) {
    item {
        SyncOverviewCards(isTablet = isTablet)
    }
}

@Composable
private fun SyncOverviewCards(isTablet: Boolean) {
    val log = remember { Logger.withTag("SyncOverview") }
    var overviewItems by remember { mutableStateOf<List<SyncOverviewItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        runCatching {
            val result = SupabaseProvider.client.postgrest.rpc("get_sync_overview")
            overviewItems = result.decodeList<SyncOverviewItem>()
        }.onFailure { e ->
            log.e(e) { "Failed to fetch sync overview" }
        }
        isLoading = false
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (isLoading) {
            NuvioSurfaceCard {
                Text(
                    text = "Loading sync data...",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else if (overviewItems.isEmpty()) {
            NuvioSurfaceCard {
                Text(
                    text = "No sync data available. Sign in with an account to enable cloud sync.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            overviewItems.sortedBy { it.profileIndex }.forEach { item ->
                NuvioSurfaceCard {
                    Text(
                        text = item.profileName.ifBlank { "Profile ${item.profileIndex}" },
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    SyncStatRow("Library Items", item.libraryCount)
                    Spacer(modifier = Modifier.height(8.dp))
                    SyncStatRow("Watch Progress", item.watchProgressCount)
                    Spacer(modifier = Modifier.height(8.dp))
                    SyncStatRow("Watched Items", item.watchedCount)
                    Spacer(modifier = Modifier.height(8.dp))
                    SyncStatRow("Addons", item.addonCount)
                }
            }
        }
    }
}

@Composable
private fun SyncStatRow(label: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
    }
}
