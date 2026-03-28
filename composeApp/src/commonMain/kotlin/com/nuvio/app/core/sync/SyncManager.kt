package com.nuvio.app.core.sync

import co.touchlab.kermit.Logger
import com.nuvio.app.core.auth.AuthRepository
import com.nuvio.app.core.auth.AuthState
import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.library.LibraryRepository
import com.nuvio.app.features.profiles.ProfileRepository
import com.nuvio.app.features.watched.WatchedRepository
import com.nuvio.app.features.watchprogress.WatchProgressRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object SyncManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val log = Logger.withTag("SyncManager")

    fun pullAllForProfile(profileId: Int) {
        val authState = AuthRepository.state.value
        if (authState !is AuthState.Authenticated) return
        if (authState.isAnonymous) return

        scope.launch {
            log.i { "Starting pull-all for profile $profileId" }

            launch {
                runCatching { LibraryRepository.pullFromServer(profileId) }
                    .onFailure { log.e(it) { "Library pull failed" } }
            }
            launch {
                runCatching { WatchProgressRepository.pullFromServer(profileId) }
                    .onFailure { log.e(it) { "WatchProgress pull failed" } }
            }
            launch {
                runCatching { AddonRepository.pullFromServer(profileId) }
                    .onFailure { log.e(it) { "Addon pull failed" } }
            }
            launch {
                runCatching { WatchedRepository.pullFromServer(profileId) }
                    .onFailure { log.e(it) { "Watched pull failed" } }
            }
            launch {
                runCatching { ProfileSettingsSync.pull(profileId) }
                    .onFailure { log.e(it) { "ProfileSettings pull failed" } }
            }

            log.i { "Pull-all launched for profile $profileId" }
        }
    }
}
