package com.arflix.tv.util

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * fIPTV: kleines Protokoll der letzten Ereignisse (Anmeldung, Sync, Fehler) auf dem Gerät.
 * Wird nur lokal gespeichert und in der Profilauswahl angezeigt – nichts wird verschickt.
 * So lassen sich Probleme auf Geräten ohne Entwicklerwerkzeuge (Handy, Fire TV) erkennen.
 */
object FiptvDiagnostics {
    private const val PREFS = "fiptv_diag"
    private const val KEY = "log"
    private const val MAX_LINES = 80
    private val lock = Any()
    private val time = SimpleDateFormat("HH:mm:ss", Locale.GERMANY)

    private fun context(): Context? = runCatching { com.arflix.tv.ArflixApplication.instance }.getOrNull()

    fun add(line: String) {
        val ctx = context() ?: return
        runCatching {
            synchronized(lock) {
                val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val old = prefs.getString(KEY, "").orEmpty().lines().filter { it.isNotBlank() }
                val next = (old + "${time.format(Date())} $line").takeLast(MAX_LINES)
                prefs.edit().putString(KEY, next.joinToString("\n")).apply()
            }
        }
    }

    fun read(context: Context, lines: Int = 30): String =
        runCatching {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "").orEmpty()
                .lines().filter { it.isNotBlank() }.takeLast(lines).joinToString("\n")
        }.getOrDefault("")
}
