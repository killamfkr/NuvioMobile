package com.nuvio.app.features.home

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.NuvioScreen
import com.nuvio.app.core.ui.NuvioScreenHeader
import com.nuvio.app.core.ui.NuvioSectionLabel
import com.nuvio.app.core.ui.NuvioSurfaceCard

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
) {
    NuvioScreen(modifier = modifier) {
        item {
            NuvioScreenHeader(title = "Home")
        }
        item {
            NuvioSectionLabel(text = "OVERVIEW")
        }
        item {
            NuvioSurfaceCard {
                Text(
                    text = "Home screen content comes next.",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Shared cards, spacing and typography are now aligned with the Addons screen so the next feature can reuse the same shell.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
