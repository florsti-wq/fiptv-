package com.arflix.tv.fiptv.sport

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.arflix.tv.data.repository.ProfileManager
import com.arflix.tv.util.settingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/** Ein Hinweis: Tor oder Abpfiff in einem Spiel eines gefolgten Teams. */
data class GoalAlert(
    val key: String,
    val title: String,
    val match: SportMatch,
    val line: String,
    val forFollowed: Boolean,
)

object FiptvGoalAlerts {
    private const val PREFS = "fiptv_goal_alerts"
    fun enabled(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("on", true)
    fun setEnabled(context: Context, on: Boolean) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("on", on).apply()
}

/**
 * fIPTV: beobachtet die Spiele der gefolgten Teams (nur während sie laufen) und meldet Tore und den
 * Abpfiff – überall in der App, auch während ein Film oder Sender läuft.
 */
@HiltViewModel
class FiptvGoalWatcherViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val profileManager: ProfileManager,
) : ViewModel() {
    private val _alert = MutableStateFlow<GoalAlert?>(null)
    val alert: StateFlow<GoalAlert?> = _alert.asStateFlow()

    private var schedule: List<SportMatch> = emptyList()
    private var scheduleAt = 0L
    private var scheduleKey = ""
    private val known = HashMap<String, Triple<String?, String?, String>>() // id → (home, away, state)
    private val queue = ArrayDeque<GoalAlert>()

    init {
        viewModelScope.launch {
            delay(20_000)
            while (isActive) {
                val wait = runCatching { tick() }.getOrDefault(120_000L)
                delay(wait)
            }
        }
        viewModelScope.launch {
            while (isActive) {
                if (_alert.value == null) queue.removeFirstOrNull()?.let { _alert.value = it }
                delay(1_000)
            }
        }
    }

    fun dismiss() { _alert.value = null }

    /** @return Wartezeit bis zur nächsten Prüfung */
    private suspend fun tick(): Long {
        if (!FiptvGoalAlerts.enabled(context)) return 5 * 60_000L
        val raw = context.settingsDataStore.data.first()[profileManager.profileStringKey(SportViewModel.PREF_NAME)]
        val followed = runCatching { SportViewModel.decode(raw) }.getOrDefault(emptyList())
        if (followed.isEmpty()) return 10 * 60_000L
        val followedIds = followed.map { it.id }.toSet()
        val now = System.currentTimeMillis()

        // Spielplan der gefolgten Teams alle 30 Minuten auffrischen
        val key = followedIds.sorted().joinToString(",")
        if (key != scheduleKey || now - scheduleAt > 30 * 60_000L) {
            schedule = followed.flatMap { t -> runCatching { SportRepository.teamMatches(t) }.getOrDefault(emptyList()) }
                .filter { it.startMs in (now - 4 * 3600_000L)..(now + 36 * 3600_000L) }
                .distinctBy { it.id }
            scheduleAt = now; scheduleKey = key
        }
        val active = schedule.filter { it.isLive || (it.state != "post" && now in (it.startMs - 10 * 60_000L)..(it.startMs + 150 * 60_000L)) }
        if (active.isEmpty()) {
            val next = schedule.filter { it.state == "pre" && it.startMs > now }.minOfOrNull { it.startMs }
            return if (next == null) 15 * 60_000L else (next - now - 10 * 60_000L).coerceIn(60_000L, 15 * 60_000L)
        }

        // Nur die Spieltage der Wettbewerbe mit laufenden Spielen abfragen
        for (league in active.map { it.league }.distinct()) {
            val games = runCatching { SportRepository.currentScoreboard(league) }.getOrDefault(emptyList())
            for (g in games) {
                if (g.home.team.id !in followedIds && g.away.team.id !in followedIds) continue
                val before = known[g.id]
                known[g.id] = Triple(g.home.score, g.away.score, g.state)
                schedule = schedule.map { if (it.id == g.id) g else it }
                if (before == null) continue
                val (h0, a0, s0) = before
                val h = g.home.score?.toIntOrNull() ?: 0
                val a = g.away.score?.toIntOrNull() ?: 0
                val hOld = h0?.toIntOrNull() ?: 0
                val aOld = a0?.toIntOrNull() ?: 0
                if (h > hOld || a > aOld) {
                    val scorerSide = if (h > hOld) g.home.team else g.away.team
                    val scorer = withTimeoutOrNull(6_000) {
                        runCatching {
                            SportRepository.matchDetail(league, g.id).events
                                .lastOrNull { it.type == "goal" && it.teamId == scorerSide.id }
                                ?.let { "${it.text} (${it.minute})" }
                        }.getOrNull()
                    }
                    queue += GoalAlert(
                        key = "${g.id}:$h:$a",
                        title = "⚽ TOR für ${scorerSide.name}!",
                        match = g,
                        line = scorer ?: g.detail,
                        forFollowed = scorerSide.id in followedIds,
                    )
                } else if (h < hOld || a < aOld) {
                    queue += GoalAlert("${g.id}:var:$h:$a", "🖥 Tor zurückgenommen (VAR)", g, g.detail, false)
                }
                if (s0 != "post" && g.state == "post") {
                    queue += GoalAlert("${g.id}:end", "🏁 Abpfiff", g, g.league.label, false)
                } else if (s0 == "pre" && g.state == "in") {
                    queue += GoalAlert("${g.id}:start", "▶ Anpfiff", g, g.league.label, false)
                }
            }
        }
        return 40_000L
    }
}

/** Banner oben rechts; nimmt den Fokus nicht weg und verschwindet nach ein paar Sekunden. */
@Composable
fun FiptvGoalAlertHost(enabled: Boolean, viewModel: FiptvGoalWatcherViewModel = hiltViewModel()) {
    if (!enabled) return
    val alert by viewModel.alert.collectAsStateWithLifecycle()
    LaunchedEffect(alert?.key) {
        if (alert != null) { delay(9_000); viewModel.dismiss() }
    }
    val a = alert ?: return
    Popup(
        alignment = Alignment.TopEnd,
        offset = IntOffset(-40, 40),
        properties = PopupProperties(focusable = false, dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        AnimatedVisibility(visible = true, enter = slideInVertically { -it } + fadeIn(), exit = slideOutVertically { -it } + fadeOut()) {
            Column(
                Modifier
                    .widthIn(min = 320.dp, max = 460.dp)
                    .background(Color(0xF2101620), RoundedCornerShape(14.dp))
                    .border(2.dp, if (a.forFollowed) Color(0xFF22C55E) else Color.White.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(a.title, color = if (a.forFollowed) Color(0xFF4ADE80) else Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TeamLogo(a.match.home.team.logo)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${a.match.home.team.name}  ${a.match.home.score ?: "0"} : ${a.match.away.score ?: "0"}  ${a.match.away.team.name}",
                        color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(8.dp))
                    TeamLogo(a.match.away.team.logo)
                }
                if (a.line.isNotBlank()) Text(a.line, color = Color.White.copy(alpha = 0.75f), fontSize = 14.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun TeamLogo(url: String?) {
    if (url != null) AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(26.dp))
}
