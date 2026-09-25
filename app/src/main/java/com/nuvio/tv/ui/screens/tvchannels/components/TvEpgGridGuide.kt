@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.tvchannels.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Star
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.nuvio.tv.R
import com.nuvio.tv.data.repository.epg.EpgProgram
import com.nuvio.tv.domain.model.TvChannelItem
import com.nuvio.tv.ui.theme.NuvioTheme
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.max

private const val SLOT_DURATION_MINUTES = 30
private val SLOT_WIDTH_DP = 160.dp
private val PIXELS_PER_MINUTE_DP = SLOT_WIDTH_DP / SLOT_DURATION_MINUTES.toFloat()
private val CHANNEL_COL_WIDTH = 200.dp
private val RULER_HEIGHT = 42.dp
private val ROW_HEIGHT = 68.dp

@Composable
fun TvEpgGridGuide(
    channels: List<TvChannelItem>,
    selectedChannel: TvChannelItem?,
    onSelectChannel: (TvChannelItem, Boolean) -> Unit,
    onBackToList: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentTimeMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Atualiza o relógio a cada 30 segundos
    LaunchedEffect(Unit) {
        while (true) {
            delay(30000L)
            currentTimeMs = System.currentTimeMillis()
        }
    }

    // Calcula o início da janela da grade (arredondado para a meia hora anterior)
    val slotMs = SLOT_DURATION_MINUTES * 60 * 1000L
    val windowStartMs = remember(currentTimeMs) {
        // Começa 30 minutos antes do horário atual para contexto imediato
        val currentSlotStart = currentTimeMs - (currentTimeMs % slotMs)
        currentSlotStart - slotMs
    }
    val windowHours = 5
    val windowEndMs = remember(windowStartMs) {
        windowStartMs + (windowHours * 60 * 60 * 1000L)
    }

    val totalSlots = (windowHours * 60 / SLOT_DURATION_MINUTES) + 1
    val timeSlots = remember(windowStartMs) {
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault()).apply {
            timeZone = TimeZone.getDefault()
        }
        (0 until totalSlots).map { i ->
            val slotTime = windowStartMs + (i * slotMs)
            timeFormat.format(Date(slotTime)) to slotTime
        }
    }

    // Estado compartilhado de rolagem horizontal para régua e canais
    val horizontalScrollState = rememberScrollState()
    val verticalLazyListState = rememberLazyListState()

    // Programa atualmente em foco para exibição detalhada no topo
    var focusedChannel by remember { mutableStateOf(selectedChannel ?: channels.firstOrNull()) }
    var focusedProgram by remember { mutableStateOf<EpgProgram?>(null) }

    LaunchedEffect(channels) {
        if (focusedChannel == null && channels.isNotEmpty()) {
            focusedChannel = channels.first()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NuvioTheme.colors.Background)
    ) {
        // 1. Cabeçalho Superior com Sinopse do Programa em Foco e Ações
        EpgTopSynopsisBanner(
            channel = focusedChannel,
            program = focusedProgram,
            currentTimeMs = currentTimeMs,
            onBackToList = onBackToList,
            onPlayChannel = {
                focusedChannel?.let { onSelectChannel(it, true) }
            }
        )

        Spacer(modifier = Modifier.height(6.dp))

        // 2. Régua de Horários Superior (Sincronizada Horizontalmente)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(RULER_HEIGHT)
                .background(Color.Black.copy(alpha = 0.45f))
                .border(
                    BorderStroke(
                        0.5.dp,
                        Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = 0.15f), Color.Transparent)
                        )
                    )
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Coluna fixa de Canais (Cabeçalho)
            Box(
                modifier = Modifier
                    .width(CHANNEL_COL_WIDTH)
                    .fillMaxHeight()
                    .background(Color(0xFF14171E))
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LiveTv,
                        contentDescription = null,
                        tint = NuvioTheme.colors.Secondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "CANAL (${channels.size})",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.85f),
                        letterSpacing = 1.1.sp
                    )
                }
            }

            // Régua de Slots de Tempo
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .horizontalScroll(horizontalScrollState),
                verticalAlignment = Alignment.CenterVertically
            ) {
                timeSlots.forEach { (slotLabel, slotTime) ->
                    val isCurrentSlot = currentTimeMs in slotTime until (slotTime + slotMs)
                    Box(
                        modifier = Modifier
                            .width(SLOT_WIDTH_DP)
                            .fillMaxHeight()
                            .border(
                                width = 0.5.dp,
                                color = Color.White.copy(alpha = 0.08f)
                            )
                            .background(
                                if (isCurrentSlot) NuvioTheme.colors.Secondary.copy(alpha = 0.18f)
                                else Color.Transparent
                            )
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (isCurrentSlot) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFE50914))
                                )
                            }
                            Text(
                                text = slotLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isCurrentSlot) FontWeight.Bold else FontWeight.Medium,
                                color = if (isCurrentSlot) Color.White else Color.White.copy(alpha = 0.65f)
                            )
                        }
                    }
                }
            }
        }

        // 3. Grade Principal de Canais e Programas
        LazyColumn(
            state = verticalLazyListState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            itemsIndexed(
                items = channels,
                key = { _, channel -> channel.stableKey() }
            ) { index, channel ->
                EpgChannelRow(
                    channel = channel,
                    channelNumber = index + 1,
                    windowStartMs = windowStartMs,
                    windowEndMs = windowEndMs,
                    currentTimeMs = currentTimeMs,
                    horizontalScrollState = horizontalScrollState,
                    isChannelPlaying = selectedChannel?.stableKey() == channel.stableKey(),
                    onChannelFocused = {
                        focusedChannel = channel
                        val nowProg = channel.epgInfo?.nowProgram
                        focusedProgram = nowProg
                    },
                    onProgramFocused = { program ->
                        focusedChannel = channel
                        focusedProgram = program
                    },
                    onSelectChannel = {
                        onSelectChannel(channel, true)
                    }
                )
            }
        }
    }
}

