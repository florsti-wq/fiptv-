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
import java.time.format.DateTimeFormatter

// ---------- Formel 1 ----------

data class F1Result(val position: Int, val driver: String, val flag: String?, val winner: Boolean)
data class F1Session(val label: String, val startMs: Long, val state: String, val results: List<F1Result>)
data class F1Weekend(val id: String, val name: String, val circuit: String, val city: String, val startMs: Long, val state: String, val sessions: List<F1Session>) {
    val race: F1Session? get() = sessions.lastOrNull { it.label == "Rennen" }
}
data class F1Standing(val rank: Int, val name: String, val points: String, val flag: String?)
data class F1Calendar(val name: String, val startMs: Long, val endMs: Long)

// ---------- Tennis ----------

data class TennisPlayer(val name: String, val flag: String?, val winner: Boolean, val sets: List<String>)
data class TennisMatch(val id: String, val round: String, val startMs: Long, val state: String, val detail: String, val p1: TennisPlayer, val p2: TennisPlayer, val draw: String) {
    val isLive get() = state == "in"
}
data class TennisTournament(val id: String, val name: String, val major: Boolean, val startMs: Long, val matches: List<TennisMatch>)
data class TennisRank(val rank: Int, val name: String, val points: String, val flag: String?, val trend: String)

// ---------- Wrestling ----------

data class WrestlingShow(val promotion: String, val name: String, val startMs: Long, val venue: String, val image: String?, val card: String) {
    val upcoming get() = startMs > System.currentTimeMillis() - 4 * 3600_000L
}

/**
 * fIPTV: weitere Sportarten. Formel 1 und Tennis über die öffentliche ESPN-Schnittstelle,
 * WWE und AEW über TheSportsDB (frei nutzbarer Test-Schlüssel). Nur Lesen, kein Konto.
 */
object MoreSportsRepository {
    private const val SITE = "https://site.api.espn.com/apis/site/v2/sports"
    private const val V2 = "https://site.api.espn.com/apis/v2/sports"
    private const val SPORTSDB = "https://www.thesportsdb.com/api/v1/json/123"
    const val WWE_LEAGUE = "4444"
    const val AEW_LEAGUE = "4563"
    const val WWE_YOUTUBE = "UCJ5v_MCY6GNUBTO8-D3XoAg"
    const val AEW_YOUTUBE = "UCFN4JkGP_bVhAdBsoV9xftA"

    private val sessionNames = mapOf(
        "FP1" to "1. Training", "FP2" to "2. Training", "FP3" to "3. Training", "Qual" to "Qualifying",
        "SS" to "Sprint-Quali", "SQ" to "Sprint-Quali", "Sprint" to "Sprint", "Race" to "Rennen",
    )

    // ---------- Formel 1 ----------

    /** Rennwochenenden von vor 3 Wochen bis in 6 Wochen, dazu der Saisonkalender. */
    suspend fun f1Weekends(): Pair<List<F1Weekend>, List<F1Calendar>> {
        val today = LocalDate.now()
        // ESPN: Datumsbereiche liefern Fehler 400 → Saison(en) holen und auf -3/+6 Wochen filtern
        val from = today.minusDays(21)
        val to = today.plusDays(45)
        val seasons = listOf(from.year, to.year).distinct().mapNotNull { y -> runCatching { get("$SITE/racing/f1/scoreboard?dates=$y") }.getOrNull() }
        if (seasons.isEmpty()) error("Formel-1-Daten nicht erreichbar")
        val events = seasons.flatMap { it.optJSONArray("events").objects() }.filter { e ->
            val d = runCatching { LocalDate.parse(e.optString("date").take(10)) }.getOrNull()
            d == null || (!d.isBefore(from) && !d.isAfter(to))
        }
        val j = org.json.JSONObject().put("events", org.json.JSONArray(events))
            .put("leagues", (seasons.firstOrNull { it.optJSONObject("season")?.optInt("year") == today.year } ?: seasons.last()).optJSONArray("leagues") ?: org.json.JSONArray())
        val weekends = j.optJSONArray("events").objects().map { e ->
            val circuit = e.optJSONObject("circuit")
            val sessions = e.optJSONArray("competitions").objects().map { c ->
                val type = c.optJSONObject("type")?.optString("abbreviation").orEmpty()
                F1Session(
                    label = sessionNames[type] ?: type,
                    startMs = parseTime(c.optString("date")),
                    state = c.optJSONObject("status")?.optJSONObject("type")?.optString("state").orEmpty(),
                    results = c.optJSONArray("competitors").objects().mapNotNull { r ->
                        val a = r.optJSONObject("athlete") ?: return@mapNotNull null
                        F1Result(r.optInt("order"), a.optString("displayName"), a.optJSONObject("flag")?.optString("href"), r.optBoolean("winner"))
                    }.filter { it.position > 0 }.sortedBy { it.position },
                )
            }.sortedBy { it.startMs }
            F1Weekend(
                id = e.optString("id"),
                name = germanGp(e.optString("name")),
                circuit = circuit?.optString("fullName").orEmpty(),
                city = listOfNotNull(circuit?.optJSONObject("address")?.optString("city"), circuit?.optJSONObject("address")?.optString("country"))
                    .filter { it.isNotBlank() }.joinToString(", "),
                startMs = sessions.firstOrNull()?.startMs ?: parseTime(e.optString("date")),
                state = e.optJSONObject("status")?.optJSONObject("type")?.optString("state").orEmpty(),
                sessions = sessions,
            )
        }.sortedBy { it.startMs }
        val calendar = j.optJSONArray("leagues")?.optJSONObject(0)?.optJSONArray("calendar").objects().map {
            F1Calendar(germanGp(it.optString("label")), parseTime(it.optString("startDate")), parseTime(it.optString("endDate")))
        }
        return weekends to calendar
    }

