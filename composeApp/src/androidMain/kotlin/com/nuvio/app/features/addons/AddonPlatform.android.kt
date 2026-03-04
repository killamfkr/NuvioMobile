package com.nuvio.app.features.addons

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

actual object AddonStorage {
    private const val preferencesName = "nuvio_addons"
    private const val addonUrlsKey = "installed_manifest_urls"

    private var preferences: SharedPreferences? = null

    fun initialize(context: Context) {
        preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    }

    actual fun loadInstalledAddonUrls(): List<String> =
        preferences
            ?.getString(addonUrlsKey, null)
            .orEmpty()
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()

    actual fun saveInstalledAddonUrls(urls: List<String>) {
        preferences
            ?.edit()
            ?.putString(addonUrlsKey, urls.joinToString(separator = "\n"))
            ?.apply()
    }
}

actual suspend fun httpGetText(url: String): String =
    withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        connection.setRequestProperty("Accept", "application/json")

        try {
            val statusCode = connection.responseCode
            val stream = if (statusCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: connection.inputStream
            }
            val payload = stream.bufferedReader().use(BufferedReader::readText)
            if (statusCode !in 200..299) {
                error("Request failed with HTTP $statusCode")
            }
            payload
        } finally {
            connection.disconnect()
        }
    }
