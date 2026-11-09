package com.arflix.tv.fiptv

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * fIPTV: „Weiterschauen“ für Inhalte, die nicht über TMDB/Addons laufen
 * (Filme & Serien des IPTV-Anbieters, Mediathek).
 *
 * Ablauf: Der Bereich meldet vor dem Abspielen den Titel an ([register]), der Player meldet
 * laufend die Position ([report], aus PlayerViewModel.saveProgress). Nur angemeldete
 * Stream-Adressen werden gespeichert. Daten liegen je Profil auf dem Gerät.
 */
object FiptvResume {
    enum class Kind { MOVIE, EPISODE, MEDIATHEK }

    data class Entry(
        val url: String,
        val kind: Kind,
        val title: String,
        /** z. B. Serienname bei Folgen, Sender bei der Mediathek */
        val subtitle: String = "",
        val image: String? = null,
        val tmdbId: Int? = null,
        val season: Int? = null,
        val episode: Int? = null,
        val positionMs: Long = 0,
        val durationMs: Long = 0,
        val finished: Boolean = false,
        val updatedAt: Long = 0,
    ) {
        val progress: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
        val remainingMin: Int get() = (((durationMs - positionMs).coerceAtLeast(0) + 30_000) / 60_000).toInt()
        /** Lohnt es sich, hier weiterzumachen? */
        val resumable: Boolean get() = !finished && positionMs >= MIN_POSITION_MS && durationMs > 0
    }

    private const val PREFS = "fiptv_resume"
    private const val MAX_ENTRIES = 300
    private const val MIN_POSITION_MS = 60_000L
    private const val PERSIST_EVERY_MS = 15_000L

    private val cache = mutableMapOf<String, MutableList<Entry>>()
    private var lastPersist = 0L

    private val _changes = MutableStateFlow(0L)
    /** Ändert sich bei jeder Aktualisierung – zum Neuzeichnen der Listen. */
    val changes: StateFlow<Long> = _changes.asStateFlow()

    /** Titel vor dem Abspielen anmelden; vorhandene Position bleibt erhalten. */
    @Synchronized
    fun register(context: Context, profileId: String, entry: Entry) {
        val list = load(context, profileId)
        val i = list.indexOfFirst { it.url == entry.url }
        val old = list.getOrNull(i)
        val merged = entry.copy(
            positionMs = old?.positionMs ?: 0,
            durationMs = old?.durationMs ?: 0,
            finished = old?.finished ?: false,
            updatedAt = old?.updatedAt ?: System.currentTimeMillis(),
        )
        if (i >= 0) list[i] = merged else list.add(merged)
        persist(context, profileId, force = true)
    }

    /** Position vom Player. Unbekannte Adressen (TMDB/Addons, Live-TV) werden ignoriert. */
    @Synchronized
    fun report(context: Context, profileId: String, url: String?, positionMs: Long, durationMs: Long, closing: Boolean) {
        if (url.isNullOrBlank() || durationMs <= 0) return
        val list = load(context, profileId)
        val i = list.indexOfFirst { it.url == url }
        if (i < 0) return
        // Fertig, wenn höchstens 5 % fehlen oder bei langen Titeln nur noch der Abspann (2 Min.) läuft
        val finished = positionMs >= durationMs * 0.95 || (durationMs > 20 * 60_000L && durationMs - positionMs <= 2 * 60_000L)
        list[i] = list[i].copy(
            positionMs = positionMs,
            durationMs = durationMs,
            finished = finished,
            updatedAt = System.currentTimeMillis(),
        )
        persist(context, profileId, force = closing || finished)
    }

    /** Startposition für einen Titel, falls dort weitergeschaut werden kann. */
    fun startPosition(context: Context, profileId: String, url: String): Long? =
        entries(context, profileId).firstOrNull { it.url == url }?.takeIf { it.resumable }?.positionMs

    fun entry(context: Context, profileId: String, url: String): Entry? =
        entries(context, profileId).firstOrNull { it.url == url }

    @Synchronized
    fun entries(context: Context, profileId: String): List<Entry> = load(context, profileId).toList()

    /** Angefangene Titel, neueste zuerst. */
    fun continueWatching(context: Context, profileId: String, kinds: Set<Kind>): List<Entry> =
        entries(context, profileId)
            .filter { it.kind in kinds && it.resumable }
            .sortedByDescending { it.updatedAt }
            .take(30)

    @Synchronized
    fun remove(context: Context, profileId: String, url: String) {
        val list = load(context, profileId)
        if (list.removeAll { it.url == url }) persist(context, profileId, force = true)
    }

    // ---------- Speicher ----------

    private fun key(profileId: String) = "p_$profileId"

    private fun load(context: Context, profileId: String): MutableList<Entry> =
        cache.getOrPut(profileId) {
            val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key(profileId), null)
            runCatching {
                val arr = JSONArray(raw ?: "[]")
                (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(::fromJson) }.toMutableList()
            }.getOrDefault(mutableListOf())
        }

    private fun persist(context: Context, profileId: String, force: Boolean) {
        val now = System.currentTimeMillis()
        _changes.value = now
        if (!force && now - lastPersist < PERSIST_EVERY_MS) return
        lastPersist = now
        val list = load(context, profileId)
        if (list.size > MAX_ENTRIES) {
            val keep = list.sortedByDescending { it.updatedAt }.take(MAX_ENTRIES).toSet()
            list.retainAll(keep)
        }
        val arr = JSONArray()
        list.forEach { arr.put(toJson(it)) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(key(profileId), arr.toString()).apply()
    }

    private fun toJson(e: Entry) = JSONObject()
        .put("url", e.url).put("kind", e.kind.name).put("title", e.title).put("sub", e.subtitle)
        .put("img", e.image ?: "").put("tmdb", e.tmdbId ?: -1).put("s", e.season ?: -1).put("e", e.episode ?: -1)
        .put("pos", e.positionMs).put("dur", e.durationMs).put("fin", e.finished).put("at", e.updatedAt)

    private fun fromJson(o: JSONObject): Entry? {
        val url = o.optString("url").takeIf { it.isNotBlank() } ?: return null
        return Entry(
            url = url,
            kind = runCatching { Kind.valueOf(o.optString("kind")) }.getOrDefault(Kind.MOVIE),
            title = o.optString("title"),
            subtitle = o.optString("sub"),
            image = o.optString("img").takeIf { it.isNotBlank() },
            tmdbId = o.optInt("tmdb", -1).takeIf { it > 0 },
            season = o.optInt("s", -1).takeIf { it >= 0 },
            episode = o.optInt("e", -1).takeIf { it >= 0 },
            positionMs = o.optLong("pos"),
            durationMs = o.optLong("dur"),
            finished = o.optBoolean("fin"),
            updatedAt = o.optLong("at"),
        )
    }
}
