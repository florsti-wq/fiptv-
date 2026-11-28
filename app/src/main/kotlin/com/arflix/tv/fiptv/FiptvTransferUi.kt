package com.arflix.tv.fiptv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Text
import kotlinx.coroutines.launch

private val Accent = Color(0xFF5CE1E6)
private val Panel = Color(0xFF17181C)
private val Muted = Color(0xFFA0A0A8)

@Composable
private fun Btn(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, focus: FocusRequester? = null, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .let { if (focus != null) it.focusRequester(focus) else it }
            .background(
                when { !enabled -> Color.White.copy(alpha = 0.05f); focused -> Accent; else -> Color.White.copy(alpha = 0.12f) },
                RoundedCornerShape(10.dp),
            )
            .clickable(enabled = enabled, interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 11.dp),
    ) {
        Text(text, color = if (focused && enabled) Color(0xFF062024) else if (enabled) Color.White else Muted,
            fontSize = 16.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}

@Composable
private fun Frame(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .width(600.dp)
            .background(Panel, RoundedCornerShape(14.dp))
            .border(1.dp, Accent.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) { content() }
}

/** Fenster für beide Richtungen; in den Einstellungen eingebunden. */
@Composable
fun FiptvTransferOverlay(applySnapshot: suspend (String) -> Unit) {
    FiptvTransfer.send?.let { st ->
        Dialog(onDismissRequest = { FiptvTransfer.stopSend() }) {
            val focus = remember { FocusRequester() }
            LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
            Frame {
                Text("Auf anderes Gerät übertragen", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("Am neuen Gerät: Einstellungen → Netzwerk → „Von anderem Gerät übernehmen“ und diesen Code eingeben. Beide Geräte müssen im selben WLAN sein.",
                    color = Muted, fontSize = 14.sp, textAlign = TextAlign.Center)
                Text(st.code.chunked(3).joinToString(" "), color = Accent, fontSize = 54.sp, fontWeight = FontWeight.Bold, letterSpacing = 6.sp)
                if (st.address.isNotBlank()) Text("Falls das Gerät nicht gefunden wird, dort diese Adresse eingeben: " + st.address, color = Muted, fontSize = 13.sp, textAlign = TextAlign.Center)
                Text("Übertragen werden Quellen mit Zugangsdaten, Profile, Favoriten, eigene Gruppen, Programm-Zuordnungen und Einstellungen. Aufnahmen bleiben hier.",
                    color = Muted, fontSize = 13.sp, textAlign = TextAlign.Center)
                st.message?.let { Text(it, color = Accent, fontSize = 15.sp, textAlign = TextAlign.Center) }
                Spacer(Modifier.height(4.dp))
                Btn(if (st.done) "Fertig" else "Abbrechen", focus = focus) { FiptvTransfer.stopSend() }
            }
        }
    }

    if (FiptvTransfer.receiveOpen) {
        Dialog(onDismissRequest = { if (!FiptvTransfer.receiveBusy) FiptvTransfer.closeReceive() }) {
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            var target by remember { mutableStateOf<FiptvTransfer.Found?>(null) }
            var code by remember { mutableStateOf("") }
            var manual by remember { mutableStateOf("") }
            var manualOpen by remember { mutableStateOf(false) }
            val focus = remember { FocusRequester() }
            LaunchedEffect(target, manualOpen) { runCatching { focus.requestFocus() } }
            Frame {
                Text("Von anderem Gerät übernehmen", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                val t = target
                if (t == null) {
                    Text("Am alten Gerät: Einstellungen → Netzwerk → „Auf anderes Gerät übertragen“ öffnen. Es erscheint dann hier.",
                        color = Muted, fontSize = 14.sp, textAlign = TextAlign.Center)
                    Text("Achtung: Profile, Quellen und Einstellungen auf diesem Gerät werden dabei ersetzt.",
                        color = Color(0xFFFFC266), fontSize = 13.sp, textAlign = TextAlign.Center)
                    if (FiptvTransfer.found.isEmpty()) Text("Suche im WLAN …", color = Muted, fontSize = 15.sp)
                    FiptvTransfer.found.forEachIndexed { i, f ->
                        Btn(f.name, Modifier.fillMaxWidth(), focus = if (i == 0 && !manualOpen) focus else null) { target = f; code = "" }
                    }
                    if (manualOpen) {
                        BasicTextField(
                            value = manual, onValueChange = { manual = it.take(21) }, singleLine = true,
                            textStyle = TextStyle(color = Color.White, fontSize = 18.sp),
                            cursorBrush = SolidColor(Accent),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            modifier = Modifier.fillMaxWidth().focusRequester(focus)
                                .background(Color(0xFF0E1012), RoundedCornerShape(8.dp)).padding(12.dp),
                            decorationBox = { inner -> if (manual.isEmpty()) Text("z. B. 192.168.1.20:8210", color = Muted, fontSize = 18.sp); inner() },
                        )
                        Btn("Verbinden", Modifier.fillMaxWidth()) {
                            FiptvTransfer.parseAddress(manual)?.let { target = it; code = "" }
                        }
                    } else {
                        Btn("Adresse von Hand eingeben", Modifier.fillMaxWidth(), focus = if (FiptvTransfer.found.isEmpty()) focus else null) { manualOpen = true }
                    }
                    Btn("Abbrechen", Modifier.fillMaxWidth()) { FiptvTransfer.closeReceive() }
                } else {
                    Text("Code von „${t.name}“ eingeben", color = Muted, fontSize = 15.sp)
                    Text(code.padEnd(6, '·').chunked(3).joinToString(" "), color = Accent, fontSize = 44.sp, fontWeight = FontWeight.Bold, letterSpacing = 6.sp)
                    FiptvTransfer.receiveMessage?.let { Text(it, color = Color.White, fontSize = 14.sp, textAlign = TextAlign.Center) }
                    val busy = FiptvTransfer.receiveBusy
                    val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("←", "0", "OK"))
                    rows.forEachIndexed { ri, row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEachIndexed { ci, k ->
                                Btn(k, Modifier.size(width = 96.dp, height = 52.dp),
                                    enabled = !busy && (k != "OK" || code.length == 6),
                                    focus = if (ri == 0 && ci == 0) focus else null) {
                                    when (k) {
                                        "←" -> code = code.dropLast(1)
                                        "OK" -> scope.launch { FiptvTransfer.receive(context, t, code, applySnapshot) }
                                        else -> if (code.length < 6) {
                                            code += k
                                            if (code.length == 6) scope.launch { FiptvTransfer.receive(context, t, code, applySnapshot) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Btn("Zurück", Modifier.fillMaxWidth(), enabled = !busy) { target = null }
                }
            }
        }
    }
}