    /** Fahrer- und Konstrukteurs-WM. */
    suspend fun f1Standings(): Pair<List<F1Standing>, List<F1Standing>> {
        val j = get("$V2/racing/f1/standings")
        fun parse(group: JSONObject?): List<F1Standing> = group?.optJSONObject("standings")?.optJSONArray("entries").objects().map { e ->
            val stats = e.optJSONArray("stats").objects().associate { it.optString("name") to it.optString("displayValue") }
            val who = e.optJSONObject("athlete") ?: e.optJSONObject("team")
            F1Standing(
                rank = stats["rank"]?.trim()?.toIntOrNull() ?: 0,
                name = who?.optString("displayName").orEmpty(),
                points = (stats["championshipPts"] ?: stats["points"]).orEmpty().trim(),
                flag = who?.optJSONObject("flag")?.optString("href"),
            )
        }.sortedBy { if (it.rank > 0) it.rank else 999 }
        val groups = j.optJSONArray("children").objects()
        val drivers = groups.firstOrNull { it.optString("name").contains("Driver", true) } ?: groups.getOrNull(0)
        val teams = groups.firstOrNull { it.optString("name").contains("Constructor", true) } ?: groups.getOrNull(1)
        return parse(drivers) to parse(teams)
    }

    private fun germanGp(name: String): String {
        val map = mapOf(
            "Las Vegas" to "Las Vegas", "Qatar" to "Katar", "Abu Dhabi" to "Abu Dhabi", "São Paulo" to "São Paulo", "Mexico City" to "Mexiko-Stadt",
            "United States" to "USA", "Singapore" to "Singapur", "Azerbaijan" to "Aserbaidschan", "Italian" to "Italien", "Dutch" to "Niederlande",
            "Hungarian" to "Ungarn", "Belgian" to "Belgien", "British" to "Großbritannien", "Austrian" to "Österreich", "Spanish" to "Spanien",
            "Barcelona-Catalunya" to "Barcelona", "Canadian" to "Kanada", "Monaco" to "Monaco", "Emilia Romagna" to "Emilia-Romagna",
            "Miami" to "Miami", "Saudi Arabian" to "Saudi-Arabien", "Bahrain" to "Bahrain", "Japanese" to "Japan", "Chinese" to "China",
            "Australian" to "Australien", "Madrid" to "Madrid",
        )
        val core = name.removeSuffix(" Grand Prix").trim()
        val key = map.keys.firstOrNull { core.endsWith(it) }
        return if (key != null) "Großer Preis von ${map[key]}" else name
    }

    // ---------- Tennis ----------

    /** Laufende Turniere der Woche (nur Einzel). [tour] = "atp" oder "wta" */
    suspend fun tennisTournaments(tour: String): List<TennisTournament> {
        val j = get("$SITE/tennis/$tour/scoreboard")
        return j.optJSONArray("events").objects().mapNotNull { e ->
            val matches = e.optJSONArray("groupings").objects()
                .filter { g -> g.optJSONObject("grouping")?.optString("displayName").orEmpty().contains("Singles", true) }
                .flatMap { g ->
                    val draw = if (g.optJSONObject("grouping")?.optString("displayName").orEmpty().startsWith("Men", true)) "Herren-Einzel" else "Damen-Einzel"
                    g.optJSONArray("competitions").objects().mapNotNull { c ->
                        val cs = c.optJSONArray("competitors").objects()
                        if (cs.size < 2) return@mapNotNull null
                        fun player(o: JSONObject): TennisPlayer {
                            val a = o.optJSONObject("athlete")
                            return TennisPlayer(
                                name = a?.optString("displayName")?.takeIf { it.isNotBlank() && it != "null" } ?: "offen",
                                flag = a?.optJSONObject("flag")?.optString("href")?.takeIf { it.isNotBlank() },
                                winner = o.optBoolean("winner"),
                                sets = o.optJSONArray("linescores").objects().map { ls ->
                                    val v = ls.optDouble("value").takeIf { !it.isNaN() }?.toInt()?.toString().orEmpty()
                                    val tb = ls.optInt("tiebreak", -1)
                                    if (tb >= 0) "$v($tb)" else v
                                },
                            )
                        }
                        val st = c.optJSONObject("status")?.optJSONObject("type")
                        TennisMatch(
                            id = c.optString("id"),
                            round = germanRound(c.optJSONObject("round")?.optString("displayName").orEmpty()),
                            startMs = parseTime(c.optString("date")),
                            state = st?.optString("state").orEmpty(),
                            detail = st?.optString("shortDetail").orEmpty(),
                            p1 = player(cs[0]), p2 = player(cs[1]), draw = draw,
                        )
                    }
                }
                .filterNot { it.round.startsWith("Quali") && it.state == "post" }
            if (matches.isEmpty()) return@mapNotNull null
            TennisTournament(
                id = e.optString("id"),
                name = e.optString("name"),
                major = e.optBoolean("major"),
                startMs = parseTime(e.optString("date")),
                matches = matches.sortedWith(compareByDescending<TennisMatch> { it.isLive }.thenBy { if (it.state == "pre") 0 else 1 }.thenBy { if (it.state == "post") -it.startMs else it.startMs }),
            )
        }.sortedWith(compareByDescending<TennisTournament> { t -> t.matches.any { it.isLive } }.thenByDescending { it.major })
    }

