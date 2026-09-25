package com.nuvio.tv.domain.model

import androidx.compose.runtime.Immutable
import com.nuvio.tv.data.repository.epg.ChannelEpgInfo

@Immutable
data class TvChannelItem(
    val id: String,
    val type: String,
    val name: String,
    val poster: String? = null,
    val logo: String? = null,
    val genres: List<String> = emptyList(),
    val description: String? = null,
    val addonName: String,
    val addonManifestUrl: String,
    val addonLogo: String? = null,
    val catalogId: String,
    val catalogName: String,
    val isFavorite: Boolean = false,
    val epgInfo: ChannelEpgInfo? = null,
) {
    val displayLogo: String?
        get() = logo?.takeIf { it.isNotBlank() } ?: poster?.takeIf { it.isNotBlank() }

    val primaryGenre: String?
        get() = genres.firstOrNull { it.isNotBlank() }

    fun stableKey(): String = "${addonManifestUrl.trim().removeSuffix("/")}:$type:$id"

    fun isFavoritedIn(keys: Set<String>): Boolean =
        keys.contains(stableKey()) || keys.contains("$addonManifestUrl:$type:$id")
}

@Immutable
data class TvCatalogSection(
    val id: String,
    val title: String,
    val iconEmoji: String? = null,
    val channels: List<TvChannelItem>,
)

@Immutable
data class TvAddonFilterOption(
    val manifestUrl: String,
    val addonName: String,
    val logoUrl: String? = null,
    val catalogCount: Int = 0,
    val isSelected: Boolean = false,
)

@Immutable
data class TvChannelsUiState(
    val allChannels: List<TvChannelItem> = emptyList(),
    val filteredChannels: List<TvChannelItem> = emptyList(),
    val catalogSections: List<TvCatalogSection> = emptyList(),
    val availableAddons: List<TvAddonFilterOption> = emptyList(),
    val selectedAddonUrls: Set<String> = emptySet(),
    val favoriteKeys: Set<String> = emptySet(),
    val categories: List<String> = emptyList(),
    val selectedCategory: String = "",
    val searchQuery: String = "",
    val isLoadingChannels: Boolean = false,
    val channelsErrorMessage: String? = null,
    val previewChannel: TvChannelItem? = null,
    val previewStreams: List<Stream> = emptyList(),
    val selectedStreamIndex: Int = 0,
    val isLoadingPreview: Boolean = false,
    val isPreviewPlaybackActive: Boolean = false,
    val previewErrorMessage: String? = null,
    val isAddonsDialogVisible: Boolean = false,
    val isScheduleDialogVisible: Boolean = false,
    val isFullscreen: Boolean = false,
    val hideAdultChannels: Boolean = true,
    val channelAutoplay: Boolean = false,
) {
    val activeStream: Stream?
        get() = previewStreams.getOrNull(selectedStreamIndex)

    val totalChannelsCount: Int
        get() = allChannels.size

    val filteredChannelsCount: Int
        get() = filteredChannels.size
}

/**
 * Resolves any playable media stream URL for live TV channels, supporting:
 * - HLS (.m3u8, .m3u)
 * - MPEG-TS (.ts)
 * - DASH (.mpd)
 * - RTMP / RTMPS
 * - RTSP / RTSPS
 * - HTTP/HTTPS direct media (MP4, MKV, FLV, WebM, AAC, MP3, etc.)
 * - Xtream Codes dynamic live endpoints
 * - URLs located in stream.url, stream.externalUrl, or stream.sources
 */
val Stream.playableTvUrl: String?
    get() = resolvePlayableTvStreamUrl(this)

fun resolvePlayableTvStreamUrl(stream: Stream): String? {
    val candidates = listOfNotNull(
        stream.getStreamUrl(),
        stream.url,
        stream.externalUrl
    ) + stream.sources.orEmpty()

    for (cand in candidates) {
        val cleaned = cleanTvStreamUrl(cand)
        if (!cleaned.isNullOrBlank() && isSupportedTvProtocol(cleaned) && !isTorrentOrMagnet(cleaned)) {
            return cleaned
        }
    }

    return null
}

fun cleanTvStreamUrl(rawUrl: String?): String? {
    if (rawUrl.isNullOrBlank()) return null
    var url = rawUrl.trim()

    // Desembrulha esquemas de intents comuns em addons IPTV Android (ex: Brazuca, Kodi/VLC intents)
    if (url.startsWith("intent:", ignoreCase = true)) {
        val afterIntent = url.substring(7)
        val candidate = afterIntent.substringBefore('#').substringBefore(';')
        if (candidate.startsWith("http://", ignoreCase = true) || candidate.startsWith("https://", ignoreCase = true)) {
            url = candidate
        }
    }

    // Desembrulha prefixos de players externos (ex: vlc://https://..., mxplayer://http://...)
    val playerSchemes = listOf("vlc://", "mxplayer://", "wuffy://", "nplayer://", "iplayer://")
    for (scheme in playerSchemes) {
        if (url.startsWith(scheme, ignoreCase = true)) {
            url = url.substring(scheme.length)
            break
        }
    }

    return url.takeIf { it.isNotBlank() }
}

fun isSupportedTvProtocol(url: String): Boolean {
    val u = url.lowercase().trim()
    return u.startsWith("http://") ||
        u.startsWith("https://") ||
        u.startsWith("rtmp://") ||
        u.startsWith("rtmps://") ||
        u.startsWith("rtsp://") ||
        u.startsWith("rtsps://") ||
        u.startsWith("udp://") ||
        u.startsWith("rtp://") ||
        u.startsWith("mms://") ||
        u.startsWith("mmsh://")
}

private fun isTorrentOrMagnet(url: String): Boolean {
    val u = url.lowercase().trim()
    return u.startsWith("magnet:") || u.startsWith("torrent://") || u.startsWith("torrent:")
}

