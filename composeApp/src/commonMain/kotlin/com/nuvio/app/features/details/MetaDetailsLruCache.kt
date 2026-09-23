package com.nuvio.app.features.details

internal data class CachedMetaEntry(
    val baseMeta: MetaDetails,
    val metaScreenMeta: MetaDetails? = null,
    val metaScreenSettingsFingerprint: String? = null,
)

internal class MetaDetailsLruCache(
    private val maxEntries: Int = MAX_CACHED_META_ENTRIES,
) {
    private val entries = linkedMapOf<String, CachedMetaEntry>()

    fun get(requestKey: String): CachedMetaEntry? {
        val entry = entries.remove(requestKey) ?: return null
        entries[requestKey] = entry
        return entry
    }

    fun peek(requestKey: String): CachedMetaEntry? = entries[requestKey]

    fun put(requestKey: String, entry: CachedMetaEntry) {
        entries.remove(requestKey)
        entries[requestKey] = entry
        trimToMaxEntries()
    }

    fun clear() {
        entries.clear()
    }

    private fun trimToMaxEntries() {
        while (entries.size > maxEntries) {
            val eldestKey = entries.keys.first()
            entries.remove(eldestKey)
        }
    }

    companion object {
        const val MAX_CACHED_META_ENTRIES = 32
    }
}
