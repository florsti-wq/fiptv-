package com.arflix.tv.fiptv

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * fIPTV: Animationen und Töne der Oberfläche.
 *
 * Die Klänge werden beim Start selbst erzeugt (kurze Sinus-Töne als WAV im Cache) und über
 * SoundPool abgespielt, damit sie ohne Verzögerung kommen. Beides lässt sich in den
 * Einstellungen abschalten.
 */
object FiptvEffects {
    private const val PREFS = "fiptv_effects"

    var animations by mutableStateOf(true)
        private set
    var sounds by mutableStateOf(true)
        private set

    /** True, solange Video im Vollbild läuft: dann kein Navigationsklick. */
    @Volatile var playbackActive = false

    private var pool: SoundPool? = null
    private val ids = IntArray(4)
    private var lastNavAt = 0L
    private var appContext: Context? = null

    const val START = 0
    const val NAV = 1
    const val OK = 2
    const val STAR = 3

    fun init(context: Context) {
        val app = context.applicationContext
        appContext = app
        val p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        animations = p.getBoolean("animations", true)
        sounds = p.getBoolean("sounds", true)
        if (pool == null) Thread { runCatching { loadSounds(app) } }.start()
    }

    fun setAnimations(context: Context, on: Boolean) {
        animations = on
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("animations", on).apply()
    }

    fun setSounds(context: Context, on: Boolean) {
        sounds = on
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("sounds", on).apply()
        if (on) play(OK, force = true)
    }

    fun toggleAnimations() { appContext?.let { setAnimations(it, !animations) } }
    fun toggleSounds() { appContext?.let { setSounds(it, !sounds) } }

    fun play(which: Int, force: Boolean = false) {
        if (!sounds && !force) return
        val sp = pool ?: return
        val id = ids.getOrNull(which) ?: return
        if (id == 0) return
        val vol = when (which) { NAV -> 0.35f; START -> 0.6f; else -> 0.5f }
        runCatching { sp.play(id, vol, vol, 1, 0, 1f) }
    }

    /** Leiser Klick bei Pfeiltasten, gedrosselt, nicht während der Wiedergabe. */
    fun navClick() {
        if (!sounds || playbackActive) return
        val now = System.currentTimeMillis()
        if (now - lastNavAt < 70) return
        lastNavAt = now
        play(NAV)
    }

    fun okClick() {
        if (!sounds || playbackActive) return
        play(OK)
    }

    private fun loadSounds(context: Context) {
        val sp = SoundPool.Builder()
            .setMaxStreams(3)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            ).build()
        val dir = File(context.cacheDir, "fiptv_sounds").apply { mkdirs() }
        // Startklang: aufsteigender Dreiklang (C-E-G-C)
        ids[START] = sp.load(wav(dir, "start_v1", listOf(
            Note(523.25, 0.00, 0.55), Note(659.25, 0.11, 0.55),
            Note(783.99, 0.22, 0.60), Note(1046.5, 0.33, 0.75),
        ), 1.15, 0.22), 1)
        // Navigation: sehr kurzer, weicher Tick
        ids[NAV] = sp.load(wav(dir, "nav_v1", listOf(Note(1400.0, 0.0, 0.05, triangle = true)), 0.07, 0.25), 1)
        // OK: zwei kurze Töne (A-E)
        ids[OK] = sp.load(wav(dir, "ok_v1", listOf(Note(880.0, 0.0, 0.12), Note(1318.5, 0.07, 0.16)), 0.26, 0.25), 1)
        // Favorit: heller Doppelton (H-E)
        ids[STAR] = sp.load(wav(dir, "star_v1", listOf(Note(987.77, 0.0, 0.10), Note(1318.5, 0.06, 0.20)), 0.28, 0.25), 1)
        pool = sp
    }

    private class Note(val freq: Double, val start: Double, val dur: Double, val triangle: Boolean = false)

    private fun wav(dir: File, name: String, notes: List<Note>, total: Double, peak: Double): String {
        val file = File(dir, "$name.wav")
        if (file.exists() && file.length() > 44) return file.absolutePath
        val rate = 44_100
        val n = (total * rate).toInt()
        val buf = DoubleArray(n)
        for (note in notes) {
            val s0 = (note.start * rate).toInt()
            val len = (note.dur * rate).toInt()
            for (i in 0 until len) {
                val idx = s0 + i
                if (idx >= n) break
                val t = i.toDouble() / rate
                val attack = (t / 0.008).coerceAtMost(1.0)
                val env = attack * exp(-t * 5.0 / note.dur)
                val phase = 2 * PI * note.freq * t
                val wave = if (note.triangle) (2 / PI) * kotlin.math.asin(sin(phase)) else sin(phase)
                buf[idx] += wave * env
            }
        }
        val max = buf.maxOf { kotlin.math.abs(it) }.coerceAtLeast(1e-6)
        val data = ByteBuffer.allocate(n * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (v in buf) data.putShort((v / max * peak * Short.MAX_VALUE).toInt().toShort())
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + n * 2); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1)
            putInt(rate); putInt(rate * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(n * 2)
        }
        val tmp = File(dir, "$name.tmp")
        FileOutputStream(tmp).use { it.write(header.array()); it.write(data.array()) }
        tmp.renameTo(file)
        return file.absolutePath
    }
}
