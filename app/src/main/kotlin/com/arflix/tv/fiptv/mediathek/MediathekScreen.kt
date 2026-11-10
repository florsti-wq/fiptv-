package com.arflix.tv.fiptv.mediathek

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.arflix.tv.fiptv.FiptvResume
import com.arflix.tv.fiptv.ProgressBar
import com.arflix.tv.fiptv.ResumeRow
import com.arflix.tv.ui.components.TextInputModal
import com.arflix.tv.util.LocalDeviceType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Bg: Color get() = com.arflix.tv.ui.theme.BackgroundDark
private val Panel = Color(0xFF14161C)
private val Muted = Color(0xFFB3B8C4)
private val Accent = Color(0xFFE50914)
private val dateFormat = SimpleDateFormat("EE, dd.MM.", Locale.GERMANY)
private val dateTimeFormat = SimpleDateFormat("EEEE, dd.MM.yyyy · HH:mm", Locale.GERMANY)

private val TileWidth = 240.dp
private val TileHeight = 135.dp

/**
 * fIPTV: Mediatheken im Netflix-Stil – großes Titelbild der gewählten Sendung oben,
 * darunter waagerechte Reihen (Neu, Filme, Krimis, Dokus, Kinder, je Sender).
 * OK auf einer Kachel öffnet die Detailseite mit „Abspielen“ und weiteren Folgen.
 * Funktioniert mit Fernbedienung (Steuerkreuz + OK) und Touch.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MediathekScreen(
    /** Abspielen; zweiter Wert = Startposition (Weiterschauen) */
    onPlay: (entry: FiptvResume.Entry, startPositionMs: Long?) -> Unit,
    /** Kinderprofil: Kinder-Sender live abspielen */
    onPlayChannel: (com.arflix.tv.data.model.IptvChannel) -> Unit = {},
    viewModel: MediathekViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val resumeChanges by FiptvResume.changes.collectAsStateWithLifecycle()
    val resume = remember(resumeChanges) { viewModel.resumeEntries() }
    val isTouch = LocalDeviceType.current.isTouchDevice()
    var searchOpen by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf<MediathekItem?>(null) }
    var initialFocusDone by remember { mutableStateOf(false) }
    val firstTile = remember { FocusRequester() }
    val searchPill = remember { FocusRequester() }

    val play: (MediathekItem, Boolean) -> Unit = { item, fromStart ->
        viewModel.prepare(item, fromStart)?.let { (e, start) -> onPlay(e, start) }
    }

    val detail = state.detail
    if (detail != null) {
        BackHandler { viewModel.closeDetail() }
        MediathekDetailView(
            detail = detail,
            viewModel = viewModel,
            resumeChanges = resumeChanges,
            onPlay = play,
        )
        return
    }

    val heroItem = focused ?: state.rows.firstOrNull { it.items.isNotEmpty() }?.items?.firstOrNull()
    val heroArt by produceState<MediathekArt?>(initialValue = heroItem?.let { viewModel.cachedArt(it) }, heroItem?.id) {
        value = heroItem?.let { viewModel.cachedArt(it) ?: viewModel.art(it) }
    }

    Box(Modifier.fillMaxSize().background(Bg)) {
        if (state.search.isBlank()) HeroBackdrop(heroArt?.hero)

        Column(Modifier.fillMaxSize().padding(top = 20.dp)) {
            // Kopfzeile
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 40.dp, end = 40.dp)) {
                Text(if (state.kids) "Kinder-Mediathek" else "Mediathek", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(20.dp))
                Pill(
                    label = if (state.search.isBlank()) "🔍 Suchen" else "🔍 ${state.search}",
                    selected = state.search.isNotBlank(),
                    onClick = { searchOpen = true },
                    modifier = Modifier.focusRequester(searchPill),
                )
                if (state.search.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Pill(label = "✕ Suche beenden", selected = false, onClick = { viewModel.setSearch("") })
                }
                Spacer(Modifier.width(8.dp))
                Pill(
                    label = if (state.longOnly) "Nur ganze Sendungen" else "Auch kurze Clips",
                    selected = state.longOnly,
                    onClick = { viewModel.toggleLongOnly() },
                )
            }

            if (state.search.isNotBlank()) {
                SearchGrid(state, viewModel, resumeChanges, onOpen = { viewModel.openDetail(it) })
            } else {
                HeroInfo(heroItem, Modifier.padding(start = 40.dp, top = 18.dp).height(196.dp))
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 40.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    if (state.kidsChannels.isNotEmpty()) {
                        item(key = "kids_live") {
                            KidsChannelRow(state.kidsChannels, onPlayChannel)
                        }
                    }
                    if (resume.isNotEmpty()) {
                        item(key = "resume") {
                            ResumeRow(
                                entries = resume,
                                onPlay = { e -> onPlay(e, e.positionMs) },
                                onRemove = { e -> viewModel.removeResume(e) },
                                isTouch = isTouch,
                                modifier = Modifier.padding(start = 40.dp),
                            )
                        }
                    }
                    itemsIndexed(state.rows, key = { _, row -> row.def.key }) { rowIndex, row ->
                        LaunchedEffect(row.def.key) { viewModel.ensureRow(row.def.key) }
                        val isFirstFilled = row.items.isNotEmpty() &&
                            state.rows.indexOfFirst { it.items.isNotEmpty() } == rowIndex
                        if (isFirstFilled && !initialFocusDone) {
                            LaunchedEffect(Unit) {
                                runCatching { firstTile.requestFocus() }
                                initialFocusDone = true
                            }
                        }
                        MediathekRowView(
                            row = row,
                            viewModel = viewModel,
                            resumeChanges = resumeChanges,
                            firstTileModifier = if (isFirstFilled) Modifier.focusRequester(firstTile) else Modifier,
                            onFocus = { focused = it },
                            onOpen = { viewModel.openDetail(it) },
                        )
                    }
                }
            }
        }
    }

    // Falls noch nichts geladen ist, wenigstens die Suche fokussierbar machen
    LaunchedEffect(Unit) { if (!initialFocusDone) runCatching { searchPill.requestFocus() } }

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

