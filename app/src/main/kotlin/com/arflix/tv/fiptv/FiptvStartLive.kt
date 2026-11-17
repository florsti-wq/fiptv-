package com.arflix.tv.fiptv

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** fIPTV: auf Wunsch nach der Profilauswahl direkt mit dem zuletzt gesehenen Sender starten. */
object FiptvStartLive {
    private const val PREFS = "fiptv_start_live"
    private var appContext: Context? = null

    var enabled by mutableStateOf(false)
        private set
    var lastChannelId: String? = null
        private set

    fun init(context: Context) {
        appContext = context.applicationContext
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        enabled = p.getBoolean("enabled", false)
        lastChannelId = p.getString("last", null)
    }

    fun toggle() {
        enabled = !enabled
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.putBoolean("enabled", enabled)?.apply()
    }

    fun rememberChannel(id: String) {
        if (id == lastChannelId) return
        lastChannelId = id
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.putString("last", id)?.apply()
    }

    /** Nur beim ersten Profilwechsel nach dem App-Start. */
    private var used = false
    fun channelForStartup(): String? {
        if (used || !enabled) return null
        used = true
        return lastChannelId
    }
}
