package com.arflix.tv.ui.screens.tv.live

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.arflix.tv.data.model.IptvNowNext
import com.arflix.tv.fiptv.FiptvEffects
import com.arflix.tv.fiptv.live.FiptvChannelCleanup
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val ZapAccent = Color(0xFF5CE1E6)

/**
 * fIPTV: Senderwechsel im Vollbild.
 *  - Schieben: ein dunkler Vorhang mit dem Sendernamen schiebt sich in Tastenrichtung über das
 *    alte Bild und gibt das neue frei, sobald dessen erstes Bild da ist.
 *  - Infoleiste: unten Sender, laufende Sendung und Fortschritt, nach einigen Sekunden weg.
 */
@Composable
internal fun FiptvZapOverlay(
    zapSignal: Int,
    direction: Int,
    channel: EnrichedChannel?,
    nowNext: IptvNowNext?,
    firstFrameSignal: Int,
    showBanner: Boolean,
) {
    val curtain = remember { Animatable(2f) } // |wert| >= 1 → außerhalb des Bildes
    var bannerVisible by remember { mutableStateOf(false) }
    val frames = rememberUpdatedState(firstFrameSignal)

    LaunchedEffect(zapSignal) {
        if (zapSignal == 0) return@LaunchedEffect
        val frameAtZap = frames.value
        if (FiptvEffects.animations) {
            val from = if (direction >= 0) 1f else -1f
            if (kotlin.math.abs(curtain.value) >= 1f) curtain.snapTo(from)
            curtain.animateTo(0f, tween(200, easing = FastOutSlowInEasing))
            withTimeoutOrNull(2_500) { snapshotFlow { frames.value }.first { it != frameAtZap } }
            delay(60)
            curtain.animateTo(-from, tween(240, easing = FastOutSlowInEasing))
            curtain.snapTo(2f)
        }
    }
    LaunchedEffect(zapSignal, showBanner) {
        if (zapSignal == 0 || !showBanner) { bannerVisible = false; return@LaunchedEffect }
        bannerVisible = true
        delay(3_800)
        bannerVisible = false
    }

    Box(Modifier.fillMaxSize()) {
        if (kotlin.math.abs(curtain.value) < 1f) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val h = constraints.maxHeight.toFloat()
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { translationY = curtain.value * h }
                        .background(Color(0xFF0E0E10)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = channel?.let { zapTitle(it) }.orEmpty(),
                            color = Color.White,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(14.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.size(26.dp),
                            color = ZapAccent,
                            strokeWidth = 3.dp,
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = bannerVisible && channel != null,
            enter = if (FiptvEffects.animations) slideInVertically(tween(280)) { it * 2 } + fadeIn(tween(200)) else fadeIn(tween(1)),
            exit = if (FiptvEffects.animations) slideOutVertically(tween(260)) { it * 2 } + fadeOut(tween(200)) else fadeOut(tween(1)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            channel?.let { ZapBanner(it, nowNext) }
        }
    }
}

private fun zapTitle(channel: EnrichedChannel): String {
    val name = FiptvChannelCleanup.displayName(channel.name)
    return if (channel.number > 0) "${channel.number} · $name" else name
}

@Composable
private fun ZapBanner(channel: EnrichedChannel, nowNext: IptvNowNext?) {
    val fmt = remember { SimpleDateFormat("HH:mm", Locale.GERMANY) }
    val now = System.currentTimeMillis()
    val current = nowNext?.now?.takeIf { it.isLive(now) } ?: nowNext?.now
    val next = nowNext?.next
    Column(
        modifier = Modifier
            .padding(horizontal = 48.dp, vertical = 28.dp)
            .widthIn(max = 900.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xEB141416))
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = zapTitle(channel),
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Text(fmt.format(Date(now)), color = Color(0xFFAAAAAA), fontSize = 16.sp)
        }
        if (current != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Jetzt: ${current.title}  ·  ${fmt.format(Date(current.startUtcMillis))} – ${fmt.format(Date(current.endUtcMillis))}",
                color = Color(0xFFDDDDDD),
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val span = (current.endUtcMillis - current.startUtcMillis).coerceAtLeast(1L)
            val progress = ((now - current.startUtcMillis).toFloat() / span).coerceIn(0f, 1f)
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF333333))) {
                Box(Modifier.fillMaxWidth(progress).height(3.dp).background(ZapAccent))
            }
            if (next != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Danach: ${next.title}  ·  ${fmt.format(Date(next.startUtcMillis))}",
                    color = Color(0xFF999999),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            Spacer(Modifier.height(4.dp))
            Text("Keine Programmdaten", color = Color(0xFF999999), fontSize = 13.sp)
        }
    }
}
