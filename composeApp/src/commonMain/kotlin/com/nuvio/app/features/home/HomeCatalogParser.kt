package com.nuvio.app.features.home

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal object HomeCatalogParser {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    fun parseCatalog(payload: String): List<MetaPreview> {
        val root = json.parseToJsonElement(payload).jsonObject
        return root.array("metas")
            .mapNotNull { element ->
                val meta = element as? JsonObject ?: return@mapNotNull null
                val id = meta.string("id")
                val type = meta.string("type")
                val name = meta.string("name")

                if (id.isNullOrBlank() || type.isNullOrBlank() || name.isNullOrBlank()) {
                    return@mapNotNull null
                }

                MetaPreview(
                    id = id,
                    type = type,
                    name = name,
                    poster = meta.string("poster"),
                    posterShape = meta.string("posterShape").toPosterShape(),
                    description = meta.string("description"),
                    releaseInfo = meta.string("releaseInfo"),
                    imdbRating = meta.string("imdbRating"),
                    genres = meta.array("genres").mapNotNull { genre ->
                        genre.jsonPrimitive.contentOrNull?.takeIf { it.isNotBlank() }
                    },
                )
            }
    }

    private fun JsonObject.string(name: String): String? =
        this[name]?.jsonPrimitive?.contentOrNull

    private fun JsonObject.array(name: String): JsonArray =
        this[name] as? JsonArray ?: JsonArray(emptyList())

    private fun String?.toPosterShape(): PosterShape =
        when (this?.lowercase()) {
            "square" -> PosterShape.Square
            "landscape" -> PosterShape.Landscape
            else -> PosterShape.Poster
        }
}
