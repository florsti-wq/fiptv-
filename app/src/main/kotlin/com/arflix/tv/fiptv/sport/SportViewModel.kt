package com.arflix.tv.fiptv.sport

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arflix.tv.data.repository.CloudSyncRepository
import com.arflix.tv.data.repository.ProfileManager
import com.arflix.tv.util.settingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

sealed interface SportView {
    /** Sports-Hub: Spiele mit verfügbaren Quellen (TV-Sender, Stremio) */
    data object Hub : SportView
    /** Quellen eines Spiels */
    data class Sources(val match: SportMatch) : SportView
    data object MyTeams : SportView
    /** Fußball: Auswahl der Wettbewerbe */
    data object Football : SportView
    data class League(val league: SportLeague, val tab: Int = if (league.hasTable) 0 else 1) : SportView  // 0 Tabelle, 1 Spiele, 2 Teams, 3 Statistik
    /** 0 Rennwochenenden, 1 Fahrer-WM, 2 Team-WM, 3 Im TV */
    data class F1(val tab: Int = 0) : SportView
    /** tour = atp/wta; 0 Turniere, 1 Weltrangliste, 2 Im TV */
    data class Tennis(val tour: String = "atp", val tab: Int = 0) : SportView
    /** 0 Shows, 1 Im TV, 2 Videos */
    data class Wrestling(val tab: Int = 0) : SportView
    data class Team(val team: SportTeam) : SportView
    data class Match(val league: SportLeague, val eventId: String) : SportView
}

/** Übersicht je gefolgtem Team */
data class TeamOverview(val team: SportTeam, val live: SportMatch?, val next: SportMatch?, val last: SportMatch?)

data class SportUiState(
    val stack: List<SportView> = listOf(SportView.Hub),
    /** Sports-Hub: Spiele (live und kommende) */
    val hubGames: List<SportMatch> = emptyList(),
    val sources: Map<String, GameSources> = emptyMap(),
    val sourcesLoading: Boolean = false,
    /** aufgelöste Stremio-Streams je Addon-Ereignis */
    val addonStreams: Map<String, List<com.arflix.tv.data.repository.SportsAddonStream>> = emptyMap(),
    val resolvingAddon: String? = null,
    val followed: List<SportTeam> = emptyList(),
    val overview: List<TeamOverview> = emptyList(),
    val table: List<SportTableRow> = emptyList(),
    val matches: List<SportMatch> = emptyList(),
    val teams: List<SportTeam> = emptyList(),
    val detail: SportMatchDetail? = null,
    val leaders: List<StatCategory> = emptyList(),
    val f1Weekends: List<F1Weekend> = emptyList(),
    val f1Calendar: List<F1Calendar> = emptyList(),
    val f1Drivers: List<F1Standing> = emptyList(),
    val f1Teams: List<F1Standing> = emptyList(),
    val tennis: List<TennisTournament> = emptyList(),
    val tennisRanking: List<TennisRank> = emptyList(),
    val wrestling: List<WrestlingShow> = emptyList(),
    val tvListings: List<TvSource> = emptyList(),
    val videos: List<com.arflix.tv.fiptv.youtube.YtVideo> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
) {
    val view: SportView get() = stack.last()
    fun isFollowed(id: String) = followed.any { it.id == id }
}

/**
 * fIPTV: Sport-Bereich (Bundesliga, Champions League). Gefolgte Teams liegen je Profil in den
 * Einstellungen („profile_<id>_fiptv_sport_teams“) und werden über den eigenen Server abgeglichen.
 */
