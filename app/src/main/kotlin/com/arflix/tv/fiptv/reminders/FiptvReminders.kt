package com.arflix.tv.fiptv.reminders

import android.content.Context
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * fIPTV: „Sendung merken“ – im TV-Programm eine künftige Sendung anklicken, kurz vor Beginn
 * erscheint ein Hinweis mit „Umschalten“. Gespeichert pro Gerät (SharedPreferences).
 */
data class FiptvReminder(
    val channelId: String,
    val channelName: String,
    val title: String,
    val startUtcMillis: Long,
    val endUtcMillis: Long,
) {
    val key: String get() = keyOf(title, startUtcMillis)

    companion object {
        fun keyOf(title: String, startUtcMillis: Long) = "$startUtcMillis|${title.trim().lowercase()}"
    }
}

object FiptvReminders {
    private const val PREFS = "fiptv_reminders"
    private const val KEY = "list"
    /** So viele Minuten vor Beginn kommt der Hinweis. */
    const val LEAD_MINUTES = 2L

    private val _items = MutableStateFlow<List<FiptvReminder>>(emptyList())
    val items: StateFlow<List<FiptvReminder>> = _items
    private val _keys = MutableStateFlow<Set<String>>(emptySet())
    val keys: StateFlow<Set<String>> = _keys

    /** Umschalt-Wünsche an einen schon offenen Live-TV-Bildschirm. */
    private val _tune = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val tuneRequests: SharedFlow<String> = _tune

    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return
        val list = runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                FiptvReminder(o.getString("c"), o.optString("n"), o.getString("t"), o.getLong("s"), o.optLong("e"))
            }
        }.getOrDefault(emptyList())
        publish(list.filter { it.endUtcMillis <= 0 || it.endUtcMillis > System.currentTimeMillis() })
    }

    private fun publish(list: List<FiptvReminder>) {
        val sorted = list.sortedBy { it.startUtcMillis }
        _items.value = sorted
        _keys.value = sorted.map { it.key }.toSet()
    }

    private fun save(context: Context) {
        val arr = JSONArray()
        _items.value.forEach {
            arr.put(JSONObject().put("c", it.channelId).put("n", it.channelName).put("t", it.title).put("s", it.startUtcMillis).put("e", it.endUtcMillis))
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply()
    }

    fun isSet(title: String, startUtcMillis: Long) = _keys.value.contains(FiptvReminder.keyOf(title, startUtcMillis))

    /** @return true = jetzt gemerkt, false = entfernt */
    fun toggle(context: Context, reminder: FiptvReminder): Boolean {
        load(context)
        val exists = _items.value.any { it.key == reminder.key }
        publish(if (exists) _items.value.filterNot { it.key == reminder.key } else _items.value + reminder)
        save(context)
        return !exists
    }

    fun remove(context: Context, reminder: FiptvReminder) {
        publish(_items.value.filterNot { it.key == reminder.key })
        save(context)
    }

    /** Erinnerungen, die jetzt fällig sind (ab LEAD_MINUTES vor Beginn bis 5 Min. nach Beginn). */
    fun due(now: Long): List<FiptvReminder> = _items.value.filter {
        now >= it.startUtcMillis - LEAD_MINUTES * 60_000L && now < it.startUtcMillis + 5 * 60_000L
    }

    fun dropExpired(context: Context, now: Long) {
        val keep = _items.value.filter { now < it.startUtcMillis + 5 * 60_000L }
        if (keep.size != _items.value.size) { publish(keep); save(context) }
    }

    fun requestTune(channelId: String) { _tune.tryEmit(channelId) }
}
