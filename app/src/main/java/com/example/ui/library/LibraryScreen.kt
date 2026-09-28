package com.example.ui.library

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import com.example.ui.common.ThreeLineVisualizer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import androidx.compose.ui.res.painterResource
import com.example.R
import com.example.data.model.PlayableTrack
import com.example.data.model.Song
import com.example.data.model.UserPlaylistSummary
import com.example.player.PlayerManager
import com.example.ui.common.AddToPlaylistBottomSheet
import com.example.ui.common.FourSongGridCover
import com.example.ui.common.SongRowItem
import com.example.ui.common.TrackOptionsBottomSheet

data class LibrarySquareBox(
    val tab: LibraryTab,
    val title: String,
    val subtitle: String,
    val count: Int,
    val icon: ImageVector,
    val startGradient: Color,
    val endGradient: Color,
    val iconTint: Color = Color.White
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    playerManager: PlayerManager,
    modifier: Modifier = Modifier,
    onNavigateToPlaylist: ((String) -> Unit)? = null
) {
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val likedSongs by viewModel.likedSongs.collectAsStateWithLifecycle()
    val downloadedSongs by viewModel.downloadedSongs.collectAsStateWithLifecycle()
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val playlistsWithPreviews by viewModel.playlistsWithPreviews.collectAsStateWithLifecycle()
    val localSongs by viewModel.localSongs.collectAsStateWithLifecycle()
    val isScanningLocal by viewModel.isScanningLocal.collectAsStateWithLifecycle()
    val currentTrack by playerManager.currentTrack.collectAsStateWithLifecycle()

    var showOverviewGrid by remember { mutableStateOf(true) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var showImportPlaylistSheet by remember { mutableStateOf(false) }
    var selectedTrackForOptions by remember { mutableStateOf<PlayableTrack?>(null) }
    var trackToAddToPlaylist by remember { mutableStateOf<PlayableTrack?>(null) }

    // Handle back navigation when inside a category view
    BackHandler(enabled = !showOverviewGrid) {
        showOverviewGrid = true
    }

    // Storage permission launcher for local audio
    val permissionToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.scanLocalMusic()
        }
    }

    val squareBoxes = listOf(
        LibrarySquareBox(
            tab = LibraryTab.LIKED,
            title = "Liked Songs",
            subtitle = if (likedSongs.isEmpty()) "Favorite tracks" else "${likedSongs.size} tracks",
            count = likedSongs.size,
            icon = Icons.Filled.Favorite,
            startGradient = Color(0xFFF43F5E), // Rose Pink
            endGradient = Color(0xFFBE123C)
        ),
        LibrarySquareBox(
            tab = LibraryTab.DOWNLOADS,
            title = "Downloads",
            subtitle = if (downloadedSongs.isEmpty()) "Offline music" else "${downloadedSongs.size} saved",
            count = downloadedSongs.size,
            icon = Icons.Filled.DownloadDone,
            startGradient = Color(0xFF10B981), // Emerald Green
            endGradient = Color(0xFF047857)
        ),
        LibrarySquareBox(
            tab = LibraryTab.PLAYLISTS,
            title = "Playlists",
            subtitle = if (playlists.isEmpty()) "Custom mixes" else "${playlists.size} playlists",
            count = playlists.size,
            icon = Icons.AutoMirrored.Filled.QueueMusic,
            startGradient = Color(0xFF8B5CF6), // Purple
            endGradient = Color(0xFF6D28D9)
        ),
        LibrarySquareBox(
            tab = LibraryTab.RECENT,
            title = "History",
            subtitle = if (recentlyPlayed.isEmpty()) "Recent listens" else "${recentlyPlayed.size} played",
            count = recentlyPlayed.size,
            icon = Icons.Filled.History,
            startGradient = Color(0xFFF59E0B), // Amber Orange
            endGradient = Color(0xFFB45309)
        ),
        LibrarySquareBox(
            tab = LibraryTab.LOCAL,
            title = "Local Files",
            subtitle = if (localSongs.isEmpty()) "Device storage" else "${localSongs.size} songs",
            count = localSongs.size,
            icon = Icons.Filled.FolderOpen,
            startGradient = Color(0xFF3B82F6), // Electric Blue
            endGradient = Color(0xFF1D4ED8)
        )
    )

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .statusBarsPadding()
                    .padding(top = 10.dp, bottom = 4.dp)
            ) {
                // Top Header Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!showOverviewGrid) {
                            IconButton(
                                onClick = { showOverviewGrid = true },
                                modifier = Modifier
                                    .padding(end = 4.dp)
                                    .size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back to Library Grid"
                                )
                            }
                        }

                        Text(
                            text = if (showOverviewGrid) "Your Library" else when (selectedTab) {
                                LibraryTab.LIKED -> "Liked Songs"
                                LibraryTab.DOWNLOADS -> "Downloads"
                                LibraryTab.PLAYLISTS -> "Playlists"
                                LibraryTab.RECENT -> "History"
                                LibraryTab.LOCAL -> "Local Files"
                            },
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 22.sp
                            )
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Quick switch to Grid toggle
                        if (!showOverviewGrid) {
                            IconButton(
                                onClick = { showOverviewGrid = true },
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GridView,
                                    contentDescription = "Grid View",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // Import JioSaavn Playlist button
                        IconButton(
                            onClick = { showImportPlaylistSheet = true },
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .testTag("import_playlist_button")
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_jiosaavn),
                                contentDescription = "Import JioSaavn Playlist",
                                tint = Color.Unspecified,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Create playlist button
                        IconButton(
                            onClick = { showCreatePlaylistDialog = true },
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .testTag("create_playlist_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Create Playlist",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // If inside category list, show horizontal filter tags
                if (!showOverviewGrid) {
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(squareBoxes, key = { it.tab.name }) { box ->
                            val isSelected = selectedTab == box.tab
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    viewModel.setTab(box.tab)
                                    if (box.tab == LibraryTab.LOCAL) {
                                        permissionLauncher.launch(permissionToRequest)
                                    }
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = box.icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                },
                                label = {
                                    Text(
                                        text = box.title,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 12.sp
                                        )
                                    )
                                },
                                shape = RoundedCornerShape(16.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = box.startGradient,
                                    selectedLabelColor = Color.White,
                                    selectedLeadingIconColor = Color.White,
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                    labelColor = MaterialTheme.colorScheme.onSurface,
                                    iconColor = box.startGradient
                                ),
                                border = null,
                                modifier = Modifier.height(34.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = showOverviewGrid,
                transitionSpec = {
                    fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMedium)) togetherWith
                    fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMedium))
                },
                label = "library_content_anim"
            ) { isGridOverview ->
                if (isGridOverview) {
                    // Sleek Material 3 Horizontal Expensive Style List Layout
                    LazyColumn(
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 6.dp,
                            bottom = 165.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Featured JioSaavn Import Playlist Square Card
                        item(key = "jiosaavn_import_square_card") {
                            JioSaavnImportSquareCard(
                                onClick = { showImportPlaylistSheet = true },
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                        }

                        items(squareBoxes, key = { it.title }) { box ->
                            LibraryHorizontalItemCard(
                                box = box,
                                onClick = {
                                    viewModel.setTab(box.tab)
                                    showOverviewGrid = false
                                    if (box.tab == LibraryTab.LOCAL && localSongs.isEmpty()) {
                                        permissionLauncher.launch(permissionToRequest)
                                    }
                                }
                            )
                        }

                        // Recently Played Quick List below Horizontal Items if available
                        if (recentlyPlayed.isNotEmpty()) {
                            item {
                                Column(modifier = Modifier.padding(top = 16.dp, bottom = 6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Recently Played",
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 18.sp
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )

                                        TextButton(
                                            onClick = {
                                                viewModel.setTab(LibraryTab.RECENT)
                                                showOverviewGrid = false
                                            }
                                        ) {
                                            Text(
                                                text = "See All",
                                                color = MaterialTheme.colorScheme.primary,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }

                            items(recentlyPlayed.take(4), key = { "rec_${it.id}" }) { track ->
                                SongRowItem(
                                    song = Song(
                                        id = track.id,
                                        title = track.title,
                                        artist = track.artist,
                                        album = track.album,
                                        albumId = track.albumId,
                                        artwork = track.artwork,
                                        duration = track.duration,
                                        streamUrl = track.streamUrl
                                    ),
                                    isPlaying = currentTrack?.id == track.id,
                                    onClick = { viewModel.playTrack(track, recentlyPlayed) },
                                    onMoreClick = { selectedTrackForOptions = track }
                                )
                            }
                        }
                    }
                } else {
                    // Category Songs / Playlists View
                    when (selectedTab) {
                        LibraryTab.LIKED -> {
                            TrackListSection(
                                tracks = likedSongs,
                                currentPlayingId = currentTrack?.id,
                                emptyTitle = "No Liked Songs",
                                emptyMessage = "Tap the heart icon on any song or album to save it to your library!",
                                emptyIcon = Icons.Outlined.FavoriteBorder,
                                emptyAccentColor = Color(0xFFF43F5E),
                                onPlayTrack = { track -> viewModel.playTrack(track, likedSongs) },
                                onPlayAll = { viewModel.playAll(likedSongs) },
                                onShuffleAll = { viewModel.shuffleAll(likedSongs) },
                                onMoreClick = { selectedTrackForOptions = it }
                            )
                        }
                        LibraryTab.DOWNLOADS -> {
                            val activeDownloads by viewModel.activeDownloads.collectAsStateWithLifecycle()
                            DownloadsSection(
                                downloadedTracks = downloadedSongs,
                                activeDownloads = activeDownloads,
                                currentPlayingId = currentTrack?.id,
                                onPlayTrack = { track -> viewModel.playTrack(track, downloadedSongs) },
                                onPlayAll = { viewModel.playAll(downloadedSongs) },
                                onShuffleAll = { viewModel.shuffleAll(downloadedSongs) },
                                onCancelDownload = { songId -> viewModel.cancelDownload(songId) },
                                onMoreClick = { selectedTrackForOptions = it }
                            )
                        }
                        LibraryTab.RECENT -> {
                            TrackListSection(
                                tracks = recentlyPlayed,
                                currentPlayingId = currentTrack?.id,
                                emptyTitle = "No History Yet",
                                emptyMessage = "Your recently played songs will automatically appear here as you listen!",
                                emptyIcon = Icons.Outlined.History,
                                emptyAccentColor = Color(0xFFF59E0B),
                                onPlayTrack = { track -> viewModel.playTrack(track, recentlyPlayed) },
                                onPlayAll = { viewModel.playAll(recentlyPlayed) },
                                onShuffleAll = { viewModel.shuffleAll(recentlyPlayed) },
                                onMoreClick = { selectedTrackForOptions = it }
                            )
                        }
                        LibraryTab.LOCAL -> {
                            if (isScanningLocal) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                }
                            } else {
                                TrackListSection(
                                    tracks = localSongs,
                                    currentPlayingId = currentTrack?.id,
                                    emptyTitle = "No Local Music Found",
                                    emptyMessage = "Scan your device storage to listen to offline MP3 and audio files.",
                                    emptyIcon = Icons.Outlined.FolderOpen,
                                    emptyAccentColor = Color(0xFF3B82F6),
                                    actionText = "Scan Storage",
                                    onAction = { permissionLauncher.launch(permissionToRequest) },
                                    onPlayTrack = { track -> viewModel.playTrack(track, localSongs) },
                                    onPlayAll = { viewModel.playAll(localSongs) },
                                    onShuffleAll = { viewModel.shuffleAll(localSongs) },
                                    onMoreClick = { selectedTrackForOptions = it }
                                )
                            }
                        }
                        LibraryTab.PLAYLISTS -> {
                            if (playlistsWithPreviews.isEmpty()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    JioSaavnImportSquareCard(
                                        onClick = { showImportPlaylistSheet = true },
                                        modifier = Modifier.padding(bottom = 20.dp)
                                    )

                                    EmptyLibraryState(
                                        title = "Create Your First Playlist",
                                        message = "Organize songs by your mood, favorite artists, or import playlists directly from JioSaavn.",
                                        icon = Icons.Outlined.QueueMusic,
                                        accentColor = Color(0xFF8B5CF6),
                                        actionText = "Create Playlist",
                                        onAction = { showCreatePlaylistDialog = true }
                                    )
                                }
                            } else {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${playlistsWithPreviews.size} ${if (playlistsWithPreviews.size == 1) "Playlist" else "Playlists"}",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )

                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            FilledTonalButton(
                                                onClick = { showImportPlaylistSheet = true },
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                                shape = RoundedCornerShape(20.dp)
                                            ) {
                                                Icon(
                                                    painter = painterResource(id = R.drawable.ic_jiosaavn),
                                                    contentDescription = null,
                                                    tint = Color.Unspecified,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Import", fontWeight = FontWeight.SemiBold)
                                            }

                                            FilledTonalButton(
                                                onClick = { showCreatePlaylistDialog = true },
                                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                                shape = RoundedCornerShape(20.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Add,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("New Playlist", fontWeight = FontWeight.SemiBold)
                                            }
                                        }
                                    }

                                    LazyVerticalGrid(
                                        columns = GridCells.Fixed(2),
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(
                                            start = 14.dp,
                                            end = 14.dp,
                                            top = 6.dp,
                                            bottom = 165.dp
                                        ),
                                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        // JioSaavn Import Square Card inside Playlists Grid
                                        item(key = "import_jiosaavn_playlist_grid_card") {
                                            JioSaavnImportGridSquareCard(
                                                onClick = { showImportPlaylistSheet = true }
                                            )
                                        }

                                        items(playlistsWithPreviews, key = { it.id }) { playlist ->
                                            PlaylistGridCard(
                                                playlist = playlist,
                                                onClick = { onNavigateToPlaylist?.invoke("local_${playlist.id}") },
                                                onDelete = { viewModel.deletePlaylist(playlist.id) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreatePlaylistDialog) {
        var playlistName by remember { mutableStateOf("") }
        var playlistDesc by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            title = { Text("New Playlist", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = playlistName,
                        onValueChange = { playlistName = it },
                        label = { Text("Playlist Name") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = playlistDesc,
                        onValueChange = { playlistDesc = it },
                        label = { Text("Description (Optional)") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (playlistName.isNotBlank()) {
                            viewModel.createPlaylist(playlistName.trim(), playlistDesc.trim())
                            showCreatePlaylistDialog = false
                        }
                    },
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylistDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    selectedTrackForOptions?.let { track ->
        TrackOptionsBottomSheet(
            track = track,
            isLiked = selectedTab == LibraryTab.LIKED,
            onDismiss = { selectedTrackForOptions = null },
            onPlayNow = { viewModel.playTrack(track) },
            onPlayNext = { viewModel.playNext(track) },
            onAddToQueue = { viewModel.addToQueue(track) },
            onToggleLike = { viewModel.toggleLike(track) },
            onAddToPlaylist = { trackToAddToPlaylist = track }
        )
    }

    trackToAddToPlaylist?.let { track ->
        AddToPlaylistBottomSheet(
            track = track,
            repository = viewModel.repository,
            onDismiss = { trackToAddToPlaylist = null }
        )
    }

    if (showImportPlaylistSheet) {
        ImportPlaylistDialog(
            viewModel = viewModel,
            onDismiss = { showImportPlaylistSheet = false },
            onNavigateToPlaylist = onNavigateToPlaylist
        )
    }
}

@Composable
fun PlaylistGridCard(
    playlist: UserPlaylistSummary,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.10f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Large 4-Song Grid Mosaic Cover (Zero Black Box)
            FourSongGridCover(
                artworks = playlist.previewArtworks,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                cornerRadius = 14.dp,
                fallbackTitle = playlist.name
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = playlist.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${playlist.songCount} ${if (playlist.songCount == 1) "song" else "songs"}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        maxLines = 1
                    )
                }

                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Open Playlist") },
                            leadingIcon = {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                            },
                            onClick = {
                                showMenu = false
                                onClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete Playlist", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                showMenu = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LibraryHorizontalItemCard(
    box: LibrarySquareBox,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // M3 Expressive Asymmetrical Shape: Large start rounding (topStart 28.dp) & Large end rounding (bottomEnd 28.dp)
    val expressiveCardShape = RoundedCornerShape(
        topStart = 28.dp,
        bottomStart = 12.dp,
        topEnd = 12.dp,
        bottomEnd = 28.dp
    )

    Surface(
        onClick = onClick,
        shape = expressiveCardShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        ),
        tonalElevation = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("library_item_${box.tab.name}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Square Icon Container (Original M3 Theme, No Multi-Color)
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                ),
                modifier = Modifier.size(46.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = box.icon,
                        contentDescription = box.title,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // 2 Distinct Lines: Title (Line 1) and Subtitle (Line 2)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = box.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = box.subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Right Count Badge & Chevron Arrow
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (box.count > 0) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest
                    ) {
                        Text(
                            text = "${box.count}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}

@Composable
fun LargeSquareBoxCard(
    box: LibrarySquareBox,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    LibraryHorizontalItemCard(box = box, onClick = onClick, modifier = modifier)
}

@Composable
fun TrackListSection(
    tracks: List<PlayableTrack>,
    currentPlayingId: String?,
    emptyTitle: String,
    emptyMessage: String,
    emptyIcon: ImageVector,
    emptyAccentColor: Color,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    onPlayTrack: (PlayableTrack) -> Unit,
    onPlayAll: () -> Unit,
    onShuffleAll: () -> Unit,
    onMoreClick: (PlayableTrack) -> Unit
) {
    if (tracks.isEmpty()) {
        EmptyLibraryState(
            title = emptyTitle,
            message = emptyMessage,
            icon = emptyIcon,
            accentColor = emptyAccentColor,
            actionText = actionText,
            onAction = onAction
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 165.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onPlayAll,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Play All", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }

                    FilledTonalButton(
                        onClick = onShuffleAll,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Shuffle", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            items(tracks, key = { it.id }) { track ->
                SongRowItem(
                    song = Song(
                        id = track.id,
                        title = track.title,
                        artist = track.artist,
                        album = track.album,
                        albumId = track.albumId,
                        artwork = track.artwork,
                        duration = track.duration,
                        streamUrl = track.streamUrl
                    ),
                    isPlaying = currentPlayingId == track.id,
                    onClick = { onPlayTrack(track) },
                    onMoreClick = { onMoreClick(track) }
                )
            }
        }
    }
}

@Composable
fun EmptyLibraryState(
    title: String,
    message: String,
    icon: ImageVector,
    accentColor: Color,
    actionText: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Expressive M3 Floating Icon Plate
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(accentColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(42.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            ),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            ),
            modifier = Modifier.padding(horizontal = 20.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        if (actionText != null && onAction != null) {
            Spacer(modifier = Modifier.height(18.dp))
            Button(
                onClick = onAction,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.height(42.dp)
            ) {
                Text(actionText, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun DownloadsSection(
    downloadedTracks: List<PlayableTrack>,
    activeDownloads: Map<String, com.example.download.DownloadProgressState>,
    currentPlayingId: String?,
    onPlayTrack: (PlayableTrack) -> Unit,
    onPlayAll: () -> Unit,
    onShuffleAll: () -> Unit,
    onCancelDownload: (String) -> Unit,
    onMoreClick: (PlayableTrack) -> Unit
) {
    if (downloadedTracks.isEmpty() && activeDownloads.isEmpty()) {
        EmptyLibraryState(
            title = "No Offline Downloads",
            message = "Download your favorite tracks to listen anytime 100% offline without using mobile data.",
            icon = Icons.Outlined.Download,
            accentColor = Color(0xFF10B981)
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 165.dp)
        ) {
            // Storage Summary & Action Buttons Header
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    val totalBytes = downloadedTracks.sumOf { it.fileSize }
                    val totalSizeMb = if (totalBytes > 0) "%.1f MB".format(totalBytes.toDouble() / (1024 * 1024)) else "Offline Available"

                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color(0xFF10B981).copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.OfflinePin,
                                    contentDescription = null,
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "${downloadedTracks.size} Offline Songs",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "$totalSizeMb used • High Quality 320 kbps",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 11.sp
                                        )
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (downloadedTracks.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = onPlayAll,
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                            ) {
                                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Play All", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }

                            FilledTonalButton(
                                onClick = onShuffleAll,
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Shuffle", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            // In-Progress Downloads Section
            if (activeDownloads.isNotEmpty()) {
                item {
                    Text(
                        text = "Downloading (${activeDownloads.size})",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        ),
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                items(activeDownloads.values.toList(), key = { "dl_${it.songId}" }) { task ->
                    ActiveDownloadItemCard(
                        task = task,
                        onCancel = { onCancelDownload(task.songId) }
                    )
                }
            }

            // Completed Downloaded Songs
            if (downloadedTracks.isNotEmpty()) {
                if (activeDownloads.isNotEmpty()) {
                    item {
                        Text(
                            text = "Downloaded",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            ),
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                items(downloadedTracks, key = { "saved_${it.id}" }) { track ->
                    DownloadedSongRowItem(
                        track = track,
                        isPlaying = currentPlayingId == track.id,
                        onClick = { onPlayTrack(track) },
                        onMoreClick = { onMoreClick(track) }
                    )
                }
            }
        }
    }
}

@Composable
fun ActiveDownloadItemCard(
    task: com.example.download.DownloadProgressState,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progressFraction = (task.progress / 100f).coerceIn(0f, 1f)
    val sizeText = if (task.totalBytes > 0) {
        val dlMb = task.bytesDownloaded.toDouble() / (1024 * 1024)
        val totMb = task.totalBytes.toDouble() / (1024 * 1024)
        "%.1f / %.1f MB".format(dlMb, totMb)
    } else {
        "${task.progress}%"
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .testTag("active_download_${task.songId}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    AsyncImage(
                        model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                            .data(task.artwork)
                            .crossfade(true)
                            .build(),
                        contentDescription = task.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${task.artist} • $sizeText",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                IconButton(
                    onClick = onCancel,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel Download",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                LinearProgressIndicator(
                    progress = { progressFraction },
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "${task.progress}%",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp
                    )
                )
            }
        }
    }
}

@Composable
fun DownloadedSongRowItem(
    track: PlayableTrack,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        color = if (isPlaying) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else Color.Transparent,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("downloaded_song_${track.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                AsyncImage(
                    model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                        .data(track.artwork)
                        .crossfade(true)
                        .build(),
                    contentDescription = track.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                if (isPlaying) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center
                    ) {
                        ThreeLineVisualizer(
                            isPlaying = true,
                            color = MaterialTheme.colorScheme.primary,
                            barWidth = 3.dp,
                            maxHeight = 20.dp
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(2.dp)
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Offline Available",
                            tint = Color.White,
                            modifier = Modifier.size(10.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(2.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFF10B981).copy(alpha = 0.15f),
                        modifier = Modifier.padding(end = 6.dp)
                    ) {
                        Text(
                            text = "320 KBPS",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF10B981)
                            ),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }

                    val subtitle = buildString {
                        append(track.artist)
                        if (track.fileSizeFormatted.isNotBlank()) {
                            append(" • ")
                            append(track.fileSizeFormatted)
                        }
                        if (track.durationFormatted.isNotBlank() && track.durationFormatted != "0:00") {
                            append(" • ")
                            append(track.durationFormatted)
                        }
                    }

                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(
                onClick = onMoreClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
