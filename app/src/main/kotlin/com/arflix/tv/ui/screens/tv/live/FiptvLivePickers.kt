package com.arflix.tv.ui.screens.tv.live

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Text
import com.arflix.tv.data.model.IptvNowNext
import com.arflix.tv.fiptv.live.FiptvChannelCleanup
import com.arflix.tv.fiptv.live.FiptvCustomGroups
import com.arflix.tv.fiptv.live.FiptvEpgMap

private val PickerAccent = Color(0xFF5CE1E6)

@Composable
private fun PickerRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean? = null,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .background(if (focused) Color.White.copy(alpha = 0.14f) else Color.Transparent, RoundedCornerShape(8.dp))
            .border(if (focused) 2.dp else 0.dp, if (focused) PickerAccent else Color.Transparent, RoundedCornerShape(8.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (checked != null) {
            Text(if (checked) "✓" else "", color = PickerAccent, fontSize = 18.sp, modifier = Modifier.width(26.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, color = Color(0xFFA0A0A8), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun PickerFrame(title: String, hint: String?, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(560.dp)
                .heightIn(max = 620.dp)
                .background(Color(0xFF17181C), RoundedCornerShape(14.dp))
                .border(1.dp, PickerAccent.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                .padding(18.dp),
        ) {
            Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            if (hint != null) {
                Spacer(Modifier.padding(top = 4.dp))
                Text(hint, color = Color(0xFFA0A0A8), fontSize = 13.sp)
            }
            Spacer(Modifier.padding(top = 10.dp))
            content()
        }
    }
}

private fun nameKey(name: String): String =
    FiptvChannelCleanup.displayName(name).lowercase().filter { it.isLetterOrDigit() || it == ' ' }.trim()

/**
 * Programmdaten zuordnen: Liste von Sendern, die Programmdaten haben, die ähnlichsten zuerst.
 */
@Composable
internal fun FiptvEpgPicker(
    target: EnrichedChannel,
    candidates: Collection<EnrichedChannel>,
    nowNext: Map<String, IptvNowNext>,
    onDismiss: () -> Unit,
) {
    val targetKey = remember(target.id) { nameKey(target.name) }
    val targetWords = remember(targetKey) { targetKey.split(' ').filter { it.isNotBlank() }.toSet() }
    val list = remember(target.id, candidates.size, nowNext.size) {
        candidates.asSequence()
            .filter { it.id != target.id && (nowNext[it.id]?.now != null || !it.source.epgId.isNullOrBlank()) }
            .distinctBy { it.id }
            .map { ch ->
                val key = nameKey(ch.name)
                val words = key.split(' ').filter { it.isNotBlank() }.toSet()
                val score = when {
                    key == targetKey -> 100
                    key.startsWith(targetKey) || targetKey.startsWith(key) -> 70
                    else -> (words intersect targetWords).size * 20
                } + if (nowNext[ch.id]?.now != null) 5 else 0
                ch to score
            }
            .sortedWith(compareByDescending<Pair<EnrichedChannel, Int>> { it.second }.thenBy { it.first.name.lowercase() })
            .take(80)
            .map { it.first }
            .toList()
    }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    val current = FiptvEpgMap.sourceFor(target.id)
    PickerFrame(
        title = "Programmdaten für „" + FiptvChannelCleanup.displayName(target.name) + "“",
        hint = "Wähle einen Sender, dessen Programm zu diesem Sender passt. Die Zuordnung wird gespeichert.",
        onDismiss = onDismiss,
    ) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (current != null) {
                item {
                    PickerRow("Zuordnung entfernen", "Wieder die Programmdaten des Anbieters verwenden", focusRequester = first) {
                        FiptvEpgMap.set(target.id, null); onDismiss()
                    }
                }
            }
            if (list.isEmpty()) {
                item { Text("Keine passenden Sender mit Programmdaten gefunden.", color = Color(0xFFA0A0A8), fontSize = 14.sp) }
            }
            items(list, key = { it.id }) { ch ->
                val now = nowNext[ch.id]?.now?.title
                PickerRow(
                    title = ch.name,
                    subtitle = now?.let { "Jetzt: $it" } ?: "Programmdaten vorhanden",
                    checked = ch.id == current,
                    focusRequester = if (current == null && ch.id == list.first().id) first else null,
                ) {
                    FiptvEpgMap.set(target.id, ch.id); onDismiss()
                }
            }
        }
    }
}

private val PresetGroupNames = listOf("Kinder", "Nachrichten", "Sport", "Filme", "Serien", "Dokus", "Musik")

/**
 * Eigene Sendergruppen: Sender einer oder mehreren Gruppen hinzufügen, neue Gruppe anlegen,
 * Gruppe löschen.
 */
@Composable
internal fun FiptvGroupPicker(
    channel: EnrichedChannel,
    onDismiss: () -> Unit,
) {
    var mode by remember { mutableStateOf(0) } // 0 = Liste, 1 = neuer Name, 2 = löschen
    var nameInput by remember { mutableStateOf("") }
    val version = FiptvCustomGroups.version
    val groups = remember(version) { FiptvCustomGroups.all() }
    val first = remember { FocusRequester() }
    LaunchedEffect(mode) { runCatching { first.requestFocus() } }
    PickerFrame(
        title = when (mode) { 1 -> "Neue Gruppe"; 2 -> "Gruppe löschen"; else -> "Eigene Gruppen" },
        hint = when (mode) {
            1 -> "Wähle einen Namen oder gib einen eigenen ein. „" + FiptvChannelCleanup.displayName(channel.name) + "“ kommt gleich hinein."
            2 -> "Die Sender selbst bleiben erhalten, nur die Gruppe verschwindet."
            else -> "„" + FiptvChannelCleanup.displayName(channel.name) + "“ in Gruppen aufnehmen oder entfernen."
        },
        onDismiss = onDismiss,
    ) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            when (mode) {
                0 -> {
                    items(groups, key = { it.id }) { g ->
                        PickerRow(
                            title = g.name,
                            subtitle = "${g.channelIds.size} Sender",
                            checked = channel.id in g.channelIds,
                            focusRequester = if (g.id == groups.first().id) first else null,
                        ) { FiptvCustomGroups.toggle(g.id, channel.id) }
                    }
                    item {
                        PickerRow("+ Neue Gruppe anlegen", focusRequester = if (groups.isEmpty()) first else null) { mode = 1 }
                    }
                    if (groups.isNotEmpty()) {
                        item { PickerRow("Gruppe löschen …") { mode = 2 } }
                    }
                    item { PickerRow("Fertig") { onDismiss() } }
                }
                1 -> {
                    val taken = groups.map { it.name.lowercase() }.toSet()
                    val presets = PresetGroupNames.filter { it.lowercase() !in taken }
                    items(presets) { name ->
                        PickerRow(name, focusRequester = if (name == presets.first()) first else null) {
                            FiptvCustomGroups.create(name, channel.id); mode = 0
                        }
                    }
                    item {
                        Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            OutlinedTextField(
                                value = nameInput,
                                onValueChange = { nameInput = it.take(30) },
                                singleLine = true,
                                label = { androidx.compose.material3.Text("Eigener Name") },
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = {
                                    if (nameInput.isNotBlank()) { FiptvCustomGroups.create(nameInput, channel.id); nameInput = ""; mode = 0 }
                                }),
                                modifier = Modifier.fillMaxWidth().then(if (presets.isEmpty()) Modifier.focusRequester(first) else Modifier),
                            )
                            PickerRow("Anlegen") {
                                if (nameInput.isNotBlank()) { FiptvCustomGroups.create(nameInput, channel.id); nameInput = ""; mode = 0 }
                            }
                        }
                    }
                    item { PickerRow("Zurück") { mode = 0 } }
                }
                else -> {
                    items(groups, key = { it.id }) { g ->
                        PickerRow(
                            title = "„" + g.name + "“ löschen",
                            subtitle = "${g.channelIds.size} Sender",
                            focusRequester = if (g.id == groups.first().id) first else null,
                        ) { FiptvCustomGroups.delete(g.id); mode = 0 }
                    }
                    item { PickerRow("Zurück", focusRequester = if (groups.isEmpty()) first else null) { mode = 0 } }
                }
            }
        }
    }
}

