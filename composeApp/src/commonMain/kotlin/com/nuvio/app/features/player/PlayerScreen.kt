package com.nuvio.app.features.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContent
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PlayerScreen(
    title: String,
    sourceUrl: String,
    providerName: String,
    streamTitle: String,
    streamSubtitle: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    logo: String? = null,
    poster: String? = null,
    background: String? = null,
    seasonNumber: Int? = null,
    episodeNumber: Int? = null,
    episodeTitle: String? = null,
) {
    LockPlayerToLandscape()
    EnterImmersivePlayerMode()
    val playerSettingsUiState by remember {
        PlayerSettingsRepository.ensureLoaded()
        PlayerSettingsRepository.uiState
    }.collectAsStateWithLifecycle()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        val horizontalSafePadding = playerHorizontalSafePadding()
        val metrics = remember(maxWidth) { PlayerLayoutMetrics.fromWidth(maxWidth) }
        val scope = rememberCoroutineScope()
        var controlsVisible by rememberSaveable { mutableStateOf(true) }
        var shouldPlay by rememberSaveable(sourceUrl) { mutableStateOf(true) }
        var resizeMode by rememberSaveable { mutableStateOf(PlayerResizeMode.Fit) }
        var layoutSize by remember { mutableStateOf(IntSize.Zero) }
        var playbackSnapshot by remember { mutableStateOf(PlayerPlaybackSnapshot()) }
        var playerController by remember { mutableStateOf<PlayerEngineController?>(null) }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        var scrubbingPositionMs by remember { mutableStateOf<Long?>(null) }
        var pausedOverlayVisible by remember { mutableStateOf(false) }
        var gestureMessage by remember { mutableStateOf<String?>(null) }
        var gestureMessageJob by remember { mutableStateOf<Job?>(null) }
        var initialLoadCompleted by remember(sourceUrl) { mutableStateOf(false) }
        val backdropArtwork = background ?: poster
        val displayedPositionMs = scrubbingPositionMs ?: playbackSnapshot.positionMs
        val isEpisode = seasonNumber != null && episodeNumber != null

        fun showGestureMessage(message: String) {
            gestureMessageJob?.cancel()
            gestureMessage = message
            gestureMessageJob = scope.launch {
                delay(900)
                gestureMessage = null
            }
        }

        fun togglePlayback() {
            if (playbackSnapshot.isPlaying) {
                shouldPlay = false
                playerController?.pause()
            } else {
                if (playbackSnapshot.isEnded) {
                    playerController?.seekTo(0L)
                }
                shouldPlay = true
                playerController?.play()
            }
            controlsVisible = true
        }

        fun seekBy(offsetMs: Long) {
            playerController?.seekBy(offsetMs)
            controlsVisible = true
            val seconds = offsetMs / 1000L
            if (seconds != 0L) {
                showGestureMessage(if (seconds > 0) "+${seconds}s" else "${seconds}s")
            }
        }

        fun cycleResizeMode() {
            resizeMode = resizeMode.next()
            showGestureMessage(resizeMode.label)
            controlsVisible = true
        }

        fun cyclePlaybackSpeed() {
            val speeds = listOf(1f, 1.25f, 1.5f, 2f)
            val current = playbackSnapshot.playbackSpeed
            val next = speeds.firstOrNull { it > current + 0.01f } ?: speeds.first()
            playerController?.setPlaybackSpeed(next)
            showGestureMessage("${next}x")
            controlsVisible = true
        }

        LaunchedEffect(sourceUrl) {
            errorMessage = null
            scrubbingPositionMs = null
            initialLoadCompleted = false
        }

        LaunchedEffect(controlsVisible, playbackSnapshot.isPlaying, playbackSnapshot.isLoading, errorMessage) {
            if (!controlsVisible || !playbackSnapshot.isPlaying || playbackSnapshot.isLoading || errorMessage != null) {
                return@LaunchedEffect
            }
            delay(3500)
            controlsVisible = false
        }

        LaunchedEffect(playbackSnapshot.isPlaying, playbackSnapshot.isLoading, playbackSnapshot.durationMs, errorMessage) {
            pausedOverlayVisible = false
            if (playbackSnapshot.isPlaying || playbackSnapshot.isLoading || playbackSnapshot.durationMs <= 0L || errorMessage != null) {
                return@LaunchedEffect
            }
            delay(5000)
            pausedOverlayVisible = true
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { layoutSize = it }
                .pointerInput(controlsVisible, playerController) {
                    detectTapGestures(
                        onTap = { offset ->
                            val centerStart = layoutSize.width * 0.4f
                            val centerEnd = layoutSize.width * 0.6f
                            if (controlsVisible && offset.x in centerStart..centerEnd) {
                                controlsVisible = false
                            } else {
                                controlsVisible = !controlsVisible
                            }
                        },
                        onDoubleTap = { offset ->
                            when {
                                offset.x < layoutSize.width * 0.4f -> seekBy(-10_000L)
                                offset.x > layoutSize.width * 0.6f -> seekBy(10_000L)
                                else -> controlsVisible = !controlsVisible
                            }
                        },
                    )
                },
        ) {
            PlatformPlayerSurface(
                sourceUrl = sourceUrl,
                modifier = Modifier.fillMaxSize(),
                playWhenReady = shouldPlay,
                resizeMode = resizeMode,
                onControllerReady = { controller ->
                    playerController = controller
                },
                onSnapshot = { snapshot ->
                    playbackSnapshot = snapshot
                    if (!snapshot.isLoading) {
                        initialLoadCompleted = true
                    }
                    if (snapshot.isEnded) {
                        shouldPlay = false
                        controlsVisible = true
                    }
                },
                onError = { message ->
                    errorMessage = message
                    if (message != null) {
                        controlsVisible = true
                    }
                },
            )

            if (pausedOverlayVisible && !controlsVisible) {
                PauseMetadataOverlay(
                    title = title,
                    isEpisode = isEpisode,
                    seasonNumber = seasonNumber,
                    episodeNumber = episodeNumber,
                    episodeTitle = episodeTitle,
                    streamSubtitle = streamSubtitle,
                    providerName = providerName,
                    metrics = metrics,
                    horizontalSafePadding = horizontalSafePadding,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                PlayerControlsShell(
                    title = title,
                    streamTitle = streamTitle,
                    providerName = providerName,
                    seasonNumber = seasonNumber,
                    episodeNumber = episodeNumber,
                    episodeTitle = episodeTitle,
                    playbackSnapshot = playbackSnapshot,
                    displayedPositionMs = displayedPositionMs,
                    metrics = metrics,
                    resizeMode = resizeMode,
                    onBack = onBack,
                    onTogglePlayback = ::togglePlayback,
                    onSeekBack = { seekBy(-10_000L) },
                    onSeekForward = { seekBy(10_000L) },
                    onResizeModeClick = ::cycleResizeMode,
                    onSpeedClick = ::cyclePlaybackSpeed,
                    onScrubChange = { positionMs -> scrubbingPositionMs = positionMs },
                    onScrubFinished = { positionMs ->
                        scrubbingPositionMs = null
                        playerController?.seekTo(positionMs)
                    },
                    horizontalSafePadding = horizontalSafePadding,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            AnimatedVisibility(
                visible = playerSettingsUiState.showLoadingOverlay && !initialLoadCompleted && errorMessage == null,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                OpeningOverlay(
                    artwork = backdropArtwork,
                    logo = logo,
                    onBack = onBack,
                    horizontalSafePadding = horizontalSafePadding,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            AnimatedVisibility(
                visible = gestureMessage != null,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    GestureFeedbackPill(
                        message = gestureMessage.orEmpty(),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .windowInsetsPadding(WindowInsets.safeContent.only(WindowInsetsSides.Top))
                            .padding(horizontal = horizontalSafePadding)
                            .padding(top = 40.dp),
                    )
                }
            }

            if (errorMessage != null) {
                ErrorModal(
                    message = errorMessage.orEmpty(),
                    onDismiss = onBack,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
}
