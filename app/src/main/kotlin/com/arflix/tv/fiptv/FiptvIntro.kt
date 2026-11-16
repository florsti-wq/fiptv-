package com.arflix.tv.fiptv

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** fIPTV: kurze Startanimation (Logo), überspringbar mit jeder Taste oder einem Tipp. */
object FiptvIntro {
    var visible by mutableStateOf(false)
        private set
    var skip by mutableStateOf(false)

    fun start() {
        visible = FiptvEffects.animations || FiptvEffects.sounds
        skip = false
    }

    fun finish() { visible = false }

    /** Von MainActivity aus: Taste während der Animation → beenden, Taste verschlucken. */
    fun consumeKey(): Boolean {
        if (!visible) return false
        skip = true
        return true
    }
}

private val IntroAccent = Color(0xFF5CE1E6)

@Composable
fun FiptvIntroOverlay() {
    if (!FiptvIntro.visible) return
    val letters = "fIPTV"
    val anims = remember { List(letters.length) { Animatable(0f) } }
    val bar = remember { Animatable(0f) }
    val alpha = remember { Animatable(1f) }
    var started by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        started = true
        if (!FiptvEffects.animations) {
            // Nur Ton gewünscht: Klang spielen, Overlay sofort weg
            FiptvEffects.play(FiptvEffects.START)
            FiptvIntro.finish()
            return@LaunchedEffect
        }
        delay(120)
        FiptvEffects.play(FiptvEffects.START)
        anims.forEachIndexed { i, a ->
            launch {
                delay(i * 90L)
                a.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow))
            }
        }
        launch { delay(450); bar.animateTo(1f, tween(650)) }
        delay(1_450)
        alpha.animateTo(0f, tween(320))
        FiptvIntro.finish()
    }
    LaunchedEffect(FiptvIntro.skip) {
        if (FiptvIntro.skip && started) {
            alpha.animateTo(0f, tween(150))
            FiptvIntro.finish()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha.value }
            .background(Color.Black)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                FiptvIntro.skip = true
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                letters.forEachIndexed { i, c ->
                    val v = anims[i].value
                    Text(
                        text = c.toString(),
                        color = if (i == 0) IntroAccent else Color.White,
                        fontSize = 64.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.graphicsLayer {
                            this.alpha = v.coerceIn(0f, 1f)
                            translationY = (1f - v) * 40f
                            val s = 0.8f + 0.2f * v
                            scaleX = s; scaleY = s
                        },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .width((180 * bar.value).dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(IntroAccent)
            )
        }
    }
}
