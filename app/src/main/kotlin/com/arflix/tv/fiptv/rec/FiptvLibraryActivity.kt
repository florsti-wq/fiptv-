package com.arflix.tv.fiptv.rec

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Accent = Color(0xFF5CE1E6)

/** fIPTV: Übersicht der Aufnahmen und Downloads. */
class FiptvLibraryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FiptvLibrary.init(applicationContext)
        setContent { LibraryScreen(onClose = { finish() }) }
    }

    companion object {
        fun open(context: Context) {
            context.startActivity(Intent(context, FiptvLibraryActivity::class.java).apply {
                if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }
}

@Composable
private fun LibRow(
    title: String,
    subtitle: String,
    progress: Float?,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .background(if (focused) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.04f), RoundedCornerShape(10.dp))
            .border(if (focused) 2.dp else 0.dp, if (focused) Accent else Color.Transparent, RoundedCornerShape(10.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(title, color = Color.White, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle, color = Color(0xFFA0A0A8), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (progress != null) {
            Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(3.dp).background(Color(0xFF333333))) {
                Box(Modifier.fillMaxWidth(progress).height(3.dp).background(Accent))
            }
        }
    }
}

private fun sizeText(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.1f GB".format(Locale.GERMANY, bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> "%d MB".format(bytes / (1L shl 20))
    else -> "${bytes / 1024} KB"
}

@Composable
private fun LibraryScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(2_000); tick++ } }
    val version = FiptvLibrary.version
    val entries = remember(version, tick) { FiptvLibrary.all() }
    var menuFor by remember { mutableStateOf<FiptvLibrary.Entry?>(null) }
    val first = remember { FocusRequester() }
    LaunchedEffect(entries.isNotEmpty()) { if (entries.isNotEmpty()) runCatching { first.requestFocus() } }
    val fmt = remember { SimpleDateFormat("dd.MM. HH:mm", Locale.GERMANY) }

    Box(Modifier.fillMaxSize().background(Color(0xFF0E0F12)), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 900.dp).fillMaxWidth().padding(32.dp)) {
            Text("Aufnahmen und Downloads", color = Color.White, fontSize = 26.sp)
            Text(
                "Aufnahmen startest du im Live TV mit langem OK auf einen Sender. Downloads im Film-Player auf dem Handy.",
                color = Color(0xFFA0A0A8), fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
            )
            if (entries.isEmpty()) {
                Text("Noch nichts gespeichert.", color = Color(0xFFA0A0A8), fontSize = 16.sp)
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(entries, key = { it.id }) { e ->
                    val running = e.status == FiptvLibrary.Status.RUNNING
                    val progress = if (e.kind == FiptvLibrary.Kind.DOWNLOAD && running) FiptvLibrary.downloadProgress(context, e) else null
                    val size = if (e.kind == FiptvLibrary.Kind.RECORDING && running) FiptvRecordService.bytesOf(e.id) ?: e.file.length() else e.file.length()
                    val state = when (e.status) {
                        FiptvLibrary.Status.RUNNING -> if (e.kind == FiptvLibrary.Kind.RECORDING) "● Aufnahme läuft" + (e.endsAt?.let { " bis " + SimpleDateFormat("HH:mm", Locale.GERMANY).format(Date(it)) } ?: "") else "Wird geladen" + (progress?.let { " · ${(it * 100).toInt()} %" } ?: "")
                        FiptvLibrary.Status.DONE -> if (e.kind == FiptvLibrary.Kind.RECORDING) "Aufnahme" else "Download"
                        FiptvLibrary.Status.FAILED -> "Fehlgeschlagen"
                    }
                    LibRow(
                        title = e.title,
                        subtitle = listOfNotNull(state, fmt.format(Date(e.createdAt)), sizeText(size).takeIf { size > 0 }, e.note).joinToString("  ·  "),
                        progress = progress,
                        focusRequester = if (e.id == entries.first().id) first else null,
                    ) { menuFor = e }
                }
            }
        }
    }

    menuFor?.let { e ->
        Dialog(onDismissRequest = { menuFor = null }) {
            val f = remember { FocusRequester() }
            LaunchedEffect(Unit) { runCatching { f.requestFocus() } }
            Column(
                Modifier.widthIn(max = 480.dp).background(Color(0xFF17181C), RoundedCornerShape(14.dp)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(e.title, color = Color.White, fontSize = 18.sp, maxLines = 2)
                val canPlay = e.file.exists() && e.file.length() > 0
                if (canPlay) LibRow("Abspielen", "", null, f) { menuFor = null; FiptvLocalPlayerActivity.open(context, e) }
                if (e.kind == FiptvLibrary.Kind.RECORDING && e.status == FiptvLibrary.Status.RUNNING) {
                    LibRow("Aufnahme beenden", "Bisher Aufgenommenes bleibt erhalten", null, if (!canPlay) f else null) {
                        menuFor = null; FiptvRecordService.stop(context, e.id)
                    }
                }
                LibRow("Löschen", "Datei wird entfernt", null, if (!canPlay && e.status != FiptvLibrary.Status.RUNNING) f else null) {
                    menuFor = null; FiptvLibrary.delete(context, e.id)
                }
                LibRow("Abbrechen", "", null, null) { menuFor = null }
            }
        }
    }
    androidx.activity.compose.BackHandler(enabled = menuFor == null) { onClose() }
}
