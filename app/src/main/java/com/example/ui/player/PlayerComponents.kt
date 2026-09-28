package com.example.ui.player

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.data.model.PlayableTrack
import com.example.player.PlayerManager
import com.example.player.RepeatMode
import com.example.repository.MusicRepository
import com.example.ui.common.ThreeLineVisualizer
import com.example.ui.theme.NowPlayingStyle
import com.example.ui.theme.ThemeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.sin

/**
 * Dynamic Palette extracted from Song Artwork
 */
data class DynamicSongColors(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val backgroundTop: Color,
    val backgroundBottom: Color,
    val surfaceContainer: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val glowAccent: Color
)

@Composable
fun rememberDynamicSongColors(
    artworkUrl: String?,
    seedKey: String,
    isDark: Boolean
): DynamicSongColors {
    val context = LocalContext.current

    // Generate fallback base colors from track seed
    val defaultHue = remember(seedKey) {
        val hash = seedKey.hashCode()
        kotlin.math.abs(hash % 360).toFloat()
    }

    var extractedPrimary by remember(artworkUrl) {
        mutableStateOf<Color?>(null)
    }
    var extractedSecondary by remember(artworkUrl) {
        mutableStateOf<Color?>(null)
    }

    LaunchedEffect(artworkUrl) {
        if (artworkUrl.isNullOrBlank()) {
            extractedPrimary = null
            extractedSecondary = null
            return@LaunchedEffect
        }

        withContext(Dispatchers.IO) {
            try {
                val loader = ImageLoader(context)
                val request = ImageRequest.Builder(context)
                    .data(artworkUrl)
                    .allowHardware(false)
                    .build()
                val result = loader.execute(request)
                if (result is SuccessResult) {
                    val bitmap = result.drawable.toBitmap(96, 96, Bitmap.Config.ARGB_8888)
                    val sampledColors = mutableListOf<Int>()
                    val step = 8
                    for (x in 0 until bitmap.width step step) {
                        for (y in 0 until bitmap.height step step) {
                            val pixel = bitmap.getPixel(x, y)
                            val alpha = (pixel ushr 24) and 0xff
                            if (alpha > 128) {
                                sampledColors.add(pixel)
                            }
                        }
                    }

                    if (sampledColors.isNotEmpty()) {
                        // Find vibrant saturated colors
                        val hsv = FloatArray(3)
                        val scoredColors = sampledColors.map { c ->
                            android.graphics.Color.colorToHSV(c, hsv)
                            val saturation = hsv[1]
                            val brightness = hsv[2]
                            // Score based on saturation and balanced brightness
                            val score = saturation * 2f + (if (brightness in 0.3f..0.85f) 1f else 0.2f)
                            c to score
                        }.sortedByDescending { it.second }

                        val topColorInt = scoredColors.firstOrNull()?.first
                        val secondaryColorInt = scoredColors.drop(scoredColors.size / 3).firstOrNull()?.first

                        withContext(Dispatchers.Main) {
                            topColorInt?.let { extractedPrimary = Color(it) }
                            secondaryColorInt?.let { extractedSecondary = Color(it) }
                        }
                    }
                }
            } catch (e: Exception) {
                // Fallback gracefully
            }
        }
    }

    val basePrimary = extractedPrimary ?: if (isDark) Color(0xFF6EE7B7) else Color(0xFF006C4C)
    val baseSecondary = extractedSecondary ?: if (isDark) Color(0xFF93C5FD) else Color(0xFF1E6586)

    // Smoothly animate the colors when song changes
    val animPrimary by animateColorAsState(basePrimary, animationSpec = tween(700), label = "prim")
    val animSecondary by animateColorAsState(baseSecondary, animationSpec = tween(700), label = "sec")

    val bgTop = if (isDark) {
        animPrimary.copy(alpha = 0.38f)
    } else {
        animPrimary.copy(alpha = 0.18f)
    }
    val bgBottom = if (isDark) Color(0xFF0B0E0D) else Color(0xFFF7FBF7)
    val surfContainer = if (isDark) {
        Color(0xFF181C1A).copy(alpha = 0.85f)
    } else {
        Color(0xFFFFFFFF).copy(alpha = 0.88f)
    }
    val onSurf = if (isDark) Color(0xFFF0F4F0) else Color(0xFF121A16)
    val onSurfVar = if (isDark) Color(0xFFA8B4AD) else Color(0xFF53635B)

    return DynamicSongColors(
        primary = animPrimary,
        secondary = animSecondary,
        tertiary = if (isDark) Color(0xFFFBBF24) else Color(0xFFD97706),
        backgroundTop = bgTop,
        backgroundBottom = bgBottom,
        surfaceContainer = surfContainer,
        onSurface = onSurf,
        onSurfaceVariant = onSurfVar,
        glowAccent = animPrimary.copy(alpha = 0.45f)
    )
}

/**
 * Authentic M3 Squiggly Wave Seekbar with Live Sine Wave Animation & Fluid Bar Scrubber
 */
@Composable
fun SquigglySlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    thumbColor: Color = MaterialTheme.colorScheme.primary
) {
    val infiniteTransition = rememberInfiniteTransition(label = "squiggly")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (isPlaying) (2 * Math.PI).toFloat() else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart
        ),
        label = "phase"
    )

    // Smooth wave amplitude animation when playing/paused
    val targetAmplitude = if (isPlaying) 4.2f else 0f
    val waveAmpDp by animateFloatAsState(
        targetValue = targetAmplitude,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "waveAmp"
    )

    var isDragging by remember { mutableStateOf(false) }
    val thumbScale by animateFloatAsState(
        targetValue = if (isDragging) 1.25f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy),
        label = "thumbScale"
    )

    val currentFraction = if (valueRange.endInclusive > valueRange.start) {
        ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
    } else 0f

    val density = LocalDensity.current
    val trackStrokeWidth = with(density) { 5.dp.toPx() }
    val waveLength = with(density) { 34.dp.toPx() }
    val waveAmplitude = with(density) { waveAmpDp.dp.toPx() }

    // Vertical bar scrubber thumb ("|" shape)
    val barWidth = with(density) { (4.5.dp * thumbScale).toPx() }
    val barHeight = with(density) { (22.dp * thumbScale).toPx() }
    val barCorner = with(density) { 2.5.dp.toPx() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .pointerInput(valueRange) {
                detectTapGestures(
                    onPress = { offset ->
                        isDragging = true
                        val newFraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        val newValue = valueRange.start + newFraction * (valueRange.endInclusive - valueRange.start)
                        onValueChange(newValue)
                        tryAwaitRelease()
                        isDragging = false
                        onValueChangeFinished()
                    }
                )
            }
            .pointerInput(valueRange) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        val newFraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        val newValue = valueRange.start + newFraction * (valueRange.endInclusive - valueRange.start)
                        onValueChange(newValue)
                    },
                    onDragEnd = {
                        isDragging = false
                        onValueChangeFinished()
                    },
                    onDragCancel = {
                        isDragging = false
                        onValueChangeFinished()
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        val newFraction = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                        val newValue = valueRange.start + newFraction * (valueRange.endInclusive - valueRange.start)
                        onValueChange(newValue)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(44.dp)) {
            val centerY = size.height / 2f
            val startX = 0f
            val endX = size.width
            val currentProgressX = startX + (endX - startX) * currentFraction

            // 1. Inactive Track (Unplayed area)
            if (currentProgressX < endX) {
                drawLine(
                    color = inactiveColor,
                    start = Offset(currentProgressX, centerY),
                    end = Offset(endX, centerY),
                    strokeWidth = trackStrokeWidth,
                    cap = StrokeCap.Round
                )
            }

            // 2. Active Wave Track (Played sine wave)
            if (currentProgressX > startX) {
                val wavePath = Path()
                wavePath.moveTo(startX, centerY)

                var x = startX
                val step = 2f
                while (x <= currentProgressX) {
                    val angle = ((x - startX) / waveLength * (2 * Math.PI) + phase).toFloat()
                    val y = centerY + waveAmplitude * sin(angle)
                    wavePath.lineTo(x, y)
                    x += step
                }
                drawPath(
                    path = wavePath,
                    color = activeColor,
                    style = Stroke(width = trackStrokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }

            // 3. Thumb Scrubber Glow & Bar
            if (isDragging) {
                drawCircle(
                    color = thumbColor.copy(alpha = 0.22f),
                    radius = barHeight * 0.8f,
                    center = Offset(currentProgressX, centerY)
                )
            }

            // Scrubber Bar "|"
            drawRoundRect(
                color = thumbColor,
                topLeft = Offset(currentProgressX - barWidth / 2f, centerY - barHeight / 2f),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barCorner, barCorner)
            )
        }
    }
}

