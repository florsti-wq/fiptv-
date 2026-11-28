package com.arflix.tv.fiptv

import android.content.Context
import android.content.Intent
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * fIPTV: Einstellungen per Heimnetz auf ein anderes Gerät übertragen (eigene Umsetzung).
 *
 * Altes Gerät („Senden“): kleiner Server im WLAN, im Netz sichtbar als „_fiptv._tcp“, zeigt einen
 * 6-stelligen Code. Neues Gerät („Übernehmen“): findet das Gerät, Code eingeben, holt alles ab.
 * Die Daten (Quellen mit Zugangsdaten, Profile, Favoriten, Gruppen, Einstellungen) werden mit einem
 * aus dem Code abgeleiteten Schlüssel verschlüsselt übertragen; ohne Code bekommt niemand etwas.
 * Nach 5 falschen Codes oder 10 Minuten schließt der Server.
 */
object FiptvTransfer {
    private const val SERVICE_TYPE = "_fiptv._tcp."

    /** fIPTV-eigene Einstellungen, die mitkommen (gerätebezogene wie Decoder, Aufnahme-Pfade, Caches nicht). */
    private val PREF_FILES = listOf(
        "fiptv_custom_groups", "fiptv_epg_map", "fiptv_epg_offset", "fiptv_effects", "fiptv_live_latency",
        "fiptv_night_mode", "fiptv_pip", "fiptv_start_live", "fiptv_timeshift", "fiptv_ui", "fiptv_channels",
        "fiptv_sub_reminder", "fiptv_goal_alerts", "fiptv_reminders", "fiptv_kids_limit",
    )

    // ---------- Zustand für die Oberfläche ----------
    data class SendState(val code: String, val address: String, val message: String? = null, val done: Boolean = false)
    data class Found(val name: String, val host: String, val port: Int)

    var send by mutableStateOf<SendState?>(null)
        private set
    var receiveOpen by mutableStateOf(false)
        private set
    var receiveMessage by mutableStateOf<String?>(null)
        private set
    var receiveBusy by mutableStateOf(false)
        private set
    val found = mutableStateListOf<Found>()

    /** Von den Einstellungen gesetzt und abgeholt (wie bei der QR-Einrichtung). */
    var sendRequested by mutableStateOf(false)
    var receiveRequested by mutableStateOf(false)

    private val main = Handler(Looper.getMainLooper())
    private var server: Server? = null
    private var nsd: NsdManager? = null
    private var registration: NsdManager.RegistrationListener? = null
    private var discovery: NsdManager.DiscoveryListener? = null
    private val stopper = Runnable { stopSend() }

    private fun deviceName(): String =
        listOf(Build.MANUFACTURER, Build.MODEL).filter { it.isNotBlank() }.joinToString(" ")
            .replaceFirstChar { it.uppercase() }.take(40)

    // ---------- Verschlüsselung (Schlüssel aus dem Code) ----------
    private fun key(code: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(code.toCharArray(), salt, 120_000, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded
        return SecretKeySpec(bytes, "AES")
    }

    private fun encrypt(code: String, plain: String): JSONObject {
        val rnd = SecureRandom()
        val salt = ByteArray(16).also(rnd::nextBytes)
        val iv = ByteArray(12).also(rnd::nextBytes)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key(code, salt), GCMParameterSpec(128, iv))
        val data = c.doFinal(plain.toByteArray(Charsets.UTF_8))
        fun b(x: ByteArray) = Base64.encodeToString(x, Base64.NO_WRAP)
        return JSONObject().put("salt", b(salt)).put("iv", b(iv)).put("data", b(data))
    }

    private fun decrypt(code: String, box: JSONObject): String {
        fun d(k: String) = Base64.decode(box.getString(k), Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(code, d("salt")), GCMParameterSpec(128, d("iv")))
        return String(c.doFinal(d("data")), Charsets.UTF_8)
    }

    // ---------- fIPTV-Einstellungen ----------
    private fun exportPrefs(context: Context): JSONObject {
        val out = JSONObject()
        for (name in PREF_FILES) {
            val all = context.getSharedPreferences(name, Context.MODE_PRIVATE).all
            if (all.isEmpty()) continue
            val o = JSONObject()
            for ((k, v) in all) {
                when (v) {
                    is Boolean -> o.put(k, JSONObject().put("t", "b").put("v", v))
                    is Int -> o.put(k, JSONObject().put("t", "i").put("v", v))
                    is Long -> o.put(k, JSONObject().put("t", "l").put("v", v))
                    is Float -> o.put(k, JSONObject().put("t", "f").put("v", v.toDouble()))
                    is String -> o.put(k, JSONObject().put("t", "s").put("v", v))
                    is Set<*> -> o.put(k, JSONObject().put("t", "ss").put("v", org.json.JSONArray(v.map { it.toString() })))
                }
            }
            out.put(name, o)
        }
        return out
    }

