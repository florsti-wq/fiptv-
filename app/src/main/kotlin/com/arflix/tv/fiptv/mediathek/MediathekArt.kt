package com.arflix.tv.fiptv.mediathek

import android.content.Context
import com.arflix.tv.data.api.TmdbApi
import com.arflix.tv.data.api.TmdbMediaItem
import com.arflix.tv.util.Constants
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** Bilder einer Sendung (TMDB) */
data class MediathekArt(val backdrop: String?, val poster: String?) {
    val tile: String? get() = backdrop?.let { "https://image.tmdb.org/t/p/w780$it" } ?: poster?.let { "https://image.tmdb.org/t/p/w500$it" }
    val hero: String? get() = backdrop?.let { "https://image.tmdb.org/t/p/w1280$it" } ?: poster?.let { "https://image.tmdb.org/t/p/w780$it" }
}

/**
 * fIPTV: Die Mediathek-Schnittstelle liefert keine Bilder. Für den Netflix-Look suchen wir
 * Sendungen (Thema) bzw. Filme (Titel) bei TMDB – nur bei eindeutig gleichem Namen,
 * sonst zeigt die Kachel eine Senderfarbe mit Titel. Ergebnisse werden dauerhaft gemerkt.
 */
class MediathekArtCache(context: Context, private val tmdbApi: TmdbApi) {
    private val prefs = context.getSharedPreferences("fiptv_mediathek_art", Context.MODE_PRIVATE)
    private val memory = ConcurrentHashMap<String, MediathekArt>()
    private val limit = Semaphore(4)

    private fun norm(text: String) = text.lowercase()
        .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
        .replace(Regex("[^a-z0-9]"), "")

    /** „Der Fall X (1/3) - Hörfassung | ARD“ → „Der Fall X“ */
    private fun clean(text: String) = text
        .replace(Regex("\\(.*?\\)|\\[.*?]"), " ")
        .split(" - ", " | ", " – ", ": ").first()
        .replace(Regex("(?i)\\b(hörfassung|audiodeskription|originalversion|ov|mit untertitel|in gebärdensprache)\\b"), " ")
        .trim()

    private fun query(item: MediathekItem): Pair<String, Boolean>? {
        val movie = MediathekRepository.isContainerTopic(item.topic)
        val q = clean(if (movie) item.title else item.topic)
        return if (norm(q).length < 3) null else q to movie
    }

    fun cached(item: MediathekItem): MediathekArt? {
        val (q, movie) = query(item) ?: return EMPTY
        val key = (if (movie) "m:" else "s:") + norm(q)
        memory[key]?.let { return it }
        val stored = prefs.getString(key, null) ?: return null
        val parts = stored.split("|")
        if (parts[0] == "miss") {
            val at = parts.getOrNull(1)?.toLongOrNull() ?: 0
            if (System.currentTimeMillis() - at > MISS_RETRY_MS) return null
            return EMPTY.also { memory[key] = it }
        }
        return MediathekArt(parts.getOrNull(0)?.ifBlank { null }, parts.getOrNull(1)?.ifBlank { null }).also { memory[key] = it }
    }

    suspend fun lookup(item: MediathekItem): MediathekArt {
        cached(item)?.let { return it }
        val (q, movie) = query(item) ?: return EMPTY
        val key = (if (movie) "m:" else "s:") + norm(q)
        if (Constants.TMDB_API_KEY.isBlank()) return EMPTY
        return limit.withPermit {
            memory[key]?.let { return@withPermit it }
            val art = withContext(Dispatchers.IO) {
                runCatching {
                    val wanted = norm(q)
                    fun match(list: List<TmdbMediaItem>) = list.firstOrNull { r ->
                        listOfNotNull(r.name, r.title, r.originalName, r.originalTitle).any { norm(clean(it)) == wanted } &&
                            (r.backdropPath != null || r.posterPath != null)
                    }
                    val hit = if (movie) {
                        match(tmdbApi.searchMovies(Constants.TMDB_API_KEY, q, language = "de-DE").results)
                            ?: match(tmdbApi.searchTv(Constants.TMDB_API_KEY, q, language = "de-DE").results)
                    } else {
                        match(tmdbApi.searchTv(Constants.TMDB_API_KEY, q, language = "de-DE").results)
                            ?: match(tmdbApi.searchMovies(Constants.TMDB_API_KEY, q, language = "de-DE").results)
                    }
                    MediathekArt(hit?.backdropPath, hit?.posterPath)
                }.getOrNull()
            } ?: return@withPermit EMPTY // Netzfehler: nicht merken, später erneut
            prefs.edit().putString(
                key,
                if (art.backdrop == null && art.poster == null) "miss|${System.currentTimeMillis()}"
                else "${art.backdrop.orEmpty()}|${art.poster.orEmpty()}",
            ).apply()
            memory[key] = art
            art
        }
    }

    companion object {
        val EMPTY = MediathekArt(null, null)
        private const val MISS_RETRY_MS = 7L * 24 * 60 * 60 * 1000
    }
}
