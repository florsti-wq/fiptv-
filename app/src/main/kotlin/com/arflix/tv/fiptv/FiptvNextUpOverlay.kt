package com.arflix.tv.fiptv

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.delay

/** Sekunden bis zur nächsten Folge */
private const val COUNTDOWN = 10

/** Hat diese Wiedergabe eine nächste fIPTV-Folge? Dann übernimmt [FiptvNextUpOverlay] das Ende. */
fun fiptvHasNext(streamUrl: String?): Boolean = FiptvNextUp.nextFor(streamUrl) != null

@Composable
fun FiptvNextUpOverlay(player: Player, streamUrl: String?, onFinish: () -> Unit) {
    val next = remember(streamUrl) { FiptvNextUp.nextFor(streamUrl) } ?: return
    val context = LocalContext.current
    var ended by remember { mutableStateOf(false) }
    var cancelled by remember { mutableStateOf(false) }
    var seconds by remember { mutableIntStateOf(COUNTDOWN) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) ended = true
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    // Abspann: auch schon in den letzten Sekunden anbieten, falls der Stream nicht sauber endet
    LaunchedEffect(player) {
        while (true) {
            val d = player.duration
            if (d > 60_000 && player.currentPosition >= d - 1_500) ended = true
            if (player.playbackState == Player.STATE_ENDED) ended = true
            delay(1_000)
        }
    }
    if (!ended || cancelled) return

    val start = {
        val pos = FiptvNextUp.prepare(context, next)
        FiptvNextUp.playNext?.invoke(next, pos) ?: onFinish()
    }
    LaunchedEffect(Unit) {
        while (seconds > 0) { delay(1_000); seconds-- }
        start()
    }
    val focus = remember { FocusRequester() }
    androidx.compose.ui.window.Dialog(
        onDismissRequest = { cancelled = true; onFinish() },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.BottomEnd) {
        Column(
            modifier = Modifier
                .padding(48.dp)
                .widthIn(max = 560.dp)
                .background(Color(0xF0141820), RoundedCornerShape(16.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Nächste Folge in $seconds s", color = Color.White.copy(alpha = 0.75f), fontSize = 16.sp)
            val label = buildString {
                if (next.season != null && next.episode != null) append("S${next.season} E${next.episode} · ")
                append(next.title)
            }
            Text(label, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2)
            if (next.subtitle.isNotBlank()) Text(next.subtitle, color = Color.White.copy(alpha = 0.7f), fontSize = 15.sp, maxLines = 1)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                NextButton("▶  Jetzt abspielen", primary = true, modifier = Modifier.focusRequester(focus)) { start() }
                NextButton("Abbrechen", primary = false) { cancelled = true; onFinish() }
            }
        }
    }
    }
    LaunchedEffect(Unit) { runCatching { delay(100); focus.requestFocus() } }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun NextButton(label: String, primary: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.fiptvTap(onClick),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (primary) Color.White else Color.White.copy(alpha = 0.15f),
            contentColor = if (primary) Color.Black else Color.White,
            focusedContainerColor = Color(0xFFE50914),
            focusedContentColor = Color.White,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(border = BorderStroke(2.dp, Color.White), shape = RoundedCornerShape(10.dp)),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
    ) {
        Text(label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 11.dp))
    }
}
