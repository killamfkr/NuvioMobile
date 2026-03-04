package com.nuvio.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.nuvio.app.core.ui.NuvioTheme
import com.nuvio.app.features.addons.AddonStorage

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        AddonStorage.initialize(applicationContext)

        setContent {
            AndroidAppRoot()
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    AndroidAppRoot()
}

@Composable
private fun AndroidAppRoot() {
    NuvioTheme {
        var selectedTab by rememberSaveable { mutableStateOf(AppScreenTab.Addons) }

        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            AppScreen(
                tab = selectedTab,
                modifier = Modifier.weight(1f),
            )
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                NavigationBarItem(
                    selected = selectedTab == AppScreenTab.Home,
                    onClick = { selectedTab = AppScreenTab.Home },
                    icon = { Icon(Icons.Rounded.Home, contentDescription = null) },
                    label = { Text("Home") },
                )
                NavigationBarItem(
                    selected = selectedTab == AppScreenTab.Addons,
                    onClick = { selectedTab = AppScreenTab.Addons },
                    icon = { Icon(Icons.Rounded.Extension, contentDescription = null) },
                    label = { Text("Addons") },
                )
            }
        }
    }
}
