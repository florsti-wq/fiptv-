package com.arflix.tv.fiptv.youtube

import com.arflix.tv.BuildConfig
import com.arflix.tv.network.OkHttpProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

data class YtVideo(
    val id: String,
    val title: String,
    val channelId: String,
    val channelTitle: String,
    val thumbnail: String?,
    val publishedAt: String,
    val durationSec: Int = 0,
    val isLive: Boolean = false,
)

data class YtChannel(
    val id: String,
    val title: String,
    val thumbnail: String?,
)

data class YtPage<T>(val items: List<T>, val nextPageToken: String?)

/**
 * fIPTV: Zugriff auf die YouTube Data API v3 (nur Lesen, mit API-Schlüssel, ohne Anmeldung).
 * Abgespielt wird ausschließlich mit dem offiziellen YouTube-Player (IFrame) – Werbung bleibt erhalten.
 *
 * Kosten pro Tag (frei: 10 000 Einheiten): Suche 100, alles andere 1 Einheit je Aufruf.
 */
object YouTubeRepository {
    private const val BASE = "https://www.googleapis.com/youtube/v3"
    private const val REGION = "DE"
    private const val LANGUAGE = "de"

    val apiKey: String
        get() = BuildConfig.YOUTUBE_API_KEY.trim().takeUnless { it.isBlank() || it.startsWith("your-") }.orEmpty()

    val isConfigured: Boolean get() = apiKey.isNotEmpty()

    /** Beliebte Videos in Deutschland. */
    suspend fun trending(pageToken: String? = null): YtPage<YtVideo> {
        val json = get(
            "videos",
            "part" to "snippet,contentDetails",
            "chart" to "mostPopular",
            "regionCode" to REGION,
            "hl" to LANGUAGE,
            "maxResults" to "30",
            "pageToken" to pageToken,
        )
        return YtPage(json.optJSONArray("items").mapObjects(::parseVideo), json.optStringOrNull("nextPageToken"))
    }

    /** Suche nach Videos; Kanäle mit passendem Namen kommen getrennt zurück. */
    suspend fun search(query: String, pageToken: String? = null): Pair<YtPage<YtVideo>, List<YtChannel>> {
        val json = get(
            "search",
            "part" to "snippet",
            "q" to query,
            "type" to "video,channel",
            "regionCode" to REGION,
            "relevanceLanguage" to LANGUAGE,
            "safeSearch" to "moderate",
            "maxResults" to "30",
            "pageToken" to pageToken,
        )
        val items = json.optJSONArray("items").mapObjects { it }
        val channels = items.filter { it.optJSONObject("id")?.optString("kind") == "youtube#channel" }.map { item ->
            val sn = item.optJSONObject("snippet") ?: JSONObject()
            YtChannel(
                id = item.getJSONObject("id").optString("channelId"),
                title = sn.optString("channelTitle").ifBlank { sn.optString("title") },
                thumbnail = thumb(sn),
            )
        }.filter { it.id.isNotBlank() }
        val videoIds = items.mapNotNull { it.optJSONObject("id")?.optStringOrNull("videoId") }
        return YtPage(details(videoIds), json.optStringOrNull("nextPageToken")) to channels
    }

    /** Neueste Videos eines Kanals (über die „Uploads“-Playlist, kostet nur 1–3 Einheiten). */
    suspend fun channelVideos(channelId: String, max: Int = 30, pageToken: String? = null): Pair<YtChannel?, YtPage<YtVideo>> {
        val ch = get("channels", "part" to "snippet,contentDetails", "id" to channelId)
            .optJSONArray("items")?.optJSONObject(0)
            ?: return null to YtPage(emptyList(), null)
        val sn = ch.optJSONObject("snippet") ?: JSONObject()
        val channel = YtChannel(channelId, sn.optString("title"), thumb(sn))
        val uploads = ch.optJSONObject("contentDetails")?.optJSONObject("relatedPlaylists")?.optStringOrNull("uploads")
            ?: return channel to YtPage(emptyList(), null)
        val list = get(
            "playlistItems",
            "part" to "contentDetails",
            "playlistId" to uploads,
            "maxResults" to max.coerceIn(1, 50).toString(),
            "pageToken" to pageToken,
        )
        val ids = list.optJSONArray("items").mapObjects { it.optJSONObject("contentDetails")?.optString("videoId").orEmpty() }
            .filter { it.isNotBlank() }
        return channel to YtPage(details(ids), list.optStringOrNull("nextPageToken"))
    }

