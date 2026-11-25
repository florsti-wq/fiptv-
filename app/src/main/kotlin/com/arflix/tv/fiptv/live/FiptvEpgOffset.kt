package com.arflix.tv.fiptv.live

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.arflix.tv.data.model.IptvNowNext
import com.arflix.tv.ui.screens.tv.live.atTime
import org.json.JSONObject

/**
 * fIPTV: Zeitversatz der Programmdaten (Idee aus OwnTV, eigene Umsetzung).
 * Liegt das Programm eines Anbieters immer z. B. eine Stunde daneben, verschiebt der Versatz
 * alle Sendungszeiten – für alle Sender (global) oder nur für einzelne Sender.
 * Ein Wert pro Sender ersetzt den globalen Wert.
 */
object FiptvEpgOffset {
    private const val PREFS = "fiptv_epg_offset"
    private var appContext: Context? = null
    private val perChannel = HashMap<String, Int>()

    /** Auswahl in Minuten (Reihenfolge für das Durchschalten in den Einstellungen). */
    val CHOICES = listOf(0, 30, 60, 90, 120, 180, -180, -120, -90, -60, -30)

    var globalMinutes by mutableIntStateOf(0)
        private set

    /** Ändert sich bei jeder Änderung (für Compose). */
    var version by mutableIntStateOf(0)
        private set

    fun init(context: Context) {
        appContext = context.applicationContext
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        globalMinutes = p.getInt("global", 0)
        runCatching {
            val o = JSONObject(p.getString("channels", null) ?: "{}")
            o.keys().forEach { k -> perChannel[k] = o.getInt(k) }
        }
        version++
    }

    fun label(minutes: Int): String = when {
        minutes == 0 -> "Kein Versatz"
        minutes % 60 == 0 -> "%+d Std.".format(minutes / 60)
        else -> "%+d Min.".format(minutes)
    }

    fun cycleGlobal() {
        globalMinutes = CHOICES[(CHOICES.indexOf(globalMinutes).coerceAtLeast(0) + 1) % CHOICES.size]
        save()
    }

    fun forChannel(channelId: String): Int? = perChannel[channelId]

    /** null = globalen Wert verwenden */
    fun setChannel(channelId: String, minutes: Int?) {
        if (minutes == null) perChannel.remove(channelId) else perChannel[channelId] = minutes
        save()
    }

    private fun save() {
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()
            ?.putInt("global", globalMinutes)
            ?.putString("channels", JSONObject(perChannel as Map<*, *>).toString())
            ?.apply()
        version++
    }

    fun isEmpty(): Boolean = globalMinutes == 0 && perChannel.isEmpty()

    /** Alle Sendungszeiten verschieben und „jetzt/als Nächstes“ neu bestimmen. */
    fun apply(nowNext: Map<String, IptvNowNext>, now: Long = System.currentTimeMillis()): Map<String, IptvNowNext> {
        if (isEmpty()) return nowNext
        val out = HashMap<String, IptvNowNext>(nowNext.size)
        for ((id, guide) in nowNext) {
            val minutes = perChannel[id] ?: globalMinutes
            out[id] = if (minutes == 0) guide else shift(guide, minutes * 60_000L, now)
        }
        return out
    }

    private fun shift(g: IptvNowNext, delta: Long, now: Long): IptvNowNext {
        val shifted = (g.recent + listOfNotNull(g.now, g.next, g.later) + g.upcoming)
            .map { it.copy(startUtcMillis = it.startUtcMillis + delta, endUtcMillis = it.endUtcMillis + delta) }
        // gleiche Aufteilung in vorbei/jetzt/als Nächstes wie im übrigen Live TV
        return IptvNowNext(recent = shifted).atTime(now)
    }
}
