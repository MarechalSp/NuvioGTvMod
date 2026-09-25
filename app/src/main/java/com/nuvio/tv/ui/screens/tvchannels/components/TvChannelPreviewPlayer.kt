package com.nuvio.tv.ui.screens.tvchannels.components

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import android.util.Log
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.nuvio.tv.core.player.TvChannelPreviewPlayerPool
import com.nuvio.tv.core.player.TvLiveMediaSourceFactory
import com.nuvio.tv.ui.components.LoadingIndicator
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun TvChannelPreviewPlayer(
    streamUrl: String?,
    playerPool: TvChannelPreviewPlayerPool,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    headers: Map<String, String>? = null,
    isMuted: Boolean = false,
    onFirstFrameRendered: () -> Unit = {},
    onError: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val currentUrl by rememberUpdatedState(streamUrl)
    val currentHeaders by rememberUpdatedState(headers)
    val currentIsPlaying by rememberUpdatedState(isPlaying)
    val currentOnFirstFrame by rememberUpdatedState(onFirstFrameRendered)
    val currentOnError by rememberUpdatedState(onError)

    var hasRenderedFirstFrame by remember(streamUrl) { mutableStateOf(false) }
    var isBuffering by remember(streamUrl) { mutableStateOf(true) }

    val player = remember(playerPool) {
        playerPool.acquire()
    }

    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> player?.playWhenReady = false
                Lifecycle.Event.ON_RESUME -> if (currentIsPlaying) player?.playWhenReady = true
                Lifecycle.Event.ON_STOP -> playerPool.stop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            playerPool.stop()
        }
    }

    DisposableEffect(player, streamUrl) {
        val activePlayer = player ?: return@DisposableEffect onDispose {}

        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                hasRenderedFirstFrame = true
                isBuffering = false
                currentOnFirstFrame()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> isBuffering = true
                    Player.STATE_READY -> {
                        isBuffering = false
                        hasRenderedFirstFrame = true
                    }
                    Player.STATE_ENDED -> isBuffering = false
                    else -> Unit
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e("TvPreviewPlayer", "ExoPlayer playback error: ${error.errorCodeName} (${error.errorCode})", error)
                isBuffering = false
                currentOnError(error.localizedMessage ?: "Erro ao reproduzir stream ao vivo")
            }
        }

        activePlayer.addListener(listener)

        onDispose {
            activePlayer.removeListener(listener)
        }
    }

    // Watchdog de Timeout de Buffering para evitar loop infinito em conexões travadas
    LaunchedEffect(streamUrl, isBuffering, isPlaying, hasRenderedFirstFrame) {
        if (isPlaying && isBuffering && !hasRenderedFirstFrame && !streamUrl.isNullOrBlank()) {
            delay(14_000L)
            if (isBuffering && !hasRenderedFirstFrame) {
                Log.w("TvPreviewPlayer", "Live stream buffering timeout after 14s for: $streamUrl")
                currentOnError("Tempo limite ao conectar à transmissão ao vivo")
            }
        }
    }

    LaunchedEffect(player, streamUrl, headers, isPlaying, isMuted) {
        val activePlayer = player ?: return@LaunchedEffect
        activePlayer.volume = if (isMuted) 0f else 1f

        if (isPlaying && !streamUrl.isNullOrBlank()) {
            hasRenderedFirstFrame = false
            isBuffering = true
            runCatching {
                val mediaSource = TvLiveMediaSourceFactory.createMediaSource(
                    context = context,
                    rawUrl = streamUrl,
                    headers = headers
                )
                activePlayer.setMediaSource(mediaSource, /* resetPosition = */ true)
                activePlayer.prepare()
                activePlayer.seekToDefaultPosition()
                activePlayer.playWhenReady = true
            }.onFailure { err ->
                Log.e("TvPreviewPlayer", "Failed to create live MediaSource: ${err.message}", err)
                isBuffering = false
                currentOnError(err.localizedMessage ?: "Falha ao preparar stream ao vivo")
            }
        } else {
            hasRenderedFirstFrame = false
            isBuffering = false
            activePlayer.playWhenReady = false
            activePlayer.stop()
            activePlayer.clearMediaItems()
        }
    }

    Box(
        modifier = modifier.background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        if (player != null) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        this.player = player
                    }
                },
                update = { view ->
                    if (view.player != player) {
                        view.player = player
                    }
                }
            )
        }

        // Indicador de Carregamento inicial ou Buffering suave
        AnimatedVisibility(
            visible = isBuffering || !hasRenderedFirstFrame,
            enter = fadeIn(animationSpec = tween(250)),
            exit = fadeOut(animationSpec = tween(250))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = if (hasRenderedFirstFrame) 0.4f else 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                LoadingIndicator(modifier = Modifier.size(36.dp))
            }
        }
    }
}
