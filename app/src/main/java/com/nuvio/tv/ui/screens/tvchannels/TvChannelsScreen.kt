@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.tvchannels

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.focusGroup
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.BorderStroke
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.domain.model.AdultChannelDetector
import com.nuvio.tv.domain.model.Stream
import com.nuvio.tv.domain.model.TvChannelItem
import com.nuvio.tv.domain.model.playableTvUrl
import com.nuvio.tv.ui.components.LoadingIndicator
import com.nuvio.tv.ui.screens.tvchannels.components.TvAddonsSelectionDialog
import com.nuvio.tv.ui.screens.tvchannels.components.TvChannelFullscreenHud
import com.nuvio.tv.ui.screens.tvchannels.components.TvChannelListItem
import com.nuvio.tv.ui.screens.tvchannels.components.TvChannelPreviewPanel
import com.nuvio.tv.ui.screens.tvchannels.components.TvChannelScheduleDialog
import com.nuvio.tv.ui.screens.tvchannels.components.TvEpgGridGuide
import com.nuvio.tv.ui.theme.NuvioTheme

@Composable
fun TvChannelsScreen(
    viewModel: TvChannelsViewModel = hiltViewModel(),
    onWatchFullscreenInMainPlayer: ((TvChannelItem, Stream) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    // Estado do modo de visualização: Grade EPG vs Lista Split-Screen
    var isEpgGridMode by rememberSaveable { mutableStateOf(false) }

    // Estado da barra de busca de canais
    var searchQuery by rememberSaveable { mutableStateOf(state.searchQuery) }
    var isSearchFocused by remember { mutableStateOf(false) }

    // FocusRequesters para navegação D-pad fluida entre Top Bar, Busca, Categorias e Lista
    val gradeEpgFocusRequester = remember { FocusRequester() }
    val addonsFocusRequester = remember { FocusRequester() }
    val refreshFocusRequester = remember { FocusRequester() }
    val voiceButtonFocusRequester = remember { FocusRequester() }
    val searchInputFocusRequester = remember { FocusRequester() }
    val activeSearchInputFocusRequester = remember { FocusRequester() }
    val firstCategoryFocusRequester = remember { FocusRequester() }
    val clearSearchFocusRequester = remember { FocusRequester() }

    // Estado da Busca por Digitação: só ativa modo de digitação/teclado ao clicar em cima
    var isEditingSearch by rememberSaveable { mutableStateOf(false) }
    var hasFocusedSearchField by remember { mutableStateOf(false) }
    var isSearchBoxFocused by remember { mutableStateOf(false) }

    // Intercepta botão Voltar do controle quando em modo de edição de busca
    BackHandler(enabled = isEditingSearch) {
        keyboardController?.hide()
        isEditingSearch = false
        runCatching { searchInputFocusRequester.requestFocus() }
    }

    LaunchedEffect(isEditingSearch) {
        if (isEditingSearch) {
            hasFocusedSearchField = false
            for (attempt in 0..4) {
                kotlinx.coroutines.yield()
                try {
                    activeSearchInputFocusRequester.requestFocus()
                    keyboardController?.show()
                    break
                } catch (e: Exception) {
                    kotlinx.coroutines.delay(20L)
                }
            }
        } else {
            hasFocusedSearchField = false
        }
    }

    // Estado da Busca por Voz
    var isVoiceListening by remember { mutableStateOf(false) }
    var voiceRmsLevel by remember { mutableFloatStateOf(0f) }
    var isVoiceButtonFocused by remember { mutableStateOf(false) }

    val isVoiceSearchAvailable = remember(context) { SpeechRecognizer.isRecognitionAvailable(context) }
    val speechRecognizer = remember(context, isVoiceSearchAvailable) {
        if (isVoiceSearchAvailable) {
            runCatching { SpeechRecognizer.createSpeechRecognizer(context) }.getOrNull()
        } else null
    }

    val buildRecognizeIntent = remember {
        {
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            }
        }
    }

    val onVoiceRecognized = rememberUpdatedState { query: String ->
        if (query.isNotBlank()) {
            searchQuery = query
            viewModel.setSearchQuery(query)
            Toast.makeText(context, "Buscando: $query", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Nenhuma fala detectada", Toast.LENGTH_SHORT).show()
        }
    }

    val requestAudioPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            isVoiceListening = true
            runCatching {
                speechRecognizer?.cancel()
                speechRecognizer?.startListening(buildRecognizeIntent())
            }.onFailure {
                isVoiceListening = false
                Toast.makeText(context, "Busca por voz indisponível", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Permissão de microfone necessária", Toast.LENGTH_SHORT).show()
        }
    }

    DisposableEffect(speechRecognizer) {
        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) {
                voiceRmsLevel = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
            }
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit

            override fun onError(error: Int) {
                isVoiceListening = false
                voiceRmsLevel = 0f
                Log.w("TvChannelsScreen", "Voice recognition error: $error")
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                        Toast.makeText(context, "Nenhuma fala reconhecida", Toast.LENGTH_SHORT).show()
                    }
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                        Toast.makeText(context, "Permissão de microfone necessária", Toast.LENGTH_SHORT).show()
                    }
                    else -> Unit
                }
            }

            override fun onResults(results: Bundle?) {
                isVoiceListening = false
                voiceRmsLevel = 0f
                val recognized = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                    .trim()
                onVoiceRecognized.value(recognized)
            }

            override fun onPartialResults(partialResults: Bundle?) = Unit
        }

        speechRecognizer?.setRecognitionListener(listener)
        onDispose {
            speechRecognizer?.setRecognitionListener(null)
            speechRecognizer?.destroy()
        }
    }

    val launchVoiceSearch: () -> Unit = {
        if (!isVoiceSearchAvailable) {
            Toast.makeText(context, "Reconhecimento de voz indisponível neste dispositivo", Toast.LENGTH_SHORT).show()
        } else {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

            if (hasPermission) {
                if (isVoiceListening) {
                    speechRecognizer?.stopListening()
                    isVoiceListening = false
                } else {
                    isVoiceListening = true
                    runCatching {
                        speechRecognizer?.cancel()
                        speechRecognizer?.startListening(buildRecognizeIntent())
                    }.onFailure {
                        isVoiceListening = false
                        Toast.makeText(context, "Falha ao iniciar reconhecimento de voz", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                requestAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    // 1. Modo Tela Cheia Integrado com Zapping D-pad e Gaveta Lateral
    if (state.isFullscreen) {
        val eligibleChannels = remember(state.allChannels, state.hideAdultChannels) {
            if (state.hideAdultChannels) {
                state.allChannels.filter { !AdultChannelDetector.isAdult(it) }
            } else {
                state.allChannels
            }
        }
        val favoritesLabel = stringResource(R.string.tv_channels_favorites)
        val hudCategories = remember(state.categories, state.favoriteKeys, favoritesLabel) {
            (if (state.favoriteKeys.isNotEmpty()) listOf(favoritesLabel) else emptyList()) + state.categories
        }

        TvChannelFullscreenHud(
            channel = state.previewChannel,
            streams = state.previewStreams,
            selectedStreamIndex = state.selectedStreamIndex,
            playerPool = viewModel.playerPool,
            allChannels = eligibleChannels,
            categories = hudCategories,
            hideAdultChannels = state.hideAdultChannels,
            isLoading = state.isLoadingPreview,
            errorMessage = state.previewErrorMessage,
            onSelectChannel = { ch ->
                viewModel.onChannelClicked(ch, openFullscreen = true)
            },
            onExitFullscreen = { viewModel.setFullscreen(false) },
            onNextChannel = { viewModel.selectNextChannel() },
            onPreviousChannel = { viewModel.selectPreviousChannel() }
        )
        return
    }

    // 2. Modo Grade EPG Completa (TV Guide com Régua de Horários)
    if (isEpgGridMode) {
        TvEpgGridGuide(
            channels = state.filteredChannels,
            selectedChannel = state.previewChannel,
            onSelectChannel = { ch, fullscreen ->
                viewModel.onChannelClicked(ch, openFullscreen = fullscreen)
            },
            onBackToList = {
                isEpgGridMode = false
            }
        )
        return
    }

    // 3. Tela Principal Split-Screen (Lista à esquerda, Preview à direita)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NuvioTheme.colors.Background)
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Linha 1: Barra Superior (Título, Badge Ao Vivo, Botões de Ação)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Título e Badges
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.tv_channels_title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFE50914))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.tv_channels_live_badge),
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (state.totalChannelsCount > 0) {
                        Text(
                            text = "(${stringResource(R.string.settings_tv_channels_channels_count, state.filteredChannelsCount)})",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }
                }

                // Botões de Ação Superiores: Grade EPG, Addons, Atualizar
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Botão Alternar para Grade EPG / Modo Lista
                    Button(
                        onClick = { isEpgGridMode = !isEpgGridMode },
                        modifier = Modifier
                            .focusRequester(gradeEpgFocusRequester)
                            .focusProperties {
                                down = searchInputFocusRequester
                            },
                        shape = ButtonDefaults.shape(shape = RoundedCornerShape(8.dp)),
                        colors = ButtonDefaults.colors(
                            containerColor = if (isEpgGridMode) NuvioTheme.colors.Secondary else Color.White.copy(alpha = 0.10f),
                            focusedContainerColor = Color.White,
                            focusedContentColor = Color.Black
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (isEpgGridMode) Icons.Rounded.ViewList else Icons.Rounded.GridView,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (isEpgGridMode) stringResource(R.string.tv_channels_mode_list) else stringResource(R.string.tv_channels_mode_epg),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Botão de Addons
                    Button(
                        onClick = { viewModel.setAddonsDialogVisible(true) },
                        modifier = Modifier
                            .focusRequester(addonsFocusRequester)
                            .focusProperties {
                                down = searchInputFocusRequester
                            },
                        shape = ButtonDefaults.shape(shape = RoundedCornerShape(8.dp)),
                        colors = ButtonDefaults.colors(
                            containerColor = Color.White.copy(alpha = 0.10f),
                            focusedContainerColor = Color.White,
                            focusedContentColor = Color.Black
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Extension,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = stringResource(R.string.tv_channels_addons_count, state.selectedAddonUrls.size),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Botão Atualizar
                    Button(
                        onClick = { viewModel.refresh() },
                        modifier = Modifier
                            .focusRequester(refreshFocusRequester)
                            .focusProperties {
                                down = searchInputFocusRequester
                            },
                        shape = ButtonDefaults.shape(shape = RoundedCornerShape(8.dp)),
                        colors = ButtonDefaults.colors(
                            containerColor = Color.White.copy(alpha = 0.10f),
                            focusedContainerColor = Color.White,
                            focusedContentColor = Color.Black
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = stringResource(R.string.tv_channels_refresh),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Linha 2: Barra de Pesquisa Padrão do App (Botão de Voz Separado do Campo de Digitação)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .focusGroup(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Botão de Busca por Voz (Separado, padrão SearchScreen)
                val themeAccent = NuvioTheme.colors.Secondary
                val pulseTransition = rememberInfiniteTransition(label = "voicePulseChannels")
                val pulseScale by pulseTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.35f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(800, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "pulseScaleChannels"
                )
                val pulseAlpha by pulseTransition.animateFloat(
                    initialValue = 0.5f,
                    targetValue = 0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(800, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "pulseAlphaChannels"
                )
                val animatedRms by animateFloatAsState(
                    targetValue = if (isVoiceListening) voiceRmsLevel else 0f,
                    animationSpec = tween(100),
                    label = "rmsChannels"
                )

                Box(
                    modifier = Modifier.size(54.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isVoiceListening) {
                        Canvas(modifier = Modifier.matchParentSize()) {
                            val radius = (size.minDimension / 2f) * pulseScale
                            drawCircle(
                                color = themeAccent.copy(alpha = pulseAlpha * 0.4f),
                                radius = radius
                            )
                        }
                        if (animatedRms > 0.01f) {
                            Canvas(modifier = Modifier.matchParentSize()) {
                                val rmsRadius = (size.minDimension / 2f) * (1f + animatedRms * 0.35f)
                                drawCircle(
                                    color = themeAccent.copy(alpha = 0.25f + animatedRms * 0.25f),
                                    radius = rmsRadius,
                                    style = Stroke(width = 2.5f + animatedRms * 3f)
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = launchVoiceSearch,
                        modifier = Modifier
                            .focusRequester(voiceButtonFocusRequester)
                            .focusProperties {
                                up = gradeEpgFocusRequester
                                right = searchInputFocusRequester
                                down = firstCategoryFocusRequester
                            }
                            .onFocusChanged { isVoiceButtonFocused = it.isFocused }
                            .size(46.dp)
                            .border(
                                width = if (isVoiceButtonFocused || isVoiceListening) 2.dp else 1.dp,
                                color = if (isVoiceListening) themeAccent else if (isVoiceButtonFocused) NuvioTheme.colors.FocusRing else NuvioTheme.colors.Border,
                                shape = RoundedCornerShape(NuvioTheme.radii.md)
                            )
                            .background(
                                color = if (isVoiceListening) themeAccent.copy(alpha = 0.15f) else NuvioTheme.colors.BackgroundCard,
                                shape = RoundedCornerShape(NuvioTheme.radii.md)
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Mic,
                            contentDescription = "Busca por Voz",
                            tint = if (isVoiceListening) themeAccent else if (isVoiceButtonFocused) Color.White else NuvioTheme.colors.TextPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Campo de Pesquisa por Digitação
                // Se NÃO estiver editando: exibe uma caixa navegável via D-pad que NÃO abre o teclado ao receber foco
                // Abre o teclado virtual SOMENTE ao clicar (Enter / Center / Toque)
                if (!isEditingSearch) {
                    Surface(
                        onClick = {
                            isEditingSearch = true
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .focusRequester(searchInputFocusRequester)
                            .focusProperties {
                                up = gradeEpgFocusRequester
                                left = voiceButtonFocusRequester
                                right = if (searchQuery.isNotEmpty()) clearSearchFocusRequester else FocusRequester.Default
                                down = firstCategoryFocusRequester
                            }
                            .onFocusChanged { isSearchBoxFocused = it.isFocused }
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                    when (keyEvent.nativeKeyEvent.keyCode) {
                                        KeyEvent.KEYCODE_DPAD_CENTER,
                                        KeyEvent.KEYCODE_ENTER,
                                        KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                                            isEditingSearch = true
                                            true
                                        }
                                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                                            runCatching { firstCategoryFocusRequester.requestFocus() }
                                            true
                                        }
                                        KeyEvent.KEYCODE_DPAD_UP -> {
                                            runCatching { gradeEpgFocusRequester.requestFocus() }
                                            true
                                        }
                                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                                            runCatching { voiceButtonFocusRequester.requestFocus() }
                                            true
                                        }
                                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                            if (searchQuery.isNotEmpty()) {
                                                runCatching { clearSearchFocusRequester.requestFocus() }
                                                true
                                            } else false
                                        }
                                        else -> false
                                    }
                                } else false
                            },
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(NuvioTheme.radii.md)),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = NuvioTheme.colors.BackgroundCard,
                            focusedContainerColor = NuvioTheme.colors.BackgroundCard
                        ),
                        border = ClickableSurfaceDefaults.border(
                            border = Border(
                                border = BorderStroke(1.dp, NuvioTheme.colors.Border),
                                shape = RoundedCornerShape(NuvioTheme.radii.md)
                            ),
                            focusedBorder = Border(
                                border = BorderStroke(2.dp, NuvioTheme.colors.FocusRing),
                                shape = RoundedCornerShape(NuvioTheme.radii.md)
                            )
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = null,
                                tint = if (isSearchBoxFocused) NuvioTheme.colors.Secondary else NuvioTheme.colors.TextTertiary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (searchQuery.isNotEmpty()) searchQuery else stringResource(R.string.tv_channels_search_hint),
                                color = if (searchQuery.isNotEmpty()) NuvioTheme.colors.TextPrimary else NuvioTheme.colors.TextTertiary,
                                fontSize = 13.sp,
                                maxLines = 1,
                                modifier = Modifier.weight(1f)
                            )
                            if (isSearchBoxFocused) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color.White.copy(alpha = 0.15f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "OK para digitar",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.White.copy(alpha = 0.85f)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { newQuery ->
                            searchQuery = newQuery
                            viewModel.setSearchQuery(newQuery)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(activeSearchInputFocusRequester)
                            .focusProperties {
                                up = gradeEpgFocusRequester
                                left = voiceButtonFocusRequester
                                right = if (searchQuery.isNotEmpty()) clearSearchFocusRequester else FocusRequester.Default
                                down = firstCategoryFocusRequester
                            }
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused) {
                                    hasFocusedSearchField = true
                                } else if (hasFocusedSearchField && isEditingSearch) {
                                    isEditingSearch = false
                                    keyboardController?.hide()
                                }
                            }
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                    when (keyEvent.nativeKeyEvent.keyCode) {
                                        KeyEvent.KEYCODE_ENTER,
                                        KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                                            keyboardController?.hide()
                                            isEditingSearch = false
                                            runCatching { searchInputFocusRequester.requestFocus() }
                                            return@onPreviewKeyEvent true
                                        }
                                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                                            keyboardController?.hide()
                                            isEditingSearch = false
                                            runCatching { firstCategoryFocusRequester.requestFocus() }
                                            return@onPreviewKeyEvent true
                                        }
                                        KeyEvent.KEYCODE_DPAD_UP -> {
                                            keyboardController?.hide()
                                            isEditingSearch = false
                                            runCatching { gradeEpgFocusRequester.requestFocus() }
                                            return@onPreviewKeyEvent true
                                        }
                                        KeyEvent.KEYCODE_BACK -> {
                                            keyboardController?.hide()
                                            isEditingSearch = false
                                            runCatching { searchInputFocusRequester.requestFocus() }
                                            return@onPreviewKeyEvent true
                                        }
                                    }
                                }
                                false
                            },
                        keyboardOptions = KeyboardOptions.Default.copy(
                            imeAction = ImeAction.Search,
                            autoCorrectEnabled = false
                        ),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                keyboardController?.hide()
                                isEditingSearch = false
                                runCatching { searchInputFocusRequester.requestFocus() }
                            }
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(NuvioTheme.radii.md),
                        placeholder = {
                            Text(
                                text = stringResource(R.string.tv_channels_search_hint),
                                color = NuvioTheme.colors.TextTertiary,
                                fontSize = 13.sp
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = null,
                                tint = NuvioTheme.colors.Secondary,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = NuvioTheme.colors.BackgroundCard,
                            unfocusedContainerColor = NuvioTheme.colors.BackgroundCard,
                            focusedIndicatorColor = NuvioTheme.colors.FocusRing,
                            unfocusedIndicatorColor = NuvioTheme.colors.Border,
                            focusedTextColor = NuvioTheme.colors.TextPrimary,
                            unfocusedTextColor = NuvioTheme.colors.TextPrimary,
                            cursorColor = NuvioTheme.colors.FocusRing
                        )
                    )
                }

                // Botão de Limpar Busca (Aparece ao lado do campo para acesso fácil por D-pad)
                if (searchQuery.isNotEmpty()) {
                    var isClearButtonFocused by remember { mutableStateOf(false) }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            searchQuery = ""
                            viewModel.setSearchQuery("")
                            isEditingSearch = false
                            runCatching { searchInputFocusRequester.requestFocus() }
                        },
                        modifier = Modifier
                            .focusRequester(clearSearchFocusRequester)
                            .focusProperties {
                                up = addonsFocusRequester
                                left = if (isEditingSearch) activeSearchInputFocusRequester else searchInputFocusRequester
                                down = firstCategoryFocusRequester
                            }
                            .onFocusChanged { isClearButtonFocused = it.isFocused }
                            .size(46.dp)
                            .border(
                                width = if (isClearButtonFocused) 2.dp else 1.dp,
                                color = if (isClearButtonFocused) NuvioTheme.colors.FocusRing else NuvioTheme.colors.Border,
                                shape = RoundedCornerShape(NuvioTheme.radii.md)
                            )
                            .background(
                                color = NuvioTheme.colors.BackgroundCard,
                                shape = RoundedCornerShape(NuvioTheme.radii.md)
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Limpar busca",
                            tint = if (isClearButtonFocused) Color.White else NuvioTheme.colors.TextTertiary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Linha 3: Barra de Categorias / Gêneros (Horizontal, deslocada para a direita para não cortar a aba "Todos")
            val allLabel = stringResource(R.string.tv_channels_category_all)
            val favoritesLabel = stringResource(R.string.tv_channels_favorites)
            val categories = listOf(allLabel) +
                (if (state.favoriteKeys.isNotEmpty()) listOf(favoritesLabel) else emptyList()) +
                state.categories

            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 6.dp),
                contentPadding = PaddingValues(start = 10.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(categories) { index, cat ->
                    val isFirst = index == 0
                    val isAll = cat == allLabel || cat.equals("todos", ignoreCase = true) || cat.equals("all", ignoreCase = true)
                    val isFavCat = cat == favoritesLabel || cat.equals("favoritos", ignoreCase = true) || cat.equals("favorites", ignoreCase = true)
                    val isSelected = when {
                        isAll -> state.selectedCategory.isBlank()
                        isFavCat -> state.selectedCategory.equals("favoritos", ignoreCase = true) || state.selectedCategory.equals("favorites", ignoreCase = true) || state.selectedCategory.equals(favoritesLabel, ignoreCase = true)
                        else -> state.selectedCategory.equals(cat, ignoreCase = true)
                    }
                    var isCatFocused by remember { mutableStateOf(false) }
                    val isLightAccent = NuvioTheme.colors.Secondary.luminance() > 0.45f
                    val selectedTextColor = if (isLightAccent) Color.Black else Color.White

                    Button(
                        onClick = {
                            val target = when {
                                isAll -> ""
                                isFavCat -> "Favoritos"
                                else -> cat
                            }
                            viewModel.selectCategory(target)
                        },
                        modifier = (if (isFirst) Modifier.focusRequester(firstCategoryFocusRequester) else Modifier)
                            .focusProperties {
                                up = searchInputFocusRequester
                            }
                            .onFocusChanged { isCatFocused = it.isFocused }
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                                    if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                        runCatching { searchInputFocusRequester.requestFocus() }
                                        return@onPreviewKeyEvent true
                                    }
                                }
                                false
                            },
                        shape = ButtonDefaults.shape(shape = RoundedCornerShape(20.dp)),
                        colors = ButtonDefaults.colors(
                            containerColor = if (isSelected) NuvioTheme.colors.Secondary else Color.White.copy(alpha = 0.08f),
                            contentColor = if (isSelected) selectedTextColor else Color.White,
                            focusedContainerColor = Color.White,
                            focusedContentColor = Color.Black
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val textColor = when {
                                isCatFocused -> Color.Black
                                isSelected -> selectedTextColor
                                else -> Color.White
                            }
                            if (isFavCat) {
                                Icon(
                                    imageVector = Icons.Rounded.Star,
                                    contentDescription = null,
                                    tint = when {
                                        isCatFocused -> Color.Black
                                        isSelected -> selectedTextColor
                                        else -> Color(0xFFFFD700)
                                    },
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Text(
                                text = cat,
                                color = textColor,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Linha 4: Área Split-Screen: Lista à Esquerda e Pré-visualizador à Direita
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // Coluna da Esquerda: Lista de Canais
                Box(
                    modifier = Modifier
                        .weight(1.15f)
                        .fillMaxHeight()
                ) {
                    when {
                        state.selectedAddonUrls.isEmpty() -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Rounded.Extension,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.3f),
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = stringResource(R.string.tv_channels_no_addons_selected),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = stringResource(R.string.tv_channels_no_addons_selected_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.6f)
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Button(
                                        onClick = { viewModel.setAddonsDialogVisible(true) },
                                        shape = ButtonDefaults.shape(shape = RoundedCornerShape(8.dp)),
                                        colors = ButtonDefaults.colors(
                                            containerColor = NuvioTheme.colors.Secondary,
                                            focusedContainerColor = Color.White,
                                            focusedContentColor = Color.Black
                                        )
                                    ) {
                                        Text(text = stringResource(R.string.tv_channels_manage_addons_modal_title), fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        state.isLoadingChannels && state.allChannels.isEmpty() -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    LoadingIndicator(modifier = Modifier.size(42.dp))
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = stringResource(R.string.tv_channels_loading_channels),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }

                        state.filteredChannels.isEmpty() -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Rounded.Tv,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.3f),
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = stringResource(R.string.tv_channels_empty_no_channels),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = stringResource(R.string.tv_channels_empty_no_channels_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }

                        else -> {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                itemsIndexed(
                                    items = state.filteredChannels,
                                    key = { _, channel -> channel.stableKey() }
                                ) { channelIndex, channel ->
                                    val isFirstItem = channelIndex == 0
                                    val isSelected = state.previewChannel?.stableKey() == channel.stableKey()
                                    val isPlaying = isSelected && state.isPreviewPlaybackActive && state.previewStreams.isNotEmpty()

                                    TvChannelListItem(
                                        channel = channel,
                                        isSelected = isSelected,
                                        isPlaying = isPlaying,
                                        modifier = if (isFirstItem) {
                                            Modifier
                                                .focusProperties {
                                                    up = firstCategoryFocusRequester
                                                }
                                                .onPreviewKeyEvent { keyEvent ->
                                                    if (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                                                        if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                                            runCatching { firstCategoryFocusRequester.requestFocus() }
                                                            return@onPreviewKeyEvent true
                                                        }
                                                    }
                                                    false
                                                }
                                        } else {
                                            Modifier
                                        },
                                        onFocused = {
                                            viewModel.onChannelFocused(channel)
                                        },
                                        onClick = {
                                            viewModel.onChannelClicked(channel, openFullscreen = true)
                                        },
                                        onLongClick = {
                                            viewModel.toggleFavorite(channel)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Coluna da Direita: Pré-visualizador
                TvChannelPreviewPanel(
                    channel = state.previewChannel,
                    streams = state.previewStreams,
                    selectedStreamIndex = state.selectedStreamIndex,
                    isLoading = state.isLoadingPreview,
                    errorMessage = state.previewErrorMessage,
                    playerPool = viewModel.playerPool,
                    isPreviewActive = state.isPreviewPlaybackActive,
                    onStartPlayback = {
                        state.previewChannel?.let { viewModel.startChannelPreviewPlayback(it) }
                    },
                    onWatchFullscreen = {
                        val activeStream = state.activeStream
                        if (onWatchFullscreenInMainPlayer != null && state.previewChannel != null && activeStream != null) {
                            onWatchFullscreenInMainPlayer(state.previewChannel!!, activeStream)
                        } else {
                            if (!state.isPreviewPlaybackActive && state.previewChannel != null) {
                                viewModel.startChannelPreviewPlayback(state.previewChannel!!)
                            }
                            viewModel.setFullscreen(true)
                        }
                    },
                    onToggleFavorite = {
                        state.previewChannel?.let { viewModel.toggleFavorite(it) }
                    },
                    onOpenSchedule = {
                        viewModel.setScheduleDialogVisible(true)
                    },
                    onStreamSelected = { idx ->
                        viewModel.selectStreamIndex(idx)
                    },
                    modifier = Modifier
                        .weight(1.0f)
                        .fillMaxHeight()
                )
            }
        }
    }

    // Diálogos Modais
    TvAddonsSelectionDialog(
        visible = state.isAddonsDialogVisible,
        addons = state.availableAddons,
        onToggleAddon = viewModel::toggleAddonSelection,
        onSelectAll = viewModel::selectAllAddons,
        onClearAll = viewModel::deselectAllAddons,
        onDismiss = { viewModel.setAddonsDialogVisible(false) }
    )

    TvChannelScheduleDialog(
        visible = state.isScheduleDialogVisible,
        channel = state.previewChannel,
        onOpenFullGuide = {
            viewModel.setScheduleDialogVisible(false)
            isEpgGridMode = true
        },
        onDismiss = { viewModel.setScheduleDialogVisible(false) }
    )
}
