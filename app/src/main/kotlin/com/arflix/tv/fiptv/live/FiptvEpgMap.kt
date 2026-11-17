package com.arflix.tv.fiptv.live

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.arflix.tv.data.model.IptvNowNext
import org.json.JSONObject

/**
 * fIPTV: Programmdaten selbst zuordnen. Ein Sender ohne Programmdaten (z. B. „RTL UHD“)
 * übernimmt die Programmdaten eines anderen Senders (z. B. „RTL HD“). Gespeichert als
 * Sender-ID → Quell-Sender-ID.
 */
object FiptvEpgMap {
    private const val PREFS = "fiptv_epg_map"
    private var appContext: Context? = null
    private val map = HashMap<String, String>()

    /** Ändert sich bei jeder Zuordnung (für Compose). */
    var version by mutableIntStateOf(0)
        private set

    fun init(context: Context) {
        appContext = context.applicationContext
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("map", null) ?: return
        runCatching {
            val o = JSONObject(raw)
            o.keys().forEach { k -> map[k] = o.getString(k) }
        }
        version++
    }

    fun sourceFor(channelId: String): String? = map[channelId]

    fun set(channelId: String, sourceChannelId: String?) {
        if (sourceChannelId == null || sourceChannelId == channelId) map.remove(channelId) else map[channelId] = sourceChannelId
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()
            ?.putString("map", JSONObject(map as Map<*, *>).toString())?.apply()
        version++
    }

    fun isEmpty(): Boolean = map.isEmpty()

    /** Programmdaten der Quell-Sender auf die zugeordneten Sender übertragen. */
    fun apply(nowNext: Map<String, IptvNowNext>): Map<String, IptvNowNext> {
        if (map.isEmpty()) return nowNext
        var out: HashMap<String, IptvNowNext>? = null
        for ((target, source) in map) {
            val guide = nowNext[source] ?: continue
            if (out == null) out = HashMap(nowNext)
            out[target] = guide
        }
        return out ?: nowNext
    }

    fun sourceIds(): Set<String> = map.values.toSet()
}
