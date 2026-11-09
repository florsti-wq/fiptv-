package com.arflix.tv.navigation

/**
 * fIPTV: Zugang zu den zusätzlichen Bereichen (Mediathek, später YouTube) aus allen Bildschirmen,
 * ohne jedem Bildschirm einen eigenen Navigations-Rückruf hinzuzufügen.
 * AppNavigation setzt [navigator] beim Aufbau.
 */
object FiptvNav {
    const val MEDIATHEK = "fiptv_mediathek"
    const val VOD = "fiptv_vod"
    const val YOUTUBE = "fiptv_youtube"
    const val SPORT = "fiptv_sport"

    /** Sport → „Live im TV“: Der TV-Bereich öffnet beim nächsten Aufbau direkt den Sports-Hub. */
    @Volatile
    var openSportsHub = false

    fun consumeSportsHub(): Boolean = openSportsHub.also { openSportsHub = false }

    @Volatile
    var navigator: ((String) -> Unit)? = null

    fun open(route: String) {
        navigator?.invoke(route)
    }
}
