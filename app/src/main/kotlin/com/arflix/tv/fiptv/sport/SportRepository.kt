package com.arflix.tv.fiptv.sport

import com.arflix.tv.network.OkHttpProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Gruppen der Fußball-Wettbewerbe */
enum class LeagueGroup(val label: String) {
    TOP("🏆 Top-Ligen Europas"),
    GERMANY("🇩🇪 Deutschland"),
    EUROPE("⭐ Europapokal"),
    NATIONAL("🌍 Nationalmannschaften"),
    FRIENDLY("🤝 Freundschaftsspiele"),
}

/** Wettbewerbe, die fIPTV anzeigt (ESPN-Kürzel). */
enum class SportLeague(
    val code: String,
    val label: String,
    val short: String,
    val group: LeagueGroup,
    val hasTable: Boolean = true,
    val hasStats: Boolean = true,
    /** Nationalmannschaften statt Vereine */
    val national: Boolean = false,
) {
    BUNDESLIGA("ger.1", "Bundesliga", "BL", LeagueGroup.TOP),
    PREMIER_LEAGUE("eng.1", "Premier League", "PL", LeagueGroup.TOP),
    LA_LIGA("esp.1", "LaLiga", "LL", LeagueGroup.TOP),
    SERIE_A("ita.1", "Serie A", "SA", LeagueGroup.TOP),
    LIGUE_1("fra.1", "Ligue 1", "L1", LeagueGroup.TOP),
    BUNDESLIGA_2("ger.2", "2. Bundesliga", "BL2", LeagueGroup.GERMANY),
    DFB_POKAL("ger.dfb_pokal", "DFB-Pokal", "DFB", LeagueGroup.GERMANY, hasTable = false),
    CHAMPIONS_LEAGUE("uefa.champions", "Champions League", "CL", LeagueGroup.EUROPE),
    EUROPA_LEAGUE("uefa.europa", "Europa League", "EL", LeagueGroup.EUROPE),
    CONFERENCE_LEAGUE("uefa.europa.conf", "Conference League", "ECL", LeagueGroup.EUROPE),
    WORLD_CUP("fifa.world", "Weltmeisterschaft", "WM", LeagueGroup.NATIONAL, national = true),
    EURO("uefa.euro", "Europameisterschaft", "EM", LeagueGroup.NATIONAL, national = true),
    NATIONS_LEAGUE("uefa.nations", "UEFA Nations League", "UNL", LeagueGroup.NATIONAL, national = true),
    WC_QUALI_EUROPE("fifa.worldq.uefa", "WM-Qualifikation Europa", "WMQ", LeagueGroup.NATIONAL, national = true),
    EURO_QUALI("uefa.euroq", "EM-Qualifikation", "EMQ", LeagueGroup.NATIONAL, national = true),
    FRIENDLIES("fifa.friendly", "Länderspiele (Freundschaft)", "LS", LeagueGroup.FRIENDLY, hasTable = false, hasStats = false, national = true),
    CLUB_FRIENDLIES("club.friendly", "Vereins-Testspiele", "TS", LeagueGroup.FRIENDLY, hasTable = false, hasStats = false);

    companion object {
        fun byCode(code: String?): SportLeague? = entries.firstOrNull { it.code == code }
    }
}

/** Bestenliste einer Liga (Torjäger, Vorlagen) */
data class StatLeader(val rank: Int, val name: String, val team: String, val teamLogo: String?, val value: String, val detail: String)
data class StatCategory(val title: String, val leaders: List<StatLeader>)

data class SportTeam(
    val id: String,
    val name: String,
    val logo: String?,
    /** Liga, über die das Team gefunden wurde (für Abfragen) */
    val league: String = "ger.1",
    /** vollständiger Name laut ESPN (für die Suche nach Sendern) */
    val fullName: String = "",
)

data class SportSide(val team: SportTeam, val score: String?, val winner: Boolean)

data class SportMatch(
    val id: String,
    val league: SportLeague,
    val startMs: Long,
    val home: SportSide,
    val away: SportSide,
    /** pre / in / post */
    val state: String,
    /** z. B. „FT“, „45'“, „HT“ */
    val detail: String,
) {
    val isLive: Boolean get() = state == "in"
    val isFinished: Boolean get() = state == "post"
    fun involves(teamId: String) = home.team.id == teamId || away.team.id == teamId
}

data class SportTableRow(
    val rank: Int,
    val team: SportTeam,
    val played: String,
    val won: String,
    val drawn: String,
    val lost: String,
    val goals: String,
    val diff: String,
    val points: String,
)

data class SportKeyEvent(val minute: String, val type: String, val teamId: String?, val text: String)

