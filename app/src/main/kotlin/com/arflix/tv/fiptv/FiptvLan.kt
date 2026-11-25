@file:Suppress("UnsafeOptInUsageError")

package com.arflix.tv.fiptv

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Text
import com.arflix.tv.util.DeviceIpAddress
import com.arflix.tv.util.QrCodeGenerator
import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject
import java.io.File
import java.security.SecureRandom

/**
 * fIPTV: Verbindung vom Handy/PC im Heimnetz (Idee aus OwnTV, eigene Umsetzung).
 * Der Fernseher zeigt einen QR-Code; die Seite dahinter kann
 *  - eine Quelle (Xtream, M3U-Link, Stalker) eintragen – getippt wird am Handy statt mit der Fernbedienung,
 *  - den letzten Stream-Bericht anzeigen.
 * Jede Anfrage braucht den Einmal-Schlüssel aus dem QR-Code. Der Server endet nach 10 Minuten
 * oder wenn das Fenster geschlossen wird.
 */
object FiptvLan {
    data class Source(
        val type: String, // xtream | m3u | stalker
        val name: String,
        val server: String,
        val user: String,
        val pass: String,
        val epg: String,
        val mac: String,
    )

    data class State(val url: String, val qr: Bitmap?, val allowSource: Boolean, val message: String? = null)

    var state by mutableStateOf<State?>(null)
        private set

    /** Einstellungen → „Quelle per Handy oder PC hinzufügen“ wurde gewählt (wird von der Einstellungsseite abgeholt). */
    var setupRequested by mutableStateOf(false)

    private var server: Server? = null
    private val main = Handler(Looper.getMainLooper())
    private val stopper = Runnable { stop() }

