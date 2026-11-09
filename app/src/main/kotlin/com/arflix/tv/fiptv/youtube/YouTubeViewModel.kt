package com.arflix.tv.fiptv.youtube

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arflix.tv.data.repository.ProfileManager
import com.arflix.tv.util.settingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** Was gerade angezeigt wird. */
sealed interface YtView {
    data object Trends : YtView
    data object MyChannels : YtView
    data class Search(val query: String) : YtView
    data class Channel(val id: String, val title: String) : YtView
}

data class YouTubeUiState(
    val view: YtView = YtView.Trends,
    val videos: List<YtVideo> = emptyList(),
    /** Kanäle aus der Suche */
    val foundChannels: List<YtChannel> = emptyList(),
    /** Gemerkte Kanäle des aktiven Profils */
    val savedChannels: List<YtChannel> = emptyList(),
    /** Angaben zum gerade geöffneten Kanal (Name, Bild) */
    val openChannel: YtChannel? = null,
    val nextPageToken: String? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val configured: Boolean = YouTubeRepository.isConfigured,
) {
    val canLoadMore: Boolean
        get() = !loading && error == null && nextPageToken != null && view !is YtView.MyChannels
    val isOpenChannelSaved: Boolean
        get() = (view as? YtView.Channel)?.id?.let { id -> savedChannels.any { it.id == id } } == true
}

/**
 * fIPTV: YouTube-Bereich. Gemerkte Kanäle liegen je Profil in den Einstellungen
 * („profile_<id>_fiptv_yt_channels“, JSON-Liste).
 */
@HiltViewModel
class YouTubeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val profileManager: ProfileManager,
) : ViewModel() {
    private val _state = MutableStateFlow(YouTubeUiState())
    val state: StateFlow<YouTubeUiState> = _state.asStateFlow()
    private var job: Job? = null

    private fun channelsKey() = profileManager.profileStringKey("fiptv_yt_channels")

    init {
        viewModelScope.launch {
            val saved = readSavedChannels()
            _state.update { it.copy(savedChannels = saved) }
            // Wer schon Kanäle gemerkt hat, sieht zuerst diese
            show(if (saved.isNotEmpty()) YtView.MyChannels else YtView.Trends)
        }
    }

    fun show(view: YtView) {
        if (!_state.value.configured) return
        job?.cancel()
        _state.update {
            it.copy(
                view = view, videos = emptyList(), foundChannels = emptyList(),
                openChannel = if (view is YtView.Channel) YtChannel(view.id, view.title, null) else null,
                nextPageToken = null, error = null,
            )
        }
        load(append = false)
    }

    fun search(query: String) {
        val q = query.trim()
        show(if (q.isBlank()) YtView.Trends else YtView.Search(q))
    }

    fun openChannel(id: String, title: String) = show(YtView.Channel(id, title))

    fun loadMore() {
        if (!_state.value.canLoadMore || job?.isActive == true) return
        load(append = true)
    }

    fun retry() = show(_state.value.view)

    fun toggleSaveOpenChannel() {
        val s = _state.value
        val view = s.view as? YtView.Channel ?: return
        val channel = s.openChannel ?: YtChannel(view.id, view.title, null)
        val updated = if (s.isOpenChannelSaved) {
            s.savedChannels.filterNot { it.id == view.id }
        } else {
            s.savedChannels + channel
        }
        _state.update { it.copy(savedChannels = updated) }
        viewModelScope.launch { writeSavedChannels(updated) }
    }

    private fun load(append: Boolean) {
        val s = _state.value
        _state.update { it.copy(loading = true, error = null) }
        job = viewModelScope.launch {
            try {
                when (val view = s.view) {
                    YtView.Trends -> {
                        val page = YouTubeRepository.trending(if (append) s.nextPageToken else null)
                        _state.update { it.copy(videos = merge(it.videos, page.items, append), nextPageToken = page.nextPageToken) }
                    }
                    is YtView.Search -> {
                        val (page, channels) = YouTubeRepository.search(view.query, if (append) s.nextPageToken else null)
                        _state.update {
                            it.copy(
                                videos = merge(it.videos, page.items, append),
                                foundChannels = if (append) it.foundChannels else channels,
                                nextPageToken = page.nextPageToken,
                            )
                        }
                    }
                    is YtView.Channel -> {
                        val (channel, page) = YouTubeRepository.channelVideos(view.id, pageToken = if (append) s.nextPageToken else null)
                        _state.update {
                            it.copy(
                                videos = merge(it.videos, page.items, append),
                                openChannel = channel ?: it.openChannel,
                                nextPageToken = page.nextPageToken,
                            )
                        }
                        // Bild/Name eines gemerkten Kanals aktualisieren
                        if (channel != null && _state.value.savedChannels.any { it.id == channel.id && it != channel }) {
                            val updated = _state.value.savedChannels.map { if (it.id == channel.id) channel else it }
                            _state.update { it.copy(savedChannels = updated) }
                            writeSavedChannels(updated)
                        }
                    }
                    YtView.MyChannels -> {
                        // Je Kanal die neuesten 12 Videos, zusammen nach Datum sortiert
                        val all = s.savedChannels.map { ch ->
                            async { runCatching { YouTubeRepository.channelVideos(ch.id, max = 12).second.items }.getOrDefault(emptyList()) }
                        }.awaitAll().flatten()
                        _state.update {
                            it.copy(videos = all.distinctBy { v -> v.id }.sortedByDescending { v -> v.publishedAt }, nextPageToken = null)
                        }
                    }
                }
                _state.update { it.copy(loading = false) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "YouTube nicht erreichbar") }
            }
        }
    }

    private fun merge(old: List<YtVideo>, new: List<YtVideo>, append: Boolean) =
        if (append) (old + new).distinctBy { it.id } else new

    private suspend fun readSavedChannels(): List<YtChannel> = runCatching {
        val raw = context.settingsDataStore.data.first()[channelsKey()] ?: return emptyList()
        val arr = JSONArray(raw)
        (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { o ->
                YtChannel(o.optString("id"), o.optString("title"), o.optString("thumb").takeIf { it.isNotBlank() })
            }
        }.filter { it.id.isNotBlank() }
    }.getOrDefault(emptyList())

    private suspend fun writeSavedChannels(list: List<YtChannel>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("id", it.id).put("title", it.title).put("thumb", it.thumbnail ?: "")) }
        context.settingsDataStore.edit { it[channelsKey()] = arr.toString() }
    }
}
