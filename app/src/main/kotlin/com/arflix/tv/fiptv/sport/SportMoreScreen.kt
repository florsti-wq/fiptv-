package com.arflix.tv.fiptv.sport

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.arflix.tv.data.model.IptvChannel
import com.arflix.tv.fiptv.reminders.FiptvReminder
import com.arflix.tv.fiptv.reminders.FiptvReminders
import com.arflix.tv.fiptv.youtube.YtVideo
import com.arflix.tv.navigation.FiptvNav

// fIPTV: Fußball-Wettbewerbe, Formel 1, Tennis und Wrestling im Sport-Bereich.

private fun day(ms: Long) = if (ms > 0) SportRepository.formatDay(ms) else ""
private fun time(ms: Long) = if (ms > 0) SportRepository.formatTime(ms) else ""

// ---------- Fußball: Wettbewerbe ----------

internal fun LazyListScope.footballItems(onOpen: (SportLeague) -> Unit) {
    LeagueGroup.entries.forEach { group ->
        val leagues = SportLeague.entries.filter { it.group == group }
        item(key = "g_${group.name}") { Section(group.label, when (group) { LeagueGroup.TOP -> com.arflix.tv.fiptv.FiptvIcons.Trophy; LeagueGroup.EUROPE -> com.arflix.tv.fiptv.FiptvIcons.Star; else -> com.arflix.tv.fiptv.FiptvIcons.Football }) }
        items(leagues, key = { "l_${it.code}" }) { lg ->
            FocusCard(onClick = { onOpen(lg) }) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(width = 52.dp, height = 28.dp).clip(RoundedCornerShape(6.dp)).background(Accent.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center,
                    ) { Text(lg.short, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.width(14.dp))
                    Text(lg.label, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(
                        listOfNotNull("Tabelle".takeIf { lg.hasTable }, "Spiele", "Statistik".takeIf { lg.hasStats }).joinToString(" · "),
                        color = Muted, fontSize = 13.sp,
                    )
                }
            }
        }
    }
    item {
        Text(
            "Turniere wie WM und EM zeigen Spiele und Tabellen, sobald sie laufen. Außerhalb der Turnierzeit bleibt die Liste leer.",
            color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp),
        )
    }
}

// ---------- Fußball: Torjäger & Vorlagen ----------