    suspend fun tennisRanking(tour: String): List<TennisRank> {
        val j = get("$SITE/tennis/$tour/rankings")
        val r = j.optJSONArray("rankings").objects().firstOrNull() ?: return emptyList()
        return r.optJSONArray("ranks").objects().take(100).map { e ->
            val a = e.optJSONObject("athlete")
            val cur = e.optInt("current")
            val prev = e.optInt("previous", cur)
            TennisRank(
                rank = cur,
                name = a?.optString("displayName").orEmpty(),
                points = e.optDouble("points").takeIf { !it.isNaN() }?.toInt()?.toString().orEmpty(),
                flag = a?.optJSONObject("flag")?.optString("href"),
                trend = when { prev == 0 || prev == cur -> ""; prev > cur -> "▲${prev - cur}"; else -> "▼${cur - prev}" },
            )
        }
    }

    private fun germanRound(r: String): String = when {
        r.equals("Final", true) -> "Finale"
        r.contains("Semifinal", true) -> "Halbfinale"
        r.contains("Quarterfinal", true) -> "Viertelfinale"
        r.contains("Round of 16", true) -> "Achtelfinale"
        r.contains("Qualifying", true) -> "Quali" + r.substringAfter("Qualifying", "").replace("Final", "finale").replace("1st Round", "1. Runde").replace("2nd Round", "2. Runde")
        r.contains("Group", true) -> "Gruppenphase"
        r.matches(Regex("Round (\\d+)")) -> "${r.removePrefix("Round ")}. Runde"
        r.contains("1st Round", true) -> "1. Runde"
        r.contains("2nd Round", true) -> "2. Runde"
        r.contains("3rd Round", true) -> "3. Runde"
        r.contains("4th Round", true) -> "4. Runde"
        else -> r
    }

    // ---------- Wrestling ----------

    /** Nächste und letzte Shows von WWE und AEW. */
    suspend fun wrestlingShows(): List<WrestlingShow> = coroutineScope {
        listOf(WWE_LEAGUE to "WWE", AEW_LEAGUE to "AEW").flatMap { (id, promo) ->
            listOf("eventsnextleague", "eventspastleague").map { ep ->
                async { runCatching { showsOf(get("$SPORTSDB/$ep.php?id=$id"), promo) }.getOrDefault(emptyList()) }
            }
        }.awaitAll().flatten().distinctBy { it.promotion + it.name + it.startMs }.sortedBy { it.startMs }
    }

    private fun showsOf(j: JSONObject, promo: String): List<WrestlingShow> =
        j.optJSONArray("events").objects().map { e ->
            val ts = e.optString("strTimestamp").takeIf { it.isNotBlank() && it != "null" }
            WrestlingShow(
                promotion = promo,
                name = e.optString("strEvent"),
                startMs = ts?.let { parseTime(if (it.endsWith("Z") || it.contains('+')) it else "${it}Z") } ?: 0L,
                venue = e.optString("strVenue").takeIf { it != "null" }.orEmpty(),
                image = (e.optString("strThumb").takeIf { it.isNotBlank() && it != "null" } ?: e.optString("strPoster").takeIf { it.isNotBlank() && it != "null" }),
                card = e.optString("strResult").takeIf { it != "null" }.orEmpty().replace("\r", "").trim(),
            )
        }

    // ---------- Hilfen ----------

    private fun parseTime(s: String?): Long {
        if (s.isNullOrBlank()) return 0L
        val fixed = if (Regex("T\\d\\d:\\d\\dZ$").containsMatchIn(s)) s.replace(Regex("Z$"), ":00Z") else s
        return runCatching { Instant.parse(fixed).toEpochMilli() }.getOrDefault(0L)
    }

    private suspend fun get(url: String): JSONObject = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0 fIPTV").build()
        OkHttpProvider.client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IllegalStateException("Sportdaten nicht erreichbar (HTTP ${r.code})")
            JSONObject(r.body?.string().orEmpty())
        }
    }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
}
