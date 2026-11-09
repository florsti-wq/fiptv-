package com.arflix.tv.fiptv.mediathek

import android.content.Context
import androidx.lifecycle.ViewModel
import com.arflix.tv.data.repository.ProfileManager
import com.arflix.tv.fiptv.FiptvResume
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MediathekUiState(
    /** null = alle Sender */
    val channel: String? = null,
    val search: String = "",
    /** nur längere Beiträge (ab 20 Min.) – blendet Clips und Trailer aus */
    val longOnly: Boolean = true,
    val items: List<MediathekItem> = emptyList(),
    val total: Int? = null,
    val loading: Boolean = false,
    val error: String? = null,
) {
    val canLoadMore: Boolean get() = !loading && error == null && (total == null || items.size < total)
}

@HiltViewModel
class MediathekViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val profileManager: ProfileManager,
    private val cloudSyncRepository: com.arflix.tv.data.repository.CloudSyncRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(MediathekUiState())
    val state: StateFlow<MediathekUiState> = _state.asStateFlow()
    private var job: Job? = null

    init {
        reload()
    }

    fun selectChannel(id: String?) {
        if (_state.value.channel == id) return
        _state.update { it.copy(channel = id) }
        reload()
    }

    fun setSearch(text: String) {
        _state.update { it.copy(search = text.trim()) }
        reload()
    }

    fun toggleLongOnly() {
        _state.update { it.copy(longOnly = !it.longOnly) }
        reload()
    }

    fun reload() {
        job?.cancel()
        _state.update { it.copy(items = emptyList(), total = null, error = null) }
        load(append = false)
    }

    fun loadMore() {
        if (!_state.value.canLoadMore || job?.isActive == true) return
        load(append = true)
    }

    private fun load(append: Boolean) {
        val s = _state.value
        _state.update { it.copy(loading = true, error = null) }
        job = viewModelScope.launch {
            try {
                val page = MediathekRepository.query(
                    search = s.search.ifBlank { null },
                    channel = s.channel,
                    minDurationSec = if (s.longOnly) 20 * 60 else null,
                    offset = if (append) s.items.size else 0,
                )
                _state.update {
                    it.copy(
                        items = if (append) it.items + page.items else page.items,
                        total = page.total,
                        loading = false,
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "Mediathek nicht erreichbar") }
            }
        }
    }

    // ---------- fIPTV: Weiterschauen ----------
    private val profileId: String get() = profileManager.getProfileIdSync()

    /** Sendung anmelden; liefert Eintrag und ggf. Startposition. */
    fun prepare(item: MediathekItem): Pair<FiptvResume.Entry, Long?>? {
        val url = item.bestVideo ?: return null
        val sender = MediathekRepository.channels.firstOrNull { item.channel.contains(it.id, ignoreCase = true) }?.label ?: item.channel
        val entry = FiptvResume.Entry(
            url = url, kind = FiptvResume.Kind.MEDIATHEK, title = item.title,
            subtitle = listOf(sender, item.topic.takeIf { it != item.title }.orEmpty()).filter { it.isNotBlank() }.joinToString(" · "),
        )
        FiptvResume.register(context, profileId, entry)
        return entry to FiptvResume.startPosition(context, profileId, url)
    }

    fun resumeEntries(): List<FiptvResume.Entry> =
        FiptvResume.continueWatching(context, profileId, setOf(FiptvResume.Kind.MEDIATHEK))

    fun progressOf(item: MediathekItem): FiptvResume.Entry? =
        item.bestVideo?.let { FiptvResume.entry(context, profileId, it) }

    fun removeResume(entry: FiptvResume.Entry) {
        FiptvResume.remove(context, profileId, entry.url)
        viewModelScope.launch { runCatching { cloudSyncRepository.pushToCloud() } }
    }
}
