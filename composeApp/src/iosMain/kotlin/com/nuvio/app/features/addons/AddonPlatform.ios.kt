package com.nuvio.app.features.addons

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.NSUserDefaults
import platform.Foundation.dataWithContentsOfURL
import platform.posix.memcpy

actual object AddonStorage {
    private const val addonUrlsKey = "installed_manifest_urls"

    actual fun loadInstalledAddonUrls(): List<String> =
        NSUserDefaults.standardUserDefaults
            .stringForKey(addonUrlsKey)
            .orEmpty()
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()

    actual fun saveInstalledAddonUrls(urls: List<String>) {
        NSUserDefaults.standardUserDefaults.setObject(
            urls.joinToString(separator = "\n"),
            forKey = addonUrlsKey,
        )
    }
}

actual suspend fun httpGetText(url: String): String =
    withContext(Dispatchers.Default) {
        val nsUrl = NSURL(string = url)

        val data = NSData.dataWithContentsOfURL(nsUrl)
            ?: throw IllegalStateException("Request failed")
        val payload = data.toByteArray().decodeToString()

        if (payload.isBlank()) {
            throw IllegalStateException("Empty response body")
        }

        payload
    }

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray =
    ByteArray(length.toInt()).apply {
        if (isEmpty()) return@apply
        usePinned { pinned ->
            memcpy(pinned.addressOf(0), bytes, length)
        }
    }
