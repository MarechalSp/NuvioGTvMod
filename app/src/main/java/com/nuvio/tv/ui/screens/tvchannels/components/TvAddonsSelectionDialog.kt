package com.nuvio.tv.ui.screens.tvchannels.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.nuvio.tv.domain.model.TvAddonFilterOption
import com.nuvio.tv.ui.components.NuvioDialog
import com.nuvio.tv.ui.theme.NuvioTheme

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvAddonsSelectionDialog(
    visible: Boolean,
    addons: List<TvAddonFilterOption>,
    onToggleAddon: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible) return

    NuvioDialog(
        onDismiss = onDismiss,
        title = stringResource(R.string.tv_channels_manage_addons_modal_title),
        subtitle = stringResource(R.string.tv_channels_manage_addons_modal_subtitle),
        width = 540.dp,
        contentPadding = 16.dp,
        contentSpacing = 10.dp
    ) {
        val isLightAccent = NuvioTheme.colors.Secondary.luminance() > 0.45f
        val onSecondaryColor = if (isLightAccent) Color.Black else Color.White

        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Ações Rápidas: Selecionar Todos / Desmarcar Todos
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onSelectAll,
                    shape = ButtonDefaults.shape(shape = RoundedCornerShape(8.dp)),
                    colors = ButtonDefaults.colors(
                        containerColor = Color.White.copy(alpha = 0.10f),
                        focusedContainerColor = Color.White,
                        focusedContentColor = Color.Black
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = stringResource(R.string.tv_channels_select_all),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Button(
                    onClick = onClearAll,
                    shape = ButtonDefaults.shape(shape = RoundedCornerShape(8.dp)),
                    colors = ButtonDefaults.colors(
                        containerColor = Color.White.copy(alpha = 0.10f),
                        focusedContainerColor = Color.White,
                        focusedContentColor = Color.Black
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = stringResource(R.string.tv_channels_clear_all),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Lista de Addons
            if (addons.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.tv_channels_no_addons_installed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(addons, key = { it.manifestUrl }) { addon ->
                        val shape = RoundedCornerShape(8.dp)
                        Surface(
                            onClick = { onToggleAddon(addon.manifestUrl) },
                            shape = ClickableSurfaceDefaults.shape(shape),
                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = if (addon.isSelected) {
                                    NuvioTheme.colors.Secondary.copy(alpha = 0.16f)
                                } else {
                                    Color.White.copy(alpha = 0.05f)
                                },
                                focusedContainerColor = NuvioTheme.colors.Secondary.copy(alpha = 0.35f)
                            ),
                            border = ClickableSurfaceDefaults.border(
                                focusedBorder = Border(
                                    border = BorderStroke(2.dp, NuvioTheme.colors.Secondary),
                                    shape = shape
                                ),
                                border = Border(
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                                    shape = shape
                                )
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Logo
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF222226)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!addon.logoUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = addon.logoUrl,
                                            contentDescription = addon.addonName,
                                            modifier = Modifier.fillMaxSize().padding(2.dp),
                                            contentScale = ContentScale.Fit
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Rounded.Extension,
                                            contentDescription = null,
                                            tint = Color.White.copy(alpha = 0.5f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = addon.addonName,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = stringResource(R.string.tv_channels_catalog_count, addon.catalogCount),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.45f),
                                        fontSize = 11.sp
                                    )
                                }

                                // Indicador de Seleção
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(
                                            if (addon.isSelected) NuvioTheme.colors.Secondary else Color.White.copy(alpha = 0.15f)
                                        )
                                        .border(
                                            1.dp,
                                            if (addon.isSelected) NuvioTheme.colors.Secondary else Color.White.copy(alpha = 0.3f),
                                            RoundedCornerShape(4.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (addon.isSelected) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = null,
                                            tint = onSecondaryColor,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            var isOkFocused by remember { mutableStateOf(false) }

            // Botão Concluir com tamanho fixo, texto centralizado e contraste dinâmico
            Button(
                onClick = onDismiss,
                shape = ButtonDefaults.shape(shape = RoundedCornerShape(8.dp)),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                colors = ButtonDefaults.colors(
                    containerColor = NuvioTheme.colors.Secondary,
                    contentColor = onSecondaryColor,
                    focusedContainerColor = Color.White,
                    focusedContentColor = Color.Black
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .onFocusChanged { isOkFocused = it.isFocused }
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.tv_channels_done),
                        color = if (isOkFocused) Color.Black else onSecondaryColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
