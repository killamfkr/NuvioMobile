package com.nuvio.app.features.home

import kotlin.test.Test
import kotlin.test.assertEquals

class HomeCatalogParserTest {

    @Test
    fun `parse catalog response de-duplicates repeated metas but preserves raw count`() {
        val result = HomeCatalogParser.parseCatalogResponse(
            """
            {
              "metas": [
                { "id": "mal:62516", "type": "series", "name": "A" },
                { "id": "mal:62516", "type": "series", "name": "A duplicate" },
                { "id": "mal:1", "type": "movie", "name": "B" }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(3, result.rawItemCount)
        assertEquals(
            listOf("series:mal:62516", "movie:mal:1"),
            result.items.map { it.stableKey() },
        )
        assertEquals("A", result.items.first().name)
    }
}
