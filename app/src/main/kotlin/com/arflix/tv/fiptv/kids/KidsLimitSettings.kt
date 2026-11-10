package com.arflix.tv.fiptv.kids

import com.arflix.tv.fiptv.fiptvTap

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.arflix.tv.data.model.Profile
import com.arflix.tv.data.repository.CloudSyncRepository
import com.arflix.tv.data.repository.ProfileManager
import com.arflix.tv.data.repository.ProfileRepository
import com.arflix.tv.ui.components.TextInputModal
import com.arflix.tv.util.PinUtil
import com.arflix.tv.util.settingsDataStore
import androidx.datastore.preferences.core.edit
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Öffnet den Dialog „Kinder-Zeitlimit“ aus den Einstellungen (Fernbedienung und Touch). */
object KidsLimitBus {
    var visible by mutableStateOf(false)
    fun open() { visible = true }
}

data class KidsLimitRow(val profile: Profile, val limitMinutes: Int, val usedMinutes: Int)

data class KidsLimitState(
    val rows: List<KidsLimitRow> = emptyList(),
    /** Einstellungen werden gerade im Kinderprofil geöffnet → Eltern-PIN nötig */
    val needsPin: Boolean = false,
    val activeHasPin: Boolean = false,
    val unlocked: Boolean = false,
    val loaded: Boolean = false,
)

@HiltViewModel
class KidsLimitViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val profileRepository: ProfileRepository,
    private val profileManager: ProfileManager,
    private val cloudSyncRepository: CloudSyncRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(KidsLimitState())
    val state: StateFlow<KidsLimitState> = _state.asStateFlow()
    private var active: Profile? = null

    fun load() {
        viewModelScope.launch {
            active = runCatching { profileRepository.activeProfile.first() }.getOrNull()
            val profiles = runCatching { profileRepository.profiles.first() }.getOrDefault(emptyList())
            val prefs = context.settingsDataStore.data.first()
            val kids = profiles.filter { it.isKidsProfile }.map { p ->
                KidsLimitRow(
                    profile = p,
                    limitMinutes = prefs[profileManager.profileStringKeyFor(p.id, FiptvKids.LIMIT_PREF)]?.toIntOrNull() ?: 0,
                    usedMinutes = FiptvKids.usedSeconds(context, p.id) / 60,
                )
            }
            val inKids = active?.isKidsProfile == true
            _state.value = KidsLimitState(
                rows = kids,
                needsPin = inKids,
                activeHasPin = !active?.parentPin.isNullOrBlank(),
                unlocked = !inKids,
                loaded = true,
            )
        }
    }

    fun unlock(pin: String): Boolean {
        val ok = PinUtil.verifyPin(pin, active?.parentPin)
        if (ok) _state.update { it.copy(unlocked = true) }
        return ok
    }

    fun cycle(row: KidsLimitRow) {
        if (!_state.value.unlocked) return
        val next = FiptvKids.nextLimit(row.limitMinutes)
        viewModelScope.launch {
            context.settingsDataStore.edit { it[profileManager.profileStringKeyFor(row.profile.id, FiptvKids.LIMIT_PREF)] = next.toString() }
            _state.update { s -> s.copy(rows = s.rows.map { if (it.profile.id == row.profile.id) it.copy(limitMinutes = next) else it }) }
            runCatching { cloudSyncRepository.pushToCloud() }
        }
    }
}

/** Einmal in den Einstellungen einbinden; erscheint, wenn [KidsLimitBus.open] aufgerufen wird. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun KidsLimitDialogHost(viewModel: KidsLimitViewModel = hiltViewModel()) {
    if (!KidsLimitBus.visible) return
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pinMessage by remember { mutableStateOf<String?>(null) }
    var askPin by remember { mutableStateOf(false) }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(state.loaded, state.needsPin, state.unlocked) {
        if (state.loaded && state.needsPin && !state.unlocked && state.activeHasPin) askPin = true
    }
    val close = { KidsLimitBus.visible = false }

    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color(0xE60B0D12)), contentAlignment = Alignment.Center) {
            Column(
                Modifier.widthIn(max = 680.dp).fillMaxWidth().background(Color(0xFF15181F), RoundedCornerShape(16.dp)).padding(28.dp)
            ) {
                Text("Kinder-Zeitlimit", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Wie lange darf jedes Kinderprofil pro Tag schauen? Ist die Zeit um, stoppt die Wiedergabe – mit dem Eltern-PIN gibt es 30 Minuten extra.",
                    color = Color(0xFFB3B8C4), fontSize = 15.sp,
                )
                Spacer(Modifier.height(18.dp))
                when {
                    !state.loaded -> Text("Wird geladen …", color = Color(0xFFB3B8C4))
                    state.needsPin && !state.activeHasPin -> Text(
                        "Dieses Kinderprofil hat noch keinen Eltern-PIN. Bitte zuerst im Profil (Bearbeiten) einen Eltern-PIN festlegen oder die Einstellung in einem Erwachsenen-Profil ändern.",
                        color = Color(0xFFFFB74D), fontSize = 15.sp,
                    )
                    !state.unlocked -> Text("Bitte Eltern-PIN eingeben.", color = Color(0xFFB3B8C4))
                    state.rows.isEmpty() -> Text("Es gibt noch kein Kinderprofil. Lege eines unter Profile an (Profil hinzufügen → Kinderprofil).", color = Color(0xFFB3B8C4), fontSize = 15.sp)
                    else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        state.rows.forEachIndexed { index, row ->
                            Surface(
                                onClick = { viewModel.cycle(row) },
                                modifier = (Modifier.fillMaxWidth().then(if (index == 0) Modifier.focusRequester(firstFocus) else Modifier)).then(Modifier.fiptvTap({ viewModel.cycle(row) })),
                                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(10.dp)),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = Color.White.copy(alpha = 0.06f),
                                    contentColor = Color.White,
                                    focusedContainerColor = Color.White.copy(alpha = 0.16f),
                                    focusedContentColor = Color.White,
                                ),
                                border = ClickableSurfaceDefaults.border(
                                    focusedBorder = Border(border = BorderStroke(2.dp, Color(0xFF22D3EE)), shape = RoundedCornerShape(10.dp)),
                                ),
                                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
                            ) {
                                Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(row.profile.name, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                        Text("Heute auf diesem Gerät: ${row.usedMinutes} Min.", fontSize = 13.sp, color = Color(0xFFB3B8C4))
                                    }
                                    Text(FiptvKids.limitLabel(row.limitMinutes), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF22D3EE))
                                }
                            }
                        }
                        Text("OK auf einem Profil schaltet weiter: Kein Limit → 30 Min. → 45 Min. → 1 Std. → 1½ Std. → 2 Std. → 3 Std.", fontSize = 12.sp, color = Color(0xFF8A90A0))
                    }
                }
                pinMessage?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, color = Color(0xFFFF8A80), fontSize = 14.sp)
                }
                Spacer(Modifier.height(20.dp))
                GuardButton("Schließen", primary = state.rows.isEmpty() || !state.unlocked, onClick = close)
            }
            TextInputModal(
                isVisible = askPin,
                title = "Eltern-PIN",
                hint = "PIN eingeben",
                isPassword = true,
                isNumeric = true,
                onConfirm = { pin ->
                    askPin = false
                    pinMessage = if (viewModel.unlock(pin)) null else "Falscher PIN."
                },
                onCancel = { askPin = false },
            )
        }
    }
    LaunchedEffect(state.unlocked, state.rows.size) {
        if (state.unlocked && state.rows.isNotEmpty()) runCatching { firstFocus.requestFocus() }
    }
}
