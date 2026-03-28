package com.nuvio.app.features.watchprogress

import co.touchlab.kermit.Logger
import com.nuvio.app.core.network.SupabaseProvider
import com.nuvio.app.features.player.PlayerPlaybackSnapshot
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put

@Serializable
private data class WatchProgressSyncEntry(
    @SerialName("content_id") val contentId: String,
    @SerialName("content_type") val contentType: String,
    @SerialName("video_id") val videoId: String,
    val season: Int? = null,
    val episode: Int? = null,
    val position: Long = 0,
    val duration: Long = 0,
    @SerialName("last_watched") val lastWatched: Long = 0,
    @SerialName("progress_key") val progressKey: String = "",
)

object WatchProgressRepository {
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val log = Logger.withTag("WatchProgressRepository")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _uiState = MutableStateFlow(WatchProgressUiState())
    val uiState: StateFlow<WatchProgressUiState> = _uiState.asStateFlow()

    private var hasLoaded = false
    private var entriesByVideoId: MutableMap<String, WatchProgressEntry> = mutableMapOf()

    fun ensureLoaded() {
        if (hasLoaded) return
        hasLoaded = true

        val payload = WatchProgressStorage.loadPayload().orEmpty().trim()
        if (payload.isEmpty()) return

        entriesByVideoId = WatchProgressCodec.decodeEntries(payload)
            .associateBy { it.videoId }
            .toMutableMap()
        publish()
    }

    suspend fun pullFromServer(profileId: Int) {
        runCatching {
            val params = buildJsonObject { put("p_profile_id", profileId) }
            val result = SupabaseProvider.client.postgrest.rpc("sync_pull_watch_progress", params)
            val serverEntries = result.decodeList<WatchProgressSyncEntry>()
            serverEntries.forEach { entry ->
                val videoId = entry.videoId
                entriesByVideoId[videoId] = WatchProgressEntry(
                    contentType = entry.contentType,
                    parentMetaId = entry.contentId,
                    parentMetaType = entry.contentType,
                    videoId = videoId,
                    title = "",
                    seasonNumber = entry.season,
                    episodeNumber = entry.episode,
                    lastPositionMs = entry.position,
                    durationMs = entry.duration,
                    lastUpdatedEpochMs = entry.lastWatched,
                    isCompleted = entry.duration > 0 && entry.position >= entry.duration,
                )
            }
            hasLoaded = true
            publish()
            persist()
        }.onFailure { e ->
            log.e(e) { "Failed to pull watch progress from server" }
        }
    }

    fun upsertPlaybackProgress(
        session: WatchProgressPlaybackSession,
        snapshot: PlayerPlaybackSnapshot,
    ) {
        ensureLoaded()
        upsert(session = session, snapshot = snapshot, persist = true)
    }

    fun flushPlaybackProgress(
        session: WatchProgressPlaybackSession,
        snapshot: PlayerPlaybackSnapshot,
    ) {
        ensureLoaded()
        upsert(session = session, snapshot = snapshot, persist = true)
    }

    fun clearProgress(videoId: String) {
        ensureLoaded()
        val entry = entriesByVideoId[videoId]
        if (entriesByVideoId.remove(videoId) != null) {
            publish()
            persist()
            entry?.let { pushDeleteToServer(it) }
        }
    }

    fun progressForVideo(videoId: String): WatchProgressEntry? {
        ensureLoaded()
        return entriesByVideoId[videoId]
    }

    fun resumeEntryForSeries(metaId: String): WatchProgressEntry? {
        ensureLoaded()
        return entriesByVideoId.values.toList().resumeEntryForSeries(metaId)
    }

    fun continueWatching(): List<WatchProgressEntry> {
        ensureLoaded()
        return entriesByVideoId.values.toList().continueWatchingEntries()
    }

    private fun upsert(
        session: WatchProgressPlaybackSession,
        snapshot: PlayerPlaybackSnapshot,
        persist: Boolean,
    ) {
        val positionMs = snapshot.positionMs.coerceAtLeast(0L)
        val durationMs = snapshot.durationMs.coerceAtLeast(0L)
        val isCompleted = isWatchProgressComplete(
            positionMs = positionMs,
            durationMs = durationMs,
            isEnded = snapshot.isEnded,
        )
        if (!isCompleted && !shouldStoreWatchProgress(positionMs = positionMs, durationMs = durationMs)) {
            return
        }

        val entry = WatchProgressEntry(
            contentType = session.contentType,
            parentMetaId = session.parentMetaId,
            parentMetaType = session.parentMetaType,
            videoId = session.videoId,
            title = session.title,
            logo = session.logo,
            poster = session.poster,
            background = session.background,
            seasonNumber = session.seasonNumber,
            episodeNumber = session.episodeNumber,
            episodeTitle = session.episodeTitle,
            episodeThumbnail = session.episodeThumbnail,
            lastPositionMs = if (isCompleted && durationMs > 0L) durationMs else positionMs,
            durationMs = durationMs,
            lastUpdatedEpochMs = WatchProgressClock.nowEpochMs(),
            providerName = session.providerName,
            providerAddonId = session.providerAddonId,
            lastStreamTitle = session.lastStreamTitle,
            lastStreamSubtitle = session.lastStreamSubtitle,
            lastSourceUrl = session.lastSourceUrl,
            isCompleted = isCompleted,
        )

        entriesByVideoId[session.videoId] = entry
        publish()
        if (persist) persist()
        pushScrobbleToServer(entry)
    }

    private fun pushScrobbleToServer(entry: WatchProgressEntry) {
        syncScope.launch {
            runCatching {
                val profileId = ProfileRepository.activeProfileId
                val syncEntry = WatchProgressSyncEntry(
                    contentId = entry.parentMetaId,
                    contentType = entry.contentType,
                    videoId = entry.videoId,
                    season = entry.seasonNumber,
                    episode = entry.episodeNumber,
                    position = entry.lastPositionMs,
                    duration = entry.durationMs,
                    lastWatched = entry.lastUpdatedEpochMs,
                )
                val params = buildJsonObject {
                    put("p_profile_id", profileId)
                    put("p_entries", json.encodeToJsonElement(listOf(syncEntry)))
                }
                SupabaseProvider.client.postgrest.rpc("sync_push_watch_progress", params)
            }.onFailure { e ->
                log.e(e) { "Failed to push watch progress scrobble" }
            }
        }
    }

    private fun pushDeleteToServer(entry: WatchProgressEntry) {
        syncScope.launch {
            runCatching {
                val profileId = ProfileRepository.activeProfileId
                val progressKey = if (entry.seasonNumber != null && entry.episodeNumber != null) {
                    "${entry.parentMetaId}_s${entry.seasonNumber}e${entry.episodeNumber}"
                } else {
                    entry.parentMetaId
                }
                val params = buildJsonObject {
                    put("p_progress_key", progressKey)
                    put("p_profile_id", profileId)
                }
                SupabaseProvider.client.postgrest.rpc("sync_delete_watch_progress", params)
            }.onFailure { e ->
                log.e(e) { "Failed to push watch progress delete" }
            }
        }
    }

    private fun publish() {
        _uiState.value = WatchProgressUiState(
            entries = entriesByVideoId.values.toList().sortedByDescending { it.lastUpdatedEpochMs },
        )
    }

    private fun persist() {
        WatchProgressStorage.savePayload(
            WatchProgressCodec.encodeEntries(entriesByVideoId.values),
        )
    }
}
