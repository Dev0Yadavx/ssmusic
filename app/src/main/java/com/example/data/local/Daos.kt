package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {

    // Liked Songs
    @Query("SELECT * FROM liked_songs ORDER BY addedAt DESC")
    fun getAllLikedSongs(): Flow<List<LikedSongEntity>>

    @Query("SELECT * FROM liked_songs WHERE id = :id LIMIT 1")
    suspend fun getLikedSong(id: String): LikedSongEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM liked_songs WHERE id = :id)")
    fun isLiked(id: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM liked_songs WHERE id = :id)")
    suspend fun isLikedSync(id: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLikedSong(song: LikedSongEntity)

    @Query("DELETE FROM liked_songs WHERE id = :id")
    suspend fun deleteLikedSong(id: String)

    // Recently Played
    @Query("SELECT * FROM recently_played ORDER BY playedAt DESC LIMIT 30")
    fun getRecentlyPlayed(): Flow<List<RecentlyPlayedEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecentlyPlayed(entity: RecentlyPlayedEntity)

    @Query("DELETE FROM recently_played WHERE songId = :songId")
    suspend fun deleteRecentlyPlayedBySongId(songId: String)

    @Query("DELETE FROM recently_played")
    suspend fun clearRecentlyPlayed()
}

@Dao
interface PlaylistDao {

    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :id LIMIT 1")
    suspend fun getPlaylistById(id: Long): PlaylistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Update
    suspend fun updatePlaylist(playlist: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: Long)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun deleteSongsByPlaylist(playlistId: Long)

    // Playlist songs
    @Query("SELECT * FROM playlist_songs ORDER BY addedAt DESC")
    fun getAllPlaylistSongs(): Flow<List<PlaylistSongEntity>>

    @Query("SELECT * FROM playlist_songs WHERE playlistId = :playlistId ORDER BY addedAt DESC")
    fun getSongsForPlaylist(playlistId: Long): Flow<List<PlaylistSongEntity>>

    @Query("SELECT * FROM playlist_songs WHERE playlistId = :playlistId ORDER BY addedAt DESC")
    suspend fun getSongsForPlaylistSync(playlistId: Long): List<PlaylistSongEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistSong(song: PlaylistSongEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistSongs(songs: List<PlaylistSongEntity>)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removeSongFromPlaylist(playlistId: Long, songId: String)
}

@Dao
interface SearchHistoryDao {

    @Query("SELECT * FROM search_history ORDER BY timestamp DESC LIMIT 15")
    fun getRecentSearches(): Flow<List<SearchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearch(search: SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE `query` = :query")
    suspend fun deleteSearch(query: String)

    @Query("DELETE FROM search_history")
    suspend fun clearHistory()
}

@Dao
interface DownloadedSongDao {

    @Query("SELECT * FROM downloaded_songs ORDER BY downloadedAt DESC")
    fun getAllDownloadedSongs(): Flow<List<DownloadedSongEntity>>

    @Query("SELECT * FROM downloaded_songs WHERE id = :id LIMIT 1")
    suspend fun getDownloadedSong(id: String): DownloadedSongEntity?

    @Query("SELECT * FROM downloaded_songs WHERE id = :id LIMIT 1")
    fun getDownloadedSongFlow(id: String): Flow<DownloadedSongEntity?>

    @Query("SELECT EXISTS(SELECT 1 FROM downloaded_songs WHERE id = :id AND downloadStatus = 'COMPLETED')")
    fun isDownloaded(id: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM downloaded_songs WHERE id = :id AND downloadStatus = 'COMPLETED')")
    suspend fun isDownloadedSync(id: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownloadedSong(song: DownloadedSongEntity)

    @Query("UPDATE downloaded_songs SET progress = :progress, downloadStatus = :status WHERE id = :id")
    suspend fun updateProgress(id: String, progress: Int, status: String)

    @Query("DELETE FROM downloaded_songs WHERE id = :id")
    suspend fun deleteDownloadedSong(id: String)

    @Query("DELETE FROM downloaded_songs")
    suspend fun clearAll()
}