    /** [onSource] = null → nur Bericht ansehen. */
    fun start(context: Context, onSource: ((Source) -> Unit)?) {
        stop()
        val token = (1..10).map { "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789"[SecureRandom().nextInt(57)] }.joinToString("")
        val report = com.arflix.tv.fiptv.player.FiptvStreamReport.reportFile(context.applicationContext)
        var started: Server? = null
        for (port in 8197 until 8207) {
            val s = Server(port, token, report, onSource?.let { cb -> { src: Source -> main.post { cb(src); state = state?.copy(message = "„${src.name.ifBlank { "Quelle" }}“ wurde hinzugefügt – Senderliste wird geladen") } } })
            if (runCatching { s.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false) }.isSuccess) { started = s; break }
        }
        val s = started ?: run { state = State("", null, onSource != null, "Konnte keinen freien Netzwerk-Anschluss öffnen"); return }
        server = s
        val ip = DeviceIpAddress.get(context) ?: "Gerät-IP"
        val path = if (onSource != null) "/" else "/bericht"
        val url = "http://$ip:${s.listeningPort}$path?t=$token"
        state = State(url, runCatching { QrCodeGenerator.generate(url, 512) }.getOrNull(), onSource != null)
        main.postDelayed(stopper, 10 * 60_000L)
    }

    fun stop() {
        main.removeCallbacks(stopper)
        server?.let { runCatching { it.stop() } }
        server = null
        state = null
    }

    private class Server(port: Int, private val token: String, private val report: File, private val onSource: ((Source) -> Unit)?) : NanoHTTPD(port) {
        override fun serve(session: IHTTPSession): Response {
            val supplied = session.parameters["t"]?.firstOrNull()
            if (session.method == Method.GET && supplied != token) return text(Response.Status.FORBIDDEN, "Ungültiger oder abgelaufener Code – bitte den QR-Code am Fernseher neu öffnen.")
            return when {
                session.method == Method.GET && session.uri == "/" -> html(page(onSource != null, report.exists()))
                session.method == Method.GET && session.uri == "/bericht" ->
                    if (report.exists()) newFixedLengthResponse(Response.Status.OK, "text/plain; charset=utf-8", report.readText())
                        .apply { addHeader("Content-Disposition", "inline; filename=\"fiptv-stream-bericht.txt\"") }
                    else text(Response.Status.NOT_FOUND, "Noch kein Stream-Bericht vorhanden. Im Live TV: Sendermenü → „Stream-Problem melden“.")
                session.method == Method.POST && session.uri == "/api/source" && onSource != null -> handleSource(session)
                else -> text(Response.Status.NOT_FOUND, "Nicht gefunden")
            }
        }

        private fun handleSource(session: IHTTPSession): Response {
            val body = HashMap<String, String>().also { runCatching { session.parseBody(it) } }["postData"].orEmpty()
            val o = runCatching { JSONObject(body) }.getOrNull() ?: return json(Response.Status.BAD_REQUEST, "Ungültige Daten")
            if (o.optString("t") != token) return json(Response.Status.FORBIDDEN, "Code abgelaufen")
            val src = Source(
                type = o.optString("type"), name = o.optString("name").trim().take(60),
                server = o.optString("server").trim(), user = o.optString("user").trim(), pass = o.optString("pass").trim(),
                epg = o.optString("epg").trim(), mac = o.optString("mac").trim().uppercase(),
            )
            val problem = when (src.type) {
                "xtream" -> if (!src.server.startsWith("http")) "Server-Adresse muss mit http:// oder https:// beginnen" else if (src.user.isBlank() || src.pass.isBlank()) "Benutzername und Passwort fehlen" else null
                "m3u" -> if (!src.server.startsWith("http")) "Der M3U-Link muss mit http:// oder https:// beginnen" else null
                "stalker" -> if (!src.server.startsWith("http")) "Portal-Adresse muss mit http:// oder https:// beginnen" else if (!Regex("^([0-9A-F]{2}:){5}[0-9A-F]{2}$").matches(src.mac)) "MAC-Adresse im Format 00:1A:79:XX:XX:XX" else null
                else -> "Unbekannte Art"
            }
            if (problem != null) return json(Response.Status.BAD_REQUEST, problem)
            onSource?.invoke(src)
            return json(Response.Status.OK, "ok")
        }

        private fun json(status: Response.Status, msg: String) =
            newFixedLengthResponse(status, "application/json", JSONObject().put("message", msg).toString())
        private fun text(status: Response.Status, msg: String) = newFixedLengthResponse(status, "text/plain; charset=utf-8", msg)
        private fun html(s: String) = newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", s)

        private fun page(allowSource: Boolean, hasReport: Boolean): String {
            val form = if (!allowSource) "" else """
<h2>Quelle hinzufügen</h2>
<div class="tabs"><label><input type="radio" name="type" value="xtream" checked> Xtream</label><label><input type="radio" name="type" value="m3u"> M3U-Link</label><label><input type="radio" name="type" value="stalker"> Stalker (MAC)</label></div>
<form id="f" autocomplete="off">
<label>Name (frei wählbar)<input name="name" placeholder="z. B. Mein Anbieter"></label>
<label><span id="lserver">Server-Adresse</span><input name="server" inputmode="url" placeholder="http://server.beispiel:8080" required></label>
<div class="x"><label>Benutzername<input name="user" autocapitalize="none"></label><label>Passwort<input name="pass" type="password"></label></div>
<div class="s" hidden><label>MAC-Adresse<input name="mac" placeholder="00:1A:79:XX:XX:XX" autocapitalize="characters"></label></div>
<div class="m" hidden><label>Programm-Link (EPG, optional)<input name="epg" inputmode="url"></label></div>
<button>Am Fernseher hinzufügen</button><p id="msg"></p></form>"""
            val reportLink = if (hasReport) "<p><a href=\"/bericht?t=$token\">Letzten Stream-Bericht ansehen</a></p>" else ""
            return """<!doctype html><html lang="de"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>fIPTV</title>
<style>body{font:16px system-ui,sans-serif;background:#101216;color:#eee;margin:0;padding:22px;max-width:520px}h1{font-size:22px}h2{font-size:18px;margin-top:22px}
label{display:block;margin:10px 0 4px;color:#bbb;font-size:14px}input{display:block;width:100%;box-sizing:border-box;margin-top:4px;padding:11px;border-radius:9px;border:1px solid #444;background:#1b1e24;color:#fff;font-size:16px}
.tabs{display:flex;gap:14px;flex-wrap:wrap}.tabs label{display:flex;gap:6px;align-items:center;color:#eee}.tabs input{width:auto;margin:0}
button{margin-top:16px;width:100%;padding:13px;border:0;border-radius:10px;background:#5ce1e6;color:#062024;font-weight:700;font-size:16px}#msg{min-height:1.4em}a{color:#5ce1e6}small{color:#999}</style></head>
<body><h1>fIPTV</h1>$form$reportLink<p><small>Diese Seite funktioniert nur im eigenen Heimnetz und nur, solange das Fenster am Fernseher offen ist.</small></p>
<script>
const T=${JSONObject.quote(token)};const f=document.getElementById('f');
function sync(){const t=(document.querySelector('input[name=type]:checked')||{}).value;if(!f)return;
f.querySelector('.x').hidden=t!=='xtream';f.querySelector('.s').hidden=t!=='stalker';f.querySelector('.m').hidden=t!=='m3u';
document.getElementById('lserver').textContent=t==='m3u'?'M3U-Link':t==='stalker'?'Portal-Adresse':'Server-Adresse';}
document.querySelectorAll('input[name=type]').forEach(r=>r.addEventListener('change',sync));sync();
if(f)f.addEventListener('submit',async e=>{e.preventDefault();const d=Object.fromEntries(new FormData(f));d.type=document.querySelector('input[name=type]:checked').value;d.t=T;
const m=document.getElementById('msg');m.textContent='Wird gesendet …';
try{const r=await fetch('/api/source',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(d)});const j=await r.json();
m.textContent=r.ok?'✓ Hinzugefügt – schau auf den Fernseher.':'✗ '+j.message;if(r.ok)f.reset();}catch(x){m.textContent='✗ Fernseher nicht erreichbar';}});
</script></body></html>"""
        }
    }
}

