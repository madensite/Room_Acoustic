package com.example.roomacoustic.audio

import android.content.Context
import android.media.*
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.lang.System.arraycopy
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat


data class TestConfig(
    val sampleRate: Int = 48000,
    val sweepSec: Float = 6.0f,
    val fStart: Float = 20f,
    val fEnd: Float = 20000f,
    val headSilenceSec: Float = 0.5f,
    val tailSilenceSec: Float = 0.5f,
    val volume: Float = 0.9f   // 0.0 ~ 1.0
)

data class TestResult(
    val recordedWav: File,
    val playedWav: File,
    val peakDbfs: Float,
    val rmsDbfs: Float,
    val durationSec: Float
)

class DuplexMeasurer(private val ctx: Context) {

    /** 로그 스윕 + 앞/뒤 무음 포함 (float -1..1) */
    private fun buildSweep(cfg: TestConfig): FloatArray {
        val sr = cfg.sampleRate
        val Nhead = (cfg.headSilenceSec * sr).toInt()
        val Ntail = (cfg.tailSilenceSec * sr).toInt()
        val Nsweep = (cfg.sweepSec * sr).toInt()

        // Farina형 로그 스윕 주파수 법칙
        val k = ln(cfg.fEnd / cfg.fStart) / cfg.sweepSec
        val sweep = FloatArray(Nsweep) { n ->
            val t = n / sr.toFloat()
            // 주의: kotlin.math.exp 사용 (expf 없음)
            val phase = 2f * PI.toFloat() * cfg.fStart * ((exp(k * t) - 1f) / k)
            (sin(phase) * cfg.volume).toFloat()
        }

        val signal = FloatArray(Nhead + Nsweep + Ntail)
        // head silence는 0으로 남겨두고, 스윕 복사 후 tail silence
        System.arraycopy(sweep, 0, signal, Nhead, Nsweep)
        return signal
    }

    /** Float -1..1 → 16bit PCM little-endian */
    private fun floatToPCM16(samples: FloatArray): ByteArray {
        val out = ByteArray(samples.size * 2)
        var i = 0
        samples.forEach { f ->
            val s = (min(1f, max(-1f, f)) * Short.MAX_VALUE).toInt()
            out[i++] = (s and 0xFF).toByte()
            out[i++] = ((s ushr 8) and 0xFF).toByte()
        }
        return out
    }

