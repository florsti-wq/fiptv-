@file:Suppress("UnsafeOptInUsageError")

package com.arflix.tv.fiptv.rec

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.SurfaceView
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.tv.material3.Text
import com.arflix.tv.fiptv.FiptvNightMode
import kotlinx.coroutines.delay
import java.io.File

/**
 * fIPTV: einfacher Player für Aufnahmen und Downloads (auch während eine Aufnahme noch läuft).
 * OK/Tippen: Pause/Weiter · links/rechts: 10 s · hoch/runter: 1 min · Zurück: schließen.
 */
class FiptvLocalPlayerActivity : ComponentActivity() {
    private var player: ExoPlayer? = null
    private var showUi by mutableStateOf(true)
    private var uiPoke by mutableLongStateOf(0L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val path = intent.getStringExtra(EXTRA_PATH) ?: run { finish(); return }
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val recId = intent.getStringExtra(EXTRA_REC_ID)
        val file = File(path)
        val growing = { recId != null && FiptvRecordService.isRunning(recId) }
        val exo = ExoPlayer.Builder(this).build()
        player = exo
        val item = MediaItem.fromUri(Uri.fromFile(file))
        if (growing()) {
            exo.setMediaSource(ProgressiveMediaSource.Factory(FiptvTailDataSource.Factory(file, growing)).createMediaSource(item))
        } else {
            exo.setMediaItem(item)
        }
        val night = FiptvNightMode.attach(exo)
        exo.prepare()
        exo.playWhenReady = true

        setContent {
            var position by remember { mutableLongStateOf(0L) }
            var duration by remember { mutableLongStateOf(0L) }
            var playing by remember { mutableStateOf(true) }
            LaunchedEffect(Unit) {
                while (true) {
                    position = exo.currentPosition
                    duration = exo.duration.takeIf { it != C.TIME_UNSET } ?: 0L
                    playing = exo.isPlaying
                    delay(500)
                }
            }
            LaunchedEffect(uiPoke) {
                showUi = true
                delay(4_000)
                if (exo.isPlaying) showUi = false
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        togglePlay(); uiPoke = System.nanoTime()
                    },
            ) {
                AndroidView(
                    factory = { ctx -> SurfaceView(ctx).also { exo.setVideoSurfaceView(it) } },
                    modifier = Modifier.fillMaxSize(),
                )
                if (showUi || !playing) {
                    Column(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(32.dp)
                            .background(Color(0xCC111114), RoundedCornerShape(12.dp))
                            .padding(16.dp),
                    ) {
                        Text(title, color = Color.White, fontSize = 18.sp)
                        Text(
                            (if (playing) "▶ " else "❚❚ ") + fmt(position) + (if (duration > 0) " / " + fmt(duration) else "") +
                                (if (growing()) "  ·  Aufnahme läuft noch" else ""),
                            color = Color(0xFFBBBBBB), fontSize = 14.sp,
                        )
                        if (duration > 0) {
                            Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF333333))) {
                                Box(Modifier.fillMaxWidth((position.toFloat() / duration).coerceIn(0f, 1f)).height(4.dp).background(Color(0xFF5CE1E6)))
                            }
                        }
                        Text("OK: Pause · ◀ ▶ 10 s · ▲ ▼ 1 min · Zurück: schließen", color = Color(0xFF888888), fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
        }
        this.night = night
    }

    private var night: FiptvNightMode.Handle? = null

    private fun togglePlay() {
        val p = player ?: return
        if (p.isPlaying) p.pause() else p.play()
    }

    private fun seekBy(ms: Long) {
        val p = player ?: return
        if (!p.isCurrentMediaItemSeekable) return
        val dur = p.duration.takeIf { it != C.TIME_UNSET } ?: Long.MAX_VALUE
        p.seekTo((p.currentPosition + ms).coerceIn(0L, dur))
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        uiPoke = System.nanoTime()
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_SPACE -> { togglePlay(); return true }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> { seekBy(-10_000); return true }
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { seekBy(10_000); return true }
            KeyEvent.KEYCODE_DPAD_UP -> { seekBy(60_000); return true }
            KeyEvent.KEYCODE_DPAD_DOWN -> { seekBy(-60_000); return true }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onStop() {
        super.onStop()
        player?.pause()
    }

    override fun onDestroy() {
        night?.release()
        player?.release()
        player = null
        super.onDestroy()
    }

    private fun fmt(ms: Long): String {
        val s = ms / 1000
        val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
    }

    companion object {
        const val EXTRA_PATH = "path"
        const val EXTRA_TITLE = "title"
        const val EXTRA_REC_ID = "recId"

        fun open(context: Context, entry: FiptvLibrary.Entry) {
            context.startActivity(Intent(context, FiptvLocalPlayerActivity::class.java).apply {
                putExtra(EXTRA_PATH, entry.path)
                putExtra(EXTRA_TITLE, entry.title)
                if (entry.kind == FiptvLibrary.Kind.RECORDING) putExtra(EXTRA_REC_ID, entry.id)
                if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }
}
