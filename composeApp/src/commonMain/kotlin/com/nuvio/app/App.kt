package com.nuvio.app

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.request.crossfade
import com.nuvio.app.core.ui.NuvioTheme
import com.nuvio.app.features.addons.AddonsScreen
import com.nuvio.app.features.details.MetaDetailsRepository
import com.nuvio.app.features.details.MetaDetailsScreen
import com.nuvio.app.features.home.HomeScreen
import com.nuvio.app.features.home.MetaPreview
import kotlinx.serialization.Serializable

@Serializable
object TabsRoute

@Serializable
data class DetailRoute(val type: String, val id: String)

enum class AppScreenTab {
    Home,
    Addons,
}

@Composable
fun AppScreen(
    tab: AppScreenTab,
    modifier: Modifier = Modifier,
    onPosterClick: ((MetaPreview) -> Unit)? = null,
) {
    when (tab) {
        AppScreenTab.Home -> HomeScreen(
            modifier = modifier,
            onPosterClick = onPosterClick,
        )
        AppScreenTab.Addons -> AddonsScreen(modifier = modifier)
    }
}

@Composable
@Preview
fun App() {
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .crossfade(true)
            .build()
    }
    NuvioTheme {
        val navController = rememberNavController()
        val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
        var selectedTab by rememberSaveable { mutableStateOf(AppScreenTab.Addons) }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0),
            bottomBar = {
                if (currentRoute == TabsRoute::class.qualifiedName) {
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
            },
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = TabsRoute,
            ) {
                composable<TabsRoute> {
                    AppScreen(
                        tab = selectedTab,
                        modifier = Modifier.padding(innerPadding),
                        onPosterClick = { meta ->
                            navController.navigate(DetailRoute(type = meta.type, id = meta.id))
                        },
                    )
                }
                composable<DetailRoute> { backStackEntry ->
                    val route = backStackEntry.toRoute<DetailRoute>()
                    MetaDetailsScreen(
                        type = route.type,
                        id = route.id,
                        onBack = {
                            MetaDetailsRepository.clear()
                            navController.popBackStack()
                        },
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }
}