data class SportPlayer(val name: String, val jersey: String, val position: String, val subbedIn: Boolean, val subbedOut: Boolean)

data class SportLineup(val team: SportTeam, val formation: String?, val starters: List<SportPlayer>, val subs: List<SportPlayer>)

data class SportStat(val label: String, val home: String, val away: String)

data class SportMatchDetail(
    val match: SportMatch,
    val events: List<SportKeyEvent>,
    val lineups: List<SportLineup>,
    val stats: List<SportStat>,
    val venue: String?,
)

/**
 * fIPTV: Sportdaten (Bundesliga, Champions League) über die öffentliche ESPN-Schnittstelle.
 * Kein Schlüssel nötig. Nur Lesen – Tabellen, Spiele, Aufstellungen, Ereignisse.
 */
object SportRepository {
    private const val SITE = "https://site.api.espn.com/apis/site/v2/sports/soccer"
    private const val V2 = "https://site.api.espn.com/apis/v2/sports/soccer"

    // ---------- Abfragen ----------

    suspend fun teams(league: SportLeague): List<SportTeam> {
        val j = get("$SITE/${league.code}/teams")
        val arr = j.optJSONArray("sports")?.optJSONObject(0)?.optJSONArray("leagues")?.optJSONObject(0)?.optJSONArray("teams")
        return arr.objects().mapNotNull { it.optJSONObject("team")?.let { t -> team(t, league.code) } }
            .sortedBy { it.name }
    }

    suspend fun table(league: SportLeague): List<SportTableRow> {
        val j = get("$V2/${league.code}/standings")
        val groups = j.optJSONArray("children").objects().ifEmpty { listOf(j) }
        val rows = groups.flatMap { g ->
            g.optJSONObject("standings")?.optJSONArray("entries").objects().mapNotNull { e ->
                val t = e.optJSONObject("team")?.let { team(it, league.code) } ?: return@mapNotNull null
                val stats = e.optJSONArray("stats").objects().associate { it.optString("name") to it.optString("displayValue") }
                SportTableRow(
                    rank = stats["rank"]?.toIntOrNull() ?: 0,
                    team = t,
                    played = stats["gamesPlayed"].orEmpty(),
                    won = stats["wins"].orEmpty(),
                    drawn = stats["ties"].orEmpty(),
                    lost = stats["losses"].orEmpty(),
                    goals = "${stats["pointsFor"].orEmpty()}:${stats["pointsAgainst"].orEmpty()}",
                    diff = stats["pointDifferential"].orEmpty(),
                    points = stats["points"].orEmpty(),
                )
            }
        }
        return rows.sortedBy { if (it.rank > 0) it.rank else 999 }
    }

    /** Spiele von gestern bis in 8 Tagen; ist da nichts, die aktuelle Runde. */
    suspend fun matches(league: SportLeague): List<SportMatch> {
        val today = LocalDate.now()
        val f = DateTimeFormatter.BASIC_ISO_DATE
        val range = "${today.minusDays(3).format(f)}-${today.plusDays(8).format(f)}"
        val list = parseEvents(get("$SITE/${league.code}/scoreboard?dates=$range&limit=200"), league)
        return list.ifEmpty { parseEvents(get("$SITE/${league.code}/scoreboard"), league) }.sortedBy { it.startMs }
    }

    /** Wettbewerbe, in denen ein Team spielen kann (Liga, Pokal, Europapokal bzw. Länderspiele). */
    fun competitionsOf(team: SportTeam): List<SportLeague> {
        val own = SportLeague.byCode(team.league) ?: SportLeague.BUNDESLIGA
        if (own.national) return SportLeague.entries.filter { it.national }
        val country = own.code.substringBefore('.')
        val domestic = SportLeague.entries.filter { !it.national && it.code.startsWith("$country.") }
        val europe = SportLeague.entries.filter { it.group == LeagueGroup.EUROPE }
        return (listOf(own) + domestic + europe + SportLeague.CLUB_FRIENDLIES).distinct()
    }

    /** Alle Spiele eines Teams in seinen Wettbewerben (vergangene und kommende). */
    suspend fun teamMatches(team: SportTeam): List<SportMatch> = teamMatches(team.id, competitionsOf(team))

    suspend fun teamMatches(teamId: String, leagues: List<SportLeague> = SportLeague.entries.filter { it.group == LeagueGroup.TOP || it.group == LeagueGroup.EUROPE }): List<SportMatch> = coroutineScope {
        leagues.flatMap { lg ->
            listOf("", "?fixture=true").map { q ->
                async {
                    runCatching { parseEvents(get("$SITE/${lg.code}/teams/$teamId/schedule$q"), lg) }.getOrDefault(emptyList())
                }
            }
        }.awaitAll().flatten().distinctBy { it.id }.sortedBy { it.startMs }
    }

