package com.arflix.tv.fiptv

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.arflix.tv.R

/**
 * fIPTV: Auswahl an Hintergrundfarben, die jedes Profil in den Einstellungen
 * (Darstellung → Hintergrundfarbe) für sich festlegen kann. Alle Farben sind
 * dunkel genug, dass weiße Schrift und Poster gut lesbar bleiben.
 */
object FiptvBackgrounds {
    /** Name des profilbezogenen Eintrags (gespeichert als „profile_<id>_fiptv_background“). */
    const val PREF_NAME = "fiptv_background"

    data class Option(val id: String, val argb: Long, @StringRes val label: Int) {
        val color: Color get() = Color(argb)
    }

    val options = listOf(
        Option("default", 0xFF08090A, R.string.fiptv_bg_default),
        Option("midnight", 0xFF0E1A33, R.string.fiptv_bg_midnight),
        Option("petrol", 0xFF08262C, R.string.fiptv_bg_petrol),
        Option("forest", 0xFF0E2218, R.string.fiptv_bg_forest),
        Option("violet", 0xFF1D1233, R.string.fiptv_bg_violet),
        Option("bordeaux", 0xFF2A0E17, R.string.fiptv_bg_bordeaux),
        Option("brown", 0xFF24180F, R.string.fiptv_bg_brown),
        Option("graphite", 0xFF202327, R.string.fiptv_bg_graphite),
    )

    fun byId(id: String?): Option = options.firstOrNull { it.id == id } ?: options.first()

    fun next(id: String?): Option {
        val index = options.indexOfFirst { it.id == id }.coerceAtLeast(0)
        return options[(index + 1) % options.size]
    }
}
