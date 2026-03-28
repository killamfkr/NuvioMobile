package com.nuvio.app

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.request.crossfade
import com.nuvio.app.core.auth.AuthRepository
import com.nuvio.app.core.auth.AuthState
import com.nuvio.app.core.sync.SyncManager
import com.nuvio.app.core.ui.nuvioBottomNavigationBarInsets
import com.nuvio.app.core.ui.NuvioPosterActionSheet
import com.nuvio.app.core.ui.NuvioTheme
import com.nuvio.app.features.auth.AuthScreen
import com.nuvio.app.features.catalog.CatalogRepository
import com.nuvio.app.features.catalog.CatalogScreen
import com.nuvio.app.features.details.MetaDetailsRepository
import com.nuvio.app.features.details.MetaDetailsScreen
import com.nuvio.app.features.home.HomeCatalogSection
import com.nuvio.app.features.home.HomeScreen
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.library.LibraryItem
import com.nuvio.app.features.library.LibraryRepository
import com.nuvio.app.features.library.LibraryScreen
import com.nuvio.app.features.library.toLibraryItem
import com.nuvio.app.features.player.PlayerRoute
import com.nuvio.app.features.player.PlayerScreen
import com.nuvio.app.features.profiles.NuvioProfile
import com.nuvio.app.features.profiles.ProfileEditScreen
import com.nuvio.app.features.profiles.ProfileRepository
import com.nuvio.app.features.profiles.ProfileSelectionScreen
import com.nuvio.app.features.search.SearchScreen
import com.nuvio.app.features.settings.SettingsScreen
import com.nuvio.app.features.streams.StreamsRepository
import com.nuvio.app.features.streams.StreamsScreen
import com.nuvio.app.features.watched.WatchedRepository
import com.nuvio.app.features.watched.toWatchedItem
import com.nuvio.app.features.watched.watchedItemKey
import com.nuvio.app.features.watchprogress.ContinueWatchingItem
import kotlinx.serialization.Serializable

@Serializable
object TabsRoute

@Serializable
data class DetailRoute(val type: String, val id: String)

@Serializable
data class StreamRoute(
    val type: String,
    val videoId: String,
    val parentMetaId: String? = null,
    val parentMetaType: String? = null,
    val title: String,
    val logo: String? = null,
    val poster: String? = null,
    val background: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val episodeTitle: String? = null,
    val episodeThumbnail: String? = null,
    val resumePositionMs: Long? = null,
)

@Serializable
data class CatalogRoute(
    val title: String,
    val subtitle: String,
    val manifestUrl: String,
    val type: String,
    val catalogId: String,
    val supportsPagination: Boolean = false,
    val genre: String? = null,
)

enum class AppScreenTab {
    Home,
    Search,
    Library,
    Settings,
}

