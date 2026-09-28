package com.example.ui.search

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.model.Album
import com.example.data.model.Artist
import com.example.data.model.PlayableTrack
import com.example.data.model.Playlist
import com.example.data.model.Song
import com.example.player.PlayerManager
import com.example.ui.common.*

val trendingPillsRow1 = listOf(
    "🔥 Trending Hits",
    "Arijit Singh",
    "Diljit Dosanjh",
    "Sidhu Moose Wala",
    "Bollywood 2024",
    "Lo-Fi Chill",
    "Shreya Ghoshal",
    "Gym Workout"
)

val trendingPillsRow2 = listOf(
    "Punjabi Pop",
    "Romantic Melodies",
    "Midnight Vibes",
    "Anirudh",
    "Top 50 Global",
    "Indie India",
    "Bhakti Sagar",
    "90s Classics"
)

@Composable
fun TrendingPillChip(
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)),
        onClick = onClick,
        modifier = modifier.height(32.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (tag.startsWith("🔥")) {
                Text(text = "🔥", fontSize = 12.sp)
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = tag.removePrefix("🔥 ").trim(),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.5.sp
                    )
                )
            } else {
                Icon(
                    imageVector = Icons.Default.TrendingUp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = tag,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 12.5.sp
                    )
                )
            }
        }
    }
}

