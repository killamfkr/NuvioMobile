package com.nuvio.app.core.sync

import co.touchlab.kermit.Logger
import com.nuvio.app.core.network.SupabaseProvider
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object ProfileSettingsSync {
    private val log = Logger.withTag("ProfileSettingsSync")

    suspend fun pull(profileId: Int): JsonObject? {
        return runCatching {
            val params = buildJsonObject { put("p_profile_id", profileId) }
            val result = SupabaseProvider.client.postgrest.rpc("sync_pull_profile_settings_blob", params)
            result.decodeSingleOrNull<SettingsBlobResponse>()?.settingsJson
        }.onFailure { e ->
            log.e(e) { "Failed to pull profile settings" }
        }.getOrNull()
    }

    suspend fun push(profileId: Int, settingsJson: JsonObject) {
        runCatching {
            val params = buildJsonObject {
                put("p_profile_id", profileId)
                put("p_settings_json", settingsJson)
            }
            SupabaseProvider.client.postgrest.rpc("sync_push_profile_settings_blob", params)
        }.onFailure { e ->
            log.e(e) { "Failed to push profile settings" }
        }
    }
}

@kotlinx.serialization.Serializable
private data class SettingsBlobResponse(
    @kotlinx.serialization.SerialName("profile_id") val profileId: Int = 0,
    @kotlinx.serialization.SerialName("settings_json") val settingsJson: JsonObject? = null,
    @kotlinx.serialization.SerialName("updated_at") val updatedAt: String = "",
)
