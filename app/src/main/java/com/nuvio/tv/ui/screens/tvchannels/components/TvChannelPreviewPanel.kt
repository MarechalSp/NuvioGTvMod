package com.nuvio.tv.ui.screens.tvchannels.components

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import androidx.compose.ui.res.stringResource
import com.nuvio.tv.R
import com.nuvio.tv.core.player.TvChannelPreviewPlayerPool
import com.nuvio.tv.domain.model.Stream
import com.nuvio.tv.domain.model.TvChannelItem
import com.nuvio.tv.domain.model.playableTvUrl
import com.nuvio.tv.ui.components.LoadingIndicator
import com.nuvio.tv.ui.theme.NuvioTheme

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvChannelPreviewPanel(
    channel: TvChannelItem?,
    streams: List<Stream>,
    selectedStreamIndex: Int,
    isLoading: Boolean,
    errorMessage: String?,
    playerPool: TvChannelPreviewPlayerPool,
    isPreviewActive: Boolean = true,
    onStartPlayback: () -> Unit = {},
    onWatchFullscreen: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenSchedule: () -> Unit,
    onStreamSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeStream = streams.getOrNull(selectedStreamIndex)
    val playableUrl = activeStream?.playableTvUrl
    val streamHeaders = activeStream?.behaviorHints?.proxyHeaders?.request
    var playbackError by remember(playableUrl) { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(NuvioTheme.colors.BackgroundElevated.copy(alpha = 0.55f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        if (channel == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Rounded.Tv,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.25f),
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.tv_channels_select_channel_preview),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }
            }
            return
        }

        // 1. Player de Vídeo em 16:9 ou Card de Início de Prévia
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black)
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (isPreviewActive && !playableUrl.isNullOrBlank()) {
                TvChannelPreviewPlayer(
                    streamUrl = playableUrl,
                    playerPool = playerPool,
                    isPlaying = playbackError == null,
                    headers = streamHeaders,
                    onError = { err ->
                        playbackError = err
                        if (streams.size > 1) {
                            val nextIdx = (selectedStreamIndex + 1) % streams.size
                            onStreamSelected(nextIdx)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else if (isLoading) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    LoadingIndicator(modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.tv_channels_preview_loading),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                }
            } else {
                // Card de Início de Prévia (Clique Obrigatório / Autoplay desativado)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(onClick = onStartPlayback),
                    contentAlignment = Alignment.Center
                ) {
                    val backdropUrl = channel.poster ?: channel.logo
                    if (!backdropUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = backdropUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.70f))
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(NuvioTheme.colors.Secondary.copy(alpha = 0.9f))
                                .border(2.dp, Color.White.copy(alpha = 0.35f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = "Reproduzir Prévia",
                                tint = Color.White,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.tv_channels_click_to_play),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Live Badge sobre o player
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFFE50914).copy(alpha = 0.9f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = stringResource(R.string.tv_channels_live_badge),
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Exibição de erro caso a transmissão falhe
            if (playbackError != null && streams.size <= 1) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.tv_channels_stream_unavailable),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = playbackError ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        val scrollState = rememberScrollState()
        val coroutineScope = rememberCoroutineScope()
        val isLightAccent = NuvioTheme.colors.Secondary.luminance() > 0.45f

        // 2. Metadados e Ações com scroll
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(scrollState)
        ) {
            // Cabeçalho do Canal
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val logoUrl = channel.displayLogo
                if (!logoUrl.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1E1E24))
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = logoUrl,
                            contentDescription = channel.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = channel.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${channel.addonName} • ${channel.catalogName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.55f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Botões de Ação para TV (Assistir Tela Cheia, Favoritar, Guia EPG)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                var isFullscreenFocused by remember { mutableStateOf(false) }

                Button(
                    onClick = onWatchFullscreen,
                    shape = ButtonDefaults.shape(shape = RoundedCornerShape(8.dp)),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    colors = ButtonDefaults.colors(
                        containerColor = Color.White.copy(alpha = 0.12f),
                        focusedContainerColor = Color.White,
                        focusedContentColor = Color.Black
                    ),
                    modifier = Modifier
                        .weight(1.2f)
                        .onFocusChanged { isFullscreenFocused = it.isFocused }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Fullscreen,
                            contentDescription = null,
                            tint = if (isFullscreenFocused) Color.Black else Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = stringResource(R.string.tv_channels_fullscreen_button),
                            color = if (isFullscreenFocused) Color.Black else Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                var isFavFocused by remember { mutableStateOf(false) }

                Button(
                    onClick = onToggleFavorite,
                    shape = ButtonDefaults.shape(shape = RoundedCornerShape(8.dp)),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    colors = ButtonDefaults.colors(
                        containerColor = if (channel.isFavorite) Color(0xFFFFD700).copy(alpha = 0.18f) else Color.White.copy(alpha = 0.12f),
                        focusedContainerColor = Color.White,
                        focusedContentColor = Color.Black
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { isFavFocused = it.isFocused }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val starTint = when {
                            isFavFocused -> if (channel.isFavorite) Color(0xFFB45309) else Color.Black
                            channel.isFavorite -> Color(0xFFFFD700)
                            else -> Color.White
                        }
                        Icon(
                            imageVector = if (channel.isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                            contentDescription = null,
                            tint = starTint,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = stringResource(if (channel.isFavorite) R.string.tv_channels_favorited_button else R.string.tv_channels_favorite_button),
                            color = if (isFavFocused) Color.Black else Color.White,
                            fontSize = 11.sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                var isScheduleFocused by remember { mutableStateOf(false) }

                Button(
                    onClick = onOpenSchedule,
                    shape = ButtonDefaults.shape(shape = RoundedCornerShape(8.dp)),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    colors = ButtonDefaults.colors(
                        containerColor = Color.White.copy(alpha = 0.12f),
                        focusedContainerColor = Color.White,
                        focusedContentColor = Color.Black
                    ),
                    modifier = Modifier
                        .weight(0.9f)
                        .onFocusChanged { isScheduleFocused = it.isFocused }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CalendarMonth,
                            contentDescription = null,
                            tint = if (isScheduleFocused) Color.Black else Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = stringResource(R.string.tv_channels_guide_button),
                            color = if (isScheduleFocused) Color.Black else Color.White,
                            fontSize = 11.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            // Seletor de Transmissões / Qualidades se houver mais de 1
            if (streams.size > 1) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.tv_channels_available_sources),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    itemsIndexed(streams) { idx, s ->
                        val isSel = idx == selectedStreamIndex
                        var isChipFocused by remember { mutableStateOf(false) }
                        Button(
                            onClick = { onStreamSelected(idx) },
                            modifier = Modifier.onFocusChanged { isChipFocused = it.isFocused },
                            shape = ButtonDefaults.shape(shape = RoundedCornerShape(6.dp)),
                            colors = ButtonDefaults.colors(
                                containerColor = if (isSel) NuvioTheme.colors.Secondary.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.08f),
                                focusedContainerColor = Color.White,
                                focusedContentColor = Color.Black
                            )
                        ) {
                            Text(
                                text = s.title ?: s.name ?: stringResource(R.string.tv_channels_source_n, idx + 1),
                                color = if (isChipFocused) Color.Black else Color.White,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            // 3. Informações de EPG (Programa Atual e Sinopse)
            val epg = channel.epgInfo
            val nowProg = epg?.nowProgram
            if (nowProg != null) {
                var isEpgCardFocused by remember { mutableStateOf(false) }
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isEpgCardFocused) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.04f))
                        .border(
                            width = if (isEpgCardFocused) 2.dp else 1.dp,
                            color = if (isEpgCardFocused) NuvioTheme.colors.FocusRing else Color.White.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .onFocusChanged { isEpgCardFocused = it.isFocused }
                        .focusable()
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                when (keyEvent.nativeKeyEvent.keyCode) {
                                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                                        if (scrollState.canScrollForward) {
                                            coroutineScope.launch {
                                                scrollState.animateScrollTo((scrollState.value + 250).coerceAtMost(scrollState.maxValue))
                                            }
                                            return@onPreviewKeyEvent true
                                        }
                                    }
                                    KeyEvent.KEYCODE_DPAD_UP -> {
                                        if (scrollState.canScrollBackward && scrollState.value > 10) {
                                            coroutineScope.launch {
                                                scrollState.animateScrollTo((scrollState.value - 250).coerceAtLeast(0))
                                            }
                                            return@onPreviewKeyEvent true
                                        }
                                    }
                                }
                            }
                            false
                        }
                        .padding(12.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = nowProg.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = nowProg.formattedTimeRange,
                                fontSize = 11.sp,
                                color = if (isLightAccent) Color.White.copy(alpha = 0.9f) else NuvioTheme.colors.Secondary
                            )
                        }

                        val now = System.currentTimeMillis()
                        val prog = nowProg.progress(now)
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { prog },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(CircleShape),
                            color = if (isLightAccent) Color.White else NuvioTheme.colors.Secondary,
                            trackColor = Color.White.copy(alpha = 0.12f)
                        )

                        if (!nowProg.description.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = nowProg.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.85f),
                                maxLines = if (isEpgCardFocused) 14 else 4,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 12.sp
                            )
                        }

                        val nextProg = epg.nextProgram
                        if (nextProg != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.tv_channels_up_next_format, nextProg.title, nextProg.formattedTimeRange),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            // Descrição geral do canal (se houver e não for redundante)
            if (!channel.description.isNullOrBlank() && (nowProg == null || channel.description != nowProg.description)) {
                var isDescFocused by remember { mutableStateOf(false) }
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isDescFocused) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.04f))
                        .border(
                            width = if (isDescFocused) 2.dp else 1.dp,
                            color = if (isDescFocused) NuvioTheme.colors.FocusRing else Color.White.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .onFocusChanged { isDescFocused = it.isFocused }
                        .focusable()
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                when (keyEvent.nativeKeyEvent.keyCode) {
                                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                                        if (scrollState.canScrollForward) {
                                            coroutineScope.launch {
                                                scrollState.animateScrollTo((scrollState.value + 250).coerceAtMost(scrollState.maxValue))
                                            }
                                            return@onPreviewKeyEvent true
                                        }
                                    }
                                    KeyEvent.KEYCODE_DPAD_UP -> {
                                        if (scrollState.canScrollBackward && scrollState.value > 10) {
                                            coroutineScope.launch {
                                                scrollState.animateScrollTo((scrollState.value - 250).coerceAtLeast(0))
                                            }
                                            return@onPreviewKeyEvent true
                                        }
                                    }
                                }
                            }
                            false
                        }
                        .padding(12.dp)
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.tv_channels_about_channel),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = channel.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f),
                            maxLines = if (isDescFocused) 14 else 4,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}
