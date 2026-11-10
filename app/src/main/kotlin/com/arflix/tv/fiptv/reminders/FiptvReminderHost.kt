package com.arflix.tv.fiptv.reminders

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.arflix.tv.fiptv.fiptvTap
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * fIPTV: zeigt kurz vor Beginn einer gemerkten Sendung einen Hinweis – in der ganzen App.
 * [onTune] schaltet auf den Sender um.
 */
@Composable
fun FiptvReminderHost(enabled: Boolean, onTune: (channelId: String) -> Unit) {
    val context = LocalContext.current
    var showing by remember { mutableStateOf<FiptvReminder?>(null) }
    val handled = remember { mutableSetOf<String>() }

    LaunchedEffect(enabled) {
        FiptvReminders.load(context)
        while (enabled) {
            val now = System.currentTimeMillis()
            if (showing == null) {
                FiptvReminders.due(now).firstOrNull { it.key !in handled }?.let { showing = it }
            }
            FiptvReminders.dropExpired(context, now)
            delay(15_000L)
        }
    }

    val reminder = showing ?: return
    val close = {
        handled += reminder.key
        FiptvReminders.remove(context, reminder)
        showing = null
    }
    val focus = remember { FocusRequester() }
    Dialog(onDismissRequest = close) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .background(Color(0xF0141820), RoundedCornerShape(16.dp))
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val minutes = ((reminder.startUtcMillis - System.currentTimeMillis()) / 60_000L)
            val time = SimpleDateFormat("HH:mm", Locale.GERMANY).format(Date(reminder.startUtcMillis))
            Text("🔔 Gemerkte Sendung", color = Color.White.copy(alpha = 0.7f), fontSize = 16.sp)
            Text(reminder.title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text(
                (if (minutes > 0) "Beginnt in $minutes Min. ($time Uhr)" else "Hat um $time Uhr begonnen") +
                    (if (reminder.channelName.isNotBlank()) " auf ${reminder.channelName}" else ""),
                color = Color.White.copy(alpha = 0.85f), fontSize = 18.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 10.dp)) {
                DialogButton("📺  Umschalten", primary = true, modifier = Modifier.focusRequester(focus)) {
                    close()
                    onTune(reminder.channelId)
                }
                DialogButton("Schließen", primary = false) { close() }
            }
        }
    }
    LaunchedEffect(reminder.key) { runCatching { delay(150); focus.requestFocus() } }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun DialogButton(label: String, primary: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
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
        Text(label, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp))
    }
}
