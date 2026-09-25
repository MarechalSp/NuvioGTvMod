package com.nuvio.tv.core.player

import android.content.Context
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultAllocator
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.extractor.ts.TsExtractor
import com.nuvio.tv.ui.screens.player.PlayerPlaybackNetworking
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton dedicado a gerenciar uma instância reutilizável do ExoPlayer para pré-visualização
 * e reprodução integrada de canais de TV ao vivo.
 *
 * Em TVs e dispositivos como Chromecast e TV Box (com CPU e RAM modestas), criar e destruir
 * instâncias do ExoPlayer a cada canal causa alto consumo de memória, vazamento de
 * decodificadores de hardware e travamentos. Esse pool mantém uma única instância ativa,
 * apenas trocando a mídia em tempo real.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Singleton
class TvChannelPreviewPlayerPool @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "TvPreviewPlayerPool"
    }

    private var _player: ExoPlayer? = null
    private val yielded = AtomicBoolean(false)
    private val released = AtomicBoolean(false)

    fun acquire(): ExoPlayer? {
        if (released.get()) return null
        if (yielded.get()) {
            reclaim()
        }
        return _player ?: createPlayer().also { _player = it }
    }

    fun stop() {
        _player?.let { player ->
            runCatching {
                player.playWhenReady = false
                player.stop()
                player.clearMediaItems()
            }
        }
    }

    fun yield() {
        if (yielded.compareAndSet(false, true)) {
            Log.d(TAG, "Yielding TV preview player to free hardware decoders")
            _player?.let { player ->
                runCatching { player.stop() }
                runCatching { player.clearMediaItems() }
                runCatching { player.release() }
            }
            _player = null
        }
    }

    fun reclaim() {
        if (yielded.compareAndSet(true, false)) {
            Log.d(TAG, "Reclaiming TV preview player")
            _player = createPlayer()
        }
    }

    fun release() {
        if (released.compareAndSet(false, true)) {
            _player?.let { player ->
                runCatching { player.stop() }
                runCatching { player.clearMediaItems() }
                runCatching { player.release() }
            }
            _player = null
        }
    }

    private fun createPlayer(): ExoPlayer {
        // Buffer balanceado para TV ao vivo: rápido para iniciar (1.5s), mas com margem (50s)
        // para absorver variações de rota sem entrar em buffering eterno
        val loadControl = DefaultLoadControl.Builder()
            .setAllocator(DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE))
            .setBufferDurationsMs(
                /* minBufferMs = */ 15_000,
                /* maxBufferMs = */ 50_000,
                /* bufferForPlaybackMs = */ 1_500,
                /* bufferForPlaybackAfterRebufferMs = */ 3_000
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)

        val trackSelector = DefaultTrackSelector(context).apply {
            parameters = buildUponParameters()
                .setForceHighestSupportedBitrate(false)
                .build()
        }

        val extractorsFactory = DefaultExtractorsFactory()
            .setTsExtractorFlags(DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS)
            .setTsExtractorTimestampSearchBytes(1500 * TsExtractor.TS_PACKET_SIZE)

        val defaultDataSourceFactory = PlayerPlaybackNetworking.createDataSourceFactory(context, emptyMap())
        val defaultMediaSourceFactory = DefaultMediaSourceFactory(defaultDataSourceFactory, extractorsFactory)

        return ExoPlayer.Builder(context, renderersFactory)
            .setLoadControl(loadControl)
            .setTrackSelector(trackSelector)
            .setMediaSourceFactory(defaultMediaSourceFactory)
            .build()
            .apply {
                videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
                repeatMode = Player.REPEAT_MODE_OFF
                playWhenReady = true
            }
    }
}
