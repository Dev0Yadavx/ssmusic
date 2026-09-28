package com.example.data.remote

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface JioSaavnApiService {

    // 1. Home Dashboard
    @GET("api.php?__call=webapi.getLaunchData&_format=json&_marker=0&api_version=4&ctx=web6dot0")
    suspend fun getHomeLaunch(
        @Query("languages") langs: String = "hindi,bhojpuri,haryanvi"
    ): Response<JsonObject>

    @GET("api.php?__call=webapi.getLaunchData&_format=json&_marker=0&api_version=4&ctx=web6dot0")
    suspend fun getHome(
        @Query("languages") languages: String = "hindi,english,punjabi,bhojpuri,haryanvi"
    ): Response<JsonObject>

    @GET("api.php?__call=webapi.getLaunchData&_format=json&_marker=0&api_version=4&ctx=web6dot0")
    suspend fun getLaunchData(
        @Query("languages") languages: String = "hindi,english,punjabi,bhojpuri,haryanvi"
    ): Response<JsonObject>

    // 2. Auto-Play Recommendations Queue
    @GET("api.php?__call=reco.getreco&_format=json&_marker=0&api_version=4&ctx=web6dot0")
    suspend fun getAutoPlayQueue(
        @Query("pid") songId: String
    ): Response<com.google.gson.JsonElement>

    // 3. Search Songs Only
    @GET("api.php?__call=search.getResults&_format=json&_marker=0&api_version=4&ctx=web6dot0")
    suspend fun searchSongsOnly(
        @Query("q") query: String,
        @Query("p") page: Int = 1,
        @Query("n") limit: Int = 25
    ): Response<JsonObject>

    // 4. Search Albums Only
    @GET("api.php?__call=search.getAlbumResults&_format=json&_marker=0&api_version=4&ctx=web6dot0")
    suspend fun searchAlbumsOnly(
        @Query("q") query: String,
        @Query("p") page: Int = 1,
        @Query("n") limit: Int = 20
    ): Response<JsonObject>

    // 5. Search Playlists Only
    @GET("api.php?__call=search.getPlaylistResults&_format=json&_marker=0&api_version=4&ctx=web6dot0")
    suspend fun searchPlaylistsOnly(
        @Query("q") query: String,
        @Query("p") page: Int = 1,
        @Query("n") limit: Int = 20
    ): Response<JsonObject>

    // 2. Trending Hits
    @GET("api.php?__call=webapi.getLaunchData&_format=json&_marker=0&api_version=4&ctx=web6dot0")
    suspend fun getTrending(
        @Query("languages") languages: String = "hindi,bhojpuri,haryanvi"
    ): Response<JsonObject>

    // 3. Search Autocomplete
    @GET("api.php?__call=autocomplete.get&_format=json&_marker=0&api_version=4&ctx=web6dot0")
    suspend fun autocomplete(
        @Query("query") query: String
    ): Response<JsonObject>

    @GET("api.php?__call=autocomplete.get&_format=json&_marker=0&api_version=4&ctx=web6dot0")
    suspend fun getAutocomplete(
        @Query("query") query: String
    ): Response<JsonObject>

    // 4. Search Songs
    @GET("api.php?__call=search.getResults&_format=json&_marker=0&api_version=4&ctx=web6dot0")
    suspend fun searchSongs(
        @Query("q") query: String,
        @Query("p") page: Int = 1,
        @Query("n") limit: Int = 20
    ): Response<JsonObject>

    // 5. Search Albums
    @GET("api.php?__call=search.getAlbumResults&_format=json&_marker=0&api_version=4&ctx=web6dot0")
    suspend fun searchAlbums(
        @Query("q") query: String,
        @Query("p") page: Int = 1,
        @Query("n") limit: Int = 20
    ): Response<JsonObject>

    // 6. Search Playlists
    @GET("api.php?__call=search.getPlaylistResults&_format=json&_marker=0&api_version=4&ctx=web6dot0")
    suspend fun searchPlaylists(
        @Query("q") query: String,
        @Query("p") page: Int = 1,
        @Query("n") limit: Int = 20
    ): Response<JsonObject>

    // 6.1 Search Artists
    @GET("api.php?__call=search.getArtistResults&_format=json&_marker=0&api_version=4&ctx=web6dot0")
    suspend fun searchArtists(
        @Query("q") query: String,
        @Query("p") page: Int = 1,
        @Query("n") limit: Int = 20
    ): Response<JsonObject>

    // 7. Song Details (Numeric PID)
    @GET("api.php?__call=song.getDetails&_format=json&_marker=0&api_version=4&ctx=web6dot0")
    suspend fun getSongByPid(
        @Query("pids") pid: String
    ): Response<JsonObject>

    @GET("api.php?__call=song.getDetails&_format=json&_marker=0&api_version=4&ctx=web6dot0")
    suspend fun getSongDetails(
        @Query("pids") pids: String
    ): Response<JsonObject>

    // 7.1 Song Details (String Token Fallback - e.g. "c9_7sW8q")
    @GET("api.php?__call=webapi.get&type=song&_format=json&_marker=0&api_version=4&ctx=web6dot0")
    suspend fun getSongByToken(
        @Query("token") token: String
    ): Response<JsonObject>

    // 8. Album Details
    @GET("api.php?__call=webapi.get&type=album&_format=json&_marker=0&api_version=4&ctx=web6dot0")
    suspend fun getAlbumById(
        @Query("id") id: String
    ): Response<JsonObject>

    @GET("api.php?__call=webapi.get&type=album&_format=json&_marker=0&api_version=4&ctx=web6dot0")
    suspend fun getAlbumByToken(
        @Query("token") token: String
    ): Response<JsonObject>

    @GET("api.php?__call=album.getDetails&_format=json&_marker=0&api_version=4&ctx=web6dot0")
    suspend fun getAlbumDetailsSimple(
        @Query("albumid") albumId: String
    ): Response<JsonObject>

    @GET("api.php?__call=content.getAlbumDetails&_format=json&_marker=0&api_version=4&ctx=web6dot0")
    suspend fun getAlbumDetails(
        @Query("albumid") albumId: String
    ): Response<JsonObject>

    // 9. Playlist Details
    @GET("api.php?__call=playlist.getDetails&_format=json&_marker=0&api_version=4&ctx=web6dot0")
    suspend fun getPlaylistDetails(
        @Query("listid") listId: String
    ): Response<JsonObject>

    @GET("api.php?__call=webapi.get&type=playlist&_format=json&_marker=0&api_version=4&ctx=web6dot0")
    suspend fun getPlaylistByToken(
        @Query("token") token: String
    ): Response<JsonObject>

    // 10. Artist Details & Top Hits
    @GET("api.php?__call=artist.getArtistPageDetails&_format=json&_marker=0&api_version=4&ctx=web6dot0")
    suspend fun getArtistDetails(
        @Query("artistId") artistId: String,
        @Query("n") limit: Int = 20
    ): Response<JsonObject>

    // 11. Lyrics
    @GET("api.php?__call=lyrics.getLyrics&_format=json&_marker=0&api_version=4&ctx=web6dot0")
    suspend fun getLyrics(
        @Query("lyrics_id") lyricsId: String
    ): Response<JsonObject>

    // 12. Recommendations / Radio Queue
    @GET("api.php?__call=reco.getreco&_format=json&_marker=0&api_version=4&ctx=web6dot0")
    suspend fun getRecommendations(
        @Query("pid") pid: String
    ): Response<JsonElement>
}
