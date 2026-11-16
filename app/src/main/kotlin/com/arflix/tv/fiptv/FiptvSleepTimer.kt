package com.arflix.tv.fiptv

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import java.lang.ref.WeakReference

/**
 * fIPTV: Schlaf-Timer. Nach Ablauf geht die App in den Hintergrund – laufende Wiedergabe stoppt dabei,
 * der Fire TV zeigt danach seinen Bildschirmschoner. Eine Minute vorher kommt ein Hinweis.
 */
object FiptvSleepTimer {
    private val handler = Handler(Looper.getMainLooper())
    private var activity: WeakReference<Activity>? = null
    private val choices = listOf(0, 30, 60, 90, 120)
    /** gewählte Dauer in Minuten (0 = aus) */
    val minutes = mutableStateOf(0)
    /** Ende als Zeitstempel (0 = aus) */
    val endsAt = mutableStateOf(0L)

    fun attach(a: Activity) { activity = WeakReference(a) }

    fun label(): String {
        val end = endsAt.value
        if (end <= 0L) return "Aus"
        val left = ((end - System.currentTimeMillis()) / 60_000L).coerceAtLeast(0) + 1
        return "${minutes.value} Min. (noch ca. $left Min.)"
    }

    fun cycle() {
        val next = choices[(choices.indexOf(minutes.value) + 1) % choices.size]
        set(next)
    }

    fun set(min: Int) {
        handler.removeCallbacksAndMessages(null)
        minutes.value = min
        if (min <= 0) { endsAt.value = 0L; return }
        val end = System.currentTimeMillis() + min * 60_000L
        endsAt.value = end
        handler.postDelayed({
            activity?.get()?.let { Toast.makeText(it, "Schlaf-Timer: Wiedergabe endet in 1 Minute", Toast.LENGTH_LONG).show() }
        }, (min - 1).coerceAtLeast(0) * 60_000L)
        handler.postDelayed({
            minutes.value = 0; endsAt.value = 0L
            activity?.get()?.moveTaskToBack(true)
        }, min * 60_000L)
    }
}
