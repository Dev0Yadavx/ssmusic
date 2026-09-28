package com.example.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest

/**
 * Modern 4-Song Mosaic Grid Cover for Playlists.
 * Displays a 2x2 grid of album covers when 4+ songs are present,
 * or intelligent non-empty arrangements for 1, 2, 3 songs,
 * with ZERO black boxes or empty voids.
 */
@Composable
fun FourSongGridCover(
    artworks: List<String>,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    fallbackTitle: String = "Playlist"
) {
    val cleanArtworks = remember(artworks) {
        artworks.filter { it.isNotBlank() }
    }

    val shape = RoundedCornerShape(cornerRadius)
    val fallbackGradient = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.surfaceVariant
        )
    )

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        when {
            // Case 0: Empty Playlist - Expressive M3 gradient and playlist icon (NO black box)
            cleanArtworks.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(fallbackGradient),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.QueueMusic,
                        contentDescription = fallbackTitle,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                        modifier = Modifier.fillMaxSize(0.42f)
                    )
                }
            }

            // Case 1: Exactly 1 song artwork - Full-bleed single image (NO black box)
            cleanArtworks.size == 1 -> {
                SingleQuadrantImage(
                    imageUrl = cleanArtworks[0],
                    modifier = Modifier.fillMaxSize(),
                    fallbackGradient = fallbackGradient
                )
            }

            // Case 2 or 3 or 4+: 2x2 Mosaic Grid - Every quadrant is guaranteed populated (NO black box)
            else -> {
                val gridUrls = remember(cleanArtworks) {
                    when (cleanArtworks.size) {
                        2 -> listOf(
                            cleanArtworks[0],
                            cleanArtworks[1],
                            cleanArtworks[1],
                            cleanArtworks[0]
                        )
                        3 -> listOf(
                            cleanArtworks[0],
                            cleanArtworks[1],
                            cleanArtworks[2],
                            cleanArtworks[0]
                        )
                        else -> cleanArtworks.take(4)
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        SingleQuadrantImage(
                            imageUrl = gridUrls.getOrNull(0) ?: "",
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            fallbackGradient = fallbackGradient
                        )
                        Spacer(modifier = Modifier.width(1.5.dp))
                        SingleQuadrantImage(
                            imageUrl = gridUrls.getOrNull(1) ?: "",
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            fallbackGradient = fallbackGradient
                        )
                    }
                    Spacer(modifier = Modifier.height(1.5.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        SingleQuadrantImage(
                            imageUrl = gridUrls.getOrNull(2) ?: "",
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            fallbackGradient = fallbackGradient
                        )
                        Spacer(modifier = Modifier.width(1.5.dp))
                        SingleQuadrantImage(
                            imageUrl = gridUrls.getOrNull(3) ?: "",
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            fallbackGradient = fallbackGradient
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SingleQuadrantImage(
    imageUrl: String,
    modifier: Modifier = Modifier,
    fallbackGradient: Brush
) {
    val context = LocalContext.current

    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (imageUrl.isNotBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(imageUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(fallbackGradient),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