/** großes Hintergrundbild oben rechts, zu den Rändern ausgeblendet */
@Composable
private fun BoxScope.HeroBackdrop(url: String?) {
    Crossfade(
        targetState = url,
        animationSpec = tween(450),
        modifier = Modifier.align(Alignment.TopEnd).fillMaxWidth(0.72f).fillMaxHeight(0.66f),
        label = "mediathek-hero",
    ) { image ->
        Box(Modifier.fillMaxSize()) {
            if (image != null) {
                AsyncImage(model = image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Bg, Bg.copy(alpha = 0.55f), Color.Transparent), endX = 900f)))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Bg.copy(alpha = 0.6f), Bg))))
        }
    }
}

@Composable
private fun HeroInfo(item: MediathekItem?, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(0.55f)) {
        if (item == null) {
            Text("ARD, ZDF, arte, 3sat & Co.", color = Muted, fontSize = 16.sp)
            Spacer(Modifier.height(6.dp))
            Text("Wird geladen …", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChannelBadge(item.channel)
            if (item.topic.isNotBlank() && item.topic != item.title) {
                Spacer(Modifier.width(10.dp))
                Text(item.topic.uppercase(), color = Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(item.title, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 34.sp)
        Spacer(Modifier.height(6.dp))
        Text(metaLine(item, long = true), color = Color(0xFFD7DAE0), fontSize = 14.sp, maxLines = 1)
        item.description?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = Muted, fontSize = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 19.sp)
        }
    }
}

private fun metaLine(item: MediathekItem, long: Boolean): String = listOfNotNull(
    if (item.startMs > 0) (if (long) dateTimeFormat else dateFormat).format(Date(item.startMs)) else null,
    MediathekRepository.formatDuration(item.durationSec).takeIf { it.isNotEmpty() },
    if (item.videoHd != null) "HD" else null,
    if (long && item.subtitleUrl != null) "UT" else null,
).joinToString("  ·  ")

@Composable
private fun ChannelBadge(channel: String) {
    Box(
        Modifier
            .background(Color(MediathekRepository.channelColor(channel)), RoundedCornerShape(4.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text(MediathekRepository.channelLabel(channel), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun MediathekRowView(
    row: MediathekRow,
    viewModel: MediathekViewModel,
    resumeChanges: Long,
    firstTileModifier: Modifier,
    onFocus: (MediathekItem) -> Unit,
    onOpen: (MediathekItem) -> Unit,
) {
    // leere Reihen ausblenden
    if (row.loaded && row.items.isEmpty() && row.error == null) return
    Column {
        Text(
            row.def.title,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 40.dp, bottom = 6.dp),
        )
        when {
            row.error != null -> Row(Modifier.padding(start = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("⚠ ${row.error}", color = Color(0xFFFF8A80), fontSize = 13.sp)
                Spacer(Modifier.width(10.dp))
                Pill("Erneut versuchen", selected = false, onClick = { viewModel.retryRow(row.def.key) })
            }
            row.items.isEmpty() -> LazyRow(
                contentPadding = PaddingValues(horizontal = 40.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(5) { Box(Modifier.width(TileWidth).height(TileHeight).background(Panel, RoundedCornerShape(8.dp))) }
            }
            else -> LazyRow(
                contentPadding = PaddingValues(horizontal = 40.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(row.items, key = { _, it -> it.id }) { index, item ->
                    MediathekTile(
                        item = item,
                        viewModel = viewModel,
                        resumeChanges = resumeChanges,
                        modifier = if (index == 0) firstTileModifier else Modifier,
                        onFocus = onFocus,
                        onClick = { onOpen(item) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun MediathekTile(
    item: MediathekItem,
    viewModel: MediathekViewModel,
    resumeChanges: Long,
    modifier: Modifier = Modifier,
    width: androidx.compose.ui.unit.Dp = TileWidth,
    onFocus: (MediathekItem) -> Unit = {},
    onClick: () -> Unit,
) {
    val art by produceState(initialValue = viewModel.cachedArt(item), item.id) {
        if (value == null) value = viewModel.art(item)
    }
    val progress = remember(item.id, resumeChanges) { viewModel.progressOf(item) }
    val color = Color(MediathekRepository.channelColor(item.channel))
    Surface(
        onClick = onClick,
        modifier = modifier
            .width(width)
            .height(width * 9f / 16f)
            .onFocusChanged { if (it.isFocused) onFocus(item) },
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Panel,
            contentColor = Color.White,
            focusedContainerColor = Panel,
            focusedContentColor = Color.White,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(border = BorderStroke(3.dp, Color.White), shape = RoundedCornerShape(8.dp)),
        ),
    ) {
        val image = art?.tile
        if (image != null) {
            AsyncImage(model = image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            // Kachel ohne Bild: Senderfarbe mit großem Sendungsnamen
            Box(
                Modifier.fillMaxSize().background(Brush.linearGradient(listOf(color, color.copy(alpha = 0.55f), Color(0xFF0C0D11))))
            ) {
                Text(
                    text = if (MediathekRepository.isContainerTopic(item.topic)) item.title else item.topic.ifBlank { item.title },
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 22.sp,
                    modifier = Modifier.align(Alignment.TopStart).padding(start = 12.dp, end = 12.dp, top = 30.dp),
                )
            }
        }
        // Senderlogo oben links
        Box(Modifier.align(Alignment.TopStart).padding(8.dp)) { ChannelBadge(item.channel) }
        // Titel unten auf dunklem Verlauf
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
                .padding(start = 10.dp, end = 10.dp, top = 18.dp, bottom = if (progress?.resumable == true) 7.dp else 6.dp)
        ) {
            Text(item.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                when {
                    progress?.finished == true -> "✓ gesehen"
                    progress?.resumable == true -> "noch ${progress.remainingMin} Min."
                    else -> metaLine(item, long = false)
                },
                color = when {
                    progress?.finished == true -> Color(0xFF81C784)
                    progress?.resumable == true -> Color(0xFFFFB74D)
                    else -> Muted
                },
                fontSize = 11.sp,
                maxLines = 1,
            )
        }
        if (progress?.resumable == true) ProgressBar(progress.progress, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun SearchGrid(
    state: MediathekUiState,
    viewModel: MediathekViewModel,
    resumeChanges: Long,
    onOpen: (MediathekItem) -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(start = 40.dp, end = 40.dp, top = 16.dp)) {
        val total = state.searchTotal
        Text(
            when {
                state.searchError != null -> "⚠ ${state.searchError}"
                state.searchLoading && state.searchItems.isEmpty() -> "Wird gesucht …"
                state.searchItems.isEmpty() -> "Keine Sendungen gefunden."
                total != null -> "$total Treffer"
                else -> ""
            },
            color = if (state.searchError != null) Color(0xFFFF8A80) else Muted,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(10.dp))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(220.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 6.dp, bottom = 40.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            gridItemsIndexed(state.searchItems, key = { _, it -> it.id }) { index, item ->
                if (index >= state.searchItems.size - 8) LaunchedEffect(state.searchItems.size) { viewModel.loadMoreSearch() }
                MediathekTile(item = item, viewModel = viewModel, resumeChanges = resumeChanges, width = 220.dp, onClick = { onOpen(item) })
            }
        }
    }
}

/** Detailseite: großes Bild, Beschreibung, Abspielen und weitere Folgen derselben Sendung */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun MediathekDetailView(
    detail: MediathekDetail,
    viewModel: MediathekViewModel,
    resumeChanges: Long,
    onPlay: (MediathekItem, Boolean) -> Unit,
) {
    val item = detail.item
    val art by produceState(initialValue = viewModel.cachedArt(item), item.id) {
        if (value == null) value = viewModel.art(item)
    }
    val progress = remember(item.id, resumeChanges) { viewModel.progressOf(item) }
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(item.id) { runCatching { playFocus.requestFocus() } }

    Box(Modifier.fillMaxSize().background(Bg)) {
        HeroBackdrop(art?.hero)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 48.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "info") {
                Column(Modifier.padding(start = 48.dp).fillMaxWidth(0.6f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ChannelBadge(item.channel)
                        if (item.topic.isNotBlank() && item.topic != item.title) {
                            Spacer(Modifier.width(10.dp))
                            Text(item.topic.uppercase(), color = Muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(item.title, color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 40.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(metaLine(item, long = true), color = Color(0xFFD7DAE0), fontSize = 15.sp)
                    item.description?.let {
                        Spacer(Modifier.height(12.dp))
                        Text(it, color = Muted, fontSize = 15.sp, maxLines = 6, overflow = TextOverflow.Ellipsis, lineHeight = 21.sp)
                    }
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        BigButton(
                            label = if (progress?.resumable == true) "▶  Weiterschauen (noch ${progress.remainingMin} Min.)" else "▶  Abspielen",
                            primary = true,
                            modifier = Modifier.focusRequester(playFocus),
                            onClick = { onPlay(item, false) },
                        )
                        if (progress?.resumable == true || progress?.finished == true) {
                            BigButton(label = "↺  Von vorn", primary = false, onClick = { onPlay(item, true) })
                        }
                        BigButton(label = "Zurück", primary = false, onClick = { viewModel.closeDetail() })
                    }
                    if (progress?.resumable == true) {
                        Spacer(Modifier.height(10.dp))
                        Box(Modifier.width(320.dp)) { ProgressBar(progress.progress) }
                    }
                }
            }
            val others = detail.episodes.filter { it.id != item.id }
            if (detail.loading || others.isNotEmpty()) {
                item(key = "episodes") {
                    Column(Modifier.padding(top = 24.dp)) {
                        Text(
                            if (detail.loading) "Weitere Folgen werden geladen …" else "Weitere Folgen: ${item.topic}",
                            color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 48.dp, bottom = 6.dp),
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(others, key = { it.id }) { ep ->
                                MediathekTile(item = ep, viewModel = viewModel, resumeChanges = resumeChanges, onClick = { viewModel.openDetail(ep) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun BigButton(label: String, primary: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(6.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (primary) Color.White else Color.White.copy(alpha = 0.18f),
            contentColor = if (primary) Color.Black else Color.White,
            focusedContainerColor = if (primary) Color.White else Color.White.copy(alpha = 0.9f),
            focusedContentColor = Color.Black,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(border = BorderStroke(3.dp, Accent), shape = RoundedCornerShape(6.dp)),
        ),
    ) {
        Text(label, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.padding(horizontal = 22.dp, vertical = 11.dp))
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Pill(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(50)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Color.White.copy(alpha = 0.22f) else Color.Black.copy(alpha = 0.45f),
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


/** fIPTV: Kinder-Sender live (nur im Kinderprofil) */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun KidsChannelRow(channels: List<com.arflix.tv.data.model.IptvChannel>, onPlay: (com.arflix.tv.data.model.IptvChannel) -> Unit) {
    Column {
        Text(
            "📺 Kinder-Sender live",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 40.dp, bottom = 6.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 40.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(channels, key = { it.id }) { ch ->
                Surface(
                    onClick = { onPlay(ch) },
                    modifier = Modifier.width(170.dp).height(110.dp),
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color(0xFF1E2230),
                        contentColor = Color.White,
                        focusedContainerColor = Color(0xFF2B3145),
                        focusedContentColor = Color.White,
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
                    border = ClickableSurfaceDefaults.border(
                        focusedBorder = Border(border = BorderStroke(3.dp, Color(0xFFFFD54F)), shape = RoundedCornerShape(12.dp)),
                    ),
                ) {
                    Column(
                        Modifier.fillMaxSize().padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        if (!ch.logo.isNullOrBlank()) {
                            AsyncImage(model = ch.logo, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.height(54.dp).fillMaxWidth())
                            Spacer(Modifier.height(6.dp))
                        }
                        Text(ch.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
