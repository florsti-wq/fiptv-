package com.arflix.tv.fiptv.vod

import com.arflix.tv.network.OkHttpProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/** fIPTV: ein Xtream-Zugang aus den IPTV-Einstellungen von ARVIO */
data class XtreamAccount(
    val sourceId: String,
    val name: String,
    val baseUrl: String,
    val username: String,
    val password: String,
)

data class VodCategory(val id: String, val name: String)

data class VodMovie(
    val id: Int,
    val name: String,
    val poster: String?,
    val rating: String?,
    val extension: String,
    val tmdbId: Int?,
    val categoryId: String?,
    val added: Long,
)

data class VodSeries(
    val id: Int,
    val name: String,
    val poster: String?,
    val rating: String?,
    val plot: String?,
    val tmdbId: Int?,
    val categoryId: String?,
)

data class VodEpisode(
    val id: String,
    val season: Int,
    val episode: Int,
    val title: String,
    val extension: String,
    val image: String?,
    val plot: String?,
    val durationSec: Int,
)

/**
 * fIPTV: Filme und Serien des IPTV-Anbieters direkt über die Xtream-Schnittstelle (player_api.php).
 * Unabhängig von ARVIOs Zuordnung zu TMDB – so ist das komplette Angebot des Anbieters erreichbar.
 */
class XtreamVodClient(private val account: XtreamAccount) {
    private val base = account.baseUrl.trimEnd('/')

