package com.arflix.tv.fiptv.live

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

/**
 * fIPTV: eigene Sendergruppen (z. B. „Kinder“, „Nachrichten“) mit Sendern aus allen Anbietern.
 * Kategorie-ID in Live TV: "fiptv:<id>". Reihenfolge der Sender = Reihenfolge des Hinzufügens,
 * änderbar über „nach oben/unten“.
 */
object FiptvCustomGroups {
    const val PREFIX = "fiptv:"
    private const val PREFS = "fiptv_custom_groups"

    data class Group(val id: String, val name: String, val channelIds: List<String>)

    private var appContext: Context? = null
    private var groups: List<Group> = emptyList()

    var version by mutableIntStateOf(0)
        private set

    fun init(context: Context) {
        appContext = context.applicationContext
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("groups", null)
        groups = runCatching {
            val arr = JSONArray(raw ?: "[]")
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val ids = o.optJSONArray("channels") ?: JSONArray()
                Group(o.getString("id"), o.getString("name"), (0 until ids.length()).map { ids.getString(it) })
            }
        }.getOrDefault(emptyList())
        version++
    }

    private fun save() {
        val arr = JSONArray()
        groups.forEach { g ->
            arr.put(JSONObject().put("id", g.id).put("name", g.name).put("channels", JSONArray(g.channelIds)))
        }
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.putString("groups", arr.toString())?.apply()
        version++
    }

    fun all(): List<Group> = groups
    fun byCategoryId(categoryId: String): Group? =
        if (categoryId.startsWith(PREFIX)) groups.firstOrNull { PREFIX + it.id == categoryId } else null

    fun create(name: String, firstChannelId: String? = null): Group {
        val g = Group(System.currentTimeMillis().toString(36), name.trim().ifBlank { "Meine Sender" }, listOfNotNull(firstChannelId))
        groups = groups + g
        save()
        return g
    }

    fun rename(id: String, name: String) {
        groups = groups.map { if (it.id == id) it.copy(name = name.trim().ifBlank { it.name }) else it }
        save()
    }

    fun delete(id: String) {
        groups = groups.filterNot { it.id == id }
        save()
    }

    fun contains(id: String, channelId: String) = groups.firstOrNull { it.id == id }?.channelIds?.contains(channelId) == true

    fun toggle(id: String, channelId: String) {
        groups = groups.map { g ->
            if (g.id != id) g
            else if (channelId in g.channelIds) g.copy(channelIds = g.channelIds - channelId)
            else g.copy(channelIds = g.channelIds + channelId)
        }
        save()
    }

    fun move(id: String, channelId: String, delta: Int) {
        groups = groups.map { g ->
            if (g.id != id) return@map g
            val list = g.channelIds.toMutableList()
            val i = list.indexOf(channelId)
            val j = i + delta
            if (i < 0 || j !in list.indices) return@map g
            list.removeAt(i); list.add(j, channelId)
            g.copy(channelIds = list)
        }
        save()
    }

    /** Gruppen nach oben/unten verschieben (Reihenfolge in der Seitenleiste). */
    fun moveGroup(id: String, delta: Int) {
        val list = groups.toMutableList()
        val i = list.indexOfFirst { it.id == id }
        val j = i + delta
        if (i < 0 || j !in list.indices) return
        val g = list.removeAt(i); list.add(j, g)
        groups = list
        save()
    }
}
