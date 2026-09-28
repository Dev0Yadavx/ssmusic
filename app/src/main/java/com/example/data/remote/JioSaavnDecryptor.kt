package com.example.data.remote

import android.util.Base64
import android.util.Log
import java.nio.charset.StandardCharsets
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

object JioSaavnDecryptor {
    private const val TAG = "JioSaavnDecryptor"
    private const val DES_SECRET_KEY = "38346591"

    /**
     * Decrypts encrypted_media_url into playable 320kbps / 160kbps stream URL
     */
    fun getStreamUrl(encryptedMediaUrl: String?): String {
        if (encryptedMediaUrl.isNullOrEmpty()) return ""
        return try {
            val keyBytes = DES_SECRET_KEY.toByteArray(Charsets.UTF_8)
            val keySpec = SecretKeySpec(keyBytes, "DES")
            val cipher = Cipher.getInstance("DES/ECB/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, keySpec)

            val decodedBytes = Base64.decode(encryptedMediaUrl.trim(), Base64.DEFAULT)
            val decryptedBytes = cipher.doFinal(decodedBytes)
            var baseLink = String(decryptedBytes, Charsets.UTF_8).trim()
            baseLink = baseLink.filter { it >= ' ' && it <= '~' }

            if (baseLink.startsWith("http://")) {
                baseLink = "https://" + baseLink.substring(7)
            }

            // Direct 320kbps format
            baseLink.replace("_96.mp4", "_320.mp4")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to getStreamUrl: ${e.message}")
            ""
        }
    }

    /**
     * Decrypts JioSaavn encrypted_media_url into direct CDN stream url.
     */
    fun decrypt(encryptedMediaUrl: String?): String? {
        val result = getStreamUrl(encryptedMediaUrl)
        return if (result.isNotEmpty()) result else null
    }

    /**
     * High resolution 500x500 album art generator
     */
    fun getHdImage(url: String?): String {
        if (url.isNullOrEmpty()) return ""
        return url.replace("\\/", "/")
            .replace("http://", "https://")
            .replace("150x150", "500x500")
            .replace("50x50", "500x500")
            .replace("250x250", "500x500")
    }
}

