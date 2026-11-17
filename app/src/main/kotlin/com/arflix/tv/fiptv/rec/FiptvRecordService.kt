package com.arflix.tv.fiptv.rec

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * fIPTV: Aufnahmen laufen als Vordergrund-Dienst weiter, auch wenn die App im Hintergrund ist.
 * Jede Aufnahme belegt eine Verbindung deines Abos.
 */
class FiptvRecordService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        FiptvLibrary.init(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat()
        when (intent?.action) {
            ACTION_START -> intent.let { i ->
                val id = i.getStringExtra(EXTRA_ID) ?: return@let
                val url = i.getStringExtra(EXTRA_URL) ?: return@let
                val title = i.getStringExtra(EXTRA_TITLE) ?: "Aufnahme"
                val path = i.getStringExtra(EXTRA_PATH) ?: return@let
                val endsAt = i.getLongExtra(EXTRA_ENDS_AT, 0L).takeIf { it > 0 }
                val isHls = i.getBooleanExtra(EXTRA_HLS, false)
                val headers = runCatching {
                    JSONObject(i.getStringExtra(EXTRA_HEADERS) ?: "{}").let { o -> o.keys().asSequence().associateWith { o.getString(it) } }
                }.getOrDefault(emptyMap())
                startRecording(id, url, isHls, headers, title, File(path), endsAt)
            }
            ACTION_STOP -> intent.getStringExtra(EXTRA_ID)?.let { id -> writers[id]?.stop() }
        }
        if (writers.isEmpty()) stopSelfSoon()
        return START_NOT_STICKY
    }

    private fun startRecording(id: String, url: String, isHls: Boolean, headers: Map<String, String>, title: String, file: File, endsAt: Long?) {
        if (writers.containsKey(id)) return
        val writer = FiptvStreamWriter(url, isHls, headers, file, endAtMs = endsAt)
        writers[id] = writer
        FiptvLibrary.upsert(FiptvLibrary.Entry(
            id = id, kind = FiptvLibrary.Kind.RECORDING, title = title, path = file.path,
            createdAt = System.currentTimeMillis(), endsAt = endsAt, status = FiptvLibrary.Status.RUNNING,
        ))
        updateNotification()
        val job = writer.start(scope)
        scope.launch {
            job.join()
            writers.remove(id)
            val ok = file.exists() && file.length() > 64 * 1024
            FiptvLibrary.update(id) {
                it.copy(
                    status = if (ok) FiptvLibrary.Status.DONE else FiptvLibrary.Status.FAILED,
                    note = writer.error?.let { e -> "Fehler: $e" },
                )
            }
            updateNotification()
            if (writers.isEmpty()) stopSelfSoon()
        }
    }

    private fun stopSelfSoon() {
        scope.launch {
            delay(1_000)
            if (writers.isEmpty()) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun notification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Aufnahmen", NotificationManager.IMPORTANCE_LOW))
        }
        val titles = writers.keys.mapNotNull { id -> FiptvLibrary.all().firstOrNull { it.id == id }?.title }
        val launch = packageManager.getLaunchIntentForPackage(packageName)
        val pi = launch?.let {
            android.app.PendingIntent.getActivity(this, 0, it, android.app.PendingIntent.FLAG_IMMUTABLE)
        }
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(if (titles.size <= 1) "Aufnahme läuft" else "${titles.size} Aufnahmen laufen")
            .setContentText(titles.joinToString(", ").ifBlank { "fIPTV" })
            .setOngoing(true)
            .setContentIntent(pi)
            .build()
    }

    private fun startForegroundCompat() {
        val n = notification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, n)
        }
    }

    private fun updateNotification() {
        runCatching { getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification()) }
    }

    override fun onDestroy() {
        writers.values.forEach { it.stop() }
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "fiptv_recordings"
        private const val NOTIFICATION_ID = 4711
        const val ACTION_START = "com.arflix.tv.fiptv.REC_START"
        const val ACTION_STOP = "com.arflix.tv.fiptv.REC_STOP"
        const val EXTRA_ID = "id"
        const val EXTRA_URL = "url"
        const val EXTRA_HLS = "hls"
        const val EXTRA_HEADERS = "headers"
        const val EXTRA_TITLE = "title"
        const val EXTRA_PATH = "path"
        const val EXTRA_ENDS_AT = "endsAt"

        private val writers = ConcurrentHashMap<String, FiptvStreamWriter>()

        fun isRunning(id: String) = writers.containsKey(id)
        fun bytesOf(id: String): Long? = writers[id]?.bytes

        fun start(context: Context, title: String, url: String, isHls: Boolean, headers: Map<String, String>, endsAt: Long): String {
            val id = "rec_" + System.currentTimeMillis().toString(36)
            val dir = FiptvLibrary.storageDir(context, "recordings")
            val stamp = java.text.SimpleDateFormat("yyyy-MM-dd HH-mm", java.util.Locale.GERMANY).format(java.util.Date())
            val file = File(dir, FiptvLibrary.safeName("$title $stamp") + ".ts")
            val intent = Intent(context, FiptvRecordService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_ID, id); putExtra(EXTRA_URL, url); putExtra(EXTRA_HLS, isHls)
                putExtra(EXTRA_HEADERS, JSONObject(headers as Map<*, *>).toString())
                putExtra(EXTRA_TITLE, title); putExtra(EXTRA_PATH, file.path); putExtra(EXTRA_ENDS_AT, endsAt)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
            return id
        }

        fun stop(context: Context, id: String) {
            writers[id]?.stop()
            runCatching {
                context.startService(Intent(context, FiptvRecordService::class.java).apply { action = ACTION_STOP; putExtra(EXTRA_ID, id) })
            }
        }
    }
}
