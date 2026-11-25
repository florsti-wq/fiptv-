@file:Suppress("UnsafeOptInUsageError")

package com.arflix.tv.fiptv.player

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.core.content.FileProvider
import androidx.media3.common.Format
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * fIPTV: „Stream-Problem melden“ (Idee aus OwnTV, eigene Umsetzung).
 * Sammelt, was beim Abspielen passiert (Fehler, Ruckler, Decoder, Formate) und schreibt auf Wunsch
 * einen Bericht als Textdatei. Zugangsdaten werden nie aufgenommen: von Adressen bleibt nur der Server.
 */
object FiptvStreamReport {
    private const val MAX_EVENTS = 40
    private val events = ArrayDeque<String>()
    private val time = SimpleDateFormat("HH:mm:ss", Locale.GERMANY)
    @Volatile private var videoDecoder: String? = null
    @Volatile private var audioDecoder: String? = null
    @Volatile private var rebuffers = 0

    fun reportFile(context: Context): File = File(File(context.cacheDir, "reports").apply { mkdirs() }, "fiptv-stream-bericht.txt")
    fun hasReport(context: Context): Boolean = reportFile(context).exists()

    private fun log(text: String) = synchronized(events) {
        events.addLast(time.format(Date()) + "  " + text)
        while (events.size > MAX_EVENTS) events.removeFirst()
    }

    /** Nur Schema, Server und Port – Pfad und Parameter enthalten oft Benutzername/Passwort. */
    fun redact(url: String?): String {
        if (url.isNullOrBlank()) return "–"
        return runCatching {
            val u = java.net.URI(url.trim().substringBefore(' '))
            val port = if (u.port > 0) ":" + u.port else ""
            val ext = u.path?.substringAfterLast('.', "")?.takeIf { it.length in 2..5 }?.let { " (…$it)" } ?: ""
            "${u.scheme}://${u.host}$port/…$ext"
        }.getOrDefault("(Adresse ausgeblendet)")
    }

