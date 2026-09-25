package com.nuvio.tv.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.data.repository.TvChannelsRepository
import com.nuvio.tv.data.repository.epg.TvEpgRepository
import com.nuvio.tv.data.repository.epg.TvEpgUiState
import com.nuvio.tv.domain.model.TvChannelsUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TvChannelsSettingsViewModel @Inject constructor(
    private val tvChannelsRepository: TvChannelsRepository,
    private val tvEpgRepository: TvEpgRepository
) : ViewModel() {

    val channelsUiState: StateFlow<TvChannelsUiState> = tvChannelsRepository.uiState
    val epgUiState: StateFlow<TvEpgUiState> = tvEpgRepository.uiState

    fun toggleAddonSelection(manifestUrl: String) {
        tvChannelsRepository.toggleAddonSelection(manifestUrl)
    }

    fun selectAllAddons() {
        tvChannelsRepository.selectAllAddons()
    }

    fun deselectAllAddons() {
        tvChannelsRepository.deselectAllAddons()
    }

    fun syncEpgNow() {
        tvEpgRepository.syncEpg(forceRefresh = true)
    }

    fun toggleEpgSource(sourceId: String) {
        tvEpgRepository.toggleEpgSource(sourceId)
    }

    fun selectAllEpgSources() {
        tvEpgRepository.selectAllEpgSources()
    }

    fun deselectAllEpgSources() {
        tvEpgRepository.deselectAllEpgSources()
    }

    fun reloadAll() {
        tvChannelsRepository.refresh()
    }

    fun setHideAdultChannels(hide: Boolean) {
        tvChannelsRepository.setHideAdultChannels(hide)
    }

    fun setChannelAutoplay(enabled: Boolean) {
        tvChannelsRepository.setChannelAutoplay(enabled)
    }
}
