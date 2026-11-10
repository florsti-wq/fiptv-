package com.arflix.tv.fiptv.mediathek

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arflix.tv.data.api.TmdbApi
import com.arflix.tv.data.repository.ProfileManager
import com.arflix.tv.fiptv.FiptvResume
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Eine Reihe im Netflix-Stil */
data class MediathekRowDef(
    val key: String,
    val title: String,
    val search: String? = null,
    val channel: String? = null,
    val topic: String? = null,
    /** Mindestlänge in Minuten; null = Einstellung „Nur ganze Sendungen“ */
    val minMinutes: Int? = null,
    /** eine Kachel pro Sendung statt jeder Folge */
    val onePerShow: Boolean = true,
)

data class MediathekRow(
    val def: MediathekRowDef,
    val items: List<MediathekItem> = emptyList(),
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val error: String? = null,
)

data class MediathekDetail(
    val item: MediathekItem,
    val episodes: List<MediathekItem> = emptyList(),
    val loading: Boolean = true,
)

data class MediathekUiState(
    val rows: List<MediathekRow> = emptyList(),
    /** Kinderprofil: nur Kinder-Inhalte und Kinder-Sender */
    val kids: Boolean = false,
    val kidsChannels: List<com.arflix.tv.data.model.IptvChannel> = emptyList(),
    val search: String = "",
    /** nur längere Beiträge (ab 20 Min.) – blendet Clips und Trailer aus */
    val longOnly: Boolean = true,
    val searchItems: List<MediathekItem> = emptyList(),
    val searchTotal: Int? = null,
    val searchLoading: Boolean = false,
    val searchError: String? = null,
    val detail: MediathekDetail? = null,
) {
    val canLoadMoreSearch: Boolean get() = !searchLoading && searchError == null && (searchTotal == null || searchItems.size < searchTotal)
}

