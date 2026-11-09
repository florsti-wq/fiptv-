package com.arflix.tv.fiptv.mediathek

import com.arflix.tv.network.OkHttpProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * fIPTV: Mediatheken der öffentlich-rechtlichen Sender (ARD, ZDF, arte, 3sat, ORF, SRF …)
 * über die freie Schnittstelle von MediathekViewWeb (https://mediathekviewweb.de).
 */
data class MediathekItem(
    val id: String,
    val channel: String,
    val topic: String,
    val title: String,
    val description: String?,
    /** Sendezeit in Millisekunden */
    val startMs: Long,
    /** Sekunden */
    val durationSec: Int,
    val videoHd: String?,
    val videoSd: String?,
    val videoLow: String?,
    val subtitleUrl: String?,
) {
    /** beste verfügbare Qualität */
    val bestVideo: String? get() = videoHd ?: videoSd ?: videoLow
}

data class MediathekChannel(val id: String, val label: String)

data class MediathekPage(val items: List<MediathekItem>, val total: Int?)

object MediathekRepository {
    private const val API = "https://mediathekviewweb.de/api/query"

    val channels = listOf(
        MediathekChannel("ard", "ARD"),
        MediathekChannel("zdf", "ZDF"),
        MediathekChannel("arte", "arte"),
        MediathekChannel("3sat", "3sat"),
        MediathekChannel("phoenix", "phoenix"),
        MediathekChannel("kika", "KiKA"),
        MediathekChannel("br", "BR"),
        MediathekChannel("hr", "HR"),
        MediathekChannel("mdr", "MDR"),
        MediathekChannel("ndr", "NDR"),
        MediathekChannel("rbb", "rbb"),
        MediathekChannel("swr", "SWR"),
        MediathekChannel("wdr", "WDR"),
        MediathekChannel("orf", "ORF"),
        MediathekChannel("srf", "SRF"),
    )

    /**
     * Suchtext in Teilabfragen zerlegen – wie auf mediathekviewweb.de:
     * !Sender  #Thema  +Titel  *Beschreibung, alles andere sucht in Titel und Thema.
     */
    fun parseSearch(text: String?): List<Pair<List<String>, String>> {
        val out = mutableListOf<Pair<List<String>, String>>()
        val free = mutableListOf<String>()
        fun add(fields: List<String>, q: String) {
            val v = q.replace(',', ' ').trim()
            if (v.isNotEmpty()) out += fields to v
        }
        for (tok in (text ?: "").trim().split(Regex("\\s+")).filter { it.isNotEmpty() }) {
            val rest = tok.drop(1)
            when (tok.first()) {
                '!' -> add(listOf("channel"), rest)
                '#' -> add(listOf("topic"), rest)
                '+' -> add(listOf("title"), rest)
                '*' -> add(listOf("description"), rest)
                else -> free += tok
            }
        }
        if (free.isNotEmpty()) add(listOf("title", "topic"), free.joinToString(" "))
        return out
    }

    fun buildQuery(search: String?, channel: String?, minDurationSec: Int?, offset: Int, size: Int, topic: String? = null): JSONObject {
        val queries = JSONArray()
        val parts = parseSearch(search).toMutableList()
        if (!channel.isNullOrBlank()) parts += listOf("channel") to channel
        // ganzes Thema (Sendungsname) als eine Abfrage, auch mit Leerzeichen
        if (!topic.isNullOrBlank()) parts += listOf("topic") to topic.replace(',', ' ').trim()
        for ((fields, q) in parts) {
            queries.put(JSONObject().put("fields", JSONArray(fields)).put("query", q))
        }
        return JSONObject()
            .put("queries", queries)
            .put("sortBy", "timestamp")
            .put("sortOrder", "desc")
            .put("future", false)
            .put("offset", offset)
            .put("size", size)
            .apply { if (minDurationSec != null && minDurationSec > 0) put("duration_min", minDurationSec) }
    }

    private fun url(v: Any?): String? =
        (v as? String)?.trim()?.takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) }

    fun parseResponse(json: JSONObject): MediathekPage {
        val err = json.optJSONArray("err")
        if (err != null && err.length() > 0) error("Mediathek: ${err.join(", ")}")
        val result = json.optJSONObject("result") ?: return MediathekPage(emptyList(), null)
        val list = result.optJSONArray("results") ?: JSONArray()
        val items = buildList {
            for (i in 0 until list.length()) {
                val r = list.optJSONObject(i) ?: continue
                val hd = url(r.opt("url_video_hd"))
                val sd = url(r.opt("url_video"))
                val low = url(r.opt("url_video_low"))
                if (hd == null && sd == null && low == null) continue
                val channel = r.optString("channel")
                val topic = r.optString("topic")
                val title = r.optString("title").ifBlank { topic }
                add(
                    MediathekItem(
                        id = r.optString("id").ifBlank { "$channel|$topic|$title|${r.optLong("timestamp")}" },
                        channel = channel,
                        topic = topic,
                        title = title,
                        description = r.optString("description").trim().takeIf { it.isNotEmpty() },
                        startMs = r.optLong("timestamp") * 1000L,
                        durationSec = r.optInt("duration"),
                        videoHd = hd,
                        videoSd = sd,
                        videoLow = low,
                        subtitleUrl = url(r.opt("url_subtitle")),
                    )
                )
            }
        }
        val total = result.optJSONObject("queryInfo")?.optInt("totalResults", -1)?.takeIf { it >= 0 }
        return MediathekPage(items, total)
    }

    suspend fun query(
        search: String?,
        channel: String?,
        minDurationSec: Int? = null,
        offset: Int = 0,
        size: Int = 40,
        topic: String? = null,
    ): MediathekPage = withContext(Dispatchers.IO) {
        val body = buildQuery(search, channel, minDurationSec, offset, size, topic).toString()
            // MediathekViewWeb erwartet text/plain mit JSON-Inhalt
            .toRequestBody("text/plain; charset=utf-8".toMediaType())
        val request = Request.Builder().url(API).post(body).build()
        OkHttpProvider.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Mediathek: HTTP ${response.code}")
            parseResponse(JSONObject(response.body?.string().orEmpty()))
        }
    }

    fun formatDuration(sec: Int): String {
        if (sec <= 0) return ""
        val m = Math.round(sec / 60.0).toInt()
        return if (m >= 60) {
            val h = m / 60
            val r = m % 60
            if (r > 0) "$h Std. $r Min." else "$h Std."
        } else "${maxOf(1, m)} Min."
    }

    /** Kurzname des Senders (ARD, ZDF …) */
    fun channelLabel(channel: String): String =
        channels.firstOrNull { channel.contains(it.id, ignoreCase = true) }?.label ?: channel

    /** Themen, die nur ein Sammelbehälter sind (z. B. „Filme im Ersten“) – dann zählt der Titel. */
    private val containerTopic = Regex("(?i)(film|kino|movie|doku|reportage|dokumentation|serie|krimi|thriller|komödie|drama)")
    fun isContainerTopic(topic: String): Boolean = topic.isBlank() || containerTopic.containsMatchIn(topic)

    /** Schlüssel für „eine Kachel pro Sendung“ */
    fun showKey(item: MediathekItem): String =
        if (isContainerTopic(item.topic)) "t:" + item.title.lowercase() else "s:" + item.topic.lowercase()

    /** Farbe je Sender für Kacheln ohne Bild */
    fun channelColor(channel: String): Long = when (channelLabel(channel)) {
        "ARD" -> 0xFF0B3D91
        "ZDF" -> 0xFFF07800
        "arte" -> 0xFFE4312B
        "3sat" -> 0xFF9C1C8C
        "phoenix" -> 0xFF0073B7
        "KiKA" -> 0xFF2EA43A
        "BR" -> 0xFF0067B3
        "HR" -> 0xFF005EB8
        "MDR" -> 0xFF004B87
        "NDR" -> 0xFF1460AA
        "rbb" -> 0xFFE31F25
        "SWR" -> 0xFF00A0D2
        "WDR" -> 0xFF00305E
        "ORF" -> 0xFFD7141A
        "SRF" -> 0xFFAF001E
        else -> 0xFF3A3F55
    }
}
