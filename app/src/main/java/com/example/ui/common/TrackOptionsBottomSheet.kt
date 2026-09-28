package com.example.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.PlayableTrack

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackOptionsBottomSheet(
    track: PlayableTrack,
    isLiked: Boolean,
    onDismiss: () -> Unit,
    onPlayNow: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onToggleLike: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onViewAlbum: (() -> Unit)? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val downloadManager = remember { com.example.download.SongDownloadManager.getInstance(context) }
    val activeDownloads by downloadManager.activeDownloads.collectAsState()
    val isDownloaded by downloadManager.isDownloaded(track.id).collectAsState(initial = track.isDownloaded)

    val activeTask = activeDownloads[track.id]
    val isDownloading = activeTask?.status == com.example.download.DownloadStatus.DOWNLOADING || activeTask?.status == com.example.download.DownloadStatus.QUEUED
    val downloadProgress = activeTask?.progress ?: 0

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 36.dp)
        ) {
            // Track header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    AsyncImage(
                        model = track.artwork,
                        contentDescription = track.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            )

            OptionItem(
                icon = Icons.Default.PlayArrow,
                title = "Play Now",
                onClick = {
                    onPlayNow()
                    onDismiss()
                }
            )

            OptionItem(
                icon = Icons.Outlined.QueuePlayNext,
                title = "Play Next",
                onClick = {
                    onPlayNext()
                    onDismiss()
                }
            )

            OptionItem(
                icon = Icons.Outlined.PlaylistAdd,
                title = "Add to Queue",
                onClick = {
                    onAddToQueue()
                    onDismiss()
                }
            )

            OptionItem(
                icon = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                title = if (isLiked) "Remove from Liked Songs" else "Add to Liked Songs",
                iconTint = if (isLiked) MaterialTheme.colorScheme.primary else null,
                onClick = {
                    onToggleLike()
                    onDismiss()
                }
            )

            OptionItem(
                icon = Icons.Outlined.BookmarkAdd,
                title = "Add to Playlist",
                onClick = {
                    onAddToPlaylist()
                    onDismiss()
                }
            )

            if (isDownloading) {
                OptionItem(
                    icon = Icons.Outlined.Downloading,
                    title = "Downloading ($downloadProgress% • Tap to cancel)",
                    iconTint = MaterialTheme.colorScheme.primary,
                    onClick = {
                        downloadManager.cancelDownload(track.id)
                        android.widget.Toast.makeText(context, "Download cancelled", android.widget.Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }
                )
            } else if (isDownloaded) {
                OptionItem(
                    icon = Icons.Filled.OfflinePin,
                    title = "Downloaded (Remove from Offline Storage)",
                    iconTint = androidx.compose.ui.graphics.Color(0xFF10B981),
                    onClick = {
                        downloadManager.deleteDownloadedSong(track.id)
                        onDismiss()
                    }
                )
            } else {
                OptionItem(
                    icon = Icons.Outlined.Download,
                    title = "Download Song (320 kbps • Save to Phone)",
                    onClick = {
                        downloadManager.startDownload(track, "320")
                        android.widget.Toast.makeText(context, "Download started for ${track.title}", android.widget.Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }
                )
            }

            if (track.albumId.isNotBlank() && onViewAlbum != null) {
                OptionItem(
                    icon = Icons.Outlined.Album,
                    title = "View Album (${track.album})",
                    onClick = {
                        onViewAlbum()
                        onDismiss()
                    }
                )
            }
        }
    }
}

@Composable
private fun OptionItem(
    icon: ImageVector,
    title: String,
    iconTint: androidx.compose.ui.graphics.Color? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = iconTint ?: MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(18.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
