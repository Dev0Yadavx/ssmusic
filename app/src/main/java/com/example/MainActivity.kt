package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.player.PlayerManager
import com.example.repository.MusicRepository
import com.example.ui.details.AlbumDetailScreen
import com.example.ui.details.ArtistDetailScreen
import com.example.ui.details.PlaylistDetailScreen
import com.example.ui.home.HomeScreen
import com.example.ui.home.HomeViewModel
import com.example.ui.library.LibraryScreen
import com.example.ui.library.LibraryViewModel
import com.example.ui.player.MiniPlayer
import com.example.ui.player.NowPlayingModal
import com.example.ui.search.SearchScreen
import com.example.ui.search.SearchViewModel
import com.example.ui.settings.SettingsScreen
import com.example.ui.theme.AccentPalette
import com.example.ui.theme.SMusicTheme
import com.example.ui.theme.ThemeManager
import com.example.ui.theme.ThemeMode

enum class RootScreen {
    HOME, SEARCH, LIBRARY
}

sealed class SubScreen {
    object None : SubScreen()
    object Settings : SubScreen()
    data class AlbumDetail(
        val id: String,
        val title: String = "",
        val artist: String = "",
        val artwork: String = ""
    ) : SubScreen()
    data class PlaylistDetail(val id: String) : SubScreen()
    data class ArtistDetail(val id: String) : SubScreen()
}

class MainActivity : ComponentActivity() {

    private val homeViewModel: HomeViewModel by viewModels()
    private val searchViewModel: SearchViewModel by viewModels()
    private val libraryViewModel: LibraryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val playerManager = PlayerManager.getInstance(applicationContext)
        val repository = MusicRepository(applicationContext)

        val themeManager = ThemeManager.getInstance(applicationContext)