    /** Dauer und Live-Status gibt es nur über videos.list (1 Einheit für bis zu 50 Videos). */
    private suspend fun details(ids: List<String>): List<YtVideo> {
        if (ids.isEmpty()) return emptyList()
        val json = get("videos", "part" to "snippet,contentDetails", "id" to ids.take(50).joinToString(","), "hl" to LANGUAGE)
        val byId = json.optJSONArray("items").mapObjects(::parseVideo).associateBy { it.id }
        return ids.mapNotNull { byId[it] }
    }

    private fun parseVideo(item: JSONObject): YtVideo {
        val sn = item.optJSONObject("snippet") ?: JSONObject()
        val localized = sn.optJSONObject("localized")
        return YtVideo(
            id = item.optString("id"),
            title = localized?.optStringOrNull("title") ?: sn.optString("title"),
            channelId = sn.optString("channelId"),
            channelTitle = sn.optString("channelTitle"),
            thumbnail = thumb(sn),
            publishedAt = sn.optString("publishedAt"),
            durationSec = parseIsoDuration(item.optJSONObject("contentDetails")?.optString("duration").orEmpty()),
            isLive = sn.optString("liveBroadcastContent") == "live",
        )
    }

    private fun thumb(snippet: JSONObject): String? {
        val t = snippet.optJSONObject("thumbnails") ?: return null
        for (k in listOf("medium", "high", "standard", "default")) {
            t.optJSONObject(k)?.optStringOrNull("url")?.let { return it }
        }
        return null
    }

    /** ISO-8601-Dauer wie „PT1H2M3S“ → Sekunden. */
    fun parseIsoDuration(value: String): Int {
        val m = Regex("""P(?:(\d+)D)?T?(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?""").matchEntire(value) ?: return 0
        val (d, h, min, s) = m.destructured
        return (d.toIntOrNull() ?: 0) * 86400 + (h.toIntOrNull() ?: 0) * 3600 + (min.toIntOrNull() ?: 0) * 60 + (s.toIntOrNull() ?: 0)
    }

    fun formatDuration(sec: Int): String {
        if (sec <= 0) return ""
        val h = sec / 3600
        val m = (sec % 3600) / 60
        val s = sec % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    /** „vor 3 Tagen“ usw. */
    fun formatAge(publishedAt: String, now: Long = System.currentTimeMillis()): String {
        val t = runCatching { java.time.Instant.parse(publishedAt).toEpochMilli() }.getOrNull() ?: return ""
        val min = ((now - t) / 60_000).coerceAtLeast(0)
        return when {
            min < 60 -> "vor $min Min."
            min < 60 * 24 -> "vor ${min / 60} Std."
            min < 60 * 24 * 30 -> (min / (60 * 24)).let { if (it == 1L) "vor 1 Tag" else "vor $it Tagen" }
            min < 60 * 24 * 365 -> (min / (60 * 24 * 30)).let { if (it == 1L) "vor 1 Monat" else "vor $it Monaten" }
            else -> (min / (60 * 24 * 365)).let { if (it == 1L) "vor 1 Jahr" else "vor $it Jahren" }
        }
    }

    private suspend fun get(path: String, vararg params: Pair<String, String?>): JSONObject = withContext(Dispatchers.IO) {
        check(isConfigured) { "YouTube ist noch nicht eingerichtet (API-Schlüssel fehlt)." }
        val url = "$BASE/$path".toHttpUrl().newBuilder().apply {
            params.forEach { (k, v) -> if (!v.isNullOrBlank()) addQueryParameter(k, v) }
            addQueryParameter("key", apiKey)
        }.build()
        val request = Request.Builder()
            .url(url)
            // Für einen auf Android-Apps beschränkten Schlüssel (optional)
            .header("X-Android-Package", BuildConfig.APPLICATION_ID.removeSuffix(".beta"))
            .build()
        OkHttpProvider.client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val reason = runCatching {
                    JSONObject(body).optJSONObject("error")?.optJSONArray("errors")?.optJSONObject(0)?.optString("reason")
                }.getOrNull()
                throw IllegalStateException(
                    when (reason) {
                        "quotaExceeded", "dailyLimitExceeded" -> "Das Tageskontingent für YouTube ist aufgebraucht. Morgen geht es wieder."
                        "keyInvalid", "badRequest" -> "Der YouTube-API-Schlüssel ist ungültig."
                        "accessNotConfigured" -> "Im Google-Projekt ist die „YouTube Data API v3“ noch nicht aktiviert."
                        else -> "YouTube antwortet nicht (Fehler ${response.code}${reason?.let { ": $it" } ?: ""})."
                    }
                )
            }
            JSONObject(body)
        }
    }

    private fun JSONObject.optStringOrNull(name: String): String? =
        if (has(name) && !isNull(name)) optString(name).takeIf { it.isNotBlank() } else null

    private fun <T> JSONArray?.mapObjects(transform: (JSONObject) -> T): List<T> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { optJSONObject(it)?.let(transform) }
    }
}
