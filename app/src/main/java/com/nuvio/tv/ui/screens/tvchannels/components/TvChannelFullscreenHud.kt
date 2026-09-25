package com.nuvio.tv.ui.screens.tvchannels.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
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
import com.nuvio.tv.R
import com.nuvio.tv.core.player.TvChannelPreviewPlayerPool
import com.nuvio.tv.domain.model.AdultChannelDetector
import com.nuvio.tv.domain.model.Stream
import com.nuvio.tv.domain.model.TvChannelItem
import com.nuvio.tv.domain.model.playableTvUrl
import com.nuvio.tv.ui.theme.NuvioTheme
import kotlinx.coroutines.delay

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvChannelFullscreenHud(
    channel: TvChannelItem?,
    streams: List<Stream>,
    selectedStreamIndex: Int,
    playerPool: TvChannelPreviewPlayerPool,
    allChannels: List<TvChannelItem> = emptyList(),
    categories: List<String> = emptyList(),
    hideAdultChannels: Boolean = false,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onSelectChannel: ((TvChannelItem) -> Unit)? = null,
    onExitFullscreen: () -> Unit,
    onNextChannel: () -> Unit,
    onPreviousChannel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var hudVisible by remember { mutableStateOf(true) }
    var isDrawerOpen by remember { mutableStateOf(false) }
    var drawerCategory by remember { mutableStateOf("") }
    var userActivityTick by remember { mutableLongStateOf(0L) }
    val focusRequester = remember { FocusRequester() }

    val activeStream = streams.getOrNull(selectedStreamIndex)
    val playableUrl = activeStream?.playableTvUrl
    val streamHeaders = activeStream?.behaviorHints?.proxyHeaders?.request
    var playbackError by remember(playableUrl) { mutableStateOf<String?>(null) }

    // Captura o botão Voltar do controle
    BackHandler {
        if (isDrawerOpen) {
            isDrawerOpen = false
        } else {
            onExitFullscreen()
        }
    }

    // Auto-oculta o HUD após 3.5 segundos sem interação (se a gaveta não estiver aberta)
    LaunchedEffect(hudVisible, userActivityTick, isDrawerOpen) {
        if (!hudVisible || isDrawerOpen) return@LaunchedEffect
        delay(3500L)
        hudVisible = false
    }

    LaunchedEffect(channel?.stableKey()) {
        hudVisible = true
        userActivityTick++
        playbackError = null
    }

    LaunchedEffect(errorMessage) {
        if (!errorMessage.isNullOrBlank()) {
            playbackError = errorMessage
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (!isDrawerOpen) {
                    hudVisible = true
                    userActivityTick++
                }

                when (event.key) {
                    Key.DirectionLeft -> {
                        if (!isDrawerOpen && allChannels.isNotEmpty()) {
                            isDrawerOpen = true
                            hudVisible = false
                            true
                        } else false
                    }
                    Key.DirectionRight -> {
                        if (!isDrawerOpen && allChannels.isNotEmpty()) {
                            isDrawerOpen = true
                            hudVisible = false
                            true
                        } else false
                    }
                    Key.DirectionUp, Key.PageUp -> {
                        if (!isDrawerOpen) {
                            onPreviousChannel()
                            true
                        } else false
                    }
                    Key.DirectionDown, Key.PageDown -> {
                        if (!isDrawerOpen) {
                            onNextChannel()
                            true
                        } else false
                    }
                    Key.Back, Key.Escape -> {
                        if (isDrawerOpen) {
                            isDrawerOpen = false
                            true
                        } else {
                            onExitFullscreen()
                            true
                        }
                    }
                    Key.DirectionCenter, Key.Enter -> {
                        if (!isDrawerOpen) {
                            hudVisible = !hudVisible
                            true
                        } else false
                    }
                    else -> false
                }
            }
    ) {
        // Player em Tela Cheia
        TvChannelPreviewPlayer(
            streamUrl = playableUrl,
            playerPool = playerPool,
            isPlaying = !playableUrl.isNullOrBlank() && playbackError == null,
            headers = streamHeaders,
            onError = { err ->
                playbackError = err
            },
            modifier = Modifier.fillMaxSize()
        )

        // Overlay de erro com ação rápida para o usuário
        if (playbackError != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.82f))
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Rounded.Tv,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.tv_channels_stream_unavailable_desc),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = playbackError ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = { playbackError = null },
                            shape = ButtonDefaults.shape(shape = RoundedCornerShape(8.dp)),
                            colors = ButtonDefaults.colors(
                                containerColor = NuvioTheme.colors.Secondary,
                                focusedContainerColor = Color.White,
                                focusedContentColor = Color.Black
                            )
                        ) {
                            Text(text = stringResource(R.string.tv_channels_retry), fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { onNextChannel() },
                            shape = ButtonDefaults.shape(shape = RoundedCornerShape(8.dp)),
                            colors = ButtonDefaults.colors(
                                containerColor = Color.White.copy(alpha = 0.15f),
                                focusedContainerColor = Color.White,
                                focusedContentColor = Color.Black
                            )
                        ) {
                            Text(text = stringResource(R.string.tv_channels_next_channel), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Overlay HUD
        AnimatedVisibility(
            visible = hudVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.75f),
                                Color.Transparent,
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.85f)
                            )
                        )
                    )
            ) {
                // Barra Superior
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                        .align(Alignment.TopCenter),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onExitFullscreen,
                        shape = ButtonDefaults.shape(shape = RoundedCornerShape(8.dp)),
                        colors = ButtonDefaults.colors(
                            containerColor = Color.White.copy(alpha = 0.15f),
                            focusedContainerColor = Color.White,
                            focusedContentColor = Color.Black
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(text = stringResource(R.string.tv_channels_back), fontWeight = FontWeight.Bold)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFE50914))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.tv_channels_live_badge),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Barra Inferior (Informações do Canal e EPG)
                if (channel != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp)
                            .align(Alignment.BottomCenter)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            val logoUrl = channel.displayLogo
                            if (!logoUrl.isNullOrBlank()) {
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(RoundedCornerShape(10.dp))
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
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Tv,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(36.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = channel.name,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )

                                val epg = channel.epgInfo
                                val nowProg = epg?.nowProgram
                                if (nowProg != null) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = nowProg.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.White.copy(alpha = 0.9f),
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = "•",
                                            color = Color.White.copy(alpha = 0.5f)
                                        )
                                        Text(
                                            text = nowProg.formattedTimeRange,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = NuvioTheme.colors.Secondary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    val now = System.currentTimeMillis()
                                    val prog = nowProg.progress(now)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    LinearProgressIndicator(
                                        progress = { prog },
                                        modifier = Modifier
                                            .width(260.dp)
                                            .height(3.dp)
                                            .clip(CircleShape),
                                        color = NuvioTheme.colors.Secondary,
                                        trackColor = Color.White.copy(alpha = 0.2f)
                                    )
                                } else {
                                    Text(
                                        text = "${channel.addonName} • ${channel.catalogName}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.6f)
                                    )
                                }
                            }

                            // Dica de navegação por controle
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(alpha = 0.12f))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.tv_channels_hud_hint),
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.75f)
                                )
                            }
                        }
                    }
                }
            }
        }

        val effectiveChannels = remember(allChannels, hideAdultChannels) {
            if (hideAdultChannels) {
                allChannels.filter { !AdultChannelDetector.isAdult(it) }
            } else {
                allChannels
            }
        }
        val effectiveCategories = remember(categories, hideAdultChannels) {
            if (hideAdultChannels) {
                categories.filter { !AdultChannelDetector.isAdultCategory(it) }
            } else {
                categories
            }
        }
        val favoritesLabel = stringResource(R.string.tv_channels_favorites)
        val allLabel = stringResource(R.string.tv_channels_category_all)
        val drawerChannels = remember(effectiveChannels, drawerCategory, favoritesLabel, allLabel) {
            if (drawerCategory.isBlank() || drawerCategory.equals(allLabel, ignoreCase = true) || drawerCategory.equals("todos", ignoreCase = true) || drawerCategory.equals("all", ignoreCase = true)) {
                effectiveChannels
            } else if (drawerCategory.equals(favoritesLabel, ignoreCase = true) || drawerCategory.equals("favoritos", ignoreCase = true) || drawerCategory.equals("favorites", ignoreCase = true)) {
                effectiveChannels.filter { it.isFavorite }
            } else {
                effectiveChannels.filter { ch ->
                    ch.genres.any { g -> g.equals(drawerCategory, ignoreCase = true) } ||
                        ch.catalogName.equals(drawerCategory, ignoreCase = true)
                }
            }
        }

        // Gaveta lateral de zapping ativada ao pressionar seta para o lado
        TvChannelZappingDrawer(
            isOpen = isDrawerOpen,
            currentChannel = channel,
            channels = drawerChannels,
            categories = effectiveCategories,
            selectedCategory = drawerCategory,
            onCategorySelected = { drawerCategory = it },
            onChannelSelected = { ch ->
                playbackError = null
                isDrawerOpen = false
                onSelectChannel?.invoke(ch)
                runCatching { focusRequester.requestFocus() }
            },
            onClose = {
                isDrawerOpen = false
                runCatching { focusRequester.requestFocus() }
            },
            modifier = Modifier.align(Alignment.CenterStart)
        )
    }
}
