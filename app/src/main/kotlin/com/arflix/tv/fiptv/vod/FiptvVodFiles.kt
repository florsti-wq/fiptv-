package com.arflix.tv.fiptv.vod

import android.content.Context
import com.arflix.tv.data.repository.IptvRepository
import kotlinx.coroutines.sync.withLock

/**
 * fIPTV: Film-/Serienliste des Xtream-Anbieters als Datei (alle 6 Stunden neu) – gemeinsam für Suche
 * und „Neu bei deinem Anbieter“. Hält nichts davon dauerhaft im Speicher.
 */
object FiptvVodFiles {
    data class Files(val client: XtreamVodClient, val movies: java.io.File, val series: java.io.File)
    @Volatile private var client: XtreamVodClient? = null
    @Volatile private var clientKey: String? = null
    private val lock = kotlinx.coroutines.sync.Mutex()

    suspend fun ensure(context: Context, iptvRepository: IptvRepository): Files? {
        val account = iptvRepository.fiptvXtreamAccounts().firstOrNull() ?: return null
        val key = "${account.sourceId}|${account.username}|${account.baseUrl}"
        val tag = Integer.toHexString(key.hashCode())
        val dir = java.io.File(context.cacheDir, "fiptv_vod").apply { mkdirs() }
        val movieFile = java.io.File(dir, "movies_$tag.json")
        val seriesFile = java.io.File(dir, "series_$tag.json")
        val c = client?.takeIf { clientKey == key } ?: XtreamVodClient(account)
        client = c; clientKey = key
        val stale = { f: java.io.File -> !f.exists() || f.length() < 3 || System.currentTimeMillis() - f.lastModified() > 6 * 60 * 60_000L }
        lock.withLock {
            if (stale(movieFile)) runCatching { c.downloadAll(false, movieFile) }
            if (stale(seriesFile)) runCatching { c.downloadAll(true, seriesFile) }
        }
        return Files(c, movieFile, seriesFile)
    }
}
