package com.nuvio.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.request.crossfade
import com.nuvio.app.core.ui.NuvioTheme
import com.nuvio.app.features.addons.AddonsScreen
import com.nuvio.app.features.home.HomeScreen

enum class AppScreenTab {
    Home,
    Addons,
}

@Composable
fun AppScreen(
    tab: AppScreenTab,
    modifier: Modifier = Modifier,
) {
    when (tab) {
        AppScreenTab.Home -> HomeScreen(modifier = modifier)
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
        AppScreen(tab = AppScreenTab.Addons)
    }
}
