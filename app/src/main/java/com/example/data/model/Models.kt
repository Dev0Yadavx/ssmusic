package com.example.data.model

data class Song(
    val id: String,
    val token: String = "",
    val title: String,
    val artist: String,
    val album: String = "",
    val albumId: String = "",
    val artwork: String = "",
    val duration: Long = 0L,
    val encryptedMediaUrl: String = "",
    val streamUrl: String = "",
    val mediaPreviewUrl: String = "",
    val lyricsId: String = "",
    val lyricsSnippet: String = "",
    val language: String = "",
    val year: String = ""
) {
    fun toPlayableTrack(source: TrackSource = TrackSource.JIOSAAVN): PlayableTrack {
        return PlayableTrack(
            id = id,
            token = token,
            title = title,
            artist = artist,
            album = album,
            albumId = albumId,
            artwork = artwork,
            duration = duration,
            streamUrl = streamUrl,
            encryptedMediaUrl = encryptedMediaUrl,
            mediaPreviewUrl = mediaPreviewUrl,
            lyricsId = lyricsId,
            lyricsSnippet = lyricsSnippet,
            source = source,
            year = year,
            language = language
        )
    }
}

data class Album(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val artist: String = "",
    val artwork: String = "",
    val year: String = "",
    val songCount: Int = 0,
    val songs: List<Song> = emptyList()
)

data class Artist(
    val id: String,
    val name: String,
    val image: String = "",
    val role: String = "",
    val topSongs: List<Song> = emptyList(),
    val topAlbums: List<Album> = emptyList()
)

data class Playlist(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val description: String = "",
    val artwork: String = "",
    val songCount: Int = 0,
    val songs: List<Song> = emptyList()
)

data class UserPlaylistSummary(
    val id: Long,
    val name: String,
    val description: String = "",
    val songCount: Int = 0,
    val previewArtworks: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

enum class ShelfType {
    SONG_HORIZONTAL,
    ALBUM_HORIZONTAL,
    PLAYLIST_HORIZONTAL,
    ARTIST_HORIZONTAL,
    SONG_GRID,
    ALBUM_GRID
}

sealed class ShelfItem {
    data class SongItem(val song: Song) : ShelfItem()
    data class AlbumItem(val album: Album) : ShelfItem()
    data class PlaylistItem(val playlist: Playlist) : ShelfItem()
    data class ArtistItem(val artist: Artist) : ShelfItem()
}

data class MusicShelf(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val type: ShelfType,
    val items: List<ShelfItem>
)

data class SearchResultCategory(
    val query: String,
    val songs: List<Song> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val playlists: List<Playlist> = emptyList()
)
