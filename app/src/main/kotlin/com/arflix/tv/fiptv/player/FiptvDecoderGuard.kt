package com.arflix.tv.fiptv.player

import android.content.Context
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.runtime.mutableIntStateOf
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext

/**
 * fIPTV: Schutz gegen eingefrorenes Bild (Ton läuft, Bild steht).
 * Passiert auf Fire TV, wenn Fire OS die App nach einem Update hart beendet, während ein Video läuft –
 * der Hardware-Decoder bleibt dann bis zum Neustart des Geräts hängen.
 * 1) Erkennen: Wiedergabe läuft, aber > 6 s kein neues Bild → Stream neu aufbauen.
 * 2) Wieder eingefroren (innerhalb 2 Min.) → Software-Decoder bis zum nächsten Geräte-Neustart.
 */
@UnstableApi
object FiptvDecoderGuard {
    private const val PREFS = "fiptv_decoder"
    /** Wird erhöht, wenn der Player mit anderem Decoder neu gebaut werden muss. */
    val generation = mutableIntStateOf(0)
    @Volatile private var software: Boolean? = null
    private var lastRecoveryAt = 0L

    /** Software-Decoder aktiv? Gilt nur bis zum nächsten Geräte-Neustart. */
    fun softwareDecoding(context: Context): Boolean {
        software?.let { return it }
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val bootAt = System.currentTimeMillis() - SystemClock.elapsedRealtime()
        val savedBoot = p.getLong("boot", 0L)
        val on = p.getBoolean("software", false) && kotlin.math.abs(savedBoot - bootAt) < 120_000L
        if (!on && p.getBoolean("software", false)) p.edit().putBoolean("software", false).apply()
        software = on
        return on
    }

    private fun enableSoftware(context: Context) {
        val bootAt = System.currentTimeMillis() - SystemClock.elapsedRealtime()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("software", true).putLong("boot", bootAt).apply()
        software = true
        generation.intValue++
    }

    /** Decoder-Auswahl: im Software-Modus Software-Decoder (und FFmpeg-Erweiterung) zuerst. */
    fun apply(factory: DefaultRenderersFactory, context: Context): DefaultRenderersFactory {
        factory.setEnableDecoderFallback(true)
        if (softwareDecoding(context)) {
            factory.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            factory.setMediaCodecSelector(SOFTWARE_FIRST)
        }
        return factory
    }

    private val SOFTWARE_FIRST = MediaCodecSelector { mime, secure, tunneling ->
        MediaCodecUtil.getDecoderInfos(mime, secure, tunneling).sortedBy { if (it.softwareOnly) 0 else 1 }
    }

    /**
     * Läuft solange der Player lebt. [restart] baut den aktuellen Stream neu auf (gleicher Player).
     */
    suspend fun watch(player: ExoPlayer, context: Context, restart: () -> Unit) {
        var lastRendered = -1
        var stalledSince = 0L
        while (coroutineContext.isActive) {
            delay(2_000)
            val counters = player.videoDecoderCounters
            val hasVideo = player.videoFormat != null
            if (!player.isPlaying || !hasVideo || counters == null) { lastRendered = -1; stalledSince = 0L; continue }
            counters.ensureUpdated()
            val rendered = counters.renderedOutputBufferCount
            val now = SystemClock.elapsedRealtime()
            if (rendered != lastRendered) { lastRendered = rendered; stalledSince = 0L; continue }
            if (stalledSince == 0L) { stalledSince = now; continue }
            if (now - stalledSince < 6_000) continue
            // Bild steht seit > 6 s, Wiedergabe läuft
            stalledSince = 0L; lastRendered = -1
            if (now - lastRecoveryAt < 120_000 && !softwareDecoding(context)) {
                Toast.makeText(context, "Bild hing – fIPTV nutzt bis zum nächsten Fire-TV-Neustart den Software-Decoder", Toast.LENGTH_LONG).show()
                enableSoftware(context)
                return
            }
            lastRecoveryAt = now
            runCatching { restart() }
        }
    }
}
