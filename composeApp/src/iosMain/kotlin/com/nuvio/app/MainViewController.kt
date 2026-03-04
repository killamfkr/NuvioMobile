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
    view.backgroundColor = UIColor.blackColor
}

fun HomeViewController() = nuvioViewController(tab = AppScreenTab.Home)

fun AddonsViewController() = nuvioViewController(tab = AppScreenTab.Addons)