    /** WAV 저장(모노 16비트) */
    private fun writeWavMono16(file: File, pcm: ByteArray, sampleRate: Int) {
        val dataLen = pcm.size
        val riffLen = 36 + dataLen
        file.outputStream().use { os ->
            fun w(s: String) = os.write(s.toByteArray(Charsets.US_ASCII))
            fun d32(v: Int) {
                val bb = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v)
                os.write(bb.array())
            }
            fun d16(v: Int) {
                val bb = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(v.toShort())
                os.write(bb.array())
            }
            w("RIFF"); d32(riffLen); w("WAVE")
            w("fmt "); d32(16); d16(1) // PCM
            d16(1) // mono
            d32(sampleRate)
            d32(sampleRate * 2) // byte rate
            d16(2) // block align
            d16(16) // bits
            w("data"); d32(dataLen)
            os.write(pcm)
        }
    }

    private fun levelDbfs(pcm: ShortArray): Pair<Float, Float> {
        var peak = 0
        var sum2 = 0.0
        pcm.forEach {
            val a = abs(it.toInt())
            if (a > peak) peak = a
            val f = it / 32768.0
            sum2 += f * f
        }
        val rms = sqrt(sum2 / max(1, pcm.size))
        val peakDb = if (peak > 0) (20 * log10(peak / 32768.0)).toFloat() else -120f
        val rmsDb  = if (rms  > 0) (20 * log10(rms)).toFloat() else -120f
        return peakDb to rmsDb
    }

    /** 스윕 재생 + 동시 녹음 → 두 개 WAV와 간단 레벨 반환 */
    @Suppress("MissingPermission") // 아래에서 직접 체크 + try/catch 처리함
    suspend fun runOnce(
        cfg: TestConfig,
        outDir: File,
        micSource: Int = MediaRecorder.AudioSource.UNPROCESSED // 미지원 시 폴백
    ): TestResult = withContext(Dispatchers.IO) {

        // ───────────────────────────────────────────
        // 0) 권한 확인 (RECORD_AUDIO)
        // ───────────────────────────────────────────
        val hasPerm = ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPerm) {
            throw SecurityException("RECORD_AUDIO permission not granted")
        }

        // ───────────────────────────────────────────
        // 1) 스윕 생성 & 재생 파일 저장
        // ───────────────────────────────────────────
        val sweep = buildSweep(cfg)                     // FloatArray (-1..1)
        val sweepPCM = floatToPCM16(sweep)             // ByteArray (16bit LE)
        val playedFile = File(outDir, "played_sweep_${System.currentTimeMillis()}.wav")
        writeWavMono16(playedFile, sweepPCM, cfg.sampleRate)

        // ───────────────────────────────────────────
        // 2) AudioTrack 준비 (STREAM, 16bit/mono)
        //    버퍼는 최소버퍼와 스윕길이 기반으로 여유있게
        // ───────────────────────────────────────────
        val minPlay = AudioTrack.getMinBufferSize(
            cfg.sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(cfg.sampleRate / 5) // ~200ms
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(cfg.sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(max(minPlay, sweepPCM.size / 4)) // 재생 지연 줄이기
            .build()

        // ───────────────────────────────────────────
        // 3) AudioRecord 준비 (UNPROCESSED → MIC → VOICE_RECOGNITION 폴백)
        //    lint 경고(권한) 억제: try/catch + 명시적 체크
        // ───────────────────────────────────────────
        val minRec = AudioRecord.getMinBufferSize(
            cfg.sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(cfg.sampleRate / 5) // ~200ms

        fun buildRecorderUnsafe(source: Int, bufferBytes: Int): AudioRecord =
            AudioRecord.Builder()
                .setAudioSource(source)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(cfg.sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferBytes)
                .build()

        fun buildRecorderSafe(source: Int, bufferBytes: Int): AudioRecord {
            // 여기서도 한 번 더 방어
            if (!hasPerm) throw SecurityException("RECORD_AUDIO permission not granted")
            return buildRecorderUnsafe(source, bufferBytes)
        }

        val record = runCatching {
            buildRecorderSafe(micSource, minRec * 2)
        }.getOrElse {
            // UNPROCESSED 미지원 단말 많음 → MIC 폴백
            runCatching {
                buildRecorderSafe(MediaRecorder.AudioSource.MIC, minRec * 2)
            }.getOrElse {
                // 마지막 폴백
                buildRecorderSafe(MediaRecorder.AudioSource.VOICE_RECOGNITION, minRec * 2)
            }
        }

        // ───────────────────────────────────────────
        // 4) 동시 실행 (재생/녹음)
        //    - arraycopy → System.arraycopy로 고정
        //    - chunk 길이는 2의 배수(16bit 정렬) 유지
        // ───────────────────────────────────────────
        val recBuf = ShortArray(sweep.size + cfg.sampleRate) // tail 여유
        record.startRecording()
        track.play()

        var playOffset = 0
        var recOffset = 0
        val tmpBytes = ByteArray(2048) // 짝수 유지(16bit 정렬)

        // 선제 녹음(헤드룸 확보)
        record.read(recBuf, recOffset, min(recBuf.size - recOffset, 1024))
            .takeIf { it > 0 }?.let { recOffset += it }

        while (playOffset < sweepPCM.size) {
            var chunk = min(tmpBytes.size, sweepPCM.size - playOffset)
            // 16bit 정렬: 짝수 길이 유지
            if ((chunk and 1) != 0) chunk -= 1
            if (chunk <= 0) break

            // ⚠️ Kotlin의 arraycopy가 아니라 System.arraycopy 사용
            System.arraycopy(sweepPCM, playOffset, tmpBytes, 0, chunk)
            val w = track.write(tmpBytes, 0, chunk)
            if (w > 0) playOffset += w else if (w < 0) break

            val rNeed = min(recBuf.size - recOffset, 1024)
            if (rNeed > 0) {
                val r = record.read(recBuf, recOffset, rNeed)
                if (r > 0) recOffset += r
            }
        }

        // tail 녹음(실내 잔향 포획)
        val tailReads = cfg.sampleRate / 4 // ~250ms
        repeat(tailReads) {
            val rNeed = min(recBuf.size - recOffset, 1024)
            if (rNeed <= 0) return@repeat
            val r = record.read(recBuf, recOffset, rNeed)
            if (r > 0) recOffset += r
        }

        // 정리
        runCatching { track.stop() }; track.release()
        runCatching { record.stop() }; record.release()

        // ───────────────────────────────────────────
        // 5) 저장 & 레벨 계산
        // ───────────────────────────────────────────
        val recShort = if (recOffset in 1 until recBuf.size) recBuf.copyOf(recOffset) else recBuf
        val recPcm = ByteArray(recShort.size * 2).also { ba ->
            var bi = 0
            recShort.forEach {
                ba[bi++] = (it.toInt() and 0xFF).toByte()
                ba[bi++] = ((it.toInt() ushr 8) and 0xFF).toByte()
            }
        }
        val recordedFile = File(outDir, "recorded_${System.currentTimeMillis()}.wav")
        writeWavMono16(recordedFile, recPcm, cfg.sampleRate)

        val (peak, rms) = levelDbfs(recShort)

        TestResult(
            recordedWav = recordedFile,
            playedWav   = playedFile,
            peakDbfs    = peak,
            rmsDbfs     = rms,
            durationSec = recShort.size.toFloat() / cfg.sampleRate
        )
    }

}
