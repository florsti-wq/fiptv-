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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.arflix.tv.data.model.Profile
import com.arflix.tv.data.repository.ProfileManager
import com.arflix.tv.data.repository.ProfileRepository
import com.arflix.tv.ui.components.TextInputModal
import com.arflix.tv.util.PinUtil
import com.arflix.tv.util.settingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Zählt im Kinderprofil die Abspielzeit und sperrt den Player, wenn das Tageslimit erreicht ist. */
@HiltViewModel
class KidsGuardViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val profileRepository: ProfileRepository,
    private val profileManager: ProfileManager,
) : ViewModel() {
    private var profile: Profile? = null
    private var limitMinutes = 0
    private val _locked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    init {
        viewModelScope.launch {
            profile = runCatching { profileRepository.activeProfile.first() }.getOrNull()
            val p = profile
            limitMinutes = if (p?.isKidsProfile == true) {
                runCatching {
                    context.settingsDataStore.data.first()[profileManager.profileStringKeyFor(p.id, FiptvKids.LIMIT_PREF)]?.toIntOrNull()
                }.getOrNull() ?: 0
            } else 0
            check()
        }
    }

    val hasParentPin: Boolean get() = !profile?.parentPin.isNullOrBlank()

    private fun check(): Boolean {
        val p = profile ?: return false
        val remaining = FiptvKids.remainingSeconds(context, p.id, limitMinutes)
        val over = remaining != null && remaining <= 0
        _locked.value = over
        return over
    }

    /** Abspielzeit melden; true = Limit erreicht */
    fun tick(seconds: Int): Boolean {
        val p = profile ?: return false
        if (!p.isKidsProfile || limitMinutes <= 0) return false
        FiptvKids.addSeconds(context, p.id, seconds)
        return check()
    }

    /** Eltern-PIN prüfen und 30 Minuten freigeben */
    fun unlock(pin: String): Boolean {
        val p = profile ?: return false
        if (!PinUtil.verifyPin(pin, p.parentPin)) return false
        FiptvKids.grantBonus(context, p.id)
        check()
        return true
    }
}

/** Im Player einbinden: zählt die Zeit und zeigt bei Bedarf die Sperre. */
@Composable
fun KidsGuard(player: Player, onBack: () -> Unit, viewModel: KidsGuardViewModel = hiltViewModel()) {
    val locked by viewModel.locked.collectAsStateWithLifecycle()
    LaunchedEffect(player) {
        while (true) {
            delay(15_000)
            if (player.isPlaying && viewModel.tick(15)) player.pause()
        }
    }
    LaunchedEffect(locked) { if (locked) player.pause() }
    if (locked) KidsTimeUpDialog(hasParentPin = viewModel.hasParentPin, onBack = onBack, onUnlock = { viewModel.unlock(it) })
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun KidsTimeUpDialog(hasParentPin: Boolean, onBack: () -> Unit, onUnlock: (String) -> Boolean) {
    var askPin by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val backFocus = remember { FocusRequester() }
    Dialog(
        onDismissRequest = onBack,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false, usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize().background(Color(0xF20B0D12)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = 620.dp).padding(32.dp)) {
                Text("⏰", fontSize = 64.sp)
                Spacer(Modifier.height(12.dp))
                Text("Für heute ist Schluss!", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(10.dp))
                Text(
                    "Die Fernsehzeit für heute ist aufgebraucht. Morgen geht es weiter.",
                    color = Color(0xFFCBD5E1), fontSize = 18.sp, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(26.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    GuardButton("Zurück", primary = true, modifier = Modifier.focusRequester(backFocus), onClick = onBack)
                    if (hasParentPin) GuardButton("Eltern: +${FiptvKids.BONUS_MINUTES} Min.", primary = false, onClick = { askPin = true; message = null })
                }
                if (!hasParentPin) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Zum Verlängern im Profil einen Eltern-PIN festlegen (Profil bearbeiten).",
                        color = Color(0xFF94A3B8), fontSize = 14.sp, textAlign = TextAlign.Center,
                    )
                }
                message?.let {
                    Spacer(Modifier.height(14.dp))
                    Text(it, color = Color(0xFFFF8A80), fontSize = 15.sp)
                }
            }
            TextInputModal(
                isVisible = askPin,
                title = "Eltern-PIN",
                hint = "PIN eingeben",
                isPassword = true,
                isNumeric = true,
                onConfirm = { pin ->
                    askPin = false
                    if (!onUnlock(pin)) message = "Falscher PIN."
                },
                onCancel = { askPin = false },
            )
        }
    }
    LaunchedEffect(Unit) { runCatching { backFocus.requestFocus() } }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun GuardButton(label: String, primary: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = (modifier).then(Modifier.fiptvTap(onClick)),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (primary) Color.White else Color.White.copy(alpha = 0.16f),
            contentColor = if (primary) Color.Black else Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(border = BorderStroke(3.dp, Color(0xFF22D3EE)), shape = RoundedCornerShape(8.dp)),
        ),
    ) {
        Text(label, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
    }
}
