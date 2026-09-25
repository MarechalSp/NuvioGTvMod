package com.nuvio.tv.data.local

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.nuvio.tv.core.profile.ProfileManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class TvChannelsDataStore @Inject constructor(
    private val factory: ProfileDataStoreFactory,
    private val profileManager: ProfileManager
) {
    companion object {
        private const val FEATURE = "tv_channels_settings"
    }

    private val selectedAddonUrlsKey = stringSetPreferencesKey("selected_tv_addon_urls")
    private val favoriteChannelKeysKey = stringSetPreferencesKey("favorite_tv_channel_keys")
    private val openFullscreenOnClickKey = booleanPreferencesKey("open_fullscreen_on_click")
    private val enabledEpgSourceIdsKey = stringSetPreferencesKey("enabled_epg_source_ids")
    private val hideAdultChannelsKey = booleanPreferencesKey("hide_adult_channels")
    private val channelAutoplayKey = booleanPreferencesKey("channel_autoplay")

    private fun store(profileId: Int = profileManager.activeProfileId.value) =
        factory.get(profileId, FEATURE)

    val selectedAddonUrls: Flow<Set<String>?> = profileManager.activeProfileId.flatMapLatest { pid ->
        factory.get(pid, FEATURE).data.map { prefs ->
            prefs[selectedAddonUrlsKey]
        }
    }

    val enabledEpgSourceIds: Flow<Set<String>?> = profileManager.activeProfileId.flatMapLatest { pid ->
        factory.get(pid, FEATURE).data.map { prefs ->
            prefs[enabledEpgSourceIdsKey]
        }
    }

    val hideAdultChannels: Flow<Boolean> = profileManager.activeProfileId.flatMapLatest { pid ->
        factory.get(pid, FEATURE).data.map { prefs ->
            prefs[hideAdultChannelsKey] ?: true
        }
    }

    val channelAutoplay: Flow<Boolean> = profileManager.activeProfileId.flatMapLatest { pid ->
        factory.get(pid, FEATURE).data.map { prefs ->
            prefs[channelAutoplayKey] ?: false
        }
    }

    val favoriteChannelKeys: Flow<Set<String>> = profileManager.activeProfileId.flatMapLatest { pid ->
        factory.get(pid, FEATURE).data.map { prefs ->
            prefs[favoriteChannelKeysKey] ?: emptySet()
        }
    }

    val openFullscreenOnClick: Flow<Boolean> = profileManager.activeProfileId.flatMapLatest { pid ->
        factory.get(pid, FEATURE).data.map { prefs ->
            prefs[openFullscreenOnClickKey] ?: false
        }
    }

    suspend fun setSelectedAddonUrls(urls: Set<String>) {
        store().edit { it[selectedAddonUrlsKey] = urls }
    }

    suspend fun setEnabledEpgSourceIds(ids: Set<String>) {
        store().edit { it[enabledEpgSourceIdsKey] = ids }
    }

    suspend fun setHideAdultChannels(hide: Boolean) {
        store().edit { it[hideAdultChannelsKey] = hide }
    }

    suspend fun setChannelAutoplay(enabled: Boolean) {
        store().edit { it[channelAutoplayKey] = enabled }
    }

    suspend fun setFavoriteChannelKeys(keys: Set<String>) {
        store().edit { it[favoriteChannelKeysKey] = keys }
    }

    suspend fun toggleFavorite(channelKey: String) {
        store().edit { prefs ->
            val current = prefs[favoriteChannelKeysKey] ?: emptySet()
            prefs[favoriteChannelKeysKey] = if (current.contains(channelKey)) {
                current - channelKey
            } else {
                current + channelKey
            }
        }
    }

    suspend fun setOpenFullscreenOnClick(enabled: Boolean) {
        store().edit { it[openFullscreenOnClickKey] = enabled }
    }
}
