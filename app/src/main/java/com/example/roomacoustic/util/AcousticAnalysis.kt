package com.example.roomacoustic.util

import kotlin.math.*
import com.example.roomacoustic.audio.TestConfig

/**
 * RT60, C50, C80 계산 유틸.
 * 전제: 녹음 신호는 로그 스윕 재생에 대한 응답(스윕 응답)이며,
 *       TestConfig와 동일한 파라미터로 스윕이 만들어졌다고 가정.
 */
data class AcousticMetrics(
    val rt60Sec: Float?,   // null이면 추정 불가
    val tMethod: String?,  // "T30", "T20" 등
    val c50dB: Float?,
    val c80dB: Float?
)

/** Short PCM(-32768..32767) → Float(-1..1) */
private fun pcm16ToFloat(pcm: ShortArray): FloatArray =
    FloatArray(pcm.size) { i -> pcm[i] / 32768f }

/** 로그 스윕(전방) 생성: DuplexMeasurer.buildSweep과 동일한 수식 (Farina) */
private fun generateLogSweep(cfg: TestConfig): FloatArray {
    val sr = cfg.sampleRate
    val Nhead = (cfg.headSilenceSec * sr).toInt()
    val Ntail = (cfg.tailSilenceSec * sr).toInt()
    val Nsweep = (cfg.sweepSec * sr).toInt()

    val k = ln(cfg.fEnd / cfg.fStart) / cfg.sweepSec
    val sweep = FloatArray(Nsweep) { n ->
        val t = n / sr.toFloat()
        val phase = (2.0 * Math.PI * cfg.fStart * ((exp(k * t) - 1f) / k)).toFloat()
        (sin(phase) * cfg.volume).toFloat()
    }
    val signal = FloatArray(Nhead + Nsweep + Ntail)
    System.arraycopy(sweep, 0, signal, Nhead, Nsweep)
    return signal
}

/** Farina inverse sweep: time-reversed + exp(k t) 보정 */
private fun generateInverseSweep(cfg: TestConfig): FloatArray {
    val sr = cfg.sampleRate
    val raw = generateLogSweep(cfg) // head/tail 포함 시그널
    val N = raw.size
    val inv = FloatArray(N)
    val k = ln(cfg.fEnd / cfg.fStart) / cfg.sweepSec

    // head/tail 포함한 전체 신호를 뒤집되, exp(k t) 보정 가중
    for (n in 0 until N) {
        val t = n / sr.toFloat()
        val w = exp(k * t) // 보정
        inv[n] = raw[N - 1 - n] * w
    }
    return inv
}

/** 실수 FFT (in-place, Cooley–Tukey). inverse=false: FFT, inverse=true: IFFT */
private fun fft(re: FloatArray, im: FloatArray, inverse: Boolean) {
    val n = re.size
    // bit-reversal
    var j = 0
    for (i in 1 until n - 1) {
        var bit = n shr 1
        while (j >= bit) { j -= bit; bit = bit shr 1 }
        j += bit
        if (i < j) {
            val tr = re[i]; re[i] = re[j]; re[j] = tr
            val ti = im[i]; im[i] = im[j]; im[j] = ti
        }
    }
    var len = 2
    val sgn = if (inverse) 1f else -1f
    while (len <= n) {
        val half = len shr 1
        val theta = (2.0 * Math.PI / len * sgn).toFloat()
        val wpr = cos(theta)
        val wpi = sin(theta)
        var i = 0
        while (i < n) {
            var wr = 1f; var wi = 0f
            for (k in 0 until half) {
                val i0 = i + k
                val i1 = i0 + half
                val tr = wr * re[i1] - wi * im[i1]
                val ti = wr * im[i1] + wi * re[i1]
                re[i1] = re[i0] - tr
                im[i1] = im[i0] - ti
                re[i0] += tr
                im[i0] += ti
                val wrn = wr * wpr - wi * wpi
                val win = wr * wpi + wi * wpr
                wr = wrn; wi = win
            }
            i += len
        }
        len = len shl 1
    }
    if (inverse) {
        val invN = 1f / n.toFloat()
        for (i in 0 until n) {
            re[i] = re[i] * invN
            im[i] = im[i] * invN
        }
    }
}

/** 두 신호(a,b) FFT 컨볼루션 → 길이 a.size + b.size - 1 */
private fun fftConvolve(a: FloatArray, b: FloatArray): FloatArray {
    val nConv = a.size + b.size - 1
    var nFFT = 1
    while (nFFT < nConv) nFFT = nFFT shl 1

    val are = FloatArray(nFFT)
    val aim = FloatArray(nFFT)
    val bre = FloatArray(nFFT)
    val bim = FloatArray(nFFT)

    System.arraycopy(a, 0, are, 0, a.size)
    System.arraycopy(b, 0, bre, 0, b.size)

    fft(are, aim, false)
    fft(bre, bim, false)

    for (i in 0 until nFFT) {
        val r = are[i] * bre[i] - aim[i] * bim[i]
        val im = are[i] * bim[i] + aim[i] * bre[i]
        are[i] = r
        aim[i] = im
    }
    fft(are, aim, true)

    val out = FloatArray(nConv)
    System.arraycopy(are, 0, out, 0, nConv)
    return out
}

