package com.arflix.tv.fiptv

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as ACanvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan
import kotlin.random.Random

/**
 * fIPTV: animierter Weltraum-Hintergrund (viele Sterne, dezente Nebel, ferne Galaxien).
 * Wird komplett im Code gezeichnet; Nebel und Sternenstaub werden einmal als Bild vorbereitet,
 * pro Bild bewegen sich nur noch Ebenen und funkelnde Sterne (max. 30 Bilder/s).
 */
object FiptvSpace {
    private const val PREFS = "fiptv_effects"

    /** 0 = aus, 1 = Galaxie */
    var mode by mutableIntStateOf(1)
        private set

    /** 0..2 feste Farbstimmung, 3 = bei jedem Start zufällig */
    var palette by mutableIntStateOf(3)
        private set

    private val sessionRandom = Random.nextInt(3)
    val activePalette: Int get() = if (palette == 3) sessionRandom else palette

    val paletteNames = listOf("Magenta und Türkis", "Feuer und Eis", "Smaragd und Violett", "Bei jedem Start anders")

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        mode = p.getInt("space_mode", 1)
        palette = p.getInt("space_palette", 3)
    }

    fun cycleMode() {
        mode = if (mode == 0) 1 else 0
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.putInt("space_mode", mode)?.apply()
    }

    fun cyclePalette() {
        palette = (palette + 1) % 4
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.putInt("space_palette", palette)?.apply()
    }

    fun modeLabel(): String = if (mode == 0) "Aus" else "Galaxie"
    fun paletteLabel(): String = paletteNames[palette]

    // Farbtöne (Hue, Sättigung, Helligkeit) je Farbstimmung – bewusst gedämpft
    internal val palettes = listOf(
        listOf(floatArrayOf(295f, .40f, .45f), floatArrayOf(190f, .45f, .42f), floatArrayOf(255f, .35f, .40f)),
        listOf(floatArrayOf(20f, .45f, .45f), floatArrayOf(205f, .45f, .45f), floatArrayOf(35f, .40f, .45f)),
        listOf(floatArrayOf(155f, .35f, .38f), floatArrayOf(275f, .40f, .45f), floatArrayOf(195f, .40f, .42f)),
    )
}

private fun hsl(h: Float, s: Float, l: Float, a: Float): Int {
    val c = (1 - abs(2 * l - 1)) * s
    val hp = ((h % 360f) + 360f) % 360f / 60f
    val x = c * (1 - abs(hp % 2 - 1))
    val (r1, g1, b1) = when {
        hp < 1 -> Triple(c, x, 0f); hp < 2 -> Triple(x, c, 0f); hp < 3 -> Triple(0f, c, x)
        hp < 4 -> Triple(0f, x, c); hp < 5 -> Triple(x, 0f, c); else -> Triple(c, 0f, x)
    }
    val m = l - c / 2
    fun ch(v: Float) = ((v + m).coerceIn(0f, 1f) * 255).toInt()
    return android.graphics.Color.argb((a.coerceIn(0f, 1f) * 255).toInt(), ch(r1), ch(g1), ch(b1))
}

private class Star(val x: Float, val y: Float, val p: Float, val d: Int, val color: Int)
private class Bright(val x: Float, val y: Float, val p: Float, val s: Float, val warm: Boolean)

