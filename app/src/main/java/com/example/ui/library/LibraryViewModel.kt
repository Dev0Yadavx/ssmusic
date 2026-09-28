package com.example.ui.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.PlaylistEntity
import com.example.data.model.PlayableTrack
import com.example.player.PlayerManager
import com.example.repository.MusicRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed class ImportPlaylistUiState {
    object Idle : ImportPlaylistUiState()
    data class Loading(val token: String) : ImportPlaylistUiState()
    data class Loaded(val playlist: com.example.data.model.Playlist) : ImportPlaylistUiState()
    data class Saved(val localPlaylistId: Long, val title: String) : ImportPlaylistUiState()
    data class Error(val message: String) : ImportPlaylistUiState()
}

enum class LibraryTab {
    LIKED, DOWNLOADS, PLAYLISTS, RECENT, LOCAL
}

class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    val repository = MusicRepository(application)
    private val playerManager = PlayerManager.getInstance(application)
    private val downloadManager = com.example.download.SongDownloadManager.getInstance(application)

    private val _selectedTab = MutableStateFlow(LibraryTab.LIKED)
    val selectedTab: StateFlow<LibraryTab> = _selectedTab.asStateFlow()

    val likedSongs: StateFlow<List<PlayableTrack>> = repository.likedSongs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentlyPlayed: StateFlow<List<PlayableTrack>> = repository.recentlyPlayed
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlists: StateFlow<List<PlaylistEntity>> = repository.userPlaylists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlistsWithPreviews: StateFlow<List<com.example.data.model.UserPlaylistSummary>> = repository.userPlaylistsWithPreviews
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _localSongs = MutableStateFlow<List<PlayableTrack>>(emptyList())
    val localSongs: StateFlow<List<PlayableTrack>> = _localSongs.asStateFlow()

    private val _isScanningLocal = MutableStateFlow(false)
    val isScanningLocal: StateFlow<Boolean> = _isScanningLocal.asStateFlow()

    // Real downloaded songs flow from Room Database & storage
    val downloadedSongs: StateFlow<List<PlayableTrack>> = downloadManager.allDownloadedTracks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active live download progress tasks
    val activeDownloads: StateFlow<Map<String, com.example.download.DownloadProgressState>> = downloadManager.activeDownloads

    fun downloadTrack(track: PlayableTrack, bitrate: String = "320") {
        downloadManager.startDownload(track, bitrate)
    }

    fun cancelDownload(songId: String) {
        downloadManager.cancelDownload(songId)
    }

    fun deleteDownloadedSong(songId: String) {
        downloadManager.deleteDownloadedSong(songId)
    }

    fun setTab(tab: LibraryTab) {
        _selectedTab.value = tab
        if (tab == LibraryTab.LOCAL && _localSongs.value.isEmpty()) {
            scanLocalMusic()
        }
    }

    fun scanLocalMusic() {
        viewModelScope.launch {
            _isScanningLocal.value = true
            val tracks = repository.scanLocalAudio()
            _localSongs.value = tracks
            _isScanningLocal.value = false
        }
    }

    fun createPlaylist(name: String, description: String = "") {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.createPlaylist(name, description)
        }
    }

    fun deletePlaylist(id: Long) {
        viewModelScope.launch {
            repository.deletePlaylist(id)
        }
    }

    fun playTrack(track: PlayableTrack, queueList: List<PlayableTrack> = emptyList()) {
        playerManager.playTrack(track, queueList)
    }

    fun playAll(tracks: List<PlayableTrack>) {
        if (tracks.isNotEmpty()) {
            playerManager.playTrack(tracks.first(), tracks)
        }
    }

    fun shuffleAll(tracks: List<PlayableTrack>) {
        if (tracks.isNotEmpty()) {
            val shuffled = tracks.shuffled()
            playerManager.playTrack(shuffled.first(), shuffled)
        }
    }

    fun toggleLike(track: PlayableTrack) {
        viewModelScope.launch {
            repository.toggleLike(track)
        }
    }

    fun addToQueue(track: PlayableTrack) {
        playerManager.addToQueue(track)
    }

    fun playNext(track: PlayableTrack) {
        playerManager.playNext(track)
    }

    private val _importState = MutableStateFlow<ImportPlaylistUiState>(ImportPlaylistUiState.Idle)
    val importState: StateFlow<ImportPlaylistUiState> = _importState.asStateFlow()

    fun resetImportState() {
        _importState.value = ImportPlaylistUiState.Idle
    }

    fun loadJioSaavnPlaylist(urlOrToken: String) {
        val token = com.musicx.app.utils.PlaylistLinkParser.extractToken(urlOrToken) ?: urlOrToken.trim()
        if (token.isBlank()) {
            _importState.value = ImportPlaylistUiState.Error("Please enter a valid JioSaavn playlist link or token")
            return
        }

        viewModelScope.launch {
            _importState.value = ImportPlaylistUiState.Loading(token)
            when (val result = repository.fetchPlaylistByToken(token)) {
                is com.example.data.remote.NetworkResult.Success -> {
                    _importState.value = ImportPlaylistUiState.Loaded(result.data)
                }
                is com.example.data.remote.NetworkResult.Error -> {
                    _importState.value = ImportPlaylistUiState.Error(result.message)
                }
                is com.example.data.remote.NetworkResult.Loading -> {}
            }
        }
    }

    fun importPlaylistDirectly(urlOrToken: String, onSaved: ((Long) -> Unit)? = null) {
        val token = com.musicx.app.utils.PlaylistLinkParser.extractToken(urlOrToken) ?: urlOrToken.trim()
        if (token.isBlank()) {
            _importState.value = ImportPlaylistUiState.Error("Please enter a valid JioSaavn playlist link or token")
            return
        }

        viewModelScope.launch {
            _importState.value = ImportPlaylistUiState.Loading(token)
            when (val result = repository.fetchPlaylistByToken(token)) {
                is com.example.data.remote.NetworkResult.Success -> {
                    val playlist = result.data
                    val localId = repository.importJioSaavnPlaylistToLocal(playlist)
                    _importState.value = ImportPlaylistUiState.Saved(localId, playlist.title)
                    onSaved?.invoke(localId)
                }
                is com.example.data.remote.NetworkResult.Error -> {
                    _importState.value = ImportPlaylistUiState.Error(result.message)
                }
                is com.example.data.remote.NetworkResult.Loading -> {}
            }
        }
    }

    fun saveImportedPlaylistToLibrary(playlist: com.example.data.model.Playlist, onSaved: (Long) -> Unit) {
        viewModelScope.launch {
            val localId = repository.importJioSaavnPlaylistToLocal(playlist)
            _importState.value = ImportPlaylistUiState.Saved(localId, playlist.title)
            onSaved(localId)
        }
    }
}
