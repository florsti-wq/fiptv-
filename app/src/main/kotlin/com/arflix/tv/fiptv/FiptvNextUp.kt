package com.arflix.tv.fiptv

import android.content.Context

/**
 * fIPTV: „Nächste Folge automatisch“ für Anbieter-Serien und Mediathek.
 * Beim Start einer Folge legen VOD/Mediathek die folgenden Folgen als Warteschlange ab;
 * der Player fragt am Ende nach der nächsten und startet sie nach einem kurzen Countdown.
 */
object FiptvNextUp {
    private var profileId: String = ""
    private var queue: List<FiptvResume.Entry> = emptyList()

    /** [current] = gerade gestartete Folge, [following] = alle danach in Abspielreihenfolge. */
    fun set(profileId: String, current: FiptvResume.Entry, following: List<FiptvResume.Entry>) {
        this.profileId = profileId
        queue = listOf(current) + following.filter { it.url != current.url }
    }

    fun clear() { queue = emptyList() }

    fun nextFor(url: String?): FiptvResume.Entry? {
        if (url.isNullOrBlank()) return null
        val i = queue.indexOfFirst { it.url == url }
        return if (i >= 0) queue.getOrNull(i + 1) else null
    }

    /** Für „Weiterschauen“ anmelden und Startposition (falls schon angefangen) liefern. */
    fun prepare(context: Context, entry: FiptvResume.Entry): Long? {
        FiptvResume.register(context, profileId, entry)
        return FiptvResume.startPosition(context, profileId, entry.url)
    }

    /** Vom Player gesetzt: startet die nächste Folge (ersetzt den aktuellen Player). */
    @Volatile
    var playNext: ((FiptvResume.Entry, Long?) -> Unit)? = null
}
