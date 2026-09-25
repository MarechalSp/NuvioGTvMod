package com.nuvio.tv.ui.screens.tvchannels.components

import android.view.KeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
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
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.nuvio.tv.R
import com.nuvio.tv.domain.model.TvChannelItem
import com.nuvio.tv.ui.theme.NuvioTheme

/**
 * Gaveta lateral de zapping que se abre sobre o player em tela cheia sem interromper
 * a reprodução do vídeo. Permite trocar de canal instantaneamente com o D-pad.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvChannelZappingDrawer(
    isOpen: Boolean,
    currentChannel: TvChannelItem?,
    channels: List<TvChannelItem>,
    categories: List<String>,
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    onChannelSelected: (TvChannelItem) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val drawerFocusRequester = remember { FocusRequester() }

    LaunchedEffect(isOpen) {
        if (isOpen) {
            drawerFocusRequester.requestFocus()
            val currentIndex = channels.indexOfFirst { it.stableKey() == currentChannel?.stableKey() }
            if (currentIndex >= 0) {
                listState.scrollToItem(maxOf(0, currentIndex - 2))
            }
        }
    }

    AnimatedVisibility(
        visible = isOpen,
        enter = slideInHorizontally(initialOffsetX = { -it }),
        exit = slideOutHorizontally(targetOffsetX = { -it }),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .width(360.dp)
                .fillMaxHeight()
                .background(Color(0xF0101014))
                .border(width = 1.dp, color = Color.White.copy(alpha = 0.12f))
                .focusRequester(drawerFocusRequester)
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        if (event.key == Key.DirectionRight || event.key == Key.Back || event.key == Key.Escape) {
                            onClose()
                            true
                        } else false
                    } else false
                }
                .padding(14.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Cabeçalho da Gaveta
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Tv,
                            contentDescription = null,
                            tint = NuvioTheme.colors.Secondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = stringResource(R.string.tv_channels_drawer_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Text(
                        text = stringResource(R.string.tv_channels_back_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.45f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Barra Horizontal de Categorias
                val allLabel = stringResource(R.string.tv_channels_category_all)
                val allCats = remember(categories, allLabel) { listOf(allLabel) + categories }
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(allCats) { cat ->
                        val isSelected = (cat == allLabel && selectedCategory.isBlank()) ||
                            selectedCategory.equals(cat, ignoreCase = true)
                        var isCatFocused by remember { mutableStateOf(false) }
                        val isLightAccent = NuvioTheme.colors.Secondary.luminance() > 0.45f
                        val selectedTextColor = if (isLightAccent) Color.Black else Color.White
                        val textColor = when {
                            isCatFocused -> Color.Black
                            isSelected -> selectedTextColor
                            else -> Color.White
                        }
                        Button(
                            onClick = {
                                onCategorySelected(if (cat == allLabel) "" else cat)
                            },
                            modifier = Modifier.onFocusChanged { isCatFocused = it.isFocused },
                            shape = ButtonDefaults.shape(shape = RoundedCornerShape(16.dp)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            colors = ButtonDefaults.colors(
                                containerColor = if (isSelected) NuvioTheme.colors.Secondary else Color.White.copy(alpha = 0.08f),
                                contentColor = if (isSelected) selectedTextColor else Color.White,
                                focusedContainerColor = Color.White,
                                focusedContentColor = Color.Black
                            )
                        ) {
                            Text(
                                text = cat,
                                color = textColor,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Lista de Canais Compacta para Zapping Rápido
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(channels, key = { _, it -> it.stableKey() }) { index, ch ->
                        val isCurrent = ch.stableKey() == currentChannel?.stableKey()
                        var isFocused by remember { mutableStateOf(false) }
                        val itemFocusRequester = remember { FocusRequester() }

                        LaunchedEffect(isOpen) {
                            if (isOpen && (isCurrent || (currentChannel == null && index == 0))) {
                                kotlinx.coroutines.delay(120L)
                                runCatching { itemFocusRequester.requestFocus() }
                            }
                        }

                        Surface(
                            onClick = {
                                onChannelSelected(ch)
                                onClose()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(itemFocusRequester)
                                .onFocusChanged { isFocused = it.isFocused }
                                .onPreviewKeyEvent { keyEvent ->
                                    if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                        when (keyEvent.nativeKeyEvent.keyCode) {
                                            KeyEvent.KEYCODE_DPAD_CENTER,
                                            KeyEvent.KEYCODE_ENTER,
                                            KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                                                onChannelSelected(ch)
                                                onClose()
                                                true
                                            }
                                            KeyEvent.KEYCODE_DPAD_RIGHT,
                                            KeyEvent.KEYCODE_BACK -> {
                                                onClose()
                                                true
                                            }
                                            else -> false
                                        }
                                    } else false
                                },
                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = if (isCurrent) NuvioTheme.colors.Secondary.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.05f),
                                focusedContainerColor = Color.White
                            ),
                            border = ClickableSurfaceDefaults.border(
                                border = Border(
                                    border = BorderStroke(
                                        1.dp,
                                        if (isCurrent) NuvioTheme.colors.Secondary.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.08f)
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ),
                                focusedBorder = Border(
                                    border = BorderStroke(2.dp, NuvioTheme.colors.Secondary),
                                    shape = RoundedCornerShape(8.dp)
                                )
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Logo
                                val logoUrl = ch.displayLogo
                                if (!logoUrl.isNullOrBlank()) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color(0xFF1E1E24))
                                            .padding(2.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AsyncImage(
                                            model = logoUrl,
                                            contentDescription = ch.name,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Fit
                                        )
                                    }
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.Tv,
                                        contentDescription = null,
                                        tint = if (isFocused) Color.Black else Color.White.copy(alpha = 0.6f),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                // Nome e Programa Atual
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = ch.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isFocused) Color.Black else Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        if (ch.isFavorite) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(
                                                imageVector = Icons.Rounded.Star,
                                                contentDescription = null,
                                                tint = Color(0xFFFFD700),
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }

                                    val nowProg = ch.epgInfo?.nowProgram
                                    if (nowProg != null) {
                                        Text(
                                            text = nowProg.title,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isFocused) Color.Black.copy(alpha = 0.75f) else Color.White.copy(alpha = 0.55f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            fontSize = 10.sp
                                        )
                                    }
                                }

                                if (isCurrent) {
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = stringResource(R.string.tv_channels_watching),
                                        tint = NuvioTheme.colors.Secondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
