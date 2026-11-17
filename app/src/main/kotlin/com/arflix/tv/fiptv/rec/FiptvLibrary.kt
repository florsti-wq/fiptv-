package com.arflix.tv.fiptv.rec

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * fIPTV: Aufnahmen und Downloads – Liste, Speicherort, Downloads über den Android-DownloadManager.
 */
object FiptvLibrary {
    private const val PREFS = "fiptv_library"

    enum class Kind { RECORDING, DOWNLOAD }
    enum class Status { RUNNING, DONE, FAILED }

    data class Entry(
        val id: String,
        val kind: Kind,
        val title: String,
        val path: String,
        val createdAt: Long,
        val endsAt: Long? = null,
        val status: Status,
        val downloadId: Long? = null,
        val note: String? = null,
    ) {
        val file: File get() = File(path)
    }

    private var appContext: Context? = null
    private var entries: List<Entry> = emptyList()

    var version by mutableIntStateOf(0)
        private set

    fun init(context: Context) {
        appContext = context.applicationContext
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("entries", null)
        entries = runCatching {
            val arr = JSONArray(raw ?: "[]")
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Entry(
                    id = o.getString("id"),
                    kind = Kind.valueOf(o.getString("kind")),
                    title = o.getString("title"),
                    path = o.getString("path"),
                    createdAt = o.getLong("createdAt"),
                    endsAt = if (o.has("endsAt")) o.getLong("endsAt") else null,
                    status = Status.valueOf(o.getString("status")),
                    downloadId = if (o.has("downloadId")) o.getLong("downloadId") else null,
                    note = o.optString("note").takeIf { it.isNotBlank() },
                )
            }
        }.getOrDefault(emptyList())
        // Aufnahmen, die beim letzten Beenden noch liefen, gelten als abgebrochen (Datei bleibt)
        entries = entries.map {
            if (it.kind == Kind.RECORDING && it.status == Status.RUNNING && !FiptvRecordService.isRunning(it.id)) {
                it.copy(status = if (File(it.path).length() > 0) Status.DONE else Status.FAILED, note = "abgebrochen")
            } else it
        }
        save()
    }

    private fun save() {
        val arr = JSONArray()
        entries.forEach { e ->
            arr.put(JSONObject().apply {
                put("id", e.id); put("kind", e.kind.name); put("title", e.title); put("path", e.path)
                put("createdAt", e.createdAt); e.endsAt?.let { put("endsAt", it) }
                put("status", e.status.name); e.downloadId?.let { put("downloadId", it) }
                e.note?.let { put("note", it) }
            })
        }
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.putString("entries", arr.toString())?.apply()
        version++
    }

    fun all(): List<Entry> = entries.sortedByDescending { it.createdAt }

    fun upsert(entry: Entry) {
        entries = entries.filterNot { it.id == entry.id } + entry
        save()
    }

    fun update(id: String, change: (Entry) -> Entry) {
        entries = entries.map { if (it.id == id) change(it) else it }
        save()
    }

    fun delete(context: Context, id: String) {
        val e = entries.firstOrNull { it.id == id } ?: return
        if (e.kind == Kind.RECORDING && e.status == Status.RUNNING) FiptvRecordService.stop(context, id)
        e.downloadId?.let { dl -> runCatching { (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).remove(dl) } }
        runCatching { File(e.path).delete() }
        entries = entries.filterNot { it.id == id }
        save()
    }

    /** Speicherort mit dem meisten freien Platz (z. B. USB-Stick am Fire TV, sonst intern). */
    fun storageDir(context: Context, sub: String): File {
        val dirs = context.getExternalFilesDirs(null).filterNotNull().ifEmpty { listOf(context.filesDir) }
        val best = dirs.maxByOrNull { d -> runCatching { StatFs(d.path).availableBytes }.getOrDefault(0L) } ?: context.filesDir
        return File(best, "fiptv/$sub").apply { mkdirs() }
    }

    fun freeBytes(dir: File): Long = runCatching { StatFs(dir.path).availableBytes }.getOrDefault(0L)

    fun safeName(title: String): String =
        title.replace(Regex("""[\\/:*?"<>|]"""), " ").replace(Regex("\\s+"), " ").trim().take(80).ifBlank { "fIPTV" }

    // ---------- Downloads ----------

    fun startDownload(context: Context, title: String, url: String, headers: Map<String, String>): Boolean {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager ?: return false
        val ext = url.substringBefore('?').substringAfterLast('.', "mp4").lowercase().takeIf { it.length in 2..4 } ?: "mp4"
        val dir = storageDir(context, "downloads")
        val file = File(dir, safeName(title) + "." + ext).let { f ->
            var c = f; var i = 2
            while (c.exists()) { c = File(dir, safeName(title) + " ($i)." + ext); i++ }
            c
        }
        val req = DownloadManager.Request(Uri.parse(url))
            .setTitle(title)
            .setDescription("fIPTV-Download")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationUri(Uri.fromFile(file))
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
        headers.forEach { (k, v) -> runCatching { req.addRequestHeader(k, v) } }
        if (headers.keys.none { it.equals("User-Agent", true) }) req.addRequestHeader("User-Agent", "fIPTV")
        val id = runCatching { dm.enqueue(req) }.getOrElse { return false }
        upsert(Entry(
            id = "dl_$id", kind = Kind.DOWNLOAD, title = title, path = file.path,
            createdAt = System.currentTimeMillis(), status = Status.RUNNING, downloadId = id,
        ))
        return true
    }

    /** Fortschritt eines Downloads (0..1) bzw. null, wenn unbekannt; aktualisiert den Status. */
    fun downloadProgress(context: Context, entry: Entry): Float? {
        val id = entry.downloadId ?: return null
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager ?: return null
        dm.query(DownloadManager.Query().setFilterById(id))?.use { c ->
            if (!c.moveToFirst()) return null
            val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val done = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> if (entry.status != Status.DONE) update(entry.id) { it.copy(status = Status.DONE) }
                DownloadManager.STATUS_FAILED -> if (entry.status != Status.FAILED) update(entry.id) { it.copy(status = Status.FAILED, note = "Download fehlgeschlagen") }
            }
            return if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else null
        }
        return null
    }

    @Suppress("unused")
    private fun publicMoviesDir(): File = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
}
