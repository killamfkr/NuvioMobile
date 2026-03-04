package com.nuvio.app

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIColor
import com.nuvio.app.core.ui.NuvioTheme

private fun nuvioViewController(
    tab: AppScreenTab,
) = ComposeUIViewController {
    NuvioTheme {
        AppScreen(tab = tab)
    }
}.apply {
    view.backgroundColor = UIColor(red = 0.008, green = 0.016, blue = 0.016, alpha = 1.0)
}

fun HomeViewController() = nuvioViewController(tab = AppScreenTab.Home)

fun AddonsViewController() = nuvioViewController(tab = AppScreenTab.Addons)
