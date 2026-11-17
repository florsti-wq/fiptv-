@file:Suppress("UnsafeOptInUsageError")

package com.arflix.tv.fiptv.rec

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import java.io.File
import java.io.RandomAccessFile

/**
 * fIPTV: liest eine Datei, die gerade noch geschrieben wird (Live-Pause, laufende Aufnahme).
 * Am aktuellen Dateiende wird gewartet, solange [isGrowing] true ist.
 */
class FiptvTailDataSource(
    private val file: File,
    private val isGrowing: () -> Boolean,
) : BaseDataSource(false) {
    private var raf: RandomAccessFile? = null
    private var uri: Uri? = null

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        uri = dataSpec.uri
        // kurz warten, bis die Datei existiert
        var waited = 0
        while (!file.exists() && waited < 15_000) { Thread.sleep(100); waited += 100 }
        raf = RandomAccessFile(file, "r").also { it.seek(dataSpec.position) }
        transferStarted(dataSpec)
        return if (isGrowing()) C.LENGTH_UNSET.toLong() else (file.length() - dataSpec.position).coerceAtLeast(0L)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        val r = raf ?: return C.RESULT_END_OF_INPUT
        var waited = 0
        while (true) {
            val n = r.read(buffer, offset, length)
            if (n > 0) {
                bytesTransferred(n)
                return n
            }
            if (!isGrowing() || waited > 20_000) return C.RESULT_END_OF_INPUT
            Thread.sleep(100)
            waited += 100
        }
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        runCatching { raf?.close() }
        raf = null
        if (uri != null) {
            uri = null
            transferEnded()
        }
    }

    class Factory(private val file: File, private val isGrowing: () -> Boolean) : DataSource.Factory {
        override fun createDataSource(): DataSource = FiptvTailDataSource(file, isGrowing)
    }
}
