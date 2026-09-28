package com.example.data.remote

import android.util.Log
import com.example.data.model.PlayableTrack
import com.example.data.model.TrackSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object StreamUrlResolver {
    private const val TAG = "StreamUrlResolver"

    enum class AudioQuality(val bitrateSuffix: String) {
        HIGH("_320"),
        MEDIUM("_160"),
        LOW("_96")
    }

    /**
     * Resolves the playable URL for a given track, prioritizing user quality preference
     * and falling back to lower qualities or preview URLs if needed.
     */
    suspend fun resolve(
        track: PlayableTrack,
        preferredQuality: AudioQuality = AudioQuality.HIGH
    ): String? = withContext(Dispatchers.IO) {
        if (track.source == TrackSource.LOCAL) {
            return@withContext track.streamUrl
        }

        // If already resolved and not empty
        if (track.streamUrl.isNotBlank() && !track.streamUrl.startsWith("encrypted:")) {
            return@withContext track.streamUrl
        }

        val encrypted = track.encryptedMediaUrl
        if (encrypted.isNotBlank()) {
            val decrypted = JioSaavnDecryptor.decrypt(encrypted)
            if (!decrypted.isNullOrBlank()) {
                val candidate = transformQuality(decrypted, preferredQuality)
                return@withContext candidate
            }
        }

        // Fallback to preview url
        if (track.mediaPreviewUrl.isNotBlank()) {
            var preview = track.mediaPreviewUrl.replace("\\/", "/")
            if (preview.startsWith("http://")) {
                preview = "https://" + preview.substring(7)
            }
            return@withContext preview
        }

        Log.w(TAG, "No valid stream URL found for track: ${track.title} (${track.id})")
        null
    }

    fun transformQuality(baseUrl: String, quality: AudioQuality): String {
        val target = quality.bitrateSuffix
        var url = baseUrl
        when {
            url.contains("_96.") -> url = url.replace("_96.", "$target.")
            url.contains("_160.") -> url = url.replace("_160.", "$target.")
            url.contains("_320.") -> url = url.replace("_320.", "$target.")
        }
        return url
    }

    /**
     * Generates a fallback candidate when playback error occurs with current URL.
     */
    fun getFallbackUrl(currentUrl: String): String? {
        return when {
            currentUrl.contains("_320.") -> currentUrl.replace("_320.", "_160.")
            currentUrl.contains("_160.") -> currentUrl.replace("_160.", "_96.")
            else -> null
        }
    }
}