    /** Torjäger und Vorlagengeber einer Liga. */
    suspend fun leaders(league: SportLeague): List<StatCategory> {
        val j = get("$SITE/${league.code}/statistics")
        return j.optJSONArray("stats").objects().mapNotNull { cat ->
            val title = when (cat.optString("name")) {
                "goalsLeaders" -> "⚽ Torjäger"
                "assistsLeaders" -> "🅰 Vorlagen"
                else -> cat.optString("displayName").ifBlank { return@mapNotNull null }
            }
            val leaders = cat.optJSONArray("leaders").objects().take(20).mapIndexedNotNull { i, l ->
                val a = l.optJSONObject("athlete") ?: return@mapIndexedNotNull null
                val t = a.optJSONObject("team")
                val stats = a.optJSONArray("statistics").objects().associate { it.optString("name") to it.optString("displayValue") }
                StatLeader(
                    rank = i + 1,
                    name = a.optString("displayName"),
                    team = t?.let { germanName(it.optString("shortDisplayName", it.optString("name")), it.optString("displayName")) }.orEmpty(),
                    teamLogo = t?.optJSONArray("logos")?.optJSONObject(0)?.optString("href")?.takeIf { it.isNotBlank() },
                    value = l.optDouble("value").takeIf { !it.isNaN() }?.let { v -> if (v % 1.0 == 0.0) v.toInt().toString() else v.toString() } ?: l.optString("displayValue"),
                    detail = stats["appearances"]?.let { "$it Spiele" } ?: l.optString("displayValue").replace("Matches", "Spiele").replace("Goals", "Tore").replace("Assists", "Vorlagen"),
                )
            }
            StatCategory(title, leaders).takeIf { leaders.isNotEmpty() }
        }
    }

    suspend fun matchDetail(league: SportLeague, eventId: String): SportMatchDetail {
        val j = get("$SITE/${league.code}/summary?event=$eventId")
        val comp = j.optJSONObject("header")?.optJSONArray("competitions")?.optJSONObject(0) ?: JSONObject()
        val match = parseCompetition(eventId, comp.optString("date"), comp, league)
            ?: error("Spiel nicht gefunden")
        val events = j.optJSONArray("keyEvents").objects().mapNotNull { k ->
            val type = k.optJSONObject("type")?.optString("text").orEmpty()
            val goal = k.optBoolean("scoringPlay") || type.contains("Goal", true)
            val card = type.contains("Card", true)
            val sub = type.contains("Substitution", true)
            if (!goal && !card && !sub) return@mapNotNull null
            val who = k.optJSONArray("participants").objects()
                .mapNotNull { it.optJSONObject("athlete")?.optString("displayName")?.takeIf { n -> n.isNotBlank() } }
            SportKeyEvent(
                minute = k.optJSONObject("clock")?.optString("displayValue").orEmpty(),
                type = when {
                    goal -> "goal"
                    type.contains("Red", true) -> "red"
                    card -> "yellow"
                    else -> "sub"
                },
                teamId = k.optJSONObject("team")?.optString("id"),
                text = if (sub && who.size >= 2) "${who[0]} für ${who[1]}" else who.joinToString(", ").ifBlank { type },
            )
        }
        val lineups = j.optJSONArray("rosters").objects().mapNotNull { r ->
            val t = r.optJSONObject("team")?.let { team(it, league.code) } ?: return@mapNotNull null
            val players = r.optJSONArray("roster").objects().map { p ->
                p.optBoolean("starter") to SportPlayer(
                    name = p.optJSONObject("athlete")?.optString("displayName").orEmpty(),
                    jersey = p.optString("jersey"),
                    position = p.optJSONObject("position")?.optString("abbreviation").orEmpty(),
                    subbedIn = p.optBoolean("subbedIn"),
                    subbedOut = p.optBoolean("subbedOut"),
                )
            }
            SportLineup(
                team = t,
                formation = r.optString("formation").takeIf { it.isNotBlank() },
                starters = players.filter { it.first }.map { it.second },
                subs = players.filterNot { it.first }.map { it.second },
            ).takeIf { it.starters.isNotEmpty() || it.subs.isNotEmpty() }
        }
        val boxTeams = j.optJSONObject("boxscore")?.optJSONArray("teams").objects()
        val homeId = match.home.team.id
        val homeStats = boxTeams.firstOrNull { it.optJSONObject("team")?.optString("id") == homeId } ?: boxTeams.getOrNull(0)
        val awayStats = boxTeams.firstOrNull { it != homeStats }
        val wanted = listOf(
            "possessionPct" to "Ballbesitz %", "totalShots" to "Schüsse", "shotsOnTarget" to "Schüsse aufs Tor",
            "wonCorners" to "Ecken", "foulsCommitted" to "Fouls", "offsides" to "Abseits",
            "yellowCards" to "Gelbe Karten", "redCards" to "Rote Karten", "saves" to "Paraden",
        )
        fun statMap(o: JSONObject?) = o?.optJSONArray("statistics").objects().associate { it.optString("name") to it.optString("displayValue") }
        val hs = statMap(homeStats)
        val aws = statMap(awayStats)
        val stats = wanted.mapNotNull { (key, label) ->
            val h = hs[key]; val a = aws[key]
            if (h.isNullOrBlank() && a.isNullOrBlank()) null else SportStat(label, h.orEmpty(), a.orEmpty())
        }
        val venue = j.optJSONObject("gameInfo")?.optJSONObject("venue")?.optString("fullName")?.takeIf { it.isNotBlank() }
        return SportMatchDetail(match, events, lineups, stats, venue)
    }