/**
 * MiniPlayer docked seamlessly with bottom navigation
 */
@Composable
fun MiniPlayer(
    playerManager: PlayerManager,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    repository: MusicRepository? = null
) {
    val currentTrack by playerManager.currentTrack.collectAsState()
    val isPlaying by playerManager.isPlaying.collectAsState()
    val isBuffering by playerManager.isBuffering.collectAsState()
    val currentPosMs by playerManager.currentPositionMs.collectAsState()
    val durationMs by playerManager.durationMs.collectAsState()
    val scope = rememberCoroutineScope()

    val track = currentTrack ?: return

    val progress = if (durationMs > 0) {
        (currentPosMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    var isLiked by remember { mutableStateOf(false) }

    LaunchedEffect(track.id, repository) {
        if (repository != null) {
            repository.isSongLiked(track.id).collect { liked ->
                isLiked = liked
            }
        }
    }

    // Horizontal swipe gesture for skipping tracks
    var dragOffsetX by remember { mutableStateOf(0f) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(track.id) {
                detectHorizontalDragGestures(
                    onDragStart = { dragOffsetX = 0f },
                    onDragEnd = {
                        if (dragOffsetX < -80f) {
                            playerManager.skipToNext()
                        } else if (dragOffsetX > 80f) {
                            playerManager.skipToPrevious()
                        }
                        dragOffsetX = 0f
                    },
                    onDragCancel = { dragOffsetX = 0f },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        dragOffsetX += dragAmount
                    }
                )
            }
            .clickable(onClick = onClick)
            .testTag("mini_player")
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Top Slim Progress Bar with smooth rounded look
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Album Art with rounded corners
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    AsyncImage(
                        model = track.artwork,
                        contentDescription = track.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    if (isPlaying) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.35f)),
                            contentAlignment = Alignment.Center
                        ) {
                            ThreeLineVisualizer(
                                isPlaying = true,
                                color = MaterialTheme.colorScheme.primary,
                                barWidth = 2.5.dp,
                                maxHeight = 16.dp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Title & Artist with Marquee
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.5.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        maxLines = 1,
                        modifier = Modifier.basicMarquee()
                    )
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.5.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Like / Favorite Button
                if (repository != null) {
                    val heartScale by animateFloatAsState(
                        targetValue = if (isLiked) 1.2f else 1.0f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                        label = "mini_like_scale"
                    )
                    IconButton(
                        onClick = {
                            scope.launch {
                                repository.toggleLike(track)
                            }
                        },
                        modifier = Modifier
                            .size(38.dp)
                            .scale(heartScale)
                            .testTag("mini_player_like")
                    ) {
                        Icon(
                            imageVector = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = if (isLiked) "Unlike" else "Like",
                            tint = if (isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Play / Pause Button in M3 styled Container
                Surface(
                    onClick = { playerManager.playPause() },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("mini_player_play_pause")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isBuffering) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Next Track Button
                IconButton(
                    onClick = { playerManager.skipToNext() },
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("mini_player_next")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = "Next Track",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

/**
 * Full Screen Now Playing Screen with M3 Expressive Dynamic Song Color Gradient,
 * Squiggly Wave Seekbar, Wave Like & Download buttons, M3 Morphing Play/Pause Shape,
 * and Niche Bottom Controls (Queue, Lyrics, Connected Device).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingModal(
    playerManager: PlayerManager,
    repository: MusicRepository,
    onDismiss: () -> Unit,
    onViewAlbum: (String) -> Unit,
    onViewArtist: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val track by playerManager.currentTrack.collectAsState()
    val isPlaying by playerManager.isPlaying.collectAsState()
    val isBuffering by playerManager.isBuffering.collectAsState()
    val currentPosMs by playerManager.currentPositionMs.collectAsState()
    val durationMs by playerManager.durationMs.collectAsState()
    val repeatMode by playerManager.repeatMode.collectAsState()
    val isShuffle by playerManager.isShuffleEnabled.collectAsState()
    val volume by playerManager.volume.collectAsState()
    val queue by playerManager.queue.collectAsState()
    val currentIndex by playerManager.currentIndex.collectAsState()
    val errorMessage by playerManager.errorMessage.collectAsState()

    var showQueueSheet by remember { mutableStateOf(false) }
    var showLyricsSheet by remember { mutableStateOf(false) }
    var showCreditsSheet by remember { mutableStateOf(false) }
    var showMoreOptionsSheet by remember { mutableStateOf(false) }
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var showDeviceDialog by remember { mutableStateOf(false) }
    var isDownloaded by remember { mutableStateOf(false) }
    var isLiked by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val downloadManager = remember { com.example.download.SongDownloadManager.getInstance(context) }
    val activeDownloads by downloadManager.activeDownloads.collectAsState()

    LaunchedEffect(track?.id) {
        track?.let { t ->
            launch {
                repository.isSongLiked(t.id).collect { liked ->
                    isLiked = liked
                }
            }
            launch {
                repository.isSongDownloaded(t.id).collect { dl ->
                    isDownloaded = dl
                }
            }
        }
    }

    val currentT = track ?: return

    val activeTask = activeDownloads[currentT.id]
    val isDownloading = activeTask?.status == com.example.download.DownloadStatus.DOWNLOADING || activeTask?.status == com.example.download.DownloadStatus.QUEUED
    val downloadProgress = activeTask?.progress ?: 0

    var isUserDraggingSlider by remember { mutableStateOf(false) }
    var sliderDragPositionMs by remember { mutableStateOf(0f) }

    val displayPositionMs = if (isUserDraggingSlider) sliderDragPositionMs.toLong() else currentPosMs
    val totalDurationMs = if (durationMs > 0) durationMs else (currentT.duration * 1000)

    val formatTime = { ms: Long ->
        val totalSec = (ms / 1000).coerceAtLeast(0)
        val m = totalSec / 60
        val s = totalSec % 60
        "%d:%02d".format(m, s)
    }

    fun shareCurrentTrack() {
        try {
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(
                    Intent.EXTRA_TEXT,
                    "🎵 Listening to \"${currentT.title}\" by ${currentT.artist} on SMusic!"
                )
                type = "text/plain"
            }
            val shareIntent = Intent.createChooser(sendIntent, "Share Track")
            context.startActivity(shareIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot share track: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    // Dynamic Song Colors extracted from album artwork
    val dynamicColors = rememberDynamicSongColors(
        artworkUrl = currentT.artwork,
        seedKey = "${currentT.id}_${currentT.title}",
        isDark = isDark
    )

    // Animated like button bounce
    var likeAnimateTrigger by remember { mutableStateOf(false) }
    val likeScale by animateFloatAsState(
        targetValue = if (likeAnimateTrigger) 1.35f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        finishedListener = { likeAnimateTrigger = false },
        label = "likeScale"
    )

    val themeManager = remember { ThemeManager.getInstance(context) }
    val nowPlayingStyle by themeManager.nowPlayingStyle.collectAsState()
    var showSleepTimerDialog by remember { mutableStateOf(false) }

    AnimatedContent(
        targetState = nowPlayingStyle,
        transitionSpec = {
            fadeIn(animationSpec = tween(250)) togetherWith fadeOut(animationSpec = tween(200))
        },
        label = "NowPlayingStyleTransition"
    ) { currentStyle ->
        when (currentStyle) {
            NowPlayingStyle.IMMERSIVE_POSTER -> {
                ImmersivePosterNowPlayingLayout(
                    track = currentT,
                    isPlaying = isPlaying,
                    isBuffering = isBuffering,
                    displayPositionMs = displayPositionMs,
                    totalDurationMs = totalDurationMs,
                    formatTime = formatTime,
                    isLiked = isLiked,
                    likeScale = likeScale,
                    errorMessage = errorMessage,
                    onDismiss = onDismiss,
                    onShare = { shareCurrentTrack() },
                    onToggleLike = {
                        likeAnimateTrigger = true
                        scope.launch { repository.toggleLike(currentT) }
                    },
                    onMoreOptions = { showMoreOptionsSheet = true },
                    onSeek = { pos ->
                        isUserDraggingSlider = true
                        sliderDragPositionMs = pos
                    },
                    onSeekFinished = {
                        playerManager.seekTo(sliderDragPositionMs.toLong())
                        isUserDraggingSlider = false
                    },
                    onPrevious = { playerManager.skipToPrevious() },
                    onPlayPause = { playerManager.playPause() },
                    onNext = { playerManager.skipToNext() },
                    onRetry = { playerManager.retryCurrentTrack() },
                    onOpenQueue = { showQueueSheet = true },
                    onOpenLyrics = { showLyricsSheet = true },
                    onOpenDeviceSelector = { showDeviceDialog = true }
                )
            }
            NowPlayingStyle.VINYL_DISC -> {
                VinylDiscNowPlayingLayout(
                    track = currentT,
                    isPlaying = isPlaying,
                    isBuffering = isBuffering,
                    displayPositionMs = displayPositionMs,
                    totalDurationMs = totalDurationMs,
                    formatTime = formatTime,
                    isLiked = isLiked,
                    likeScale = likeScale,
                    isShuffle = isShuffle,
                    repeatMode = repeatMode,
                    isDownloading = isDownloading,
                    isDownloaded = isDownloaded,
                    downloadProgress = downloadProgress,
                    errorMessage = errorMessage,
                    dynamicColors = dynamicColors,
                    onDismiss = onDismiss,
                    onToggleLike = {
                        likeAnimateTrigger = true
                        scope.launch { repository.toggleLike(currentT) }
                    },
                    onToggleShuffle = { playerManager.toggleShuffle() },
                    onToggleRepeat = { playerManager.toggleRepeat() },
                    onDownload = {
                        if (isDownloading) {
                            downloadManager.cancelDownload(currentT.id)
                            Toast.makeText(context, "Download cancelled", Toast.LENGTH_SHORT).show()
                        } else if (isDownloaded) {
                            downloadManager.deleteDownloadedSong(currentT.id)
                            Toast.makeText(context, "Removed from downloads", Toast.LENGTH_SHORT).show()
                        } else {
                            downloadManager.startDownload(currentT, "320")
                            Toast.makeText(context, "Downloading in 320kbps HD...", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onRadio = {
                        playerManager.skipToNext()
                        Toast.makeText(context, "Playing recommendations for ${currentT.title}", Toast.LENGTH_SHORT).show()
                    },
                    onSleepTimer = { showSleepTimerDialog = true },
                    onMoreOptions = { showMoreOptionsSheet = true },
                    onSeek = { pos ->
                        isUserDraggingSlider = true
                        sliderDragPositionMs = pos
                    },
                    onSeekFinished = {
                        playerManager.seekTo(sliderDragPositionMs.toLong())
                        isUserDraggingSlider = false
                    },
                    onPrevious = { playerManager.skipToPrevious() },
                    onPlayPause = { playerManager.playPause() },
                    onNext = { playerManager.skipToNext() },
                    onRetry = { playerManager.retryCurrentTrack() },
                    onOpenQueue = { showQueueSheet = true }
                )
            }
            NowPlayingStyle.MODERN_CARD -> {
                ModernCardNowPlayingLayout(
                    track = currentT,
                    isPlaying = isPlaying,
                    isBuffering = isBuffering,
                    displayPositionMs = displayPositionMs,
                    totalDurationMs = totalDurationMs,
                    formatTime = formatTime,
                    isLiked = isLiked,
                    likeScale = likeScale,
                    isShuffle = isShuffle,
                    repeatMode = repeatMode,
                    errorMessage = errorMessage,
                    dynamicColors = dynamicColors,
                    onDismiss = onDismiss,
                    onToggleLike = {
                        likeAnimateTrigger = true
                        scope.launch { repository.toggleLike(currentT) }
                    },
                    onToggleShuffle = { playerManager.toggleShuffle() },
                    onToggleRepeat = { playerManager.toggleRepeat() },
                    onMoreOptions = { showMoreOptionsSheet = true },
                    onSeek = { pos ->
                        isUserDraggingSlider = true
                        sliderDragPositionMs = pos
                    },
                    onSeekFinished = {
                        playerManager.seekTo(sliderDragPositionMs.toLong())
                        isUserDraggingSlider = false
                    },
                    onPrevious = { playerManager.skipToPrevious() },
                    onPlayPause = { playerManager.playPause() },
                    onNext = { playerManager.skipToNext() },
                    onRetry = { playerManager.retryCurrentTrack() },
                    onOpenQueue = { showQueueSheet = true },
                    onOpenLyrics = { showLyricsSheet = true },
                    onOpenDeviceSelector = { showDeviceDialog = true }
                )
            }
        }
    }

    // Bottom Sheet: Queue (Up Next)
    if (showQueueSheet) {
        QueueBottomSheet(
            queue = queue,
            currentIndex = currentIndex,
            isPlaying = isPlaying,
            onDismiss = { showQueueSheet = false },
            onSelectTrack = { index ->
                playerManager.playTrack(queue[index], queue)
                showQueueSheet = false
            },
            onRemoveTrack = { playerManager.removeFromQueue(it) },
            onClearQueue = {
                playerManager.clearQueue()
                showQueueSheet = false
            }
        )
    }

    // Bottom Sheet: Lyrics
    if (showLyricsSheet) {
        LyricsBottomSheet(
            track = currentT,
            repository = repository,
            onDismiss = { showLyricsSheet = false }
        )
    }

    // Bottom Sheet: Song Credits
    if (showCreditsSheet) {
        SongCreditsBottomSheet(
            track = currentT,
            onViewArtist = { artistName ->
                showCreditsSheet = false
                onDismiss()
                onViewArtist(artistName)
            },
            onViewAlbum = { albumId ->
                showCreditsSheet = false
                onDismiss()
                onViewAlbum(albumId)
            },
            onDismiss = { showCreditsSheet = false }
        )
    }

    // Bottom Sheet: 3-Dot More Options
    if (showMoreOptionsSheet) {
        SongOptionsBottomSheet(
            track = currentT,
            isLiked = isLiked,
            isShuffle = isShuffle,
            repeatMode = repeatMode,
            isDownloaded = isDownloaded,
            isDownloading = isDownloading,
            downloadProgress = downloadProgress,
            volume = volume,
            onVolumeChange = { playerManager.setVolume(it) },
            onToggleLike = {
                scope.launch { repository.toggleLike(currentT) }
            },
            onToggleShuffle = {
                playerManager.toggleShuffle()
            },
            onToggleRepeat = {
                playerManager.toggleRepeat()
            },
            onShowQueue = {
                showMoreOptionsSheet = false
                showQueueSheet = true
            },
            onShowLyrics = {
                showMoreOptionsSheet = false
                showLyricsSheet = true
            },
            onShowCredits = {
                showMoreOptionsSheet = false
                showCreditsSheet = true
            },
            onDownload = {
                if (isDownloading) {
                    downloadManager.cancelDownload(currentT.id)
                    Toast.makeText(context, "Download cancelled", Toast.LENGTH_SHORT).show()
                } else if (isDownloaded) {
                    downloadManager.deleteDownloadedSong(currentT.id)
                    Toast.makeText(context, "Song deleted from downloads", Toast.LENGTH_SHORT).show()
                } else {
                    downloadManager.startDownload(currentT, "320")
                    Toast.makeText(context, "Downloading ${currentT.title}...", Toast.LENGTH_SHORT).show()
                }
            },
            onAddToPlaylist = {
                showMoreOptionsSheet = false
                showAddToPlaylistDialog = true
            },
            onViewAlbum = {
                showMoreOptionsSheet = false
                if (currentT.albumId.isNotBlank()) onViewAlbum(currentT.albumId)
            },
            onShare = {
                showMoreOptionsSheet = false
                shareCurrentTrack()
            },
            onDismiss = { showMoreOptionsSheet = false }
        )
    }

    // Dialog: Add to Playlist
    if (showAddToPlaylistDialog) {
        AddToPlaylistDialog(
            track = currentT,
            repository = repository,
            onDismiss = { showAddToPlaylistDialog = false }
        )
    }

    // Dialog: Connected Device Selector (M3 Style Audio Device Switcher)
    if (showDeviceDialog) {
        DeviceConnectDialog(
            onDismiss = { showDeviceDialog = false }
        )
    }

    // Dialog: Sleep Timer
    if (showSleepTimerDialog) {
        SleepTimerDialog(
            onSetTimerMinutes = { mins ->
                Toast.makeText(context, if (mins > 0) "Sleep timer set for $mins mins" else "Playback will stop at end of track", Toast.LENGTH_SHORT).show()
                if (mins > 0) {
                    scope.launch {
                        kotlinx.coroutines.delay(mins * 60 * 1000L)
                        playerManager.pause()
                    }
                }
            },
            onCancelTimer = {
                Toast.makeText(context, "Sleep timer cancelled", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showSleepTimerDialog = false }
        )
    }
}

/**
 * 1. IMMERSIVE POSTER NOW PLAYING LAYOUT (Photo 1)
 * Full-screen artwork canvas with ambient blur, glass scrim, audio format badge,
 * large rounded seeker, and speaker/queue pills.
 */
@Composable
fun ImmersivePosterNowPlayingLayout(
    track: PlayableTrack,
    isPlaying: Boolean,
    isBuffering: Boolean,
    displayPositionMs: Long,
    totalDurationMs: Long,
    formatTime: (Long) -> String,
    isLiked: Boolean,
    likeScale: Float,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onToggleLike: () -> Unit,
    onMoreOptions: () -> Unit,
    onSeek: (Float) -> Unit,
    onSeekFinished: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onRetry: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenLyrics: () -> Unit,
    onOpenDeviceSelector: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Full Canvas Background Artwork
        AsyncImage(
            model = track.artwork,
            contentDescription = track.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Dark gradient scrim overlay (Clear at top, darker at controls)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.35f),
                            Color.Black.copy(alpha = 0.15f),
                            Color.Black.copy(alpha = 0.45f),
                            Color.Black.copy(alpha = 0.82f),
                            Color.Black.copy(alpha = 0.96f)
                        )
                    )
                )
        )

        // Foreground UI Elements
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar: Close (X) & Share/Pip button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = onDismiss,
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.45f),
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Surface(
                    onClick = onShare,
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.45f),
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                            contentDescription = "Share",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Error Banner if any
            if (errorMessage != null) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFDC2626).copy(alpha = 0.85f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onRetry) {
                            Text("Retry", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Track Title & Artist Info with Like & More Buttons (Photo 1 exact layout)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = (-0.3).sp
                        ),
                        maxLines = 1,
                        modifier = Modifier.basicMarquee()
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 15.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = onToggleLike,
                        modifier = Modifier
                            .size(44.dp)
                            .scale(likeScale)
                    ) {
                        Icon(
                            imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (isLiked) Color(0xFFFF4D6D) else Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    IconButton(
                        onClick = onMoreOptions,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreHoriz,
                            contentDescription = "More Options",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Smooth Thick Custom Progress Slider
            Column(modifier = Modifier.fillMaxWidth()) {
                Slider(
                    value = displayPositionMs.toFloat(),
                    onValueChange = onSeek,
                    onValueChangeFinished = onSeekFinished,
                    valueRange = 0f..totalDurationMs.toFloat().coerceAtLeast(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.28f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(26.dp)
                )

                // Time Row: Elapsed, Audio format badge (WEBM / 320 KBPS), Remaining Time
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatTime(displayPositionMs),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    )

                    // Audio Stream Badge
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White.copy(alpha = 0.16f),
                        modifier = Modifier.padding(horizontal = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.GraphicEq,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = if (track.streamUrl.contains("mp4") || track.streamUrl.contains("aac")) "AAC 320" else "WEBM HD",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = Color.White
                                )
                            )
                        }
                    }

                    val remainingMs = (totalDurationMs - displayPositionMs).coerceAtLeast(0L)
                    Text(
                        text = "-" + formatTime(remainingMs),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Playback Controls Row: Prev, Big Rounded Play/Pause, Next
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onPrevious,
                    modifier = Modifier.size(54.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipPrevious,
                        contentDescription = "Previous",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Surface(
                    onClick = onPlayPause,
                    shape = RoundedCornerShape(26.dp),
                    color = Color.White,
                    shadowElevation = 12.dp,
                    modifier = Modifier.size(76.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isBuffering) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                color = Color.Black,
                                strokeWidth = 3.dp
                            )
                        } else {
                            Icon(
                                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.Black,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onNext,
                    modifier = Modifier.size(54.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = "Next",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Bottom Dock Pills: Queue/Lyrics & Speaker Cast
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        onClick = onOpenQueue,
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White.copy(alpha = 0.16f)
                    ) {
                        Box(
                            modifier = Modifier.size(46.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                contentDescription = "Queue",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Surface(
                        onClick = onOpenLyrics,
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White.copy(alpha = 0.16f)
                    ) {
                        Box(
                            modifier = Modifier.size(46.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lyrics,
                                contentDescription = "Lyrics",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Surface(
                    onClick = onOpenDeviceSelector,
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White.copy(alpha = 0.16f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.VolumeUp,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Speaker",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 13.sp
                            )
                        )
                    }
                }
            }
        }
    }
}

/**
 * 2. VINYL DISC NOW PLAYING LAYOUT (Photo 2)
 * Circular rotating vinyl artwork with quick action tool row (Radio, Download, Sleep, More),
 * large squircle play button, and bottom shuffle/heart/repeat row.
 */
@Composable
fun VinylDiscNowPlayingLayout(
    track: PlayableTrack,
    isPlaying: Boolean,
    isBuffering: Boolean,
    displayPositionMs: Long,
    totalDurationMs: Long,
    formatTime: (Long) -> String,
    isLiked: Boolean,
    likeScale: Float,
    isShuffle: Boolean,
    repeatMode: RepeatMode,
    isDownloading: Boolean,
    isDownloaded: Boolean,
    downloadProgress: Int,
    errorMessage: String?,
    dynamicColors: DynamicSongColors,
    onDismiss: () -> Unit,
    onToggleLike: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onDownload: () -> Unit,
    onRadio: () -> Unit,
    onSleepTimer: () -> Unit,
    onMoreOptions: () -> Unit,
    onSeek: (Float) -> Unit,
    onSeekFinished: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onRetry: () -> Unit,
    onOpenQueue: () -> Unit
) {
    // Rotating Vinyl Disc Animation when playing
    val infiniteTransition = rememberInfiniteTransition(label = "vinyl_rotate")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart
        ),
        label = "rotation"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF141712))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Header: Close + Playing from: Album/Mix
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close",
                        tint = Color.White.copy(alpha = 0.8f)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Playing from:",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.White.copy(alpha = 0.6f),
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp
                        )
                    )
                    Text(
                        text = if (track.album.isNotBlank()) track.album else "SMusic Radio Mix",
                        style = MaterialTheme.typography.titleSmall.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(onClick = onMoreOptions) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More",
                        tint = Color.White.copy(alpha = 0.8f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Center: Circular Vinyl Disc Art with Realistic Grooves & Soft Glow
            Box(
                modifier = Modifier
                    .size(245.dp)
                    .shadow(24.dp, shape = CircleShape, spotColor = dynamicColors.primary.copy(alpha = 0.5f))
                    .clip(CircleShape)
                    .background(Color(0xFF0D0F0D))
                    .border(3.dp, Color(0xFF2A3026), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                // Vinyl Groove Lines
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val radiusStep = size.minDimension / 14
                    for (i in 1..6) {
                        drawCircle(
                            color = Color.White.copy(alpha = 0.04f),
                            radius = size.minDimension / 2 - (i * radiusStep),
                            style = Stroke(width = 1.dp.toPx())
                        )
                    }
                }

                // Center Album Art (Rotates smoothly when playing)
                Box(
                    modifier = Modifier
                        .size(190.dp)
                        .clip(CircleShape)
                        .scale(1f)
                ) {
                    AsyncImage(
                        model = track.artwork,
                        contentDescription = track.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                // Center spindle hole
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF141712))
                        .border(1.5.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Track Title & Artist (Center Aligned as in Photo 2)
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = Color.White
                    ),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.5.sp
                    ),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Quick Action Tool Row: [Radio, Download, Sleep, More] (Photo 2 exact style)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Radio / Reco pill
                Surface(
                    onClick = onRadio,
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    contentColor = Color.Black,
                    modifier = Modifier.size(width = 54.dp, height = 44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Radio,
                            contentDescription = "Radio Mix",
                            tint = Color.Black,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // 2. Download pill
                Surface(
                    onClick = onDownload,
                    shape = RoundedCornerShape(16.dp),
                    color = if (isDownloaded) Color(0xFF10B981) else Color.White,
                    contentColor = if (isDownloaded) Color.White else Color.Black,
                    modifier = Modifier.size(width = 54.dp, height = 44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isDownloading) {
                            CircularProgressIndicator(
                                progress = { (downloadProgress / 100f).coerceIn(0f, 1f) },
                                modifier = Modifier.size(20.dp),
                                color = Color.Black,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = if (isDownloaded) Icons.Filled.OfflinePin else Icons.Outlined.Download,
                                contentDescription = "Download",
                                tint = if (isDownloaded) Color.White else Color.Black,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                // 3. Sleep Timer pill
                Surface(
                    onClick = onSleepTimer,
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    contentColor = Color.Black,
                    modifier = Modifier.size(width = 54.dp, height = 44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Bedtime,
                            contentDescription = "Sleep Timer",
                            tint = Color.Black,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // 4. More options pill
                Surface(
                    onClick = onMoreOptions,
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    contentColor = Color.Black,
                    modifier = Modifier.size(width = 54.dp, height = 44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.MoreHoriz,
                            contentDescription = "More",
                            tint = Color.Black,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Progress Slider Row with Time stamps
            Column(modifier = Modifier.fillMaxWidth()) {
                Slider(
                    value = displayPositionMs.toFloat(),
                    onValueChange = onSeek,
                    onValueChangeFinished = onSeekFinished,
                    valueRange = 0f..totalDurationMs.toFloat().coerceAtLeast(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatTime(displayPositionMs),
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                    Text(
                        text = formatTime(totalDurationMs),
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Main Playback Controls: Prev, Squircle Play/Pause, Next
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onPrevious,
                    modifier = Modifier.size(54.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipPrevious,
                        contentDescription = "Previous",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Large Squircle Play Button (Photo 2)
                Surface(
                    onClick = onPlayPause,
                    shape = RoundedCornerShape(24.dp),
                    color = Color.White,
                    shadowElevation = 8.dp,
                    modifier = Modifier.size(76.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isBuffering) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                color = Color.Black,
                                strokeWidth = 3.dp
                            )
                        } else {
                            Icon(
                                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.Black,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onNext,
                    modifier = Modifier.size(54.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = "Next",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Bottom Action Row: Shuffle, Heart/Like, Repeat (Photo 2)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onToggleShuffle) {
                    Icon(
                        imageVector = Icons.Rounded.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (isShuffle) Color(0xFF6EE7B7) else Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(
                    onClick = onToggleLike,
                    modifier = Modifier.scale(likeScale)
                ) {
                    Icon(
                        imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (isLiked) Color(0xFFFF4D6D) else Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(26.dp)
                    )
                }

                IconButton(onClick = onToggleRepeat) {
                    val icon = if (repeatMode == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat
                    Icon(
                        imageVector = icon,
                        contentDescription = "Repeat",
                        tint = if (repeatMode != RepeatMode.OFF) Color(0xFF6EE7B7) else Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Bottom Expand Chevron for Queue
            IconButton(
                onClick = onOpenQueue,
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowUp,
                    contentDescription = "Open Queue",
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

/**
 * 3. MODERN CARD NOW PLAYING LAYOUT (Photo 3)
 * Classic elevated rounded square album card, top "NOW PLAYING" header,
 * 5-button studio control row, and bottom sheet drag indicator.
 */
@Composable
fun ModernCardNowPlayingLayout(
    track: PlayableTrack,
    isPlaying: Boolean,
    isBuffering: Boolean,
    displayPositionMs: Long,
    totalDurationMs: Long,
    formatTime: (Long) -> String,
    isLiked: Boolean,
    likeScale: Float,
    isShuffle: Boolean,
    repeatMode: RepeatMode,
    errorMessage: String?,
    dynamicColors: DynamicSongColors,
    onDismiss: () -> Unit,
    onToggleLike: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onMoreOptions: () -> Unit,
    onSeek: (Float) -> Unit,
    onSeekFinished: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onRetry: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenLyrics: () -> Unit,
    onOpenDeviceSelector: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF101311))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar: Down Chevron, Center "NOW PLAYING" + Subtitle, Right More Options
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Collapse",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                ) {
                    Text(
                        text = "NOW PLAYING",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.White.copy(alpha = 0.6f),
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            fontSize = 10.5.sp
                        )
                    )
                    Text(
                        text = "\"${track.title}\" in ${if (track.album.isNotBlank()) track.album else "Search"}",
                        style = MaterialTheme.typography.titleSmall.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp
                        ),
                        maxLines = 1,
                        modifier = Modifier.basicMarquee()
                    )
                }

                IconButton(onClick = onMoreOptions) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More Options",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Large Elevated Rounded Square Album Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .shadow(
                        elevation = 20.dp,
                        shape = RoundedCornerShape(26.dp),
                        spotColor = dynamicColors.primary.copy(alpha = 0.4f)
                    )
                    .clip(RoundedCornerShape(26.dp))
                    .background(Color(0xFF1E221F))
            ) {
                AsyncImage(
                    model = track.artwork,
                    contentDescription = track.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            // Title, Artist and Heart Favorite Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 23.sp,
                            color = Color.White
                        ),
                        maxLines = 1,
                        modifier = Modifier.basicMarquee()
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 15.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onToggleLike,
                    modifier = Modifier.scale(likeScale)
                ) {
                    Icon(
                        imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (isLiked) Color(0xFFFF4D6D) else Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Progress Slider & Timestamps
            Column(modifier = Modifier.fillMaxWidth()) {
                Slider(
                    value = displayPositionMs.toFloat(),
                    onValueChange = onSeek,
                    onValueChangeFinished = onSeekFinished,
                    valueRange = 0f..totalDurationMs.toFloat().coerceAtLeast(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.28f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(26.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatTime(displayPositionMs),
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                    Text(
                        text = formatTime(totalDurationMs),
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 5-Button Primary Controls Row: Shuffle, Prev, Big Circle Play, Next, Repeat
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onToggleShuffle) {
                    Icon(
                        imageVector = Icons.Rounded.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (isShuffle) Color(0xFF6EE7B7) else Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(
                    onClick = onPrevious,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipPrevious,
                        contentDescription = "Previous",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Surface(
                    onClick = onPlayPause,
                    shape = CircleShape,
                    color = Color.White,
                    shadowElevation = 10.dp,
                    modifier = Modifier.size(72.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isBuffering) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(30.dp),
                                color = Color.Black,
                                strokeWidth = 3.dp
                            )
                        } else {
                            Icon(
                                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.Black,
                                modifier = Modifier.size(38.dp)
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onNext,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = "Next",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                IconButton(onClick = onToggleRepeat) {
                    val icon = if (repeatMode == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat
                    Icon(
                        imageVector = icon,
                        contentDescription = "Repeat",
                        tint = if (repeatMode != RepeatMode.OFF) Color(0xFF6EE7B7) else Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Bottom row dock with device selector & queue/lyrics
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = onOpenDeviceSelector,
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White.copy(alpha = 0.12f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.SpeakerGroup,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "This Device",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 12.sp
                            )
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        onClick = onOpenLyrics,
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White.copy(alpha = 0.12f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Lyrics, contentDescription = "Lyrics", tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Lyrics", color = Color.White, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold))
                        }
                    }

                    Surface(
                        onClick = onOpenQueue,
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White.copy(alpha = 0.12f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Queue", tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Queue", color = Color.White, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Sleep Timer Dialog
 */
@Composable
fun SleepTimerDialog(
    onSetTimerMinutes: (Int) -> Unit,
    onCancelTimer: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Bedtime,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text("Sleep Timer", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val durations = listOf(
                    15 to "15 minutes",
                    30 to "30 minutes",
                    45 to "45 minutes",
                    60 to "1 hour",
                    -1 to "End of Track"
                )
                durations.forEach { (mins, label) ->
                    Surface(
                        onClick = {
                            onSetTimerMinutes(mins)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowForwardIos,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onCancelTimer()
                onDismiss()
            }) {
                Text("Turn Off")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongCreditsBottomSheet(
    track: PlayableTrack,
    onViewArtist: (String) -> Unit = {},
    onViewAlbum: (String) -> Unit = {},
    onDismiss: () -> Unit
) {
    val artistList = remember(track.artist) {
        track.artist.split(Regex("[,&;/]|\\bfeat\\.?\\b|\\bft\\.?\\b", RegexOption.IGNORE_CASE))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header preview with album artwork
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    AsyncImage(
                        model = track.artwork,
                        contentDescription = track.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Song Credits",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // 1. Artists & Performers Section (with Artwork / Avatars and Clickable to open)
            Text(
                text = "Artists & Performers",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 2.dp)
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                artistList.forEach { artistName ->
                    Surface(
                        onClick = {
                            onViewArtist(artistName)
                        },
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Round Artist Avatar
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(
                                                MaterialTheme.colorScheme.primary,
                                                MaterialTheme.colorScheme.secondary
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (track.artwork.isNotBlank()) {
                                    AsyncImage(
                                        model = track.artwork,
                                        contentDescription = artistName,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Text(
                                        text = artistName.take(1).uppercase(),
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = Color.White
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = artistName,
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Artist • Tap to view profile & songs",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }

                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowForwardIos,
                                contentDescription = "View Artist",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // 2. Track & Album Technical Metadata Card
            Text(
                text = "Release & Audio Details",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 2.dp)
            )

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    CreditRow(label = "Song Title", value = track.title)

                    if (track.album.isNotBlank()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (track.albumId.isNotBlank()) {
                                        Modifier.clickable { onViewAlbum(track.albumId) }
                                    } else Modifier
                                )
                                .padding(vertical = 6.dp)
                        ) {
                            Text(
                                text = "Album",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = track.album,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Medium,
                                        color = if (track.albumId.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                                if (track.albumId.isNotBlank()) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.ArrowForwardIos,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (track.language.isNotBlank()) {
                        CreditRow(label = "Language", value = track.language.replaceFirstChar { it.uppercase() })
                    }
                    if (track.year.isNotBlank()) {
                        CreditRow(label = "Release Year", value = track.year)
                    }
                    CreditRow(label = "Audio Quality", value = "320 kbps Ultra HD (Studio Master Fidelity)")
                    CreditRow(label = "Source", value = "SMusic Official Catalog")
                    if (track.copyright.isNotBlank()) {
                        CreditRow(label = "Copyright / Label", value = track.copyright)
                    }
                }
            }
        }
    }
}

@Composable
private fun CreditRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongOptionsBottomSheet(
    track: PlayableTrack,
    isLiked: Boolean,
    isShuffle: Boolean,
    repeatMode: RepeatMode,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    downloadProgress: Int,
    volume: Float = 1.0f,
    onVolumeChange: (Float) -> Unit = {},
    onToggleLike: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onShowQueue: () -> Unit,
    onShowLyrics: () -> Unit,
    onShowCredits: () -> Unit,
    onDownload: () -> Unit,
    onAddToPlaylist: () -> Unit = {},
    onViewAlbum: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header: Track preview
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    AsyncImage(
                        model = track.artwork,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // 1. Volume Control Bar with large start and end rounding
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val volIcon = when {
                        volume <= 0.01f -> Icons.AutoMirrored.Filled.VolumeMute
                        volume < 0.5f -> Icons.AutoMirrored.Filled.VolumeDown
                        else -> Icons.AutoMirrored.Filled.VolumeUp
                    }
                    IconButton(
                        onClick = {
                            if (volume > 0.05f) onVolumeChange(0f) else onVolumeChange(0.8f)
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = volIcon,
                            contentDescription = "Volume",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Slider(
                        value = volume.coerceIn(0f, 1f),
                        onValueChange = onVolumeChange,
                        valueRange = 0f..1f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${(volume * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.widthIn(min = 36.dp),
                        textAlign = TextAlign.End
                    )
                }
            }

            // 2. Mini Action Bar with Large Rounded Corners (Like, Download, Playlist, Share)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Like Mini Pill
                MiniActionPill(
                    icon = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    label = if (isLiked) "Liked" else "Like",
                    isActive = isLiked,
                    activeColor = MaterialTheme.colorScheme.primary,
                    onClick = onToggleLike,
                    modifier = Modifier.weight(1f)
                )

                // Download Mini Pill (only download text)
                val dlLabel = when {
                    isDownloading -> "$downloadProgress%"
                    isDownloaded -> "Saved"
                    else -> "Download"
                }
                MiniActionPill(
                    icon = when {
                        isDownloaded -> Icons.Filled.OfflinePin
                        isDownloading -> Icons.Outlined.Download
                        else -> Icons.Outlined.Download
                    },
                    label = dlLabel,
                    isActive = isDownloaded || isDownloading,
                    activeColor = if (isDownloaded) Color(0xFF10B981) else MaterialTheme.colorScheme.primary,
                    onClick = onDownload,
                    modifier = Modifier.weight(1f)
                )

                // Add to Playlist Mini Pill
                MiniActionPill(
                    icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                    label = "Playlist",
                    isActive = false,
                    activeColor = MaterialTheme.colorScheme.primary,
                    onClick = onAddToPlaylist,
                    modifier = Modifier.weight(1f)
                )

                // Share Mini Pill with arrow style icon
                MiniActionPill(
                    icon = Icons.AutoMirrored.Rounded.Send,
                    label = "Share",
                    isActive = false,
                    activeColor = MaterialTheme.colorScheme.primary,
                    onClick = onShare,
                    modifier = Modifier.weight(1f)
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

            // 3. Option Items with Large Rounded Corner Shapes & Arrow Navigation Style
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                OptionItem(
                    icon = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    title = if (isLiked) "Remove from Liked Songs" else "Save to Liked Songs",
                    tint = if (isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    onClick = {
                        onToggleLike()
                        onDismiss()
                    }
                )

                OptionItem(
                    icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                    title = "Add to Playlist",
                    tint = MaterialTheme.colorScheme.onSurface,
                    onClick = onAddToPlaylist
                )

                OptionItem(
                    icon = when {
                        isDownloading -> Icons.Outlined.Download
                        isDownloaded -> Icons.Filled.OfflinePin
                        else -> Icons.Outlined.Download
                    },
                    title = when {
                        isDownloading -> "Downloading... $downloadProgress%"
                        isDownloaded -> "Downloaded Offline (Tap to remove)"
                        else -> "Download"
                    },
                    tint = when {
                        isDownloaded -> Color(0xFF10B981)
                        isDownloading -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    onClick = onDownload
                )

                OptionItem(
                    icon = Icons.AutoMirrored.Filled.QueueMusic,
                    title = "Up Next (Queue)",
                    onClick = onShowQueue
                )

                OptionItem(
                    icon = Icons.Default.Lyrics,
                    title = "View Lyrics",
                    onClick = onShowLyrics
                )

                OptionItem(
                    icon = Icons.Rounded.Shuffle,
                    title = if (isShuffle) "Shuffle: ON" else "Shuffle: OFF",
                    tint = if (isShuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    onClick = onToggleShuffle
                )

                val repeatTitle = when (repeatMode) {
                    RepeatMode.ONE -> "Repeat: Track (One)"
                    RepeatMode.ALL -> "Repeat: All"
                    RepeatMode.OFF -> "Repeat: OFF"
                }
                OptionItem(
                    icon = if (repeatMode == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                    title = repeatTitle,
                    tint = if (repeatMode != RepeatMode.OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    onClick = onToggleRepeat
                )

                if (track.albumId.isNotBlank()) {
                    OptionItem(
                        icon = Icons.Outlined.Album,
                        title = "View Album",
                        onClick = onViewAlbum
                    )
                }

                OptionItem(
                    icon = Icons.Outlined.Info,
                    title = "View Song Credits",
                    onClick = onShowCredits
                )

                OptionItem(
                    icon = Icons.AutoMirrored.Rounded.Send,
                    title = "Share Song",
                    onClick = onShare
                )
            }
        }
    }
}

@Composable
private fun MiniActionPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = if (isActive) activeColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    val contentColor = if (isActive) activeColor else MaterialTheme.colorScheme.onSurface

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = bgColor,
        border = if (isActive) androidx.compose.foundation.BorderStroke(1.dp, activeColor.copy(alpha = 0.5f)) else null,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 11.sp
                ),
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun OptionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = tint,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForwardIos,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
fun AddToPlaylistDialog(
    track: PlayableTrack,
    repository: MusicRepository,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val playlists by repository.userPlaylists.collectAsState(initial = emptyList())
    var showCreateDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("New Playlist", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text("Playlist Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPlaylistName.isNotBlank()) {
                            scope.launch {
                                val id = repository.createPlaylist(newPlaylistName.trim())
                                repository.addSongToPlaylist(id, track)
                                Toast.makeText(context, "Added to playlist \"$newPlaylistName\"", Toast.LENGTH_SHORT).show()
                                showCreateDialog = false
                                onDismiss()
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Create & Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text("Add to Playlist", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Button to create new playlist
                Surface(
                    onClick = { showCreateDialog = true },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Create New Playlist",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (playlists.isEmpty()) {
                    Text(
                        text = "No playlists found. Create one above!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp)
                    )
                } else {
                    playlists.forEach { playlist ->
                        Surface(
                            onClick = {
                                scope.launch {
                                    repository.addSongToPlaylist(playlist.id, track)
                                    Toast.makeText(context, "Added to playlist \"${playlist.name}\"", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                }
                            },
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = playlist.name,
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                    if (playlist.description.isNotBlank()) {
                                        Text(
                                            text = playlist.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowForwardIos,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun DeviceConnectDialog(
    onDismiss: () -> Unit
) {
    var selectedDevice by remember { mutableStateOf("phone") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Devices,
                    contentDescription = null,
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Connect a Device",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "AUDIO OUTPUT",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                Spacer(modifier = Modifier.height(10.dp))

                // Option 1: This Phone Speaker
                Surface(
                    onClick = { selectedDevice = "phone" },
                    shape = RoundedCornerShape(16.dp),
                    color = if (selectedDevice == "phone") Color(0xFF10B981).copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = if (selectedDevice == "phone") androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF10B981)) else null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PhoneAndroid,
                            contentDescription = null,
                            tint = if (selectedDevice == "phone") Color(0xFF10B981) else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "This Phone",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (selectedDevice == "phone") Color(0xFF10B981) else MaterialTheme.colorScheme.onSurface
                                )
                            )
                            Text(
                                text = "Built-in Speaker • Ultra HD 320k Active",
                                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                            )
                        }
                        if (selectedDevice == "phone") {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Option 2: Bluetooth Device
                Surface(
                    onClick = { selectedDevice = "bluetooth" },
                    shape = RoundedCornerShape(16.dp),
                    color = if (selectedDevice == "bluetooth") Color(0xFF10B981).copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = if (selectedDevice == "bluetooth") androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF10B981)) else null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.BluetoothAudio,
                            contentDescription = null,
                            tint = if (selectedDevice == "bluetooth") Color(0xFF10B981) else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Bluetooth Audio / Headphones",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (selectedDevice == "bluetooth") Color(0xFF10B981) else MaterialTheme.colorScheme.onSurface
                                )
                            )
                            Text(
                                text = "Auto-routing enabled via System Settings",
                                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                            )
                        }
                        if (selectedDevice == "bluetooth") {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueBottomSheet(
    queue: List<PlayableTrack>,
    currentIndex: Int,
    isPlaying: Boolean = true,
    onDismiss: () -> Unit,
    onSelectTrack: (Int) -> Unit,
    onRemoveTrack: (Int) -> Unit,
    onClearQueue: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Playing Queue (${queue.size})",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                if (queue.isNotEmpty()) {
                    TextButton(onClick = onClearQueue) {
                        Text("Clear", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (queue.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Queue is empty", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 450.dp)
                ) {
                    itemsIndexed(queue, key = { idx, item -> "${item.id}_$idx" }) { index, track ->
                        val isCurrent = index == currentIndex
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else Color.Transparent)
                                .clickable { onSelectTrack(index) }
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            ) {
                                AsyncImage(
                                    model = track.artwork,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                if (isCurrent) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.50f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        ThreeLineVisualizer(
                                            isPlaying = isPlaying,
                                            color = MaterialTheme.colorScheme.primary,
                                            barWidth = 2.5.dp,
                                            maxHeight = 16.dp
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = track.artist,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            IconButton(onClick = { onRemoveTrack(index) }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsBottomSheet(
    track: PlayableTrack,
    repository: MusicRepository,
    onDismiss: () -> Unit
) {
    var lyricsText by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(track.id) {
        isLoading = true
        val lyrics = repository.getLyrics(track.lyricsId, null)
        lyricsText = lyrics
        isLoading = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp)
        ) {
            Text(
                text = "Lyrics",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = "${track.title} • ${track.artist}",
                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = lyricsText ?: "Lyrics not available for this track.",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 17.sp,
                            lineHeight = 28.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
