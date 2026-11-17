package com.arflix.tv.fiptv

import androidx.compose.runtime.mutableStateMapOf
import com.arflix.tv.data.repository.IptvRepository

/**
 * fIPTV: Verbindungsanzeige – wie viele gleichzeitige Streams das Xtream-Abo gerade belegt
 * (`active_cons`) und wie viele es erlaubt (`max_connections`).
 */
object FiptvConnections {
    /** Playlist-ID → (aktiv, max) */
    val usage = mutableStateMapOf<String, Pair<Int, Int>>()
    @Volatile private var lastAt = 0L

    suspend fun refresh(repo: IptvRepository, minIntervalMs: Long = 15_000L) {
        val now = System.currentTimeMillis()
        if (now - lastAt < minIntervalMs) return
        lastAt = now
        val fresh = runCatching { repo.fiptvConnectionUsage() }.getOrNull() ?: return
        usage.clear()
        usage.putAll(fresh)
    }

    fun label(playlistId: String): String? = usage[playlistId]?.let { (a, m) -> "Verbindungen: $a von $m belegt" }

    /** Kurzform für die Bedienleiste, über alle Anbieter. */
    fun summary(): String? {
        if (usage.isEmpty()) return null
        return usage.values.joinToString("  ·  ") { (a, m) -> "$a/$m" }.let { "Verbindungen $it" }
    }
}
