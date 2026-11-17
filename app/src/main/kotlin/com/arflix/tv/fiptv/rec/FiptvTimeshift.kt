@file:Suppress("UnsafeOptInUsageError")

package com.arflix.tv.fiptv.rec

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import kotlinx.coroutines.CoroutineScope
import java.io.File

/**
 * fIPTV: Live-Pause. Beim Pausieren wird der Sender ab diesem Moment in eine Datei
 * zwischengespeichert; beim Weiterspielen läuft die Sendung zeitversetzt aus dieser Datei,
 * während im Hintergrund weiter mitgeschrieben wird.
 */
class FiptvTimeshift {
    var writer by mutableStateOf<FiptvStreamWriter?>(null)
        private set
    var playing by mutableStateOf(false)
        private set
    var pausedAt = 0L
        private set

    val active: Boolean get() = writer != null

    fun begin(context: Context, scope: CoroutineScope, url: String, isHls: Boolean, headers: Map<String, String>): Boolean {
        stop()
        val dir = FiptvLibrary.storageDir(context, "timeshift")
        dir.listFiles()?.forEach { runCatching { it.delete() } }
        val free = FiptvLibrary.freeBytes(dir)
        val max = minOf((free * 0.6).toLong(), 8L shl 30)
        if (max < (200L shl 20)) return false
        val w = FiptvStreamWriter(url, isHls, headers, File(dir, "live.ts"), maxBytes = max)
        w.start(scope)
        writer = w
        playing = false
        pausedAt = System.currentTimeMillis()
        return true
    }

    /** Zeitversetzt weiterspielen. */
    fun play(player: ExoPlayer) {
        val w = writer ?: return
        val source = ProgressiveMediaSource.Factory(FiptvTailDataSource.Factory(w.file) { w.running })
            .createMediaSource(MediaItem.Builder().setUri(Uri.fromFile(w.file)).setMediaId("fiptv-timeshift").build())
        player.setMediaSource(source)
        player.prepare()
        player.playWhenReady = true
        playing = true
    }

    fun stop() {
        writer?.let { w ->
            w.stop()
            runCatching { w.file.delete() }
        }
        writer = null
        playing = false
    }

    companion object {
        private const val PREFS = "fiptv_timeshift"
        var enabled by mutableStateOf(true)
            private set
        private var appContext: Context? = null

        fun init(context: Context) {
            appContext = context.applicationContext
            enabled = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("enabled", true)
            // Reste einer früheren Live-Pause wegräumen
            runCatching { FiptvLibrary.storageDir(context, "timeshift").listFiles()?.forEach { it.delete() } }
        }

        fun toggle() {
            enabled = !enabled
            appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.putBoolean("enabled", enabled)?.apply()
        }
    }
}