        setContent {
            val themeMode by themeManager.themeMode.collectAsState()
            val useDynamicColor by themeManager.useDynamicColor.collectAsState()
            val isAmoledBlack by themeManager.isAmoledBlack.collectAsState()
            val accentPalette by themeManager.accentPalette.collectAsState()
            val fontOption by themeManager.fontOption.collectAsState()

            SMusicTheme(
                themeMode = themeMode,
                dynamicColor = useDynamicColor,
                isAmoledBlack = isAmoledBlack,
                accentPalette = accentPalette,
                fontOption = fontOption
            ) {
                // Runtime Notification Permission on Android 13+ (Pixel lockscreen / media notification support)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val notifPermissionLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestPermission()
                    ) { /* Handled */ }

                    LaunchedEffect(Unit) {
                        if (ContextCompat.checkSelfPermission(
                                this@MainActivity,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                }

                var currentRootScreen by remember { mutableStateOf(RootScreen.HOME) }
                var currentSubScreen by remember { mutableStateOf<SubScreen>(SubScreen.None) }
                var isNowPlayingOpen by remember { mutableStateOf(false) }

                val currentTrack by playerManager.currentTrack.collectAsState()

                // Intercept back button when Now Playing or Subscreen is open
                BackHandler(enabled = isNowPlayingOpen || currentSubScreen !is SubScreen.None) {
                    if (isNowPlayingOpen) {
                        isNowPlayingOpen = false
                    } else if (currentSubScreen !is SubScreen.None) {
                        currentSubScreen = SubScreen.None
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    // Full Screen Content Layer: Extends fully to screen edges underneath floating controls
                    Box(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        when (val sub = currentSubScreen) {
                            is SubScreen.Settings -> {
                                SettingsScreen(
                                    repository = repository,
                                    playerManager = playerManager,
                                    themeManager = themeManager,
                                    onBack = { currentSubScreen = SubScreen.None }
                                )
                            }
                            is SubScreen.AlbumDetail -> {
                                AlbumDetailScreen(
                                    albumId = sub.id,
                                    initialTitle = sub.title,
                                    initialArtist = sub.artist,
                                    initialArtwork = sub.artwork,
                                    repository = repository,
                                    playerManager = playerManager,
                                    onBack = { currentSubScreen = SubScreen.None }
                                )
                            }
                            is SubScreen.PlaylistDetail -> {
                                PlaylistDetailScreen(
                                    playlistId = sub.id,
                                    repository = repository,
                                    playerManager = playerManager,
                                    onBack = { currentSubScreen = SubScreen.None }
                                )
                            }
                            is SubScreen.ArtistDetail -> {
                                ArtistDetailScreen(
                                    artistId = sub.id,
                                    repository = repository,
                                    playerManager = playerManager,
                                    onNavigateToAlbum = { albumId -> 
                                        currentSubScreen = SubScreen.AlbumDetail(id = albumId) 
                                    },
                                    onBack = { currentSubScreen = SubScreen.None }
                                )
                            }
                            SubScreen.None -> {
                                when (currentRootScreen) {
                                    RootScreen.HOME -> {
                                        HomeScreen(
                                            viewModel = homeViewModel,
                                            playerManager = playerManager,
                                            onNavigateToSearch = { currentRootScreen = RootScreen.SEARCH },
                                            onNavigateToSettings = { currentSubScreen = SubScreen.Settings },
                                            onNavigateToAlbum = { album ->
                                                currentSubScreen = SubScreen.AlbumDetail(
                                                    id = album.id,
                                                    title = album.title,
                                                    artist = album.artist,
                                                    artwork = album.artwork
                                                )
                                            },
                                            onNavigateToPlaylist = { currentSubScreen = SubScreen.PlaylistDetail(it) },
                                            onNavigateToArtist = { currentSubScreen = SubScreen.ArtistDetail(it) }
                                        )
                                    }
                                    RootScreen.SEARCH -> {
                                        SearchScreen(
                                            viewModel = searchViewModel,
                                            playerManager = playerManager,
                                            onNavigateToAlbum = { album ->
                                                currentSubScreen = SubScreen.AlbumDetail(
                                                    id = album.id,
                                                    title = album.title,
                                                    artist = album.artist,
                                                    artwork = album.artwork
                                                )
                                            },
                                            onNavigateToPlaylist = { currentSubScreen = SubScreen.PlaylistDetail(it) },
                                            onNavigateToArtist = { currentSubScreen = SubScreen.ArtistDetail(it) }
                                        )
                                    }
                                    RootScreen.LIBRARY -> {
                                        LibraryScreen(
                                            viewModel = libraryViewModel,
                                            playerManager = playerManager,
                                            onNavigateToPlaylist = { currentSubScreen = SubScreen.PlaylistDetail(it) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Full Mask Gradient Scrim (Smooth fade overlay so scrolling content effortlessly blends under floating controls)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(190.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        MaterialTheme.colorScheme.background.copy(alpha = 0.40f),
                                        MaterialTheme.colorScheme.background.copy(alpha = 0.85f),
                                        MaterialTheme.colorScheme.background.copy(alpha = 0.98f)
                                    )
                                )
                            )
                    )

                    // Floating Column containing Full Rounded Mini Player and FAB-style Navigation Bar
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Full Rounded Floating Mini Player
                        AnimatedVisibility(
                            visible = currentTrack != null && !isNowPlayingOpen,
                            enter = slideInVertically(initialOffsetY = { it / 2 }) + expandVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                            exit = slideOutVertically(targetOffsetY = { it / 2 }) + shrinkVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut()
                        ) {
                            Surface(
                                shape = RoundedCornerShape(24.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
                                border = androidx.compose.foundation.BorderStroke(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                ),
                                tonalElevation = 6.dp,
                                shadowElevation = 10.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("floating_mini_player_container")
                            ) {
                                MiniPlayer(
                                    playerManager = playerManager,
                                    repository = repository,
                                    onClick = { isNowPlayingOpen = true }
                                )
                            }
                        }

                        // Full Rounded FAB Style Navigation Bar
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f),
                            border = androidx.compose.foundation.BorderStroke(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                            ),
                            tonalElevation = 8.dp,
                            shadowElevation = 12.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("floating_bottom_bar_container")
                        ) {
                            RoundedAnimatedBottomBar(
                                currentRootScreen = currentRootScreen,
                                isSubScreenOpen = currentSubScreen !is SubScreen.None,
                                onSelectTab = { screen ->
                                    currentRootScreen = screen
                                    currentSubScreen = SubScreen.None
                                }
                            )
                        }
                    }

                    // Full Screen Now Playing Modal (Slide in from bottom, covers root)
                    AnimatedVisibility(
                        visible = isNowPlayingOpen && currentTrack != null,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        NowPlayingModal(
                            playerManager = playerManager,
                            repository = repository,
                            onDismiss = { isNowPlayingOpen = false },
                            onViewAlbum = { albumId ->
                                isNowPlayingOpen = false
                                currentSubScreen = SubScreen.AlbumDetail(albumId)
                            },
                            onViewArtist = { artistId ->
                                isNowPlayingOpen = false
                                currentSubScreen = SubScreen.ArtistDetail(artistId)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RoundedAnimatedBottomBar(
    currentRootScreen: RootScreen,
    isSubScreenOpen: Boolean,
    onSelectTab: (RootScreen) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag("bottom_nav_bar"),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnimatedNavTabItem(
            selected = currentRootScreen == RootScreen.HOME && !isSubScreenOpen,
            selectedIcon = Icons.Rounded.Home,
            unselectedIcon = Icons.Rounded.Home,
            label = "Home",
            onClick = { onSelectTab(RootScreen.HOME) },
            testTag = "nav_item_home"
        )

        AnimatedNavTabItem(
            selected = currentRootScreen == RootScreen.SEARCH && !isSubScreenOpen,
            selectedIcon = Icons.Rounded.Search,
            unselectedIcon = Icons.Rounded.Search,
            label = "Search",
            onClick = { onSelectTab(RootScreen.SEARCH) },
            testTag = "nav_item_search"
        )

        AnimatedNavTabItem(
            selected = currentRootScreen == RootScreen.LIBRARY && !isSubScreenOpen,
            selectedIcon = Icons.Rounded.LibraryMusic,
            unselectedIcon = Icons.Rounded.LibraryMusic,
            label = "Library",
            onClick = { onSelectTab(RootScreen.LIBRARY) },
            testTag = "nav_item_library"
        )
    }
}

@Composable
fun AnimatedNavTabItem(
    selected: Boolean,
    selectedIcon: ImageVector,
    unselectedIcon: ImageVector,
    label: String,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val scaleAnim by animateFloatAsState(
        targetValue = if (selected) 1.12f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "tab_scale"
    )

    val iconColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(220),
        label = "tab_color"
    )

    val labelColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(220),
        label = "tab_label_color"
    )

    val pillScaleAnim by animateFloatAsState(
        targetValue = if (selected) 1.0f else 0.6f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "pill_scale"
    )

    val pillAlphaAnim by animateFloatAsState(
        targetValue = if (selected) 1.0f else 0.0f,
        animationSpec = tween(200),
        label = "pill_alpha"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .testTag(testTag)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(bottom = 2.dp)
        ) {
            // Animated Pill Indicator background (full rounded FAB pill)
            if (pillAlphaAnim > 0.01f) {
                Box(
                    modifier = Modifier
                        .size(width = 58.dp, height = 30.dp)
                        .scale(pillScaleAnim)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = pillAlphaAnim))
                )
            }

            // Animated Icon with bounce and morph
            Icon(
                imageVector = if (selected) selectedIcon else unselectedIcon,
                contentDescription = label,
                tint = iconColor,
                modifier = Modifier
                    .size(23.dp)
                    .scale(scaleAnim)
            )
        }

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                fontSize = 11.5.sp,
                color = labelColor
            )
        )
    }
}
