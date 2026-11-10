package com.arflix.tv.ui.screens.tv.live

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * fIPTV: Lieblingssender bekommen eigene Programmplätze 1, 2, 3 … in der Reihenfolge der
 * Favoritenliste (wie beim normalen Fernseher). Die Zifferntasten springen zuerst auf diese
 * Plätze; höhere Nummern (oder ganz ohne Favoriten) gelten wie bisher als Anbieter-Nummer.
 */
internal val LocalFiptvFavoriteNumbers = staticCompositionLocalOf<Map<String, Int>> { emptyMap() }

internal object FiptvFavoriteNumbers {
    fun numbers(favoriteOrderIds: List<String>): Map<String, Int> =
        favoriteOrderIds.withIndex().associate { (index, id) -> id to index + 1 }

    private fun favorites(favoriteOrderIds: List<String>, all: List<EnrichedChannel>): List<EnrichedChannel> {
        if (favoriteOrderIds.isEmpty()) return emptyList()
        val byId = all.associateBy { it.id }
        return favoriteOrderIds.mapNotNull { byId[it] }
    }

    /** Sofort umschalten, wenn keine längere Nummer mehr passen kann. */
    fun exact(query: String, favoriteOrderIds: List<String>, all: List<EnrichedChannel>): EnrichedChannel? {
        val n = query.toIntOrNull() ?: return null
        val favs = favorites(favoriteOrderIds, all)
        if (favs.isNotEmpty() && n in 1..favs.size) {
            return if (n * 10 > favs.size) favs[n - 1] else null
        }
        // Anbieter-Nummer (wie bisher), nur wenn sie nicht mehr Anfang eines Favoritenplatzes ist
        return all.firstOrNull { it.number.toString() == query }
            ?.takeIf { favs.isEmpty() || n > favs.size }
    }

    /** Nach der Wartezeit: bester Treffer für die eingegebene Nummer. */
    fun settle(query: String, favoriteOrderIds: List<String>, all: List<EnrichedChannel>): EnrichedChannel? {
        val n = query.toIntOrNull() ?: return null
        val favs = favorites(favoriteOrderIds, all)
        if (favs.isNotEmpty() && n in 1..favs.size) return favs[n - 1]
        all.firstOrNull { it.number.toString() == query }?.let { return it }
        return all.filter { it.number.toString().startsWith(query) }.take(2).singleOrNull()
    }

    fun previewName(query: String, favoriteOrderIds: List<String>, all: List<EnrichedChannel>): String? {
        val n = query.toIntOrNull() ?: return null
        val favs = favorites(favoriteOrderIds, all)
        if (favs.isNotEmpty() && n in 1..favs.size) return favs[n - 1].name
        return all.firstOrNull { it.number.toString() == query }?.name
    }

    fun matchCount(query: String, favoriteOrderIds: List<String>, all: List<EnrichedChannel>): Int {
        if (query.isBlank()) return 0
        val favs = favorites(favoriteOrderIds, all)
        val favMatches = favs.indices.count { (it + 1).toString().startsWith(query) }
        return favMatches + all.count { it.number.toString().startsWith(query) && (favs.isEmpty() || (query.toIntOrNull() ?: 0) > favs.size) }
    }
}

/** fIPTV: Schlüssel gemerkter Sendungen (Titel + Beginn) für das 🔔 im TV-Programm. */
internal val LocalFiptvReminderKeys = staticCompositionLocalOf<Set<String>> { emptySet() }
