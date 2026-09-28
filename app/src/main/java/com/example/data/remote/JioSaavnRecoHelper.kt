package com.example.data.remote

import com.example.data.mapper.SongMapper
import com.example.data.model.PlayableTrack
import com.example.data.model.Song
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject

/**
 * JioSaavn Recommendation & Radio Helper.
 * Parses raw JSON recommendation responses and converts them into high quality playable tracks.
 */
object JioSaavnRecoHelper {

    fun parseRecommendations(rawResponse: JsonElement?): List<Song> {
        val tracks = mutableListOf<Song>()
        if (rawResponse == null) return tracks

        val array: JsonArray? = when {
            rawResponse.isJsonArray -> rawResponse.asJsonArray
            rawResponse.isJsonObject -> {
                val obj = rawResponse.asJsonObject
                when {
                    obj.has("results") && obj.get("results").isJsonArray -> obj.getAsJsonArray("results")
                    obj.has("data") && obj.get("data").isJsonArray -> obj.getAsJsonArray("data")
                    obj.has("songs") && obj.get("songs").isJsonArray -> obj.getAsJsonArray("songs")
                    else -> null
                }
            }
            else -> null
        }

        if (array != null) {
            array.forEach { element ->
                if (element.isJsonObject) {
                    val songObj = element.asJsonObject
                    val song = SongMapper.map(songObj)
                    if (song.id.isNotBlank()) {
                        tracks.add(song)
                    }
                }
            }
        }
        return tracks
    }

    fun parseRecommendationsAsPlayable(rawResponse: JsonElement?): List<PlayableTrack> {
        return parseRecommendations(rawResponse).map { it.toPlayableTrack() }
    }
}
