package com.efprojects.vombatsu_client.content

import android.content.pm.ActivityInfo
import android.view.LayoutInflater
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.annotation.OptIn
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.efprojects.vombatsu_client.ErrorLog
import com.efprojects.vombatsu_client.R
import kotlinx.coroutines.delay

private const val FALLBACK_ASPECT = 16f / 9f
private const val VIDEO_JOB_POLL_MS = 10_000L
private val MAX_VIDEO_HEIGHT = 560.dp
private val MEDIA_SHAPE = RoundedCornerShape(8.dp)
val MAX_CONTENT_WIDTH = 640.dp

@Composable
private fun contentWidthPx(): Int {
    val window = LocalWindowInfo.current.containerSize.width
    val limit = with(LocalDensity.current) { MAX_CONTENT_WIDTH.roundToPx() }
    return minOf(window, limit)
}

@Composable
fun MediaView(media: Media, modifier: Modifier = Modifier) {
    when (media) {
        is Media.Image -> ImageView(media, modifier)
        is Media.Video -> VideoView(media, modifier)
    }
}

@Composable
private fun ImageView(image: Media.Image, modifier: Modifier) {
    val aspect = if (image.width != null && image.height != null && image.height > 0) {
        image.width.toFloat() / image.height
    } else {
        FALLBACK_ASPECT
    }
    val url = imageUrl(image.path, contentWidthPx())
    var failed by remember(url) { mutableStateOf(false) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(if (failed) FALLBACK_ASPECT else aspect)
            .clip(MEDIA_SHAPE)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (failed) {
            MediaUnavailable(MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                onError = { failed = true },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun MediaUnavailable(color: Color) {
    Text(
        stringResource(R.string.media_unavailable),
        color = color,
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun VideoView(video: Media.Video, modifier: Modifier) {
    if (video.jobId == null) {
        ReadyVideo(video, modifier)
    } else {
        ProcessingVideo(video, video.jobId, modifier)
    }
}

@Composable
private fun VideoFrame(video: Media.Video, modifier: Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = MAX_VIDEO_HEIGHT)
            .aspectRatio(video.aspectRatio ?: FALLBACK_ASPECT, matchHeightConstraintsFirst = true)
            .clip(MEDIA_SHAPE)
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun ReadyVideo(video: Media.Video, modifier: Modifier) {
    var playing by rememberSaveable(video.url) { mutableStateOf(false) }
    var failed by remember(video.url) { mutableStateOf(false) }
    VideoFrame(video, modifier) {
        if (failed) {
            MediaUnavailable(Color.White)
        } else if (playing) {
            VideoPlayer(video, onError = { failed = true })
        } else {
            Poster(video.thumbnailUrl, stringResource(R.string.play_symbol)) { playing = true }
        }
    }
}

@Composable
private fun ProcessingVideo(video: Media.Video, jobId: String, modifier: Modifier) {
    val jobs = LocalVideoJobs.current
    val job by produceState<VideoJob?>(null, jobId) {
        while (value?.finished != true) {
            jobs.status(jobId)?.let { value = it }
            if (value?.finished != true) delay(VIDEO_JOB_POLL_MS)
        }
    }
    val playlist = job?.playlistKey
    if (playlist != null) {
        ReadyVideo(
            video.copy(
                url = videoUrl(playlist),
                thumbnailUrl = job?.thumbnailKey?.let(::rawImageUrl) ?: video.thumbnailUrl,
                jobId = null,
            ),
            modifier,
        )
    } else {
        VideoFrame(video, modifier) {
            Text(
                stringResource(if (job?.finished == true) R.string.video_processing_failed else R.string.video_processing),
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun Poster(thumbnailUrl: String?, symbol: String, onClick: () -> Unit) {
    Box(Modifier.fillMaxSize().clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        if (thumbnailUrl != null) {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Text(
            symbol,
            color = Color.White,
            style = MaterialTheme.typography.displaySmall,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 20.dp, vertical = 8.dp),
        )
    }
}

private class Playback(var position: Long = 0L, var playWhenReady: Boolean = true) {
    var player: Player? = null

    fun capture() {
        player?.let {
            position = it.currentPosition
            playWhenReady = it.playWhenReady
        }
    }
}

private val PlaybackSaver = listSaver<Playback, Any>(
    save = { playback ->
        playback.capture()
        listOf(playback.position, playback.playWhenReady)
    },
    restore = { Playback(it[0] as Long, it[1] as Boolean) },
)

@OptIn(UnstableApi::class)
@Composable
private fun VideoPlayer(video: Media.Video, onError: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val playback = rememberSaveable(video.url, saver = PlaybackSaver) { Playback() }
    val player = remember(video.url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(video.url))
            repeatMode = if (video.loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            seekTo(playback.position)
            playWhenReady = playback.playWhenReady
            prepare()
        }
    }
    DisposableEffect(player, lifecycleOwner) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                ErrorLog.write("Playback failed: ${video.url}", error)
                onError()
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) player.pause()
        }
        playback.player = player
        player.addListener(listener)
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            playback.capture()
            playback.player = null
            player.release()
        }
    }
    var fullscreen by rememberSaveable(video.url) { mutableStateOf(false) }
    PlayerSurface(if (fullscreen) null else player, fullscreen = false, onToggle = { fullscreen = true })
    if (fullscreen) {
        FullscreenVideo(player, video.aspectRatio, onExit = { fullscreen = false })
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun PlayerSurface(player: Player?, fullscreen: Boolean, onToggle: () -> Unit) {
    AndroidView(
        factory = { viewContext ->
            val view = LayoutInflater.from(viewContext).inflate(R.layout.video_player, null) as PlayerView
            view.apply {
                setFullscreenButtonState(fullscreen)
                setFullscreenButtonClickListener { onToggle() }
            }
        },
        update = { it.player = player },
        onRelease = { it.player = null },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun FullscreenVideo(player: Player, aspectRatio: Float?, onExit: () -> Unit) {
    val activity = LocalActivity.current
    DisposableEffect(activity, aspectRatio) {
        val previous = activity?.requestedOrientation
        if (aspectRatio != null && aspectRatio > 1f) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        onDispose { if (previous != null) activity.requestedOrientation = previous }
    }
    Dialog(
        onDismissRequest = onExit,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val window = (LocalView.current.parent as DialogWindowProvider).window
        DisposableEffect(window) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            WindowCompat.getInsetsController(window, window.decorView).apply {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
            onDispose {}
        }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            PlayerSurface(player, fullscreen = true, onToggle = onExit)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GalleryView(items: List<Media>, modifier: Modifier = Modifier) {
    if (items.isEmpty()) return
    val pagerState = rememberPagerState { items.size }
    Box(modifier) {
        HorizontalPager(pagerState) { page -> MediaView(items[page]) }
        if (items.size > 1) {
            Text(
                "${pagerState.currentPage + 1}/${items.size}",
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
fun EmbedView(embed: Block.Embed, modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    Column(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(FALLBACK_ASPECT)
                .clip(MEDIA_SHAPE)
                .background(Color.Black),
        ) {
            Poster(embed.thumbnailUrl, stringResource(R.string.play_symbol)) {
                uriHandler.openUri(embed.url)
            }
        }
        Text(
            stringResource(R.string.open_external, embed.provider),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