/** deconvolution: IR ≈ recorded ⊗ inverseSweep */
private fun estimateImpulseResponse(
    recorded: FloatArray,
    cfg: TestConfig
): FloatArray {
    val inv = generateInverseSweep(cfg)
    val irFull = fftConvolve(recorded, inv)

    // IR 정렬: 최대 피크를 0으로 맞추고 앞뒤 트리밍
    var peakIdx = 0
    var peakVal = 0f
    for (i in irFull.indices) {
        val a = abs(irFull[i])
        if (a > peakVal) { peakVal = a; peakIdx = i }
    }
    // 직접음 피크 주변으로 윈도우 자르기 (앞 20ms, 뒤 ~3s)
    val sr = cfg.sampleRate
    val pre = (0.02f * sr).toInt()
    val post = (3.0f * sr).toInt().coerceAtMost(irFull.size - peakIdx)
    val start = (peakIdx - pre).coerceAtLeast(0)
    val end = (peakIdx + post).coerceAtMost(irFull.size)
    return irFull.copyOfRange(start, end)
}

/** Schroeder 적분으로 EDC(dB) 생성 */
private fun schroederEDC(ir: FloatArray): FloatArray {
    // 에너지(제곱)
    val e = FloatArray(ir.size) { i -> ir[i] * ir[i] }
    // 역누적합
    var acc = 0.0
    val edcLin = DoubleArray(e.size)
    for (i in e.indices.reversed()) {
        acc += e[i]
        edcLin[i] = acc
    }
    // 0 dB 정규화 → dB 스케일
    val edc0 = edcLin[0].coerceAtLeast(1e-20)
    val edcDb = FloatArray(e.size) { i ->
        (10.0 * ln(edcLin[i] / edc0) / ln(10.0)).toFloat()
    }
    return edcDb
}

/* 선형회귀로 구간[dB1..dB2]의 기울기(dB/샘플) → RT60 추정 */
private fun rt60FromEdc(
    edcDb: FloatArray,
    sampleRate: Int,
    dB1: Float,  // 예: -5
    dB2: Float   // 예: -35 (T30) 또는 -25 (T20)
): Float? {
    fun findIndexAt(dB: Float): Int? {
        // edcDb는 0에서 음수로 감소 → dB에 가장 가까운 지점
        var bestIdx = -1
        var bestErr = Float.POSITIVE_INFINITY
        for (i in edcDb.indices) {
            val err = abs(edcDb[i] - dB)
            if (err < bestErr) { bestErr = err; bestIdx = i }
        }
        return if (bestIdx >= 0) bestIdx else null
    }
    val i1 = findIndexAt(dB1) ?: return null
    val i2 = findIndexAt(dB2) ?: return null
    if (i2 <= i1) return null

    // 최소자승 직선 적합 (x: time[s], y: edc[dB])
    val n = (i2 - i1 + 1)
    var sumx = 0.0; var sumy = 0.0; var sumxy = 0.0; var sumx2 = 0.0
    for (k in i1..i2) {
        val x = k / sampleRate.toDouble()
        val y = edcDb[k].toDouble()
        sumx += x; sumy += y; sumxy += x * y; sumx2 += x * x
    }
    val denom = n * sumx2 - sumx * sumx
    if (abs(denom) < 1e-12) return null
    val slope = (n * sumxy - sumx * sumy) / denom // dB per second (음수)
    if (slope >= 0) return null
    val rt60 = -60.0 / slope
    return rt60.toFloat()
}

/* Cx(dB) = 10*log10( E[0..xms] / E[xms..inf] ) */
private fun clarityCx(ir: FloatArray, sampleRate: Int, xMs: Int): Float? {
    val split = (xMs / 1000.0 * sampleRate).roundToInt().coerceIn(0, ir.size)
    var early = 0.0
    var late = 0.0
    for (i in 0 until split) early += (ir[i] * ir[i]).toDouble()
    for (i in split until ir.size) late += (ir[i] * ir[i]).toDouble()
    if (late <= 0.0) return null
    val c = 10.0 * ln((early + 1e-20) / (late + 1e-20)) / ln(10.0)
    return c.toFloat()
}

/**
 * 최종 메인 함수: 녹음 PCM + 샘플레이트 → RT60, C50, C80 계산
 * - TestConfig는 현재 저장되어 있지 않아, 기본값을 사용(확실하지 않음).
 *   추후 DB에 TestConfig 저장을 권장.
 */
fun computeAcousticMetrics(
    pcm: ShortArray,
    sampleRate: Int,
    assumedConfig: TestConfig = TestConfig(sampleRate = sampleRate) // fStart~tailSec 기본값 가정
): AcousticMetrics {
    val rec = pcm16ToFloat(pcm)
    val ir = estimateImpulseResponse(rec, assumedConfig)
    val edc = schroederEDC(ir)

    // T30 우선, 실패 시 T20
    val rt60T30 = rt60FromEdc(edc, sampleRate, dB1 = -5f, dB2 = -35f)
    val rt60T20 = rt60FromEdc(edc, sampleRate, dB1 = -5f, dB2 = -25f)
    val (rt60, method) = when {
        rt60T30 != null -> rt60T30 to "T30"
        rt60T20 != null -> rt60T20 to "T20"
        else -> null to null
    }

    val c50 = clarityCx(ir, sampleRate, 50)
    val c80 = clarityCx(ir, sampleRate, 80)

    return AcousticMetrics(
        rt60Sec = rt60,
        tMethod = method,
        c50dB = c50,
        c80dB = c80
    )
}
