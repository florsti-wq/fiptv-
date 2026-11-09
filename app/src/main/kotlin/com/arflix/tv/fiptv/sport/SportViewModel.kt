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
    data object MyTeams : SportView
    data class League(val league: SportLeague, val tab: Int = 0) : SportView  // 0 Tabelle, 1 Spiele, 2 Teams
    data class Team(val team: SportTeam) : SportView
    data class Match(val league: SportLeague, val eventId: String) : SportView
}

/** Übersicht je gefolgtem Team */
data class TeamOverview(val team: SportTeam, val live: SportMatch?, val next: SportMatch?, val last: SportMatch?)

data class SportUiState(
    val stack: List<SportView> = listOf(SportView.MyTeams),
    val followed: List<SportTeam> = emptyList(),
    val overview: List<TeamOverview> = emptyList(),
    val table: List<SportTableRow> = emptyList(),
    val matches: List<SportMatch> = emptyList(),
    val teams: List<SportTeam> = emptyList(),
    val detail: SportMatchDetail? = null,
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
) : ViewModel() {
    private val _state = MutableStateFlow(SportUiState())
    val state: StateFlow<SportUiState> = _state.asStateFlow()
    private var job: Job? = null

    private fun teamsKey() = profileManager.profileStringKey(PREF_NAME)

    init {
        viewModelScope.launch {
            val followed = readFollowed()
            _state.update { it.copy(followed = followed) }
            // Ohne gefolgte Teams direkt die Bundesliga-Tabelle zeigen
            if (followed.isEmpty()) _state.update { it.copy(stack = listOf(SportView.League(SportLeague.BUNDESLIGA))) }
            load()
        }
        // Laufende Spiele jede Minute aktualisieren
        viewModelScope.launch {
            while (isActive) {
                delay(60_000)
                val s = _state.value
                val live = when (val v = s.view) {
                    SportView.MyTeams -> s.overview.any { it.live != null }
                    is SportView.League -> v.tab == 1 && s.matches.any { it.isLive }
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
        val v = _state.value.view as? SportView.League ?: return
        _state.update { it.copy(stack = it.stack.dropLast(1) + v.copy(tab = tab)) }
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
                    SportView.MyTeams -> {
                        val overview = _state.value.followed.map { t ->
                            async {
                                val all = runCatching { SportRepository.teamMatches(t.id) }.getOrDefault(emptyList())
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
                    is SportView.League -> when (view.tab) {
                        0 -> SportRepository.table(view.league).let { t -> _state.update { it.copy(table = t) } }
                        1 -> SportRepository.matches(view.league).let { m -> _state.update { it.copy(matches = m) } }
                        else -> SportRepository.teams(view.league).let { t -> _state.update { it.copy(teams = t) } }
                    }
                    is SportView.Team -> {
                        val m = async { SportRepository.teamMatches(view.team.id) }
                        val t = async { runCatching { SportRepository.table(SportLeague.BUNDESLIGA) }.getOrDefault(emptyList()) }
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
