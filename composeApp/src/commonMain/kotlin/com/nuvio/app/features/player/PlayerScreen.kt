package com.nuvio.app.features.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContent
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.nuvioTypeScale
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max

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
                visible = !initialLoadCompleted && errorMessage == null,
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

@Composable
private fun PlayerControlsShell(
    title: String,
    streamTitle: String,
    providerName: String,
    seasonNumber: Int?,
    episodeNumber: Int?,
    episodeTitle: String?,
    playbackSnapshot: PlayerPlaybackSnapshot,
    displayedPositionMs: Long,
    metrics: PlayerLayoutMetrics,
    resizeMode: PlayerResizeMode,
    onBack: () -> Unit,
    onTogglePlayback: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onResizeModeClick: () -> Unit,
    onSpeedClick: () -> Unit,
    onScrubChange: (Long) -> Unit,
    onScrubFinished: (Long) -> Unit,
    horizontalSafePadding: Dp,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.7f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.7f),
                        ),
                    ),
                ),
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = horizontalSafePadding),
        ) {
            PlayerHeader(
                title = title,
                streamTitle = streamTitle,
                providerName = providerName,
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
                episodeTitle = episodeTitle,
                backendLabel = "Android · Media3 ExoPlayer",
                metrics = metrics,
                onBack = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeContent.only(WindowInsetsSides.Top))
                    .padding(horizontal = metrics.horizontalPadding, vertical = metrics.verticalPadding),
            )

            CenterControls(
                snapshot = playbackSnapshot,
                metrics = metrics,
                onSeekBack = onSeekBack,
                onSeekForward = onSeekForward,
                onTogglePlayback = onTogglePlayback,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(bottom = metrics.centerLift),
            )

            ProgressControls(
                playbackSnapshot = playbackSnapshot,
                displayedPositionMs = displayedPositionMs,
                metrics = metrics,
                resizeMode = resizeMode,
                onScrubChange = onScrubChange,
                onScrubFinished = onScrubFinished,
                onResizeModeClick = onResizeModeClick,
                onSpeedClick = onSpeedClick,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = metrics.horizontalPadding)
                    .padding(bottom = metrics.sliderBottomOffset),
            )
        }
    }
}

@Composable
private fun PlayerHeader(
    title: String,
    streamTitle: String,
    providerName: String,
    seasonNumber: Int?,
    episodeNumber: Int?,
    episodeTitle: String?,
    backendLabel: String,
    metrics: PlayerLayoutMetrics,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val typeScale = MaterialTheme.nuvioTypeScale
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = title,
                    style = typeScale.titleLg.copy(
                        fontSize = metrics.titleSize,
                        lineHeight = metrics.titleSize * 1.16f,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (seasonNumber != null && episodeNumber != null && !episodeTitle.isNullOrBlank()) {
                    Text(
                        text = "S${seasonNumber}E${episodeNumber} • $episodeTitle",
                        style = typeScale.bodyMd.copy(
                            fontSize = metrics.episodeInfoSize,
                            lineHeight = metrics.episodeInfoSize * 1.3f,
                        ),
                        color = Color.White.copy(alpha = 0.9f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = streamTitle,
                        style = typeScale.labelSm.copy(
                            fontSize = metrics.metadataSize,
                            lineHeight = metrics.metadataSize * 1.25f,
                        ),
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = providerName,
                        style = typeScale.labelSm.copy(
                            fontSize = metrics.metadataSize,
                            lineHeight = metrics.metadataSize * 1.25f,
                            fontStyle = FontStyle.Italic,
                        ),
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = backendLabel,
                    style = typeScale.labelXs,
                    color = Color.White.copy(alpha = 0.9f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable(onClick = onBack)
                    .padding(8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Close player",
                    tint = Color.White,
                    modifier = Modifier.size(metrics.headerIconSize),
                )
            }
        }
    }
}

@Composable
private fun CenterControls(
    snapshot: PlayerPlaybackSnapshot,
    metrics: PlayerLayoutMetrics,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onTogglePlayback: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(metrics.centerGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SideControlButton(
            icon = Icons.Rounded.Replay10,
            contentDescription = "Seek backward 10 seconds",
            metrics = metrics,
            onClick = onSeekBack,
        )
        PlayPauseControlButton(
            isPlaying = snapshot.isPlaying,
            isBuffering = snapshot.isLoading,
            metrics = metrics,
            onClick = onTogglePlayback,
        )
        SideControlButton(
            icon = Icons.Rounded.Forward10,
            contentDescription = "Seek forward 10 seconds",
            metrics = metrics,
            onClick = onSeekForward,
        )
    }
}

@Composable
private fun SideControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    metrics: PlayerLayoutMetrics,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.28f))
            .clickable(onClick = onClick)
            .padding(metrics.sideButtonPadding),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(metrics.sideIconSize),
        )
    }
}

