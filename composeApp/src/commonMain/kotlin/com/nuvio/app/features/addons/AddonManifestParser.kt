package com.nuvio.app.features.addons

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal object AddonManifestParser {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    fun parse(
        manifestUrl: String,
        payload: String,
    ): AddonManifest {
        val root = json.parseToJsonElement(payload).jsonObject
        val defaultTypes = root.stringList("types")
        val defaultPrefixes = root.stringList("idPrefixes")

        return AddonManifest(
            id = root.requiredString("id"),
            name = root.requiredString("name"),
            description = root.requiredString("description"),
            version = root.requiredString("version"),
            resources = root.resources(defaultTypes, defaultPrefixes),
            types = defaultTypes,
            idPrefixes = defaultPrefixes,
            catalogs = root.catalogs(),
            behaviorHints = root.behaviorHints(),
            transportUrl = manifestUrl,
        )
    }

    private fun JsonObject.resources(
        defaultTypes: List<String>,
        defaultPrefixes: List<String>,
    ): List<AddonResource> =
        array("resources").map { resource ->
            when (resource) {
                is JsonPrimitive -> AddonResource(
                    name = resource.contentOrNull.orEmpty(),
                    types = defaultTypes,
                    idPrefixes = defaultPrefixes,
                )

                else -> {
                    val obj = resource.jsonObject
                    AddonResource(
                        name = obj.requiredString("name"),
                        types = obj.stringList("types").ifEmpty { defaultTypes },
                        idPrefixes = obj.stringList("idPrefixes").ifEmpty { defaultPrefixes },
                    )
                }
            }
        }.filter { it.name.isNotBlank() }

    private fun JsonObject.catalogs(): List<AddonCatalog> =
        array("catalogs").map { catalogElement ->
            val catalog = catalogElement.jsonObject
            AddonCatalog(
                type = catalog.requiredString("type"),
                id = catalog.requiredString("id"),
                name = catalog.optionalString("name").orEmpty().ifBlank { catalog.requiredString("id") },
                extra = catalog.array("extra").mapNotNull { extraElement ->
                    extraElement.jsonObject.optionalString("name")?.takeIf { it.isNotBlank() }?.let { name ->
                        AddonExtraProperty(
                            name = name,
                            isRequired = extraElement.jsonObject.boolean("isRequired"),
                        )
                    }
                },
            )
        }

    private fun JsonObject.behaviorHints(): AddonBehaviorHints {
        val hints = this["behaviorHints"]?.jsonObject ?: return AddonBehaviorHints()
        return AddonBehaviorHints(
            configurable = hints.boolean("configurable"),
            configurationRequired = hints.boolean("configurationRequired"),
            adult = hints.boolean("adult"),
            p2p = hints.boolean("p2p"),
        )
    }

    private fun JsonObject.requiredString(name: String): String =
        optionalString(name)?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Manifest missing \"$name\"")

    private fun JsonObject.optionalString(name: String): String? =
        this[name]?.jsonPrimitive?.contentOrNull

    private fun JsonObject.stringList(name: String): List<String> =
        array(name).mapNotNull { element ->
            element.jsonPrimitive.contentOrNull?.takeIf { it.isNotBlank() }
        }

    private fun JsonObject.array(name: String): JsonArray =
        (this[name] as? JsonArray) ?: JsonArray(emptyList())

    private fun JsonObject.boolean(name: String): Boolean =
        this[name]?.jsonPrimitive?.booleanOrNull == true
}
