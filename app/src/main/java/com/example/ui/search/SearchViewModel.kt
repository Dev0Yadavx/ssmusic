package com.example.ui.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.*
import com.example.data.remote.NetworkResult
import com.example.player.PlayerManager
import com.example.repository.MusicRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class SearchTab {
    ALL, SONGS, ALBUMS, ARTISTS, PLAYLISTS
}

sealed class SearchUiState {
    object Idle : SearchUiState()
    object Loading : SearchUiState()
    data class Results(
        val songs: List<Song> = emptyList(),
        val albums: List<Album> = emptyList(),
        val artists: List<Artist> = emptyList(),
        val playlists: List<Playlist> = emptyList()
    ) : SearchUiState()
    data class Error(val message: String) : SearchUiState()
}

class SearchViewModel(application: Application) : AndroidViewModel(application) {

    val repository = MusicRepository(application)
    private val playerManager = PlayerManager.getInstance(application)

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _activeTab = MutableStateFlow(SearchTab.ALL)
    val activeTab: StateFlow<SearchTab> = _activeTab.asStateFlow()

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val _autocompleteSuggestions = MutableStateFlow<SearchResultCategory?>(null)
    val autocompleteSuggestions: StateFlow<SearchResultCategory?> = _autocompleteSuggestions.asStateFlow()

    private val _suggestedSongs = MutableStateFlow<List<Song>>(emptyList())
    val suggestedSongs: StateFlow<List<Song>> = _suggestedSongs.asStateFlow()

    val searchHistory: StateFlow<List<String>> = repository.searchHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var debounceJob: Job? = null

    init {
        loadSuggestedSongs()
    }

    fun loadSuggestedSongs() {
        viewModelScope.launch {
            val result = repository.searchSongs("Trending Hits")
            if (result is NetworkResult.Success && result.data.isNotEmpty()) {
                _suggestedSongs.value = result.data.take(15)
            }
        }
    }

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
        debounceJob?.cancel()

        if (newQuery.isBlank()) {
            _autocompleteSuggestions.value = null
            _uiState.value = SearchUiState.Idle
            return
        }

        debounceJob = viewModelScope.launch {
            delay(200) // Fast 200ms debounce for quick live typing feedback
            val suggestions = repository.getAutocomplete(newQuery)
            if (suggestions.songs.isNotEmpty()) {
                _autocompleteSuggestions.value = suggestions
            } else {
                val songSearch = repository.searchSongs(newQuery)
                if (songSearch is NetworkResult.Success && songSearch.data.isNotEmpty()) {
                    _autocompleteSuggestions.value = SearchResultCategory(
                        query = newQuery,
                        songs = songSearch.data.take(15)
                    )
                } else {
                    _autocompleteSuggestions.value = suggestions
                }
            }
        }
    }

    fun submitSearch(searchQuery: String? = null) {
        val q = searchQuery ?: _query.value
        if (q.isBlank()) return
        _query.value = q
        _autocompleteSuggestions.value = null
        _activeTab.value = SearchTab.ALL
        debounceJob?.cancel()

        viewModelScope.launch {
            _uiState.value = SearchUiState.Loading
            repository.addSearchQuery(q)

            // Perform searches concurrently
            val songsDeferred = async { repository.searchSongs(q) }
            val albumsDeferred = async { repository.searchAlbums(q) }
            val playlistsDeferred = async { repository.searchPlaylists(q) }
            val artistsDeferred = async { repository.searchArtists(q) }

            val songsResult = songsDeferred.await()
            val albumsResult = albumsDeferred.await()
            val playlistsResult = playlistsDeferred.await()
            val artistsResult = artistsDeferred.await()

            val songs = if (songsResult is NetworkResult.Success) songsResult.data else emptyList()
            val albums = if (albumsResult is NetworkResult.Success) albumsResult.data else emptyList()
            val playlists = if (playlistsResult is NetworkResult.Success) playlistsResult.data else emptyList()
            val artists = if (artistsResult is NetworkResult.Success) artistsResult.data else emptyList()

            if (songs.isEmpty() && albums.isEmpty() && playlists.isEmpty() && artists.isEmpty()) {
                _uiState.value = SearchUiState.Error("No results found for \"$q\"")
            } else {
                _uiState.value = SearchUiState.Results(
                    songs = songs,
                    albums = albums,
                    artists = artists,
                    playlists = playlists
                )
            }
        }
    }

    fun setTab(tab: SearchTab) {
        _activeTab.value = tab
    }

    fun clearQuery() {
        _query.value = ""
        _autocompleteSuggestions.value = null
        _uiState.value = SearchUiState.Idle
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearSearchHistory()
        }
    }

    fun playTrack(track: PlayableTrack, queueList: List<PlayableTrack> = emptyList()) {
        playerManager.playTrack(track, queueList)
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
}
