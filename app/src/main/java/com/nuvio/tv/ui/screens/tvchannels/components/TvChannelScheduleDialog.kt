package com.nuvio.tv.ui.screens.tvchannels.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.nuvio.tv.R
import com.nuvio.tv.domain.model.TvChannelItem
import com.nuvio.tv.ui.components.NuvioDialog
import com.nuvio.tv.ui.theme.NuvioTheme

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvChannelScheduleDialog(
    visible: Boolean,
    channel: TvChannelItem?,
    onOpenFullGuide: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    if (!visible || channel == null) return

    val programs = channel.epgInfo?.todayPrograms.orEmpty()
    val now = System.currentTimeMillis()

    NuvioDialog(
        onDismiss = onDismiss,
        title = "${channel.name} • Guia",
        subtitle = "Programação completa para este canal",
        width = 580.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            if (programs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Rounded.Schedule,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.3f),
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.tv_channels_no_schedule),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(programs, key = { it.id }) { program ->
                        val isLive = program.isLive(now)
                        val shape = RoundedCornerShape(8.dp)

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(shape)
                                .background(
                                    if (isLive) NuvioTheme.colors.Secondary.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.04f)
                                )
                                .border(
                                    1.dp,
                                    if (isLive) NuvioTheme.colors.Secondary.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.06f),
                                    shape
                                )
                                .padding(12.dp)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        if (isLive) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(3.dp))
                                                    .background(Color(0xFFE50914))
                                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                                            ) {
                                                Text(
                                                    text = "NO AR",
                                                    color = Color.White,
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                        Text(
                                            text = program.title,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = if (isLive) FontWeight.Bold else FontWeight.Medium,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Text(
                                        text = program.formattedTimeRange,
                                        fontSize = 11.sp,
                                        color = if (isLive) NuvioTheme.colors.Secondary else Color.White.copy(alpha = 0.55f),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                if (isLive) {
                                    val prog = program.progress(now)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    LinearProgressIndicator(
                                        progress = { prog },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(2.5.dp)
                                            .clip(CircleShape),
                                        color = NuvioTheme.colors.Secondary,
                                        trackColor = Color.White.copy(alpha = 0.1f)
                                    )
                                }

                                if (!program.description.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = program.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.6f),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (onOpenFullGuide != null) {
                    Button(
                        onClick = onOpenFullGuide,
                        shape = ButtonDefaults.shape(shape = RoundedCornerShape(8.dp)),
                        colors = ButtonDefaults.colors(
                            containerColor = NuvioTheme.colors.Secondary,
                            focusedContainerColor = Color.White,
                            focusedContentColor = Color.Black
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Abrir Grade EPG Completa",
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }

                Button(
                    onClick = onDismiss,
                    shape = ButtonDefaults.shape(shape = RoundedCornerShape(8.dp)),
                    colors = ButtonDefaults.colors(
                        containerColor = if (onOpenFullGuide != null) Color.White.copy(alpha = 0.12f) else NuvioTheme.colors.Secondary,
                        focusedContainerColor = Color.White,
                        focusedContentColor = Color.Black
                    ),
                    modifier = if (onOpenFullGuide != null) Modifier.weight(0.6f) else Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.tv_channels_done),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