/** Fenster mit QR-Code am Fernseher. */
@Composable
fun FiptvLanOverlay() {
    val st = FiptvLan.state ?: return
    Dialog(onDismissRequest = { FiptvLan.stop() }) {
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        Column(
            modifier = Modifier
                .width(560.dp)
                .background(Color(0xFF17181C), RoundedCornerShape(14.dp))
                .border(1.dp, Color(0xFF5CE1E6).copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(if (st.allowSource) "Quelle per Handy oder PC hinzufügen" else "Stream-Bericht am Handy oder PC öffnen",
                color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(
                if (st.allowSource) "QR-Code mit der Handy-Kamera scannen (gleiches WLAN) oder die Adresse am PC eingeben. Zugangsdaten tippst du dort ein."
                else "QR-Code scannen oder Adresse am PC öffnen – dort kannst du den Bericht kopieren und mir schicken.",
                color = Color(0xFFA0A0A8), fontSize = 14.sp, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(14.dp))
            st.qr?.let { Image(bitmap = it.asImageBitmap(), contentDescription = "QR-Code", modifier = Modifier.size(230.dp)) }
            Spacer(Modifier.height(10.dp))
            Text(st.url, color = Color(0xFFDDDDDD), fontSize = 15.sp, textAlign = TextAlign.Center)
            st.message?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = Color(0xFF5CE1E6), fontSize = 15.sp, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(16.dp))
            val interaction = remember { MutableInteractionSource() }
            val focused by interaction.collectIsFocusedAsState()
            Text(
                "Fertig",
                color = if (focused) Color(0xFF062024) else Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .focusRequester(focus)
                    .background(if (focused) Color(0xFF5CE1E6) else Color.White.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                    .clickable(interactionSource = interaction, indication = null) { FiptvLan.stop() }
                    .padding(horizontal = 36.dp, vertical = 12.dp),
            )
        }
    }
}
