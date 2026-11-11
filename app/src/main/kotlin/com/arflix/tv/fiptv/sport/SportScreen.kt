package com.arflix.tv.fiptv.sport

import com.arflix.tv.fiptv.fiptvTap

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
import androidx.compose.runtime.setValue
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

internal val Muted = Color(0xFFA0A6B4)
internal val Accent = Color(0xFF22C55E)
internal val LiveRed = Color(0xFFEF4444)
private val Highlight = Color(0xFF2A3A2E)

/**
 * fIPTV: Sport – Fußball (Top-Ligen, Pokal, Europapokal, Nationalteams, Testspiele), Formel 1, Tennis, Wrestling.
 * Sports-Hub (Spiele mit Quellen: IPTV-Sender und Stremio) · Meine Teams · Ligen (Tabelle, Spiele, Teams)
 * · Team · Spiel (Ereignisse, Aufstellungen, Statistik).
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SportScreen(
    onPlayChannel: (com.arflix.tv.data.model.IptvChannel) -> Unit,
    onPlayStream: (title: String, url: String) -> Unit,
    viewModel: SportViewModel = hiltViewModel(),
) {
    val context = androidx.compose.ui.platform.LocalContext.current
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.tv.material3.Icon(com.arflix.tv.fiptv.FiptvIcons.Trophy, contentDescription = null, tint = Color.White, modifier = Modifier.padding(end = 10.dp).size(28.dp))
                Text("Sport", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 10.dp, top = 2.dp))
                }
            }
            item {
                Pill("Sports-Hub", state.stack.first() == SportView.Hub, { viewModel.showRoot(SportView.Hub) }, icon = com.arflix.tv.fiptv.FiptvIcons.Overview, modifier = Modifier.focusRequester(firstFocus))
            }
            item {
                Pill("Meine Teams", state.stack.first() == SportView.MyTeams, { viewModel.showRoot(SportView.MyTeams) }, icon = com.arflix.tv.fiptv.FiptvIcons.Star)
            }
            // fIPTV: Sportarten
            item {
                val root = state.stack.first()
                Pill("Fußball", root is SportView.Football || root is SportView.League, { viewModel.showRoot(SportView.Football) }, icon = com.arflix.tv.fiptv.FiptvIcons.Football)
            }
            item { Pill("Formel 1", state.stack.first() is SportView.F1, { viewModel.showRoot(SportView.F1()) }, icon = com.arflix.tv.fiptv.FiptvIcons.Formula1) }
            item { Pill("Tennis", state.stack.first() is SportView.Tennis, { viewModel.showRoot(SportView.Tennis()) }, icon = com.arflix.tv.fiptv.FiptvIcons.Tennis) }
            item { Pill("Wrestling", state.stack.first() is SportView.Wrestling, { viewModel.showRoot(SportView.Wrestling()) }, icon = com.arflix.tv.fiptv.FiptvIcons.Wrestling) }
        }
        Spacer(Modifier.height(10.dp))

        val view = state.view
        // Unterleiste (Liga, Formel 1, Tennis, Wrestling)
        val subTabs: List<Pair<Int, String>> = when (view) {
            is SportView.League -> buildList {
                if (view.league.hasTable) add(0 to "Tabelle")
                add(1 to "Spiele"); add(2 to "Teams")
                if (view.league.hasStats) add(3 to "Torjäger & Vorlagen")
            }
            is SportView.F1 -> listOf(0 to "Rennwochenenden", 1 to "Fahrer-WM", 2 to "Team-WM", 3 to "Im TV")
            is SportView.Tennis -> listOf(0 to "Turniere", 1 to "Weltrangliste", 2 to "Im TV")
            is SportView.Wrestling -> listOf(0 to "Shows", 1 to "Im TV", 2 to "Videos")
            else -> emptyList()
        }
        if (subTabs.isNotEmpty()) {
            val current = when (view) {
                is SportView.League -> view.tab; is SportView.F1 -> view.tab
                is SportView.Tennis -> view.tab; is SportView.Wrestling -> view.tab; else -> -1
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (view is SportView.League) item {
                    Text(view.league.label, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 6.dp))
                }
                if (view is SportView.Tennis) {
                    item { Pill("ATP Herren", view.tour == "atp", { viewModel.setTennisTour("atp") }, small = true) }
                    item { Pill("WTA Damen", view.tour == "wta", { viewModel.setTennisTour("wta") }, small = true) }
                    item { Spacer(Modifier.width(12.dp)) }
                }
                items(subTabs) { (i, label) -> Pill(label, current == i, { viewModel.setLeagueTab(i) }, small = true, icon = subTabIcon(label)) }
            }
            Spacer(Modifier.height(10.dp))
        }

        when {
            state.error != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${state.error}", color = Color(0xFFFF8A80), fontSize = 15.sp)
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
                        SportView.Hub -> hubItems(state, onOpen = { viewModel.open(SportView.Sources(it)) })
                        is SportView.Sources -> sourceItems(view.match, state, viewModel,
                            onPlayChannel = onPlayChannel,
                            onPlayStream = { title, stream ->
                                if (stream.external) runCatching {
                                    context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(stream.url))
                                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                                } else onPlayStream(title, stream.url)
                            })
                        SportView.MyTeams -> myTeams(state, viewModel)
                        is SportView.League -> when (view.tab) {
                            0 -> tableItems(state.table, highlightId = null, onTeam = { viewModel.open(SportView.Team(it)) })
                            3 -> leaderItems(state.leaders)
                            else -> matchItems(state.matches, grouped = true, onMatch = { viewModel.open(SportView.Match(it.league, it.id)) })
                        }
                        SportView.Football -> footballItems(onOpen = { viewModel.open(SportView.League(it)) })
                        is SportView.F1 -> when (view.tab) {
                            0 -> f1WeekendItems(state)
                            1 -> f1StandingItems(state.f1Drivers, "Fahrer-Weltmeisterschaft")
                            2 -> f1StandingItems(state.f1Teams, "Konstrukteurs-Weltmeisterschaft")
                            else -> listingItems(state.tvListings, "Formel 1", context, onPlayChannel)
                        }
                        is SportView.Tennis -> when (view.tab) {
                            0 -> tennisItems(state.tennis)
                            1 -> tennisRankingItems(state.tennisRanking, if (view.tour == "atp") "ATP-Weltrangliste" else "WTA-Weltrangliste")
                            else -> listingItems(state.tvListings, "Tennis", context, onPlayChannel)
                        }
                        is SportView.Wrestling -> when (view.tab) {
                            0 -> wrestlingItems(state.wrestling)
                            1 -> listingItems(state.tvListings, "Wrestling", context, onPlayChannel)
                            else -> videoItems(state.videos)
                        }
                        is SportView.Team -> teamItems(view.team, state, viewModel)
                        is SportView.Match -> state.detail?.let { matchDetailItems(it) }
                    }
                }
            }
        }
    }
}

// ---------- Sports-Hub ----------

private fun LazyListScope.hubItems(state: SportUiState, onOpen: (SportMatch) -> Unit) {
    if (state.hubGames.isEmpty()) {
        item { Text("Keine Spiele in den nächsten Tagen.", color = Muted, fontSize = 15.sp) }
        return
    }
    item {
        Text(
            if (state.sourcesLoading) "Suche passende Sender und Streams …" else "Spiel auswählen → Quelle wählen (TV-Sender oder Stremio)",
            color = Muted.copy(alpha = 0.75f), fontSize = 12.sp,
        )
    }
    val sections = linkedMapOf<String, MutableList<SportMatch>>()
    state.hubGames.forEach { m ->
        val title = if (m.isLive) "● Jetzt live" else SportRepository.formatDay(m.startMs)
        sections.getOrPut(title) { mutableListOf() }.add(m)
    }
    sections.forEach { (title, games) ->
        item(key = "h$title") {
            Column {
                Text(title, color = if (title.startsWith("●")) LiveRed else Accent, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 4.dp, horizontal = 2.dp)) {
                    items(games, key = { it.id }) { m ->
                        GameCard(m, state.sources[m.id], state.sourcesLoading, followed = state.isFollowed(m.home.team.id) || state.isFollowed(m.away.team.id)) { onOpen(m) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun GameCard(m: SportMatch, sources: GameSources?, searching: Boolean, followed: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = (Modifier.width(250.dp).height(150.dp)).then(Modifier.fiptvTap(onClick)),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = BackgroundCard, contentColor = Color.White,
            focusedContainerColor = Color(0xFF2A2E3A), focusedContentColor = Color.White,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        border = focusBorder(),
    ) {
        Column(Modifier.fillMaxSize().padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(m.league.label, color = Muted, fontSize = 11.sp, modifier = Modifier.weight(1f), maxLines = 1)
                if (followed) Text("★ ", color = Color(0xFFFFC107), fontSize = 12.sp)
                if (m.isLive) Text("● LIVE ${SportRepository.statusText(m)}", color = LiveRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
                Logo(m.home.team.logo, 46)
                Text(
                    if (m.state == "pre") SportRepository.formatTime(m.startMs) else "${m.home.score ?: "–"}:${m.away.score ?: "–"}",
                    color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                )
                Logo(m.away.team.logo, 46)
            }
            Text("${m.home.team.name} – ${m.away.team.name}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            val src = when {
                sources == null && searching -> "Quellen werden gesucht …"
                sources == null -> " "
                sources.count == 0 -> "keine Quelle gefunden"
                else -> listOfNotNull(
                    sources.tv.size.takeIf { it > 0 }?.let { "$it Sender" },
                    sources.addons.size.takeIf { it > 0 }?.let { "$it Stremio" },
                ).joinToString("  ")
            }
            Text(src, color = if ((sources?.count ?: 0) > 0) Accent else Muted, fontSize = 11.sp, maxLines = 1,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

// ---------- Quellen eines Spiels ----------

private fun LazyListScope.sourceItems(
    m: SportMatch,
    state: SportUiState,
    vm: SportViewModel,
    onPlayChannel: (com.arflix.tv.data.model.IptvChannel) -> Unit,
    onPlayStream: (String, com.arflix.tv.data.repository.SportsAddonStream) -> Unit,
) {
    item {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Logo(m.home.team.logo, 44); Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("${m.home.team.name} – ${m.away.team.name}", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${m.league.label} · " + (if (m.isLive) "LIVE ${SportRepository.statusText(m)} · ${m.home.score ?: "–"}:${m.away.score ?: "–"}"
                    else "${SportRepository.formatDay(m.startMs)} ${SportRepository.formatTime(m.startMs)}"), color = if (m.isLive) LiveRed else Muted, fontSize = 14.sp)
            }
            Spacer(Modifier.width(10.dp)); Logo(m.away.team.logo, 44); Spacer(Modifier.width(16.dp))
            Pill("ℹ Spieldetails", false, { vm.open(SportView.Match(m.league, m.id)) }, small = true)
        }
    }
    val sources = state.sources[m.id]
    if (sources == null) {
        item { Text(if (state.sourcesLoading) "Suche passende Sender und Streams …" else "Keine Angaben.", color = Muted, fontSize = 15.sp) }
        return
    }
    item { Section("TV-Sender (${sources.tv.size})") }
    if (sources.tv.isEmpty()) {
        item {
            Text("Kein passender Sender gefunden. Sender tauchen auf, sobald dein Anbieter das Spiel im Programmführer hat (meist 1–2 Tage vorher) " +
                "oder einen Event-Sender mit beiden Teamnamen anbietet.", color = Muted, fontSize = 14.sp)
        }
    }
    items(sources.tv, key = { "c" + it.channel.id }) { s ->
        FocusCard(onClick = { onPlayChannel(s.channel) }) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(64.dp), contentAlignment = Alignment.Center) { Logo(s.channel.logo, 40) }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.channel.name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val p = s.programme
                    Text(
                        if (p != null) "${SportRepository.formatTime(p.startUtcMillis)}–${SportRepository.formatTime(p.endUtcMillis)}  ${p.title}" else s.channel.group,
                        color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                if (s.uhd) Text("4K ", color = Color(0xFF60A5FA), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(s.confidence, color = when (s.score) { 3 -> Accent; 2 -> Color(0xFFA3E635); else -> Color(0xFFFBBF24) }, fontSize = 12.sp)
            }
        }
    }
    item { Section("Stremio (${sources.addons.size})") }
    if (sources.addons.isEmpty()) {
        item { Text("Kein Stremio-Sport-Addon mit diesem Spiel gefunden.", color = Muted, fontSize = 14.sp) }
    }
    sources.addons.forEach { e ->
        item(key = "a" + e.key) {
            FocusCard(onClick = { vm.resolveAddon(e) }) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(e.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(e.addonName, color = Muted, fontSize = 13.sp, maxLines = 1)
                    }
                    Text(
                        when {
                            state.resolvingAddon == e.key -> "lädt …"
                            state.addonStreams.containsKey(e.key) -> "${state.addonStreams[e.key].orEmpty().size} Streams"
                            else -> "Streams anzeigen ›"
                        },
                        color = Accent, fontSize = 13.sp,
                    )
                }
            }
        }
        val list = state.addonStreams[e.key]
        if (list != null) {
            if (list.isEmpty()) item(key = "a0" + e.key) { Text("   Keine abspielbaren Streams.", color = Muted, fontSize = 13.sp) }
            list.forEachIndexed { i, st ->
                item(key = "s$i" + e.key) {
                    FocusCard(onClick = { onPlayStream(e.title, st) }) {
                        Row(Modifier.fillMaxWidth().padding(start = 32.dp, end = 12.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("▶ ", color = Accent, fontSize = 14.sp)
                            Text(listOfNotNull(st.name, st.description).joinToString(" · "), color = Color.White, fontSize = 14.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            if (st.external) Text("extern", color = Muted, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

// ---------- Meine Teams ----------

private fun LazyListScope.myTeams(state: SportUiState, vm: SportViewModel) {
    item(key = "goal_toggle") { GoalAlertToggle() }
    if (state.followed.isEmpty()) {
        item {
            Text(
                "Du folgst noch keinem Team. Öffne „Fußball“ → einen Wettbewerb → „Teams“ und wähle „Folgen“.",
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
internal fun Cell(text: String, width: Int, bold: Boolean = false, color: Color = Muted) {
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
        modifier = Modifier.fiptvTap({ onOpen(t) }),
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
            val icon = when (e.type) { "goal" -> "Tor ·"; "red" -> "Rot ·"; "yellow" -> "Gelb ·"; else -> "Wechsel ·" }
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
internal fun Section(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp, bottom = 2.dp)) {
        if (icon != null) {
            androidx.tv.material3.Icon(icon, contentDescription = null, tint = Accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(title, color = Accent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun Logo(url: String?, sizeDp: Int) {
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
internal fun FocusCard(onClick: () -> Unit, highlighted: Boolean = false, content: @Composable () -> Unit) {
    // Alle Zeilen sind fokussierbar, damit man auf dem Fernseher mit dem Steuerkreuz durch die Liste scrollen kann
    Surface(
        onClick = onClick,
        modifier = (Modifier.fillMaxWidth()).then(Modifier.fiptvTap(onClick)),
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
internal fun Pill(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, small: Boolean = false, icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    Surface(
        onClick = onClick,
        modifier = (modifier).then(Modifier.fiptvTap(onClick)),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(50)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Accent.copy(alpha = 0.35f) else BackgroundCard,
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                androidx.tv.material3.Icon(icon, contentDescription = null, modifier = Modifier.padding(start = if (small) 12.dp else 14.dp).size(if (small) 18.dp else 20.dp))
            }
            Text(
                label,
                fontSize = if (small) 14.sp else 15.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                modifier = Modifier.padding(start = if (icon != null) 8.dp else if (small) 14.dp else 16.dp, end = if (small) 14.dp else 16.dp, top = if (small) 6.dp else 8.dp, bottom = if (small) 6.dp else 8.dp),
            )
        }
    }
}

@Composable
private fun GoalAlertToggle() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var on by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(FiptvGoalAlerts.enabled(context)) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
        Pill(if (on) "Tor-Hinweise: an" else "Tor-Hinweise: aus", on, icon = com.arflix.tv.fiptv.FiptvIcons.Bell, onClick = {
            on = !on
            FiptvGoalAlerts.setEnabled(context, on)
        }, small = true)
        Spacer(Modifier.width(12.dp))
        Text("Tore, Anpfiff und Abpfiff deiner Teams als kurzer Hinweis – auch während du etwas anderes schaust.", color = Muted, fontSize = 13.sp)
    }
}

private fun subTabIcon(label: String): androidx.compose.ui.graphics.vector.ImageVector? = when (label) {
    "Tabelle" -> com.arflix.tv.fiptv.FiptvIcons.Table
    "Spiele" -> com.arflix.tv.fiptv.FiptvIcons.Calendar
    "Teams" -> com.arflix.tv.fiptv.FiptvIcons.Star
    "Torjäger & Vorlagen" -> com.arflix.tv.fiptv.FiptvIcons.Stats
    "Im TV" -> com.arflix.tv.fiptv.FiptvIcons.OnTv
    "Videos" -> com.arflix.tv.fiptv.FiptvIcons.PlayCircle
    "Rennwochenenden", "Turniere", "Shows" -> com.arflix.tv.fiptv.FiptvIcons.Calendar
    "Fahrer-WM", "Team-WM", "Weltrangliste" -> com.arflix.tv.fiptv.FiptvIcons.Trophy
    else -> null
}
