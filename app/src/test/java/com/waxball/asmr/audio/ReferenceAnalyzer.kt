package com.waxball.asmr.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.sqrt

/**
 * 실제로 녹음한 왁뿌볼 소리에서 합성 파라미터를 뽑아낸다.
 *
 * 귀로 "좀 더 낮게"를 반복하는 것보다, 정답지의 수치를 재서 맞추는 편이 빠르고 정확하다.
 * 여기서 나온 값은 출발점이고, 마지막 손질은 여전히 사람 귀가 한다.
 *
 * 테스트 전용이라 앱에는 들어가지 않는다.
 */
object ReferenceAnalyzer {

    private const val WIN = 1024
    private const val HOP = 256

    class Report(
        val durationSec: Float,
        val onsetsPerSecond: Float,
        val meanGapMs: Float,
        val gapSpread: Float,
        val centroidHz: Float,
        val spreadOctaves: Float,
        val medianDecayMs: Float,
        val lowRatio: Float,
        val midRatio: Float,
        val highRatio: Float,
        val flatness: Float,
    ) {
        override fun toString(): String = buildString {
            appendLine("길이            %.1f초".format(durationSec))
            appendLine("파열 빈도       초당 %.1f회".format(onsetsPerSecond))
            appendLine("파열 간격       평균 %.1fms (편차 %.2f)".format(meanGapMs, gapSpread))
            appendLine("스펙트럼 중심   %.0fHz".format(centroidHz))
            appendLine("음높이 산포     %.2f 옥타브".format(spreadOctaves))
            appendLine("감쇠 시간       중앙값 %.0fms".format(medianDecayMs))
            appendLine("대역 에너지     저역 %.0f%% / 중역 %.0f%% / 고역 %.0f%%"
                .format(lowRatio * 100, midRatio * 100, highRatio * 100))
            appendLine("스펙트럼 평탄도 %.2f  (0=음정 있음, 1=잡음에 가까움)".format(flatness))
        }

        /** 바로 붙여 넣을 수 있는 SoundProfile 초안. */
        fun toProfileSource(name: String = "recorded"): String {
            val decayMin = (medianDecayMs * 0.5f).coerceIn(3f, 60f)
            val decayMax = (medianDecayMs * 1.9f).coerceIn(decayMin + 2f, 160f)
            val resonance = (1f - flatness).coerceIn(0.15f, 0.85f)
            val q = (2f + (1f - flatness) * 12f).coerceIn(2f, 12f)
            val density = (onsetsPerSecond / 260f).coerceIn(0.3f, 2.2f)
            val gapScale = (meanGapMs / 3.5f).coerceIn(0.3f, 4f)
            val body = (lowRatio / 0.22f).coerceIn(0f, 1.5f)
            val brightness = (highRatio / 0.25f).coerceIn(0.4f, 2f)

            return """
                |/** $name 녹음에서 뽑은 프로파일. */
                |fun $name() = SoundProfile(
                |    baseFreq = ${"%.0f".format(centroidHz)}f,
                |    freqSpread = ${"%.2f".format(spreadOctaves.coerceIn(0.3f, 1.6f))}f,
                |    q = ${"%.1f".format(q)}f,
                |    decayMsMin = ${"%.0f".format(decayMin)}f,
                |    decayMsMax = ${"%.0f".format(decayMax)}f,
                |    resonance = ${"%.2f".format(resonance)}f,
                |    density = ${"%.2f".format(density)}f,
                |    toughness = 1.0f,
                |    propagation = 0.45f,
                |    brightness = ${"%.2f".format(brightness)}f,
                |    body = ${"%.2f".format(body)}f,
                |    gapScale = ${"%.2f".format(gapScale)}f,
                |)
            """.trimMargin()
        }
    }