@HiltViewModel
class MediathekViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val profileManager: ProfileManager,
    private val cloudSyncRepository: com.arflix.tv.data.repository.CloudSyncRepository,
    tmdbApi: TmdbApi,
    private val profileRepository: com.arflix.tv.data.repository.ProfileRepository,
    private val iptvRepository: com.arflix.tv.data.repository.IptvRepository,
) : ViewModel() {
    private val artCache = MediathekArtCache(context, tmdbApi)

    /** fIPTV: Reihen im Kinderprofil – KiKA und bekannte Kindersendungen */
    private val kidsDefs: List<MediathekRowDef> = listOf(
        MediathekRowDef("k_kika", "Neu bei KiKA", channel = "kika", minMinutes = 5),
        MediathekRowDef("k_maus", "Die Sendung mit der Maus", topic = "Die Sendung mit der Maus", minMinutes = 5, onePerShow = false),
        MediathekRowDef("k_loewenzahn", "Löwenzahn", topic = "Löwenzahn", minMinutes = 5, onePerShow = false),
        MediathekRowDef("k_sesam", "Sesamstraße", topic = "Sesamstraße", minMinutes = 5, onePerShow = false),
        MediathekRowDef("k_checker", "Checker Tobi", topic = "Checker Tobi", minMinutes = 5, onePerShow = false),
        MediathekRowDef("k_wissen", "Wissen macht Ah!", topic = "Wissen macht Ah!", minMinutes = 5, onePerShow = false),
        MediathekRowDef("k_logo", "logo! Nachrichten für Kinder", topic = "logo!", minMinutes = 3, onePerShow = false),
    )

    private val adultDefs: List<MediathekRowDef> = buildList {
        add(MediathekRowDef("new", "Neu in den Mediatheken"))
        add(MediathekRowDef("long", "Filme & lange Dokus", minMinutes = 80))
        add(MediathekRowDef("tatort", "Tatort", topic = "Tatort", minMinutes = 60, onePerShow = false))
        add(MediathekRowDef("polizeiruf", "Polizeiruf 110", topic = "Polizeiruf 110", minMinutes = 60, onePerShow = false))
        add(MediathekRowDef("terrax", "Terra X", topic = "Terra X", onePerShow = false))
        add(MediathekRowDef("doku", "Dokus & Reportagen", search = "#doku"))
        add(MediathekRowDef("kids", "Für Kinder", channel = "kika", minMinutes = 10))
        MediathekRepository.channels.filter { it.id != "kika" }.forEach { ch ->
            add(MediathekRowDef("ch_${ch.id}", "Neu bei ${ch.label}", channel = ch.id))
        }
    }

    private var defs: List<MediathekRowDef> = emptyList()
    private val _state = MutableStateFlow(MediathekUiState())
    val state: StateFlow<MediathekUiState> = _state.asStateFlow()
    private val rowJobs = mutableMapOf<String, Job>()
    private var searchJob: Job? = null
    private var detailJob: Job? = null

    init {
        viewModelScope.launch {
            val kids = runCatching { profileRepository.activeProfile.first()?.isKidsProfile == true }.getOrDefault(false)
            defs = if (kids) kidsDefs else adultDefs
            _state.update { it.copy(kids = kids, rows = defs.map { d -> MediathekRow(d) }) }
            // die ersten Reihen sofort, der Rest wenn sie ins Bild kommen
            defs.take(3).forEach { ensureRow(it.key) }
            if (kids) loadKidsChannels()
        }
    }

    /** Kinder-Sender aus der eigenen IPTV-Liste (KiKA, Super RTL, Toggo, Disney, Nick …) */
    private suspend fun loadKidsChannels() {
        val channels = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val ids = LinkedHashSet<String>()
                iptvRepository.visitStoredChannelLabels(null) { id, name, group ->
                    if (ids.size < 400 && com.arflix.tv.fiptv.kids.FiptvKids.isKidsChannel(name, group)) ids.add(id)
                }
                ids.chunked(200).flatMap { iptvRepository.pagedChannelsByIds(it) }
                    .distinctBy { it.streamUrl }
                    .distinctBy { it.name.lowercase().replace(Regex("\\s*(hd|fhd|uhd|4k|sd|\\|.*)$"), "").trim() }
                    .sortedWith(compareBy({ !it.name.contains("kika", ignoreCase = true) }, { it.name.lowercase() }))
                    .take(40)
            }.getOrDefault(emptyList())
        }
        _state.update { it.copy(kidsChannels = channels) }
    }

    private fun minDuration(def: MediathekRowDef, longOnly: Boolean): Int? =
        def.minMinutes?.let { it * 60 } ?: if (longOnly) 20 * 60 else null

    fun ensureRow(key: String) {
        val row = _state.value.rows.firstOrNull { it.def.key == key } ?: return
        if (row.loaded || row.loading || rowJobs[key]?.isActive == true) return
        updateRow(key) { it.copy(loading = true, error = null) }
        rowJobs[key] = viewModelScope.launch {
            try {
                val def = row.def
                val page = MediathekRepository.query(
                    search = def.search,
                    channel = def.channel,
                    topic = def.topic,
                    minDurationSec = minDuration(def, _state.value.longOnly),
                    size = if (def.onePerShow) 60 else 30,
                )
                val items = when {
                    def.onePerShow -> page.items.distinctBy { MediathekRepository.showKey(it) }
                    // Sendungsreihen: nur genau diese Sendung (z. B. nicht „Maus-Spezial“ fremder Themen)
                    def.topic != null -> page.items.filter { it.topic.startsWith(def.topic, ignoreCase = true) }
                    else -> page.items
                }
                updateRow(key) { it.copy(items = items.take(30), loading = false, loaded = true) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                updateRow(key) { it.copy(loading = false, loaded = true, error = e.message ?: "Mediathek nicht erreichbar") }
            }
        }
    }

    fun retryRow(key: String) {
        updateRow(key) { it.copy(loaded = false, error = null) }
        ensureRow(key)
    }

    private fun updateRow(key: String, change: (MediathekRow) -> MediathekRow) {
        _state.update { s -> s.copy(rows = s.rows.map { if (it.def.key == key) change(it) else it }) }
    }

    fun toggleLongOnly() {
        rowJobs.values.forEach { it.cancel() }
        rowJobs.clear()
        _state.update { s -> s.copy(longOnly = !s.longOnly, rows = defs.map { MediathekRow(it) }) }
        defs.take(3).forEach { ensureRow(it.key) }
        if (_state.value.search.isNotBlank()) reloadSearch()
    }

    // ---------- Suche ----------
    fun setSearch(text: String) {
        _state.update { it.copy(search = text.trim()) }
        reloadSearch()
    }

    private fun reloadSearch() {
        searchJob?.cancel()
        _state.update { it.copy(searchItems = emptyList(), searchTotal = null, searchError = null) }
        if (_state.value.search.isNotBlank()) loadSearch(append = false)
    }

    fun loadMoreSearch() {
        if (!_state.value.canLoadMoreSearch || searchJob?.isActive == true) return
        loadSearch(append = true)
    }

    private fun loadSearch(append: Boolean) {
        val s = _state.value
        _state.update { it.copy(searchLoading = true, searchError = null) }
        searchJob = viewModelScope.launch {
            try {
                val page = MediathekRepository.query(
                    search = s.search,
                    // Kinderprofil: nur KiKA durchsuchen
                    channel = if (s.kids) "kika" else null,
                    minDurationSec = if (s.longOnly) 20 * 60 else null,
                    offset = if (append) s.searchItems.size else 0,
                )
                _state.update {
                    it.copy(
                        searchItems = (if (append) it.searchItems + page.items else page.items).distinctBy { i -> i.id },
                        searchTotal = page.total,
                        searchLoading = false,
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(searchLoading = false, searchError = e.message ?: "Mediathek nicht erreichbar") }
            }
        }
    }

    // ---------- Detailseite ----------
    fun openDetail(item: MediathekItem) {
        detailJob?.cancel()
        _state.update { it.copy(detail = MediathekDetail(item)) }
        if (MediathekRepository.isContainerTopic(item.topic)) {
            // Film o. Ä.: keine weiteren Folgen
            _state.update { it.copy(detail = it.detail?.copy(loading = false)) }
            return
        }
        detailJob = viewModelScope.launch {
            val episodes = runCatching {
                MediathekRepository.query(
                    search = null,
                    channel = MediathekRepository.channels.firstOrNull { item.channel.contains(it.id, ignoreCase = true) }?.id,
                    topic = item.topic,
                    minDurationSec = null,
                    size = 50,
                ).items.filter { it.topic.equals(item.topic, ignoreCase = true) }.distinctBy { it.id }
            }.getOrDefault(emptyList())
            _state.update { s ->
                if (s.detail?.item?.id != item.id) s
                else s.copy(detail = s.detail.copy(episodes = episodes, loading = false))
            }
        }
    }

    fun closeDetail() {
        detailJob?.cancel()
        _state.update { it.copy(detail = null) }
    }

    // ---------- Bilder ----------
    fun cachedArt(item: MediathekItem): MediathekArt? = artCache.cached(item)
    suspend fun art(item: MediathekItem): MediathekArt = artCache.lookup(item)

    // ---------- fIPTV: Weiterschauen ----------
    private val profileId: String get() = profileManager.getProfileIdSync()

    /** Sendung anmelden; liefert Eintrag und ggf. Startposition. */
    fun prepare(item: MediathekItem, fromStart: Boolean = false): Pair<FiptvResume.Entry, Long?>? {
        val url = item.bestVideo ?: return null
        val sender = MediathekRepository.channelLabel(item.channel)
        val entry = FiptvResume.Entry(
            url = url, kind = FiptvResume.Kind.MEDIATHEK, title = item.title,
            subtitle = listOf(sender, item.topic.takeIf { it != item.title }.orEmpty()).filter { it.isNotBlank() }.joinToString(" · "),
            image = artCache.cached(item)?.tile,
        )
        FiptvResume.register(context, profileId, entry)
        // fIPTV: nächste (neuere) Folgen derselben Sendung für „Nächste Folge automatisch“
        val detail = _state.value.detail
        val following = if (detail != null && !MediathekRepository.isContainerTopic(item.topic)) {
            detail.episodes
                .filter { it.topic.equals(item.topic, ignoreCase = true) && it.startMs > item.startMs && it.id != item.id }
                .sortedBy { it.startMs }
                .take(20)
                .mapNotNull { n ->
                    n.bestVideo?.let { u ->
                        FiptvResume.Entry(
                            url = u, kind = FiptvResume.Kind.MEDIATHEK, title = n.title,
                            subtitle = listOf(sender, n.topic.takeIf { it != n.title }.orEmpty()).filter { it.isNotBlank() }.joinToString(" · "),
                            image = artCache.cached(n)?.tile,
                        )
                    }
                }
        } else emptyList()
        com.arflix.tv.fiptv.FiptvNextUp.set(profileId, entry, following)
        return entry to (if (fromStart) null else FiptvResume.startPosition(context, profileId, url))
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
