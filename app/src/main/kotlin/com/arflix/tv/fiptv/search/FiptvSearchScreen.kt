package com.arflix.tv.fiptv.search

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.arflix.tv.data.model.IptvChannel
import com.arflix.tv.fiptv.FiptvResume
import com.arflix.tv.fiptv.fiptvTap
import com.arflix.tv.fiptv.mediathek.MediathekRepository
import com.arflix.tv.fiptv.reminders.FiptvReminder
import com.arflix.tv.fiptv.reminders.FiptvReminders
import com.arflix.tv.navigation.FiptvNav
import com.arflix.tv.ui.screens.search.rememberVoiceSearch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Bg = Color(0xFF0B0D12)
private val Panel = Color(0xFF1A1E27)

/**
 * fIPTV: „Suche über alles“ – ein Suchfeld für Live-TV-Programm, Sender, Anbieter-Filme/-Serien,
 * Mediathek und YouTube. Mit Sprachsuche, wenn das Gerät eine Spracherkennung hat.
 */
@Composable
fun FiptvSearchScreen(
    onTuneChannel: (channelId: String) -> Unit,
    onPlay: (entry: FiptvResume.Entry, startPositionMs: Long?) -> Unit,
    onOpenCatalogSearch: () -> Unit,
    viewModel: FiptvSearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val reminderKeys by FiptvReminders.keys.collectAsState()
    val fieldFocus = remember { FocusRequester() }
    val voice = rememberVoiceSearch(
        onPartial = { viewModel.setQuery(it) },
        onResult = { viewModel.setQuery(it, immediate = true) },
    )
    LaunchedEffect(Unit) {
        FiptvReminders.load(context)
        runCatching { fieldFocus.requestFocus() }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Bg),
        contentPadding = PaddingValues(top = 24.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item(key = "field") {
            Column(Modifier.padding(horizontal = 40.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        Modifier
                            .weight(1f)
                            .height(54.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Panel)
                            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 18.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (state.query.isEmpty()) {
                            Text(" Sender, Sendung, Film, Serie, YouTube …", color = Color.White.copy(alpha = 0.45f), fontSize = 18.sp)
                        }
                        BasicTextField(
                            value = state.query,
                            onValueChange = { viewModel.setQuery(it) },
                            singleLine = true,
                            textStyle = TextStyle(color = Color.White, fontSize = 20.sp),
                            cursorBrush = SolidColor(Color.White),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { viewModel.setQuery(state.query, immediate = true) }),
                            modifier = Modifier.fillMaxWidth().focusRequester(fieldFocus),
                        )
                    }
                    if (voice.isAvailable) {
                        Chip(if (voice.isListening) "Höre zu …" else "Sprechen", selected = voice.isListening) { voice.toggle() }
                    }
                    if (state.query.isNotEmpty()) Chip("✕", selected = false) { viewModel.setQuery("") }
                }
                Text(
                    if (voice.isAvailable) "Tipp: Auf „Sprechen“ drücken und den Titel sagen."
                    else "Tipp Fire TV: Im Suchfeld die Tastatur öffnen, dann die Mikrofon-Taste der Fernbedienung gedrückt halten und sprechen.",
                    color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        if (state.query.trim().length < 2) {
            item(key = "hint") {
                Column(Modifier.padding(horizontal = 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Durchsucht gleichzeitig: TV-Programm (jetzt und demnächst), Sender, Filme und Serien deines Anbieters, ARD/ZDF-Mediathek und YouTube.",
                        color = Color.White.copy(alpha = 0.75f), fontSize = 16.sp,
                    )
                    Chip("Film- & Serienkatalog durchsuchen (Streaming)", selected = false, onClick = onOpenCatalogSearch)
                }
            }
            return@LazyColumn
        }

        section("Im TV-Programm", state.epg, com.arflix.tv.fiptv.FiptvIcons.OnTv) { hits ->
            items(hits, key = { "${it.channel.id}|${it.program.startUtcMillis}" }) { hit ->
                val reminded = FiptvReminder.keyOf(hit.program.title, hit.program.startUtcMillis) in reminderKeys
                TextCard(
                    title = (if (reminded) "◉ " else "") + hit.program.title,
                    line1 = hit.channel.name,
                    line2 = if (hit.live) "● Läuft gerade – ansehen" else "${when_(hit.program.startUtcMillis)} · merken",
                    accent = if (hit.live) Color(0xFFE53935) else null,
                    image = hit.channel.logo,
                ) {
                    if (hit.live) onTuneChannel(hit.channel.id)
                    else {
                        val set = FiptvReminders.toggle(
                            context,
                            FiptvReminder(hit.channel.id, hit.channel.name, hit.program.title, hit.program.startUtcMillis, hit.program.endUtcMillis),
                        )
                        Toast.makeText(
                            context,
                            if (set) "Gemerkt: ${hit.program.title} – Hinweis kommt kurz vorher" else "Erinnerung entfernt",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            }
        }
        section("Sender", state.channels, com.arflix.tv.fiptv.FiptvIcons.LiveTv) { list ->
            items(list, key = { it.id }) { ch -> ChannelCard(ch) { onTuneChannel(ch.id) } }
        }
        section("Filme beim Anbieter", state.movies, com.arflix.tv.fiptv.FiptvIcons.Film) { list ->
            items(list, key = { "m${it.id}" }) { m ->
                PosterCard(m.name, m.poster) { viewModel.prepareMovie(m)?.let { (e, start) -> onPlay(e, start) } }
            }
        }
        section("Serien beim Anbieter", state.series, com.arflix.tv.fiptv.FiptvIcons.Film) { list ->
            items(list, key = { "s${it.id}" }) { s ->
                PosterCard(s.name, s.poster) {
                    FiptvSearchBridge.series = s
                    FiptvNav.open(FiptvNav.VOD)
                }
            }
        }
        section("Mediathek", state.mediathek, com.arflix.tv.fiptv.FiptvIcons.Mediathek) { list ->
            items(list, key = { "mt${it.id}" }) { item ->
                TextCard(
                    title = item.title,
                    line1 = listOf(MediathekRepository.channelLabel(item.channel), item.topic.takeIf { it != item.title }.orEmpty())
                        .filter { it.isNotBlank() }.joinToString(" · "),
                    line2 = "${SimpleDateFormat("dd.MM.yy", Locale.GERMANY).format(Date(item.startMs))} · ${item.durationSec / 60} Min.",
                    accent = Color(MediathekRepository.channelColor(item.channel)),
                    image = null,
                ) {
                    FiptvSearchBridge.mediathek = item
                    FiptvNav.open(FiptvNav.MEDIATHEK)
                }
            }
        }
        section("YouTube", state.youtube, com.arflix.tv.fiptv.FiptvIcons.VideoScreen) { list ->
            items(list, key = { "yt${it.id}" }) { v ->
                VideoCard(v.title, v.channelTitle, v.thumbnail) {
                    FiptvSearchBridge.youtube = v
                    FiptvNav.open(FiptvNav.YOUTUBE)
                }
            }
        }
        item(key = "catalog") {
            Box(Modifier.padding(horizontal = 40.dp)) {
                Chip("„${state.query.trim()}“ im Film- & Serienkatalog suchen", selected = false, onClick = onOpenCatalogSearch)
            }
        }
    }
}

private fun when_(start: Long): String {
    val d = Date(start)
    val today = SimpleDateFormat("yyyyMMdd", Locale.GERMANY)
    val day = when (today.format(d)) {
        today.format(Date()) -> "Heute"
        today.format(Date(System.currentTimeMillis() + 86_400_000L)) -> "Morgen"
        else -> SimpleDateFormat("EEE dd.MM.", Locale.GERMANY).format(d)
    }
    return "$day ${SimpleDateFormat("HH:mm", Locale.GERMANY).format(d)}"
}

private fun <T> androidx.compose.foundation.lazy.LazyListScope.section(
    title: String,
    section: Section<T>,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    content: androidx.compose.foundation.lazy.LazyListScope.(List<T>) -> Unit,
) {
    val items = section.items
    if (items != null && items.isEmpty() && section.error == null) return
    item(key = "h_$title") {
        Row(Modifier.padding(horizontal = 40.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                androidx.tv.material3.Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
            }
            Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(10.dp))
            when {
                items == null -> Text("sucht …", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp)
                section.error != null -> Text(section.error, color = Color(0xFFFF8A80), fontSize = 14.sp)
                else -> Text("${items.size}", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp)
            }
        }
    }
    if (!items.isNullOrEmpty()) {
        item(key = "r_$title") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 40.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) { content(items) }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CardSurface(width: Dp, height: Dp, onClick: () -> Unit, content: @Composable () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.width(width).height(height).fiptvTap(onClick),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Panel, contentColor = Color.White,
            focusedContainerColor = Panel, focusedContentColor = Color.White,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(border = BorderStroke(3.dp, Color.White), shape = RoundedCornerShape(10.dp)),
        ),
    ) { content() }
}

@Composable
private fun TextCard(title: String, line1: String, line2: String, accent: Color?, image: String?, onClick: () -> Unit) {
    CardSurface(260.dp, 112.dp, onClick) {
        Row(Modifier.fillMaxSize().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (image != null) {
                AsyncImage(model = image, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(48.dp))
                Spacer(Modifier.width(10.dp))
            } else if (accent != null) {
                Box(Modifier.width(4.dp).height(72.dp).background(accent, RoundedCornerShape(2.dp)))
                Spacer(Modifier.width(10.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (line1.isNotBlank()) Text(line1, fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(line2, fontSize = 12.sp, color = accent ?: Color.White.copy(alpha = 0.6f), maxLines = 1)
            }
        }
    }
}

@Composable
private fun ChannelCard(ch: IptvChannel, onClick: () -> Unit) {
    CardSurface(170.dp, 110.dp, onClick) {
        Column(Modifier.fillMaxSize().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            if (!ch.logo.isNullOrBlank()) AsyncImage(model = ch.logo, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(54.dp))
            Text(ch.name, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun PosterCard(title: String, poster: String?, onClick: () -> Unit) {
    CardSurface(130.dp, 220.dp, onClick) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().height(180.dp).background(Color(0xFF262B36))) {
                if (!poster.isNullOrBlank()) AsyncImage(model = poster, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Text(title, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(6.dp))
        }
    }
}

@Composable
private fun VideoCard(title: String, channel: String, thumb: String?, onClick: () -> Unit) {
    CardSurface(260.dp, 210.dp, onClick) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().height(146.dp).background(Color(0xFF262B36))) {
                if (!thumb.isNullOrBlank()) AsyncImage(model = thumb, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Column(Modifier.padding(8.dp)) {
                Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(channel, fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f), maxLines = 1)
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fiptvTap(onClick),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(24.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Color(0xFFE53935) else Color.White.copy(alpha = 0.12f),
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
        ),
    ) {
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
    }
}