@Composable
private fun PlayPauseControlButton(
    isPlaying: Boolean,
    isBuffering: Boolean,
    metrics: PlayerLayoutMetrics,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.28f))
            .clickable(onClick = onClick)
            .padding(metrics.playButtonPadding),
        contentAlignment = Alignment.Center,
    ) {
        if (isBuffering) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 3.dp,
                modifier = Modifier.size(metrics.playIconSize),
            )
        } else {
            Icon(
                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = Color.White,
                modifier = Modifier.size(metrics.playIconSize),
            )
        }
    }
}

@Composable
private fun ProgressControls(
    playbackSnapshot: PlayerPlaybackSnapshot,
    displayedPositionMs: Long,
    metrics: PlayerLayoutMetrics,
    resizeMode: PlayerResizeMode,
    onScrubChange: (Long) -> Unit,
    onScrubFinished: (Long) -> Unit,
    onResizeModeClick: () -> Unit,
    onSpeedClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val durationMs = playbackSnapshot.durationMs.coerceAtLeast(1L)
    Column(modifier = modifier) {
        Slider(
            modifier = Modifier
                .fillMaxWidth()
                .height(metrics.sliderTouchHeight)
                .graphicsLayer(scaleY = metrics.sliderScaleY),
            value = displayedPositionMs.coerceIn(0L, durationMs).toFloat(),
            onValueChange = { value -> onScrubChange(value.toLong()) },
            onValueChangeFinished = { onScrubFinished(displayedPositionMs.coerceIn(0L, durationMs)) },
            valueRange = 0f..durationMs.toFloat(),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .padding(top = 4.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TimePill(text = formatPlaybackTime(displayedPositionMs), fontSize = metrics.timeSize)
            TimePill(text = formatPlaybackTime(durationMs), fontSize = metrics.timeSize)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.5f),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(24.dp),
                ),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PlayerActionPillButton(
                        icon = Icons.Rounded.AspectRatio,
                        label = resizeMode.label,
                        onClick = onResizeModeClick,
                    )
                    PlayerActionPillButton(
                        icon = Icons.Rounded.Speed,
                        label = "${playbackSnapshot.playbackSpeed}x",
                        onClick = onSpeedClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun TimePill(
    text: String,
    fontSize: androidx.compose.ui.unit.TextUnit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.5f))
            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.nuvioTypeScale.labelSm.copy(
                fontSize = fontSize,
                lineHeight = fontSize * 1.25f,
                fontWeight = FontWeight.Medium,
            ),
            color = Color.White,
        )
    }
}

@Composable
private fun PlayerActionPillButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color.White,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.nuvioTypeScale.labelSm,
            color = Color.White,
        )
    }
}

@Composable
private fun OpeningOverlay(
    artwork: String?,
    logo: String?,
    onBack: () -> Unit,
    horizontalSafePadding: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.85f)),
    ) {
        if (artwork != null) {
            AsyncImage(
                model = artwork,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.3f),
                                Color.Black.copy(alpha = 0.6f),
                                Color.Black.copy(alpha = 0.8f),
                                Color.Black.copy(alpha = 0.9f),
                            ),
                        ),
                    ),
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.safeContent.only(WindowInsetsSides.Top))
                .padding(top = 20.dp, start = horizontalSafePadding, end = horizontalSafePadding + 20.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.3f))
                .clickable(onClick = onBack)
                .padding(10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Close player",
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (logo != null) {
                AsyncImage(
                    model = logo,
                    contentDescription = null,
                    modifier = Modifier
                        .width(300.dp)
                        .height(180.dp),
                    contentScale = ContentScale.Fit,
                )
            } else {
                CircularProgressIndicator(
                    color = Color(0xFFE50914),
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(54.dp),
                )
            }
        }
    }
}

