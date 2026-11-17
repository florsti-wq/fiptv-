package com.arflix.tv.fiptv

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Rational
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.exoplayer.ExoPlayer

/**
 * fIPTV: Bild-in-Bild auf dem Handy – wird die App verlassen (Home-Taste, Wischgeste),
 * während ein Video im Vollbild läuft, spielt es in einem kleinen Fenster weiter.
 */
object FiptvPip {
    private const val PREFS = "fiptv_pip"
    private var appContext: Context? = null

    var enabled by mutableStateOf(true)
        private set

    /** Der Player, der gerade im Vollbild läuft (Live TV oder Film/Serie). */
    @Volatile var activePlayer: ExoPlayer? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        enabled = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("enabled", true)
    }

    fun toggle() {
        enabled = !enabled
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.putBoolean("enabled", enabled)?.apply()
    }

    /** Aus MainActivity.onUserLeaveHint. */
    fun onUserLeaveHint(activity: Activity) {
        if (!enabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (!activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) return
        // nur auf Geräten mit Touchscreen (auf dem Fernseher ergibt Bild-in-Bild keinen Sinn)
        if (!activity.packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)) return
        val player = activePlayer ?: return
        if (!player.isPlaying) return
        if (activity.isInPictureInPictureMode) return
        val size = player.videoSize
        val ratio = if (size.width > 0 && size.height > 0) {
            Rational(size.width, size.height).let { r ->
                // Android erlaubt nur Seitenverhältnisse zwischen 1:2,39 und 2,39:1
                val v = r.toFloat()
                if (v < 0.42f || v > 2.39f) Rational(16, 9) else r
            }
        } else Rational(16, 9)
        runCatching {
            activity.enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(ratio).build())
        }
    }
}
