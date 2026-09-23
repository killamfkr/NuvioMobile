package com.nuvio.app.features.details

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MetaDetailsLruCacheTest {
    @Test
    fun evictsLeastRecentlyUsedEntryWhenOverCapacity() {
        val cache = MetaDetailsLruCache(maxEntries = 2)
        val first = cachedEntry("first")
        val second = cachedEntry("second")
        val third = cachedEntry("third")

        cache.put("series:1", first)
        cache.put("series:2", second)
        cache.get("series:1")

        cache.put("series:3", third)

        assertNull(cache.peek("series:2"))
        assertEquals("first", cache.get("series:1")?.baseMeta?.name)
        assertEquals("third", cache.get("series:3")?.baseMeta?.name)
    }

    @Test
    fun getPromotesEntryToMostRecentlyUsed() {
        val cache = MetaDetailsLruCache(maxEntries = 2)
        cache.put("series:1", cachedEntry("one"))
        cache.put("series:2", cachedEntry("two"))
        cache.get("series:1")
        cache.put("series:3", cachedEntry("three"))

        assertNull(cache.peek("series:2"))
        assertEquals("one", cache.get("series:1")?.baseMeta.name)
    }

    private fun cachedEntry(name: String): CachedMetaEntry =
        CachedMetaEntry(
            baseMeta = MetaDetails(
                id = name,
                type = "series",
                name = name,
            ),
        )
}
