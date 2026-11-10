package com.arflix.tv.fiptv.kids

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * fIPTV: Kinderprofile – tägliches Zeitlimit und Kinder-Sender.
 *
 * Das Limit (Minuten pro Tag, 0 = keins) liegt je Profil in den Einstellungen
 * („profile_<id>_fiptv_kids_limit“) und wird über den eigenen Server abgeglichen.
 * Die verbrauchte Zeit zählt jedes Gerät für sich (SharedPreferences, je Tag).
 */
object FiptvKids {
    const val LIMIT_PREF = "fiptv_kids_limit"
    val LIMIT_OPTIONS = listOf(0, 30, 45, 60, 90, 120, 180)
    const val BONUS_MINUTES = 30

    private const val USAGE_PREFS = "fiptv_kids_usage"

    private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    private fun usedKey(profileId: String) = "u_${profileId}_${today()}"
    private fun bonusKey(profileId: String) = "b_${profileId}_${today()}"

    fun usedSeconds(context: Context, profileId: String): Int =
        context.getSharedPreferences(USAGE_PREFS, Context.MODE_PRIVATE).getInt(usedKey(profileId), 0)

    fun bonusSeconds(context: Context, profileId: String): Int =
        context.getSharedPreferences(USAGE_PREFS, Context.MODE_PRIVATE).getInt(bonusKey(profileId), 0)

    @Synchronized
    fun addSeconds(context: Context, profileId: String, seconds: Int) {
        val prefs = context.getSharedPreferences(USAGE_PREFS, Context.MODE_PRIVATE)
        val key = usedKey(profileId)
        val editor = prefs.edit().putInt(key, prefs.getInt(key, 0) + seconds)
        // alte Tage aufräumen
        prefs.all.keys.filter { (it.startsWith("u_") || it.startsWith("b_")) && !it.endsWith(today()) }.forEach { editor.remove(it) }
        editor.apply()
    }

    fun grantBonus(context: Context, profileId: String, minutes: Int = BONUS_MINUTES) {
        val prefs = context.getSharedPreferences(USAGE_PREFS, Context.MODE_PRIVATE)
        val key = bonusKey(profileId)
        prefs.edit().putInt(key, prefs.getInt(key, 0) + minutes * 60).apply()
    }

    /** verbleibende Sekunden heute; null = kein Limit */
    fun remainingSeconds(context: Context, profileId: String, limitMinutes: Int): Int? {
        if (limitMinutes <= 0) return null
        return limitMinutes * 60 + bonusSeconds(context, profileId) - usedSeconds(context, profileId)
    }

    fun limitLabel(minutes: Int): String = when {
        minutes <= 0 -> "Kein Limit"
        minutes < 60 -> "$minutes Min. pro Tag"
        minutes % 60 == 0 -> "${minutes / 60} Std. pro Tag"
        else -> "${minutes / 60} Std. ${minutes % 60} Min. pro Tag"
    }

    fun nextLimit(current: Int): Int {
        val i = LIMIT_OPTIONS.indexOf(current)
        return LIMIT_OPTIONS[(if (i < 0) 0 else i + 1) % LIMIT_OPTIONS.size]
    }

    // ---------- Kinder-Sender (Live TV im Kinderprofil) ----------

    private val kidsName = Regex(
        "(?i)(\\bki\\.?ka\\b|super ?rtl|toggo|disney|nick( ?jr| jr\\.?|elodeon|toons)?\\b|cartoon|cartoonito|boomerang|baby ?tv|junior|duck ?tv|kividoo|fix ?& ?foxi|gulli|piwi|minimax|\\bkids?\\b|kinder)"
    )
    private val adult = Regex("(?i)(xxx|adult|erotic|erotik|18\\+|\\bsex|playboy|hustler|blue ?hustler|private)")

    fun isKidsChannel(name: String, group: String?): Boolean {
        if (adult.containsMatchIn(name) || (group != null && adult.containsMatchIn(group))) return false
        return kidsName.containsMatchIn(name) || (group != null && Regex("(?i)\\b(kids|kinder|children|enfants)\\b").containsMatchIn(group))
    }
}
