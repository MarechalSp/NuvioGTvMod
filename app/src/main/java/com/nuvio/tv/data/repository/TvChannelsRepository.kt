package com.nuvio.tv.data.repository

import android.content.Context
import android.util.Log
import com.nuvio.tv.core.network.safeApiCall
import com.nuvio.tv.data.local.TvChannelsDataStore
import com.nuvio.tv.data.mapper.toDomain
import com.nuvio.tv.data.remote.api.AddonApi
import com.nuvio.tv.data.repository.epg.TvEpgRepository
import com.nuvio.tv.domain.model.Addon
import com.nuvio.tv.domain.model.AdultChannelDetector
import com.nuvio.tv.domain.model.CatalogDescriptor
import com.nuvio.tv.domain.model.Stream
import com.nuvio.tv.domain.model.TvAddonFilterOption
import com.nuvio.tv.domain.model.TvCatalogSection
import com.nuvio.tv.domain.model.TvChannelItem
import com.nuvio.tv.domain.model.TvChannelsUiState
import com.nuvio.tv.domain.model.enabledAddons
import com.nuvio.tv.domain.model.resolvePlayableTvStreamUrl
import com.nuvio.tv.domain.repository.AddonRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TvChannelsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val addonRepository: AddonRepository,
    private val addonApi: AddonApi,
    private val tvChannelsDataStore: TvChannelsDataStore,
    private val tvEpgRepository: TvEpgRepository
) {
    companion object {
        private const val TAG = "TvChannelsRepo"

        private val TV_CATALOG_TYPES = setOf(
            "tv", "channel", "channels", "iptv", "live", "livetv", "live_tv",
            "stream", "streams", "broadcast", "broadcasts", "radio", "sports",
            "sport", "events", "event", "news", "cctv", "feed", "feeds", "other"
        )
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _uiState = MutableStateFlow(TvChannelsUiState())
    val uiState: StateFlow<TvChannelsUiState> = _uiState.asStateFlow()

    private var loadChannelsJob: Job? = null
    private var loadStreamJob: Job? = null
    private var isInitialized = false

    // Cache em memória para resposta instantânea
    private val channelsMemoryCache = mutableMapOf<Set<String>, List<TvChannelItem>>()
    private val streamMemoryCache = mutableMapOf<String, List<Stream>>()

    fun initialize() {
        if (isInitialized) return
        isInitialized = true
        tvEpgRepository.initialize()

        // 1. Observa mudanças nos addons instalados e addons selecionados pelo usuário
        scope.launch {
            combine(
                addonRepository.getInstalledAddons(),
                tvChannelsDataStore.selectedAddonUrls
            ) { addons, selectedUrls ->
                addons.enabledAddons() to selectedUrls
            }.collectLatest { (enabledAddons, explicitSelectedUrls) ->
                handleAddonsOrSelectionChanged(enabledAddons, explicitSelectedUrls)
            }
        }

        // 2. Observa mudanças em canais favoritos
        scope.launch {
            tvChannelsDataStore.favoriteChannelKeys.collectLatest { favKeys ->
                _uiState.update { current ->
                    val enrichedAll = current.allChannels.map { ch ->
                        ch.copy(isFavorite = ch.isFavoritedIn(favKeys))
                    }
                    val newCategory = if (favKeys.isEmpty() && (current.selectedCategory.equals("favoritos", ignoreCase = true) || current.selectedCategory.equals("favorites", ignoreCase = true))) {
                        ""
                    } else {
                        current.selectedCategory
                    }
                    val enrichedFiltered = filterChannels(
                        channels = enrichedAll,
                        category = newCategory,
                        query = current.searchQuery,
                        hideAdult = current.hideAdultChannels,
                        favoriteKeys = favKeys
                    )
                    val sections = buildCatalogSections(enrichedFiltered, favKeys)
                    val updatedPreview = current.previewChannel?.let { pc ->
                        pc.copy(isFavorite = pc.isFavoritedIn(favKeys))
                    }
                    current.copy(
                        allChannels = enrichedAll,
                        filteredChannels = enrichedFiltered,
                        favoriteKeys = favKeys,
                        selectedCategory = newCategory,
                        catalogSections = sections,
                        previewChannel = updatedPreview
                    )
                }
            }
        }

        // 3. Observa atualizações do EPG para enriquecer programas em tempo real
        scope.launch {
            tvEpgRepository.uiState.collectLatest { _ ->
                _uiState.update { current ->
                    val enrichedAll = current.allChannels.map { ch ->
                        ch.copy(epgInfo = tvEpgRepository.findEpgForChannel(ch))
                    }
                    val enrichedFiltered = current.filteredChannels.map { ch ->
                        ch.copy(epgInfo = tvEpgRepository.findEpgForChannel(ch))
                    }
                    val sections = buildCatalogSections(enrichedFiltered, current.favoriteKeys)
                    current.copy(
                        allChannels = enrichedAll,
                        filteredChannels = enrichedFiltered,
                        catalogSections = sections
                    )
                }
            }
        }

        // 4. Observa preferência de ocultação de canais adultos
        scope.launch {
            tvChannelsDataStore.hideAdultChannels.collectLatest { hideAdult ->
                _uiState.update { current ->
                    val updated = filterChannels(current.allChannels, current.selectedCategory, current.searchQuery, hideAdult = hideAdult)
                    val updatedCategories = extractCategories(current.allChannels, hideAdult = hideAdult)
                    val sections = buildCatalogSections(updated, current.favoriteKeys)
                    current.copy(
                        hideAdultChannels = hideAdult,
                        filteredChannels = updated,
                        categories = updatedCategories,
                        catalogSections = sections
                    )
                }
            }
        }

        // 5. Observa preferência de autoplay de canais
        scope.launch {
            tvChannelsDataStore.channelAutoplay.collectLatest { autoplay ->
                _uiState.update { it.copy(channelAutoplay = autoplay) }
            }
        }
    }

    private fun handleAddonsOrSelectionChanged(
        enabledAddons: List<Addon>,
        explicitSelectedUrls: Set<String>?
    ) {
        val availableOptions = enabledAddons.map { addon ->
            val hasTvCatalogs = isTvAddon(addon)
            TvAddonFilterOption(
                manifestUrl = addon.baseUrl,
                addonName = addon.displayName,
                logoUrl = addon.logo,
                catalogCount = addon.catalogs.size,
                isSelected = explicitSelectedUrls?.contains(addon.baseUrl) ?: hasTvCatalogs
            )
        }

        val effectiveSelectedUrls = if (explicitSelectedUrls != null) {
            explicitSelectedUrls
        } else {
            val withTv = availableOptions.filter { it.isSelected }.map { it.manifestUrl }.toSet()
            withTv.ifEmpty { availableOptions.map { it.manifestUrl }.toSet() }
        }

        _uiState.update { current ->
            current.copy(
                availableAddons = availableOptions.map { opt ->
                    opt.copy(isSelected = effectiveSelectedUrls.contains(opt.manifestUrl))
                },
                selectedAddonUrls = effectiveSelectedUrls
            )
        }

        loadChannels(enabledAddons, effectiveSelectedUrls)
    }

    private fun isTvCatalog(type: String, id: String = "", name: String = ""): Boolean {
        val t = type.lowercase().trim()
        val i = id.lowercase().trim()
        val n = name.lowercase().trim()
        if (TV_CATALOG_TYPES.contains(t)) return true
        if (i.contains("tv") || i.contains("channel") || i.contains("iptv") || i.contains("live") || i.contains("aovivo") || i.contains("canal")) return true
        if (n.contains("tv") || n.contains("canal") || n.contains("canais") || n.contains("channel") || n.contains("iptv") || n.contains("ao vivo")) return true
        return false
    }

    private fun isTvAddon(addon: Addon): Boolean {
        val id = addon.id.lowercase()
        val name = addon.name.lowercase()
        val desc = (addon.description ?: "").lowercase()
        if (id.contains("iptv") || id.contains("tv") || id.contains("channel") || id.contains("live") || id.contains("brazuca") || id.contains("sexta")) return true
        if (name.contains("iptv") || name.contains("tv") || name.contains("channel") || name.contains("canal") || name.contains("ao vivo") || name.contains("live")) return true
        if (desc.contains("iptv") || desc.contains("canal") || desc.contains("canais") || desc.contains("live tv") || desc.contains("tv ao vivo")) return true
        return addon.catalogs.any { isTvCatalog(it.rawType, it.id, it.name) }
    }

    fun refresh() {
        channelsMemoryCache.clear()
        streamMemoryCache.clear()
        _uiState.update { it.copy(isLoadingChannels = true, channelsErrorMessage = null) }
        scope.launch {
            // Força sincronização de EPG
            tvEpgRepository.syncEpg(forceRefresh = true)
            // Recarrega addons instalados frescos e seleção do DataStore
            val installed = addonRepository.getInstalledAddons().first()
            val explicitSelectedUrls = tvChannelsDataStore.selectedAddonUrls.first()
            handleAddonsOrSelectionChanged(installed.enabledAddons(), explicitSelectedUrls)
        }
    }

    private fun loadChannels(
        allAddons: List<Addon>,
        selectedUrls: Set<String>,
        forceRefresh: Boolean = false
    ) {
        loadChannelsJob?.cancel()

        if (selectedUrls.isEmpty()) {
            _uiState.update { current ->
                current.copy(
                    allChannels = emptyList(),
                    filteredChannels = emptyList(),
                    categories = emptyList(),
                    isLoadingChannels = false,
                    channelsErrorMessage = null
                )
            }
            return
        }

        val cached = channelsMemoryCache[selectedUrls]
        if (cached != null && !forceRefresh) {
            val favKeys = _uiState.value.favoriteKeys
            val enrichedCached = cached.map { ch ->
                ch.copy(isFavorite = ch.isFavoritedIn(favKeys))
            }
            val allCategories = extractCategories(enrichedCached, _uiState.value.hideAdultChannels)
            _uiState.update { current ->
                val currentFavs = if (favKeys.isNotEmpty()) favKeys else current.favoriteKeys
                val filtered = filterChannels(
                    channels = enrichedCached,
                    category = current.selectedCategory,
                    query = current.searchQuery,
                    hideAdult = current.hideAdultChannels,
                    favoriteKeys = currentFavs
                )
                val sections = buildCatalogSections(filtered, currentFavs)
                val updatedPreview = current.previewChannel?.let { pc ->
                    pc.copy(isFavorite = pc.isFavoritedIn(currentFavs))
                }
                current.copy(
                    allChannels = enrichedCached,
                    filteredChannels = filtered,
                    catalogSections = sections,
                    categories = allCategories,
                    previewChannel = updatedPreview,
                    isLoadingChannels = false,
                    channelsErrorMessage = null
                )
            }
            return
        }

        _uiState.update { it.copy(isLoadingChannels = cached == null, channelsErrorMessage = null) }

        loadChannelsJob = scope.launch {
            val selectedAddons = allAddons.filter { selectedUrls.contains(it.baseUrl) }

            val catalogTasks = selectedAddons.flatMap { addon ->
                addon.catalogs.map { catalog -> addon to catalog }
            }

            val catalogResults = coroutineScope {
                catalogTasks.map { (addon, catalog) ->
                    async(Dispatchers.IO) {
                        val items = runCatching {
                            fetchAllItemsFromCatalog(
                                baseUrl = addon.baseUrl,
                                catalog = catalog
                            )
                        }.onFailure { err ->
                            Log.w(TAG, "Failed to load TV catalog ${catalog.name} from ${addon.displayName}: ${err.message}")
                        }.getOrNull().orEmpty()

                        Triple(addon, catalog, items)
                    }
                }.awaitAll()
            }

            val loadedChannels = mutableListOf<TvChannelItem>()
            val seenKeys = mutableSetOf<String>()
            for ((addon, catalog, items) in catalogResults) {
                val catName = catalog.name.ifBlank { catalog.id }
                for (meta in items) {
                    val metaId = meta.id ?: continue
                    val metaName = meta.name ?: continue
                    val genres = if (!meta.genres.isNullOrEmpty()) {
                        meta.genres
                    } else if (catName.isNotBlank() && !catName.equals("tv", ignoreCase = true) && !catName.equals("canais", ignoreCase = true)) {
                        listOf(catName)
                    } else {
                        emptyList()
                    }

                    val channel = TvChannelItem(
                        id = metaId,
                        type = meta.type ?: catalog.rawType,
                        name = metaName,
                        poster = meta.poster,
                        logo = meta.logo ?: meta.poster,
                        genres = genres,
                        description = meta.description,
                        addonName = addon.displayName,
                        addonManifestUrl = addon.baseUrl,
                        addonLogo = addon.logo,
                        catalogId = catalog.id,
                        catalogName = catName
                    )
                    if (seenKeys.add(channel.stableKey())) {
                        loadedChannels.add(channel)
                    }
                }
            }

            val favKeys = runCatching { tvChannelsDataStore.favoriteChannelKeys.first() }.getOrDefault(_uiState.value.favoriteKeys)
            val enrichedChannels = loadedChannels.map { channel ->
                channel.copy(
                    isFavorite = channel.isFavoritedIn(favKeys),
                    epgInfo = tvEpgRepository.findEpgForChannel(channel)
                )
            }

            channelsMemoryCache[selectedUrls] = enrichedChannels
            val allCategories = extractCategories(enrichedChannels, _uiState.value.hideAdultChannels)

            _uiState.update { current ->
                val effectiveFavs = if (favKeys.isNotEmpty()) favKeys else current.favoriteKeys
                val filtered = filterChannels(
                    channels = enrichedChannels,
                    category = current.selectedCategory,
                    query = current.searchQuery,
                    hideAdult = current.hideAdultChannels,
                    favoriteKeys = effectiveFavs
                )
                val sections = buildCatalogSections(filtered, effectiveFavs)
                val updatedPreview = current.previewChannel?.let { pc ->
                    pc.copy(isFavorite = pc.isFavoritedIn(effectiveFavs))
                }
                current.copy(
                    allChannels = enrichedChannels,
                    filteredChannels = filtered,
                    catalogSections = sections,
                    categories = allCategories,
                    favoriteKeys = effectiveFavs,
                    previewChannel = updatedPreview,
                    isLoadingChannels = false,
                    channelsErrorMessage = if (enrichedChannels.isEmpty() && selectedAddons.isNotEmpty()) {
                        "Nenhum canal encontrado nos addons selecionados."
                    } else null
                )
            }
        }
    }

    private suspend fun fetchAllItemsFromCatalog(
        baseUrl: String,
        catalog: CatalogDescriptor
    ): List<com.nuvio.tv.data.remote.dto.MetaPreviewDto> {
        val allItems = mutableListOf<com.nuvio.tv.data.remote.dto.MetaPreviewDto>()
        val seenIds = mutableSetOf<String>()
        var currentSkip = 0
        var pageCount = 0
        val maxPages = 15

        while (pageCount < maxPages) {
            pageCount++
            val url = buildCatalogUrl(baseUrl, catalog.rawType, catalog.id, currentSkip)
            val response = runCatching {
                safeApiCall(context) { addonApi.getCatalog(url) }
            }.getOrNull()

            val metas = when (response) {
                is com.nuvio.tv.core.network.NetworkResult.Success -> response.data.metas.filterNotNull()
                else -> emptyList()
            }

            if (metas.isEmpty()) break

            var addedAny = false
            for (item in metas) {
                val id = item.id ?: continue
                if (seenIds.add(id)) {
                    allItems.add(item)
                    addedAny = true
                }
            }

            if (!addedAny || metas.size < 50) break
            currentSkip += metas.size
        }

        return allItems
    }

    private fun buildCatalogUrl(baseUrl: String, type: String, catalogId: String, skip: Int): String {
        val trimmed = baseUrl.trimEnd('/')
        val queryStart = trimmed.indexOf('?')
        val basePath = if (queryStart >= 0) trimmed.substring(0, queryStart).trimEnd('/') else trimmed
        val baseQuery = if (queryStart >= 0) trimmed.substring(queryStart) else ""

        val catalogPath = if (skip > 0) {
            "$basePath/catalog/$type/$catalogId/skip=$skip.json"
        } else {
            "$basePath/catalog/$type/$catalogId.json"
        }
        return catalogPath + baseQuery
    }

    fun setCategory(category: String) {
        _uiState.update { current ->
            val updated = filterChannels(
                channels = current.allChannels,
                category = category,
                query = current.searchQuery,
                hideAdult = current.hideAdultChannels,
                favoriteKeys = current.favoriteKeys
            )
            val sections = buildCatalogSections(updated, current.favoriteKeys)
            current.copy(
                selectedCategory = category,
                filteredChannels = updated,
                catalogSections = sections
            )
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { current ->
            val updated = filterChannels(
                channels = current.allChannels,
                category = current.selectedCategory,
                query = query,
                hideAdult = current.hideAdultChannels,
                favoriteKeys = current.favoriteKeys
            )
            val sections = buildCatalogSections(updated, current.favoriteKeys)
            current.copy(
                searchQuery = query,
                filteredChannels = updated,
                catalogSections = sections
            )
        }
    }

    fun setHideAdultChannels(hide: Boolean) {
        scope.launch {
            tvChannelsDataStore.setHideAdultChannels(hide)
        }
        _uiState.update { current ->
            val updated = filterChannels(
                channels = current.allChannels,
                category = current.selectedCategory,
                query = current.searchQuery,
                hideAdult = hide,
                favoriteKeys = current.favoriteKeys
            )
            val updatedCategories = extractCategories(current.allChannels, hideAdult = hide)
            val sections = buildCatalogSections(updated, current.favoriteKeys)
            current.copy(
                hideAdultChannels = hide,
                filteredChannels = updated,
                categories = updatedCategories,
                catalogSections = sections
            )
        }
    }

    fun setChannelAutoplay(enabled: Boolean) {
        scope.launch {
            tvChannelsDataStore.setChannelAutoplay(enabled)
        }
        _uiState.update { it.copy(channelAutoplay = enabled) }
    }

    private fun filterChannels(
        channels: List<TvChannelItem>,
        category: String,
        query: String,
        hideAdult: Boolean = _uiState.value.hideAdultChannels,
        favoriteKeys: Set<String> = _uiState.value.favoriteKeys
    ): List<TvChannelItem> {
        val trimmedQuery = query.trim().lowercase()
        return channels.mapNotNull { channel ->
            if (hideAdult && AdultChannelDetector.isAdult(channel)) {
                return@mapNotNull null
            }

            val isChannelFav = channel.isFavorite || channel.isFavoritedIn(favoriteKeys)
            val matchesCategory = when {
                category.isBlank() || category.equals("todos", ignoreCase = true) || category.equals("all", ignoreCase = true) -> true
                category.equals("favoritos", ignoreCase = true) || category.equals("favorites", ignoreCase = true) -> isChannelFav
                else -> channel.genres.any { it.equals(category, ignoreCase = true) } ||
                    channel.catalogName.equals(category, ignoreCase = true)
            }

            val matchesQuery = trimmedQuery.isBlank() ||
                channel.name.lowercase().contains(trimmedQuery) ||
                channel.addonName.lowercase().contains(trimmedQuery) ||
                channel.catalogName.lowercase().contains(trimmedQuery) ||
                channel.genres.any { it.lowercase().contains(trimmedQuery) }

            if (matchesCategory && matchesQuery) {
                if (channel.isFavorite != isChannelFav) channel.copy(isFavorite = isChannelFav) else channel
            } else {
                null
            }
        }
    }

    private fun extractCategories(channels: List<TvChannelItem>, hideAdult: Boolean): List<String> {
        val candidateChannels = if (hideAdult) {
            channels.filter { !AdultChannelDetector.isAdult(it) }
        } else {
            channels
        }
        return candidateChannels
            .flatMap { ch -> ch.genres + listOf(ch.catalogName) }
            .map { it.trim() }
            .filter {
                it.isNotBlank() &&
                    !it.equals("tv", ignoreCase = true) &&
                    !it.equals("canais", ignoreCase = true) &&
                    (!hideAdult || !AdultChannelDetector.isAdultCategory(it))
            }
            .distinct()
            .sorted()
    }

    fun toggleAddonSelection(manifestUrl: String) {
        val currentSelected = _uiState.value.selectedAddonUrls
        val updated = if (currentSelected.contains(manifestUrl)) {
            currentSelected - manifestUrl
        } else {
            currentSelected + manifestUrl
        }
        _uiState.update { current ->
            current.copy(
                availableAddons = current.availableAddons.map { addon ->
                    if (addon.manifestUrl == manifestUrl) addon.copy(isSelected = updated.contains(addon.manifestUrl)) else addon
                },
                selectedAddonUrls = updated
            )
        }
        scope.launch {
            tvChannelsDataStore.setSelectedAddonUrls(updated)
        }
    }

    fun selectAllAddons() {
        val allAvailable = _uiState.value.availableAddons.map { it.manifestUrl }.toSet()
        _uiState.update { current ->
            current.copy(
                availableAddons = current.availableAddons.map { it.copy(isSelected = true) },
                selectedAddonUrls = allAvailable
            )
        }
        scope.launch {
            tvChannelsDataStore.setSelectedAddonUrls(allAvailable)
        }
    }

    fun deselectAllAddons() {
        _uiState.update { current ->
            current.copy(
                availableAddons = current.availableAddons.map { it.copy(isSelected = false) },
                selectedAddonUrls = emptySet()
            )
        }
        scope.launch {
            tvChannelsDataStore.setSelectedAddonUrls(emptySet())
        }
    }

    fun selectChannelMetadata(channel: TvChannelItem?) {
        loadStreamJob?.cancel()

        if (channel == null) {
            _uiState.update {
                it.copy(
                    previewChannel = null,
                    previewStreams = emptyList(),
                    selectedStreamIndex = 0,
                    isLoadingPreview = false,
                    isPreviewPlaybackActive = false,
                    previewErrorMessage = null,
                    isFullscreen = false
                )
            }
            return
        }

        val enriched = channel.copy(isFavorite = channel.isFavoritedIn(_uiState.value.favoriteKeys))
        _uiState.update {
            it.copy(
                previewChannel = enriched,
                previewStreams = emptyList(),
                selectedStreamIndex = 0,
                isLoadingPreview = false,
                isPreviewPlaybackActive = false,
                previewErrorMessage = null
            )
        }
    }

    fun selectChannelForPreview(channel: TvChannelItem?, startPlayback: Boolean = true) {
        loadStreamJob?.cancel()

        if (channel == null) {
            _uiState.update {
                it.copy(
                    previewChannel = null,
                    previewStreams = emptyList(),
                    selectedStreamIndex = 0,
                    isLoadingPreview = false,
                    isPreviewPlaybackActive = false,
                    previewErrorMessage = null,
                    isFullscreen = false
                )
            }
            return
        }

        if (!startPlayback) {
            selectChannelMetadata(channel)
            return
        }

        val enriched = channel.copy(isFavorite = channel.isFavoritedIn(_uiState.value.favoriteKeys))

        // Verifica cache de streams
        val cached = streamMemoryCache[channel.stableKey()]
        if (!cached.isNullOrEmpty()) {
            _uiState.update {
                it.copy(
                    previewChannel = enriched,
                    previewStreams = cached,
                    selectedStreamIndex = 0,
                    isLoadingPreview = false,
                    isPreviewPlaybackActive = true,
                    previewErrorMessage = null
                )
            }
            prefetchAdjacentStreams(channel)
            return
        }

        _uiState.update {
            it.copy(
                previewChannel = enriched,
                previewStreams = emptyList(),
                selectedStreamIndex = 0,
                isLoadingPreview = true,
                isPreviewPlaybackActive = true,
                previewErrorMessage = null
            )
        }

        loadStreamJob = scope.launch(Dispatchers.IO) {
            runCatching {
                val streamUrl = buildStreamUrl(channel.addonManifestUrl, channel.type, channel.id)
                val response = safeApiCall(context) { addonApi.getStreams(streamUrl) }
                val dtoList = when (response) {
                    is com.nuvio.tv.core.network.NetworkResult.Success -> response.data.streams.orEmpty()
                    else -> emptyList()
                }

                dtoList.mapNotNull { dto ->
                    val domainStream = dto.toDomain(channel.addonName, channel.addonLogo)
                    val resolvedUrl = resolvePlayableTvStreamUrl(domainStream)
                    if (!resolvedUrl.isNullOrBlank()) {
                        if (domainStream.url != resolvedUrl) domainStream.copy(url = resolvedUrl) else domainStream
                    } else {
                        null
                    }
                }
            }.fold(
                onSuccess = { streams ->
                    streamMemoryCache[channel.stableKey()] = streams
                    _uiState.update { current ->
                        if (current.previewChannel?.stableKey() == channel.stableKey()) {
                            current.copy(
                                previewStreams = streams,
                                selectedStreamIndex = 0,
                                isLoadingPreview = false,
                                isPreviewPlaybackActive = streams.isNotEmpty(),
                                previewErrorMessage = if (streams.isEmpty()) {
                                    "Nenhuma transmissão encontrada para este canal"
                                } else null
                            )
                        } else current
                    }
                    prefetchAdjacentStreams(channel)
                },
                onFailure = { err ->
                    Log.w(TAG, "Failed to load stream for channel ${channel.name}: ${err.message}")
                    _uiState.update { current ->
                        if (current.previewChannel?.stableKey() == channel.stableKey()) {
                            current.copy(
                                previewStreams = emptyList(),
                                isLoadingPreview = false,
                                isPreviewPlaybackActive = false,
                                previewErrorMessage = err.message ?: "Falha ao carregar transmissão"
                            )
                        } else current
                    }
                }
            )
        }
    }

    private fun buildStreamUrl(baseUrl: String, type: String, id: String): String {
        val trimmed = baseUrl.trimEnd('/')
        val queryStart = trimmed.indexOf('?')
        val basePath = if (queryStart >= 0) trimmed.substring(0, queryStart).trimEnd('/') else trimmed
        val baseQuery = if (queryStart >= 0) trimmed.substring(queryStart) else ""
        val encodedId = URLEncoder.encode(id, "UTF-8").replace("+", "%20")
        val encodedType = URLEncoder.encode(type, "UTF-8").replace("+", "%20")
        return "$basePath/stream/$encodedType/$encodedId.json$baseQuery"
    }

    private fun prefetchAdjacentStreams(currentChannel: TvChannelItem) {
        val channels = _uiState.value.filteredChannels
        val index = channels.indexOfFirst { it.stableKey() == currentChannel.stableKey() }
        if (index == -1) return
        val nextIndex = (index + 1).takeIf { it in channels.indices }
        val prevIndex = (index - 1).takeIf { it in channels.indices }

        listOfNotNull(nextIndex, prevIndex).map { channels[it] }.forEach { adjChannel ->
            if (!streamMemoryCache.containsKey(adjChannel.stableKey())) {
                scope.launch(Dispatchers.IO) {
                    runCatching {
                        val streamUrl = buildStreamUrl(adjChannel.addonManifestUrl, adjChannel.type, adjChannel.id)
                        val response = safeApiCall(context) { addonApi.getStreams(streamUrl) }
                        val dtoList = when (response) {
                            is com.nuvio.tv.core.network.NetworkResult.Success -> response.data.streams.orEmpty()
                            else -> emptyList()
                        }
                        val resolved = dtoList.mapNotNull { dto ->
                            val domainStream = dto.toDomain(adjChannel.addonName, adjChannel.addonLogo)
                            val rUrl = resolvePlayableTvStreamUrl(domainStream)
                            if (!rUrl.isNullOrBlank()) {
                                if (domainStream.url != rUrl) domainStream.copy(url = rUrl) else domainStream
                            } else null
                        }
                        if (resolved.isNotEmpty()) {
                            streamMemoryCache[adjChannel.stableKey()] = resolved
                        }
                    }
                }
            }
        }
    }

    fun selectStreamIndex(index: Int) {
        _uiState.update { current ->
            if (index in current.previewStreams.indices) {
                current.copy(selectedStreamIndex = index)
            } else current
        }
    }

    fun setFullscreen(fullscreen: Boolean) {
        _uiState.update { it.copy(isFullscreen = fullscreen) }
    }

    fun setAddonsDialogVisible(visible: Boolean) {
        _uiState.update { it.copy(isAddonsDialogVisible = visible) }
    }

    fun setScheduleDialogVisible(visible: Boolean) {
        _uiState.update { it.copy(isScheduleDialogVisible = visible) }
    }

    fun selectNextChannel() {
        val current = _uiState.value
        val list = current.filteredChannels
        if (list.isEmpty()) return
        val currentIndex = list.indexOfFirst { it.stableKey() == current.previewChannel?.stableKey() }
        val nextIndex = if (currentIndex in list.indices) {
            (currentIndex + 1) % list.size
        } else 0
        selectChannelForPreview(list[nextIndex])
    }

    fun selectPreviousChannel() {
        val current = _uiState.value
        val list = current.filteredChannels
        if (list.isEmpty()) return
        val currentIndex = list.indexOfFirst { it.stableKey() == current.previewChannel?.stableKey() }
        val prevIndex = if (currentIndex in list.indices) {
            if (currentIndex - 1 < 0) list.size - 1 else currentIndex - 1
        } else list.size - 1
        selectChannelForPreview(list[prevIndex])
    }

    fun toggleFavorite(channel: TvChannelItem) {
        val key = channel.stableKey()
        val legacyKey = "${channel.addonManifestUrl}:${channel.type}:${channel.id}"

        _uiState.update { current ->
            val isCurrentlyFav = current.favoriteKeys.contains(key) || current.favoriteKeys.contains(legacyKey) || channel.isFavorite
            val isNowFav = !isCurrentlyFav
            val newFavKeys = if (isNowFav) {
                current.favoriteKeys + key
            } else {
                current.favoriteKeys - key - legacyKey
            }
            val newCategory = if (newFavKeys.isEmpty() && (current.selectedCategory.equals("favoritos", ignoreCase = true) || current.selectedCategory.equals("favorites", ignoreCase = true))) {
                ""
            } else {
                current.selectedCategory
            }
            val enrichedAll = current.allChannels.map { ch ->
                if (ch.stableKey() == key || ch.stableKey() == legacyKey) {
                    ch.copy(isFavorite = isNowFav)
                } else {
                    ch.copy(isFavorite = ch.isFavoritedIn(newFavKeys))
                }
            }
            val enrichedFiltered = filterChannels(
                channels = enrichedAll,
                category = newCategory,
                query = current.searchQuery,
                hideAdult = current.hideAdultChannels,
                favoriteKeys = newFavKeys
            )
            val sections = buildCatalogSections(enrichedFiltered, newFavKeys)
            val updatedPreview = if (current.previewChannel?.stableKey() == key || current.previewChannel?.stableKey() == legacyKey) {
                current.previewChannel.copy(isFavorite = isNowFav)
            } else {
                current.previewChannel?.let { pc ->
                    pc.copy(isFavorite = pc.isFavoritedIn(newFavKeys))
                }
            }

            current.copy(
                allChannels = enrichedAll,
                filteredChannels = enrichedFiltered,
                favoriteKeys = newFavKeys,
                selectedCategory = newCategory,
                catalogSections = sections,
                previewChannel = updatedPreview
            )
        }

        scope.launch {
            val currentPrefs = runCatching { tvChannelsDataStore.favoriteChannelKeys.first() }.getOrDefault(emptySet())
            val isCurrentlyFav = currentPrefs.contains(key) || currentPrefs.contains(legacyKey)
            val updatedPrefs = if (isCurrentlyFav) {
                currentPrefs - key - legacyKey
            } else {
                currentPrefs + key
            }
            tvChannelsDataStore.setFavoriteChannelKeys(updatedPrefs)
        }
    }

    private fun buildCatalogSections(
        channels: List<TvChannelItem>,
        favoriteKeys: Set<String>
    ): List<TvCatalogSection> {
        if (channels.isEmpty()) return emptyList()

        val sections = mutableListOf<TvCatalogSection>()

        // 1. Favoritos
        val favorites = channels.filter { it.isFavorite || it.isFavoritedIn(favoriteKeys) }
        if (favorites.isNotEmpty()) {
            sections.add(
                TvCatalogSection(
                    id = "favorites",
                    title = "Meus Favoritos",
                    iconEmoji = "⭐",
                    channels = favorites
                )
            )
        }

        // 2. Esportes & Futebol
        val sports = channels.filter { ch ->
            matchesTheme(ch, setOf("esporte", "esportes", "sport", "sports", "futebol", "premiere", "espn", "sportv", "dazn", "combate", "band sports", "caze"))
        }
        if (sports.isNotEmpty()) {
            sections.add(
                TvCatalogSection(
                    id = "sports",
                    title = "Esportes & Futebol",
                    iconEmoji = "⚽",
                    channels = sports
                )
            )
        }

        // 3. Notícias & Jornalismo
        val news = channels.filter { ch ->
            matchesTheme(ch, setOf("noticia", "noticias", "news", "jornal", "globonews", "cnn", "bandnews", "record news", "jovem pan"))
        }
        if (news.isNotEmpty()) {
            sections.add(
                TvCatalogSection(
                    id = "news",
                    title = "Notícias & Jornalismo",
                    iconEmoji = "📰",
                    channels = news
                )
            )
        }

        // 4. Filmes & Séries
        val movies = channels.filter { ch ->
            matchesTheme(ch, setOf("filme", "filmes", "serie", "series", "cinema", "movie", "movies", "telecine", "hbo", "cinemax", "megapix", "warner", "sony", "universal", "paramount", "axn", "tnt", "space", "star"))
        }
        if (movies.isNotEmpty()) {
            sections.add(
                TvCatalogSection(
                    id = "movies",
                    title = "Filmes & Séries",
                    iconEmoji = "🍿",
                    channels = movies
                )
            )
        }

        // 5. Infantil & Família
        val kids = channels.filter { ch ->
            matchesTheme(ch, setOf("infantil", "desenho", "desenhos", "kids", "animacao", "cartoon", "nick", "disney", "discovery kids", "gloob", "toonavi"))
        }
        if (kids.isNotEmpty()) {
            sections.add(
                TvCatalogSection(
                    id = "kids",
                    title = "Infantil & Desenhos",
                    iconEmoji = "🧸",
                    channels = kids
                )
            )
        }

        // 6. TV Aberta & Variedades
        val openTv = channels.filter { ch ->
            matchesTheme(ch, setOf("aberta", "globo", "sbt", "record", "band", "rede tv", "cultura", "tv brasil", "gazeta"))
        }
        if (openTv.isNotEmpty()) {
            sections.add(
                TvCatalogSection(
                    id = "opentv",
                    title = "TV Aberta & Variedades",
                    iconEmoji = "📺",
                    channels = openTv
                )
            )
        }

        // 7. Catálogos específicos dos Addons
        val channelsByCatalog = channels.groupBy { it.catalogName }
        for ((catalogName, catalogChannels) in channelsByCatalog) {
            if (catalogChannels.isNotEmpty() && catalogName.isNotBlank()) {
                val secId = "catalog_${catalogName.lowercase().replace(" ", "_")}"
                if (sections.none { it.id == secId || it.title.equals(catalogName, ignoreCase = true) }) {
                    sections.add(
                        TvCatalogSection(
                            id = secId,
                            title = catalogName,
                            iconEmoji = "📡",
                            channels = catalogChannels
                        )
                    )
                }
            }
        }

        // 8. Demais canais
        val coveredKeys = sections.flatMap { it.channels }.map { it.stableKey() }.toSet()
        val remainingChannels = channels.filter { !coveredKeys.contains(it.stableKey()) }
        if (remainingChannels.isNotEmpty()) {
            sections.add(
                TvCatalogSection(
                    id = "more_channels",
                    title = "Outros Canais",
                    iconEmoji = "📺",
                    channels = remainingChannels
                )
            )
        }

        return sections
    }

    private fun matchesTheme(channel: TvChannelItem, keywords: Set<String>): Boolean {
        val nameLower = channel.name.lowercase()
        val catLower = channel.catalogName.lowercase()
        val genres = channel.genres.map { it.lowercase() }
        for (kw in keywords) {
            if (nameLower.contains(kw) || catLower.contains(kw) || genres.any { it.contains(kw) }) {
                return true
            }
        }
        return false
    }
}