/** Aufnahme starten: Dauer wählen. [onStart] bekommt das Ende als Zeitstempel. */
@Composable
internal fun FiptvRecordPicker(
    channel: EnrichedChannel,
    nowNext: IptvNowNext?,
    onStart: (endsAt: Long, title: String) -> Unit,
    onOpenLibrary: () -> Unit,
    onDismiss: () -> Unit,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    val name = FiptvChannelCleanup.displayName(channel.name)
    val now = System.currentTimeMillis()
    val current = nowNext?.now?.takeIf { it.endUtcMillis > now }
    val fmt = remember { java.text.SimpleDateFormat("HH:mm", java.util.Locale.GERMANY) }
    PickerFrame(
        title = "„$name“ aufnehmen",
        hint = "Die Aufnahme läuft auch im Hintergrund weiter und belegt solange eine Verbindung deines Abos.",
        onDismiss = onDismiss,
    ) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (current != null) {
                item {
                    PickerRow(
                        "Bis Sendungsende (" + fmt.format(java.util.Date(current.endUtcMillis + 5 * 60_000L)) + ")",
                        current.title + " · mit 5 Min. Puffer",
                        focusRequester = first,
                    ) { onStart(current.endUtcMillis + 5 * 60_000L, "$name – ${current.title}") }
                }
            }
            val options = listOf(30, 60, 120, 180)
            items(options) { min ->
                PickerRow(
                    if (min < 60) "$min Minuten" else "${min / 60} Stunde" + (if (min >= 120) "n" else ""),
                    "bis " + fmt.format(java.util.Date(now + min * 60_000L)),
                    focusRequester = if (current == null && min == options.first()) first else null,
                ) { onStart(now + min * 60_000L, name + (current?.let { " – ${it.title}" } ?: "")) }
            }
            item { PickerRow("Aufnahmen und Downloads ansehen") { onOpenLibrary() } }
            item { PickerRow("Abbrechen") { onDismiss() } }
        }
    }
}