private enum class AppGateScreen {
    Loading,
    Auth,
    ProfileSelection,
    ProfileEdit,
    Main,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Preview
fun App() {
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .crossfade(true)
            .build()
    }
    NuvioTheme {
        LaunchedEffect(Unit) {
            AuthRepository.initialize()
        }

        val authState by AuthRepository.state.collectAsStateWithLifecycle()
        var gateScreen by rememberSaveable { mutableStateOf(AppGateScreen.Loading.name) }
        var editingProfile by remember { mutableStateOf<NuvioProfile?>(null) }
        var isNewProfile by remember { mutableStateOf(false) }

        LaunchedEffect(authState) {
            when (authState) {
                is AuthState.Loading -> gateScreen = AppGateScreen.Loading.name
                is AuthState.Unauthenticated -> gateScreen = AppGateScreen.Auth.name
                is AuthState.Authenticated -> {
                    if (gateScreen == AppGateScreen.Loading.name || gateScreen == AppGateScreen.Auth.name) {
                        gateScreen = AppGateScreen.ProfileSelection.name
                    }
                }
            }
        }

        Crossfade(
            targetState = gateScreen,
            label = "app_gate",
        ) { currentGate ->
            when (currentGate) {
                AppGateScreen.Loading.name -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
                AppGateScreen.Auth.name -> {
                    AuthScreen(modifier = Modifier.fillMaxSize())
                }
                AppGateScreen.ProfileSelection.name -> {
                    ProfileSelectionScreen(
                        onProfileSelected = { profile ->
                            ProfileRepository.selectProfile(profile.profileIndex)
                            SyncManager.pullAllForProfile(profile.profileIndex)
                            gateScreen = AppGateScreen.Main.name
                        },
                        onEditProfile = { profile ->
                            editingProfile = profile
                            isNewProfile = false
                            gateScreen = AppGateScreen.ProfileEdit.name
                        },
                        onAddProfile = {
                            editingProfile = null
                            isNewProfile = true
                            gateScreen = AppGateScreen.ProfileEdit.name
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                AppGateScreen.ProfileEdit.name -> {
                    ProfileEditScreen(
                        profile = editingProfile,
                        onBack = { gateScreen = AppGateScreen.ProfileSelection.name },
                        onSaved = { gateScreen = AppGateScreen.ProfileSelection.name },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                AppGateScreen.Main.name -> {
                    MainAppContent()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainAppContent() {
        val navController = rememberNavController()
        val hapticFeedback = LocalHapticFeedback.current
        var selectedTab by rememberSaveable { mutableStateOf(AppScreenTab.Home) }
        var selectedPosterForActions by remember { mutableStateOf<MetaPreview?>(null) }
        val libraryUiState by remember {
            LibraryRepository.ensureLoaded()
            LibraryRepository.uiState
        }.collectAsStateWithLifecycle()
        val watchedUiState by remember {
            WatchedRepository.ensureLoaded()
            WatchedRepository.uiState
        }.collectAsStateWithLifecycle()

        val onPlay: (String, String, String, String, String, String?, String?, String?, Int?, Int?, String?, String?, Long?) -> Unit =
            { type, videoId, parentMetaId, parentMetaType, title, logo, poster, background, seasonNumber, episodeNumber, episodeTitle, episodeThumbnail, resumePositionMs ->
                navController.navigate(
                    StreamRoute(
                        type = type,
                        videoId = videoId,
                        parentMetaId = parentMetaId,
                        parentMetaType = parentMetaType,
                        title = title,
                        logo = logo,
                        poster = poster,
                        background = background,
                        seasonNumber = seasonNumber,
                        episodeNumber = episodeNumber,
                        episodeTitle = episodeTitle,
                        episodeThumbnail = episodeThumbnail,
                        resumePositionMs = resumePositionMs,
                    )
                )
            }

        val onCatalogClick: (HomeCatalogSection) -> Unit = { section ->
            navController.navigate(
                CatalogRoute(
                    title = section.title,
                    subtitle = section.subtitle,
                    manifestUrl = section.manifestUrl,
                    type = section.type,
                    catalogId = section.catalogId,
                    supportsPagination = section.supportsPagination,
                ),
            )
        }

        val onContinueWatchingClick: (ContinueWatchingItem) -> Unit = { item ->
            navController.navigate(
                StreamRoute(
                    type = item.parentMetaType,
                    videoId = item.videoId,
                    parentMetaId = item.parentMetaId,
                    parentMetaType = item.parentMetaType,
                    title = item.title,
                    logo = item.logo,
                    poster = item.poster,
                    background = item.background,
                    seasonNumber = item.seasonNumber,
                    episodeNumber = item.episodeNumber,
                    episodeTitle = item.episodeTitle,
                    episodeThumbnail = item.episodeThumbnail,
                    resumePositionMs = item.resumePositionMs,
                ),
            )
        }

        val onContinueWatchingLongPress: (ContinueWatchingItem) -> Unit = { item ->
            navController.navigate(
                DetailRoute(
                    type = item.parentMetaType,
                    id = item.parentMetaId,
                ),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets(0),
                bottomBar = {
                    val navigationItemColors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        windowInsets = nuvioBottomNavigationBarInsets(),
                    ) {
                        NavigationBarItem(
                            selected = selectedTab == AppScreenTab.Home,
                            onClick = { selectedTab = AppScreenTab.Home },
                            icon = { Icon(Icons.Rounded.Home, contentDescription = null) },
                            label = { Text("Home") },
                            colors = navigationItemColors,
                        )
                        NavigationBarItem(
                            selected = selectedTab == AppScreenTab.Search,
                            onClick = { selectedTab = AppScreenTab.Search },
                            icon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                            label = { Text("Search") },
                            colors = navigationItemColors,
                        )
                        NavigationBarItem(
                            selected = selectedTab == AppScreenTab.Library,
                            onClick = { selectedTab = AppScreenTab.Library },
                            icon = { Icon(Icons.Rounded.VideoLibrary, contentDescription = null) },
                            label = { Text("Library") },
                            colors = navigationItemColors,
                        )
                        NavigationBarItem(
                            selected = selectedTab == AppScreenTab.Settings,
                            onClick = { selectedTab = AppScreenTab.Settings },
                            icon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                            label = { Text("Settings") },
                            colors = navigationItemColors,
                        )
                    }
                },
            ) { innerPadding ->
                AppTabHost(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    selectedTab = selectedTab,
                    onCatalogClick = onCatalogClick,
                    onPosterClick = { meta ->
                        navController.navigate(DetailRoute(type = meta.type, id = meta.id))
                    },
                    onPosterLongClick = { meta ->
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        selectedPosterForActions = meta
                    },
                    onLibraryPosterClick = { item ->
                        navController.navigate(DetailRoute(type = item.type, id = item.id))
                    },
                    onContinueWatchingClick = onContinueWatchingClick,
                    onContinueWatchingLongPress = onContinueWatchingLongPress,
                )
            }

            NavHost(
                navController = navController,
                startDestination = TabsRoute,
                modifier = Modifier.fillMaxSize(),
            ) {
                composable<TabsRoute> {
                    Unit
                }
                composable<DetailRoute> { backStackEntry ->
                    val route = backStackEntry.toRoute<DetailRoute>()
                    MetaDetailsScreen(
                        type = route.type,
                        id = route.id,
                        onBack = {
                            MetaDetailsRepository.clear()
                            navController.popBackStack()
                        },
                        onPlay = onPlay,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                composable<StreamRoute> { backStackEntry ->
                    val route = backStackEntry.toRoute<StreamRoute>()
                    StreamsScreen(
                        type = route.type,
                        videoId = route.videoId,
                        parentMetaId = route.parentMetaId ?: route.videoId,
                        parentMetaType = route.parentMetaType ?: route.type,
                        title = route.title,
                        logo = route.logo,
                        poster = route.poster,
                        background = route.background,
                        seasonNumber = route.seasonNumber,
                        episodeNumber = route.episodeNumber,
                        episodeTitle = route.episodeTitle,
                        episodeThumbnail = route.episodeThumbnail,
                        resumePositionMs = route.resumePositionMs,
                        onStreamSelected = { stream ->
                            val sourceUrl = stream.directPlaybackUrl
                            if (sourceUrl != null) {
                                navController.navigate(
                                    PlayerRoute(
                                        title = route.title,
                                        sourceUrl = sourceUrl,
                                        logo = route.logo,
                                        poster = route.poster,
                                        background = route.background,
                                        seasonNumber = route.seasonNumber,
                                        episodeNumber = route.episodeNumber,
                                        episodeTitle = route.episodeTitle,
                                        episodeThumbnail = route.episodeThumbnail,
                                        streamTitle = stream.streamLabel,
                                        streamSubtitle = stream.streamSubtitle,
                                        providerName = stream.addonName,
                                        providerAddonId = stream.addonId,
                                        contentType = route.type,
                                        videoId = route.videoId,
                                        parentMetaId = route.parentMetaId ?: route.videoId,
                                        parentMetaType = route.parentMetaType ?: route.type,
                                        initialPositionMs = route.resumePositionMs ?: 0L,
                                    )
                                )
                            }
                        },
                        onBack = {
                            StreamsRepository.clear()
                            navController.popBackStack()
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                composable<PlayerRoute> { backStackEntry ->
                    val route = backStackEntry.toRoute<PlayerRoute>()
                    PlayerScreen(
                        title = route.title,
                        sourceUrl = route.sourceUrl,
                        logo = route.logo,
                        poster = route.poster,
                        background = route.background,
                        seasonNumber = route.seasonNumber,
                        episodeNumber = route.episodeNumber,
                        episodeTitle = route.episodeTitle,
                        episodeThumbnail = route.episodeThumbnail,
                        streamTitle = route.streamTitle,
                        streamSubtitle = route.streamSubtitle,
                        providerName = route.providerName,
                        providerAddonId = route.providerAddonId,
                        contentType = route.contentType,
                        videoId = route.videoId,
                        parentMetaId = route.parentMetaId,
                        parentMetaType = route.parentMetaType,
                        initialPositionMs = route.initialPositionMs,
                        onBack = { navController.popBackStack() },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                composable<CatalogRoute> { backStackEntry ->
                    val route = backStackEntry.toRoute<CatalogRoute>()
                    CatalogScreen(
                        title = route.title,
                        subtitle = route.subtitle,
                        manifestUrl = route.manifestUrl,
                        type = route.type,
                        catalogId = route.catalogId,
                        supportsPagination = route.supportsPagination,
                        genre = route.genre,
                        onBack = {
                            CatalogRepository.clear()
                            navController.popBackStack()
                        },
                        onPosterClick = { meta ->
                            navController.navigate(DetailRoute(type = meta.type, id = meta.id))
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            NuvioPosterActionSheet(
                item = selectedPosterForActions,
                isSaved = selectedPosterForActions?.let { preview ->
                    LibraryRepository.isSaved(preview.id)
                } == true,
                isWatched = selectedPosterForActions?.let { preview ->
                    watchedUiState.watchedKeys.contains(watchedItemKey(preview.type, preview.id))
                } == true,
                onDismiss = { selectedPosterForActions = null },
                onToggleLibrary = {
                    selectedPosterForActions?.let { preview ->
                        LibraryRepository.toggleSaved(preview.toLibraryItem(savedAtEpochMs = 0L))
                    }
                },
                onToggleWatched = {
                    selectedPosterForActions?.let { preview ->
                        WatchedRepository.toggleWatched(preview.toWatchedItem(markedAtEpochMs = 0L))
                    }
                },
            )
        }
}

@Composable
private fun AppTabHost(
    selectedTab: AppScreenTab,
    modifier: Modifier = Modifier,
    onCatalogClick: ((HomeCatalogSection) -> Unit)? = null,
    onPosterClick: ((MetaPreview) -> Unit)? = null,
    onPosterLongClick: ((MetaPreview) -> Unit)? = null,
    onLibraryPosterClick: ((LibraryItem) -> Unit)? = null,
    onContinueWatchingClick: ((ContinueWatchingItem) -> Unit)? = null,
    onContinueWatchingLongPress: ((ContinueWatchingItem) -> Unit)? = null,
) {
    Box(modifier = modifier.fillMaxSize()) {
        keepAliveTab(
            selected = selectedTab == AppScreenTab.Home,
        ) {
            HomeScreen(
                modifier = Modifier.fillMaxSize(),
                onCatalogClick = onCatalogClick,
                onPosterClick = onPosterClick,
                onPosterLongClick = onPosterLongClick,
                onContinueWatchingClick = onContinueWatchingClick,
                onContinueWatchingLongPress = onContinueWatchingLongPress,
            )
        }
        keepAliveTab(
            selected = selectedTab == AppScreenTab.Search,
        ) {
            SearchScreen(
                modifier = Modifier.fillMaxSize(),
                onPosterClick = onPosterClick,
                onPosterLongClick = onPosterLongClick,
            )
        }
        keepAliveTab(
            selected = selectedTab == AppScreenTab.Library,
        ) {
            LibraryScreen(
                modifier = Modifier.fillMaxSize(),
                onPosterClick = onLibraryPosterClick,
            )
        }
        keepAliveTab(
            selected = selectedTab == AppScreenTab.Settings,
        ) {
            SettingsScreen(
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun BoxScope.keepAliveTab(
    selected: Boolean,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(if (selected) 1f else 0f)
            .zIndex(if (selected) 1f else 0f),
    ) {
        content()
    }
}
