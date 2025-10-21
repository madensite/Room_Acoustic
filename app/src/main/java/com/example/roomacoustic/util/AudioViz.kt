// path: app/src/main/java/com/example/roomacoustic/util/AudioViz.kt
package com.example.roomacoustic.util

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*
import android.graphics.Color

// ── WAV(Mono 16bit) Reader ──
fun readMono16Wav(path: String): Pair<ShortArray, Int> {
    val f = File(path)
    val bytes = f.readBytes()
    require(bytes.copyOfRange(0, 4).toString(Charsets.US_ASCII) == "RIFF") { "Not RIFF" }
    require(bytes.copyOfRange(8, 12).toString(Charsets.US_ASCII) == "WAVE") { "Not WAVE" }

    var i = 12
    var fmtSampleRate = 0
    var fmtChannels = 1
    var dataOffset = -1
    var dataSize = -1

    while (i + 8 <= bytes.size) {
        val chunkId = bytes.copyOfRange(i, i + 4).toString(Charsets.US_ASCII)
        val chunkSize = ByteBuffer.wrap(bytes, i + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
        val chunkDataStart = i + 8
        when (chunkId) {
            "fmt " -> {
                val bb = ByteBuffer.wrap(bytes, chunkDataStart, chunkSize).order(ByteOrder.LITTLE_ENDIAN)
                val audioFormat = bb.short.toInt() and 0xFFFF
                fmtChannels = bb.short.toInt() and 0xFFFF
                fmtSampleRate = bb.int
                bb.int /* byteRate */; bb.short /* blockAlign */
                val bitsPerSample = bb.short.toInt() and 0xFFFF
                require(audioFormat == 1) { "Only PCM" }
                require(bitsPerSample == 16) { "Only 16-bit" }
                require(fmtChannels == 1) { "Only mono" }
            }
            "data" -> {
                dataOffset = chunkDataStart
                dataSize = chunkSize
            }
        }
        i = chunkDataStart + chunkSize
    }
    require(dataOffset >= 0 && dataSize > 0) { "WAV data not found" }

    val samples = dataSize / 2
    val out = ShortArray(samples)
    val bb = ByteBuffer.wrap(bytes, dataOffset, dataSize).order(ByteOrder.LITTLE_ENDIAN)
    for (n in 0 until samples) out[n] = bb.short
    return out to fmtSampleRate
}

// ── 간이 STFT Spectrogram ──
fun computeSpectrogram(
    pcm: ShortArray,
    sampleRate: Int,
    winSize: Int = 2048,
    hop: Int = 512
): Array<FloatArray> {
    fun hann(n: Int) = FloatArray(n) { i ->
        (0.5 - 0.5 * kotlin.math.cos(2.0 * Math.PI * i / (n - 1))).toFloat()
    }
    val window = hann(winSize)
    val frames = ((pcm.size - winSize) / hop).coerceAtLeast(1)
    val spec = Array(frames) { FloatArray(winSize / 2 + 1) }

    val re = FloatArray(winSize)
    val im = FloatArray(winSize)

    fun fft(reArr: FloatArray, imArr: FloatArray) {
        val n = reArr.size
        var j = 0
        for (i in 1 until n - 1) {
            var bit = n shr 1
            while (j >= bit) { j -= bit; bit = bit shr 1 }
            j += bit
            if (i < j) {
                val tr = reArr[i]; reArr[i] = reArr[j]; reArr[j] = tr
                val ti = imArr[i]; imArr[i] = imArr[j]; imArr[j] = ti
            }
        }
        var len = 2
        while (len <= n) {
            val half = len shr 1
            val theta = (-2.0 * Math.PI / len).toFloat()
            val wpr = kotlin.math.cos(theta)
            val wpi = kotlin.math.sin(theta)
            var i = 0
            while (i < n) {
                var wr = 1f; var wi = 0f
                for (k in 0 until half) {
                    val i0 = i + k
                    val i1 = i0 + half
                    val tr = wr * reArr[i1] - wi * imArr[i1]
                    val ti = wr * imArr[i1] + wi * reArr[i1]
                    reArr[i1] = reArr[i0] - tr
                    imArr[i1] = imArr[i0] - ti
                    reArr[i0] += tr
                    imArr[i0] += ti
                    val wrn = wr * wpr - wi * wpi
                    val win = wr * wpi + wi * wpr
                    wr = wrn; wi = win
                }
                i += len
            }
            len = len shl 1
        }
    }

    var idx = 0
    for (f in 0 until frames) {
        for (i in 0 until winSize) {
            val s = if (idx + i < pcm.size) (pcm[idx + i] / 32768f) else 0f
            re[i] = s * window[i]; im[i] = 0f
        }
        fft(re, im)
        for (k in 0..winSize / 2) {
            val mag = hypot(re[k].toDouble(), im[k].toDouble()).toFloat().coerceAtLeast(1e-8f)
            spec[f][k] = 20f * ln(mag) / ln(10f) // dB-ish
        }
        idx += hop
    }
    return spec
}

// ── dB 배열 → 흑백 비트맵 ──
fun spectrogramToBitmap(spec: Array<FloatArray>): Bitmap {
    val h = spec.size
    val w = spec[0].size
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)

    var min = Float.POSITIVE_INFINITY
    var max = Float.NEGATIVE_INFINITY
    for (row in spec) for (v in row) {
        if (v < min) min = v
        if (v > max) max = v
    }
    val span = (max - min).coerceAtLeast(1e-3f)
    for (y in 0 until h) {
        for (x in 0 until w) {
            val t = ((spec[y][x] - min) / span).coerceIn(0f, 1f)
            val g = (t * 255).toInt()
            bmp.setPixel(x, y, Color.rgb(g, g, g))
        }
    }
    return bmp
}

// ── 재생 ──
fun playWav(path: String) {
    MediaPlayer().apply {
        setDataSource(path)
        setOnPreparedListener { start() }
        setOnCompletionListener { release() }
        prepareAsync()
    }
}