    private fun importPrefs(context: Context, prefs: JSONObject) {
        for (name in prefs.keys()) {
            if (name !in PREF_FILES) continue
            val o = prefs.getJSONObject(name)
            val e = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
            for (k in o.keys()) {
                val item = o.getJSONObject(k)
                when (item.optString("t")) {
                    "b" -> e.putBoolean(k, item.getBoolean("v"))
                    "i" -> e.putInt(k, item.getInt("v"))
                    "l" -> e.putLong(k, item.getLong("v"))
                    "f" -> e.putFloat(k, item.getDouble("v").toFloat())
                    "s" -> e.putString(k, item.getString("v"))
                    "ss" -> e.putStringSet(k, item.getJSONArray("v").let { a -> (0 until a.length()).map(a::getString).toSet() })
                }
            }
            e.commit()
        }
    }

    // ---------- Senden (altes Gerät) ----------
    fun startSend(context: Context, snapshot: suspend () -> String) {
        stopSend()
        val app = context.applicationContext
        val code = (0 until 6).map { SecureRandom().nextInt(10) }.joinToString("")
        var started: Server? = null
        for (port in 8210 until 8220) {
            val s = Server(port, code, app, snapshot)
            if (runCatching { s.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false) }.isSuccess) { started = s; break }
        }
        val s = started ?: run { send = SendState(code, "", "Konnte keinen freien Netzwerk-Anschluss öffnen"); return }
        server = s
        val ip = com.arflix.tv.util.DeviceIpAddress.get(app) ?: "?"
        send = SendState(code, "$ip:${s.listeningPort}")
        // im WLAN bekanntmachen, damit das neue Gerät uns ohne Eintippen findet
        runCatching {
            val m = app.getSystemService(Context.NSD_SERVICE) as NsdManager
            val info = NsdServiceInfo().apply {
                serviceName = "fIPTV " + deviceName()
                serviceType = SERVICE_TYPE
                port = s.listeningPort
            }
            val l = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(i: NsdServiceInfo) {}
                override fun onRegistrationFailed(i: NsdServiceInfo, e: Int) {}
                override fun onServiceUnregistered(i: NsdServiceInfo) {}
                override fun onUnregistrationFailed(i: NsdServiceInfo, e: Int) {}
            }
            m.registerService(info, NsdManager.PROTOCOL_DNS_SD, l)
            nsd = m; registration = l
        }
        main.postDelayed(stopper, 10 * 60_000L)
    }

    fun stopSend() {
        main.removeCallbacks(stopper)
        registration?.let { l -> runCatching { nsd?.unregisterService(l) } }
        registration = null
        server?.let { runCatching { it.stop() } }
        server = null
        send = null
    }

    private class Server(port: Int, private val code: String, private val app: Context, private val snapshot: suspend () -> String) : NanoHTTPD(port) {
        @Volatile private var wrong = 0

        override fun serve(session: IHTTPSession): Response {
            if (session.method == Method.GET && session.uri == "/fiptv/info") {
                return json(Response.Status.OK, JSONObject().put("name", "fIPTV " + deviceName()).put("app", "fiptv"))
            }
            if (session.method != Method.POST || session.uri != "/fiptv/transfer") return json(Response.Status.NOT_FOUND, JSONObject().put("message", "Nicht gefunden"))
            val body = HashMap<String, String>().also { runCatching { session.parseBody(it) } }["postData"].orEmpty()
            val given = runCatching { JSONObject(body).optString("code") }.getOrDefault("")
            if (given != code) {
                wrong++
                if (wrong >= 5) main.post { send = send?.copy(message = "Zu viele falsche Codes – Übertragen beendet"); main.postDelayed({ stopSend() }, 4_000) }
                return json(Response.Status.FORBIDDEN, JSONObject().put("message", "Falscher Code"))
            }
            return try {
                val payload = JSONObject()
                    .put("v", 1)
                    .put("from", deviceName())
                    .put("snapshot", runBlocking(Dispatchers.IO) { snapshot() })
                    .put("prefs", exportPrefs(app))
                main.post { send = send?.copy(message = "Übertragen ✓ – das neue Gerät richtet sich jetzt ein.", done = true) }
                json(Response.Status.OK, encrypt(code, payload.toString()))
            } catch (e: Exception) {
                json(Response.Status.INTERNAL_ERROR, JSONObject().put("message", "Fehler beim Zusammenstellen: ${e.javaClass.simpleName}"))
            }
        }

        private fun json(status: Response.Status, o: JSONObject) = newFixedLengthResponse(status, "application/json; charset=utf-8", o.toString())
    }

    // ---------- Übernehmen (neues Gerät) ----------
    fun openReceive(context: Context) {
        closeReceive()
        receiveOpen = true
        receiveMessage = null
        found.clear()
        val app = context.applicationContext
        runCatching {
            val m = app.getSystemService(Context.NSD_SERVICE) as NsdManager
            val l = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(t: String) {}
                override fun onDiscoveryStopped(t: String) {}
                override fun onStartDiscoveryFailed(t: String, e: Int) {}
                override fun onStopDiscoveryFailed(t: String, e: Int) {}
                override fun onServiceLost(i: NsdServiceInfo) {
                    main.post { found.removeAll { it.name == i.serviceName } }
                }
                override fun onServiceFound(i: NsdServiceInfo) {
                    @Suppress("DEPRECATION")
                    runCatching {
                        m.resolveService(i, object : NsdManager.ResolveListener {
                            override fun onResolveFailed(i: NsdServiceInfo, e: Int) {}
                            override fun onServiceResolved(r: NsdServiceInfo) {
                                val host = r.host?.hostAddress ?: return
                                main.post {
                                    if (found.none { it.host == host && it.port == r.port }) found.add(Found(r.serviceName, host, r.port))
                                }
                            }
                        })
                    }
                }
            }
            m.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, l)
            nsd = m; discovery = l
        }
    }

    fun closeReceive() {
        discovery?.let { l -> runCatching { nsd?.stopServiceDiscovery(l) } }
        discovery = null
        receiveOpen = false
        receiveBusy = false
    }

    /** Manuelle Adresse „192.168.1.20:8210“ (falls die Suche im WLAN nichts findet). */
    fun parseAddress(text: String): Found? {
        val t = text.trim().removePrefix("http://").trimEnd('/')
        val host = t.substringBefore(':').trim()
        val port = t.substringAfter(':', "8210").toIntOrNull() ?: return null
        if (!Regex("""^\d{1,3}(\.\d{1,3}){3}$""").matches(host)) return null
        return Found(host, host, port)
    }

    /**
     * Daten abholen und einspielen. [apply] spielt die Sicherung ein (Profile, Quellen, Einstellungen).
     * Danach startet die App neu, damit alles frisch geladen wird.
     */
    suspend fun receive(context: Context, target: Found, code: String, apply: suspend (String) -> Unit) {
        receiveBusy = true
        receiveMessage = "Verbinde mit „${target.name}“ …"
        try {
            val result = withContext(Dispatchers.IO) {
                val conn = (URL("http://${target.host}:${target.port}/fiptv/transfer").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = 8_000
                    readTimeout = 60_000
                    setRequestProperty("Content-Type", "application/json")
                }
                conn.outputStream.use { it.write(JSONObject().put("code", code).toString().toByteArray()) }
                val status = conn.responseCode
                val text = (if (status in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
                status to text
            }
            if (result.first == 403) { receiveMessage = "Falscher Code – bitte noch einmal"; return }
            if (result.first !in 200..299) {
                receiveMessage = runCatching { JSONObject(result.second).optString("message") }.getOrNull()?.ifBlank { null } ?: "Fehler ${result.first}"
                return
            }
            receiveMessage = "Daten empfangen – wird eingerichtet …"
            val payload = withContext(Dispatchers.Default) { JSONObject(decrypt(code, JSONObject(result.second))) }
            payload.optJSONObject("prefs")?.let { importPrefs(context.applicationContext, it) }
            apply(payload.getString("snapshot"))
            receiveMessage = "Fertig ✓ – fIPTV startet neu …"
            kotlinx.coroutines.delay(1_500)
            restart(context)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            receiveMessage = when (e) {
                is java.net.ConnectException, is java.net.SocketTimeoutException -> "Gerät nicht erreichbar – ist „Senden“ am anderen Gerät noch offen und beide im selben WLAN?"
                is javax.crypto.AEADBadTagException -> "Code passt nicht zu den Daten – bitte neu versuchen"
                else -> "Fehler: ${e.javaClass.simpleName}"
            }
        } finally {
            receiveBusy = false
        }
    }

    private fun restart(context: Context) {
        val app = context.applicationContext
        val intent = app.packageManager.getLaunchIntentForPackage(app.packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        if (intent != null) app.startActivity(intent)
        main.postDelayed({ android.os.Process.killProcess(android.os.Process.myPid()) }, 300)
    }
}