fun getTabIcon(tab: SearchTab): ImageVector {
    return when (tab) {
        SearchTab.ALL -> Icons.Default.TravelExplore
        SearchTab.SONGS -> Icons.Default.MusicNote
        SearchTab.ALBUMS -> Icons.Default.Album
        SearchTab.ARTISTS -> Icons.Default.Person
        SearchTab.PLAYLISTS -> Icons.AutoMirrored.Filled.QueueMusic
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    playerManager: PlayerManager,
    onNavigateToAlbum: (Album) -> Unit,
    onNavigateToPlaylist: (String) -> Unit,
    onNavigateToArtist: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val activeTab by viewModel.activeTab.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val suggestions by viewModel.autocompleteSuggestions.collectAsStateWithLifecycle()
    val suggestedSongs by viewModel.suggestedSongs.collectAsStateWithLifecycle()
    val searchHistory by viewModel.searchHistory.collectAsStateWithLifecycle()
    val currentTrack by playerManager.currentTrack.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var selectedTrackForOptions by remember { mutableStateOf<PlayableTrack?>(null) }

    val voiceSearchLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                viewModel.onQueryChange(spokenText)
                viewModel.submitSearch(spokenText)
                focusManager.clearFocus()
            }
        }
    }

    fun launchVoiceSearch() {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to search songs or artists...")
            }
            voiceSearchLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Voice search is not supported on this device", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                // Search Input Field with Voice Search Icon
                OutlinedTextField(
                    value = query,
                    onValueChange = { viewModel.onQueryChange(it) },
                    placeholder = {
                        Text(
                            text = "Search songs, artists, albums...",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (query.isNotEmpty()) {
                                IconButton(
                                    onClick = { viewModel.clearQuery() },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear search",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            IconButton(
                                onClick = { launchVoiceSearch() },
                                modifier = Modifier
                                    .size(38.dp)
                                    .testTag("voice_search_button")
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Rounded.Mic,
                                            contentDescription = "Voice Search",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            viewModel.submitSearch()
                            focusManager.clearFocus()
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("search_text_field")
                )

                // Results Filter Chips: Visible only when search results arrive (result aane pe filter kar sake)
                AnimatedVisibility(
                    visible = uiState is SearchUiState.Results,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(8.dp))
                        val r = uiState as? SearchUiState.Results
                        val tabs = listOf(
                            SearchTab.ALL to ("All Songs" + (if ((r?.songs?.size ?: 0) > 0) " (${r?.songs?.size})" else "")),
                            SearchTab.ARTISTS to ("Artists" + (if ((r?.artists?.size ?: 0) > 0) " (${r?.artists?.size})" else "")),
                            SearchTab.ALBUMS to ("Albums" + (if ((r?.albums?.size ?: 0) > 0) " (${r?.albums?.size})" else "")),
                            SearchTab.PLAYLISTS to ("Playlists" + (if ((r?.playlists?.size ?: 0) > 0) " (${r?.playlists?.size})" else ""))
                        )
                        ScrollableTabRow(
                            selectedTabIndex = tabs.indexOfFirst { it.first == activeTab }.coerceAtLeast(0),
                            edgePadding = 0.dp,
                            divider = {},
                            indicator = {},
                            containerColor = Color.Transparent,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            tabs.forEach { (tab, labelText) ->
                                val isSelected = activeTab == tab
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.setTab(tab) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = getTabIcon(tab),
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = labelText,
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                fontSize = 12.sp
                                            )
                                        )
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f),
                                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        iconColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    border = null,
                                    modifier = Modifier
                                        .padding(end = 6.dp)
                                        .testTag("search_tab_${tab.name}")
                                )
                            }
                        }
                    }
                }
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
            when {
                // 1. TYPING MODE: Clean view without bulky cards, pure song suggestions
                query.isNotBlank() && uiState !is SearchUiState.Results -> {
                    val currentSuggestions = suggestions
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                        contentPadding = PaddingValues(bottom = 165.dp)
                    ) {
                        // Quick Search term submit row
                        item(key = "quick_search_action") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.submitSearch(query)
                                        focusManager.clearFocus()
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Text(
                                    text = "Search for \"$query\"",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f))
                        }

                        if (currentSuggestions != null && currentSuggestions.songs.isNotEmpty()) {
                            items(currentSuggestions.songs, key = { "ac_song_${it.id}" }) { song ->
                                SongRowItem(
                                    song = song,
                                    isPlaying = currentTrack?.id == song.id,
                                    onClick = {
                                        viewModel.playTrack(song.toPlayableTrack(), currentSuggestions.songs.map { it.toPlayableTrack() })
                                        focusManager.clearFocus()
                                    },
                                    onMoreClick = { selectedTrackForOptions = song.toPlayableTrack() }
                                )
                            }
                        } else if (currentSuggestions == null) {
                            item(key = "typing_progress") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 36.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(24.dp),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. IDLE MODE: Trending Tag Pills + Suggested Songs (Bulky cards removed)
                uiState is SearchUiState.Idle -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 165.dp)
                    ) {
                        // Recent Searches section if available
                        if (searchHistory.isNotEmpty()) {
                            item(key = "recent_searches_section") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Recent Searches",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                    TextButton(
                                        onClick = { viewModel.clearHistory() },
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("Clear all", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                                    }
                                }

                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(searchHistory.take(8)) { term ->
                                        Surface(
                                            shape = RoundedCornerShape(16.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                                            onClick = {
                                                viewModel.submitSearch(term)
                                                focusManager.clearFocus()
                                            }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.History,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = term,
                                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Trending Tags Section (Pill Slim Chip Style)
                        item(key = "trending_tags_section") {
                            Text(
                                text = "Trending Searches",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 10.dp)
                            )

                            // Row 1 of slim pills
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(trendingPillsRow1) { tag ->
                                    TrendingPillChip(tag = tag, onClick = {
                                        viewModel.submitSearch(tag.removePrefix("🔥 "))
                                        focusManager.clearFocus()
                                    })
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Row 2 of slim pills
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(trendingPillsRow2) { tag ->
                                    TrendingPillChip(tag = tag, onClick = {
                                        viewModel.submitSearch(tag.removePrefix("🔥 "))
                                        focusManager.clearFocus()
                                    })
                                }
                            }
                        }

                        // Suggested Songs Section ("suggest song")
                        item(key = "suggested_songs_header") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Suggested Songs",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        if (suggestedSongs.isNotEmpty()) {
                            items(suggestedSongs, key = { "suggested_${it.id}" }) { song ->
                                SongRowItem(
                                    song = song,
                                    isPlaying = currentTrack?.id == song.id,
                                    onClick = {
                                        viewModel.playTrack(song.toPlayableTrack(), suggestedSongs.map { it.toPlayableTrack() })
                                        focusManager.clearFocus()
                                    },
                                    onMoreClick = { selectedTrackForOptions = song.toPlayableTrack() }
                                )
                            }
                        } else {
                            item(key = "suggested_songs_loading") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(24.dp),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. SEARCH LOADING
                uiState is SearchUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }

                // 4. SEARCH ERROR
                uiState is SearchUiState.Error -> {
                    val state = uiState as SearchUiState.Error
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.SearchOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                }

                // 5. SEARCH RESULTS (all me only song show)
                uiState is SearchUiState.Results -> {
                    SearchResultsContent(
                        results = uiState as SearchUiState.Results,
                        tab = activeTab,
                        currentPlayingId = currentTrack?.id,
                        onPlaySong = { song, songs ->
                            viewModel.playTrack(song.toPlayableTrack(), songs.map { it.toPlayableTrack() })
                        },
                        onMoreSong = { song -> selectedTrackForOptions = song.toPlayableTrack() },
                        onAlbumClick = { onNavigateToAlbum(it) },
                        onPlaylistClick = { onNavigateToPlaylist(it.id) },
                        onArtistClick = { onNavigateToArtist(it.id) }
                    )
                }
            }
        }
    }

    var trackForAddToPlaylist by remember { mutableStateOf<PlayableTrack?>(null) }

    selectedTrackForOptions?.let { track ->
        TrackOptionsBottomSheet(
            track = track,
            isLiked = false,
            onDismiss = { selectedTrackForOptions = null },
            onPlayNow = { viewModel.playTrack(track) },
            onPlayNext = { viewModel.playNext(track) },
            onAddToQueue = { viewModel.addToQueue(track) },
            onToggleLike = { viewModel.toggleLike(track) },
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
fun SearchResultsContent(
    results: SearchUiState.Results,
    tab: SearchTab,
    currentPlayingId: String?,
    onPlaySong: (Song, List<Song>) -> Unit,
    onMoreSong: (Song) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onArtistClick: (Artist) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 4.dp, bottom = 165.dp)
    ) {
        when (tab) {
            SearchTab.ALL, SearchTab.SONGS -> {
                // "all me only song show" - display all matching songs, cards removed
                if (results.songs.isNotEmpty()) {
                    items(results.songs, key = { "song_${it.id}" }) { song ->
                        SongRowItem(
                            song = song,
                            isPlaying = currentPlayingId == song.id,
                            onClick = { onPlaySong(song, results.songs) },
                            onMoreClick = { onMoreSong(song) }
                        )
                    }
                } else {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No songs found",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            SearchTab.ARTISTS -> {
                if (results.artists.isNotEmpty()) {
                    items(results.artists, key = { "artist_${it.id}" }) { artist ->
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = artist.name,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            supportingContent = {
                                Text(
                                    text = artist.role.ifBlank { "Artist" },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            leadingContent = {
                                Surface(
                                    modifier = Modifier.size(50.dp),
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    AsyncImage(
                                        model = artist.image,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                }
                            },
                            trailingContent = {
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            },
                            modifier = Modifier.clickable { onArtistClick(artist) }
                        )
                    }
                } else {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No artists found",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            SearchTab.ALBUMS -> {
                if (results.albums.isNotEmpty()) {
                    items(results.albums, key = { "album_${it.id}" }) { album ->
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = album.title,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            supportingContent = {
                                Text(
                                    text = album.artist.ifBlank { album.year },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            leadingContent = {
                                Surface(
                                    modifier = Modifier.size(50.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    AsyncImage(
                                        model = album.artwork,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                }
                            },
                            trailingContent = {
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            },
                            modifier = Modifier.clickable { onAlbumClick(album) }
                        )
                    }
                } else {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No albums found",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            SearchTab.PLAYLISTS -> {
                if (results.playlists.isNotEmpty()) {
                    items(results.playlists, key = { "playlist_${it.id}" }) { playlist ->
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = playlist.title,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            supportingContent = {
                                Text(
                                    text = playlist.subtitle.ifBlank { "${playlist.songCount} Songs" },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            leadingContent = {
                                Surface(
                                    modifier = Modifier.size(50.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    AsyncImage(
                                        model = playlist.artwork,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                }
                            },
                            trailingContent = {
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            },
                            modifier = Modifier.clickable { onPlaylistClick(playlist) }
                        )
                    }
                } else {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No playlists found",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
