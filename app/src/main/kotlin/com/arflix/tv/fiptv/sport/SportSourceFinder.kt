package com.arflix.tv.fiptv.sport

import com.arflix.tv.data.model.IptvChannel
import com.arflix.tv.data.model.IptvProgram
import com.arflix.tv.data.model.SportsAddonEvent
import com.arflix.tv.data.model.sportsEventCatalogs
import com.arflix.tv.data.repository.IptvRepository
import com.arflix.tv.data.repository.SportsAddonRepository
import com.arflix.tv.data.repository.SportsAddonStream
import com.arflix.tv.data.repository.StreamRepository
import java.text.Normalizer
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Ein IPTV-Sender, der ein Spiel (wahrscheinlich) zeigt. */
data class TvSource(
    val channel: IptvChannel,
    val programme: IptvProgram?,
    /** 3 = beide Teams im Sendernamen/Titel, 2 = beide Teams in Titel+Beschreibung, 1 = ein Team */
    val score: Int,
    val uhd: Boolean,
) {
    val confidence: String get() = when (score) {
        3 -> "Spiel bestätigt"
        2 -> "Sehr wahrscheinlich"
        else -> "Möglich"
    }
}

data class GameSources(val tv: List<TvSource>, val addons: List<SportsAddonEvent>) {
    val count: Int get() = tv.size + addons.size
}

/**
 * fIPTV: findet zu Spielen passende Quellen – IPTV-Sender (Sendername + Programmführer) und
 * Sport-Kataloge installierter Stremio-Addons. Vorgehen angelehnt an Sportio Live:
 * beide Teamnamen gefunden = sicher, nur ein Team = möglich, fremde Teams im Titel = ausgeschlossen.
 */