    // ---------- Auswertung ----------

    private fun parseEvents(j: JSONObject, league: SportLeague): List<SportMatch> =
        j.optJSONArray("events").objects().mapNotNull { e ->
            val comp = e.optJSONArray("competitions")?.optJSONObject(0) ?: return@mapNotNull null
            parseCompetition(e.optString("id"), e.optString("date"), comp, league)
        }

    private fun parseCompetition(id: String, date: String, comp: JSONObject, league: SportLeague): SportMatch? {
        val sides = comp.optJSONArray("competitors").objects().mapNotNull { c ->
            val t = c.optJSONObject("team")?.let { team(it, league.code) } ?: return@mapNotNull null
            val score = when (val s = c.opt("score")) {
                is JSONObject -> s.optString("displayValue").ifBlank { s.optString("value") }
                is String -> s
                is Number -> s.toInt().toString()
                else -> null
            }?.takeIf { it.isNotBlank() }
            c.optString("homeAway") to SportSide(t, score, c.optBoolean("winner"))
        }
        val home = sides.firstOrNull { it.first == "home" }?.second ?: sides.getOrNull(0)?.second ?: return null
        val away = sides.firstOrNull { it.first == "away" }?.second ?: sides.getOrNull(1)?.second ?: return null
        val status = comp.optJSONObject("status")
        val type = status?.optJSONObject("type")
        val state = type?.optString("state").orEmpty().ifBlank { "pre" }
        val detail = when (state) {
            "in" -> status?.optString("displayClock")?.takeIf { it.isNotBlank() && it != "0'" } ?: type?.optString("shortDetail").orEmpty()
            else -> type?.optString("shortDetail").orEmpty()
        }
        val start = runCatching { Instant.parse(normalizeDate(date.ifBlank { comp.optString("date") })).toEpochMilli() }.getOrDefault(0L)
        return SportMatch(id, league, start, home, away, state, detail)
    }

    /** ESPN liefert „2026-09-05T16:30Z“ (ohne Sekunden). */
    private fun normalizeDate(d: String): String =
        if (Regex("""T\d{2}:\d{2}Z$""").containsMatchIn(d)) d.dropLast(1) + ":00Z" else d

    private fun team(t: JSONObject, league: String): SportTeam? {
        val id = t.optString("id").takeIf { it.isNotBlank() } ?: return null
        val name = germanName(t.optString("shortDisplayName").ifBlank { t.optString("displayName") }, t.optString("displayName"))
        val logo = t.optJSONArray("logos")?.optJSONObject(0)?.optString("href")?.takeIf { it.isNotBlank() }
            ?: t.optString("logo").takeIf { it.isNotBlank() }
            ?: "https://a.espncdn.com/i/teamlogos/soccer/500/$id.png"
        return SportTeam(id, name, logo, league, t.optString("displayName"))
    }

