package com.example.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.MusicShelf
import com.example.data.model.PlayableTrack
import com.example.data.model.ShelfItem
import com.example.data.model.ShelfType
import com.example.data.remote.NetworkResult
import com.example.player.PlayerManager
import com.example.repository.MusicRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed class HomeUiState {
    object Loading : HomeUiState()
    data class Success(
        val shelves: List<MusicShelf>,
        val recentlyPlayed: List<PlayableTrack> = emptyList(),
        val selectedLanguage: String = "All"
    ) : HomeUiState()
    data class Error(val message: String) : HomeUiState()
}

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    val repository = MusicRepository(application)
    private val playerManager = PlayerManager.getInstance(application)

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _selectedLanguage = MutableStateFlow("All")
    val selectedLanguage: StateFlow<String> = _selectedLanguage.asStateFlow()

    private var rawShelves: List<MusicShelf> = emptyList()
    private var userPlaylists: List<com.example.data.model.UserPlaylistSummary> = emptyList()

    init {
        loadHomeData()
        observeRecentlyPlayed()
        observeUserPlaylists()
    }

    fun loadHomeData() {
        viewModelScope.launch {
            val langQuery = when (_selectedLanguage.value) {
                "Hindi" -> "hindi"
                "Punjabi" -> "punjabi"
                "English" -> "english"
                "Bhojpuri" -> "bhojpuri"
                "Haryanvi" -> "haryanvi"
                else -> "hindi,english,punjabi,bhojpuri,haryanvi"
            }

            repository.getHomeContent(languages = langQuery).collect { result ->
                when (result) {
                    is NetworkResult.Loading -> {
                        if (_uiState.value !is HomeUiState.Success) {
                            _uiState.value = HomeUiState.Loading
                        }
                    }
                    is NetworkResult.Success -> {
                        rawShelves = result.data
                        val recent = repository.recentlyPlayed.firstOrNull() ?: emptyList()
                        _uiState.value = HomeUiState.Success(
                            shelves = buildShelves(rawShelves, recent, userPlaylists),
                            recentlyPlayed = recent,
                            selectedLanguage = _selectedLanguage.value
                        )
                    }
                    is NetworkResult.Error -> {
                        if (rawShelves.isEmpty()) {
                            val recent = repository.recentlyPlayed.firstOrNull() ?: emptyList()
                            if (userPlaylists.isNotEmpty() || recent.isNotEmpty()) {
                                _uiState.value = HomeUiState.Success(
                                    shelves = buildShelves(emptyList(), recent, userPlaylists),
                                    recentlyPlayed = recent,
                                    selectedLanguage = _selectedLanguage.value
                                )
                            } else {
                                _uiState.value = HomeUiState.Error(result.message)
                            }
                        }
                    }
                }
            }
        }
    }

    fun refreshHomeData() {
        viewModelScope.launch {
            _isRefreshing.value = true
            val langQuery = when (_selectedLanguage.value) {
                "Hindi" -> "hindi"
                "Punjabi" -> "punjabi"
                "English" -> "english"
                "Bhojpuri" -> "bhojpuri"
                "Haryanvi" -> "haryanvi"
                else -> "hindi,english,punjabi,bhojpuri,haryanvi"
            }

            try {
                repository.getHomeContent(languages = langQuery).collect { result ->
                    when (result) {
                        is NetworkResult.Loading -> {}
                        is NetworkResult.Success -> {
                            rawShelves = result.data
                            val recent = repository.recentlyPlayed.firstOrNull() ?: emptyList()
                            _uiState.value = HomeUiState.Success(
                                shelves = buildShelves(rawShelves, recent, userPlaylists),
                                recentlyPlayed = recent,
                                selectedLanguage = _selectedLanguage.value
                            )
                        }
                        is NetworkResult.Error -> {
                            if (rawShelves.isEmpty()) {
                                val recent = repository.recentlyPlayed.firstOrNull() ?: emptyList()
                                if (userPlaylists.isNotEmpty() || recent.isNotEmpty()) {
                                    _uiState.value = HomeUiState.Success(
                                        shelves = buildShelves(emptyList(), recent, userPlaylists),
                                        recentlyPlayed = recent,
                                        selectedLanguage = _selectedLanguage.value
                                    )
                                } else {
                                    _uiState.value = HomeUiState.Error(result.message)
                                }
                            }
                        }
                    }
                }
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    private fun observeRecentlyPlayed() {
        viewModelScope.launch {
            repository.recentlyPlayed.collect { recent ->
                val current = _uiState.value
                if (current is HomeUiState.Success) {
                    _uiState.value = current.copy(
                        shelves = buildShelves(rawShelves, recent, userPlaylists),
                        recentlyPlayed = recent
                    )
                }
            }
        }
    }

    private fun observeUserPlaylists() {
        viewModelScope.launch {
            repository.userPlaylistsWithPreviews.collect { playlists ->
                userPlaylists = playlists
                val current = _uiState.value
                if (current is HomeUiState.Success) {
                    val recent = repository.recentlyPlayed.firstOrNull() ?: emptyList()
                    _uiState.value = current.copy(
                        shelves = buildShelves(rawShelves, recent, userPlaylists)
                    )
                }
            }
        }
    }

    private fun buildShelves(
        apiShelves: List<MusicShelf>,
        recent: List<PlayableTrack>,
        localPlaylists: List<com.example.data.model.UserPlaylistSummary>
    ): List<MusicShelf> {
        val list = mutableListOf<MusicShelf>()
        // 1. Recently Played (if any, at top)
        if (recent.isNotEmpty()) {
            list.add(
                MusicShelf(
                    id = "recently_played",
                    title = "Recently Played",
                    subtitle = "Pick up where you left off",
                    type = ShelfType.SONG_HORIZONTAL,
                    items = recent.map { ShelfItem.SongItem(it.toSong()) }
                )
            )
        }

        // 2. All online API shelves (Trending, Top Playlists, Charts, etc.)
        list.addAll(apiShelves)

        // 3. Sabse last me: Imported & Saved Playlists ("smusic playlist")
        if (localPlaylists.isNotEmpty()) {
            val playlistItems = localPlaylists.map { summary ->
                ShelfItem.PlaylistItem(
                    com.example.data.model.Playlist(
                        id = "local_${summary.id}",
                        title = summary.name.ifBlank { "smusic playlist" },
                        subtitle = "${summary.songCount} songs",
                        description = summary.description,
                        artwork = summary.previewArtworks.firstOrNull() ?: "",
                        songCount = summary.songCount
                    )
                )
            }
            list.add(
                MusicShelf(
                    id = "smusic_playlists",
                    title = "smusic playlist",
                    subtitle = "Imported & saved playlists",
                    type = ShelfType.PLAYLIST_HORIZONTAL,
                    items = playlistItems
                )
            )
        }

        return list
    }

    fun selectLanguage(language: String) {
        _selectedLanguage.value = language
        loadHomeData()
    }

    fun playTrack(track: PlayableTrack, shelfSongs: List<PlayableTrack> = emptyList()) {
        playerManager.playTrack(track, shelfSongs)
    }

    fun addToQueue(track: PlayableTrack) {
        playerManager.addToQueue(track)
    }

    fun playNext(track: PlayableTrack) {
        playerManager.playNext(track)
    }

    fun toggleLike(track: PlayableTrack) {
        viewModelScope.launch {
            repository.toggleLike(track)
        }
    }

    private fun PlayableTrack.toSong() = com.example.data.model.Song(
        id = id,
        title = title,
        artist = artist,
        album = album,
        albumId = albumId,
        artwork = artwork,
        duration = duration,
        streamUrl = streamUrl,
        encryptedMediaUrl = encryptedMediaUrl
    )
}
