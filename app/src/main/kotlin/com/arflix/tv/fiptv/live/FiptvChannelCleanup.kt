package com.arflix.tv.fiptv.live

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf
import com.arflix.tv.ui.screens.tv.live.Quality

/**
 * fIPTV: Senderliste aufräumen (abschaltbar unter Einstellungen → Darstellung).
 * - Anzeigename ohne Länderkürzel und Zusätze („DE: ZDF HD“ → „ZDF“), Qualität steht im Schild.
 * - Trenner-Einträge („##### DEUTSCHLAND #####“) ausblenden.
 * - Gleiche Sender (HD/FHD/4K) zu einem Eintrag zusammenfassen (Auswahl über „Qualitätsvarianten“).
 * - Gemessene Auflösung je Sender merken: das Schild zeigt den echten Wert statt des Etiketts.
 */
object FiptvChannelCleanup {
    private var prefs: SharedPreferences? = null
    private val measured = HashMap<String, Quality>()
    val enabledState = mutableStateOf(true)
    val enabled: Boolean get() = enabledState.value

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("fiptv_channels", Context.MODE_PRIVATE)
        prefs = p
        enabledState.value = p.getBoolean("cleanup", true)
        p.all.forEach { (k, v) ->
            if (k.startsWith("q:") && v is String) runCatching { Quality.valueOf(v) }.getOrNull()?.let { measured[k.removePrefix("q:")] = it }
        }
    }

    fun toggle() {
        enabledState.value = !enabledState.value
        prefs?.edit()?.putBoolean("cleanup", enabledState.value)?.apply()
    }

    // ---- gemessene Auflösung ----
    fun measuredQuality(channelId: String): Quality? = synchronized(measured) { measured[channelId] }

    fun rememberQuality(channelId: String, quality: Quality) {
        if (quality == Quality.UNKNOWN) return
        val changed = synchronized(measured) { measured.put(channelId, quality) != quality }
        if (changed) prefs?.edit()?.putString("q:$channelId", quality.name)?.apply()
    }

    /** true, wenn der Name mehr verspricht als gemessen wurde (z. B. „4K“, gemessen 1080p). */
    fun overLabeled(label: Quality, real: Quality?): Boolean =
        real != null && real != Quality.UNKNOWN && label != Quality.UNKNOWN && rank(real) < rank(label)

    fun rank(q: Quality): Int = when (q) { Quality.K4 -> 4; Quality.FHD -> 3; Quality.HD -> 2; Quality.SD -> 1; Quality.UNKNOWN -> 0 }

    // ---- Namen ----
    private val PREFIX = Regex("""^\s*(?:[\[(|]\s*)?[A-Z]{2,3}(?:[\s-]?[A-Z]{2})?\s*(?:[\])|:]|\s[|•\-–►➥▶]|➥|►)\s*""")
    private val PIPE_PREFIX = Regex("""^\s*\|\s*[A-Z]{2,4}\s*\|\s*""")
    private val DECOR = Regex("""[ᴴᴰᵁᴴᴰ⁴ᴷ]+|[★☆●◉◆◇■□▶►➥✦✪]+""")
    private val TAGS = Regex("""\s*[\[(](?:backup|bkp|alt|geo[- ]?blocked|not 24/7|vip|test|multi|multi[- ]?audio|raw|h\.?265|hevc|50 ?fps|60 ?fps)[\])]\s*""", RegexOption.IGNORE_CASE)
    private val QUALITY = Regex("""(?<![A-Za-z0-9])(?:UHD|4K|2160p?|FHD|1080[pi]?|HD\+?|720p?|SD|576[pi]?|HEVC|H\.?265|RAW|50fps|60fps)(?![A-Za-z0-9])""", RegexOption.IGNORE_CASE)
    private val SPACES = Regex("""\s{2,}""")
    private val SEPARATOR = Regex("""^\s*[#=*\-_~•|]{3,}.*|.*[#=*\-_~]{4,}\s*$""")

    fun displayName(name: String): String {
        if (!enabled) return name
        var n = name.replace(PIPE_PREFIX, "").replace(PREFIX, "")
        n = n.replace(TAGS, " ").replace(DECOR, " ").replace(QUALITY, " ")
        n = n.replace(SPACES, " ").trim().trim('-', '|', ':', '•', '–').trim()
        return n.ifBlank { name.trim() }
    }

    fun isSeparator(name: String): Boolean = enabled && SEPARATOR.matches(name.trim())
}
