package com.arflix.tv.ui.components

import android.content.Context
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.arflix.tv.data.model.Profile
import com.arflix.tv.ui.skin.resolveAccentColor
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * fIPTV: Hauptmenü als schmale Leiste links (nur Icons, klappt nicht auf). Die Bildschirme steuern den Fokus weiter wie bei der oberen Leiste;
 * [MainActivity] dreht dafür die Pfeiltasten, solange das Menü den Fokus hat (hoch/runter = vorher
 * links/rechts, rechts = zurück zum Inhalt).
 */
object FiptvTvRail {
    private const val PREFS = "fiptv_ui"
    val COLLAPSED: Dp = 88.dp
    val EXPANDED: Dp = 252.dp

    data class RailState(
        val selected: SidebarItem,
        val focused: Boolean,
        val focusedIndex: Int,
        val profile: Profile?,
        val hasBadge: Boolean,
    )

    /** Einstellung „Menü links“ (Fire TV); Standard an */
    var enabled by mutableStateOf(true)
        private set
    var state by mutableStateOf<RailState?>(null)
        internal set
    private var present by mutableIntStateOf(0)

    /** Wird gerade ein Bildschirm mit Hauptmenü gezeigt? */
    val visible: Boolean get() = enabled && present > 0 && state != null
    val focused: Boolean get() = visible && state?.focused == true

    private var appContext: Context? = null

    fun toggle() { appContext?.let { setEnabled(it, !enabled) } }

    fun load(context: Context) {
        appContext = context.applicationContext
        enabled = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("nav_left", true)
    }

    fun setEnabled(context: Context, on: Boolean) {
        enabled = on
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("nav_left", on).apply()
    }

    /** Statt der oberen Leiste: Zustand an die Leiste links melden. */
    @Composable
    internal fun Publish(selected: SidebarItem, isFocused: Boolean, focusedIndex: Int, profile: Profile?, hasBadge: Boolean) {
        SideEffect { state = RailState(selected, isFocused, focusedIndex, profile, hasBadge) }
        DisposableEffect(Unit) {
            present++
            onDispose { present-- }
        }
    }

    private val _enter = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** Bitte an den sichtbaren Bildschirm: Fokus ins Menü links setzen (Menü-Taste). */
    val enterRequests: kotlinx.coroutines.flow.SharedFlow<Unit> = _enter

    fun requestEnter(): Boolean {
        if (!visible || focused) return false
        return _enter.tryEmit(Unit)
    }

    /** Am linken Rand nach links: soll der Bildschirm ins Menü wechseln? */
    fun leftEdge(): Boolean = enabled && visible

    /** Pfeiltasten umdeuten, solange das Menü den Fokus hat. null = nicht anfassen. */
    fun remapKey(keyCode: Int): Int? {
        if (!focused) return null
        return when (keyCode) {
            android.view.KeyEvent.KEYCODE_DPAD_UP -> android.view.KeyEvent.KEYCODE_DPAD_LEFT
            android.view.KeyEvent.KEYCODE_DPAD_DOWN -> android.view.KeyEvent.KEYCODE_DPAD_RIGHT
            android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> android.view.KeyEvent.KEYCODE_DPAD_DOWN
            android.view.KeyEvent.KEYCODE_DPAD_LEFT -> -1 // nichts tun
            else -> null
        }
    }
}

@Composable
fun FiptvTvRailView(modifier: Modifier = Modifier) {
    val st = FiptvTvRail.state ?: return
    val accent = resolveAccentColor(fallback = Color(0xFF4F8CFF))
    val hasProfile = st.profile != null
    val items = topBarNavItems
    // fIPTV: nur Icons; Größe passt sich der Bildschirmhöhe an, damit alles (auch TV und Einstellungen) sichtbar bleibt
    val count = items.size + 1 + (if (hasProfile) 1 else 0)
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .width(FiptvTvRail.COLLAPSED)
            .background(if (st.focused) Color(0xF2121621) else Color(0xE60E1118)),
    ) {
        val gap = 4.dp
        val extra = if (hasProfile) 10.dp else 0.dp
        val avail = maxHeight - 32.dp - extra - gap * (count - 1)
        val cell = (avail / count).coerceIn(34.dp, 52.dp)
        val iconSize = (cell * 0.5f).coerceIn(20.dp, 26.dp)
        Column(
            modifier = Modifier.fillMaxHeight().fillMaxWidth().padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            if (hasProfile) {
                val p = st.profile!!
                RailIcon(cell, focused = st.focused && st.focusedIndex == 0, selected = false, accent = accent) {
                    Box(
                        Modifier.size(cell * 0.7f).clip(CircleShape).background(Color(p.avatarColor)),
                        contentAlignment = Alignment.Center,
                    ) { Text(p.name.take(1).uppercase(), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
                }
                Spacer(Modifier.height(extra))
            }
            items.forEachIndexed { i, item ->
                val idx = if (hasProfile) i + 1 else i
                val label = if (item == SidebarItem.TV) stringResource(com.arflix.tv.R.string.topbar_tv) else stringResource(item.labelRes)
                RailIcon(cell, focused = st.focused && st.focusedIndex == idx, selected = st.selected == item, accent = accent) {
                    Icon(item.icon, contentDescription = label, modifier = Modifier.size(iconSize))
                }
            }
            Spacer(Modifier.weight(1f))
            val settingsIdx = topBarMaxIndex(hasProfile)
            RailIcon(
                cell,
                focused = st.focused && st.focusedIndex == settingsIdx,
                selected = st.selected == SidebarItem.SETTINGS,
                accent = accent,
                badge = st.hasBadge,
            ) { Icon(SidebarItem.SETTINGS.icon, contentDescription = stringResource(SidebarItem.SETTINGS.labelRes), modifier = Modifier.size(iconSize)) }
        }
    }
}

@Composable
private fun RailIcon(
    cell: Dp,
    focused: Boolean,
    selected: Boolean,
    accent: Color,
    badge: Boolean = false,
    icon: @Composable () -> Unit,
) {
    val bg = when {
        selected -> accent
        focused -> Color.White.copy(alpha = 0.16f)
        else -> Color.Transparent
    }
    val fg = if (selected || focused) Color.White else Color(0xFFAEB6C4)
    Box(
        modifier = Modifier
            .size(cell)
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .then(if (focused) Modifier.border(3.dp, Color.White, RoundedCornerShape(14.dp)) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(androidx.tv.material3.LocalContentColor provides fg) {
            Box(contentAlignment = Alignment.TopEnd) {
                icon()
                if (badge) Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFF97316)))
            }
        }
    }
}
