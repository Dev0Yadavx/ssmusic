package com.example.repository

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import com.example.data.local.*
import com.example.data.mapper.*
import com.example.data.model.*
import com.example.data.remote.JioSaavnApiService
import com.example.data.remote.NetworkResult
import com.example.data.remote.RetrofitClient
import com.example.data.remote.StreamUrlResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class MusicRepository(
    private val context: Context,
    private val api: JioSaavnApiService = RetrofitClient.apiService,
    private val db: SMusicDatabase = SMusicDatabase.getInstance(context)
) {
    private val tag = "MusicRepository"
    private val songDao = db.songDao()
    private val playlistDao = db.playlistDao()
    private val searchHistoryDao = db.searchHistoryDao()
    private val downloadedDao = db.downloadedSongDao()

    // Cached home shelves for instant startup & offline support
    @Volatile
    private var cachedShelves: List<MusicShelf> = emptyList()

    /**
     * Loads Home content with shelves (Trending, Albums, Playlists, etc.)
     */
    fun getHomeContent(languages: String = "hindi,english,punjabi,bhojpuri,haryanvi"): Flow<NetworkResult<List<MusicShelf>>> = flow {
        emit(NetworkResult.Loading)
        if (cachedShelves.isNotEmpty()) {
            emit(NetworkResult.Success(cachedShelves))
        }

        try {
            val response = api.getLaunchData(languages = languages)
            if (response.isSuccessful && response.body() != null) {
                val shelves = HomeMapper.map(response.body()!!)
                cachedShelves = shelves
                emit(NetworkResult.Success(shelves))
            } else {
                if (cachedShelves.isEmpty()) {
                    emit(NetworkResult.Error("Failed to load music: ${response.code()} ${response.message()}"))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(tag, "Home content error: ${e.message}", e)
            if (cachedShelves.isEmpty()) {
                emit(NetworkResult.Error("Unable to connect to music service. Please check your internet connection.", e))
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Search songs
     */
    suspend fun searchSongs(query: String, page: Int = 1): NetworkResult<List<Song>> = withContext(Dispatchers.IO) {
        try {
            val response = api.searchSongs(query = query, page = page)
            if (response.isSuccessful && response.body() != null) {
                val resultsArr = response.body()!!.getAsJsonArray("results")
                val songs = SongMapper.mapList(resultsArr)
                NetworkResult.Success(songs)
            } else {
                NetworkResult.Error("Search failed: ${response.message()}")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            NetworkResult.Error(e.message ?: "Unknown search error", e)
        }
    }

    /**
     * Search albums
     */
    suspend fun searchAlbums(query: String, page: Int = 1): NetworkResult<List<Album>> = withContext(Dispatchers.IO) {
        try {
            val response = api.searchAlbums(query = query, page = page)
            if (response.isSuccessful && response.body() != null) {
                val resultsArr = response.body()!!.getAsJsonArray("results")
                val albums = AlbumMapper.mapList(resultsArr)
                NetworkResult.Success(albums)
            } else {
                NetworkResult.Error("Album search failed")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            NetworkResult.Error(e.message ?: "Album search error", e)
        }
    }

    /**
     * Search playlists
     */
    suspend fun searchPlaylists(query: String, page: Int = 1): NetworkResult<List<Playlist>> = withContext(Dispatchers.IO) {
        try {
            val response = api.searchPlaylists(query = query, page = page)
            if (response.isSuccessful && response.body() != null) {
                val resultsArr = response.body()!!.getAsJsonArray("results")
                val playlists = PlaylistMapper.mapList(resultsArr)
                NetworkResult.Success(playlists)
            } else {
                NetworkResult.Error("Playlist search failed")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            NetworkResult.Error(e.message ?: "Playlist search error", e)
        }
    }

    /**
     * Search artists
     */
    suspend fun searchArtists(query: String, page: Int = 1): NetworkResult<List<Artist>> = withContext(Dispatchers.IO) {
        try {
            val response = api.searchArtists(query = query, page = page)
            if (response.isSuccessful && response.body() != null) {
                val resultsArr = response.body()!!.getAsJsonArray("results")
                val artists = ArtistMapper.mapList(resultsArr)
                NetworkResult.Success(artists)
            } else {
                NetworkResult.Error("Artist search failed")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            NetworkResult.Error(e.message ?: "Artist search error", e)
        }
    }

    /**
     * Autocomplete suggestions
     */
    suspend fun getAutocomplete(query: String): SearchResultCategory = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext SearchResultCategory(query)
        try {
            val response = api.getAutocomplete(query = query)
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                val songs = body.getAsJsonObject("songs")?.getAsJsonArray("data")?.let { SongMapper.mapList(it) } ?: emptyList()
                val albums = body.getAsJsonObject("albums")?.getAsJsonArray("data")?.let { AlbumMapper.mapList(it) } ?: emptyList()
                val artists = body.getAsJsonObject("artists")?.getAsJsonArray("data")?.let { ArtistMapper.mapList(it) } ?: emptyList()
                val playlists = body.getAsJsonObject("playlists")?.getAsJsonArray("data")?.let { PlaylistMapper.mapList(it) } ?: emptyList()
                return@withContext SearchResultCategory(query, songs, albums, artists, playlists)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(tag, "Autocomplete error: ${e.message}")
        }
        SearchResultCategory(query)
    }

    private fun isRealAlbum(album: Album?): Boolean {
        if (album == null) return false
        val badPhrases = listOf("sample trailer", "testing", "sample trailer - testing")
        if (badPhrases.any { album.title.contains(it, ignoreCase = true) }) return false
        if (album.songs.any { song -> badPhrases.any { song.title.contains(it, ignoreCase = true) } }) return false
        return album.songs.isNotEmpty()
    }

    /**
     * Album details (handles numeric album ID, string token, single-song albums, and title-search fallback)
     */
    suspend fun getAlbumDetails(
        albumId: String,
        titleFallback: String = "",
        artistFallback: String = "",
        artworkFallback: String = ""
    ): NetworkResult<Album> = withContext(Dispatchers.IO) {
        try {
            var album: Album? = null
            val isNumeric = albumId.all { it.isDigit() }

            // 1. Primary endpoint: webapi.get by id or by token (modern JioSaavn API)
            val primaryResponse = if (isNumeric) {
                api.getAlbumById(id = albumId)
            } else {
                api.getAlbumByToken(token = albumId)
            }

            if (primaryResponse.isSuccessful && primaryResponse.body() != null) {
                val mapped = AlbumMapper.map(primaryResponse.body()!!)
                if (isRealAlbum(mapped)) {
                    album = mapped
                }
            }

            // 2. Fallback: album.getDetailsSimple (numeric) or token fallback
            if (album == null) {
                val fallbackResponse = if (isNumeric) {
                    api.getAlbumDetailsSimple(albumId = albumId)
                } else {
                    api.getAlbumById(id = albumId)
                }
                if (fallbackResponse.isSuccessful && fallbackResponse.body() != null) {
                    val mapped = AlbumMapper.map(fallbackResponse.body()!!)
                    if (isRealAlbum(mapped)) {
                        album = mapped
                    }
                }
            }

            // 3. Fallback: content.getAlbumDetails (legacy) or getAlbumByToken
            if (album == null) {
                val tokenResponse = if (isNumeric) {
                    api.getAlbumDetails(albumId = albumId)
                } else {
                    api.getAlbumByToken(token = albumId)
                }
                if (tokenResponse.isSuccessful && tokenResponse.body() != null) {
                    val mapped = AlbumMapper.map(tokenResponse.body()!!)
                    if (isRealAlbum(mapped)) {
                        album = mapped
                    }
                }
            }

            // 4. Fallback: Single track lookup (in case the release is a single song ID)
            if (album == null && albumId.isNotBlank()) {
                val song = getSongDetails(albumId)
                if (song != null && !song.title.contains("sample trailer", ignoreCase = true)) {
                    album = Album(
                        id = song.albumId.ifBlank { albumId },
                        title = if (titleFallback.isNotBlank()) titleFallback else song.album.ifBlank { song.title },
                        artist = if (artistFallback.isNotBlank()) artistFallback else song.artist,
                        artwork = if (artworkFallback.isNotBlank()) artworkFallback else song.artwork,
                        year = song.year,
                        songCount = 1,
                        songs = listOf(song)
                    )
                }
            }

            // 5. Fallback: Search by title query (e.g. "Aara Ke Sara")
            if (album == null && titleFallback.isNotBlank()) {
                val cleanTitle = titleFallback.replace(Regex("\\(From [^)]*\\)"), "").trim()
                val searchResult = searchSongs(cleanTitle)
                if (searchResult is NetworkResult.Success && searchResult.data.isNotEmpty()) {
                    val validSongs = searchResult.data.filter { !it.title.contains("sample trailer", ignoreCase = true) }
                    if (validSongs.isNotEmpty()) {
                        val first = validSongs.first()
                        album = Album(
                            id = albumId,
                            title = titleFallback,
                            artist = if (artistFallback.isNotBlank()) artistFallback else first.artist,
                            artwork = if (artworkFallback.isNotBlank()) artworkFallback else first.artwork,
                            year = first.year,
                            songCount = validSongs.size,
                            songs = validSongs
                        )
                    }
                }
            }

            if (album != null) {
                NetworkResult.Success(album)
            } else {
                NetworkResult.Error("Failed to load album tracks")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            NetworkResult.Error(e.message ?: "Failed to load album", e)
        }
    }

    /**
     * Playlist details (handles numeric listid, token fallbacks, and local user playlists)
     */
    suspend fun getPlaylistDetails(playlistId: String): NetworkResult<Playlist> = withContext(Dispatchers.IO) {
        try {
            // Check if this is a local user playlist
            if (playlistId.startsWith("local_")) {
                val localId = playlistId.removePrefix("local_").toLongOrNull()
                if (localId != null) {
                    val entity = playlistDao.getPlaylistById(localId)
                    if (entity != null) {
                        val songs = playlistDao.getSongsForPlaylistSync(localId)
                        val songsMapped = songs.map { it.toSong() }
                        val firstArt = songsMapped.firstOrNull { it.artwork.isNotBlank() }?.artwork ?: ""
                        return@withContext NetworkResult.Success(
                            Playlist(
                                id = playlistId,
                                title = entity.name,
                                description = entity.description,
                                artwork = firstArt,
                                songCount = songsMapped.size,
                                songs = songsMapped
                            )
                        )
                    }
                }
            }

            var playlist: Playlist? = null

            val isNumeric = playlistId.all { it.isDigit() }
            val firstResponse = if (isNumeric) {
                api.getPlaylistDetails(listId = playlistId)
            } else {
                api.getPlaylistByToken(token = playlistId)
            }

            if (firstResponse.isSuccessful && firstResponse.body() != null) {
                val mapped = PlaylistMapper.map(firstResponse.body()!!)
                if (mapped.songs.isNotEmpty()) {
                    playlist = mapped
                }
            }

            if (playlist == null || playlist.songs.isEmpty()) {
                val fallbackResponse = if (isNumeric) {
                    api.getPlaylistByToken(token = playlistId)
                } else {
                    api.getPlaylistDetails(listId = playlistId)
                }
                if (fallbackResponse.isSuccessful && fallbackResponse.body() != null) {
                    val mapped = PlaylistMapper.map(fallbackResponse.body()!!)
                    if (mapped.songs.isNotEmpty() || mapped.title != "Playlist") {
                        playlist = mapped
                    }
                }
            }

            if (playlist != null) {
                NetworkResult.Success(playlist)
            } else {
                NetworkResult.Error("Failed to load playlist details")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            NetworkResult.Error(e.message ?: "Failed to load playlist", e)
        }
    }

    /**
     * Artist details (handles numeric artistId, artist token, or artist name search)
     */
    suspend fun getArtistDetails(artistId: String): NetworkResult<Artist> = withContext(Dispatchers.IO) {
        try {
            if (artistId.all { it.isDigit() }) {
                val response = api.getArtistDetails(artistId = artistId)
                if (response.isSuccessful && response.body() != null) {
                    val mapped = ArtistMapper.map(response.body()!!)
                    if (mapped.name.isNotBlank()) {
                        return@withContext NetworkResult.Success(mapped)
                    }
                }
            }

            // Search artist by name or token
            val searchRes = searchArtists(artistId)
            if (searchRes is NetworkResult.Success && searchRes.data.isNotEmpty()) {
                val found = searchRes.data.first()
                if (found.id.isNotBlank() && found.id.all { it.isDigit() }) {
                    val detailResp = api.getArtistDetails(artistId = found.id)
                    if (detailResp.isSuccessful && detailResp.body() != null) {
                        val mapped = ArtistMapper.map(detailResp.body()!!)
                        if (mapped.name.isNotBlank()) {
                            return@withContext NetworkResult.Success(mapped)
                        }
                    }
                }
                // Fallback using found artist and song search for top songs
                val songsRes = searchSongs(found.name)
                val topSongs = if (songsRes is NetworkResult.Success) songsRes.data else emptyList()
                return@withContext NetworkResult.Success(
                    Artist(
                        id = found.id.ifBlank { artistId },
                        name = found.name,
                        image = found.image,
                        role = "Singer / Performer",
                        topSongs = topSongs,
                        topAlbums = emptyList()
                    )
                )
            }

            // Fallback: search songs by artist name to build artist profile
            val songsRes = searchSongs(artistId)
            if (songsRes is NetworkResult.Success && songsRes.data.isNotEmpty()) {
                val songs = songsRes.data
                val firstArtwork = songs.firstOrNull { it.artwork.isNotBlank() }?.artwork ?: ""
                return@withContext NetworkResult.Success(
                    Artist(
                        id = artistId,
                        name = artistId,
                        image = firstArtwork,
                        role = "Featured Artist",
                        topSongs = songs,
                        topAlbums = emptyList()
                    )
                )
            }

            NetworkResult.Error("Failed to load artist details")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            NetworkResult.Error(e.message ?: "Failed to load artist", e)
        }
    }

    /**
     * Song details (with numeric PID and string Token fallback)
     */
    suspend fun getSongDetails(songId: String): Song? = withContext(Dispatchers.IO) {
        try {
            // First try by PID if numeric, otherwise try by Token
            val response = if (songId.all { it.isDigit() }) {
                api.getSongByPid(pid = songId)
            } else {
                api.getSongByToken(token = songId)
            }
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                val songsArr = body.getAsJsonArray("songs") ?: body.getAsJsonArray("results")
                if (songsArr != null && songsArr.size() > 0) {
                    return@withContext SongMapper.map(songsArr.get(0).asJsonObject)
                }
            }
            // Fallback: try the other method if first returned empty
            if (songId.all { it.isDigit() }) {
                val fallbackResp = api.getSongByToken(token = songId)
                if (fallbackResp.isSuccessful && fallbackResp.body() != null) {
                    val fallbackArr = fallbackResp.body()!!.getAsJsonArray("songs")
                    if (fallbackArr != null && fallbackArr.size() > 0) {
                        return@withContext SongMapper.map(fallbackArr.get(0).asJsonObject)
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(tag, "Song details error: ${e.message}")
        }
        null
    }

    /**
     * Recommendations / Autoplay next songs
     */
    suspend fun getRecommendations(songId: String): List<Song> = withContext(Dispatchers.IO) {
        try {
            val response = api.getRecommendations(pid = songId)
            if (response.isSuccessful && response.body() != null) {
                val songs = RecommendationMapper.map(response.body()!!)
                if (songs.isNotEmpty()) return@withContext songs
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(tag, "Recommendations error: ${e.message}")
        }
        // Fallback: use trending songs or cached home shelf if available
        cachedShelves.firstOrNull { it.id == "trending" }?.items?.mapNotNull {
            if (it is ShelfItem.SongItem) it.song else null
        } ?: emptyList()
    }

    /**
     * Lyrics
     */
    suspend fun getLyrics(lyricsId: String, song: Song?): String = withContext(Dispatchers.IO) {
        if (lyricsId.isNotBlank()) {
            try {
                val response = api.getLyrics(lyricsId = lyricsId)
                val body = response.body()
                if (response.isSuccessful && body != null) {
                    val text = LyricsMapper.map(body)
                    if (text.isNotBlank()) return@withContext text
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(tag, "Lyrics endpoint error: ${e.message}")
            }
        }

        // Fallback to song's lyrics snippet if present
        if (!song?.lyricsSnippet.isNullOrBlank()) {
            return@withContext song!!.lyricsSnippet
        }
        "Lyrics not available for this song."
    }

    /**
     * Stream URL resolution
     */
    suspend fun resolveStreamUrl(
        track: PlayableTrack,
        quality: StreamUrlResolver.AudioQuality = StreamUrlResolver.AudioQuality.HIGH
    ): String? = StreamUrlResolver.resolve(track, quality)

    // --- LOCAL PERSISTENCE ---

    val likedSongs: Flow<List<PlayableTrack>> = songDao.getAllLikedSongs()
        .map { list -> list.map { it.toPlayableTrack() } }
        .flowOn(Dispatchers.IO)

    fun isSongLiked(id: String): Flow<Boolean> = songDao.isLiked(id).flowOn(Dispatchers.IO)

    suspend fun toggleLike(track: PlayableTrack) = withContext(Dispatchers.IO) {
        val currentlyLiked = songDao.isLikedSync(track.id)
        if (currentlyLiked) {
            songDao.deleteLikedSong(track.id)
        } else {
            songDao.insertLikedSong(
                LikedSongEntity(
                    id = track.id,
                    title = track.title,
                    artist = track.artist,
                    album = track.album,
                    albumId = track.albumId,
                    artwork = track.artwork,
                    duration = track.duration,
                    streamUrl = track.streamUrl,
                    encryptedMediaUrl = track.encryptedMediaUrl,
                    mediaPreviewUrl = track.mediaPreviewUrl
                )
            )
        }
    }

    val recentlyPlayed: Flow<List<PlayableTrack>> = songDao.getRecentlyPlayed()
        .map { list -> list.map { it.toPlayableTrack() } }
        .flowOn(Dispatchers.IO)

    suspend fun recordRecentlyPlayed(track: PlayableTrack) = withContext(Dispatchers.IO) {
        songDao.deleteRecentlyPlayedBySongId(track.id)
        songDao.insertRecentlyPlayed(
            RecentlyPlayedEntity(
                songId = track.id,
                title = track.title,
                artist = track.artist,
                album = track.album,
                albumId = track.albumId,
                artwork = track.artwork,
                duration = track.duration,
                streamUrl = track.streamUrl,
                encryptedMediaUrl = track.encryptedMediaUrl,
                mediaPreviewUrl = track.mediaPreviewUrl,
                playedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun clearRecentlyPlayed() = withContext(Dispatchers.IO) {
        songDao.clearRecentlyPlayed()
    }

    // --- DOWNLOADED SONGS & OFFLINE PLAYBACK ---

    val downloadedSongs: Flow<List<PlayableTrack>> = downloadedDao.getAllDownloadedSongs()
        .map { list ->
            list.filter { it.downloadStatus == "COMPLETED" && (it.localFilePath.isBlank() || java.io.File(it.localFilePath).exists()) }
                .map { it.toPlayableTrack() }
        }
        .flowOn(Dispatchers.IO)

    fun isSongDownloaded(id: String): Flow<Boolean> = downloadedDao.isDownloaded(id).flowOn(Dispatchers.IO)

    suspend fun isSongDownloadedSync(id: String): Boolean = withContext(Dispatchers.IO) {
        downloadedDao.isDownloadedSync(id)
    }

    suspend fun getDownloadedSong(id: String): DownloadedSongEntity? = withContext(Dispatchers.IO) {
        downloadedDao.getDownloadedSong(id)
    }

    suspend fun deleteDownload(id: String) = withContext(Dispatchers.IO) {
        val entity = downloadedDao.getDownloadedSong(id)
        if (entity != null) {
            if (entity.localFilePath.isNotBlank()) {
                val f = java.io.File(entity.localFilePath)
                if (f.exists()) f.delete()
            }
            if (entity.localArtworkPath.isNotBlank()) {
                val artUri = Uri.parse(entity.localArtworkPath)
                artUri.path?.let { p ->
                    val af = java.io.File(p)
                    if (af.exists()) af.delete()
                }
            }
            downloadedDao.deleteDownloadedSong(id)
        }
    }

    // Playlists
    val userPlaylists: Flow<List<PlaylistEntity>> = playlistDao.getAllPlaylists().flowOn(Dispatchers.IO)

    val userPlaylistsWithPreviews: Flow<List<com.example.data.model.UserPlaylistSummary>> =
        kotlinx.coroutines.flow.combine(
            playlistDao.getAllPlaylists(),
            playlistDao.getAllPlaylistSongs()
        ) { playlists, allSongs ->
            val songsByPlaylist = allSongs.groupBy { it.playlistId }
            playlists.map { playlist ->
                val songs = songsByPlaylist[playlist.id] ?: emptyList()
                val previews = songs.mapNotNull { it.artwork.takeIf { art -> art.isNotBlank() } }
                    .distinct()
                    .take(4)
                com.example.data.model.UserPlaylistSummary(
                    id = playlist.id,
                    name = playlist.name,
                    description = playlist.description,
                    songCount = songs.size,
                    previewArtworks = previews,
                    createdAt = playlist.createdAt
                )
            }
        }.flowOn(Dispatchers.IO)

    suspend fun createPlaylist(name: String, description: String = ""): Long = withContext(Dispatchers.IO) {
        playlistDao.insertPlaylist(PlaylistEntity(name = name, description = description))
    }

    suspend fun deletePlaylist(id: Long) = withContext(Dispatchers.IO) {
        playlistDao.deletePlaylist(id)
        playlistDao.deleteSongsByPlaylist(id)
    }

    fun getPlaylistSongs(playlistId: Long): Flow<List<PlayableTrack>> =
        playlistDao.getSongsForPlaylist(playlistId)
            .map { list -> list.map { it.toPlayableTrack() } }
            .flowOn(Dispatchers.IO)

    suspend fun addSongToPlaylist(playlistId: Long, track: PlayableTrack) = withContext(Dispatchers.IO) {
        playlistDao.insertPlaylistSong(
            PlaylistSongEntity(
                playlistId = playlistId,
                songId = track.id,
                title = track.title,
                artist = track.artist,
                album = track.album,
                albumId = track.albumId,
                artwork = track.artwork,
                duration = track.duration,
                streamUrl = track.streamUrl,
                encryptedMediaUrl = track.encryptedMediaUrl
            )
        )
    }

    suspend fun removeSongFromPlaylist(playlistId: Long, songId: String) = withContext(Dispatchers.IO) {
        playlistDao.removeSongFromPlaylist(playlistId, songId)
    }

    /**
     * Import a JioSaavn playlist into user's local Room playlists
     */
    suspend fun importJioSaavnPlaylistToLocal(playlist: Playlist): Long = withContext(Dispatchers.IO) {
        val playlistId = createPlaylist(
            name = playlist.title.ifBlank { "smusic playlist" },
            description = playlist.description.ifBlank { "Imported from JioSaavn (${playlist.songs.size} tracks)" }
        )
        val entities = playlist.songs.map { song ->
            PlaylistSongEntity(
                playlistId = playlistId,
                songId = song.id,
                title = song.title,
                artist = song.artist,
                album = song.album,
                albumId = song.albumId,
                artwork = song.artwork,
                duration = song.duration,
                streamUrl = song.streamUrl,
                encryptedMediaUrl = song.encryptedMediaUrl
            )
        }
        if (entities.isNotEmpty()) {
            playlistDao.insertPlaylistSongs(entities)
        }
        playlistId
    }

    /**
     * Fetch playlist using JioSaavn token or URL
     */
    suspend fun fetchPlaylistByToken(tokenOrUrl: String): NetworkResult<Playlist> = withContext(Dispatchers.IO) {
        val token = com.musicx.app.utils.PlaylistLinkParser.extractToken(tokenOrUrl) ?: tokenOrUrl.trim()
        if (token.isBlank()) {
            return@withContext NetworkResult.Error("Invalid JioSaavn playlist link or token")
        }
        getPlaylistDetails(token)
    }

    // Search History
    val searchHistory: Flow<List<String>> = searchHistoryDao.getRecentSearches()
        .map { list -> list.map { it.query } }
        .flowOn(Dispatchers.IO)

    suspend fun addSearchQuery(query: String) = withContext(Dispatchers.IO) {
        if (query.isNotBlank()) {
            searchHistoryDao.deleteSearch(query.trim())
            searchHistoryDao.insertSearch(SearchHistoryEntity(query = query.trim()))
        }
    }

    suspend fun clearSearchHistory() = withContext(Dispatchers.IO) {
        searchHistoryDao.clearHistory()
    }

    /**
     * Scans local audio files from MediaStore
     */
    suspend fun scanLocalAudio(): List<PlayableTrack> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<PlayableTrack>()
        try {
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.YEAR,
                MediaStore.Audio.Media.ALBUM_ID
            )

            val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
            val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val yearCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)

                while (cursor.moveToNext()) {
                    val mediaId = cursor.getLong(idCol)
                    val title = cursor.getString(titleCol) ?: "Unknown Track"
                    val artist = cursor.getString(artistCol) ?: "Unknown Artist"
                    val album = cursor.getString(albumCol) ?: "Unknown Album"
                    val durationMs = cursor.getLong(durationCol)
                    val year = cursor.getString(yearCol) ?: ""
                    val albumId = cursor.getLong(albumIdCol)

                    val contentUri: Uri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        mediaId
                    )

                    // Album art URI
                    val artworkUri = ContentUris.withAppendedId(
                        Uri.parse("content://media/external/audio/albumart"),
                        albumId
                    ).toString()

                    tracks.add(
                        PlayableTrack(
                            id = "local_$mediaId",
                            title = title,
                            artist = artist,
                            album = album,
                            artwork = artworkUri,
                            duration = durationMs / 1000,
                            streamUrl = contentUri.toString(),
                            source = TrackSource.LOCAL,
                            year = year
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Local audio scan failed: ${e.message}", e)
        }
        tracks
    }
}
