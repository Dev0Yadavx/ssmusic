package com.example.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.model.*
import com.example.player.PlayerManager
import com.example.ui.common.*
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    playerManager: PlayerManager,
    onNavigateToSearch: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToAlbum: (Album) -> Unit,
    onNavigateToPlaylist: (String) -> Unit,
    onNavigateToArtist: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val currentPlayingTrack by playerManager.currentTrack.collectAsStateWithLifecycle()
    val pullRefreshState = rememberPullToRefreshState()

    var selectedTrackForOptions by remember { mutableStateOf<PlayableTrack?>(null) }
    var isTrackLiked by remember { mutableStateOf(false) }

    // Dynamic Time-Based Greeting with iOS-Style Aesthetic Emojis
    val greeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when (hour) {
            in 4..11 -> "Good morning 🌅✨"
            in 12..16 -> "Good afternoon ☀️🎵"
            in 17..21 -> "Good evening 🌆✨"
            else -> "Good night 🌌🎧"
        }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { viewModel.refreshHomeData() },
        state = pullRefreshState,
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        indicator = {
            if (isRefreshing) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 114.dp)
                        .align(Alignment.TopCenter)
                ) {
                    LoadingIndicator()
                }
            } else {
                PullToRefreshDefaults.Indicator(
                    state = pullRefreshState,
                    isRefreshing = isRefreshing,
                    modifier = Modifier
                        .padding(top = 114.dp)
                        .align(Alignment.TopCenter)
                )
            }
        }
    ) {
        // Main Scrollable Body
        when (val state = uiState) {
            is HomeUiState.Loading -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 110.dp)
                ) {
                    repeat(3) {
                        ShelfSkeleton()
                    }
                }
            }
            is HomeUiState.Error -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp)
                        .padding(top = 110.dp, bottom = 120.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CloudOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Something went wrong",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { viewModel.loadHomeData() },
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.testTag("retry_button")
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Retry")
                    }
                }
            }
            is HomeUiState.Success -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 112.dp, bottom = 165.dp) // Leave space for fixed header and floating controls over mask
                ) {
                    items(state.shelves, key = { it.id }) { shelf ->
                        HomeShelfSection(
                            shelf = shelf,
                            currentPlayingId = currentPlayingTrack?.id,
                            onPlaySong = { song, shelfSongs ->
                                viewModel.playTrack(song.toPlayableTrack(), shelfSongs.map { it.toPlayableTrack() })
                            },
                            onMoreSong = { song ->
                                selectedTrackForOptions = song.toPlayableTrack()
                            },
                            onAlbumClick = { album ->
                                if (album.id.isNotBlank()) onNavigateToAlbum(album)
                            },
                            onPlaylistClick = { playlist ->
                                if (playlist.id.isNotBlank()) onNavigateToPlaylist(playlist.id)
                            },
                            onArtistClick = { artist ->
                                if (artist.id.isNotBlank()) onNavigateToArtist(artist.id)
                            }
                        )
                    }
                }
            }
        }

        // Fixed Sticky Header with iOS Aesthetic & Gradient Mask Blur Effect
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.background.copy(alpha = 0.98f),
                                MaterialTheme.colorScheme.background.copy(alpha = 0.92f),
                                MaterialTheme.colorScheme.background.copy(alpha = 0.75f)
                            )
                        )
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = greeting,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "SMusic",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.tertiary
                                    )
                                )
                            )
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        IconButton(
                            onClick = onNavigateToSearch,
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
                                .testTag("home_search_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(
                            onClick = onNavigateToSettings,
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
                                .testTag("home_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = "Settings",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // Gradient Mask Fade Effect (Songs smoothly fade under header as they scroll)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(22.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.background.copy(alpha = 0.75f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }
    }

    var trackForAddToPlaylist by remember { mutableStateOf<PlayableTrack?>(null) }

    selectedTrackForOptions?.let { track ->
        TrackOptionsBottomSheet(
            track = track,
            isLiked = isTrackLiked,
            onDismiss = { selectedTrackForOptions = null },
            onPlayNow = { viewModel.playTrack(track) },
            onPlayNext = { viewModel.playNext(track) },
            onAddToQueue = { viewModel.addToQueue(track) },
            onToggleLike = {
                viewModel.toggleLike(track)
                isTrackLiked = !isTrackLiked
            },
            onAddToPlaylist = {
                trackForAddToPlaylist = track
            },
            onViewAlbum = if (track.albumId.isNotBlank()) {
                { onNavigateToAlbum(Album(id = track.albumId, title = track.album, artist = track.artist, artwork = track.artwork)) }
            } else null
        )
    }

    trackForAddToPlaylist?.let { track ->
        AddToPlaylistBottomSheet(
            track = track,
            repository = viewModel.repository,
            onDismiss = { trackForAddToPlaylist = null }
        )
    }
}

@Composable
fun HomeShelfSection(
    shelf: MusicShelf,
    currentPlayingId: String?,
    onPlaySong: (Song, List<Song>) -> Unit,
    onMoreSong: (Song) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onArtistClick: (Artist) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        // Clean Bold Category Header without small cluttered text
        Box(modifier = Modifier.padding(horizontal = 20.dp)) {
            Text(
                text = shelf.title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        when (shelf.type) {
            ShelfType.SONG_HORIZONTAL -> {
                val songs = shelf.items.mapNotNull { if (it is ShelfItem.SongItem) it.song else null }
                val chunks = songs.chunked(4)
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(chunks) { colSongs ->
                        Column(
                            modifier = Modifier.width(300.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            colSongs.forEach { song ->
                                QuickPickRowItem(
                                    song = song,
                                    isPlaying = currentPlayingId == song.id,
                                    onClick = { onPlaySong(song, songs) },
                                    onMoreClick = { onMoreSong(song) }
                                )
                            }
                        }
                    }
                }
            }
            ShelfType.ARTIST_HORIZONTAL -> {
                val artists = shelf.items.mapNotNull { if (it is ShelfItem.ArtistItem) it.artist else null }
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(artists, key = { it.id }) { artist ->
                        ArtistCard(
                            artist = artist,
                            onClick = { onArtistClick(artist) }
                        )
                    }
                }
            }
            ShelfType.ALBUM_HORIZONTAL -> {
                val albums = shelf.items.mapNotNull { if (it is ShelfItem.AlbumItem) it.album else null }
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(albums, key = { it.id }) { album ->
                        AlbumCard(
                            album = album,
                            onClick = { onAlbumClick(album) }
                        )
                    }
                }
            }
            ShelfType.PLAYLIST_HORIZONTAL -> {
                val playlists = shelf.items.mapNotNull { if (it is ShelfItem.PlaylistItem) it.playlist else null }
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(playlists, key = { it.id }) { playlist ->
                        PlaylistCard(
                            playlist = playlist,
                            onClick = { onPlaylistClick(playlist) }
                        )
                    }
                }
            }
            else -> {
                // Fallback row
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(shelf.items) { item ->
                        when (item) {
                            is ShelfItem.SongItem -> {
                                AlbumCard(
                                    album = Album(id = item.song.id, title = item.song.title, artist = item.song.artist, artwork = item.song.artwork),
                                    onClick = { onPlaySong(item.song, emptyList()) }
                                )
                            }
                            is ShelfItem.AlbumItem -> AlbumCard(album = item.album, onClick = { onAlbumClick(item.album) })
                            is ShelfItem.PlaylistItem -> PlaylistCard(playlist = item.playlist, onClick = { onPlaylistClick(item.playlist) })
                            is ShelfItem.ArtistItem -> ArtistCard(artist = item.artist, onClick = { onArtistClick(item.artist) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QuickPickRowItem(
    song: Song,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isPlaying) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else Color.Transparent,
        modifier = modifier
            .fillMaxWidth()
            .testTag("quick_pick_item_${song.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                AsyncImage(
                    model = song.artwork,
                    contentDescription = song.title,
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
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onMoreClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * Material 3 Loading Indicator for pull-to-refresh
 * Matches: Column(horizontalAlignment = Alignment.CenterHorizontally) { LoadingIndicator() }
 */
@Composable
fun LoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 6.dp,
        tonalElevation = 6.dp,
        modifier = modifier.size(42.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = color,
                strokeWidth = 2.8.dp
            )
        }
    }
}

