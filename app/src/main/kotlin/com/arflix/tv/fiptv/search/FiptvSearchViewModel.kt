package com.arflix.tv.fiptv.search

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arflix.tv.data.model.IptvChannel
import com.arflix.tv.data.model.IptvProgram
import com.arflix.tv.data.repository.IptvRepository
import com.arflix.tv.fiptv.FiptvResume
import com.arflix.tv.fiptv.mediathek.MediathekItem
import com.arflix.tv.fiptv.mediathek.MediathekRepository
import com.arflix.tv.fiptv.vod.VodMovie
import com.arflix.tv.fiptv.vod.VodSeries
import com.arflix.tv.fiptv.vod.XtreamVodClient
import com.arflix.tv.fiptv.youtube.YouTubeRepository
import com.arflix.tv.fiptv.youtube.YtVideo
import com.arflix.tv.data.repository.ProfileManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.Normalizer
import javax.inject.Inject

/** Treffer im TV-Programm */
data class EpgHit(val channel: IptvChannel, val program: IptvProgram, val live: Boolean)

/** Ergebnis je Quelle: null = lädt, sonst Liste (ggf. leer); error = Hinweis */
data class Section<T>(val items: List<T>? = null, val error: String? = null)

data class FiptvSearchState(
    val query: String = "",
    val epg: Section<EpgHit> = Section(emptyList()),
    val channels: Section<IptvChannel> = Section(emptyList()),
    val movies: Section<VodMovie> = Section(emptyList()),
    val series: Section<VodSeries> = Section(emptyList()),
    val mediathek: Section<MediathekItem> = Section(emptyList()),
    val youtube: Section<YtVideo> = Section(emptyList()),
)

/** Gemerkte Ziele für die Bereiche (Serie öffnen, Sendung öffnen, Video abspielen). */
object FiptvSearchBridge {
    @Volatile var series: VodSeries? = null
    @Volatile var mediathek: MediathekItem? = null
    @Volatile var youtube: YtVideo? = null
    fun takeSeries() = series.also { series = null }
    fun takeMediathek() = mediathek.also { mediathek = null }
    fun takeYoutube() = youtube.also { youtube = null }
}

/** Anbieter-Katalog (alle Filme/Serien) einmal laden und im Speicher halten. */
private object VodIndex {
    var at = 0L
    var key = ""
    var movies: List<VodMovie> = emptyList()
    var series: List<VodSeries> = emptyList()
    var client: XtreamVodClient? = null
}

fun normalizeForSearch(s: String): String =
    Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        .replace("ß", "ss").replace(Regex("[^a-z0-9]+"), " ").trim()

