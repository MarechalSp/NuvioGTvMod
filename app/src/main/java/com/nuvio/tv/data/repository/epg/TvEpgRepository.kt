package com.nuvio.tv.data.repository.epg

import android.content.Context
import android.util.Log
import com.nuvio.tv.domain.model.TvChannelItem
import com.nuvio.tv.data.local.TvChannelsDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Singleton

data class TvEpgUiState(
    val isLoading: Boolean = false,
    val lastSyncEpochMs: Long = 0L,
    val totalProgramsLoaded: Int = 0,
    val errorMessage: String? = null,
    val epgByChannelKey: Map<String, ChannelEpgInfo> = emptyMap(),
    val epgSources: List<EpgSourceConfig> = TvEpgRepository.DEFAULT_EPG_SOURCES,
)

@Singleton
class TvEpgRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val tvChannelsDataStore: TvChannelsDataStore
) {
    companion object {
        private const val TAG = "TvEpgRepo"

        val DEFAULT_EPG_SOURCES = listOf(
            EpgSourceConfig(
                id = "default_epg_br1",
                name = "Brasil Principal (BR1)",
                url = "https://epgshare01.online/epgshare01/epg_ripper_BR1.xml.gz",
                isEnabled = true,
                isDefault = true,
            ),
            EpgSourceConfig(
                id = "default_epg_br2",
                name = "Brasil Aberta & Regionais (BR2)",
                url = "https://epgshare01.online/epgshare01/epg_ripper_BR2.xml.gz",
                isEnabled = true,
                isDefault = true,
            ),
            EpgSourceConfig(
                id = "default_epg_pt1",
                name = "Portugal & Europa (PT1)",
                url = "https://epgshare01.online/epgshare01/epg_ripper_PT1.xml.gz",
                isEnabled = true,
                isDefault = true,
            ),
            EpgSourceConfig(
                id = "default_epg_us1",
                name = "Estados Unidos / USA (US1)",
                url = "https://epgshare01.online/epgshare01/epg_ripper_US1.xml.gz",
                isEnabled = true,
                isDefault = true,
            )
        )

        private val ALIASES = mapOf(
            "h2" to "history2",
            "premiere1" to "premiereclubes",
            "premiere" to "premiereclubes",
            "discoveryhh" to "discoveryhomehealth",
            "universaltv" to "universal",
            "sonychannel" to "sony",
            "historychannel" to "history",
            "espn" to "espnbrasil",
            "espn1" to "espnbrasil",
            "combate" to "canalcombate",
            "telecinepremium" to "telecinepremium",
            "telecinepipoca" to "telecinepipoca",
            "telecineaction" to "telecineaction",
            "usanetwork" to "usa",
            "paramountnetwork" to "paramount",
            "disneychannel" to "disney",
            "cartoonnetwork" to "cartoon",
        )

        private val QUALITY_TOKENS = setOf("hd", "fhd", "uhd", "4k", "sd", "hdtv", "fullhd")
        private val FILLER_TOKENS = setOf("canal", "channel", "tv", "rede", "and", "e", "pt", "br", "aovivo", "live")
        private val PARENS_REGEX = Regex("""\([^)]*\)""")
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _uiState = MutableStateFlow(TvEpgUiState())
    val uiState: StateFlow<TvEpgUiState> = _uiState.asStateFlow()

    private val cachedChannels = mutableMapOf<String, XmlTvChannel>()
    private val cachedProgramsByChannelId = mutableMapOf<String, List<EpgProgram>>()
    private val keyToChannelIds = mutableMapOf<String, MutableList<String>>()
    private val channelMatchCache = mutableMapOf<String, String?>()

    private var syncJob: Job? = null
    private var isInitialized = false

    private val epgHttpClient: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(40, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    fun initialize() {
        if (isInitialized) return
        isInitialized = true
        scope.launch {
            val initialIds = tvChannelsDataStore.enabledEpgSourceIds.firstOrNull()
            val initialSet = initialIds ?: DEFAULT_EPG_SOURCES.map { it.id }.toSet()
            _uiState.update { state ->
                state.copy(
                    epgSources = DEFAULT_EPG_SOURCES.map { src ->
                        src.copy(isEnabled = initialSet.contains(src.id))
                    }
                )
            }
            syncEpg(forceRefresh = false)

            tvChannelsDataStore.enabledEpgSourceIds.collect { savedEnabledIds ->
                val enabledSet = savedEnabledIds ?: DEFAULT_EPG_SOURCES.map { it.id }.toSet()
                _uiState.update { state ->
                    state.copy(
                        epgSources = DEFAULT_EPG_SOURCES.map { src ->
                            src.copy(isEnabled = enabledSet.contains(src.id))
                        }
                    )
                }
            }
        }
    }

    fun syncEpg(forceRefresh: Boolean = false) {
        syncJob?.cancel()
        syncJob = scope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val activeSources = _uiState.value.epgSources.filter { it.isEnabled }
            var totalPrograms = 0

            synchronized(this@TvEpgRepository) {
                cachedChannels.clear()
                cachedProgramsByChannelId.clear()
                keyToChannelIds.clear()
                channelMatchCache.clear()
            }

            if (activeSources.isEmpty()) {
                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        lastSyncEpochMs = System.currentTimeMillis(),
                        totalProgramsLoaded = 0,
                        errorMessage = "Nenhuma lista EPG selecionada."
                    )
                }
                return@launch
            }

            for (source in activeSources) {
                val xml = fetchEpgXml(source.id, source.url, forceRefresh)
                if (!xml.isNullOrBlank()) {
                    val parseResult = XmlTvParser.parse(xml)
                    synchronized(this@TvEpgRepository) {
                        for ((id, channel) in parseResult.channels) {
                            cachedChannels[id] = channel
                            indexChannel(id, channel)
                        }
                        for ((channelId, programs) in parseResult.programsByChannelId) {
                            cachedProgramsByChannelId[channelId] = programs
                            totalPrograms += programs.size
                        }
                    }
                }
            }

            channelMatchCache.clear()
            _uiState.update { current ->
                current.copy(
                    isLoading = false,
                    lastSyncEpochMs = System.currentTimeMillis(),
                    totalProgramsLoaded = totalPrograms,
                    errorMessage = if (totalPrograms == 0) {
                        "Nenhum programa EPG carregado."
                    } else null
                )
            }
        }
    }

    fun toggleEpgSource(sourceId: String) {
        val currentSources = _uiState.value.epgSources
        val target = currentSources.firstOrNull { it.id == sourceId } ?: return
        val newEnabled = !target.isEnabled
        val updatedSources = currentSources.map {
            if (it.id == sourceId) it.copy(isEnabled = newEnabled) else it
        }
        _uiState.update { it.copy(epgSources = updatedSources) }
        val enabledIds = updatedSources.filter { it.isEnabled }.map { it.id }.toSet()
        scope.launch {
            tvChannelsDataStore.setEnabledEpgSourceIds(enabledIds)
            syncEpg(forceRefresh = false)
        }
    }

    fun selectAllEpgSources() {
        val updatedSources = _uiState.value.epgSources.map { it.copy(isEnabled = true) }
        _uiState.update { it.copy(epgSources = updatedSources) }
        val enabledIds = updatedSources.map { it.id }.toSet()
        scope.launch {
            tvChannelsDataStore.setEnabledEpgSourceIds(enabledIds)
            syncEpg(forceRefresh = false)
        }
    }

    fun deselectAllEpgSources() {
        val updatedSources = _uiState.value.epgSources.map { it.copy(isEnabled = false) }
        _uiState.update { it.copy(epgSources = updatedSources) }
        scope.launch {
            tvChannelsDataStore.setEnabledEpgSourceIds(emptySet())
            syncEpg(forceRefresh = false)
        }
    }

    private fun indexChannel(id: String, channel: XmlTvChannel) {
        val keys = mutableSetOf<String>()
        val cleanId = normalizeText(id.substringBefore('.'))
        if (cleanId.isNotBlank()) keys.add(cleanId)

        for (dn in channel.displayNames) {
            val norm = normalizeText(dn)
            if (norm.isNotBlank()) keys.add(norm)
        }

        for (k in keys) {
            keyToChannelIds.getOrPut(k) { mutableListOf() }.add(id)
        }
    }

    fun findEpgForChannel(channel: TvChannelItem): ChannelEpgInfo? {
        val key = channel.stableKey()
        val xmlTvId = synchronized(this) {
            if (channelMatchCache.containsKey(key)) {
                channelMatchCache[key]
            } else {
                val matched = matchChannelId(channel.name)
                channelMatchCache[key] = matched
                matched
            }
        } ?: return null

        val programs = cachedProgramsByChannelId[xmlTvId].orEmpty()
        if (programs.isEmpty()) return null

        val now = System.currentTimeMillis()
        val nowProg = programs.firstOrNull { it.isLive(now) }
        val nextProg = programs.firstOrNull { it.startEpochMs >= (nowProg?.endEpochMs ?: now) }

        return ChannelEpgInfo(
            channelKey = key,
            channelId = xmlTvId,
            nowProgram = nowProg,
            nextProgram = nextProg,
            todayPrograms = programs
        )
    }

    private fun matchChannelId(channelName: String): String? {
        val norm = normalizeText(channelName)
        if (norm.isBlank()) return null

        // 1. Match exato
        keyToChannelIds[norm]?.firstOrNull()?.let { return it }

        // 2. Apelidos (aliases)
        ALIASES[norm]?.let { aliasKey ->
            keyToChannelIds[aliasKey]?.firstOrNull()?.let { return it }
        }

        // 3. Match por prefixo / contenção
        for ((k, ids) in keyToChannelIds) {
            if (k.length >= 4 && (norm.contains(k) || k.contains(norm))) {
                return ids.firstOrNull()
            }
        }

        return null
    }

    private fun normalizeText(text: String): String {
        var clean = text.lowercase()
        clean = PARENS_REGEX.replace(clean, " ")
        clean = clean.replace(Regex("""[^a-z0-9]"""), " ")
        val tokens = clean.split(Regex("""\s+""")).filter { it.isNotBlank() }
        val filtered = tokens.filter { token ->
            token !in QUALITY_TOKENS && token !in FILLER_TOKENS
        }
        return filtered.joinToString("")
    }

    private suspend fun fetchEpgXml(sourceId: String, url: String, forceRefresh: Boolean): String? =
        withContext(Dispatchers.IO) {
            val sanitizedId = sourceId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val cacheDir = File(context.cacheDir, "nuvio_epg_cache").apply { mkdirs() }
            val cacheFile = File(cacheDir, "epg_$sanitizedId.xml")

            // 1. Cache local válido (24h)
            if (!forceRefresh && cacheFile.exists()) {
                val age = System.currentTimeMillis() - cacheFile.lastModified()
                if (age in 0 until (24 * 3600_000L)) {
                    return@withContext runCatching { cacheFile.readText(StandardCharsets.UTF_8) }.getOrNull()
                }
            }

            // 2. Download
            runCatching {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Nuvio/1.0 (Google TV)")
                    .build()

                epgHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    val body = response.body ?: return@withContext null
                    val buffered = BufferedInputStream(body.byteStream(), 64 * 1024)

                    buffered.mark(4)
                    val b1 = buffered.read()
                    val b2 = buffered.read()
                    buffered.reset()

                    val isGzip = (b1 == 0x1F && b2 == 0x8B) || url.lowercase().endsWith(".gz")
                    val inputStream: InputStream = if (isGzip) GZIPInputStream(buffered, 64 * 1024) else buffered

                    val text = InputStreamReader(inputStream, StandardCharsets.UTF_8).use { it.readText() }
                    if (text.isNotBlank()) {
                        runCatching {
                            val temp = File(cacheDir, "epg_${sanitizedId}_temp.xml")
                            temp.writeText(text, StandardCharsets.UTF_8)
                            temp.renameTo(cacheFile)
                        }
                    }
                    text
                }
            }.getOrNull()
        }
}
