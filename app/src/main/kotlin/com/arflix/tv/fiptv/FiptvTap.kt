package com.arflix.tv.fiptv

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput

/**
 * fIPTV: Die Surface aus tv-material reagiert nur auf die Fernbedienung (OK-Taste).
 * Damit Kacheln und Knöpfe am Handy/Tablet auch per Antippen funktionieren.
 */
fun Modifier.fiptvTap(onClick: () -> Unit): Modifier = composed {
    val callback by rememberUpdatedState(onClick)
    pointerInput(Unit) { detectTapGestures(onTap = { callback() }) }
}