@HiltViewModel
class FiptvSearchViewModel @Inject constructor(
    private val iptvRepository: IptvRepository,
    @ApplicationContext private val context: Context,
    private val profileManager: ProfileManager,
) : ViewModel() {
    private val _state = MutableStateFlow(FiptvSearchState())
    val state: StateFlow<FiptvSearchState> = _state.asStateFlow()
    private var job: Job? = null

    fun setQuery(q: String, immediate: Boolean = false) {
        if (q == _state.value.query && !immediate) return
        _state.update { it.copy(query = q) }
        job?.cancel()
        val query = q.trim()
        if (query.length < 2) {
            _state.update { FiptvSearchState(query = q) }
            return
        }
        job = viewModelScope.launch {
            if (!immediate) delay(650)
            _state.update {
                it.copy(epg = Section(), channels = Section(), movies = Section(), series = Section(), mediathek = Section(),
                    youtube = if (YouTubeRepository.isConfigured) Section() else Section(emptyList()))
            }
            launch { searchLive(query) }
            launch { searchVod(query) }
            launch { searchMediathek(query) }
            if (YouTubeRepository.isConfigured) launch { searchYouTube(query) }
        }
    }

    private fun matches(text: String?, words: List<String>): Boolean {
        if (text.isNullOrBlank()) return false
        val n = normalizeForSearch(text)
        return words.all { n.contains(it) }
    }

    private suspend fun searchLive(query: String) {
        val words = normalizeForSearch(query).split(' ').filter { it.isNotBlank() }
        try {
            val snapshot = iptvRepository.getCachedSnapshotOrNull()
            if (snapshot == null) {
                _state.update { it.copy(epg = Section(emptyList(), "TV-Programm noch nicht geladen – einmal Live-TV öffnen"), channels = Section(emptyList())) }
                return
            }
            val now = System.currentTimeMillis()
            val byId = snapshot.channels.associateBy { it.id }
            val hits = mutableListOf<EpgHit>()
            for ((id, nn) in snapshot.nowNext) {
                val ch = byId[id] ?: continue
                val programs = buildList {
                    nn.now?.let(::add); nn.next?.let(::add); nn.later?.let(::add); addAll(nn.upcoming)
                }.distinctBy { it.startUtcMillis }
                for (p in programs) {
                    if (p.endUtcMillis <= now) continue
                    if (matches(p.title, words)) hits += EpgHit(ch, p, p.isLive(now))
                }
                if (hits.size > 400) break
            }
            val favs = snapshot.favoriteChannels.toSet()
            val sorted = hits.sortedWith(compareBy<EpgHit>({ !it.live }, { it.program.startUtcMillis }, { it.channel.id !in favs }))
                .distinctBy { "${it.program.title}|${it.program.startUtcMillis}|${it.channel.name}" }
                .take(60)
            val channels = snapshot.channels.filter { matches(it.name, words) }
                .sortedWith(compareBy({ it.id !in favs }, { it.name.length }))
                .distinctBy { it.name.lowercase() }
                .take(40)
            _state.update { it.copy(epg = Section(sorted), channels = Section(channels)) }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(epg = Section(emptyList(), e.message), channels = Section(emptyList())) }
        }
    }

    private suspend fun searchVod(query: String) {
        val words = normalizeForSearch(query).split(' ').filter { it.isNotBlank() }
        try {
            val account = iptvRepository.fiptvXtreamAccounts().firstOrNull()
            if (account == null) {
                _state.update { it.copy(movies = Section(emptyList()), series = Section(emptyList())) }
                return
            }
            val key = "${account.sourceId}|${account.username}|${account.baseUrl}"
            if (VodIndex.key != key || System.currentTimeMillis() - VodIndex.at > 6 * 60 * 60_000L || VodIndex.movies.isEmpty()) {
                val c = XtreamVodClient(account)
                val movies = runCatching { c.movies(null) }.getOrDefault(emptyList())
                val series = runCatching { c.series(null) }.getOrDefault(emptyList())
                VodIndex.movies = movies; VodIndex.series = series; VodIndex.client = c
                VodIndex.key = key; VodIndex.at = System.currentTimeMillis()
            }
            val movies = VodIndex.movies.filter { matches(it.name, words) }.sortedByDescending { it.added }.take(60)
            val series = VodIndex.series.filter { matches(it.name, words) }.take(60)
            _state.update { it.copy(movies = Section(movies), series = Section(series)) }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(movies = Section(emptyList(), e.message), series = Section(emptyList())) }
        }
    }

    private suspend fun searchMediathek(query: String) {
        try {
            val page = MediathekRepository.query(search = query, channel = null, minDurationSec = 300, size = 40)
            _state.update { it.copy(mediathek = Section(page.items.distinctBy { i -> i.id })) }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(mediathek = Section(emptyList(), "Mediathek nicht erreichbar")) }
        }
    }

    private suspend fun searchYouTube(query: String) {
        try {
            val (page, _) = YouTubeRepository.search(query)
            _state.update { it.copy(youtube = Section(page.items.take(30))) }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(youtube = Section(emptyList(), "YouTube nicht erreichbar")) }
        }
    }

    /** Anbieter-Film zum Abspielen vorbereiten (Weiterschauen anmelden). */
    fun prepareMovie(m: VodMovie): Pair<FiptvResume.Entry, Long?>? {
        val url = VodIndex.client?.movieUrl(m) ?: return null
        val profileId = profileManager.getProfileIdSync()
        val entry = FiptvResume.Entry(url = url, kind = FiptvResume.Kind.MOVIE, title = m.name, image = m.poster, tmdbId = m.tmdbId)
        FiptvResume.register(context, profileId, entry)
        return entry to FiptvResume.startPosition(context, profileId, url)
    }
}
