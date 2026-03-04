package com.nuvio.app.features.addons

internal expect object AddonStorage {
    fun loadInstalledAddonUrls(): List<String>
    fun saveInstalledAddonUrls(urls: List<String>)
}

internal expect suspend fun httpGetText(url: String): String
