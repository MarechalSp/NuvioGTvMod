@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.nuvio.tv.R
import com.nuvio.tv.data.repository.epg.TvEpgRepository
import com.nuvio.tv.ui.theme.NuvioTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
internal fun TvChannelsSettingsPane(
    initialFocusRequester: FocusRequester?,
    viewModel: TvChannelsSettingsViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val channelsState by viewModel.channelsUiState.collectAsStateWithLifecycle()
    val epgState by viewModel.epgUiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val neverSyncedText = stringResource(R.string.settings_tv_channels_never_synced)
    val lastSyncText = if (epgState.lastSyncEpochMs > 0) {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).apply {
            timeZone = TimeZone.getDefault()
        }
        sdf.format(Date(epgState.lastSyncEpochMs))
    } else {
        neverSyncedText
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(end = NuvioTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.lg)
    ) {
        // Cabeçalho da Seção
        item {
            SettingsDetailHeader(
                title = stringResource(R.string.settings_tv_channels),
                subtitle = stringResource(R.string.settings_tv_channels_subtitle)
            )
        }

        // Grupo 1: Addons de Canais de TV
        item {
            SettingsGroupCard(
                title = stringResource(R.string.settings_tv_channels_addons),
                subtitle = stringResource(R.string.settings_tv_channels_addons_subtitle)
            ) {
                if (channelsState.availableAddons.isEmpty()) {
                    SettingsActionRow(
                        title = stringResource(R.string.settings_tv_channels_no_addons),
                        subtitle = stringResource(R.string.settings_tv_channels_no_addons_subtitle),
                        onClick = {},
                        enabled = false,
                        modifier = if (initialFocusRequester != null) {
                            Modifier.focusRequester(initialFocusRequester)
                        } else {
                            Modifier
                        }
                    )
                } else {
                    channelsState.availableAddons.forEachIndexed { index, addon ->
                        val isFirst = index == 0
                        SettingsToggleRow(
                            title = addon.addonName,
                            subtitle = if (addon.catalogCount > 0) {
                                stringResource(R.string.settings_tv_channels_catalogs_available, addon.catalogCount)
                            } else addon.manifestUrl,
                            checked = addon.isSelected,
                            onToggle = {
                                viewModel.toggleAddonSelection(addon.manifestUrl)
                            },
                            modifier = if (isFirst && initialFocusRequester != null) {
                                Modifier.focusRequester(initialFocusRequester)
                            } else {
                                Modifier
                            }
                        )
                    }

                    val allAddonsEnabledToast = stringResource(R.string.settings_tv_channels_toast_all_addons_enabled)
                    SettingsActionRow(
                        title = stringResource(R.string.tv_channels_select_all),
                        subtitle = stringResource(R.string.settings_tv_channels_activate_all_addons_sub),
                        leadingIcon = Icons.Default.CheckCircle,
                        onClick = {
                            viewModel.selectAllAddons()
                            Toast.makeText(context, allAddonsEnabledToast, Toast.LENGTH_SHORT).show()
                        }
                    )

                    val addonsDisabledToast = stringResource(R.string.settings_tv_channels_toast_addons_disabled)
                    SettingsActionRow(
                        title = stringResource(R.string.tv_channels_clear_all),
                        subtitle = stringResource(R.string.settings_tv_channels_deactivate_all_addons_sub),
                        leadingIcon = Icons.Default.Extension,
                        onClick = {
                            viewModel.deselectAllAddons()
                            Toast.makeText(context, addonsDisabledToast, Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }

        // Grupo 2: Reprodução e Controle Parental
        item {
            SettingsGroupCard(
                title = stringResource(R.string.settings_tv_channels_playback_parental),
                subtitle = stringResource(R.string.settings_tv_channels_playback_parental_sub)
            ) {
                val adultHiddenMsg = stringResource(R.string.settings_tv_channels_toast_adult_hidden)
                val adultVisibleMsg = stringResource(R.string.settings_tv_channels_toast_adult_visible)
                SettingsToggleRow(
                    title = stringResource(R.string.settings_tv_channels_hide_adult),
                    subtitle = stringResource(R.string.settings_tv_channels_hide_adult_sub),
                    checked = channelsState.hideAdultChannels,
                    onToggle = {
                        val newValue = !channelsState.hideAdultChannels
                        viewModel.setHideAdultChannels(newValue)
                        Toast.makeText(
                            context,
                            if (newValue) adultHiddenMsg else adultVisibleMsg,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )

                val autoplayEnabledMsg = stringResource(R.string.settings_tv_channels_toast_autoplay_enabled)
                val autoplayDisabledMsg = stringResource(R.string.settings_tv_channels_toast_autoplay_disabled)
                SettingsToggleRow(
                    title = stringResource(R.string.settings_tv_channels_autoplay),
                    subtitle = stringResource(R.string.settings_tv_channels_autoplay_sub),
                    checked = channelsState.channelAutoplay,
                    onToggle = {
                        val newValue = !channelsState.channelAutoplay
                        viewModel.setChannelAutoplay(newValue)
                        Toast.makeText(
                            context,
                            if (newValue) autoplayEnabledMsg else autoplayDisabledMsg,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            }
        }

        // Grupo 3: Guia de Programação (EPG)
        item {
            SettingsGroupCard(
                title = stringResource(R.string.settings_tv_channels_epg),
                subtitle = stringResource(R.string.settings_tv_channels_epg_subtitle)
            ) {
                // Status da Sincronização
                val lastSyncFormatted = stringResource(R.string.settings_tv_channels_last_sync, lastSyncText)
                val syncingText = stringResource(R.string.settings_tv_channels_syncing)
                val programsCountText = stringResource(R.string.settings_tv_channels_programs_count, epgState.totalProgramsLoaded)
                SettingsActionRow(
                    title = stringResource(R.string.settings_tv_channels_epg_status),
                    subtitle = lastSyncFormatted,
                    value = if (epgState.isLoading) syncingText else programsCountText,
                    leadingIcon = Icons.Default.Schedule,
                    onClick = {}
                )

                // Botão Sincronizar Agora
                val syncDownloadingText = stringResource(R.string.settings_tv_channels_sync_downloading)
                val syncSubText = stringResource(R.string.settings_tv_channels_sync_sub)
                val syncStartedToast = stringResource(R.string.settings_tv_channels_toast_sync_started)
                SettingsActionRow(
                    title = stringResource(R.string.settings_tv_channels_sync_now),
                    subtitle = if (epgState.isLoading) syncDownloadingText else syncSubText,
                    leadingIcon = Icons.Default.Sync,
                    enabled = !epgState.isLoading,
                    onClick = {
                        viewModel.syncEpgNow()
                        Toast.makeText(context, syncStartedToast, Toast.LENGTH_SHORT).show()
                    }
                )

                // Fontes de XMLTV com interruptores para ativar/desativar
                epgState.epgSources.forEach { source ->
                    SettingsToggleRow(
                        title = source.name,
                        subtitle = source.url,
                        checked = source.isEnabled,
                        onToggle = {
                            viewModel.toggleEpgSource(source.id)
                        }
                    )
                }

                val allEpgEnabledToast = stringResource(R.string.settings_tv_channels_toast_all_epg_enabled)
                SettingsActionRow(
                    title = stringResource(R.string.settings_tv_channels_enable_all_epg),
                    subtitle = stringResource(R.string.settings_tv_channels_enable_all_epg_sub),
                    leadingIcon = Icons.Default.CheckCircle,
                    onClick = {
                        viewModel.selectAllEpgSources()
                        Toast.makeText(context, allEpgEnabledToast, Toast.LENGTH_SHORT).show()
                    }
                )

                val allEpgDisabledToast = stringResource(R.string.settings_tv_channels_toast_all_epg_disabled)
                SettingsActionRow(
                    title = stringResource(R.string.settings_tv_channels_disable_all_epg),
                    subtitle = stringResource(R.string.settings_tv_channels_disable_all_epg_sub),
                    leadingIcon = Icons.Default.Extension,
                    onClick = {
                        viewModel.deselectAllEpgSources()
                        Toast.makeText(context, allEpgDisabledToast, Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }

        // Grupo 4: Ações Rápidas e Manutenção
        item {
            SettingsGroupCard(
                title = stringResource(R.string.settings_tv_channels_maintenance),
                subtitle = stringResource(R.string.settings_tv_channels_maintenance_sub)
            ) {
                val reloadingToast = stringResource(R.string.settings_tv_channels_toast_reloading)
                SettingsActionRow(
                    title = stringResource(R.string.settings_tv_channels_reload_all),
                    subtitle = stringResource(R.string.settings_tv_channels_reload_all_subtitle),
                    leadingIcon = Icons.Default.Refresh,
                    onClick = {
                        viewModel.reloadAll()
                        Toast.makeText(context, reloadingToast, Toast.LENGTH_SHORT).show()
                    }
                )

                SettingsActionRow(
                    title = stringResource(R.string.settings_tv_channels_channels_loaded),
                    subtitle = stringResource(R.string.settings_tv_channels_channels_loaded_sub),
                    value = stringResource(R.string.settings_tv_channels_channels_count, channelsState.totalChannelsCount),
                    leadingIcon = Icons.Default.Tv,
                    onClick = {}
                )
            }
        }
    }
}
