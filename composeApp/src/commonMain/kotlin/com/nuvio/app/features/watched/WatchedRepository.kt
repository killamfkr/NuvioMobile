package com.nuvio.app.features.watched

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
private data class StoredWatchedPayload(
    val items: List<WatchedItem> = emptyList(),
)

@Serializable
private data class WatchedSyncItem(
    @SerialName("content_id") val contentId: String,
    @SerialName("content_type") val contentType: String,
    val title: String = "",
    val season: Int? = null,
    val episode: Int? = null,
    @SerialName("watched_at") val watchedAt: Long = 0,
)

@Serializable
private data class WatchedDeleteKey(
    @SerialName("content_id") val contentId: String,
    val season: Int? = null,
    val episode: Int? = null,
)

object WatchedRepository {
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val log = Logger.withTag("WatchedRepository")
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _uiState = MutableStateFlow(WatchedUiState())
    val uiState: StateFlow<WatchedUiState> = _uiState.asStateFlow()

    private var hasLoaded = false
    private var itemsByKey: MutableMap<String, WatchedItem> = mutableMapOf()

    fun ensureLoaded() {
        if (hasLoaded) return
        hasLoaded = true

        val payload = WatchedStorage.loadPayload().orEmpty().trim()
        if (payload.isNotEmpty()) {
            val items = runCatching {
                json.decodeFromString<StoredWatchedPayload>(payload).items
            }.getOrDefault(emptyList())
            itemsByKey = items.associateBy { watchedItemKey(it.type, it.id) }.toMutableMap()
        }

        publish()
    }

    suspend fun pullFromServer(profileId: Int) {
        runCatching {
            val params = buildJsonObject { put("p_profile_id", profileId) }
            val result = SupabaseProvider.client.postgrest.rpc("sync_pull_watched_items", params)
            val serverItems = result.decodeList<WatchedSyncItem>()
            itemsByKey = serverItems.map { syncItem ->
                WatchedItem(
                    id = syncItem.contentId,
                    type = syncItem.contentType,
                    name = syncItem.title,
                    markedAtEpochMs = syncItem.watchedAt,
                )
            }.associateBy { watchedItemKey(it.type, it.id) }.toMutableMap()
            hasLoaded = true
            publish()
            persist()
        }.onFailure { e ->
            log.e(e) { "Failed to pull watched items from server" }
        }
    }

    fun toggleWatched(item: WatchedItem) {
        ensureLoaded()
        val key = watchedItemKey(item.type, item.id)
        if (itemsByKey.containsKey(key)) {
            unmarkWatched(item.id, item.type)
        } else {
            markWatched(item)
        }
    }

    fun markWatched(item: WatchedItem) {
        ensureLoaded()
        val key = watchedItemKey(item.type, item.id)
        val timestamped = item.copy(markedAtEpochMs = WatchedClock.nowEpochMs())
        itemsByKey[key] = timestamped
        publish()
        persist()
        pushMarkToServer(timestamped)
    }

    fun unmarkWatched(id: String, type: String) {
        ensureLoaded()
        val removed = itemsByKey.remove(watchedItemKey(type, id))
        if (removed != null) {
            publish()
            persist()
            pushDeleteToServer(id, type)
        }
    }

    fun isWatched(id: String, type: String): Boolean {
        ensureLoaded()
        return itemsByKey.containsKey(watchedItemKey(type, id))
    }

    private fun pushMarkToServer(item: WatchedItem) {
        syncScope.launch {
            runCatching {
                val profileId = ProfileRepository.activeProfileId
                val syncItem = WatchedSyncItem(
                    contentId = item.id,
                    contentType = item.type,
                    title = item.name,
                    watchedAt = item.markedAtEpochMs,
                )
                val params = buildJsonObject {
                    put("p_profile_id", profileId)
                    put("p_items", json.encodeToJsonElement(listOf(syncItem)))
                }
                SupabaseProvider.client.postgrest.rpc("sync_push_watched_items", params)
            }.onFailure { e ->
                log.e(e) { "Failed to push watched item" }
            }
        }
    }

    private fun pushDeleteToServer(contentId: String, contentType: String) {
        syncScope.launch {
            runCatching {
                val profileId = ProfileRepository.activeProfileId
                val keys = listOf(WatchedDeleteKey(contentId = contentId))
                val params = buildJsonObject {
                    put("p_profile_id", profileId)
                    put("p_keys", json.encodeToJsonElement(keys))
                }
                SupabaseProvider.client.postgrest.rpc("sync_delete_watched_items", params)
            }.onFailure { e ->
                log.e(e) { "Failed to push watched item delete" }
            }
        }
    }

    private fun publish() {
        val items = itemsByKey.values.sortedByDescending { it.markedAtEpochMs }
        _uiState.value = WatchedUiState(
            items = items,
            watchedKeys = items.mapTo(linkedSetOf()) { watchedItemKey(it.type, it.id) },
            isLoaded = true,
        )
    }

    private fun persist() {
        WatchedStorage.savePayload(
            json.encodeToString(
                StoredWatchedPayload(
                    items = itemsByKey.values.sortedByDescending { it.markedAtEpochMs },
                ),
            ),
        )
    }
}