internal fun LazyListScope.leaderItems(categories: List<StatCategory>) {
    if (categories.isEmpty()) {
        item { Text("Für diesen Wettbewerb gibt es (noch) keine Statistik.", color = Muted, fontSize = 15.sp) }
        return
    }
    categories.forEach { cat ->
        item(key = "c_${cat.title}") { Section(cat.title, com.arflix.tv.fiptv.FiptvIcons.Stats) }
        items(cat.leaders, key = { "${cat.title}_${it.rank}_${it.name}" }) { l ->
            FocusCard(onClick = {}) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Cell(l.rank.toString(), 30, bold = true, color = Color.White)
                    Logo(l.teamLogo, 26); Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(l.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text(listOf(l.team, l.detail).filter { it.isNotBlank() }.joinToString(" · "), color = Muted, fontSize = 12.sp, maxLines = 1)
                    }
                    Text(l.value, color = Accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ---------- Formel 1 ----------

internal fun LazyListScope.f1WeekendItems(state: SportUiState) {
    val now = System.currentTimeMillis()
    val weekends = state.f1Weekends
    if (weekends.isEmpty()) {
        item { Text("Gerade keine Rennwochenenden.", color = Muted, fontSize = 15.sp) }
    }
    // Aktuelles/nächstes Wochenende zuerst, danach die letzten
    val upcoming = weekends.filter { w -> w.sessions.any { it.state != "post" } }
    val past = weekends.filter { w -> w.sessions.all { it.state == "post" } }.reversed()
    (upcoming + past).forEach { w ->
        item(key = "w_${w.id}") {
            val live = w.sessions.any { it.state == "in" }
            Section((if (live) "● LIVE · " else "") + w.name)
            Text(listOf(w.circuit, w.city).filter { it.isNotBlank() }.joinToString(" · "), color = Muted, fontSize = 13.sp)
        }
        items(w.sessions, key = { "s_${w.id}_${it.label}_${it.startMs}" }) { s ->
            FocusCard(onClick = {}) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(s.label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(140.dp))
                        Text(
                            when (s.state) {
                                "in" -> "● läuft gerade"
                                "post" -> "${day(s.startMs)} · beendet"
                                else -> "${day(s.startMs)} ${time(s.startMs)} Uhr"
                            },
                            color = if (s.state == "in") LiveRed else Muted, fontSize = 13.sp, modifier = Modifier.weight(1f),
                        )
                    }
                    if (s.results.isNotEmpty() && s.state != "pre") {
                        val top = if (s.label == "Rennen") s.results.take(10) else s.results.take(3)
                        Text(
                            top.joinToString("   ") { "${it.position}. ${it.driver}" },
                            color = if (s.label == "Rennen") Color.White else Muted, fontSize = 13.sp, maxLines = if (s.label == "Rennen") 3 else 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                }
            }
        }
    }
    val rest = state.f1Calendar.filter { it.endMs > now && weekends.none { w -> w.name == it.name } }
    if (rest.isNotEmpty()) {
        item { Section("Weitere Rennen der Saison", com.arflix.tv.fiptv.FiptvIcons.Calendar) }
        items(rest, key = { "cal_${it.name}_${it.startMs}" }) { c ->
            FocusCard(onClick = {}) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(c.name, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Text("${day(c.startMs)} – ${day(c.endMs)}", color = Muted, fontSize = 13.sp)
                }
            }
        }
    }
}

internal fun LazyListScope.f1StandingItems(list: List<F1Standing>, title: String) {
    item { Section(title, com.arflix.tv.fiptv.FiptvIcons.Trophy) }
    if (list.isEmpty()) item { Text("Keine Wertung verfügbar.", color = Muted, fontSize = 15.sp) }
    items(list, key = { "st_${title}_${it.rank}_${it.name}" }) { r ->
        FocusCard(onClick = {}, highlighted = r.rank == 1) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Cell(r.rank.toString(), 34, bold = true, color = Color.White)
                Logo(r.flag, 22); Spacer(Modifier.width(12.dp))
                Text(r.name, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f), maxLines = 1)
                Text("${r.points} Pkt", color = Accent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ---------- Tennis ----------

internal fun LazyListScope.tennisItems(tournaments: List<TennisTournament>) {
    if (tournaments.isEmpty()) {
        item { Text("Diese Woche keine Turniere (oder Saisonpause).", color = Muted, fontSize = 15.sp) }
        return
    }
    tournaments.forEach { t ->
        item(key = "t_${t.id}") { Section(t.name, if (t.major) com.arflix.tv.fiptv.FiptvIcons.Trophy else com.arflix.tv.fiptv.FiptvIcons.Tennis) }
        items(t.matches.take(40), key = { "m_${t.id}_${it.id}" }) { m ->
            FocusCard(onClick = {}) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.width(130.dp)) {
                        Text(m.round, color = Muted, fontSize = 12.sp, maxLines = 1)
                        Text(
                            when (m.state) { "in" -> "● LIVE"; "post" -> "beendet"; else -> "${day(m.startMs)} ${time(m.startMs)}" },
                            color = if (m.isLive) LiveRed else Muted, fontSize = 12.sp, fontWeight = if (m.isLive) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        PlayerLine(m.p1); PlayerLine(m.p2)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerLine(p: TennisPlayer) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Logo(p.flag, 18); Spacer(Modifier.width(8.dp))
        Text(p.name, color = Color.White, fontSize = 15.sp, fontWeight = if (p.winner) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        p.sets.forEach { Text(it, color = if (p.winner) Color.White else Muted, fontSize = 15.sp, modifier = Modifier.width(38.dp)) }
    }
}

internal fun LazyListScope.tennisRankingItems(list: List<TennisRank>, title: String) {
    item { Section(title, com.arflix.tv.fiptv.FiptvIcons.Trophy) }
    if (list.isEmpty()) item { Text("Keine Rangliste verfügbar.", color = Muted, fontSize = 15.sp) }
    items(list, key = { "r_${it.rank}_${it.name}" }) { r ->
        FocusCard(onClick = {}) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Cell(r.rank.toString(), 40, bold = true, color = Color.White)
                Logo(r.flag, 20); Spacer(Modifier.width(12.dp))
                Text(r.name, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f), maxLines = 1)
                Text(r.trend, color = if (r.trend.startsWith("▲")) Accent else LiveRed, fontSize = 12.sp, modifier = Modifier.width(48.dp))
                Text("${r.points} Pkt", color = Muted, fontSize = 14.sp)
            }
        }
    }
}

// ---------- Wrestling ----------

internal fun LazyListScope.wrestlingItems(shows: List<WrestlingShow>) {
    if (shows.isEmpty()) {
        item { Text("Keine Show-Daten verfügbar.", color = Muted, fontSize = 15.sp) }
        return
    }
    val (next, past) = shows.partition { it.upcoming }
    if (next.isNotEmpty()) item { Section("Nächste Shows", com.arflix.tv.fiptv.FiptvIcons.Calendar) }
    items(next, key = { "n_${it.promotion}_${it.name}" }) { ShowCard(it) }
    if (past.isNotEmpty()) item { Section("Letzte Shows & Ergebnisse", com.arflix.tv.fiptv.FiptvIcons.Wrestling) }
    items(past.reversed(), key = { "p_${it.promotion}_${it.name}" }) { ShowCard(it) }
    item {
        Text("Ausstrahlung in Deutschland: WWE Raw, SmackDown & große Events bei Netflix, AEW u. a. bei DAZN/TNT. " +
            "Unter „Im TV“ siehst du, was dein Anbieter gerade zeigt.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun ShowCard(s: WrestlingShow) {
    FocusCard(onClick = {}) {
        Row(Modifier.fillMaxWidth().padding(12.dp)) {
            if (s.image != null) {
                AsyncImage(model = s.image, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.width(150.dp).height(84.dp).clip(RoundedCornerShape(8.dp)))
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text("${s.promotion} · ${s.name}", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(listOf("${day(s.startMs)} ${time(s.startMs)} Uhr", s.venue).filter { it.isNotBlank() }.joinToString(" · "), color = Muted, fontSize = 13.sp)
                if (s.card.isNotBlank()) {
                    Text(s.card.lines().filter { it.isNotBlank() }.take(8).joinToString("\n"), color = Color.White.copy(alpha = 0.85f),
                        fontSize = 13.sp, maxLines = 8, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

internal fun LazyListScope.videoItems(videos: List<YtVideo>) {
    if (videos.isEmpty()) {
        item { Text("Keine Videos geladen.", color = Muted, fontSize = 15.sp) }
        return
    }
    item { Section("Neueste Videos von WWE und AEW (offizieller YouTube-Player)", com.arflix.tv.fiptv.FiptvIcons.PlayCircle) }
    items(videos.take(40), key = { "v_${it.id}" }) { v ->
        FocusCard(onClick = {
            com.arflix.tv.fiptv.search.FiptvSearchBridge.youtube = v
            FiptvNav.open(FiptvNav.YOUTUBE)
        }) {
            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(model = v.thumbnail, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.width(140.dp).height(79.dp).clip(RoundedCornerShape(6.dp)))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(v.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(v.channelTitle, color = Muted, fontSize = 12.sp)
                }
            }
        }
    }
}

// ---------- Im TV (Programmführer) ----------

internal fun LazyListScope.listingItems(list: List<TvSource>, sport: String, context: Context, onPlayChannel: (IptvChannel) -> Unit) {
    item { Section("$sport im TV-Programm deines Anbieters (nächste 3 Tage)", com.arflix.tv.fiptv.FiptvIcons.OnTv) }
    if (list.isEmpty()) {
        item {
            Text("Nichts gefunden. Entweder läuft gerade nichts, oder für die Sport-Sender fehlt der Programmführer " +
                "(Tipp: zusätzliche EPG-Quelle in den IPTV-Einstellungen).", color = Muted, fontSize = 14.sp)
        }
        return
    }
    val now = System.currentTimeMillis()
    items(list, key = { "tv_${it.channel.id}_${it.programme?.startUtcMillis}" }) { s ->
        val p = s.programme
        val live = p?.isLive(now) == true
        FocusCard(onClick = {
            if (live || p == null) onPlayChannel(s.channel)
            else {
                val set = FiptvReminders.toggle(context, FiptvReminder(s.channel.id, s.channel.name, p.title, p.startUtcMillis, p.endUtcMillis))
                Toast.makeText(context, if (set) "Gemerkt: ${p.title} – Hinweis kommt kurz vorher" else "Erinnerung entfernt", Toast.LENGTH_SHORT).show()
            }
        }) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(64.dp), contentAlignment = Alignment.Center) { Logo(s.channel.logo, 40) }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(p?.title ?: s.channel.name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${s.channel.name} · " + if (p != null) "${day(p.startUtcMillis)} ${time(p.startUtcMillis)}–${time(p.endUtcMillis)}" else "",
                        color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                if (s.uhd) Text("4K ", color = Color(0xFF60A5FA), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(if (live) "● LIVE ansehen" else "Merken", color = if (live) LiveRed else Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
