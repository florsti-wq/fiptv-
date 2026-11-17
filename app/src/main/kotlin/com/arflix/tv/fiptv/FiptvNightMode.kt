package com.arflix.tv.fiptv

import android.content.Context
import android.media.audiofx.DynamicsProcessing
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/**
 * fIPTV: Nachtmodus für den Ton – ein Kompressor gleicht laute und leise Stellen an
 * (Werbung leiser, Dialoge lauter), ein Limiter verhindert Spitzen.
 * Wirkt nicht, wenn der Ton unverändert an einen AV-Receiver durchgereicht wird.
 */
object FiptvNightMode {
    private const val PREFS = "fiptv_night_mode"
    private var appContext: Context? = null

    var enabled by mutableStateOf(false)
        private set

    fun init(context: Context) {
        appContext = context.applicationContext
        enabled = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("enabled", false)
    }

    fun toggle() {
        enabled = !enabled
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.putBoolean("enabled", enabled)?.apply()
    }

    class Handle internal constructor(private val player: ExoPlayer) : Player.Listener {
        private var effect: DynamicsProcessing? = null

        init {
            player.addListener(this)
            apply(player.audioSessionId)
        }

        override fun onAudioSessionIdChanged(audioSessionId: Int) = apply(audioSessionId)

        private fun apply(sessionId: Int) {
            runCatching { effect?.release() }
            effect = null
            if (!enabled || sessionId == C.AUDIO_SESSION_ID_UNSET || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
            effect = runCatching {
                val channels = 2
                val cfg = DynamicsProcessing.Config.Builder(
                    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                    channels,
                    false, 0,   // Pre-EQ
                    true, 1,    // Mehrband-Kompressor mit einem Band
                    false, 0,   // Post-EQ
                    true,       // Limiter
                ).build()
                // enabled, Grenzfrequenz, Attack ms, Release ms, Ratio, Schwelle dB, Knie dB,
                // Noise-Gate dB, Expander-Ratio, Vorverstärkung dB, Nachverstärkung dB
                val band = DynamicsProcessing.MbcBand(true, 20_000f, 8f, 250f, 4f, -30f, 8f, -90f, 1f, 0f, 9f)
                cfg.setMbcBandAllChannelsTo(0, band)
                // inUse, enabled, Gruppe, Attack ms, Release ms, Ratio, Schwelle dB, Nachverstärkung dB
                cfg.setLimiterAllChannelsTo(DynamicsProcessing.Limiter(true, true, 0, 1f, 60f, 10f, -3f, 0f))
                DynamicsProcessing(0, sessionId, cfg).apply { setEnabled(true) }
            }.getOrNull()
        }

        fun release() {
            player.removeListener(this)
            runCatching { effect?.release() }
            effect = null
        }
    }

    /** An einen Player hängen; mit [Handle.release] wieder lösen. */
    fun attach(player: ExoPlayer): Handle = Handle(player)
}
