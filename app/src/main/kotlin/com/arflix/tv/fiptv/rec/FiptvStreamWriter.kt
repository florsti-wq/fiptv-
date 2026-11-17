package com.arflix.tv.fiptv.rec

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.net.URL
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * fIPTV: schreibt einen Live-Stream in eine Datei – für Aufnahmen und Live-Pause.
 *  - TS/progressiv: der Datenstrom wird direkt mitgeschrieben.
 *  - HLS: die Playlist wird regelmäßig gelesen, neue Segmente werden angehängt
 *    (TS oder fMP4 mit Init-Segment, AES-128 wird entschlüsselt).
 * Endet mit [stop], bei [endAtMs], bei [maxBytes] oder wenn der Stream endet.
 */
class FiptvStreamWriter(
    private val url: String,
    private val isHls: Boolean,
    private val headers: Map<String, String>,
    val file: File,
    private val endAtMs: Long? = null,
    private val maxBytes: Long = Long.MAX_VALUE,
) {
    @Volatile var running = false
        private set
    @Volatile var bytes = 0L
        private set
    @Volatile var error: String? = null
        private set
    /** fMP4-Segmente (statt TS) – Dateiendung .mp4 sinnvoll. */
    @Volatile var fragmentedMp4 = false
        private set

    private var job: Job? = null

    fun start(scope: CoroutineScope): Job {
        running = true
        file.parentFile?.mkdirs()
        val j = scope.launch(Dispatchers.IO) {
            try {
                FileOutputStream(file, false).use { out ->
                    if (isHls || url.substringBefore('?').endsWith(".m3u8", ignoreCase = true)) hls(out) else progressive(out)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // gestoppt
            } catch (e: Throwable) {
                error = e.message ?: e.javaClass.simpleName
            } finally {
                running = false
            }
        }
        job = j
        return j
    }

    fun stop() {
        running = false
        job?.cancel()
    }

    private fun done(): Boolean =
        !running || (endAtMs != null && System.currentTimeMillis() >= endAtMs) || bytes >= maxBytes

    private fun request(u: String): Request = Request.Builder().url(u).apply {
        headers.forEach { (k, v) -> runCatching { header(k, v) } }
        if (headers.keys.none { it.equals("User-Agent", true) }) header("User-Agent", "fIPTV")
    }.build()

    private fun write(out: OutputStream, data: ByteArray, len: Int) {
        out.write(data, 0, len)
        bytes += len
    }

    private suspend fun progressive(out: OutputStream) {
        client.newCall(request(url)).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
            val input = resp.body?.byteStream() ?: throw IllegalStateException("leere Antwort")
            val buf = ByteArray(64 * 1024)
            while (kotlinx.coroutines.currentCoroutineContext().isActive && !done()) {
                val n = input.read(buf)
                if (n < 0) break
                if (n > 0) { write(out, buf, n); out.flush() }
            }
        }
    }

    private fun text(u: String): String = client.newCall(request(u)).execute().use { r ->
        if (!r.isSuccessful) throw IllegalStateException("HTTP ${r.code}")
        r.body?.string().orEmpty()
    }

    private fun bin(u: String): ByteArray = client.newCall(request(u)).execute().use { r ->
        if (!r.isSuccessful) throw IllegalStateException("HTTP ${r.code}")
        r.body?.bytes() ?: ByteArray(0)
    }

    private fun resolve(base: String, rel: String): String = runCatching { URL(URL(base), rel).toString() }.getOrDefault(rel)

    private fun attr(line: String, name: String): String? {
        val m = Regex("""$name=("([^"]*)"|[^,]*)""").find(line) ?: return null
        return m.groupValues[2].ifEmpty { m.groupValues[1] }
    }

    private suspend fun hls(out: OutputStream) {
        var playlistUrl = url
        // Master-Playlist → Variante mit der höchsten Bandbreite
        var body = text(playlistUrl)
        if (body.contains("#EXT-X-STREAM-INF")) {
            val lines = body.lines()
            var best: Pair<Long, String>? = null
            lines.forEachIndexed { i, l ->
                if (l.startsWith("#EXT-X-STREAM-INF")) {
                    val bw = attr(l, "BANDWIDTH")?.toLongOrNull() ?: 0L
                    val uri = lines.drop(i + 1).firstOrNull { it.isNotBlank() && !it.startsWith("#") } ?: return@forEachIndexed
                    if (best == null || bw > best!!.first) best = bw to uri.trim()
                }
            }
            playlistUrl = resolve(playlistUrl, best?.second ?: throw IllegalStateException("keine Variante"))
            body = text(playlistUrl)
        }
        var lastSeq = -1L
        var initWritten = false
        var keyUrl: String? = null
        var keyBytes: ByteArray? = null
        while (kotlinx.coroutines.currentCoroutineContext().isActive && !done()) {
            val lines = body.lines()
            var seq = lines.firstOrNull { it.startsWith("#EXT-X-MEDIA-SEQUENCE") }?.substringAfter(':')?.trim()?.toLongOrNull() ?: 0L
            val target = lines.firstOrNull { it.startsWith("#EXT-X-TARGETDURATION") }?.substringAfter(':')?.trim()?.toDoubleOrNull() ?: 6.0
            var curKeyMethod: String? = null
            var curKeyUri: String? = null
            var curIv: String? = null
            var pendingSegment = false
            for (raw in lines) {
                val line = raw.trim()
                when {
                    line.startsWith("#EXT-X-MAP") && !initWritten -> {
                        attr(line, "URI")?.let { u ->
                            val init = bin(resolve(playlistUrl, u))
                            write(out, init, init.size)
                            fragmentedMp4 = true
                            initWritten = true
                        }
                    }
                    line.startsWith("#EXT-X-KEY") -> {
                        curKeyMethod = attr(line, "METHOD")
                        curKeyUri = attr(line, "URI")?.let { resolve(playlistUrl, it) }
                        curIv = attr(line, "IV")
                    }
                    line.startsWith("#EXTINF") -> pendingSegment = true
                    line.isNotEmpty() && !line.startsWith("#") && pendingSegment -> {
                        pendingSegment = false
                        val thisSeq = seq++
                        if (thisSeq <= lastSeq) continue
                        if (done()) break
                        var data = runCatching { bin(resolve(playlistUrl, line)) }.getOrNull() ?: continue
                        if (curKeyMethod.equals("AES-128", true) && curKeyUri != null) {
                            if (curKeyUri != keyUrl) { keyBytes = bin(curKeyUri!!); keyUrl = curKeyUri }
                            val iv = curIv?.removePrefix("0x")?.removePrefix("0X")?.padStart(32, '0')
                                ?.chunked(2)?.map { it.toInt(16).toByte() }?.toByteArray()
                                ?: ByteArray(16).also { b -> var s = thisSeq; for (i in 15 downTo 8) { b[i] = (s and 0xFF).toByte(); s = s shr 8 } }
                            val c = Cipher.getInstance("AES/CBC/PKCS5Padding")
                            c.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes!!, "AES"), IvParameterSpec(iv))
                            data = c.doFinal(data)
                        } else if (curKeyMethod != null && !curKeyMethod.equals("NONE", true)) {
                            throw IllegalStateException("verschlüsselter Stream wird nicht unterstützt")
                        }
                        write(out, data, data.size)
                        out.flush()
                        lastSeq = thisSeq
                    }
                }
            }
            if (body.contains("#EXT-X-ENDLIST")) break
            delay((target * 500).toLong().coerceIn(1_000L, 6_000L))
            body = runCatching { text(playlistUrl) }.getOrElse { delay(2_000); text(playlistUrl) }
        }
    }

    companion object {
        private val client: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()
        }
    }
}