    private suspend fun get(action: String, vararg params: Pair<String, String>): Any? = withContext(Dispatchers.IO) {
        val url = "$base/player_api.php".toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("username", account.username)
            ?.addQueryParameter("password", account.password)
            ?.addQueryParameter("action", action)
            ?.apply { params.forEach { (k, v) -> addQueryParameter(k, v) } }
            ?.build() ?: error("Ungültige Anbieter-Adresse")
        val request = Request.Builder().url(url).header("User-Agent", "fIPTV").build()
        OkHttpProvider.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Anbieter antwortet mit HTTP ${response.code}")
            val body = response.body?.string().orEmpty().trim()
            when {
                body.startsWith("[") -> JSONArray(body)
                body.startsWith("{") -> JSONObject(body)
                else -> null
            }
        }
    }

    private fun JSONObject.str(key: String): String? =
        if (isNull(key)) null else optString(key).trim().takeIf { it.isNotEmpty() && it != "null" }

    private fun JSONObject.int(key: String): Int? = str(key)?.toDoubleOrNull()?.toInt()

    private fun JSONObject.img(key: String): String? =
        str(key)?.takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }

    /** fIPTV: Gesamtliste direkt in eine Datei schreiben (ohne sie als Text im Speicher zu halten). */
    suspend fun downloadAll(series: Boolean, target: java.io.File): Boolean = withContext(Dispatchers.IO) {
        val url = "$base/player_api.php".toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("username", account.username)
            ?.addQueryParameter("password", account.password)
            ?.addQueryParameter("action", if (series) "get_series" else "get_vod_streams")
            ?.build() ?: return@withContext false
        val request = Request.Builder().url(url).header("User-Agent", "fIPTV").build()
        OkHttpProvider.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext false
            val tmp = java.io.File(target.parentFile, target.name + ".part")
            response.body?.byteStream()?.use { input -> tmp.outputStream().use { input.copyTo(it, 64 * 1024) } } ?: return@withContext false
            tmp.renameTo(target)
        }
    }

    /**
     * fIPTV: Datei Eintrag für Eintrag lesen und nur passende Titel behalten – so bleibt der Speicher klein,
     * auch bei Anbietern mit sehr vielen Filmen/Serien (vorher: OutOfMemory in der Suche auf Fire TV).
     */
    fun scan(file: java.io.File, series: Boolean, accept: (String) -> Boolean, limit: Int): Pair<List<VodMovie>, List<VodSeries>> {
        val movies = ArrayList<VodMovie>(); val shows = ArrayList<VodSeries>()
        if (!file.exists()) return movies to shows
        android.util.JsonReader(java.io.InputStreamReader(java.io.BufferedInputStream(file.inputStream(), 64 * 1024), Charsets.UTF_8)).use { r ->
            r.isLenient = true
            if (r.peek() != android.util.JsonToken.BEGIN_ARRAY) return movies to shows
            r.beginArray()
            while (r.hasNext()) {
                if (r.peek() != android.util.JsonToken.BEGIN_OBJECT) { r.skipValue(); continue }
                val f = HashMap<String, String?>(12)
                r.beginObject()
                while (r.hasNext()) {
                    val k = r.nextName()
                    when (k) {
                        "stream_id", "series_id", "name", "stream_icon", "cover", "rating", "container_extension", "tmdb", "tmdb_id", "category_id", "added" ->
                            f[k] = when (r.peek()) {
                                android.util.JsonToken.NULL -> { r.nextNull(); null }
                                android.util.JsonToken.STRING, android.util.JsonToken.NUMBER -> r.nextString()
                                android.util.JsonToken.BOOLEAN -> r.nextBoolean().toString()
                                else -> { r.skipValue(); null }
                            }
                        else -> r.skipValue()
                    }
                }
                r.endObject()
                val name = f["name"]?.trim().orEmpty()
                if (name.isEmpty() || !accept(name)) continue
                fun img(v: String?) = v?.trim()?.takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) }
                fun num(v: String?) = v?.trim()?.toDoubleOrNull()?.toInt()
                val rating = f["rating"]?.trim()?.takeIf { it.isNotEmpty() && it != "0" && it != "0.0" && it != "null" }
                if (series) {
                    val id = num(f["series_id"]) ?: continue
                    shows += VodSeries(id, name, img(f["cover"]) ?: img(f["stream_icon"]), rating, null, num(f["tmdb"]) ?: num(f["tmdb_id"]), f["category_id"])
                } else {
                    val id = num(f["stream_id"]) ?: continue
                    movies += VodMovie(id, name, img(f["stream_icon"]) ?: img(f["cover"]), rating, f["container_extension"]?.trim()?.ifEmpty { null } ?: "mp4",
                        num(f["tmdb"]) ?: num(f["tmdb_id"]), f["category_id"], f["added"]?.trim()?.toLongOrNull() ?: 0L)
                }
                if (movies.size + shows.size >= limit) break
            }
        }
        return movies to shows
    }

    /** fIPTV: die zuletzt hinzugefügten Filme (nur mit TMDB-Nummer), ohne die ganze Liste im Speicher. */
    fun newestMovies(file: java.io.File, limit: Int): List<VodMovie> {
        if (!file.exists()) return emptyList()
        val heap = java.util.PriorityQueue<VodMovie>(limit + 1, compareBy { it.added })
        val seenTmdb = HashSet<Int>()
        android.util.JsonReader(java.io.InputStreamReader(java.io.BufferedInputStream(file.inputStream(), 64 * 1024), Charsets.UTF_8)).use { r ->
            r.isLenient = true
            if (r.peek() != android.util.JsonToken.BEGIN_ARRAY) return emptyList()
            r.beginArray()
            while (r.hasNext()) {
                if (r.peek() != android.util.JsonToken.BEGIN_OBJECT) { r.skipValue(); continue }
                var id: Int? = null; var name = ""; var icon: String? = null; var tmdb: Int? = null; var added = 0L; var ext: String? = null; var rating: String? = null
                r.beginObject()
                while (r.hasNext()) {
                    val k = r.nextName()
                    if (k !in setOf("stream_id", "name", "stream_icon", "tmdb", "tmdb_id", "added", "container_extension", "rating")) { r.skipValue(); continue }
                    val v = when (r.peek()) {
                        android.util.JsonToken.NULL -> { r.nextNull(); null }
                        android.util.JsonToken.STRING, android.util.JsonToken.NUMBER -> r.nextString()
                        else -> { r.skipValue(); null }
                    }
                    when (k) {
                        "stream_id" -> id = v?.toDoubleOrNull()?.toInt()
                        "name" -> name = v?.trim().orEmpty()
                        "stream_icon" -> icon = v?.trim()?.takeIf { it.startsWith("http", true) }
                        "tmdb", "tmdb_id" -> if (tmdb == null) tmdb = v?.toDoubleOrNull()?.toInt()?.takeIf { it > 0 }
                        "added" -> added = v?.toLongOrNull() ?: 0L
                        "container_extension" -> ext = v
                        "rating" -> rating = v
                    }
                }
                r.endObject()
                val t = tmdb ?: continue
                val sid = id ?: continue
                if (added <= 0L || name.isEmpty() || !seenTmdb.add(t)) continue
                if (heap.size >= limit && added <= (heap.peek()?.added ?: 0L)) continue
                heap.add(VodMovie(sid, name, icon, rating, ext ?: "mp4", t, null, added))
                if (heap.size > limit) heap.poll()
            }
        }
        return heap.sortedByDescending { it.added }
    }

    suspend fun categories(series: Boolean): List<VodCategory> {
        val arr = get(if (series) "get_series_categories" else "get_vod_categories") as? JSONArray ?: return emptyList()
        return arr.objects().mapNotNull { o ->
            val id = o.str("category_id") ?: return@mapNotNull null
            VodCategory(id, o.str("category_name") ?: id)
        }
    }

    suspend fun movies(categoryId: String?): List<VodMovie> {
        val arr = (if (categoryId != null) get("get_vod_streams", "category_id" to categoryId) else get("get_vod_streams"))
            as? JSONArray ?: return emptyList()
        return arr.objects().mapNotNull { o ->
            val id = o.int("stream_id") ?: return@mapNotNull null
            VodMovie(
                id = id,
                name = o.str("name") ?: "Film $id",
                poster = o.img("stream_icon") ?: o.img("cover"),
                rating = o.str("rating")?.takeIf { it != "0" && it != "0.0" },
                extension = o.str("container_extension") ?: "mp4",
                tmdbId = o.int("tmdb") ?: o.int("tmdb_id"),
                categoryId = o.str("category_id"),
                added = o.str("added")?.toLongOrNull() ?: 0L,
            )
        }
    }

    suspend fun series(categoryId: String?): List<VodSeries> {
        val arr = (if (categoryId != null) get("get_series", "category_id" to categoryId) else get("get_series"))
            as? JSONArray ?: return emptyList()
        return arr.objects().mapNotNull { o ->
            val id = o.int("series_id") ?: return@mapNotNull null
            VodSeries(
                id = id,
                name = o.str("name") ?: "Serie $id",
                poster = o.img("cover") ?: o.img("stream_icon"),
                rating = o.str("rating")?.takeIf { it != "0" && it != "0.0" },
                plot = o.str("plot"),
                tmdbId = o.int("tmdb") ?: o.int("tmdb_id"),
                categoryId = o.str("category_id"),
            )
        }
    }

    suspend fun episodes(seriesId: Int): List<VodEpisode> {
        val root = get("get_series_info", "series_id" to seriesId.toString()) as? JSONObject ?: return emptyList()
        val raw = root.opt("episodes")
        val groups: List<Pair<Int?, JSONArray>> = when (raw) {
            is JSONObject -> raw.keys().asSequence().mapNotNull { k -> raw.optJSONArray(k)?.let { k.toIntOrNull() to it } }.toList()
            is JSONArray -> listOf(null to raw)
            else -> emptyList()
        }
        return groups.flatMap { (seasonKey, arr) ->
            arr.objects().mapNotNull { o ->
                val id = o.str("id") ?: return@mapNotNull null
                val info = o.optJSONObject("info")
                VodEpisode(
                    id = id,
                    season = o.int("season") ?: seasonKey ?: 1,
                    episode = o.int("episode_num") ?: 0,
                    title = o.str("title") ?: "Folge",
                    extension = o.str("container_extension") ?: "mp4",
                    image = info?.img("movie_image"),
                    plot = info?.str("plot"),
                    durationSec = info?.int("duration_secs") ?: 0,
                )
            }
        }.sortedWith(compareBy({ it.season }, { it.episode }))
    }

    fun movieUrl(m: VodMovie) = "$base/movie/${account.username}/${account.password}/${m.id}.${m.extension}"
    fun episodeUrl(e: VodEpisode) = "$base/series/${account.username}/${account.password}/${e.id}.${e.extension}"
}