/**
 * Painel Superior que mostra a sinopse detalhada do programa e canal atualmente em foco
 */
@Composable
private fun EpgTopSynopsisBanner(
    channel: TvChannelItem?,
    program: EpgProgram?,
    currentTimeMs: Long,
    onBackToList: () -> Unit,
    onPlayChannel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(130.dp)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF161B26),
                        Color(0xFF0F1218)
                    )
                )
            )
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Informações do Programa e Canal
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Logo do Canal em Destaque
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (!channel?.logo.isNullOrBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(channel?.logo)
                                .crossfade(true)
                                .build(),
                            contentDescription = channel?.name,
                            modifier = Modifier
                                .size(58.dp)
                                .clip(RoundedCornerShape(6.dp)),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Tv,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.4f),
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                // Detalhes Textuais
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = channel?.name ?: stringResource(R.string.tv_channels_select_channel),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (channel?.isFavorite == true) {
                            Icon(
                                imageVector = Icons.Rounded.Star,
                                contentDescription = stringResource(R.string.tv_channels_favorited_button),
                                tint = Color(0xFFFFD700),
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        if (!channel?.catalogName.isNullOrBlank()) {
                            Text(
                                text = "• ${channel?.catalogName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.5f)
                            )
                        }
                    }

                    // Título do Programa
                    val defaultTitle = stringResource(R.string.tv_channels_continuous_live_broadcast)
                    val progTitle = program?.title ?: channel?.epgInfo?.nowProgram?.title ?: defaultTitle
                    val isLive = program?.isLive(currentTimeMs) ?: true

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isLive) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Color(0xFFE50914))
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.tv_channels_on_air_badge),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        Text(
                            text = progTitle,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = NuvioTheme.colors.Secondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        program?.formattedTimeRange?.let { range ->
                            Text(
                                text = "($range)",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.65f)
                            )
                        }
                    }

                    // Sinopse / Descrição
                    val defaultDesc = stringResource(R.string.tv_channels_no_synopsis_available)
                    val description = program?.description ?: channel?.description ?: defaultDesc
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.70f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Botões de Ação na Direita
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Assistir Agora
                Button(
                    onClick = onPlayChannel,
                    shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                    colors = ButtonDefaults.colors(
                        containerColor = NuvioTheme.colors.Secondary,
                        focusedContainerColor = Color.White,
                        focusedContentColor = Color.Black
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = stringResource(R.string.tv_channels_watch_channel),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }

                // Voltar para Lista
                Button(
                    onClick = onBackToList,
                    shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                    colors = ButtonDefaults.colors(
                        containerColor = Color.White.copy(alpha = 0.12f),
                        focusedContainerColor = Color.White,
                        focusedContentColor = Color.Black
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ArrowBack,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = stringResource(R.string.tv_channels_mode_list),
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Linha de cada Canal na Grade: Coluna fixa na esquerda com o Canal e Linha Horizontal com Programas
 */
@Composable
private fun EpgChannelRow(
    channel: TvChannelItem,
    channelNumber: Int,
    windowStartMs: Long,
    windowEndMs: Long,
    currentTimeMs: Long,
    horizontalScrollState: androidx.compose.foundation.ScrollState,
    isChannelPlaying: Boolean,
    onChannelFocused: () -> Unit,
    onProgramFocused: (EpgProgram) -> Unit,
    onSelectChannel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isChannelHeaderFocused by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT)
            .border(
                width = 0.5.dp,
                color = Color.White.copy(alpha = 0.05f)
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Coluna Fixa do Canal (Logo, Número, Nome)
        Card(
            onClick = onSelectChannel,
            modifier = Modifier
                .width(CHANNEL_COL_WIDTH)
                .fillMaxHeight()
                .onFocusChanged { state ->
                    isChannelHeaderFocused = state.isFocused
                    if (state.isFocused) onChannelFocused()
                },
            shape = CardDefaults.shape(RoundedCornerShape(0.dp)),
            colors = CardDefaults.colors(
                containerColor = if (isChannelPlaying) Color(0xFF1C2230) else Color(0xFF14171E),
                focusedContainerColor = NuvioTheme.colors.Secondary.copy(alpha = 0.9f)
            ),
            border = CardDefaults.border(
                focusedBorder = Border(
                    border = BorderStroke(2.dp, Color.White),
                    shape = RoundedCornerShape(0.dp)
                )
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Número do Canal
                Text(
                    text = String.format("%02d", channelNumber),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isChannelHeaderFocused) Color.White else Color.White.copy(alpha = 0.45f)
                )

                // Logo do Canal
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White.copy(alpha = 0.06f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (!channel.logo.isNullOrBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(channel.logo)
                                .crossfade(true)
                                .build(),
                            contentDescription = channel.name,
                            modifier = Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Tv,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.4f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Nome do Canal
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = channel.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isChannelPlaying) {
                        Text(
                            text = stringResource(R.string.tv_channels_on_air_badge),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF4CAF50)
                        )
                    }
                }
            }
        }

        // 2. Linha Horizontal de Programas (Sincronizada com o Scroll da Régua)
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .horizontalScroll(horizontalScrollState),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val programs = remember(channel.epgInfo, windowStartMs, windowEndMs) {
                channel.epgInfo?.programsForWindow(windowStartMs, windowEndMs).orEmpty()
            }

            if (programs.isEmpty()) {
                // Bloco único indicando ausência de dados EPG
                val totalWidth = SLOT_WIDTH_DP * 10
                EpgEmptySlot(
                    title = stringResource(R.string.tv_channels_continuous_live_broadcast),
                    width = totalWidth,
                    onClick = onSelectChannel,
                    onFocused = onChannelFocused
                )
            } else {
                programs.forEach { program ->
                    val isLive = program.isLive(currentTimeMs)
                    val progDurationMin = program.durationMinutes
                    val widthDp = (progDurationMin * PIXELS_PER_MINUTE_DP.value).dp.coerceAtLeast(80.dp)

                    EpgProgramBlock(
                        program = program,
                        width = widthDp,
                        isLive = isLive,
                        currentTimeMs = currentTimeMs,
                        onClick = onSelectChannel,
                        onFocused = {
                            onProgramFocused(program)
                        }
                    )
                }
            }
        }
    }
}