    /** ESPN nennt deutsche Vereine englisch – die gängigen Namen eindeutschen. */
    private fun germanName(short: String, full: String): String {
        val map = mapOf(
            "Bayern Munich" to "FC Bayern", "FC Cologne" to "1. FC Köln", "Cologne" to "1. FC Köln",
            "Borussia Monchengladbach" to "M'gladbach", "Monchengladbach" to "M'gladbach", "Gladbach" to "M'gladbach",
            "Borussia Dortmund" to "Dortmund", "Bayer Leverkusen" to "Leverkusen", "Leverkusen" to "Leverkusen",
            "Eintracht Frankfurt" to "Frankfurt", "VfB Stuttgart" to "Stuttgart", "SC Freiburg" to "Freiburg",
            "TSG Hoffenheim" to "Hoffenheim", "Hoffenheim" to "Hoffenheim", "Mainz" to "Mainz 05", "FSV Mainz 05" to "Mainz 05",
            "VfL Wolfsburg" to "Wolfsburg", "Werder Bremen" to "Werder Bremen", "Union Berlin" to "Union Berlin",
            "FC Augsburg" to "Augsburg", "Heidenheim" to "Heidenheim", "1. FC Heidenheim 1846" to "Heidenheim",
            "St. Pauli" to "St. Pauli", "FC St. Pauli" to "St. Pauli", "Hamburg SV" to "Hamburger SV", "Hamburg" to "Hamburger SV",
            "Schalke 04" to "Schalke 04", "Hertha Berlin" to "Hertha BSC", "Nuremberg" to "1. FC Nürnberg",
            "Bayern" to "FC Bayern", "Inter Milan" to "Inter Mailand", "AC Milan" to "AC Mailand",
            "Barcelona" to "FC Barcelona", "Juventus" to "Juventus Turin", "Sporting CP" to "Sporting Lissabon",
            "Benfica" to "Benfica Lissabon", "Club Brugge" to "Club Brügge", "Napoli" to "SSC Neapel",
            "Atletico Madrid" to "Atlético Madrid", "Red Bull Salzburg" to "RB Salzburg",
            // Nationalmannschaften
            "Germany" to "Deutschland", "Austria" to "Österreich", "Switzerland" to "Schweiz", "Netherlands" to "Niederlande",
            "France" to "Frankreich", "Spain" to "Spanien", "Italy" to "Italien", "Belgium" to "Belgien", "Croatia" to "Kroatien",
            "Denmark" to "Dänemark", "Poland" to "Polen", "Czechia" to "Tschechien", "Czech Republic" to "Tschechien",
            "Hungary" to "Ungarn", "Scotland" to "Schottland", "Turkey" to "Türkei", "Türkiye" to "Türkei", "Sweden" to "Schweden",
            "Norway" to "Norwegen", "Luxembourg" to "Luxemburg", "Brazil" to "Brasilien", "Argentina" to "Argentinien",
            "United States" to "USA", "Greece" to "Griechenland", "Serbia" to "Serbien", "Slovakia" to "Slowakei",
            "Slovenia" to "Slowenien", "Romania" to "Rumänien", "Ukraine" to "Ukraine", "Ireland" to "Irland",
            "Republic of Ireland" to "Irland", "Northern Ireland" to "Nordirland", "Iceland" to "Island", "Finland" to "Finnland",
            "Albania" to "Albanien", "Georgia" to "Georgien", "Bosnia-Herzegovina" to "Bosnien-Herzegowina", "Japan" to "Japan",
            "Mexico" to "Mexiko", "Morocco" to "Marokko", "South Korea" to "Südkorea", "Colombia" to "Kolumbien",
        )
        return map[short] ?: map[full] ?: short.ifBlank { full }
    }

    // ---------- Anzeige ----------

    private val dayFormat = DateTimeFormatter.ofPattern("EE dd.MM.", Locale.GERMANY)
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMANY)

    fun formatDay(ms: Long): String {
        if (ms <= 0) return ""
        val d = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
        val today = LocalDate.now()
        return when (d) {
            today -> "Heute"
            today.plusDays(1) -> "Morgen"
            today.minusDays(1) -> "Gestern"
            else -> d.format(dayFormat)
        }
    }

    fun formatTime(ms: Long): String =
        if (ms <= 0) "" else Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(timeFormat)

    /** Status kurz auf Deutsch. */
    fun statusText(m: SportMatch): String = when {
        m.isLive -> when (m.detail.uppercase()) {
            "HT", "HALFTIME" -> "Halbzeit"
            else -> m.detail.ifBlank { "Live" }
        }
        m.isFinished -> when {
            m.detail.contains("Pen", true) -> "n. E."
            m.detail.contains("AET", true) || m.detail.contains("ET", false) -> "n. V."
            else -> "Ende"
        }
        m.detail.contains("Postponed", true) -> "Verlegt"
        m.detail.contains("Canceled", true) -> "Abgesagt"
        else -> formatTime(m.startMs)
    }

    // ---------- Netzwerk ----------

    private suspend fun get(url: String): JSONObject = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).header("Accept", "application/json").build()
        OkHttpProvider.client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) error("Sportdaten nicht erreichbar (Fehler ${r.code})")
            JSONObject(r.body?.string().orEmpty())
        }
    }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
}
