package com.nuvio.tv.ui.screens.tvchannels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.core.player.TvChannelPreviewPlayerPool
import com.nuvio.tv.data.repository.TvChannelsRepository
import com.nuvio.tv.domain.model.TvChannelItem
import com.nuvio.tv.domain.model.TvChannelsUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TvChannelsViewModel @Inject constructor(
    private val repository: TvChannelsRepository,
    val playerPool: TvChannelPreviewPlayerPool
) : ViewModel() {

    val uiState: StateFlow<TvChannelsUiState> = repository.uiState

    private var focusDebounceJob: Job? = null

    init {
        repository.initialize()
    }

    /**
     * Ao focar no canal com o controle remoto D-pad:
     * - Se autoplay estiver desativado (padrão): atualiza apenas metadados sem requisitar streams
     * - Se autoplay estiver ativado: aguarda debounce de 450ms e inicia reprodução automática
     */
    fun onChannelFocused(channel: TvChannelItem) {
        if (uiState.value.previewChannel?.stableKey() == channel.stableKey()) return
        focusDebounceJob?.cancel()

        val isAutoplay = uiState.value.channelAutoplay
        if (!isAutoplay) {
            repository.selectChannelMetadata(channel)
            playerPool.stop()
        } else {
            focusDebounceJob = viewModelScope.launch {
                delay(450L)
                repository.selectChannelForPreview(channel, startPlayback = true)
            }
        }
    }

    /**
     * Ao clicar no canal:
     * - Com autoplay desativado: 1º clique inicia a prévia no split-screen; após ativo, clique abre tela cheia
     * - Com autoplay ativado: clique abre tela cheia diretamente
     */
    fun onChannelClicked(channel: TvChannelItem, openFullscreen: Boolean = false) {
        focusDebounceJob?.cancel()
        val currentUiState = uiState.value
        val isAutoplay = currentUiState.channelAutoplay
        val isSameChannel = currentUiState.previewChannel?.stableKey() == channel.stableKey()
        val isCurrentlyPlaying = isSameChannel && currentUiState.isPreviewPlaybackActive && currentUiState.previewStreams.isNotEmpty()

        if (currentUiState.isFullscreen || openFullscreen) {
            // Em tela cheia ou solicitado para abrir em tela cheia (ex: gaveta de zapping ou grade EPG):
            // Inicia imediatamente a reprodução do canal selecionado em tela cheia
            if (!isSameChannel || !isCurrentlyPlaying) {
                repository.selectChannelForPreview(channel, startPlayback = true)
            }
            repository.setFullscreen(true)
            return
        }

        if (!isAutoplay) {
            if (!isCurrentlyPlaying) {
                // 1º Clique obrigatório: inicia a prévia no painel split-screen
                repository.selectChannelForPreview(channel, startPlayback = true)
            } else {
                // Já ativo: clique subsequente abre tela cheia
                repository.setFullscreen(true)
            }
        } else {
            // Com autoplay ativo no split-screen:
            if (!isSameChannel || currentUiState.previewStreams.isEmpty()) {
                repository.selectChannelForPreview(channel, startPlayback = true)
            }
            repository.setFullscreen(true)
        }
    }

    fun startChannelPreviewPlayback(channel: TvChannelItem) {
        repository.selectChannelForPreview(channel, startPlayback = true)
    }

    fun setHideAdultChannels(hide: Boolean) {
        repository.setHideAdultChannels(hide)
    }

    fun setChannelAutoplay(enabled: Boolean) {
        repository.setChannelAutoplay(enabled)
    }

    fun toggleFavorite(channel: TvChannelItem) {
        repository.toggleFavorite(channel)
    }

    fun selectCategory(category: String) {
        repository.setCategory(category)
    }

    fun setSearchQuery(query: String) {
        repository.setSearchQuery(query)
    }

    fun toggleAddonSelection(manifestUrl: String) {
        repository.toggleAddonSelection(manifestUrl)
    }

    fun selectAllAddons() {
        repository.selectAllAddons()
    }

    fun deselectAllAddons() {
        repository.deselectAllAddons()
    }

    fun setAddonsDialogVisible(visible: Boolean) {
        repository.setAddonsDialogVisible(visible)
    }

    fun setScheduleDialogVisible(visible: Boolean) {
        repository.setScheduleDialogVisible(visible)
    }

    fun setFullscreen(fullscreen: Boolean) {
        repository.setFullscreen(fullscreen)
    }

    fun selectNextChannel() {
        repository.selectNextChannel()
    }

    fun selectPreviousChannel() {
        repository.selectPreviousChannel()
    }

    fun selectStreamIndex(index: Int) {
        repository.selectStreamIndex(index)
    }

    fun refresh() {
        repository.refresh()
    }

    override fun onCleared() {
        super.onCleared()
        focusDebounceJob?.cancel()
        playerPool.stop()
    }
}
