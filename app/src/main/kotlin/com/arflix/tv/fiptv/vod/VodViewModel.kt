package com.arflix.tv.fiptv.vod

import android.content.Context
import androidx.lifecycle.ViewModel
import com.arflix.tv.data.repository.ProfileManager
import com.arflix.tv.fiptv.FiptvResume
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.lifecycle.viewModelScope
import com.arflix.tv.data.repository.IptvRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Pseudo-Kategorie: alle Titel, neueste zuerst */
const val VOD_ALL = "__all__"

data class VodUiState(
    val accounts: List<XtreamAccount> = emptyList(),
    val accountIndex: Int = 0,
    val series: Boolean = false,
    val categories: List<VodCategory> = emptyList(),
    val categoryId: String? = null,
    val movies: List<VodMovie> = emptyList(),
    val seriesList: List<VodSeries> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    /** geöffnete Serie (Staffeln/Folgen) */
    val openSeries: VodSeries? = null,
    val episodes: List<VodEpisode> = emptyList(),
    val episodesLoading: Boolean = false,
    val season: Int? = null,
) {
    val account: XtreamAccount? get() = accounts.getOrNull(accountIndex)
    val seasons: List<Int> get() = episodes.map { it.season }.distinct().sorted()
    val seasonEpisodes: List<VodEpisode> get() = episodes.filter { season == null || it.season == season }
}

@HiltViewModel
class VodViewModel @Inject constructor(
    private val iptvRepository: IptvRepository,
    @ApplicationContext private val context: Context,
    private val profileManager: ProfileManager,
) : ViewModel() {
    private val _state = MutableStateFlow(VodUiState())
    val state: StateFlow<VodUiState> = _state.asStateFlow()
    private var job: Job? = null
    private var client: XtreamVodClient? = null

    init {
        viewModelScope.launch {
            val accounts = runCatching { iptvRepository.fiptvXtreamAccounts() }.getOrDefault(emptyList())
            _state.update {
                it.copy(
                    accounts = accounts,
                    loading = accounts.isNotEmpty(),
                    error = if (accounts.isEmpty()) "Kein Xtream-Zugang gefunden. Filme und Serien gibt es nur bei Xtream-Anbietern (IPTV-Einstellungen)." else null,
                )
            }
            if (accounts.isNotEmpty()) loadCategories()
        }
    }

    fun selectAccount(index: Int) {
        if (index == _state.value.accountIndex) return
        _state.update { it.copy(accountIndex = index, openSeries = null) }
        loadCategories()
    }

    fun setSeries(series: Boolean) {
        if (series == _state.value.series) return
        _state.update { it.copy(series = series, openSeries = null) }
        loadCategories()
    }

    private fun loadCategories() {
        val account = _state.value.account ?: return
        val c = XtreamVodClient(account).also { client = it }
        job?.cancel()
        _state.update { it.copy(loading = true, error = null, categories = emptyList(), movies = emptyList(), seriesList = emptyList()) }
        job = viewModelScope.launch {
            try {
                val cats = c.categories(_state.value.series)
                _state.update { it.copy(categories = listOf(VodCategory(VOD_ALL, "Alle (neueste zuerst)")) + cats) }
                loadItems(cats.firstOrNull()?.id ?: VOD_ALL)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "Anbieter nicht erreichbar") }
            }
        }
    }

    fun selectCategory(id: String) {
        if (id == _state.value.categoryId && !_state.value.loading) return
        job?.cancel()
        job = viewModelScope.launch { loadItems(id) }
    }

    private suspend fun loadItems(categoryId: String) {
        val c = client ?: return
        _state.update { it.copy(categoryId = categoryId, loading = true, error = null, movies = emptyList(), seriesList = emptyList()) }
        try {
            val cat = categoryId.takeIf { it != VOD_ALL }
            if (_state.value.series) {
                val list = c.series(cat)
                _state.update { it.copy(seriesList = list, loading = false) }
            } else {
                var list = c.movies(cat)
                if (cat == null) list = list.sortedByDescending { it.added }
                _state.update { it.copy(movies = list, loading = false) }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(loading = false, error = e.message ?: "Anbieter nicht erreichbar") }
        }
    }

    fun openSeries(s: VodSeries) {
        val c = client ?: return
        _state.update { it.copy(openSeries = s, episodes = emptyList(), episodesLoading = true, season = null) }
        viewModelScope.launch {
            val eps = runCatching { c.episodes(s.id) }.getOrDefault(emptyList())
            _state.update {
                if (it.openSeries?.id != s.id) it
                else it.copy(episodes = eps, episodesLoading = false, season = eps.firstOrNull()?.season)
            }
        }
    }

    fun closeSeries() = _state.update { it.copy(openSeries = null, episodes = emptyList()) }

    fun selectSeason(season: Int) = _state.update { it.copy(season = season) }

    fun movieUrl(m: VodMovie): String? = client?.movieUrl(m)
    fun episodeUrl(e: VodEpisode): String? = client?.episodeUrl(e)

    // ---------- fIPTV: Weiterschauen ----------
    private val profileId: String get() = profileManager.getProfileIdSync()

    /** Film anmelden; liefert Eintrag und ggf. Startposition. */
    fun prepareMovie(m: VodMovie): Pair<FiptvResume.Entry, Long?>? {
        val url = movieUrl(m) ?: return null
        val entry = FiptvResume.Entry(url = url, kind = FiptvResume.Kind.MOVIE, title = m.name, image = m.poster, tmdbId = m.tmdbId)
        FiptvResume.register(context, profileId, entry)
        return entry to FiptvResume.startPosition(context, profileId, url)
    }

    fun prepareEpisode(s: VodSeries, e: VodEpisode): Pair<FiptvResume.Entry, Long?>? {
        val url = episodeUrl(e) ?: return null
        val entry = FiptvResume.Entry(
            url = url, kind = FiptvResume.Kind.EPISODE,
            title = e.title.ifBlank { "Folge ${e.episode}" }, subtitle = s.name,
            image = s.poster, tmdbId = s.tmdbId, season = e.season, episode = e.episode,
        )
        FiptvResume.register(context, profileId, entry)
        return entry to FiptvResume.startPosition(context, profileId, url)
    }

    fun resumeEntries(): List<FiptvResume.Entry> =
        FiptvResume.continueWatching(context, profileId, setOf(FiptvResume.Kind.MOVIE, FiptvResume.Kind.EPISODE))

    fun episodeProgress(e: VodEpisode): FiptvResume.Entry? =
        episodeUrl(e)?.let { FiptvResume.entry(context, profileId, it) }

    fun removeResume(entry: FiptvResume.Entry) = FiptvResume.remove(context, profileId, entry.url)
}
