package com.arflix.tv.fiptv.mediathek

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.arflix.tv.ui.components.TextInputModal
import com.arflix.tv.fiptv.FiptvResume
import com.arflix.tv.fiptv.ProgressBar
import com.arflix.tv.fiptv.ResumeRow
import com.arflix.tv.util.LocalDeviceType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Bg: Color get() = com.arflix.tv.ui.theme.BackgroundDark
private val Panel = Color(0xFF14161C)
private val Muted = Color(0xFFA0A6B4)
private val Accent = Color(0xFF4F7CFF)
private val dateFormat = SimpleDateFormat("EE dd.MM. HH:mm", Locale.GERMANY)

/**
 * fIPTV: Mediatheken-Bereich. Oben Senderwahl, Suche und Filter, darunter die neuesten Sendungen.
 * Funktioniert mit Fernbedienung (Steuerkreuz + OK) und Touch.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MediathekScreen(
    /** Abspielen; zweiter Wert = Startposition (Weiterschauen) */
    onPlay: (entry: FiptvResume.Entry, startPositionMs: Long?) -> Unit,
    viewModel: MediathekViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val resumeChanges by FiptvResume.changes.collectAsStateWithLifecycle()
    val resume = remember(resumeChanges) { viewModel.resumeEntries() }
    val isTouch = LocalDeviceType.current.isTouchDevice()
    var searchOpen by remember { mutableStateOf(false) }
    val firstFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()

    // Fokus beim Öffnen auf "Alle" – sonst hätte auf dem Fernseher nichts den Fokus
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
            .padding(start = 40.dp, end = 40.dp, top = 24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Mediathek", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(20.dp))
            Pill(
                label = if (state.search.isBlank()) "🔍 Suchen" else "🔍 ${state.search}",
                selected = state.search.isNotBlank(),
                onClick = { searchOpen = true },
            )
            if (state.search.isNotBlank()) {
                Spacer(Modifier.width(8.dp))
                Pill(label = "✕", selected = false, onClick = { viewModel.setSearch("") })
            }
            Spacer(Modifier.width(8.dp))
            Pill(
                label = if (state.longOnly) "Nur ganze Sendungen" else "Auch kurze Clips",
                selected = state.longOnly,
                onClick = { viewModel.toggleLongOnly() },
            )
        }
        Spacer(Modifier.height(12.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
            item {
                Pill(
                    label = "Alle",
                    selected = state.channel == null,
                    onClick = { viewModel.selectChannel(null) },
                    modifier = Modifier.focusRequester(firstFocus),
                )
            }
            items(MediathekRepository.channels, key = { it.id }) { ch ->
                Pill(label = ch.label, selected = state.channel == ch.id, onClick = { viewModel.selectChannel(ch.id) })
            }
        }
        Spacer(Modifier.height(10.dp))
        // Weiterschauen nur in der Startansicht (ohne Suche)
        if (state.search.isBlank()) {
            ResumeRow(
                entries = resume,
                onPlay = { e -> onPlay(e, e.positionMs) },
                onRemove = { e -> viewModel.removeResume(e) },
                isTouch = isTouch,
                modifier = Modifier.padding(bottom = 10.dp),
            )
        }
        when {
            state.error != null -> Text("⚠ ${state.error}", color = Color(0xFFFF8A80), fontSize = 15.sp)
            state.loading && state.items.isEmpty() -> Text("Wird geladen …", color = Muted, fontSize = 15.sp)
            !state.loading && state.items.isEmpty() -> Text("Keine Sendungen gefunden.", color = Muted, fontSize = 15.sp)
        }
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(bottom = 32.dp, top = 4.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(state.items, key = { _, it -> it.id }) { index, item ->
                // kurz vor dem Ende die nächste Seite laden
                if (index >= state.items.size - 6) LaunchedEffect(state.items.size) { viewModel.loadMore() }
                val progress = remember(item.id, resumeChanges) { viewModel.progressOf(item) }
                MediathekRow(item = item, progress = progress, onClick = {
                    viewModel.prepare(item)?.let { (e, start) -> onPlay(e, start) }
                })
            }
            if (state.loading && state.items.isNotEmpty()) {
                item { Text("Weitere werden geladen …", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(8.dp)) }
            }
        }
    }

    TextInputModal(
        isVisible = searchOpen,
        title = "In den Mediatheken suchen",
        hint = "z. B. Tatort  ·  !zdf heute  ·  #Thema",
        initialValue = state.search,
        onConfirm = {
            searchOpen = false
            viewModel.setSearch(it)
        },
        onCancel = { searchOpen = false },
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Pill(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(50)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Accent.copy(alpha = 0.35f) else Panel,
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
    ) {
        Text(
            text = label,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun MediathekRow(item: MediathekItem, progress: FiptvResume.Entry?, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Panel,
            contentColor = Color.White,
            focusedContainerColor = Color(0xFF232838),
            focusedContentColor = Color.White,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.01f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(2.dp, Accent),
                shape = RoundedCornerShape(10.dp),
            )
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(72.dp)
                    .background(Color(0xFF1E2230), RoundedCornerShape(6.dp))
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = MediathekRepository.channels.firstOrNull { item.channel.contains(it.id, ignoreCase = true) }?.label ?: item.channel,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (item.topic.isNotBlank() && item.topic != item.title) {
                    Text(item.topic, color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                item.description?.let {
                    Text(it, color = Muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 16.sp)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(horizontalAlignment = Alignment.End) {
                if (item.startMs > 0) Text(dateFormat.format(Date(item.startMs)), color = Muted, fontSize = 13.sp)
                Text(
                    listOfNotNull(
                        MediathekRepository.formatDuration(item.durationSec).takeIf { it.isNotEmpty() },
                        if (item.videoHd != null) "HD" else null,
                    ).joinToString(" · "),
                    color = Muted,
                    fontSize = 13.sp,
                )
                when {
                    progress?.finished == true -> Text("✓ gesehen", color = Color(0xFF81C784), fontSize = 12.sp)
                    progress?.resumable == true -> Text("noch ${progress.remainingMin} Min.", color = Color(0xFFFFB74D), fontSize = 12.sp)
                }
            }
        }
        if (progress?.resumable == true) ProgressBar(progress.progress, Modifier.align(Alignment.BottomCenter))
    }
}