    /** Am Live-TV-Player anhängen; läuft solange der Player lebt. */
    fun attach(player: ExoPlayer) {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_BUFFERING -> { rebuffers++; log("lädt / puffert") }
                    Player.STATE_READY -> log("läuft")
                    Player.STATE_ENDED -> log("Stream zu Ende")
                    Player.STATE_IDLE -> log("angehalten")
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                log("FEHLER ${error.errorCodeName}: ${error.message?.take(160)}" +
                    (error.cause?.let { " · Ursache: ${it.javaClass.simpleName} ${it.message?.take(120)}" } ?: ""))
            }
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                rebuffers = 0
                log("neuer Stream (Grund $reason)")
            }
        })
        player.addAnalyticsListener(object : AnalyticsListener {
            override fun onVideoDecoderInitialized(eventTime: AnalyticsListener.EventTime, decoderName: String, initializedTimestampMs: Long, initializationDurationMs: Long) {
                videoDecoder = decoderName; log("Bild-Decoder: $decoderName")
            }
            override fun onAudioDecoderInitialized(eventTime: AnalyticsListener.EventTime, decoderName: String, initializedTimestampMs: Long, initializationDurationMs: Long) {
                audioDecoder = decoderName; log("Ton-Decoder: $decoderName")
            }
            override fun onDroppedVideoFrames(eventTime: AnalyticsListener.EventTime, droppedFrames: Int, elapsedMs: Long) {
                if (droppedFrames >= 10) log("$droppedFrames Bilder ausgelassen in ${elapsedMs} ms")
            }
            override fun onAudioUnderrun(eventTime: AnalyticsListener.EventTime, bufferSize: Int, bufferSizeMs: Long, elapsedSinceLastFeedMs: Long) {
                log("Ton-Aussetzer (Puffer leer)")
            }
            override fun onLoadError(eventTime: AnalyticsListener.EventTime, loadEventInfo: androidx.media3.exoplayer.source.LoadEventInfo, mediaLoadData: androidx.media3.exoplayer.source.MediaLoadData, error: java.io.IOException, wasCanceled: Boolean) {
                log("Ladefehler: ${error.javaClass.simpleName} ${error.message?.let { redactText(it) }?.take(140)}")
            }
        })
    }

    /** Adressen in Fehlermeldungen kürzen. */
    private fun redactText(text: String): String =
        Regex("""https?://[^\s"']+""").replace(text) { redact(it.value) }

    private fun fmt(f: Format?): String {
        if (f == null) return "–"
        val parts = mutableListOf<String>()
        (f.codecs ?: f.sampleMimeType)?.let { parts += it }
        if (f.width > 0) parts += "${f.width}×${f.height}"
        if (f.frameRate > 0) parts += "%.0f fps".format(f.frameRate)
        if (f.channelCount > 0) parts += "${f.channelCount} Kanäle"
        if (f.sampleRate > 0) parts += "${f.sampleRate} Hz"
        if (f.bitrate > 0) parts += "${f.bitrate / 1000} kbit/s"
        f.language?.let { parts += "Sprache $it" }
        return parts.joinToString(", ")
    }

    private fun network(context: Context): String = runCatching {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return "keine Verbindung"
        val type = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "LAN-Kabel"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WLAN"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobilfunk"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "anderes"
        }
        "$type, ca. ${caps.linkDownstreamBandwidthKbps / 1000} Mbit/s (Schätzung des Systems)"
    }.getOrDefault("unbekannt")

    /** Bericht schreiben und Datei zurückgeben. */
    fun create(context: Context, player: ExoPlayer, channelName: String, streamUrl: String?, extra: Map<String, String> = emptyMap()): File {
        val versionName = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
        val counters = player.videoDecoderCounters?.also { it.ensureUpdated() }
        val sb = StringBuilder()
        sb.appendLine("fIPTV – Stream-Bericht")
        sb.appendLine("Erstellt: " + SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.GERMANY).format(Date()))
        sb.appendLine("App: $versionName · Gerät: ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}")
        sb.appendLine("Netz: ${network(context)}")
        sb.appendLine()
        sb.appendLine("Sender: $channelName")
        sb.appendLine("Server: ${redact(streamUrl)}")
        sb.appendLine("Zustand: " + when (player.playbackState) { Player.STATE_READY -> if (player.isPlaying) "läuft" else "pausiert"; Player.STATE_BUFFERING -> "puffert"; Player.STATE_ENDED -> "zu Ende"; else -> "angehalten" })
        player.playerError?.let { sb.appendLine("Letzter Fehler: ${it.errorCodeName} – ${it.message?.let(::redactText)}") }
        sb.appendLine("Bild: ${fmt(player.videoFormat)}")
        sb.appendLine("Ton: ${fmt(player.audioFormat)}")
        sb.appendLine("Decoder: Bild ${videoDecoder ?: "–"} · Ton ${audioDecoder ?: "–"}")
        sb.appendLine("Software-Decoder-Modus: " + if (FiptvDecoderGuard.softwareDecoding(context)) "an" else "aus")
        sb.appendLine("Live-Verzögerung: ${FiptvLiveLatency.label()} · Tempo ${player.playbackParameters.speed}")
        sb.appendLine("Puffer voraus: ${(player.bufferedPosition - player.currentPosition).coerceAtLeast(0) / 1000.0} s · Ruckler seit Senderstart: $rebuffers")
        counters?.let { sb.appendLine("Bilder: gezeigt ${it.renderedOutputBufferCount}, ausgelassen ${it.droppedBufferCount}, übersprungen ${it.skippedOutputBufferCount}") }
        extra.forEach { (k, v) -> sb.appendLine("$k: $v") }
        sb.appendLine()
        sb.appendLine("Letzte Ereignisse:")
        synchronized(events) { events.forEach { sb.appendLine("  $it") } }
        val file = reportFile(context)
        file.writeText(sb.toString())
        return file
    }

    /** Handy: Teilen-Menü öffnen (Mail, Messenger …). */
    fun share(context: Context, file: File): Boolean = runCatching {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "fIPTV Stream-Bericht")
            putExtra(Intent.EXTRA_TEXT, file.readText())
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Stream-Bericht senden").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    }.getOrDefault(false)
}
