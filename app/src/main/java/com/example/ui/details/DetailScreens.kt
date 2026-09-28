package com.example.ui.details

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import com.example.data.model.Album
import com.example.data.model.Artist
import com.example.data.model.PlayableTrack
import com.example.data.model.Playlist
import com.example.data.remote.NetworkResult
import com.example.player.PlayerManager
import com.example.repository.MusicRepository
import com.example.ui.common.AlbumCard
import com.example.ui.common.AddToPlaylistBottomSheet
import com.example.ui.common.FourSongGridCover
import com.example.ui.common.SongRowItem
import com.example.ui.common.TrackOptionsBottomSheet

@Composable
fun AlbumDetailScreen(
    albumId: String,
    initialTitle: String = "",
    initialArtist: String = "",
    initialArtwork: String = "",
    repository: MusicRepository,
    playerManager: PlayerManager,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val scope = rememberCoroutineScope()
    var albumResult by remember { mutableStateOf<NetworkResult<Album>>(NetworkResult.Loading) }
    val currentTrack by playerManager.currentTrack.collectAsState()
    val isPlaying by playerManager.isPlaying.collectAsState()
    var selectedTrackForOptions by remember { mutableStateOf<PlayableTrack?>(null) }
    var isLiked by remember { mutableStateOf(false) }

    LaunchedEffect(albumId, initialTitle) {
        albumResult = repository.getAlbumDetails(albumId, initialTitle, initialArtist, initialArtwork)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when (val result = albumResult) {
            is NetworkResult.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            is NetworkResult.Error -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CloudOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Failed to load album",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                albumResult = NetworkResult.Loading
                                albumResult = repository.getAlbumDetails(albumId)
                            }
                        },
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text("Retry")
                    }
                }
            }
            is NetworkResult.Success -> {
                val album = result.data
                val playableTracks = album.songs.map { it.toPlayableTrack() }
                val isCurrentAlbumPlaying = playableTracks.any { it.id == currentTrack?.id } && isPlaying

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 165.dp)
                ) {
                    // Double Exposure / Ambient Mask Header (Zero Gap to Edge)
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(380.dp)
                        ) {
                            // Ambient Backdrop
                            AsyncImage(
                                model = album.artwork,
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .blur(50.dp),
                                contentScale = ContentScale.Crop,
                                alpha = 0.45f
                            )

                            // Double Exposure Gradient Mask
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Black.copy(alpha = 0.55f),
                                                Color.Black.copy(alpha = 0.25f),
                                                MaterialTheme.colorScheme.background.copy(alpha = 0.95f),
                                                MaterialTheme.colorScheme.background
                                            )
                                        )
                                    )
                            )

                            // Foreground Artwork & Metadata
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .statusBarsPadding()
                                    .padding(horizontal = 20.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Spacer(modifier = Modifier.height(36.dp))

                                // Rounded Glow Album Artwork
                                Surface(
                                    shape = RoundedCornerShape(22.dp),
                                    shadowElevation = 16.dp,
                                    modifier = Modifier.size(175.dp)
                                ) {
                                    AsyncImage(
                                        model = album.artwork,
                                        contentDescription = album.title,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                Text(
                                    text = album.title,
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 22.sp
                                    ),
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = "${album.artist} • ${album.year.ifBlank { "Album" }} • ${album.songs.size} Songs",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    // Large M3 Expensive Play FAB Action Row
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Follow / Favorite Heart Button
                                IconButton(
                                    onClick = { isLiked = !isLiked },
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                ) {
                                    Icon(
                                        imageVector = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                        contentDescription = "Favorite",
                                        tint = if (isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                // Shuffle Play Button
                                IconButton(
                                    onClick = {
                                        if (playableTracks.isNotEmpty()) {
                                            val shuffled = playableTracks.shuffled()
                                            playerManager.playTrack(shuffled.first(), shuffled)
                                        }
                                    },
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Shuffle,
                                        contentDescription = "Shuffle",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            // Large M3 Expensive FAB Play Button
                            FloatingActionButton(
                                onClick = {
                                    if (playableTracks.isNotEmpty()) {
                                        if (isCurrentAlbumPlaying) {
                                            playerManager.playPause()
                                        } else {
                                            playerManager.playTrack(playableTracks.first(), playableTracks)
                                        }
                                    }
                                },
                                shape = CircleShape,
                                containerColor = MaterialTheme.colorScheme.primary,
                                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp),
                                modifier = Modifier
                                    .size(56.dp)
                                    .testTag("album_play_fab")
                            ) {
                                Icon(
                                    imageVector = if (isCurrentAlbumPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = "Play Album",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }

                    // Album Songs List Header
                    item {
                        Text(
                            text = "Tracks",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            ),
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 4.dp),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    itemsIndexed(album.songs, key = { _, s -> s.id }) { index, song ->
                        SongRowItem(
                            song = song,
                            isPlaying = currentTrack?.id == song.id,
                            onClick = { playerManager.playTrack(song.toPlayableTrack(), playableTracks) },
                            onMoreClick = { selectedTrackForOptions = song.toPlayableTrack() }
                        )
                    }
                }
            }
        }

        // Fixed Top Floating Back Button with Frosted Blur (Zero Gap)
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = 16.dp, top = 8.dp)
                .size(42.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f))
                .align(Alignment.TopStart)
                .testTag("album_back_button")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
    }

    selectedTrackForOptions?.let { track ->
        TrackOptionsBottomSheet(
            track = track,
            isLiked = false,
            onDismiss = { selectedTrackForOptions = null },
            onPlayNow = { playerManager.playTrack(track) },
            onPlayNext = { playerManager.playNext(track) },
            onAddToQueue = { playerManager.addToQueue(track) },
            onToggleLike = {
                scope.launch {
                    repository.toggleLike(track)
                }
            },
            onAddToPlaylist = {}
        )
    }
}

@Composable
fun PlaylistDetailScreen(
    playlistId: String,
    repository: MusicRepository,
    playerManager: PlayerManager,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val scope = rememberCoroutineScope()
    var playlistResult by remember { mutableStateOf<NetworkResult<Playlist>>(NetworkResult.Loading) }
    val currentTrack by playerManager.currentTrack.collectAsState()
    val isPlaying by playerManager.isPlaying.collectAsState()
    var selectedTrackForOptions by remember { mutableStateOf<PlayableTrack?>(null) }
    var isLiked by remember { mutableStateOf(false) }

    LaunchedEffect(playlistId) {
        playlistResult = repository.getPlaylistDetails(playlistId)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when (val result = playlistResult) {
            is NetworkResult.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            is NetworkResult.Error -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CloudOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Failed to load playlist",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
            is NetworkResult.Success -> {
                val playlist = result.data
                val playableTracks = playlist.songs.map { it.toPlayableTrack() }
                val isCurrentPlaylistPlaying = playableTracks.any { it.id == currentTrack?.id } && isPlaying

                val previewArtworks = remember(playlist.songs, playlist.artwork) {
                    val fromSongs = playlist.songs.mapNotNull { it.artwork.takeIf { art -> art.isNotBlank() } }.distinct().take(4)
                    if (fromSongs.isNotEmpty()) fromSongs else if (playlist.artwork.isNotBlank()) listOf(playlist.artwork) else emptyList()
                }
                val blurArtwork = remember(previewArtworks, playlist.artwork) {
                    previewArtworks.firstOrNull() ?: playlist.artwork
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 165.dp)
                ) {
                    // Double Exposure Header
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(380.dp)
                        ) {
                            if (blurArtwork.isNotBlank()) {
                                AsyncImage(
                                    model = blurArtwork,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .blur(50.dp),
                                    contentScale = ContentScale.Crop,
                                    alpha = 0.45f
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(
                                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                                    MaterialTheme.colorScheme.background
                                                )
                                            )
                                        )
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Black.copy(alpha = 0.55f),
                                                Color.Black.copy(alpha = 0.25f),
                                                MaterialTheme.colorScheme.background.copy(alpha = 0.95f),
                                                MaterialTheme.colorScheme.background
                                            )
                                        )
                                    )
                            )

                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .statusBarsPadding()
                                    .padding(horizontal = 20.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Spacer(modifier = Modifier.height(36.dp))

                                Surface(
                                    shape = RoundedCornerShape(22.dp),
                                    shadowElevation = 16.dp,
                                    modifier = Modifier.size(180.dp)
                                ) {
                                    FourSongGridCover(
                                        artworks = previewArtworks,
                                        modifier = Modifier.fillMaxSize(),
                                        cornerRadius = 22.dp,
                                        fallbackTitle = playlist.title
                                    )
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                Text(
                                    text = playlist.title,
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 22.sp
                                    ),
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                if (playlist.description.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = playlist.description,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        textAlign = TextAlign.Center,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    // Large M3 FAB Action Row
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { isLiked = !isLiked },
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                ) {
                                    Icon(
                                        imageVector = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                        contentDescription = "Favorite",
                                        tint = if (isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        if (playableTracks.isNotEmpty()) {
                                            val shuffled = playableTracks.shuffled()
                                            playerManager.playTrack(shuffled.first(), shuffled)
                                        }
                                    },
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Shuffle,
                                        contentDescription = "Shuffle",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            FloatingActionButton(
                                onClick = {
                                    if (playableTracks.isNotEmpty()) {
                                        if (isCurrentPlaylistPlaying) {
                                            playerManager.playPause()
                                        } else {
                                            playerManager.playTrack(playableTracks.first(), playableTracks)
                                        }
                                    }
                                },
                                shape = CircleShape,
                                containerColor = MaterialTheme.colorScheme.primary,
                                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp),
                                modifier = Modifier
                                    .size(56.dp)
                                    .testTag("playlist_play_fab")
                            ) {
                                Icon(
                                    imageVector = if (isCurrentPlaylistPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = "Play",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }

                    item {
                        Text(
                            text = "Tracks (${playlist.songs.size})",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            ),
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 4.dp),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    itemsIndexed(playlist.songs, key = { _, s -> s.id }) { index, song ->
                        SongRowItem(
                            song = song,
                            isPlaying = currentTrack?.id == song.id,
                            onClick = { playerManager.playTrack(song.toPlayableTrack(), playableTracks) },
                            onMoreClick = { selectedTrackForOptions = song.toPlayableTrack() }
                        )
                    }
                }
            }
        }

        IconButton(
            onClick = onBack,
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = 16.dp, top = 8.dp)
                .size(42.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f))
                .align(Alignment.TopStart)
                .testTag("playlist_back_button")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
    }

    var trackForAddToPlaylist by remember { mutableStateOf<PlayableTrack?>(null) }

    selectedTrackForOptions?.let { track ->
        TrackOptionsBottomSheet(
            track = track,
            isLiked = false,
            onDismiss = { selectedTrackForOptions = null },
            onPlayNow = { playerManager.playTrack(track) },
            onPlayNext = { playerManager.playNext(track) },
            onAddToQueue = { playerManager.addToQueue(track) },
            onToggleLike = {
                scope.launch {
                    repository.toggleLike(track)
                }
            },
            onAddToPlaylist = {
                trackForAddToPlaylist = track
            }
        )
    }

    trackForAddToPlaylist?.let { track ->
        AddToPlaylistBottomSheet(
            track = track,
            repository = repository,
            onDismiss = { trackForAddToPlaylist = null }
        )
    }
}