    fun analyze(wav: LoadedWav): Report {
        val x = normalize(wav.samples)
        val sr = wav.sampleRate
        require(x.size > WIN * 4) { "분석하기엔 너무 짧다" }

        val frames = (x.size - WIN) / HOP
        val window = FloatArray(WIN) { 0.5f - 0.5f * cos(2.0 * PI * it / (WIN - 1)).toFloat() }
        val re = FloatArray(WIN)
        val im = FloatArray(WIN)

        val bins = WIN / 2
        val mags = Array(frames) { FloatArray(bins) }
        val flux = FloatArray(frames)

        for (f in 0 until frames) {
            val off = f * HOP
            for (i in 0 until WIN) { re[i] = x[off + i] * window[i]; im[i] = 0f }
            Spectrum.fft(re, im)
            var prevSum = 0f
            for (k in 0 until bins) {
                val m = sqrt(re[k] * re[k] + im[k] * im[k])
                mags[f][k] = m
                if (f > 0) {
                    val d = m - mags[f - 1][k]
                    if (d > 0) prevSum += d
                }
            }
            flux[f] = prevSum
        }

        val onsets = pickOnsets(flux)
        val frameSec = HOP.toFloat() / sr

        // 파열 간격
        val gaps = ArrayList<Float>()
        for (i in 1 until onsets.size) gaps.add((onsets[i] - onsets[i - 1]) * frameSec * 1000f)
        val meanGap = if (gaps.isEmpty()) 0f else gaps.average().toFloat()
        val gapSpread = if (gaps.size < 2 || meanGap <= 0f) 0f else {
            val v = gaps.sumOf { ((it - meanGap) * (it - meanGap)).toDouble() } / gaps.size
            (sqrt(v).toFloat() / meanGap)
        }

        // 주파수 통계는 파열이 있는 프레임만 본다. 무음 구간이 섞이면 중심이 흐려진다.
        val loud = mags.indices.filter { f -> mags[f].sum() > 0f }
            .sortedByDescending { f -> mags[f].sum() }
            .take((frames * 0.35f).toInt().coerceAtLeast(4))

        var weighted = 0.0
        var total = 0.0
        var low = 0.0; var mid = 0.0; var high = 0.0
        var logSum = 0.0
        var linSum = 0.0
        var logCount = 0

        for (f in loud) {
            for (k in 1 until bins) {
                val hz = k.toFloat() * sr / WIN
                val m = mags[f][k].toDouble()
                weighted += m * hz
                total += m
                when {
                    hz < 500f -> low += m
                    hz < 3000f -> mid += m
                    else -> high += m
                }
                if (hz in 100f..12000f) {
                    logSum += ln((m + 1e-9).coerceAtLeast(1e-9))
                    linSum += m
                    logCount++
                }
            }
        }

        val centroid = if (total <= 0.0) 0f else (weighted / total).toFloat()
        val bandTotal = (low + mid + high).coerceAtLeast(1e-9)
        val flatness = if (logCount == 0 || linSum <= 0.0) 1f else {
            (exp(logSum / logCount) / (linSum / logCount)).toFloat().coerceIn(0f, 1f)
        }

        // 중심 주파수 기준 산포를 옥타브로
        var spreadAcc = 0.0
        var spreadWeight = 0.0
        if (centroid > 0f) {
            for (f in loud) {
                for (k in 1 until bins) {
                    val hz = k.toFloat() * sr / WIN
                    if (hz < 60f) continue
                    val m = mags[f][k].toDouble()
                    val d = log2(hz / centroid).toDouble()
                    spreadAcc += m * d * d
                    spreadWeight += m
                }
            }
        }
        val spreadOct = if (spreadWeight <= 0.0) 0.8f else sqrt(spreadAcc / spreadWeight).toFloat()

        return Report(
            durationSec = x.size.toFloat() / sr,
            onsetsPerSecond = onsets.size / (x.size.toFloat() / sr),
            meanGapMs = meanGap,
            gapSpread = gapSpread,
            centroidHz = centroid,
            spreadOctaves = spreadOct,
            medianDecayMs = medianDecay(x, sr, onsets, frameSec),
            lowRatio = (low / bandTotal).toFloat(),
            midRatio = (mid / bandTotal).toFloat(),
            highRatio = (high / bandTotal).toFloat(),
            flatness = flatness,
        )
    }

    /**
     * 이동 중앙값 위로 튀는 지점을 파열로 본다. 고정 임계는 녹음 음량에 휘둘린다.
     *
     * 한 번 잡은 뒤에는 플럭스가 임계 아래로 충분히 내려가야 다시 잡는다.
     * 이 빗장이 없으면 파열 하나의 앞뒤 봉우리를 두 번 세서 간격이 절반으로 측정된다.
     */
    private fun pickOnsets(flux: FloatArray): List<Int> {
        if (flux.isEmpty()) return emptyList()
        val window = 21
        val result = ArrayList<Int>()
        var lastPick = -99
        var armed = true

        // 파열 사이의 조용한 구간에서는 이동 중앙값이 0으로 내려앉아 임계가 무너진다.
        // 그러면 수치 잡음까지 파열로 세서 간격이 절반으로 측정된다. 전역 하한을 둔다.
        var sum = 0.0
        for (v in flux) sum += v
        val floor = (sum / flux.size).toFloat() * 0.9f

        for (f in 1 until flux.size - 1) {
            val from = (f - window).coerceAtLeast(0)
            val to = (f + window).coerceAtMost(flux.size - 1)
            val local = flux.copyOfRange(from, to + 1)
            local.sort()
            val median = local[local.size / 2]
            val threshold = maxOf(median * 2.2f, floor) + 1e-6f

            if (!armed) {
                if (flux[f] < threshold * 0.45f) armed = true
                continue
            }

            val isPeak = flux[f] >= flux[f - 1] && flux[f] >= flux[f + 1]
            if (flux[f] > threshold && isPeak && f - lastPick >= MIN_ONSET_FRAMES) {
                result.add(f)
                lastPick = f
                armed = false
            }
        }
        return result
    }

    /** 파열 두 개를 구분하는 최소 간격. 사람이 낱개로 들을 수 있는 한계쯤이다. */
    private const val MIN_ONSET_FRAMES = 3

    /** 파열 직후 20dB 떨어지는 데 걸리는 시간을 재서 60dB 기준으로 환산한다. */
    private fun medianDecay(x: FloatArray, sr: Int, onsets: List<Int>, frameSec: Float): Float {
        if (onsets.isEmpty()) return 20f
        val decays = ArrayList<Float>()
        val limit = (0.25f * sr).toInt()

        for (o in onsets) {
            val start = (o * frameSec * sr).toInt()
            if (start + 64 >= x.size) continue
            var peak = 0f
            for (i in start until minOf(start + 256, x.size)) peak = maxOf(peak, abs(x[i]))
            if (peak < 1e-4f) continue

            val target = peak * 0.1f      // -20dB
            var hit = -1
            var i = start
            while (i < minOf(start + limit, x.size)) {
                var localPeak = 0f
                for (j in i until minOf(i + 64, x.size)) localPeak = maxOf(localPeak, abs(x[j]))
                if (localPeak < target) { hit = i; break }
                i += 64
            }
            if (hit > start) decays.add((hit - start) * 3000f / sr)   // T20 → T60
        }
        if (decays.isEmpty()) return 20f
        decays.sort()
        return decays[decays.size / 2]
    }

    private fun normalize(x: FloatArray): FloatArray {
        var peak = 0f
        for (v in x) peak = maxOf(peak, abs(v))
        if (peak < 1e-6f) return x
        val g = 0.98f / peak
        return FloatArray(x.size) { x[it] * g }
    }
}
