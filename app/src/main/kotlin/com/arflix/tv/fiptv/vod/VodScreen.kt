package com.arflix.tv.fiptv.vod

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage

private val Bg: Color get() = com.arflix.tv.ui.theme.BackgroundDark
private val Panel = Color(0xFF14161C)
private val Muted = Color(0xFFA0A6B4)
private val Accent = Color(0xFF4F7CFF)

/**
 * fIPTV: Filme und Serien des IPTV-Anbieters, getrennt von TMDB/Addons.
 * Links Kategorien des Anbieters, rechts Plakate. Serien öffnen Staffeln und Folgen.
 */
@Composable
fun VodScreen(
    onPlayMovie: (movie: VodMovie, url: String) -> Unit,
    onPlayEpisode: (series: VodSeries, episode: VodEpisode, url: String) -> Unit,
    viewModel: VodViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }

    BackHandler(enabled = state.openSeries != null) { viewModel.closeSeries() }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
            .padding(start = 32.dp, end = 24.dp, top = 20.dp)
    ) {
        // ---------- links: Umschalter und Kategorien ----------
        Column(modifier = Modifier.width(230.dp).fillMaxHeight()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Filme", !state.series, { viewModel.setSeries(false) }, Modifier.focusRequester(firstFocus))
                Pill("Serien", state.series, { viewModel.setSeries(true) })
            }
            if (state.accounts.size > 1) {
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    itemsIndexed(state.accounts) { i, a ->
                        Pill(a.name.ifBlank { "Anbieter ${i + 1}" }, i == state.accountIndex, { viewModel.selectAccount(i) })
                    }
                }
            } else state.account?.let {
                Text(it.name, color = Muted, fontSize = 12.sp, maxLines = 1, modifier = Modifier.padding(top = 6.dp, start = 4.dp))
            }
            Spacer(Modifier.height(10.dp))
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(state.categories, key = { it.id }) { c ->
                    CategoryRow(c.name, c.id == state.categoryId) { viewModel.selectCategory(c.id) }
                }
            }
        }
        Spacer(Modifier.width(20.dp))

        // ---------- rechts: Plakate oder Folgen ----------
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            val open = state.openSeries
            when {
                open != null -> EpisodesPanel(
                    state = state,
                    series = open,
                    onBack = { viewModel.closeSeries() },
                    onSeason = { viewModel.selectSeason(it) },
                    onPlay = { ep -> viewModel.episodeUrl(ep)?.let { onPlayEpisode(open, ep, it) } },
                )
                state.error != null -> Message("⚠ ${state.error}")
                state.loading -> Message("Wird geladen …")
                state.series && state.seriesList.isEmpty() -> Message("Keine Serien in dieser Kategorie.")
                !state.series && state.movies.isEmpty() -> Message("Keine Filme in dieser Kategorie.")
                state.series -> PosterGrid(state.seriesList.map { PosterData(it.id, it.name, it.poster, it.rating) }) { idx ->
                    viewModel.openSeries(state.seriesList[idx])
                }
                else -> PosterGrid(state.movies.map { PosterData(it.id, it.name, it.poster, it.rating) }) { idx ->
                    val m = state.movies[idx]
                    viewModel.movieUrl(m)?.let { onPlayMovie(m, it) }
                }
            }
        }
    }
}

private data class PosterData(val id: Int, val name: String, val image: String?, val rating: String?)

@Composable
private fun Message(text: String) {
    Text(text, color = Muted, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp))
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PosterGrid(items: List<PosterData>, onClick: (Int) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 118.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 32.dp, top = 4.dp, end = 4.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(items.size, key = { items[it].id }) { i ->
            val p = items[i]
            Column {
                Surface(
                    onClick = { onClick(i) },
                    modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f),
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                    colors = ClickableSurfaceDefaults.colors(containerColor = Panel, focusedContainerColor = Panel),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
                    border = ClickableSurfaceDefaults.border(
                        focusedBorder = Border(
                            border = androidx.compose.foundation.BorderStroke(2.dp, Color.White),
                            shape = RoundedCornerShape(8.dp),
                        )
                    ),
                ) {
                    if (p.image != null) {
                        AsyncImage(
                            model = p.image,
                            contentDescription = p.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                        )
                    } else {
                        Box(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
                            Text(p.name, color = Muted, fontSize = 12.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                Text(
                    p.name,
                    color = Color.White,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
                p.rating?.let { Text("★ $it", color = Muted, fontSize = 11.sp, maxLines = 1) }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun EpisodesPanel(
    state: VodUiState,
    series: VodSeries,
    onBack: () -> Unit,
    onSeason: (Int) -> Unit,
    onPlay: (VodEpisode) -> Unit,
) {
    val backFocus = remember { FocusRequester() }
    LaunchedEffect(series.id) { runCatching { backFocus.requestFocus() } }
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Pill("‹ Zurück", false, onBack, Modifier.focusRequester(backFocus))
            Spacer(Modifier.width(14.dp))
            Text(series.name, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        series.plot?.let {
            Text(it, color = Muted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        }
        Spacer(Modifier.height(10.dp))
        if (state.seasons.size > 1) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.seasons) { s -> Pill("Staffel $s", s == state.season, { onSeason(s) }) }
            }
            Spacer(Modifier.height(10.dp))
        }
        when {
            state.episodesLoading -> Message("Folgen werden geladen …")
            state.episodes.isEmpty() -> Message("Keine Folgen gefunden.")
            else -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = 32.dp),
            ) {
                items(state.seasonEpisodes, key = { it.id }) { ep ->
                    Surface(
                        onClick = { onPlay(ep) },
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
                            focusedBorder = Border(
                                border = androidx.compose.foundation.BorderStroke(2.dp, Accent),
                                shape = RoundedCornerShape(10.dp),
                            )
                        ),
                    ) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("S${ep.season} E${ep.episode}", color = Accent, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(80.dp))
                            Column(Modifier.weight(1f)) {
                                Text(ep.title, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                ep.plot?.let { Text(it, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            }
                            if (ep.durationSec > 0) Text("${(ep.durationSec + 30) / 60} Min.", color = Muted, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CategoryRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Accent.copy(alpha = 0.3f) else Color.Transparent,
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
    ) {
        Text(
            label,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
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
            containerColor = if (selected) Accent.copy(alpha = 0.35f) else Panel,
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
        )
    }
}
