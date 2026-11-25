package com.arflix.tv.fiptv.player

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext

/**
 * fIPTV: Live-Verzögerung (Idee aus Offside IPTV, eigene Umsetzung).
 * 0 = Normal (stabil, wie bisher), 1 = Niedrig, 2 = Sehr niedrig (Sport).
 * - HLS: Ziel-Abstand zur Live-Kante + leichtes Schneller/Langsamer-Abspielen durch ExoPlayer.
 * - TS-Streams: kleinerer Puffer; liegt zu viel im Puffer, wird unhörbar leicht schneller
 *   abgespielt (bei großem Rückstand etwas zügiger), bis der Abstand wieder stimmt.
 * Kleinere Verzögerung bedeutet weniger Reserve: bei schwacher Leitung eher Ruckler.
 */
object FiptvLiveLatency {
    private const val PREFS = "fiptv_live_latency"
    private var appContext: Context? = null
    private val labels = listOf("Normal (stabil)", "Niedrig", "Sehr niedrig (Sport)")

    var mode by mutableIntStateOf(0)
        private set

    fun init(context: Context) {
        appContext = context.applicationContext
        mode = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("mode", 0).coerceIn(0, 2)
    }

    fun label(): String = labels[mode]

    fun cycle() {
        mode = (mode + 1) % labels.size
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.putInt("mode", mode)?.apply()
    }

    /** Puffergrößen (min, max, Start, Start nach Ruckler) für den gewählten Modus. */
    fun buffers(minMs: Int, maxMs: Int, startMs: Int, rebufferMs: Int): IntArray = when (mode) {
        1 -> intArrayOf(6_000, 15_000, 1_000, 2_000)
        2 -> intArrayOf(3_000, 8_000, 500, 1_500)
        else -> intArrayOf(minMs, maxMs, startMs, rebufferMs)
    }

    /** Live-Einstellung für HLS-Sender. */
    fun liveConfiguration(): MediaItem.LiveConfiguration = when (mode) {
        1 -> MediaItem.LiveConfiguration.Builder().setTargetOffsetMs(10_000).setMinOffsetMs(6_000)
            .setMinPlaybackSpeed(0.97f).setMaxPlaybackSpeed(1.03f).build()
        2 -> MediaItem.LiveConfiguration.Builder().setTargetOffsetMs(5_000).setMinOffsetMs(3_000)
            .setMinPlaybackSpeed(0.96f).setMaxPlaybackSpeed(1.05f).build()
        // Normal: Zeiten aus der Sender-Playlist, keine Tempoänderung (wie bisher)
        else -> MediaItem.LiveConfiguration.Builder().setMinPlaybackSpeed(1.0f).setMaxPlaybackSpeed(1.0f).build()
    }

    /**
     * Läuft solange der Player lebt: hält bei TS-Streams den Pufferabstand klein.
     * [allowed] = false bei Sendung-verpasst, Live-Pause usw.
     */
    suspend fun watch(player: ExoPlayer, allowed: () -> Boolean) {
        var speed = 1.0f
        while (coroutineContext.isActive) {
            delay(1_000)
            val m = mode
            val applies = m > 0 && allowed() && player.isPlaying && !player.isCurrentMediaItemLive &&
                player.currentMediaItem?.mediaId != "fiptv-timeshift"
            val wanted = if (!applies) 1.0f else {
                val ahead = player.bufferedPosition - player.currentPosition
                val target = if (m == 2) 2_500L else 5_000L
                when {
                    // weit hinten (z. B. nach längerem Ruckler): zügig aufholen
                    ahead > target + 10_000 -> 1.10f
                    ahead > target + 1_500 -> if (m == 2) 1.06f else 1.04f
                    ahead <= target -> 1.0f
                    else -> speed // dazwischen: so weiter wie bisher
                }
            }
            if (wanted != speed) {
                speed = wanted
                player.playbackParameters = if (wanted == 1.0f) PlaybackParameters.DEFAULT else PlaybackParameters(wanted)
            }
        }
    }
}