@Composable
private fun GestureFeedbackPill(
    message: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Black.copy(alpha = 0.75f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Speed,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            text = message,
            style = MaterialTheme.nuvioTypeScale.bodyLg.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White,
        )
    }
}

@Composable
private fun PauseMetadataOverlay(
    title: String,
    isEpisode: Boolean,
    seasonNumber: Int?,
    episodeNumber: Int?,
    episodeTitle: String?,
    streamSubtitle: String?,
    providerName: String,
    metrics: PlayerLayoutMetrics,
    horizontalSafePadding: Dp,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.85f),
                        Color.Black.copy(alpha = 0.45f),
                        Color.Transparent,
                    ),
                ),
            )
            .windowInsetsPadding(WindowInsets.safeContent)
            .padding(
                start = 24.dp + horizontalSafePadding,
                end = 24.dp + horizontalSafePadding,
                top = 24.dp,
                bottom = 24.dp,
            ),
        verticalArrangement = Arrangement.Bottom,
    ) {
        Text(
            text = "You're watching",
            style = MaterialTheme.nuvioTypeScale.bodyLg,
            color = Color(0xFFB8B8B8),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = title,
            style = MaterialTheme.nuvioTypeScale.displayMd.copy(
                fontSize = max(metrics.titleSize.value * 1.8f, 32f).sp,
                fontWeight = FontWeight.ExtraBold,
            ),
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(8.dp))
        val episodeInfo = if (isEpisode && seasonNumber != null && episodeNumber != null) {
            buildString {
                append("S")
                append(seasonNumber)
                append(" • E")
                append(episodeNumber)
                if (!episodeTitle.isNullOrBlank()) {
                    append(" • ")
                    append(episodeTitle)
                }
            }
        } else {
            providerName
        }
        Text(
            text = episodeInfo,
            style = MaterialTheme.nuvioTypeScale.bodyLg,
            color = Color(0xFFCCCCCC),
        )
        if (!streamSubtitle.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = streamSubtitle,
                style = MaterialTheme.nuvioTypeScale.bodyLg.copy(lineHeight = 24.sp),
                color = Color(0xFFD6D6D6),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun playerHorizontalSafePadding(): Dp {
    val layoutDirection = LocalLayoutDirection.current
    val safePadding = WindowInsets.safeContent.asPaddingValues()
    val left = safePadding.calculateLeftPadding(layoutDirection)
    val right = safePadding.calculateRightPadding(layoutDirection)
    return if (left > right) left else right
}

@Composable
private fun ErrorModal(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth(0.8f)
                .widthIn(max = 400.dp),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF1A1A1A),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEF4444).copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ErrorOutline,
                        contentDescription = null,
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(32.dp),
                    )
                }
                Text(
                    text = "Playback error",
                    style = MaterialTheme.nuvioTypeScale.titleMd.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                )
                Text(
                    text = message,
                    style = MaterialTheme.nuvioTypeScale.bodyLg.copy(lineHeight = 22.sp),
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onDismiss),
                    color = Color.White,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        text = "Dismiss",
                        modifier = Modifier.padding(vertical = 12.dp),
                        style = MaterialTheme.nuvioTypeScale.bodyLg.copy(fontWeight = FontWeight.Bold),
                        color = Color.Black,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

