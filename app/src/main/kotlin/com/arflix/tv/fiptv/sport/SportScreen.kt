package com.arflix.tv.fiptv.sport

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.arflix.tv.ui.theme.BackgroundCard
import com.arflix.tv.ui.theme.BackgroundDark
import com.arflix.tv.util.LocalDeviceType

private val Muted = Color(0xFFA0A6B4)
private val Accent = Color(0xFF22C55E)
private val LiveRed = Color(0xFFEF4444)
private val Highlight = Color(0xFF2A3A2E)

/**
 * fIPTV: Sport – Bundesliga und Champions League.
 * Meine Teams · Ligen (Tabelle, Spiele, Teams) · Team · Spiel (Ereignisse, Aufstellungen, Statistik).
 * „Live im TV“ öffnet den Sports-Hub im TV-Bereich.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SportScreen(
    onOpenTvSports: () -> Unit,
    viewModel: SportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isTouch = LocalDeviceType.current.isTouchDevice()
    val firstFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    BackHandler(enabled = state.stack.size > 1) { viewModel.back() }
    LaunchedEffect(state.view) { runCatching { listState.scrollToItem(0) } }

    Column(
        Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(start = if (isTouch) 16.dp else 40.dp, end = if (isTouch) 16.dp else 40.dp, top = 22.dp)
    ) {
        // ---------- obere Leiste ----------
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
            item {
                Text("⚽ Sport", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 10.dp, top = 2.dp))
            }
            item {
                Pill("📺 Live im TV", false, onOpenTvSports, Modifier.focusRequester(firstFocus))
            }
            item {
                Pill("⭐ Meine Teams", state.stack.first() == SportView.MyTeams, { viewModel.showRoot(SportView.MyTeams) })
            }
            items(SportLeague.entries) { lg ->
                val root = state.stack.first()
                Pill(lg.label, root is SportView.League && root.league == lg, { viewModel.showRoot(SportView.League(lg)) })
            }
        }
        Spacer(Modifier.height(10.dp))

        val view = state.view
        // Unterleiste der Liga
        if (view is SportView.League) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Tabelle", "Spiele", "Teams").forEachIndexed { i, label ->
                    Pill(label, view.tab == i, { viewModel.setLeagueTab(i) }, small = true)
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        when {
            state.error != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⚠ ${state.error}", color = Color(0xFFFF8A80), fontSize = 15.sp)
                Spacer(Modifier.width(12.dp))
                Pill("Erneut versuchen", false, { viewModel.retry() }, small = true)
            }
            state.loading -> Text("Wird geladen …", color = Muted, fontSize = 15.sp)
        }

        if (!state.loading && state.error == null) {
            if (view is SportView.League && view.tab == 2) {
                TeamGrid(state.teams, state, onOpen = { viewModel.open(SportView.Team(it)) }, onFollow = { viewModel.toggleFollow(it) }, isTouch = isTouch)
            } else {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(bottom = 40.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    when (view) {
                        SportView.MyTeams -> myTeams(state, viewModel)
                        is SportView.League -> if (view.tab == 0) tableItems(state.table, highlightId = null, onTeam = { viewModel.open(SportView.Team(it)) })
                            else matchItems(state.matches, grouped = true, onMatch = { viewModel.open(SportView.Match(it.league, it.id)) })
                        is SportView.Team -> teamItems(view.team, state, viewModel)
                        is SportView.Match -> state.detail?.let { matchDetailItems(it) }
                    }
                }
            }
        }
    }
}

// ---------- Meine Teams ----------

private fun LazyListScope.myTeams(state: SportUiState, vm: SportViewModel) {
    if (state.followed.isEmpty()) {
        item {
            Text(
                "Du folgst noch keinem Team. Öffne „Bundesliga“ oder „Champions League“ → „Teams“ und wähle „Folgen“.",
                color = Muted, fontSize = 15.sp,
            )
        }
        return
    }
    items(state.overview, key = { it.team.id }) { o ->
        FocusCard(onClick = { vm.open(SportView.Team(o.team)) }) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Logo(o.team.logo, 34)
                    Spacer(Modifier.width(10.dp))
                    Text(o.team.name, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Spacer(Modifier.height(8.dp))
                o.live?.let { MatchLine(it, label = "LIVE") }
                o.last?.takeIf { o.live == null }?.let { MatchLine(it, label = "Zuletzt") }
                o.next?.let { MatchLine(it, label = "Nächstes") }
                if (o.live == null && o.last == null && o.next == null) Text("Keine Spiele gefunden.", color = Muted, fontSize = 13.sp)
            }
        }
    }
}

// ---------- Tabelle ----------

private fun LazyListScope.tableItems(rows: List<SportTableRow>, highlightId: String?, onTeam: (SportTeam) -> Unit) {
    item {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            Cell("#", 30); Spacer(Modifier.width(40.dp)); Text("Team", color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Cell("Sp", 36); Cell("S", 30); Cell("U", 30); Cell("N", 30); Cell("Tore", 60); Cell("Diff", 44); Cell("Pkt", 44)
        }
    }
    items(rows, key = { "t" + it.team.id }) { r ->
        FocusCard(onClick = { onTeam(r.team) }, highlighted = r.team.id == highlightId) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Cell(r.rank.toString(), 30, bold = true, color = Color.White)
                Logo(r.team.logo, 26); Spacer(Modifier.width(14.dp))
                Text(r.team.name, color = Color.White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Cell(r.played, 36); Cell(r.won, 30); Cell(r.drawn, 30); Cell(r.lost, 30); Cell(r.goals, 60); Cell(r.diff, 44)
                Cell(r.points, 44, bold = true, color = Color.White)
            }
        }
    }
}

@Composable
private fun Cell(text: String, width: Int, bold: Boolean = false, color: Color = Muted) {
    Text(text, color = color, fontSize = 14.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.width(width.dp))
}

// ---------- Spiele ----------

private fun LazyListScope.matchItems(matches: List<SportMatch>, grouped: Boolean, onMatch: (SportMatch) -> Unit) {
    if (matches.isEmpty()) {
        item { Text("Keine Spiele in diesem Zeitraum.", color = Muted, fontSize = 15.sp) }
        return
    }
    var lastDay = ""
    matches.forEach { m ->
        val day = SportRepository.formatDay(m.startMs)
        if (grouped && day != lastDay) {
            lastDay = day
            item(key = "d$day${m.id}") { Text(day, color = Accent, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)) }
        }
        item(key = "m" + m.id) {
            FocusCard(onClick = { onMatch(m) }) {
                Box(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) { MatchLine(m, label = if (grouped) null else day) }
            }
        }
    }
}

@Composable
private fun MatchLine(m: SportMatch, label: String?) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        if (label != null) {
            Text(
                if (label == "LIVE") "● LIVE" else label,
                color = if (label == "LIVE") LiveRed else Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, modifier = Modifier.width(86.dp),
            )
        }
        Text(m.league.short, color = Muted, fontSize = 11.sp, modifier = Modifier.width(26.dp))
        Text(m.home.team.name, color = Color.White, fontSize = 15.sp, textAlign = TextAlign.End, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp)); Logo(m.home.team.logo, 24); Spacer(Modifier.width(8.dp))
        val center = if (m.state == "pre") SportRepository.statusText(m) else "${m.home.score ?: "–"} : ${m.away.score ?: "–"}"
        Text(center, color = if (m.isLive) LiveRed else Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center, modifier = Modifier.width(64.dp))
        Spacer(Modifier.width(8.dp)); Logo(m.away.team.logo, 24); Spacer(Modifier.width(8.dp))
        Text(m.away.team.name, color = Color.White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        val extra = when {
            m.isLive -> SportRepository.statusText(m)
            m.isFinished -> SportRepository.statusText(m)
            else -> if (label == null) "" else SportRepository.formatDay(m.startMs)
        }
        Text(extra, color = if (m.isLive) LiveRed else Muted, fontSize = 12.sp, textAlign = TextAlign.End, maxLines = 1, modifier = Modifier.width(70.dp))
    }
}

// ---------- Teams ----------

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TeamGrid(teams: List<SportTeam>, state: SportUiState, onOpen: (SportTeam) -> Unit, onFollow: (SportTeam) -> Unit, isTouch: Boolean) {
    Column {
        Text(
            if (isTouch) "Tippen: Team öffnen · lange tippen: folgen/entfolgen" else "OK: Team öffnen · OK lang: folgen/entfolgen",
            color = Muted.copy(alpha = 0.7f), fontSize = 12.sp,
        )
        Spacer(Modifier.height(8.dp))
        LazyVerticalGrid(
            columns = if (isTouch) GridCells.Adaptive(150.dp) else GridCells.Fixed(5),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 40.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(teams, key = { it.id }) { t ->
                val followed = state.isFollowed(t.id)
                Surface(
                    onClick = { onOpen(t) },
                    onLongClick = { onFollow(t) },
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(10.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = if (followed) Highlight else BackgroundCard, contentColor = Color.White,
                        focusedContainerColor = Color(0xFF2A2E3A), focusedContentColor = Color.White,
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                    border = focusBorder(),
                ) {
                    Column(Modifier.fillMaxWidth().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Logo(t.logo, 48)
                        Spacer(Modifier.height(6.dp))
                        Text(t.name, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                        Text(if (followed) "★ gefolgt" else " ", color = Accent, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

// ---------- Team ----------

private fun LazyListScope.teamItems(team: SportTeam, state: SportUiState, vm: SportViewModel) {
    item {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Logo(team.logo, 48)
            Spacer(Modifier.width(12.dp))
            Text(team.name, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(18.dp))
            val f = state.isFollowed(team.id)
            Pill(if (f) "★ Gefolgt – entfolgen" else "☆ Folgen", f, { vm.toggleFollow(team) }, small = true)
        }
    }
    val live = state.matches.filter { it.isLive }
    val upcoming = state.matches.filter { it.state == "pre" }.take(5)
    val results = state.matches.filter { it.isFinished }.takeLast(6).reversed()
    val open: (SportMatch) -> Unit = { vm.open(SportView.Match(it.league, it.id)) }
    if (live.isNotEmpty()) {
        item { Section("● Live") }
        matchItems(live, grouped = false, onMatch = open)
    }
    item { Section("Nächste Spiele") }
    matchItems(upcoming, grouped = false, onMatch = open)
    item { Section("Letzte Ergebnisse") }
    matchItems(results, grouped = false, onMatch = open)
    val idx = state.table.indexOfFirst { it.team.id == team.id }
    if (idx >= 0) {
        item { Section("Bundesliga-Tabelle") }
        tableItems(state.table.subList((idx - 3).coerceAtLeast(0), (idx + 4).coerceAtMost(state.table.size)), highlightId = team.id) {
            vm.open(SportView.Team(it))
        }
    }
}

// ---------- Spiel ----------

private fun LazyListScope.matchDetailItems(d: SportMatchDetail) {
    val m = d.match
    item {
        FocusCard(onClick = {}) {
            Column(Modifier.fillMaxWidth().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${m.league.label} · ${SportRepository.formatDay(m.startMs)} ${SportRepository.formatTime(m.startMs)}" +
                    (d.venue?.let { " · $it" } ?: ""), color = Muted, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(220.dp)) {
                        Logo(m.home.team.logo, 56); Text(m.home.team.name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(150.dp)) {
                        Text(if (m.state == "pre") SportRepository.formatTime(m.startMs) else "${m.home.score ?: "–"} : ${m.away.score ?: "–"}",
                            color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                        Text(SportRepository.statusText(m), color = if (m.isLive) LiveRed else Muted, fontSize = 14.sp)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(220.dp)) {
                        Logo(m.away.team.logo, 56); Text(m.away.team.name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
    if (d.events.isNotEmpty()) {
        item { Section("Spielverlauf") }
        items(d.events.size) { i ->
            val e = d.events[i]
            val home = e.teamId == m.home.team.id
            val icon = when (e.type) { "goal" -> "⚽"; "red" -> "🟥"; "yellow" -> "🟨"; else -> "🔁" }
            FocusCard(onClick = {}) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (home) "$icon ${e.text}" else "", color = Color.White, fontSize = 14.sp, textAlign = TextAlign.End,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(e.minute, color = Accent, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.width(70.dp))
                    Text(if (!home) "$icon ${e.text}" else "", color = Color.White, fontSize = 14.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                }
            }
        }
    }
    if (d.lineups.size >= 2) {
        val h = d.lineups.firstOrNull { it.team.id == m.home.team.id } ?: d.lineups[0]
        val a = d.lineups.firstOrNull { it != h } ?: d.lineups[1]
        item { Section("Aufstellungen" + listOfNotNull(h.formation, a.formation).takeIf { it.size == 2 }?.let { " (${it[0]} – ${it[1]})" }.orEmpty()) }
        val rows = maxOf(h.starters.size, a.starters.size)
        items(rows) { i -> PlayerRow(h.starters.getOrNull(i), a.starters.getOrNull(i)) }
        if (h.subs.isNotEmpty() || a.subs.isNotEmpty()) {
            item { Text("Ersatzbank", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) }
            items(maxOf(h.subs.size, a.subs.size)) { i -> PlayerRow(h.subs.getOrNull(i), a.subs.getOrNull(i)) }
        }
    } else if (m.state == "pre") {
        item { Text("Aufstellungen gibt es meist etwa eine Stunde vor Anpfiff.", color = Muted, fontSize = 14.sp) }
    }
    if (d.stats.isNotEmpty()) {
        item { Section("Statistik") }
        items(d.stats.size) { i ->
            val s = d.stats[i]
            FocusCard(onClick = {}) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Text(s.home, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.width(80.dp))
                    Text(s.label, color = Muted, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                    Text(s.away, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.width(80.dp))
                }
            }
        }
    }
}

@Composable
private fun PlayerRow(h: SportPlayer?, a: SportPlayer?) {
    FocusCard(onClick = {}) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
            Text(h?.let { playerText(it) }.orEmpty(), color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(16.dp))
            Text(a?.let { playerText(it) }.orEmpty(), color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
    }
}

private fun playerText(p: SportPlayer): String {
    val pos = when (p.position) { "G" -> "TW"; "D", "CD", "LB", "RB", "CB" -> "AB"; "M", "DM", "AM", "CM", "LM", "RM" -> "MF"; "F", "CF", "ST", "LW", "RW" -> "ST"; "SUB" -> ""; else -> p.position }
    val marks = (if (p.subbedOut) " ↓" else "") + (if (p.subbedIn) " ↑" else "")
    return listOf(p.jersey.takeIf { it.isNotBlank() }, p.name, pos.takeIf { it.isNotBlank() }?.let { "($it)" }).filterNotNull().joinToString(" ") + marks
}

// ---------- Bausteine ----------

@Composable
private fun Section(title: String) {
    Text(title, color = Accent, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp, bottom = 2.dp))
}

@Composable
private fun Logo(url: String?, sizeDp: Int) {
    if (url == null) Spacer(Modifier.size(sizeDp.dp))
    else AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(sizeDp.dp))
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun focusBorder() = ClickableSurfaceDefaults.border(
    focusedBorder = Border(
        border = androidx.compose.foundation.BorderStroke(2.dp, Color.White),
        shape = RoundedCornerShape(10.dp),
    )
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun FocusCard(onClick: () -> Unit, highlighted: Boolean = false, content: @Composable () -> Unit) {
    // Alle Zeilen sind fokussierbar, damit man auf dem Fernseher mit dem Steuerkreuz durch die Liste scrollen kann
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (highlighted) Highlight else BackgroundCard,
            contentColor = Color.White,
            focusedContainerColor = Color(0xFF2A2E3A),
            focusedContentColor = Color.White,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.01f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = androidx.compose.foundation.BorderStroke(2.dp, Color.White),
                shape = RoundedCornerShape(10.dp),
            )
        ),
    ) { content() }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Pill(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, small: Boolean = false) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(50)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Accent.copy(alpha = 0.35f) else BackgroundCard,
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
    ) {
        Text(
            label,
            fontSize = if (small) 14.sp else 15.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = if (small) 14.dp else 16.dp, vertical = if (small) 6.dp else 8.dp),
        )
    }
}
