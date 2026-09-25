package com.nuvio.tv.core.player

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.extractor.ts.TsExtractor
import com.nuvio.tv.ui.screens.player.PlayerMediaSourceFactory
import com.nuvio.tv.ui.screens.player.PlayerPlaybackNetworking

/**
 * Factory dedicada para criação de [MediaSource] otimizada para transmissões de TV ao vivo e IPTV.
 *
 * Características críticas para estabilidade e fluidez:
 * 1. Sanitização e suporte a headers proxy dos addons Stremio/IPTV (User-Agent, Referer, etc.).
 * 2. Suporte a redirecionamentos cross-protocol (HTTP para HTTPS ou vice-versa via OkHttp).
 * 3. DNS IPv4-first prevenindo congelamentos de resolução de nomes em redes IPv6.
 * 4. Conversão automática de credenciais em URLs Xtream Codes (user:pass@host) para Basic Auth.
 * 5. HlsMediaSource com allowChunklessPreparation = true para inicialização instantânea sem espera de múltiplos chunks.
 * 6. Suporte completo a MPEG-TS com flags para áudio DTS/HDMV e detecção estendida de timestamps.
 */
@OptIn(UnstableApi::class)
object TvLiveMediaSourceFactory {
    private const val TAG = "TvLiveMediaSource"

    fun createMediaSource(
        context: Context,
        rawUrl: String,
        headers: Map<String, String>? = null
    ): MediaSource {
        // 1. Trata credenciais embutidas na URL (http://user:pass@server:port/live/...)
        val (cleanUrl, mergedHeaders) = PlayerMediaSourceFactory.extractUserInfoAuth(
            rawUrl,
            headers.orEmpty()
        )

        // 2. Sanitiza headers HTTP e adiciona no-cache para garantir sincronia sempre ao vivo
        val liveHeaders = LinkedHashMap<String, String>(mergedHeaders.size + 2).apply {
            putAll(mergedHeaders)
            if (none { it.key.equals("Cache-Control", ignoreCase = true) }) {
                put("Cache-Control", "no-cache, no-store, must-revalidate")
            }
            if (none { it.key.equals("Pragma", ignoreCase = true) }) {
                put("Pragma", "no-cache")
            }
        }
        val sanitizedHeaders = PlayerMediaSourceFactory.sanitizeHeaders(liveHeaders)

        // 3. Cria fábrica de DataSource de alta performance com OkHttp + IPv4FirstDns + SSL fallback
        val dataSourceFactory: DataSource.Factory = PlayerPlaybackNetworking.createDataSourceFactory(
            context,
            sanitizedHeaders
        )

        // 4. Determina MIME Type
        val resolvedMimeType = PlayerMediaSourceFactory.inferMimeType(
            url = cleanUrl,
            filename = null,
            responseHeaders = null
        )

        val isHls = resolvedMimeType == MimeTypes.APPLICATION_M3U8 ||
            cleanUrl.contains(".m3u8", ignoreCase = true) ||
            cleanUrl.contains("hls", ignoreCase = true) ||
            (cleanUrl.contains("/live/", ignoreCase = true) && !cleanUrl.endsWith(".ts", ignoreCase = true))

        val isDash = resolvedMimeType == MimeTypes.APPLICATION_MPD ||
            cleanUrl.contains(".mpd", ignoreCase = true)

        // 5. Configura MediaItem com parâmetros de Live Stream
        val mediaItemBuilder = MediaItem.Builder()
            .setUri(Uri.parse(cleanUrl))
            .setLiveConfiguration(
                MediaItem.LiveConfiguration.Builder()
                    .setMaxPlaybackSpeed(1.02f)
                    .setMinPlaybackSpeed(0.98f)
                    .build()
            )

        when {
            isHls -> mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
            isDash -> mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_MPD)
            resolvedMimeType != null -> mediaItemBuilder.setMimeType(resolvedMimeType)
            cleanUrl.contains(".ts", ignoreCase = true) -> mediaItemBuilder.setMimeType(MimeTypes.VIDEO_MP2T)
        }

        val mediaItem = mediaItemBuilder.build()

        Log.d(TAG, "Creating Live TV MediaSource for: $cleanUrl (isHls=$isHls, isDash=$isDash, mime=${mediaItem.localConfiguration?.mimeType})")

        // 6. Retorna a MediaSource adequada
        return when {
            isHls -> {
                HlsMediaSource.Factory(dataSourceFactory)
                    .setAllowChunklessPreparation(true)
                    .createMediaSource(mediaItem)
            }
            isDash -> {
                DashMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(mediaItem)
            }
            else -> {
                val extractorsFactory = DefaultExtractorsFactory()
                    .setTsExtractorFlags(DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS)
                    .setTsExtractorTimestampSearchBytes(1500 * TsExtractor.TS_PACKET_SIZE)
                DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory)
                    .createMediaSource(mediaItem)
            }
        }
    }
}
