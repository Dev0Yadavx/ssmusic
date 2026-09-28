package com.musicx.app.network

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface JioSaavnDirectApi {

    // 1. Home: Jisme 'artist_recos' aur 'browse_discover' (Community) dono milenge
    @GET("api.php?__call=webapi.getLaunchData&_format=json&_marker=0&api_version=4&ctx=web6dot0")
    suspend fun getHomeLaunch(
        @Query("languages") langs: String = "hindi,bhojpuri,haryanvi"
    ): Response<JsonObject>

    // 2. Auto-Play Recommendations Queue
    @GET("api.php?__call=reco.getreco&_format=json&_marker=0&api_version=4&ctx=web6dot0")
    suspend fun getAutoPlayQueue(
        @Query("pid") songId: String
    ): Response<JsonArray>

    // 3. Search Filter: Direct Songs Search
    @GET("api.php?__call=search.getResults&_format=json&_marker=0&api_version=4&ctx=web6dot0")
    suspend fun searchSongsOnly(
        @Query("q") query: String,
        @Query("p") page: Int = 1,
        @Query("n") limit: Int = 25
    ): Response<JsonObject>

    // 4. Search Filter: Albums Only
    @GET("api.php?__call=search.getAlbumResults&_format=json&_marker=0&api_version=4&ctx=web6dot0")
    suspend fun searchAlbumsOnly(
        @Query("q") query: String,
        @Query("p") page: Int = 1,
        @Query("n") limit: Int = 20
    ): Response<JsonObject>

    // 5. Search Filter: Playlists Only
    @GET("api.php?__call=search.getPlaylistResults&_format=json&_marker=0&api_version=4&ctx=web6dot0")
    suspend fun searchPlaylistsOnly(
        @Query("q") query: String,
        @Query("p") page: Int = 1,
        @Query("n") limit: Int = 20
    ): Response<JsonObject>
}
