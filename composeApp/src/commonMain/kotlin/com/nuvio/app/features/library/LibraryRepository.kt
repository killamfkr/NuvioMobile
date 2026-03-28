package com.nuvio.app.features.library

import co.touchlab.kermit.Logger
import com.nuvio.app.core.network.SupabaseProvider
import com.nuvio.app.features.profiles.ProfileRepository
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put

@Serializable
private data class StoredLibraryPayload(
    val items: List<LibraryItem> = emptyList(),
)

@Serializable
private data class LibrarySyncItem(
    @SerialName("content_id") val contentId: String,
    @SerialName("content_type") val contentType: String,
    val name: String = "",
    val poster: String? = null,
    @SerialName("poster_shape") val posterShape: String = "POSTER",
    val background: String? = null,
    val description: String? = null,
    @SerialName("release_info") val releaseInfo: String? = null,
    @SerialName("imdb_rating") val imdbRating: Float? = null,
    val genres: List<String> = emptyList(),
    @SerialName("added_at") val addedAt: Long = 0,
)

object LibraryRepository {
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val log = Logger.withTag("LibraryRepository")
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private var hasLoaded = false
    private var itemsById: MutableMap<String, LibraryItem> = mutableMapOf()

    fun ensureLoaded() {
        if (hasLoaded) return
        hasLoaded = true

        val payload = LibraryStorage.loadPayload().orEmpty().trim()
        if (payload.isNotEmpty()) {
            val items = runCatching {
                json.decodeFromString<StoredLibraryPayload>(payload).items
            }.getOrDefault(emptyList())
            itemsById = items.associateBy { it.id }.toMutableMap()
        }

        publish()
    }

    suspend fun pullFromServer(profileId: Int) {
        runCatching {
            val params = buildJsonObject {
                put("p_profile_id", profileId)
                put("p_limit", 500)
                put("p_offset", 0)
            }
            val result = SupabaseProvider.client.postgrest.rpc("sync_pull_library", params)
            val serverItems = result.decodeList<LibrarySyncItem>()
            itemsById = serverItems.map { it.toLibraryItem() }.associateBy { it.id }.toMutableMap()
            hasLoaded = true
            publish()
            persist()
        }.onFailure { e ->
            log.e(e) { "Failed to pull library from server" }
        }
    }

    fun toggleSaved(item: LibraryItem) {
        ensureLoaded()
        if (itemsById.containsKey(item.id)) {
            remove(item.id)
        } else {
            save(item)
        }
    }

    fun save(item: LibraryItem) {
        ensureLoaded()
        itemsById[item.id] = item.copy(savedAtEpochMs = LibraryClock.nowEpochMs())
        publish()
        persist()
        pushToServer()
    }

    fun remove(id: String) {
        ensureLoaded()
        if (itemsById.remove(id) != null) {
            publish()
            persist()
            pushToServer()
        }
    }

    fun isSaved(id: String): Boolean {
        ensureLoaded()
        return itemsById.containsKey(id)
    }

    fun savedItem(id: String): LibraryItem? {
        ensureLoaded()
        return itemsById[id]
    }

    private fun pushToServer() {
        syncScope.launch {
            runCatching {
                val profileId = ProfileRepository.activeProfileId
                val syncItems = itemsById.values.map { it.toSyncItem() }
                val params = buildJsonObject {
                    put("p_profile_id", profileId)
                    put("p_items", json.encodeToJsonElement(syncItems))
                }
                SupabaseProvider.client.postgrest.rpc("sync_push_library", params)
            }.onFailure { e ->
                log.e(e) { "Failed to push library to server" }
            }
        }
    }

    private fun publish() {
        val items = itemsById.values
            .sortedByDescending { it.savedAtEpochMs }
        val sections = items
            .groupBy { it.type }
            .map { (type, typeItems) ->
                LibrarySection(
                    type = type,
                    displayTitle = type.toLibraryDisplayTitle(),
                    items = typeItems.sortedByDescending { it.savedAtEpochMs },
                )
            }
            .sortedBy { it.displayTitle }

        _uiState.value = LibraryUiState(
            items = items,
            sections = sections,
            isLoaded = true,
        )
    }

    private fun persist() {
        LibraryStorage.savePayload(
            json.encodeToString(
                StoredLibraryPayload(
                    items = itemsById.values.sortedByDescending { it.savedAtEpochMs },
                ),
            ),
        )
    }
}

private fun LibrarySyncItem.toLibraryItem(): LibraryItem = LibraryItem(
    id = contentId,
    type = contentType,
    name = name,
    poster = poster,
    banner = background,
    description = description,
    releaseInfo = releaseInfo,
    imdbRating = imdbRating?.toString(),
    genres = genres,
    savedAtEpochMs = addedAt,
)

private fun LibraryItem.toSyncItem(): LibrarySyncItem = LibrarySyncItem(
    contentId = id,
    contentType = type,
    name = name,
    poster = poster,
    background = banner,
    description = description,
    releaseInfo = releaseInfo,
    imdbRating = imdbRating?.toFloatOrNull(),
    genres = genres,
    addedAt = savedAtEpochMs,
)

internal fun String.toLibraryDisplayTitle(): String {
    val normalized = trim()
    if (normalized.isBlank()) return "Other"

    return normalized
        .split('-', '_', ' ')
        .filter { it.isNotBlank() }
        .joinToString(" ") { token ->
            token.lowercase().replaceFirstChar { char -> char.uppercase() }
        }
        .ifBlank { "Other" }
}