@Singleton
class SportSourceFinder @Inject constructor(
    private val iptv: IptvRepository,
    private val streams: StreamRepository,
    private val sportsAddons: SportsAddonRepository,
) {
    private val mutex = Mutex()
    private var cacheKey: String? = null
    private var cacheAt = 0L
    private var cache: Map<String, GameSources> = emptyMap()

    suspend fun find(games: List<SportMatch>, allTeams: List<SportTeam>, force: Boolean = false): Map<String, GameSources> = mutex.withLock {
        val key = games.joinToString(",") { it.id }
        val now = System.currentTimeMillis()
        if (!force && key == cacheKey && now - cacheAt < 10 * 60_000) return@withLock cache
        val tv = runCatching { findTv(games, allTeams, now) }.getOrDefault(emptyMap())
        val addons = runCatching { findAddons(games, now) }.getOrDefault(emptyMap())
        val result = games.associate { g -> g.id to GameSources(tv[g.id].orEmpty(), addons[g.id].orEmpty()) }
        cache = result; cacheKey = key; cacheAt = now
        result
    }

    suspend fun resolveAddon(event: SportsAddonEvent): List<SportsAddonStream> =
        runCatching { sportsAddons.resolve(event) }.getOrDefault(emptyList())

    // ---------- IPTV ----------

    private class GameKeys(val game: SportMatch, val home: Set<String>, val away: Set<String>)

    private suspend fun findTv(games: List<SportMatch>, allTeams: List<SportTeam>, now: Long): Map<String, List<TvSource>> =
        withContext(Dispatchers.IO) {
            if (games.isEmpty()) return@withContext emptyMap()
            val keys = games.map { GameKeys(it, keywords(it.home.team), keywords(it.away.team)) }
            val allKeywords = (allTeams.flatMap { keywords(it) } + keys.flatMap { it.home + it.away }).toSet()
            val found = HashMap<String, MutableMap<String, Pair<IptvProgram?, Int>>>() // gameId -> channelId -> (programme, score)
            fun add(gameId: String, channelId: String, programme: IptvProgram?, score: Int) {
                val m = found.getOrPut(gameId) { HashMap() }
                val old = m[channelId]
                if (old == null || score > old.second) m[channelId] = programme to score
            }

            // 1) Sendernamen durchgehen: Kandidaten für den Programmführer + Event-Sender („Bayern vs Köln“)
            val candidates = LinkedHashSet<String>()
            val todayOrLive = keys.filter { it.game.isLive || it.game.startMs - now < 18 * 3600_000L }
            iptv.visitStoredChannelLabels(null) { id, name, group ->
                val n = norm(name)
                val g = norm(group)
                if (sportsChannel.containsMatchIn(" $g $n ") || allKeywords.any { has(n, it) }) candidates.add(id)
                for (k in todayOrLive) {
                    if (k.home.any { has(n, it) } && k.away.any { has(n, it) }) add(k.game.id, id, null, 3)
                }
            }

            // 2) Programmführer im Zeitraum der Spiele
            val from = now - 4 * 3600_000L
            val to = minOf((games.maxOfOrNull { it.startMs } ?: now) + 3 * 3600_000L, now + 3 * 24 * 3600_000L)
            if (to > from && candidates.isNotEmpty()) {
                for (chunk in candidates.chunked(2000)) {
                    iptv.visitCachedGuideWindow(chunk.toSet(), from, to) { id, p ->
                        val title = norm(p.title)
                        val text = "$title ${norm(p.description.orEmpty())}"
                        for (k in keys) {
                            val start = k.game.startMs
                            if (p.startUtcMillis >= start + 150 * 60_000L || p.endUtcMillis <= start - 30 * 60_000L) continue
                            val hT = k.home.any { has(title, it) }
                            val aT = k.away.any { has(title, it) }
                            val hX = hT || k.home.any { has(text, it) }
                            val aX = aT || k.away.any { has(text, it) }
                            val score = when {
                                hT && aT -> 3
                                hX && aX -> 2
                                (hT || aT) && football.containsMatchIn(" $text ") -> {
                                    // Ein anderes Team im Titel → anderes Spiel
                                    val others = allKeywords - k.home - k.away
                                    if (others.any { has(title, it) }) 0 else 1
                                }
                                else -> 0
                            }
                            if (score > 0) add(k.game.id, id, p, score)
                        }
                    }
                }
            }

            // 3) Sender laden, doppelte Adressen entfernen, sortieren
            val ids = found.values.flatMap { it.keys }.toSet()
            val channels = ids.chunked(400).flatMap { iptv.pagedChannelsByIds(it) }.associateBy { it.id }
            found.mapValues { (_, m) ->
                m.mapNotNull { (cid, v) ->
                    val ch = channels[cid] ?: return@mapNotNull null
                    TvSource(ch, v.first, v.second, uhd.containsMatchIn(" ${norm(ch.name)} "))
                }
                    .sortedWith(compareByDescending<TvSource> { it.score }.thenByDescending { it.uhd }
                        .thenByDescending { it.programme?.isLive(now) == true }.thenBy { it.channel.name })
                    .distinctBy { it.channel.streamUrl }
                    .take(15)
            }
        }

    // ---------- Sendungen im TV-Programm (Formel 1, Tennis, Wrestling …) ----------

    private val listingCache = HashMap<String, Pair<Long, List<TvSource>>>()

    /**
     * Sendungen der nächsten Tage, deren Titel zu [pattern] passt (auf normalisiertem Text,
     * z. B. " formel 1 "), auf Sport- und großen Sendern. Live zuerst, dann nach Beginn.
     */
    suspend fun tvListings(key: String, pattern: Regex, hoursAhead: Int = 72): List<TvSource> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        listingCache[key]?.let { (at, list) -> if (now - at < 10 * 60_000) return@withContext list }
        val candidates = LinkedHashSet<String>()
        iptv.visitStoredChannelLabels(null) { id, name, group ->
            val text = " ${norm(group).trim()} ${norm(name).trim()} "
            if (sportsChannel.containsMatchIn(text) || listingChannels.containsMatchIn(text) || pattern.containsMatchIn(text)) candidates.add(id)
        }
        val hits = HashMap<String, IptvProgram>()
        if (candidates.isNotEmpty()) {
            for (chunk in candidates.chunked(2000)) {
                iptv.visitCachedGuideWindow(chunk.toSet(), now - 3 * 3600_000L, now + hoursAhead * 3600_000L) { id, p ->
                    if (p.endUtcMillis <= now) return@visitCachedGuideWindow
                    if (pattern.containsMatchIn(norm(p.title))) {
                        val old = hits[id]
                        if (old == null || p.startUtcMillis < old.startUtcMillis) hits[id] = p
                    }
                }
            }
        }
        // je Sender nur die nächste passende Sendung; dazu Sender, deren Name selbst passt (z. B. „Sky Sport F1“)
        val channels = hits.keys.chunked(400).flatMap { iptv.pagedChannelsByIds(it) }.associateBy { it.id }
        val list = hits.mapNotNull { (cid, p) ->
            val ch = channels[cid] ?: return@mapNotNull null
            TvSource(ch, p, if (p.isLive(now)) 3 else 1, uhd.containsMatchIn(" ${norm(ch.name)} "))
        }
            .sortedWith(compareByDescending<TvSource> { it.programme?.isLive(now) == true }.thenBy { it.programme?.startUtcMillis ?: 0L }.thenByDescending { it.uhd })
            .distinctBy { "${it.programme?.title}|${it.programme?.startUtcMillis}|${norm(it.channel.name).replace(Regex(" (hd|fhd|uhd|4k|sd|de|ger|raw) "), " ")}" }
            .take(40)
        listingCache[key] = now to list
        list
    }

    // ---------- Stremio ----------

    private suspend fun findAddons(games: List<SportMatch>, now: Long): Map<String, List<SportsAddonEvent>> {
        val addons = withTimeoutOrNull(3_000) { streams.installedAddons.first() }.orEmpty()
            .filter { sportsEventCatalogs(it).isNotEmpty() }
        if (addons.isEmpty()) return emptyMap()
        val events = withTimeoutOrNull(25_000) { sportsAddons.load(addons) { } }.orEmpty()
        return games.associate { g ->
            val home = keywords(g.home.team)
            val away = keywords(g.away.team)
            g.id to events.filter { e ->
                val t = norm(e.title)
                val both = home.any { has(t, it) } && away.any { has(t, it) }
                val timeOk = e.startsAt == null || kotlin.math.abs(e.startsAt - g.startMs) < 6 * 3600_000L
                both && timeOk
            }.sortedByDescending { it.isLive(now) }
        }
    }

    // ---------- Text ----------

    companion object {
        private val stop = setOf(
            "fc", "sc", "sv", "vfl", "vfb", "tsg", "fsv", "borussia", "bayer", "eintracht", "club", "cf", "afc", "ac",
            "as", "ssc", "rb", "rc", "sk", "fk", "cd", "ud", "real", "sporting", "united", "city", "the", "de", "la",
            "1846", "1899", "1860", "04", "05", "09", "1", "06", "olympique", "athletic",
        )
        private val synonyms = mapOf(
            "munich" to listOf("munchen", "muenchen", "bayern"), "bayern" to listOf("fcb"),
            "cologne" to listOf("koln", "koeln"), "koln" to listOf("koeln", "cologne"),
            "monchengladbach" to listOf("gladbach", "moenchengladbach"), "gladbach" to listOf("monchengladbach", "moenchengladbach"),
            "dortmund" to listOf("bvb"), "hamburger" to listOf("hamburg", "hsv"), "nuremberg" to listOf("nurnberg", "nuernberg"),
            "leverkusen" to listOf("b04"), "frankfurt" to listOf("sge"), "milan" to listOf("mailand"), "turin" to listOf("juventus"),
            "lisbon" to listOf("lissabon"), "naples" to listOf("neapel", "napoli"), "napoli" to listOf("neapel"),
        )
        private val sportsChannel = Regex(
            " (sport|sports|sky|dazn|bundesliga|champions|uefa|liga|ligue|football|fussball|soccer|event|ppv|bein|eurosport|" +
                "magenta|prime|amazon|ard|zdf|das erste|sat 1|rtl|prosieben|servus|sport1|canal|rmc|movistar|tnt|espn|arena|nova) "
        )
        private val listingChannels = Regex(" (netflix|maxx|tele 5|dmax|nitro|kabel|sixx|one|tlc|zdfneo|3sat|orf|srf|warner|tnt|max) ")

        /** Suchmuster für Sendungen im TV-Programm (auf normalisiertem Text) */
        val F1_PATTERN = Regex(" (formel 1|formula 1|formula one|f1|grand prix|qualifying) ")
        val TENNIS_PATTERN = Regex(" (tennis|atp|wta|davis cup|billie jean king|wimbledon|roland garros|french open|us open|australian open|laver cup|united cup) ")
        val WRESTLING_PATTERN = Regex(" (wwe|aew|wrestling|smackdown|raw|nxt|dynamite|collision|rampage|royal rumble|wrestlemania|summerslam|survivor series) ")

        private val football = Regex(" (bundesliga|champions|league|uefa|fussball|football|soccer|liga|pokal|live|spieltag|cup) ")
        private val uhd = Regex(" (4k|uhd|2160p?) ")

        fun norm(s: String): String {
            val t = s.lowercase().replace("ä", "a").replace("ö", "o").replace("ü", "u").replace("ß", "ss")
            val stripped = Normalizer.normalize(t, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
            return " " + stripped.replace(Regex("[^a-z0-9]+"), " ").trim() + " "
        }

        private fun has(text: String, word: String) = text.contains(" $word ")

        /** Erkennungswörter eines Teams, z. B. FC Bayern → bayern, munchen, muenchen, fcb. */
        fun keywords(team: SportTeam): Set<String> {
            val names = listOf(team.name, team.fullName).filter { it.isNotBlank() }
            val words = names.flatMap { norm(it).trim().split(" ") }.filter { it.length >= 4 && it !in stop }
            val base = words.ifEmpty { norm(team.name).trim().split(" ").filter { it.length >= 3 } }
            return (base + base.flatMap { synonyms[it].orEmpty() }).toSet()
        }
    }
}
