package com.example.download

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.widget.Toast
import com.example.data.local.DownloadedSongEntity
import com.example.data.local.SMusicDatabase
import com.example.data.model.PlayableTrack
import com.example.data.remote.StreamUrlResolver
import com.musicx.app.download.JioSaavnDownloader
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

enum class DownloadStatus {
    IDLE, QUEUED, DOWNLOADING, COMPLETED, FAILED
}

data class DownloadProgressState(
    val songId: String,
    val title: String,
    val artist: String,
    val artwork: String,
    val progress: Int = 0, // 0 to 100
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = 0L,
    val status: DownloadStatus = DownloadStatus.IDLE,
    val localFilePath: String = "",
    val errorMessage: String? = null
)

class SongDownloadManager private constructor(private val context: Context) {

    private val tag = "SongDownloadManager"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val db = SMusicDatabase.getInstance(context)
    private val downloadedDao = db.downloadedSongDao()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    // In-memory active tasks map for live UI progress observation
    private val _activeDownloads = MutableStateFlow<Map<String, DownloadProgressState>>(emptyMap())
    val activeDownloads: StateFlow<Map<String, DownloadProgressState>> = _activeDownloads.asStateFlow()

    private val runningJobs = ConcurrentHashMap<String, Job>()

    /**
     * Observable flow of all downloaded tracks from Room DB
     */
    val allDownloadedTracks: Flow<List<PlayableTrack>> = downloadedDao.getAllDownloadedSongs()
        .map { list ->
            list.filter { it.downloadStatus == "COMPLETED" && File(it.localFilePath).exists() }
                .map { it.toPlayableTrack() }
        }

    fun isDownloaded(songId: String): Flow<Boolean> = downloadedDao.isDownloaded(songId)

    suspend fun isDownloadedSync(songId: String): Boolean = downloadedDao.isDownloadedSync(songId)

    suspend fun getDownloadedTrack(songId: String): DownloadedSongEntity? = downloadedDao.getDownloadedSong(songId)

