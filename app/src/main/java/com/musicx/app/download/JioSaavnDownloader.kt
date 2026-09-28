package com.musicx.app.download

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

object JioSaavnDownloader {

    private const val DES_SECRET_KEY = "38346591"

    /**
     * 1. Encrypted URL ko direct CDN Download Endpoint me convert karta hai
     * Output: https://aac.saavncdn.com/..._320.mp4
     */
    fun getDownloadEndpoint(encryptedMediaUrl: String?, bitrate: String = "320"): String {
        if (encryptedMediaUrl.isNullOrEmpty()) return ""
        return try {
            val keyBytes = DES_SECRET_KEY.toByteArray(Charsets.UTF_8)
            val keySpec = SecretKeySpec(keyBytes, "DES")
            val cipher = Cipher.getInstance("DES/ECB/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, keySpec)

            val decodedBytes = Base64.decode(encryptedMediaUrl, Base64.DEFAULT)
            val decryptedBytes = cipher.doFinal(decodedBytes)
            val rawCdnUrl = String(decryptedBytes, Charsets.UTF_8)

            // Bitrate swap: _96.mp4 -> _320.mp4 / _160.mp4
            when (bitrate) {
                "320" -> rawCdnUrl.replace("_96.mp4", "_320.mp4")
                "160" -> rawCdnUrl.replace("_96.mp4", "_160.mp4")
                else -> rawCdnUrl.replace("_96.mp4", "_96.mp4")
            }
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * 2. Phone ke Download folder me direct MP3 save karne ka function
     */
    fun startDownload(
        context: Context,
        songTitle: String,
        artistName: String,
        encryptedMediaUrl: String?,
        bitrate: String = "320"
    ): Long {
        // Step 1: Direct CDN Endpoint nikalna
        val downloadUrl = getDownloadEndpoint(encryptedMediaUrl, bitrate)
        if (downloadUrl.isEmpty()) return -1L

        val cleanTitle = songTitle.replace("[^a-zA-Z0-9.-]".toRegex(), "_")
        val cleanArtist = artistName.replace("[^a-zA-Z0-9.-]".toRegex(), "_")
        val fileName = "$cleanTitle - $cleanArtist.mp3"

        // Step 2: Android Native Download Request
        val request = DownloadManager.Request(Uri.parse(downloadUrl)).apply {
            setTitle(songTitle)
            setDescription("Downloading $songTitle ($bitrate kbps)")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            setMimeType("audio/mpeg")
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
        }

        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return manager.enqueue(request) // Download ID return karta hai
    }
}