@HiltViewModel
class SportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val profileManager: ProfileManager,
    private val cloudSyncRepository: CloudSyncRepository,
    private val sourceFinder: SportSourceFinder,
) : ViewModel() {
    private var sourcesJob: Job? = null
    private val _state = MutableStateFlow(SportUiState())
    val state: StateFlow<SportUiState> = _state.asStateFlow()
    private var job: Job? = null

    private fun teamsKey() = profileManager.profileStringKey(PREF_NAME)

    init {
        viewModelScope.launch {
            val followed = readFollowed()
            _state.update { it.copy(followed = followed) }
            load()
        }
        // Laufende Spiele jede Minute aktualisieren
        viewModelScope.launch {
            while (isActive) {
                delay(60_000)
                val s = _state.value
                val live = when (val v = s.view) {
                    SportView.Hub -> s.hubGames.any { it.isLive }
                    is SportView.Sources -> false
                    SportView.MyTeams -> s.overview.any { it.live != null }
                    is SportView.League -> v.tab == 1 && s.matches.any { it.isLive }
                    SportView.Football -> false
                    is SportView.F1 -> v.tab == 0 && s.f1Weekends.any { w -> w.sessions.any { it.state == "in" } }
                    is SportView.Tennis -> v.tab == 0 && s.tennis.any { t -> t.matches.any { it.isLive } }
                    is SportView.Wrestling -> false
                    is SportView.Team -> s.matches.any { it.isLive }
                    is SportView.Match -> s.detail?.match?.isLive == true
                }
                if (live && job?.isActive != true) load(quiet = true)
            }
        }
    }

    // ---------- Navigation ----------

    /** Neuer Hauptbereich (oberste Leiste): Verlauf zurücksetzen */
    fun showRoot(view: SportView) {
        _state.update { it.copy(stack = listOf(view)) }
        load()
    }

    fun open(view: SportView) {
        _state.update { it.copy(stack = it.stack + view) }
        load()
    }

    /** true, wenn es eine Ebene zurück ging */
    fun back(): Boolean {
        if (_state.value.stack.size <= 1) return false
        _state.update { it.copy(stack = it.stack.dropLast(1)) }
        load()
        return true
    }

    fun setLeagueTab(tab: Int) {
        val v = _state.value.view
        val next = when (v) {
            is SportView.League -> v.copy(tab = tab)
            is SportView.F1 -> v.copy(tab = tab)
            is SportView.Tennis -> v.copy(tab = tab)
            is SportView.Wrestling -> v.copy(tab = tab)
            else -> return
        }
        _state.update { it.copy(stack = it.stack.dropLast(1) + next) }
        load()
    }

    fun setTennisTour(tour: String) {
        val v = _state.value.view as? SportView.Tennis ?: return
        if (v.tour == tour) return
        _state.update { it.copy(stack = it.stack.dropLast(1) + v.copy(tour = tour)) }
        load()
    }

    fun retry() = load()

    // ---------- Folgen ----------

    fun toggleFollow(team: SportTeam) {
        val s = _state.value
        val updated = if (s.isFollowed(team.id)) s.followed.filterNot { it.id == team.id } else s.followed + team
        _state.update { it.copy(followed = updated) }
        viewModelScope.launch {
            writeFollowed(updated)
            runCatching { cloudSyncRepository.pushToCloud() }
        }
    }

    // ---------- Laden ----------

    private fun load(quiet: Boolean = false) {
        job?.cancel()
        val view = _state.value.view
        if (!quiet) _state.update { it.copy(loading = true, error = null) }
        job = viewModelScope.launch {
            try {
                when (view) {
                    SportView.Hub -> {
                        val games = SportLeague.entries.map { lg -> async { runCatching { SportRepository.matches(lg) }.getOrDefault(emptyList()) } }
                            .awaitAll().flatten()
                        val now = System.currentTimeMillis()
                        val followedIds = _state.value.followed.map { it.id }.toSet()
                        // Live und die nächsten Tage; Vereins-Testspiele nur mit gefolgten Teams
                        val hub = games.filter { it.isLive || (it.state == "pre" && it.startMs - now < 8 * 24 * 3600_000L) }
                            .filter { it.league != SportLeague.CLUB_FRIENDLIES || it.home.team.id in followedIds || it.away.team.id in followedIds }
                            .sortedWith(compareByDescending<SportMatch> { it.isLive }.thenBy { it.startMs })
                            .take(80)
                        _state.update { it.copy(hubGames = hub) }
                        findSources(hub, force = quiet)
                    }
                    is SportView.Sources -> {
                        if (_state.value.sources[view.match.id] == null) findSources(listOf(view.match))
                    }
                    SportView.MyTeams -> {
                        val overview = _state.value.followed.map { t ->
                            async {
                                val all = runCatching { SportRepository.teamMatches(t) }.getOrDefault(emptyList())
                                TeamOverview(
                                    team = t,
                                    live = all.firstOrNull { it.isLive },
                                    next = all.firstOrNull { it.state == "pre" },
                                    last = all.lastOrNull { it.isFinished },
                                )
                            }
                        }.awaitAll()
                        _state.update { it.copy(overview = overview) }
                    }
                    SportView.Football -> Unit
                    is SportView.League -> when (view.tab) {
                        0 -> SportRepository.table(view.league).let { t -> _state.update { it.copy(table = t) } }
                        1 -> SportRepository.matches(view.league).let { m -> _state.update { it.copy(matches = m) } }
                        3 -> SportRepository.leaders(view.league).let { l -> _state.update { it.copy(leaders = l) } }
                        else -> SportRepository.teams(view.league).let { t -> _state.update { it.copy(teams = t) } }
                    }
                    is SportView.F1 -> when (view.tab) {
                        0 -> MoreSportsRepository.f1Weekends().let { (w, c) -> _state.update { it.copy(f1Weekends = w, f1Calendar = c) } }
                        1, 2 -> MoreSportsRepository.f1Standings().let { (d, t) -> _state.update { it.copy(f1Drivers = d, f1Teams = t) } }
                        else -> loadListings("f1", SportSourceFinder.F1_PATTERN)
                    }
                    is SportView.Tennis -> when (view.tab) {
                        0 -> MoreSportsRepository.tennisTournaments(view.tour).let { t -> _state.update { it.copy(tennis = t) } }
                        1 -> MoreSportsRepository.tennisRanking(view.tour).let { r -> _state.update { it.copy(tennisRanking = r) } }
                        else -> loadListings("tennis", SportSourceFinder.TENNIS_PATTERN)
                    }
                    is SportView.Wrestling -> when (view.tab) {
                        0 -> MoreSportsRepository.wrestlingShows().let { w -> _state.update { it.copy(wrestling = w) } }
                        1 -> loadListings("wrestling", SportSourceFinder.WRESTLING_PATTERN)
                        else -> {
                            if (!com.arflix.tv.fiptv.youtube.YouTubeRepository.isConfigured) error("YouTube ist in dieser App-Version nicht eingerichtet")
                            val vids = listOf(MoreSportsRepository.WWE_YOUTUBE, MoreSportsRepository.AEW_YOUTUBE).map { id ->
                                async { runCatching { com.arflix.tv.fiptv.youtube.YouTubeRepository.channelVideos(id, max = 20).second.items }.getOrDefault(emptyList()) }
                            }.awaitAll().flatten().sortedByDescending { it.publishedAt }
                            _state.update { it.copy(videos = vids) }
                        }
                    }
                    is SportView.Team -> {
                        val m = async { SportRepository.teamMatches(view.team) }
                        val t = async { runCatching { SportRepository.table(SportLeague.byCode(view.team.league) ?: SportLeague.BUNDESLIGA) }.getOrDefault(emptyList()) }
                        _state.update { it.copy(matches = m.await(), table = t.await()) }
                    }
                    is SportView.Match -> {
                        val d = SportRepository.matchDetail(view.league, view.eventId)
                        _state.update { it.copy(detail = d) }
                    }
                }
                _state.update { it.copy(loading = false) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!quiet) _state.update { it.copy(loading = false, error = e.message ?: "Sportdaten nicht erreichbar") }
            }
        }
    }

    private suspend fun loadListings(key: String, pattern: Regex) {
        _state.update { it.copy(tvListings = emptyList()) }
        val list = sourceFinder.tvListings(key, pattern)
        _state.update { it.copy(tvListings = list) }
    }

    // ---------- Quellen ----------

    private fun findSources(games: List<SportMatch>, force: Boolean = false) {
        if (games.isEmpty()) return
        sourcesJob?.cancel()
        _state.update { it.copy(sourcesLoading = true) }
        sourcesJob = viewModelScope.launch {
            try {
                val allTeams = games.flatMap { listOf(it.home.team, it.away.team) }.distinctBy { it.id }
                val found = sourceFinder.find(games, allTeams, force)
                _state.update { it.copy(sources = it.sources + found) }
            } finally {
                _state.update { it.copy(sourcesLoading = false) }
            }
        }
    }

    fun resolveAddon(event: com.arflix.tv.data.model.SportsAddonEvent) {
        if (_state.value.addonStreams.containsKey(event.key)) return
        _state.update { it.copy(resolvingAddon = event.key) }
        viewModelScope.launch {
            val list = sourceFinder.resolveAddon(event)
            _state.update { it.copy(addonStreams = it.addonStreams + (event.key to list), resolvingAddon = null) }
        }
    }

    // ---------- Speicher ----------

    private suspend fun readFollowed(): List<SportTeam> = runCatching {
        decode(context.settingsDataStore.data.first()[teamsKey()])
    }.getOrDefault(emptyList())

    private suspend fun writeFollowed(list: List<SportTeam>) {
        context.settingsDataStore.edit { it[teamsKey()] = encode(list) }
    }

    companion object {
        const val PREF_NAME = "fiptv_sport_teams"

        fun encode(list: List<SportTeam>): String {
            val arr = JSONArray()
            list.forEach { arr.put(JSONObject().put("id", it.id).put("name", it.name).put("logo", it.logo ?: "").put("league", it.league)) }
            return arr.toString()
        }

        fun decode(raw: String?): List<SportTeam> {
            if (raw.isNullOrBlank()) return emptyList()
            val arr = JSONArray(raw)
            return (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let { o ->
                    SportTeam(o.optString("id"), o.optString("name"), o.optString("logo").takeIf { it.isNotBlank() }, o.optString("league").ifBlank { SportLeague.BUNDESLIGA.code })
                }
            }.filter { it.id.isNotBlank() }
        }
    }
}