    /**
     * Initiates a full background download with live progress
     */
    fun startDownload(track: PlayableTrack, bitrate: String = "320") {
        if (runningJobs.containsKey(track.id)) {
            Log.d(tag, "Download already in progress for: ${track.title}")
            return
        }

        val initialTask = DownloadProgressState(
            songId = track.id,
            title = track.title,
            artist = track.artist,
            artwork = track.artwork,
            progress = 0,
            status = DownloadStatus.QUEUED
        )
        updateTaskState(initialTask)

        val job = scope.launch {
            try {
                // 1. Mark as DOWNLOADING in DB & Memory
                updateTaskState(initialTask.copy(status = DownloadStatus.DOWNLOADING, progress = 5))
                downloadedDao.insertDownloadedSong(
                    DownloadedSongEntity(
                        id = track.id,
                        title = track.title,
                        artist = track.artist,
                        album = track.album,
                        albumId = track.albumId,
                        artwork = track.artwork,
                        duration = track.duration,
                        downloadStatus = "DOWNLOADING",
                        progress = 5
                    )
                )

                // 2. Resolve direct high quality audio CDN stream URL
                var streamUrl = track.streamUrl
                if (streamUrl.isBlank() || streamUrl.contains(".m3u8")) {
                    if (track.encryptedMediaUrl.isNotBlank()) {
                        streamUrl = JioSaavnDownloader.getDownloadEndpoint(track.encryptedMediaUrl, bitrate)
                    }
                }
                if (streamUrl.isBlank()) {
                    try {
                        val repository = com.example.repository.MusicRepository(context)
                        val fullSong = repository.getSongDetails(track.id)
                        if (fullSong != null) {
                            if (fullSong.encryptedMediaUrl.isNotBlank()) {
                                streamUrl = JioSaavnDownloader.getDownloadEndpoint(fullSong.encryptedMediaUrl, bitrate)
                            }
                            if (streamUrl.isBlank()) {
                                streamUrl = StreamUrlResolver.resolve(fullSong.toPlayableTrack(), StreamUrlResolver.AudioQuality.HIGH) ?: ""
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(tag, "Failed to resolve details via repository: ${e.message}")
                    }
                }
                if (streamUrl.isBlank()) {
                    streamUrl = StreamUrlResolver.resolve(track, StreamUrlResolver.AudioQuality.HIGH) ?: ""
                }

                if (streamUrl.isBlank()) {
                    throw IllegalStateException("Failed to resolve audio download stream URL")
                }

                // 3. Create target local audio file in App Music Storage & Downloads
                val musicDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) 
                    ?: File(context.filesDir, "Music")
                if (!musicDir.exists()) musicDir.mkdirs()

                val cleanTitle = track.title.replace("[^a-zA-Z0-9.-]".toRegex(), "_").take(40)
                val cleanArtist = track.artist.replace("[^a-zA-Z0-9.-]".toRegex(), "_").take(30)
                val destFile = File(musicDir, "${cleanTitle}_${track.id}.mp3")

                // 4. Download and cache album artwork locally for full offline viewing
                var localArtworkPath = ""
                if (track.artwork.isNotBlank()) {
                    try {
                        val artDir = File(context.filesDir, "artworks")
                        if (!artDir.exists()) artDir.mkdirs()
                        val artFile = File(artDir, "${track.id}.jpg")
                        if (!artFile.exists() || artFile.length() == 0L) {
                            val artRequest = Request.Builder().url(track.artwork).build()
                            client.newCall(artRequest).execute().use { artResponse ->
                                if (artResponse.isSuccessful && artResponse.body != null) {
                                    artResponse.body!!.byteStream().use { input ->
                                        FileOutputStream(artFile).use { output ->
                                            input.copyTo(output)
                                        }
                                    }
                                }
                            }
                        }
                        if (artFile.exists() && artFile.length() > 0) {
                            localArtworkPath = Uri.fromFile(artFile).toString()
                        }
                    } catch (e: Exception) {
                        Log.w(tag, "Artwork download skipped: ${e.message}")
                    }
                }

                // 5. Download audio bytes with live progress calculation
                val request = Request.Builder().url(streamUrl).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful || response.body == null) {
                        throw IllegalStateException("Server returned HTTP ${response.code}")
                    }

                    val body = response.body!!
                    val contentLength = body.contentLength()
                    val inputStream: InputStream = body.byteStream()
                    val outputStream = FileOutputStream(destFile)

                    val buffer = ByteArray(8 * 1024)
                    var bytesRead: Int
                    var totalRead = 0L
                    var lastProgress = 0

                    outputStream.use { out ->
                        inputStream.use { inStream ->
                            while (inStream.read(buffer).also { bytesRead = it } != -1) {
                                out.write(buffer, 0, bytesRead)
                                totalRead += bytesRead

                                if (contentLength > 0) {
                                    val currentProgress = ((totalRead * 100) / contentLength).toInt().coerceIn(0, 100)
                                    if (currentProgress != lastProgress && (currentProgress % 5 == 0 || currentProgress == 100)) {
                                        lastProgress = currentProgress
                                        updateTaskState(
                                            initialTask.copy(
                                                progress = currentProgress,
                                                bytesDownloaded = totalRead,
                                                totalBytes = contentLength,
                                                status = DownloadStatus.DOWNLOADING
                                            )
                                        )
                                        downloadedDao.updateProgress(track.id, currentProgress, "DOWNLOADING")
                                    }
                                }
                            }
                        }
                    }
                }

                // 6. Complete and save in Room Database
                val finalFileSize = destFile.length()
                val completedEntity = DownloadedSongEntity(
                    id = track.id,
                    title = track.title,
                    artist = track.artist,
                    album = track.album,
                    albumId = track.albumId,
                    artwork = track.artwork,
                    localArtworkPath = localArtworkPath,
                    duration = track.duration,
                    localFilePath = destFile.absolutePath,
                    fileSize = finalFileSize,
                    downloadStatus = "COMPLETED",
                    progress = 100,
                    downloadedAt = System.currentTimeMillis()
                )
                downloadedDao.insertDownloadedSong(completedEntity)

                // 7. Also copy to Phone Downloads / Music folder for file manager access
                try {
                    val publicDownloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    if (publicDownloadsDir != null && publicDownloadsDir.exists()) {
                        val publicFile = File(publicDownloadsDir, "${cleanTitle} - ${cleanArtist}.mp3")
                        destFile.copyTo(publicFile, overwrite = true)
                        android.media.MediaScannerConnection.scanFile(
                            context,
                            arrayOf(destFile.absolutePath, publicFile.absolutePath),
                            arrayOf("audio/mpeg"),
                            null
                        )
                    } else {
                        android.media.MediaScannerConnection.scanFile(
                            context,
                            arrayOf(destFile.absolutePath),
                            arrayOf("audio/mpeg"),
                            null
                        )
                    }
                } catch (e: Exception) {
                    Log.d(tag, "Public folder copy skipped: ${e.message}")
                }

                updateTaskState(
                    initialTask.copy(
                        progress = 100,
                        bytesDownloaded = finalFileSize,
                        totalBytes = finalFileSize,
                        status = DownloadStatus.COMPLETED,
                        localFilePath = destFile.absolutePath
                    )
                )

                // Optional: Also register with DownloadManager for phone notification
                try {
                    JioSaavnDownloader.startDownload(
                        context = context,
                        songTitle = track.title,
                        artistName = track.artist,
                        encryptedMediaUrl = track.encryptedMediaUrl.ifEmpty { null },
                        bitrate = bitrate
                    )
                } catch (e: Exception) {
                    Log.d(tag, "DownloadManager notification skipped: ${e.message}")
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Downloaded: ${track.title}", Toast.LENGTH_SHORT).show()
                }

                // Remove from active tasks after 3 seconds
                delay(3000)
                removeTaskState(track.id)

            } catch (e: CancellationException) {
                Log.d(tag, "Download cancelled for ${track.title}")
                removeTaskState(track.id)
                downloadedDao.deleteDownloadedSong(track.id)
            } catch (e: Exception) {
                Log.e(tag, "Download error for ${track.title}: ${e.message}", e)
                updateTaskState(
                    initialTask.copy(
                        status = DownloadStatus.FAILED,
                        errorMessage = e.message ?: "Download failed"
                    )
                )
                downloadedDao.updateProgress(track.id, 0, "FAILED")
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Failed to download ${track.title}", Toast.LENGTH_SHORT).show()
                }
            } finally {
                runningJobs.remove(track.id)
            }
        }

        runningJobs[track.id] = job
    }

    fun cancelDownload(songId: String) {
        runningJobs[songId]?.cancel()
        runningJobs.remove(songId)
        removeTaskState(songId)
        scope.launch {
            downloadedDao.deleteDownloadedSong(songId)
        }
    }

    fun deleteDownloadedSong(songId: String) {
        scope.launch {
            try {
                val entity = downloadedDao.getDownloadedSong(songId)
                if (entity != null) {
                    if (entity.localFilePath.isNotBlank()) {
                        val file = File(entity.localFilePath)
                        if (file.exists()) file.delete()
                    }
                    if (entity.localArtworkPath.isNotBlank()) {
                        val artUri = Uri.parse(entity.localArtworkPath)
                        artUri.path?.let { p ->
                            val f = File(p)
                            if (f.exists()) f.delete()
                        }
                    }
                    downloadedDao.deleteDownloadedSong(songId)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Removed from Downloads", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed to delete download: ${e.message}")
            }
        }
    }

    private fun updateTaskState(state: DownloadProgressState) {
        _activeDownloads.update { current ->
            val updated = current.toMutableMap()
            updated[state.songId] = state
            updated
        }
    }

    private fun removeTaskState(songId: String) {
        _activeDownloads.update { current ->
            val updated = current.toMutableMap()
            updated.remove(songId)
            updated
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: SongDownloadManager? = null

        fun getInstance(context: Context): SongDownloadManager {
            return INSTANCE ?: synchronized(this) {
                val instance = SongDownloadManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