/**
 * Bloco individual de cada Programa na Grade
 */
@Composable
private fun EpgProgramBlock(
    program: EpgProgram,
    width: androidx.compose.ui.unit.Dp,
    isLive: Boolean,
    currentTimeMs: Long,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        modifier = modifier
            .width(width)
            .fillMaxHeight()
            .padding(1.dp)
            .onFocusChanged { state ->
                isFocused = state.isFocused
                if (state.isFocused) onFocused()
            },
        shape = CardDefaults.shape(RoundedCornerShape(4.dp)),
        colors = CardDefaults.colors(
            containerColor = when {
                isLive -> Color(0xFF1E2838)
                else -> Color(0xFF12151C)
            },
            focusedContainerColor = NuvioTheme.colors.Secondary
        ),
        border = CardDefaults.border(
            border = Border(
                border = BorderStroke(
                    0.5.dp,
                    if (isLive) Color(0xFF388E3C).copy(alpha = 0.5f) else Color.White.copy(alpha = 0.08f)
                ),
                shape = RoundedCornerShape(4.dp)
            ),
            focusedBorder = Border(
                border = BorderStroke(2.dp, Color.White),
                shape = RoundedCornerShape(4.dp)
            )
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Título e Horário
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (isLive) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFE50914))
                            )
                        }
                        Text(
                            text = program.formattedTimeRange,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isFocused) Color.White else Color.White.copy(alpha = 0.65f),
                            fontSize = 10.sp,
                            maxLines = 1
                        )
                    }

                    Text(
                        text = program.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isLive || isFocused) FontWeight.Bold else FontWeight.Medium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Categoria / Duração
                if (!program.category.isNullOrBlank()) {
                    Text(
                        text = program.category,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isFocused) Color.White.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.4f),
                        fontSize = 9.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Barra de Progresso no rodapé se o programa estiver no ar
            if (isLive) {
                val progress = program.progress(currentTimeMs)
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .align(Alignment.BottomCenter),
                    color = Color(0xFFE50914),
                    trackColor = Color.White.copy(alpha = 0.15f)
                )
            }
        }
    }
}

/**
 * Bloco para canais sem eventos individuais no EPG
 */
@Composable
private fun EpgEmptySlot(
    title: String,
    width: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        modifier = modifier
            .width(width)
            .fillMaxHeight()
            .padding(1.dp)
            .onFocusChanged { state ->
                isFocused = state.isFocused
                if (state.isFocused) onFocused()
            },
        shape = CardDefaults.shape(RoundedCornerShape(4.dp)),
        colors = CardDefaults.colors(
            containerColor = Color(0xFF101217),
            focusedContainerColor = NuvioTheme.colors.Secondary
        ),
        border = CardDefaults.border(
            border = Border(
                border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.05f)),
                shape = RoundedCornerShape(4.dp)
            ),
            focusedBorder = Border(
                border = BorderStroke(2.dp, Color.White),
                shape = RoundedCornerShape(4.dp)
            )
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Schedule,
                    contentDescription = null,
                    tint = if (isFocused) Color.White else Color.White.copy(alpha = 0.35f),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isFocused) Color.White else Color.White.copy(alpha = 0.45f)
                )
            }
        }
    }
}
