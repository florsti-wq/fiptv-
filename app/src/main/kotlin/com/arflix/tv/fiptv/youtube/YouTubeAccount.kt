package com.arflix.tv.fiptv.youtube

import android.content.Context
import com.arflix.tv.BuildConfig
import com.arflix.tv.network.OkHttpProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONObject

/**
 * fIPTV: Google-Anmeldung für den YouTube-Bereich – Googles Verfahren für Fernseher
 * („TVs and Limited Input devices“): Die App zeigt einen Code + QR-Code, man bestätigt am Handy.
 * Die App sieht nie das Passwort. Gespeichert wird nur der Zugangs-Schlüssel (refresh token),
 * je Profil und nur auf diesem Gerät.
 */
object YouTubeAccount {
    private const val PREFS = "fiptv_youtube_account"
    private const val SCOPE = "https://www.googleapis.com/auth/youtube"

    private val clientId: String
        get() = BuildConfig.YOUTUBE_CLIENT_ID.trim().takeUnless { it.isBlank() || it.startsWith("your-") }.orEmpty()
    private val clientSecret: String
        get() = BuildConfig.YOUTUBE_CLIENT_SECRET.trim().takeUnless { it.isBlank() || it.startsWith("your-") }.orEmpty()

    /** Ist die Google-Anmeldung in dieser App-Version eingerichtet? */
    val isAvailable: Boolean get() = clientId.isNotEmpty() && clientSecret.isNotEmpty()

    data class DeviceCode(val deviceCode: String, val userCode: String, val verificationUrl: String, val expiresAt: Long, val intervalSec: Int)
    data class Account(val name: String, val thumbnail: String?)

    sealed interface PollResult {
        data object Pending : PollResult
        data object SlowDown : PollResult
        data class Done(val account: Account?) : PollResult
        data class Failed(val message: String) : PollResult
    }

    private val access = HashMap<String, Pair<String, Long>>() // profileId → (token, gültig bis)

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun account(context: Context, profileId: String): Account? {
        val p = prefs(context)
        if (p.getString("rt_$profileId", null).isNullOrBlank()) return null
        return Account(p.getString("name_$profileId", null).orEmpty().ifBlank { "Google-Konto" }, p.getString("thumb_$profileId", null))
    }

    suspend fun startSignIn(): DeviceCode = withContext(Dispatchers.IO) {
        check(isAvailable) { "Die Google-Anmeldung ist in dieser App-Version noch nicht eingerichtet." }
        val json = post("https://oauth2.googleapis.com/device/code", FormBody.Builder()
            .add("client_id", clientId).add("scope", SCOPE).build())
        if (json.has("error")) error(errorText(json))
        DeviceCode(
            deviceCode = json.getString("device_code"),
            userCode = json.getString("user_code"),
            verificationUrl = json.optString("verification_url", "https://www.google.com/device"),
            expiresAt = System.currentTimeMillis() + json.optLong("expires_in", 1800) * 1000,
            intervalSec = json.optInt("interval", 5).coerceAtLeast(5),
        )
    }

    /** Einmal nachfragen, ob der Nutzer am Handy zugestimmt hat. */
    suspend fun poll(context: Context, profileId: String, code: DeviceCode): PollResult = withContext(Dispatchers.IO) {
        if (System.currentTimeMillis() > code.expiresAt) return@withContext PollResult.Failed("Der Code ist abgelaufen – bitte neu starten.")
        val json = runCatching {
            post("https://oauth2.googleapis.com/token", FormBody.Builder()
                .add("client_id", clientId).add("client_secret", clientSecret)
                .add("device_code", code.deviceCode)
                .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code").build())
        }.getOrElse { return@withContext PollResult.Pending }
        when (json.optString("error")) {
            "" -> {
                val refresh = json.optString("refresh_token")
                val token = json.optString("access_token")
                if (refresh.isBlank() || token.isBlank()) return@withContext PollResult.Failed("Google hat keinen Zugang geliefert.")
                access[profileId] = token to (System.currentTimeMillis() + json.optLong("expires_in", 3600) * 1000 - 60_000)
                val me = runCatching { YouTubeRepository.myChannel(token) }.getOrNull()
                prefs(context).edit()
                    .putString("rt_$profileId", refresh)
                    .putString("name_$profileId", me?.title.orEmpty())
                    .putString("thumb_$profileId", me?.thumbnail.orEmpty())
                    .apply()
                PollResult.Done(account(context, profileId))
            }
            "authorization_pending" -> PollResult.Pending
            "slow_down" -> PollResult.SlowDown
            "access_denied" -> PollResult.Failed("Die Anmeldung wurde am Handy abgelehnt.")
            "expired_token" -> PollResult.Failed("Der Code ist abgelaufen – bitte neu starten.")
            else -> PollResult.Failed(errorText(json))
        }
    }

    /** Gültiger Zugang für API-Aufrufe (wird bei Bedarf erneuert); null = nicht angemeldet. */
    suspend fun token(context: Context, profileId: String): String? = withContext(Dispatchers.IO) {
        access[profileId]?.let { (t, until) -> if (System.currentTimeMillis() < until) return@withContext t }
        val refresh = prefs(context).getString("rt_$profileId", null)?.takeIf { it.isNotBlank() } ?: return@withContext null
        val json = post("https://oauth2.googleapis.com/token", FormBody.Builder()
            .add("client_id", clientId).add("client_secret", clientSecret)
            .add("refresh_token", refresh).add("grant_type", "refresh_token").build())
        if (json.optString("error") == "invalid_grant") {
            // zurückgezogen oder abgelaufen → abmelden
            signOutLocal(context, profileId)
            error("Die Google-Anmeldung ist nicht mehr gültig – bitte neu anmelden.")
        }
        if (json.has("error")) error(errorText(json))
        val t = json.getString("access_token")
        access[profileId] = t to (System.currentTimeMillis() + json.optLong("expires_in", 3600) * 1000 - 60_000)
        t
    }

    suspend fun signOut(context: Context, profileId: String) = withContext(Dispatchers.IO) {
        val refresh = prefs(context).getString("rt_$profileId", null)
        if (!refresh.isNullOrBlank()) {
            runCatching { post("https://oauth2.googleapis.com/revoke", FormBody.Builder().add("token", refresh).build()) }
        }
        signOutLocal(context, profileId)
    }

    private fun signOutLocal(context: Context, profileId: String) {
        access.remove(profileId)
        prefs(context).edit().remove("rt_$profileId").remove("name_$profileId").remove("thumb_$profileId").apply()
    }

    private fun errorText(json: JSONObject): String = when (json.optString("error")) {
        "invalid_client" -> "Der Google-Anmelde-Zugang (Client) ist falsch eingerichtet."
        "unauthorized_client" -> "Der Google-Zugang muss vom Typ „TVs and Limited Input devices“ sein."
        "invalid_scope" -> "Google erlaubt YouTube für diesen Zugang nicht."
        "org_internal" -> "Dieses Google-Konto ist für die App nicht freigegeben."
        "access_denied" -> "Zugriff verweigert – ist dein Konto als Testnutzer eingetragen?"
        else -> json.optString("error_description").ifBlank { "Google-Anmeldung fehlgeschlagen (${json.optString("error")})." }
    }

    private fun post(url: String, body: FormBody): JSONObject {
        val req = Request.Builder().url(url).post(body).build()
        OkHttpProvider.client.newCall(req).execute().use { r ->
            val text = r.body?.string().orEmpty()
            return runCatching { JSONObject(text) }.getOrElse { JSONObject().put("error", "http_${r.code}") }
        }
    }
}
