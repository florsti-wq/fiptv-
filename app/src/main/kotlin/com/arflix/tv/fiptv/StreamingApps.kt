package com.arflix.tv.fiptv

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/**
 * fIPTV: „Läuft bei …“ – öffnet Netflix, Prime Video oder Disney+ direkt aus der Detailseite.
 *
 * Abspielen innerhalb von fIPTV ist nicht möglich (Kopierschutz der Anbieter, keine Schnittstelle).
 * TMDB liefert nur, WO ein Titel läuft, nicht die Titel-ID des Anbieters – deshalb startet fIPTV
 * die App (Netflix: direkt mit der Suche nach dem Titel) und nennt den Titel zum Weitersuchen.
 */
object StreamingApps {
    data class App(
        val label: String,
        /** Paketnamen: Fernseher (Fire TV / Android TV) und Handy */
        val packages: List<String>,
        /** Erkennung am TMDB-Anbieternamen */
        val matches: (String) -> Boolean,
        /** optionale Suche direkt in der App */
        val searchUrl: ((String) -> String)? = null,
    )

    data class Target(val app: App, val packageName: String)

    val apps = listOf(
        App(
            label = "Netflix",
            packages = listOf("com.netflix.ninja", "com.netflix.mediaclient"),
            matches = { it.contains("netflix", ignoreCase = true) },
            searchUrl = { q -> "https://www.netflix.com/search?q=${Uri.encode(q)}" },
        ),
        App(
            label = "Prime Video",
            packages = listOf(
                "com.amazon.avod",                       // Fire TV
                "com.amazon.amazonvideo.livingroom",     // Android TV
                "com.amazon.avod.thirdpartyclient",      // Handy
            ),
            matches = { it.contains("prime video", ignoreCase = true) || it.contains("amazon video", ignoreCase = true) },
        ),
        App(
            label = "Disney+",
            packages = listOf("com.disney.disneyplus"),
            matches = { it.contains("disney", ignoreCase = true) },
        ),
    )

    private fun isInstalled(context: Context, pkg: String): Boolean =
        launchIntent(context, pkg) != null

    private fun launchIntent(context: Context, pkg: String): Intent? {
        val pm = context.packageManager
        return runCatching { pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg) }.getOrNull()
    }

    /** erster Anbieter aus der TMDB-Liste, dessen App auf diesem Gerät installiert ist */
    fun find(context: Context, serviceNames: List<String>): Target? {
        for (name in serviceNames) {
            val app = apps.firstOrNull { it.matches(name) } ?: continue
            val pkg = app.packages.firstOrNull { isInstalled(context, it) } ?: continue
            return Target(app, pkg)
        }
        return null
    }

    fun open(context: Context, target: Target, title: String?) {
        val search = title?.takeIf { it.isNotBlank() }?.let { target.app.searchUrl?.invoke(it) }
        if (search != null) {
            val ok = runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(search))
                        .setPackage(target.packageName)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }.isSuccess
            if (ok) return
        }
        val launch = launchIntent(context, target.packageName)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            if (launch == null) throw ActivityNotFoundException()
            context.startActivity(launch)
            if (!title.isNullOrBlank()) {
                Toast.makeText(context, "In ${target.app.label} nach „$title“ suchen", Toast.LENGTH_LONG).show()
            }
        } catch (_: Exception) {
            Toast.makeText(context, "${target.app.label} konnte nicht geöffnet werden", Toast.LENGTH_LONG).show()
        }
    }
}
