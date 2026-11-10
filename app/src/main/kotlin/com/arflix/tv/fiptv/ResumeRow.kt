package com.arflix.tv.fiptv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.arflix.tv.ui.theme.BackgroundCard

private val Muted = Color(0xFFA0A6B4)
private val ProgressColor = Color(0xFFE53935)

/**
 * fIPTV: Reihe „Weiterschauen“ (Anbieter-VOD, Mediathek).
 * OK = an der gespeicherten Stelle weiterspielen, OK lang = aus der Liste entfernen.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ResumeRow(
    entries: List<FiptvResume.Entry>,
    onPlay: (FiptvResume.Entry) -> Unit,
    onRemove: (FiptvResume.Entry) -> Unit,
    isTouch: Boolean = false,
    modifier: Modifier = Modifier,
) {
    if (entries.isEmpty()) return
    Column(modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("▶ Weiterschauen", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(10.dp))
            Text(
                if (isTouch) "lange tippen: entfernen" else "OK lang: entfernen",
                color = Muted.copy(alpha = 0.7f), fontSize = 11.sp,
            )
        }
        Spacer(Modifier.height(6.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 4.dp, horizontal = 2.dp),
        ) {
            items(entries, key = { it.url }) { e ->
                Surface(
                    onClick = { onPlay(e) },
                    onLongClick = { onRemove(e) },
                    modifier = (Modifier.width(250.dp).height(84.dp)).then(Modifier.fiptvTap({ onPlay(e) })),
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(10.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = BackgroundCard,
                        contentColor = Color.White,
                        focusedContainerColor = Color(0xFF2A2E3A),
                        focusedContentColor = Color.White,
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
                    border = ClickableSurfaceDefaults.border(
                        focusedBorder = Border(
                            border = androidx.compose.foundation.BorderStroke(2.dp, Color.White),
                            shape = RoundedCornerShape(10.dp),
                        )
                    ),
                ) {
                    Column(Modifier.fillMaxSize()) {
                        Row(Modifier.weight(1f).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (e.image != null) {
                                AsyncImage(
                                    model = e.image,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.width(44.dp).fillMaxHeight().clip(RoundedCornerShape(5.dp)),
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Column(Modifier.weight(1f)) {
                                Text(e.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val sub = listOfNotNull(
                                    e.subtitle.takeIf { it.isNotBlank() },
                                    if (e.kind == FiptvResume.Kind.EPISODE && e.season != null && e.episode != null) "S${e.season} E${e.episode}" else null,
                                ).joinToString(" · ")
                                if (sub.isNotEmpty()) Text(sub, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("noch ${e.remainingMin} Min.", color = Muted, fontSize = 12.sp, maxLines = 1)
                            }
                        }
                        ProgressBar(e.progress)
                    }
                }
            }
        }
    }
}

/** Dünner Fortschrittsbalken am unteren Rand. */
@Composable
fun ProgressBar(progress: Float, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = 0.15f))) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(ProgressColor))
    }
}
