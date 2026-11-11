package com.arflix.tv.fiptv.youtube

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
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
import com.arflix.tv.ui.components.QrCodeImage

private val Panel = Color(0xF2141820)
private val Muted = Color(0xFFB4BCCA)

/** Anmeldung mit Google: QR-Code + Code, Bestätigung am Handy. */
@Composable
fun YouTubeSignInDialog(state: YtSignIn, onRetry: () -> Unit, onCancel: () -> Unit) {
    val focus = remember { FocusRequester() }
    Dialog(onDismissRequest = onCancel) {
        Column(
            Modifier.widthIn(max = 720.dp).background(Panel, RoundedCornerShape(18.dp)).padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Mit Google anmelden", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
                if (state.url.isNotBlank()) {
                    Box(Modifier.background(Color.White, RoundedCornerShape(12.dp)).padding(10.dp)) {
                        QrCodeImage(data = state.url, sizePx = 420, modifier = Modifier.size(190.dp))
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("1. QR-Code mit dem Handy scannen oder öffnen:", color = Muted, fontSize = 15.sp)
                    Text(state.url.removePrefix("https://").ifBlank { "…" }, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Text("2. Diesen Code eingeben:", color = Muted, fontSize = 15.sp)
                    Text(
                        state.userCode.ifBlank { "…" },
                        color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
                    )
                    Text("3. Mit deinem Google-Konto anmelden und „Zulassen“ tippen.", color = Muted, fontSize = 15.sp)
                }
            }
            state.message?.let {
                Text(it, color = if (state.error) Color(0xFFFF8A80) else Color.White.copy(alpha = 0.8f), fontSize = 15.sp)
            }
            Text(
                "fIPTV sieht dein Passwort nie. Der Zugang gilt nur für dieses Profil auf diesem Gerät und lässt sich jederzeit abmelden.",
                color = Muted.copy(alpha = 0.8f), fontSize = 13.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.error) DialogButton("Neu starten", true, Modifier.focusRequester(focus), onRetry)
                DialogButton("Abbrechen", !state.error, if (state.error) Modifier else Modifier.focusRequester(focus), onCancel)
            }
        }
    }
    LaunchedEffect(state.error) { runCatching { kotlinx.coroutines.delay(150); focus.requestFocus() } }
}

@Composable
fun YouTubeSignOutDialog(name: String, onSignOut: () -> Unit, onCancel: () -> Unit) {
    val focus = remember { FocusRequester() }
    Dialog(onDismissRequest = onCancel) {
        Column(
            Modifier.widthIn(max = 520.dp).background(Panel, RoundedCornerShape(18.dp)).padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Google-Konto", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("Angemeldet als $name. Abmelden entfernt den Zugang von diesem Gerät und widerruft ihn bei Google.", color = Muted, fontSize = 15.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DialogButton("Angemeldet bleiben", true, Modifier.focusRequester(focus), onCancel)
                Spacer(Modifier.width(4.dp))
                DialogButton("Abmelden", false, Modifier, onSignOut)
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { kotlinx.coroutines.delay(150); focus.requestFocus() } }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun DialogButton(label: String, primary: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.fiptvTap(onClick),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (primary) Color.White else Color.White.copy(alpha = 0.15f),
            contentColor = if (primary) Color.Black else Color.White,
            focusedContainerColor = Color(0xFFFF3B30),
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