private data class PlayerLayoutMetrics(
    val horizontalPadding: Dp,
    val verticalPadding: Dp,
    val titleSize: TextUnit,
    val episodeInfoSize: TextUnit,
    val metadataSize: TextUnit,
    val centerGap: Dp,
    val centerLift: Dp,
    val sliderBottomOffset: Dp,
    val sliderTouchHeight: Dp,
    val sliderScaleY: Float,
    val timeSize: TextUnit,
    val headerIconSize: Dp,
    val sideButtonPadding: Dp,
    val sideIconSize: Dp,
    val playButtonPadding: Dp,
    val playIconSize: Dp,
) {
    companion object {
        fun fromWidth(width: Dp): PlayerLayoutMetrics =
            when {
                width >= 1440.dp -> PlayerLayoutMetrics(
                    horizontalPadding = 28.dp,
                    verticalPadding = 24.dp,
                    titleSize = 28.dp.value.sp,
                    episodeInfoSize = 16.dp.value.sp,
                    metadataSize = 14.dp.value.sp,
                    centerGap = 112.dp,
                    centerLift = 24.dp,
                    sliderBottomOffset = 28.dp,
                    sliderTouchHeight = 28.dp,
                    sliderScaleY = 0.72f,
                    timeSize = 14.dp.value.sp,
                    headerIconSize = 24.dp,
                    sideButtonPadding = 14.dp,
                    sideIconSize = 34.dp,
                    playButtonPadding = 18.dp,
                    playIconSize = 44.dp,
                )
                width >= 1024.dp -> PlayerLayoutMetrics(
                    horizontalPadding = 24.dp,
                    verticalPadding = 20.dp,
                    titleSize = 24.dp.value.sp,
                    episodeInfoSize = 15.dp.value.sp,
                    metadataSize = 13.dp.value.sp,
                    centerGap = 88.dp,
                    centerLift = 18.dp,
                    sliderBottomOffset = 24.dp,
                    sliderTouchHeight = 26.dp,
                    sliderScaleY = 0.74f,
                    timeSize = 13.dp.value.sp,
                    headerIconSize = 22.dp,
                    sideButtonPadding = 13.dp,
                    sideIconSize = 32.dp,
                    playButtonPadding = 16.dp,
                    playIconSize = 42.dp,
                )
                width >= 768.dp -> PlayerLayoutMetrics(
                    horizontalPadding = 20.dp,
                    verticalPadding = 16.dp,
                    titleSize = 22.dp.value.sp,
                    episodeInfoSize = 14.dp.value.sp,
                    metadataSize = 12.dp.value.sp,
                    centerGap = 72.dp,
                    centerLift = 14.dp,
                    sliderBottomOffset = 20.dp,
                    sliderTouchHeight = 24.dp,
                    sliderScaleY = 0.78f,
                    timeSize = 12.dp.value.sp,
                    headerIconSize = 20.dp,
                    sideButtonPadding = 12.dp,
                    sideIconSize = 30.dp,
                    playButtonPadding = 15.dp,
                    playIconSize = 38.dp,
                )
                else -> PlayerLayoutMetrics(
                    horizontalPadding = 20.dp,
                    verticalPadding = 16.dp,
                    titleSize = 18.dp.value.sp,
                    episodeInfoSize = 14.dp.value.sp,
                    metadataSize = 12.dp.value.sp,
                    centerGap = 56.dp,
                    centerLift = 10.dp,
                    sliderBottomOffset = 16.dp,
                    sliderTouchHeight = 22.dp,
                    sliderScaleY = 0.82f,
                    timeSize = 12.dp.value.sp,
                    headerIconSize = 20.dp,
                    sideButtonPadding = 10.dp,
                    sideIconSize = 26.dp,
                    playButtonPadding = 13.dp,
                    playIconSize = 34.dp,
                )
            }
    }
}

private fun PlayerResizeMode.next(): PlayerResizeMode =
    when (this) {
        PlayerResizeMode.Fit -> PlayerResizeMode.Fill
        PlayerResizeMode.Fill -> PlayerResizeMode.Zoom
        PlayerResizeMode.Zoom -> PlayerResizeMode.Fit
    }

private val PlayerResizeMode.label: String
    get() = when (this) {
        PlayerResizeMode.Fit -> "Fit"
        PlayerResizeMode.Fill -> "Fill"
        PlayerResizeMode.Zoom -> "Zoom"
    }

private fun formatPlaybackTime(positionMs: Long): String {
    val totalSeconds = (positionMs / 1000L).coerceAtLeast(0L)
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        "${hours}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    } else {
        "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    }
}
