package com.arflix.tv.fiptv

import android.content.Context
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.arflix.tv.data.repository.IptvAccountBadge
import com.arflix.tv.data.repository.IptvAccountInfo
import com.arflix.tv.data.repository.IptvAccountInfoParser
import com.arflix.tv.data.repository.IptvRepository
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale

/**
 * fIPTV: Abo-Erinnerung. Fragt einmal täglich beim Anbieter nach, wann das Abo endet
 * (Xtream `user_info.exp_date`, Stalker-Portal), und meldet sich höchstens einmal am Tag,
 * wenn es in 7 Tagen oder weniger abläuft oder schon abgelaufen ist.
 */
object FiptvSubscriptionReminder {
    private const val PREFS = "fiptv_sub_reminder"
    private const val REMIND_DAYS = 7
    private const val RECHECK_MS = 20L * 60 * 60 * 1000

    var message by mutableStateOf<String?>(null)
        private set

    fun dismiss() { message = null }

    suspend fun check(context: Context, repo: IptvRepository) {
        val config = runCatching { repo.observeConfig().first() }.getOrNull() ?: return
        val stored = runCatching { repo.observeAccountInfo().first() }.getOrDefault(emptyMap())
        val now = System.currentTimeMillis()
        val notes = mutableListOf<String>()

        suspend fun handle(id: String, name: String, fingerprint: String, fetch: suspend () -> IptvAccountInfo?) {
            var info = stored[id]?.takeIf { it.sourceFingerprint == fingerprint }
            if (info == null || now - (info.checkedAtMs ?: 0L) > RECHECK_MS) {
                val fresh = runCatching { fetch() }.getOrNull()
                if (fresh != null) {
                    runCatching { repo.saveAccountInfo(id, fresh) }
                    info = fresh
                }
            }
            info ?: return
            val label = name.ifBlank { "IPTV" }
            when (val b = IptvAccountInfoParser.badge(info, now)) {
                is IptvAccountBadge.Warning -> if (b.daysLeft <= REMIND_DAYS) {
                    val date = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY).format(Date(b.expiresAtMs))
                    notes += when (b.daysLeft) {
                        0 -> "„$label“ läuft heute ab ($date)."
                        1 -> "„$label“ läuft morgen ab ($date)."
                        else -> "„$label“ läuft in ${b.daysLeft} Tagen ab ($date)."
                    }
                }
                is IptvAccountBadge.Expired -> notes += "„$label“ ist abgelaufen" +
                    (b.expiredAtMs?.let { " (seit " + SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY).format(Date(it)) + ")" } ?: "") + "."
                else -> Unit
            }
        }

        for (pl in config.playlists.filter { it.enabled }) {
            handle(pl.id, pl.name, IptvAccountInfoParser.fingerprint(pl)) { repo.fetchAccountInfo(pl) }
        }
        for (portal in config.stalkerPortals.filter { it.enabled }) {
            handle(portal.id, portal.name, IptvAccountInfoParser.fingerprint(portal)) { repo.fetchAccountInfo(portal) }
        }
        if (notes.isEmpty()) return

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = LocalDate.now().toString()
        if (prefs.getString("shown", null) == today) return
        prefs.edit().putString("shown", today).apply()
        message = notes.joinToString("\n") +
            "\n\nDie genauen Angaben findest du unter Einstellungen → IPTV bei deinem Anbieter."
    }
}

@Composable
fun FiptvSubscriptionReminderDialog() {
    val msg = FiptvSubscriptionReminder.message ?: return
    AlertDialog(
        onDismissRequest = { FiptvSubscriptionReminder.dismiss() },
        title = { Text("Hinweis zu deinem IPTV-Abo") },
        text = { Text(msg) },
        confirmButton = { TextButton(onClick = { FiptvSubscriptionReminder.dismiss() }) { Text("OK") } },
    )
}