@Composable
fun ArtistDetailScreen(
    artistId: String,
    repository: MusicRepository,
    playerManager: PlayerManager,
    onNavigateToAlbum: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val scope = rememberCoroutineScope()
    var artistResult by remember { mutableStateOf<NetworkResult<Artist>>(NetworkResult.Loading) }
    val currentTrack by playerManager.currentTrack.collectAsState()
    val isPlaying by playerManager.isPlaying.collectAsState()
    var selectedTrackForOptions by remember { mutableStateOf<PlayableTrack?>(null) }
    var isFollowing by remember { mutableStateOf(false) }

    LaunchedEffect(artistId) {
        artistResult = repository.getArtistDetails(artistId)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when (val result = artistResult) {
            is NetworkResult.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            is NetworkResult.Error -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CloudOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Failed to load artist",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                artistResult = NetworkResult.Loading
                                artistResult = repository.getArtistDetails(artistId)
                            }
                        },
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text("Retry")
                    }
                }
            }
            is NetworkResult.Success -> {
                val artist = result.data
                val playableTracks = artist.topSongs.map { it.toPlayableTrack() }
                val isCurrentArtistPlaying = playableTracks.any { it.id == currentTrack?.id } && isPlaying

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 165.dp)
                ) {
                    // Double Exposure / Ambient Hero Mask Header (Full Bleed - Zero Upper Gap)
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(340.dp)
                        ) {
                            // Full-Bleed High-Res Artist Backdrop Photo
                            if (artist.image.isNotBlank()) {
                                AsyncImage(
                                    model = artist.image,
                                    contentDescription = artist.name,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.linearGradient(
                                                listOf(Color(0xFF8B5CF6), Color(0xFF3B82F6))
                                            )
                                        )
                                )
                            }

                            // Double Exposure Atmospheric Gradient Mask
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Black.copy(alpha = 0.55f),
                                                Color.Transparent,
                                                Color.Black.copy(alpha = 0.35f),
                                                MaterialTheme.colorScheme.background.copy(alpha = 0.85f),
                                                MaterialTheme.colorScheme.background
                                            )
                                        )
                                    )
                            )

                            // Artist Title & Verified Badge at Bottom of Hero
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(horizontal = 20.dp, vertical = 12.dp)
                            ) {
                                // Verified Badge Pill
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color.Black.copy(alpha = 0.4f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Verified,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "VERIFIED ARTIST",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 10.sp,
                                                letterSpacing = 1.sp,
                                                color = Color.White
                                            )
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                // Giant Artist Name
                                Text(
                                    text = artist.name,
                                    style = MaterialTheme.typography.headlineLarge.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 32.sp
                                    ),
                                    color = Color.White,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(modifier = Modifier.height(2.dp))

                                Text(
                                    text = "${artist.role.ifBlank { "Top Chart Artist" }} • ${playableTracks.size} Popular Tracks",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = Color.White.copy(alpha = 0.85f),
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                            }
                        }
                    }

                    // Large M3 Expensive Floating Action Play FAB Row
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Follow Button Pill
                                Button(
                                    onClick = { isFollowing = !isFollowing },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isFollowing) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                                        contentColor = if (isFollowing) MaterialTheme.colorScheme.onSurfaceVariant else Color.White
                                    ),
                                    shape = RoundedCornerShape(20.dp),
                                    border = if (isFollowing) null else androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = if (isFollowing) "Following" else "Follow",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }

                                // Shuffle Icon Button
                                IconButton(
                                    onClick = {
                                        if (playableTracks.isNotEmpty()) {
                                            val shuffled = playableTracks.shuffled()
                                            playerManager.playTrack(shuffled.first(), shuffled)
                                        }
                                    },
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Shuffle,
                                        contentDescription = "Shuffle",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            // Large M3 Expensive FAB Play Button
                            FloatingActionButton(
                                onClick = {
                                    if (playableTracks.isNotEmpty()) {
                                        if (isCurrentArtistPlaying) {
                                            playerManager.playPause()
                                        } else {
                                            playerManager.playTrack(playableTracks.first(), playableTracks)
                                        }
                                    }
                                },
                                shape = CircleShape,
                                containerColor = MaterialTheme.colorScheme.primary,
                                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp),
                                modifier = Modifier
                                    .size(56.dp)
                                    .testTag("artist_play_fab")
                            ) {
                                Icon(
                                    imageVector = if (isCurrentArtistPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = "Play Artist",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }

                    // Section 1: Popular Songs
                    if (artist.topSongs.isNotEmpty()) {
                        item {
                            Text(
                                text = "Popular Songs",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 20.sp
                                ),
                                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        itemsIndexed(artist.topSongs, key = { _, s -> s.id }) { index, song ->
                            SongRowItem(
                                song = song,
                                isPlaying = currentTrack?.id == song.id,
                                onClick = { playerManager.playTrack(song.toPlayableTrack(), playableTracks) },
                                onMoreClick = { selectedTrackForOptions = song.toPlayableTrack() }
                            )
                        }
                    }

                    // Section 2: Albums & Singles Discography (if available)
                    if (artist.topAlbums.isNotEmpty()) {
                        item {
                            Column(modifier = Modifier.padding(top = 20.dp)) {
                                Text(
                                    text = "Albums & Singles",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 20.sp
                                    ),
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 20.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    items(artist.topAlbums, key = { it.id }) { album ->
                                        AlbumCard(
                                            album = album,
                                            onClick = { onNavigateToAlbum(album.id) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Fixed Top Floating Back Button with Frosted Blur (Zero Gap)
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = 16.dp, top = 8.dp)
                .size(42.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f))
                .align(Alignment.TopStart)
                .testTag("artist_back_button")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
    }

    selectedTrackForOptions?.let { track ->
        TrackOptionsBottomSheet(
            track = track,
            isLiked = false,
            onDismiss = { selectedTrackForOptions = null },
            onPlayNow = { playerManager.playTrack(track) },
            onPlayNext = { playerManager.playNext(track) },
            onAddToQueue = { playerManager.addToQueue(track) },
            onToggleLike = {
                scope.launch {
                    repository.toggleLike(track)
                }
            },
            onAddToPlaylist = {}
        )
    }
}
