package com.arflix.tv.navigation

/**
 * fIPTV: Zugang zu den zusätzlichen Bereichen (Mediathek, später YouTube) aus allen Bildschirmen,
 * ohne jedem Bildschirm einen eigenen Navigations-Rückruf hinzuzufügen.
 * AppNavigation setzt [navigator] beim Aufbau.
 */
object FiptvNav {
    const val MEDIATHEK = "fiptv_mediathek"
    const val VOD = "fiptv_vod"

    @Volatile
    var navigator: ((String) -> Unit)? = null

    fun open(route: String) {
        navigator?.invoke(route)
    }
}
