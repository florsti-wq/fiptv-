package com.arflix.tv.fiptv.youtube

import com.arflix.tv.fiptv.fiptvTap

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
import coil.compose.AsyncImage
import com.arflix.tv.ui.components.TextInputModal
import com.arflix.tv.ui.components.YouTubeTrailerModal
import com.arflix.tv.ui.theme.BackgroundCard
import com.arflix.tv.ui.theme.BackgroundDark
import com.arflix.tv.util.LocalDeviceType

private val Muted = Color(0xFFA0A6B4)
private val YtRed = Color(0xFFFF3B30)

/**
 * fIPTV: YouTube-Bereich – Trends, Suche und gemerkte Kanäle je Profil.
 * Abgespielt wird mit dem offiziellen YouTube-Player (inkl. Werbung).
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun YouTubeScreen(
    onFullscreenChanged: (Boolean) -> Unit = {},
    viewModel: YouTubeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isTouch = LocalDeviceType.current.isTouchDevice()
    var searchOpen by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf<YtVideo?>(null) }
    var lastPlayedId by remember { mutableStateOf<String?>(null) }
    val firstFocus = remember { FocusRequester() }
    val returnFocus = remember { FocusRequester() }
    val gridState = rememberLazyGridState()

    // Fokus beim Öffnen auf die erste Schaltfläche – sonst hätte auf dem Fernseher nichts den Fokus
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    // Nach dem Abspielen zum zuletzt gesehenen Video zurück
    LaunchedEffect(playing) {
        if (playing == null && lastPlayedId != null) runCatching { returnFocus.requestFocus() }
    }
    DisposableEffect(playing != null) {
        onFullscreenChanged(playing != null)
        onDispose { if (playing != null) onFullscreenChanged(false) }
    }
    // Neue Ansicht → nach oben
    LaunchedEffect(state.view) { gridState.scrollToItem(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(start = if (isTouch) 16.dp else 40.dp, end = if (isTouch) 16.dp else 40.dp, top = 24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(width = 34.dp, height = 24.dp).background(YtRed, RoundedCornerShape(7.dp)),
                contentAlignment = Alignment.Center,
            ) { Text("▶", color = Color.White, fontSize = 13.sp) }
            Spacer(Modifier.width(10.dp))
            Text("YouTube", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(20.dp))
            val search = (state.view as? YtView.Search)?.query
            Pill(
                label = if (search == null) "🔍 Suchen" else "🔍 $search",
                selected = search != null,
                onClick = { searchOpen = true },
                modifier = Modifier.focusRequester(firstFocus),
            )
            if (search != null) {
                Spacer(Modifier.width(8.dp))
                Pill(label = "✕", selected = false, onClick = { viewModel.show(YtView.Trends) })
            }
        }

        if (!state.configured) {
            Spacer(Modifier.height(24.dp))
            Text(
                "YouTube ist noch nicht eingerichtet: Es fehlt der API-Schlüssel (GitHub-Secret „YOUTUBE_API_KEY“).",
                color = Muted, fontSize = 16.sp,
            )
            return@Column
        }

        Spacer(Modifier.height(12.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
            item {
                Pill("★ Meine Kanäle", state.view == YtView.MyChannels, { viewModel.show(YtView.MyChannels) })
            }
            item { Pill("🔥 Trends", state.view == YtView.Trends, { viewModel.show(YtView.Trends) }) }
            items(state.savedChannels, key = { it.id }) { ch ->
                Pill(
                    label = ch.title,
                    selected = (state.view as? YtView.Channel)?.id == ch.id,
                    onClick = { viewModel.openChannel(ch.id, ch.title) },
                    image = ch.thumbnail,
                )
            }
        }

        // Kopfzeile je Ansicht
        when (val view = state.view) {
            is YtView.Channel -> {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    state.openChannel?.thumbnail?.let {
                        AsyncImage(it, null, Modifier.size(36.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(state.openChannel?.title ?: view.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(16.dp))
                    Pill(
                        label = if (state.isOpenChannelSaved) "✓ Gemerkt – entfernen" else "☆ Kanal merken",
                        selected = state.isOpenChannelSaved,
                        onClick = { viewModel.toggleSaveOpenChannel() },
                    )
                }
            }
            is YtView.Search -> if (state.foundChannels.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                    item { Text("Kanäle:", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp)) }
                    items(state.foundChannels, key = { it.id }) { ch ->
                        Pill(ch.title, false, { viewModel.openChannel(ch.id, ch.title) }, image = ch.thumbnail)
                    }
                }
            }
            YtView.MyChannels -> if (state.savedChannels.isEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Noch keine Kanäle gemerkt. Such nach einem Kanal oder öffne ihn über ein Video " +
                        "(${if (isTouch) "lange tippen" else "OK lange drücken"}) und wähle „Kanal merken“.",
                    color = Muted, fontSize = 15.sp,
                )
            }
            YtView.Trends -> Unit
        }

        Spacer(Modifier.height(6.dp))
        Text(
            if (isTouch) "Tippen: abspielen · lange tippen: Kanal öffnen" else "OK: abspielen · OK lange drücken: Kanal öffnen",
            color = Muted.copy(alpha = 0.7f), fontSize = 12.sp,
        )
        Spacer(Modifier.height(6.dp))
        when {
            state.error != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⚠ ${state.error}", color = Color(0xFFFF8A80), fontSize = 15.sp)
                Spacer(Modifier.width(12.dp))
                Pill("Erneut versuchen", false, { viewModel.retry() })
            }
            state.loading && state.videos.isEmpty() -> Text("Wird geladen …", color = Muted, fontSize = 15.sp)
            !state.loading && state.videos.isEmpty() && state.view != YtView.MyChannels ->
                Text("Keine Videos gefunden.", color = Muted, fontSize = 15.sp)
        }

        LazyVerticalGrid(
            state = gridState,
            columns = if (isTouch) GridCells.Adaptive(170.dp) else GridCells.Fixed(4),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 6.dp, bottom = 40.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(state.videos, key = { _, v -> v.id }) { index, video ->
                // kurz vor dem Ende die nächste Seite laden
                if (index >= state.videos.size - 8) LaunchedEffect(state.videos.size) { viewModel.loadMore() }
                VideoCard(
                    video = video,
                    onClick = {
                        lastPlayedId = video.id
                        playing = video
                    },
                    onLongClick = { viewModel.openChannel(video.channelId, video.channelTitle) },
                    modifier = if (video.id == lastPlayedId) Modifier.focusRequester(returnFocus) else Modifier,
                )
            }
        }
    }

    TextInputModal(
        isVisible = searchOpen,
        title = "Auf YouTube suchen",
        hint = "Video oder Kanal",
        initialValue = (state.view as? YtView.Search)?.query.orEmpty(),
        onConfirm = {
            searchOpen = false
            viewModel.search(it)
        },
        onCancel = { searchOpen = false },
    )

    playing?.let { video ->
        YouTubeTrailerModal(
            youtubeKey = video.id,
            onClose = { playing = null },
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Pill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    image: String? = null,
) {
    Surface(
        onClick = onClick,
        modifier = (modifier).then(Modifier.fiptvTap(onClick)),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(50)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) YtRed.copy(alpha = 0.35f) else BackgroundCard,
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = if (image != null) 6.dp else 16.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        ) {
            if (image != null) {
                AsyncImage(image, null, Modifier.size(24.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = label,
                fontSize = 15.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(vertical = 2.dp),
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun VideoCard(
    video: YtVideo,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = (modifier.fillMaxWidth()).then(Modifier.fiptvTap(onClick)),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = Color.White,
            focusedContainerColor = BackgroundCard,
            focusedContentColor = Color.White,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(2.dp, Color.White),
                shape = RoundedCornerShape(10.dp),
            )
        ),
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF1A1A1A))
            ) {
                AsyncImage(
                    model = video.thumbnail,
                    contentDescription = video.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                val badge = if (video.isLive) "LIVE" else YouTubeRepository.formatDuration(video.durationSec)
                if (badge.isNotEmpty()) {
                    Text(
                        badge,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .background(if (video.isLive) YtRed else Color.Black.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
            }
            Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                Text(
                    video.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    listOf(video.channelTitle, YouTubeRepository.formatAge(video.publishedAt))
                        .filter { it.isNotBlank() }.joinToString(" · "),
                    color = Muted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