private class SpaceScene(val w: Int, val h: Int, palette: Int, lowPower: Boolean) {
    val nebula: Bitmap
    val dust: Bitmap
    val stars: List<Star>
    val bright: List<Bright>
    val glowCold: Bitmap = glow(0xFFBFD8FF.toInt())
    val glowWarm: Bitmap = glow(0xFFFFE2B8.toInt())
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    init {
        val rnd = Random(System.nanoTime())
        val pal = FiptvSpace.palettes[palette]
        // Nebel in halber Auflösung, 1,6-fache Fläche (für Drehen/Gleiten)
        val bw = (w * 0.8f).toInt().coerceAtLeast(64)
        val bh = (h * 0.8f).toInt().coerceAtLeast(64)
        nebula = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val nc = ACanvas(nebula)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
        fun blob(cx: Float, cy: Float, r: Float, h0: Float, s: Float, l: Float, a: Float) {
            p.shader = RadialGradient(
                cx, cy, r,
                intArrayOf(hsl(h0, s, l, a), hsl(h0, s, l - .12f, a * .4f), hsl(h0, s, l - .2f, 0f)),
                floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP,
            )
            nc.drawCircle(cx, cy, r, p)
        }
        for (c in pal) {
            var px = bw * (.2f + rnd.nextFloat() * .6f)
            var py = bh * (.2f + rnd.nextFloat() * .6f)
            repeat(22) {
                px += (rnd.nextFloat() - .5f) * bw * .07f
                py += (rnd.nextFloat() - .5f) * bh * .07f
                blob(px, py, (.05f + rnd.nextFloat() * .13f) * bw, c[0] + (rnd.nextFloat() - .5f) * 20f, c[1], c[2], .02f + rnd.nextFloat() * .03f)
            }
        }
        // dunkle Staubwolken
        p.xfermode = null
        repeat(40) {
            val cx = rnd.nextFloat() * bw; val cy = rnd.nextFloat() * bh
            val r = (.02f + rnd.nextFloat() * .06f) * bw
            p.shader = RadialGradient(cx, cy, r, intArrayOf(0x4D020308, 0x14020308, 0x00020308), floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP)
            nc.drawCircle(cx, cy, r, p)
        }
        // ferne Galaxien
        p.xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
        repeat(4) {
            val cx = rnd.nextFloat() * bw; val cy = rnd.nextFloat() * bh
            val rr = (3f + rnd.nextFloat() * 5f) * 2f
            nc.save(); nc.translate(cx, cy); nc.rotate(rnd.nextFloat() * 180f); nc.scale(1f, .35f)
            p.shader = RadialGradient(0f, 0f, rr, intArrayOf(0x8CFFF0E1.toInt(), 0x26BEBEE6, 0x00000000), floatArrayOf(0f, .35f, 1f), Shader.TileMode.CLAMP)
            nc.drawCircle(0f, 0f, rr, p)
            nc.restore()
        }
        p.shader = null

        // Sternenstaub in voller Auflösung (steht still)
        dust = Bitmap.createBitmap(w.coerceAtLeast(1), h.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val dc = ACanvas(dust)
        val sp = Paint()
        val area = (w.toFloat() * h) / (1920f * 1080f)
        val dustCount = ((if (lowPower) 2600 else 3600) * area.coerceIn(0.4f, 2f)).toInt()
        repeat(dustCount) {
            val a = rnd.nextFloat() * rnd.nextFloat() * .75f
            sp.color = android.graphics.Color.argb((a * 255).toInt(), 220 + rnd.nextInt(36), 225 + rnd.nextInt(31), 255)
            val x = rnd.nextFloat() * w; val y = rnd.nextFloat() * h
            dc.drawRect(x, y, x + 1.2f, y + 1.2f, sp)
        }
        // zwei dichtere Sternenbänder (Milchstraße)
        repeat(2) {
            val y0 = h * (.3f + rnd.nextFloat() * .4f)
            val ang = (rnd.nextFloat() - .5f) * .6f
            repeat(((if (lowPower) 1600 else 2400) * area.coerceIn(0.4f, 2f)).toInt()) {
                val u = rnd.nextFloat() * w
                val off = (rnd.nextFloat() + rnd.nextFloat() + rnd.nextFloat() - 1.5f) * h * .12f
                sp.color = android.graphics.Color.argb((rnd.nextFloat() * .45f * 255).toInt(), 255, 255, 255)
                val y = y0 + u * tan(ang) * .3f + off
                dc.drawRect(u, y, u + 1.1f, y + 1.1f, sp)
            }
        }

        val counts = if (lowPower) intArrayOf(220, 140, 80) else intArrayOf(420, 260, 140)
        stars = buildList {
            for (d in 0..2) repeat(counts[d]) {
                val tinted = rnd.nextFloat() < .18f
                val color = if (!tinted) android.graphics.Color.WHITE
                    else if (rnd.nextFloat() < .55f) hsl(210f, .8f, .82f, 1f) else hsl(38f, .8f, .82f, 1f)
                add(Star(rnd.nextFloat(), rnd.nextFloat(), rnd.nextFloat() * 6.28f, d, color))
            }
        }
        bright = List(if (lowPower) 8 else 12) {
            Bright(rnd.nextFloat(), rnd.nextFloat(), rnd.nextFloat() * 6.28f, 1.2f + rnd.nextFloat() * 2.2f, rnd.nextFloat() >= .5f)
        }
    }

    private fun glow(color: Int): Bitmap {
        val size = 64
        val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = ACanvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val rgb = color and 0x00FFFFFF
        p.shader = RadialGradient(32f, 32f, 32f, intArrayOf(0xFFFFFFFF.toInt(), (0x73 shl 24) or rgb, 0x00000000), floatArrayOf(0f, .3f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(32f, 32f, 32f, p)
        return b
    }

    fun draw(c: ACanvas, t: Float, density: Float) {
        c.drawColor(0xFF010207.toInt())
        val W = w.toFloat(); val H = h.toFloat()
        val ox = sin(t * .02f) * W * .15f + W * .3f
        val oy = cos(t * .015f) * H * .12f + H * .3f
        val sc = 1f + sin(t * .01f) * .04f
        c.save()
        c.translate(W / 2, H / 2)
        c.rotate(Math.toDegrees((t * .004f).toDouble()).toFloat())
        c.scale(sc * 2f, sc * 2f)
        paint.alpha = 255
        c.drawBitmap(nebula, (-W / 2 - ox) / 2f, (-H / 2 - oy) / 2f, paint)
        c.restore()
        c.drawBitmap(dust, 0f, 0f, paint)

        for (s in stars) {
            val sp = (s.d + 1) * .0012f
            val px = ((s.x + t * sp) % 1f) * W
            val py = s.y * H
            val a = (.45f + .55f * abs(sin(t * (.5f + s.d * .35f) + s.p))) * (.3f + s.d * .32f)
            paint.color = s.color
            paint.alpha = (a * 255).toInt().coerceIn(0, 255)
            val z = (.8f + s.d * .7f) * density * .7f
            c.drawRect(px, py, px + z, py + z, paint)
        }
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 1f }
        for (b in bright) {
            val px = ((b.x + t * .0025f) % 1f) * W
            val py = b.y * H
            val a = (.55f + .45f * sin(t * 1.1f + b.p)).coerceIn(0f, 1f)
            val r = b.s * density
            line.color = if (b.warm) 0xFFFFE6C4.toInt() else 0xFFD2E4FF.toInt()
            line.alpha = (a * .45f * 255).toInt()
            c.drawLine(px - r * 4, py, px + r * 4, py, line)
            c.drawLine(px, py - r * 4, px, py + r * 4, line)
            paint.alpha = (a * 255).toInt()
            val g = if (b.warm) glowWarm else glowCold
            c.drawBitmap(g, null, android.graphics.RectF(px - r * 3, py - r * 3, px + r * 3, py + r * 3), paint)
        }
    }
}

/**
 * Weltraum-Hintergrund. [fadeTop]: oben durchsichtig, nach unten deckend (über dem Startseiten-Bild).
 */
@Composable
fun FiptvSpaceBackground(modifier: Modifier = Modifier, fadeTop: Boolean = false) {
    if (FiptvSpace.mode == 0) return
    val lowPower = !com.arflix.tv.util.LocalDeviceType.current.isTouchDevice()
    BoxWithConstraints(modifier.fillMaxSize()) {
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        if (w <= 0 || h <= 0 || w > 8000 || h > 8000) return@BoxWithConstraints
        val pal = FiptvSpace.activePalette
        var scene by remember { mutableStateOf<SpaceScene?>(null) }
        LaunchedEffect(w, h, pal) {
            val built = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                runCatching { SpaceScene(w, h, pal, lowPower) }.getOrNull()
            }
            // alte Bilder nicht selbst freigeben (könnten noch gezeichnet werden) – GC räumt auf
            scene = built
        }
        var t by remember { mutableFloatStateOf(0f) }
        val animate = FiptvEffects.animations
        LaunchedEffect(animate, scene) {
            if (!animate || scene == null) return@LaunchedEffect
            var start = 0L
            var last = 0L
            while (true) {
                withFrameNanos { n ->
                    if (start == 0L) start = n - (t * 1e9f).toLong()
                    if (n - last >= 33_000_000L) {
                        last = n
                        t = (n - start) / 1e9f
                    }
                }
            }
        }
        val s = scene ?: return@BoxWithConstraints
        val maskModifier = if (fadeTop) {
            Modifier
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.42f to Color.Transparent,
                            0.72f to Color.Black,
                            1f to Color.Black,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                }
        } else Modifier
        Canvas(Modifier.fillMaxSize().then(maskModifier)) {
            val time = t
            drawIntoCanvas { c -> s.draw(c.nativeCanvas, time, density) }
        }
    }
}
