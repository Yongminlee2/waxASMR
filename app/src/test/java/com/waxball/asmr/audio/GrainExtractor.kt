package com.waxball.asmr.audio

import java.io.File
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 녹음에서 파열음 하나하나를 잘라내 그레인 뱅크로 만든다. 개발 단계에서만 도는 도구다.
 *
 * 노이즈를 필터에 통과시켜 만든 소리로는 왁스 특유의 음색이 안 나왔다.
 * 그래서 진짜 파열음 파편을 쓰되, 뿌리는 방식은 그대로 둔다.
 * 파편 수백 개를 무작위로 골라 불규칙한 간격으로 뿌리면 반복이 귀에 안 잡힌다.
 */
object GrainExtractor {

    /** 파편 하나. */
    class Fragment(
        val samples: ShortArray,
        val centroidHz: Float,
        val peak: Float,
        val sourceIndex: Int,
    ) {
        val lengthMs: Float get() = samples.size * 1000f / Audition.SAMPLE_RATE
    }

    private const val MIN_MS = 22f

    /**
     * 파열 하나의 여운까지 담는다.
     *
     * 처음에는 140ms에서 잘랐는데, 왁스 파열은 여운이 있어서 그걸 매번 깎아내면
     * 딱딱하고 메마른 소리가 된다. 원본을 통째로 틀었을 때가 더 낫게 들린 이유 중 하나다.
     */
    private const val MAX_MS = 240f

    /** 다음 파열이 시작한 뒤에도 이만큼은 더 담는다. 여운이 겹치는 것이 자연스럽다. */
    private const val TAIL_OVERLAP_MS = 90f

    private const val FADE_IN_MS = 0.3f

    /** 잘린 끝을 다듬는 정도. 길게 잡으면 여운까지 같이 깎인다. */
    private const val FADE_OUT_MS = 14f

    /** 어택·몸통을 판정할 때 보는 앞머리 길이. 뒤에 물고 온 여운은 판정에서 뺀다. */
    private const val HEAD_MS = 80f

    /**
     * 파열 하나를 다음 파열 직전까지, 최대 [MAX_MS]까지 잘라낸다.
     * 너무 짧거나 잡음 바닥에 묻힌 것은 버린다.
     */
    /** 왜 버려졌는지 세어 둔다. 필터가 과하면 여기서 바로 보인다. */
    class Rejections {
        var tooShort = 0
        var tooQuiet = 0
        var slowAttack = 0
        var tooDark = 0
        var weakBody = 0
        var onsets = 0
        override fun toString() =
            "온셋 $onsets · 버림: 짧음 $tooShort, 작음 $tooQuiet, 어택느림 $slowAttack, 어두움 $tooDark, 약함 $weakBody"
    }

    var lastRejections = Rejections()
        private set

    fun extract(wav: LoadedWav, sourceIndex: Int, limit: Int = 400): List<Fragment> {
        val sr = wav.sampleRate
        val x = wav.samples
        val onsets = ReferenceAnalyzer.onsetSamples(wav)
        val reject = Rejections()
        lastRejections = reject
        reject.onsets = onsets.size
        if (onsets.isEmpty()) return emptyList()

        val floor = noiseFloor(x)
        val minLen = (MIN_MS * 0.001f * sr).toInt()
        val maxLen = (MAX_MS * 0.001f * sr).toInt()
        val tailOverlap = (TAIL_OVERLAP_MS * 0.001f * sr).toInt()

        // 녹음 전체의 최고점으로 한 번만 나눈다. 파편마다 따로 정규화하면
        // 작게 바스락거리는 것도 큰 파열만큼 커져서 전부 균일하게 시끄러워진다.
        // 원본의 셈여림이 그대로 남아야 왁스처럼 들린다.
        var globalPeak = 0f
        for (v in x) globalPeak = maxOf(globalPeak, abs(v))
        if (globalPeak < 1e-6f) return emptyList()

        val out = ArrayList<Fragment>()
        for (i in onsets.indices) {
            if (out.size >= limit) break

            // 파열은 검출 프레임보다 조금 앞에서 시작한다. 앞머리가 잘리면 "딱" 하는 맛이 사라진다.
            val start = (onsets[i] - (0.004f * sr).toInt()).coerceAtLeast(0)
            val nextOnset = if (i + 1 < onsets.size) onsets[i + 1] else x.size
            // 다음 파열 직전에서 끊지 않고 여운만큼 더 담는다. 잘라내면 메마른 소리가 된다.
            val end = min(min(start + maxLen, nextOnset + tailOverlap), x.size)
            val len = end - start
            if (len < minLen) { reject.tooShort++; continue }

            var peak = 0f
            for (j in start until end) peak = maxOf(peak, abs(x[j]))
            if (peak < floor * 5f) { reject.tooQuiet++; continue }   // 잡음만 있는 구간
            if (peak < 0.02f) { reject.tooQuiet++; continue }        // 너무 작아 쓸모없음

            val frag = FloatArray(len)
            for (j in 0 until len) frag[j] = x[start + j] / globalPeak

            // 영상에는 목소리·배경음·자막 효과음이 섞여 있다. 그대로 두면 그것까지 파편이 된다.
            // 파열음은 앞머리가 1ms 안에 서지만 목소리나 음악은 그렇지 않다. 이걸로 가른다.
            // 목소리·배경음을 걸러내려고 둔 조건이다. 여운이 긴 녹음에서는 앞 파열의 꼬리가
            // 아직 울리는 중에 다음 파열이 시작돼서, 멀쩡한 파편도 어택이 느리다고 오판된다.
            // 실제로 이 값이 14ms였을 때 온셋 491개 중 404개를 버렸다.
            // 지속음(목소리·음악)만 걸러낼 만큼만 남긴다.
            if (attackMs(frag, sr) > 45f) { reject.slowAttack++; continue }
            // 사람 목소리 대역에 에너지가 몰린 것도 뺀다.
            val centroid = centroidOf(frag, sr)
            if (centroid < 900f) { reject.tooDark++; continue }

            applyFades(frag, sr)
            // 몸통이 있는지 보는 조건이지 크기를 보는 조건이 아니다. 전체 정규화로 바꾼 뒤
            // 절대값으로 재면 작게 바스락거리는 파편이 통째로 걸러진다 — 살리려던 바로 그 소리다.
            // 어택과 같은 이유로 앞머리만 본다.
            if (headBodyRatio(frag, sr) < 0.02f) { reject.weakBody++; continue }

            out.add(
                Fragment(
                    samples = ShortArray(len) { (frag[it].coerceIn(-1f, 1f) * 32767f).toInt().toShort() },
                    centroidHz = centroid,
                    peak = peak,
                    sourceIndex = sourceIndex,
                )
            )
        }
        return out
    }

    /** 앞뒤를 다듬어 이어 붙일 때 딸깍거리지 않게 한다. */
    private fun applyFades(frag: FloatArray, sr: Int) {
        val fadeIn = (FADE_IN_MS * 0.001f * sr).toInt().coerceAtLeast(1)
        val fadeOut = (FADE_OUT_MS * 0.001f * sr).toInt().coerceAtLeast(1)
        for (i in 0 until min(fadeIn, frag.size)) frag[i] *= i.toFloat() / fadeIn
        for (i in 0 until min(fadeOut, frag.size)) {
            val at = frag.size - 1 - i
            frag[at] *= i.toFloat() / fadeOut
        }
    }

    private fun centroidOf(frag: FloatArray, sr: Int): Float {
        val stereo = FloatArray(frag.size * 2)
        for (i in frag.indices) { stereo[i * 2] = frag[i]; stereo[i * 2 + 1] = frag[i] }
        val c = Spectrum.centroid(stereo, sr)
        return if (c > 0f) c else 2000f
    }

    /**
     * 앞머리가 최고점의 80%에 닿기까지 걸린 시간. 파열음은 아주 짧다.
     *
     * 파편 전체에서 최고점을 찾으면 안 된다. 여운을 남기려고 다음 파열까지 물고 오기
     * 때문에, 뒤에 더 큰 파열이 들어 있으면 앞의 멀쩡한 파열이 "어택이 느리다"고
     * 오판된다. 실제로 그렇게 315개만 남고 176개를 버렸다. 앞머리만 본다.
     */
    private fun attackMs(frag: FloatArray, sr: Int): Float {
        val head = min((HEAD_MS * 0.001f * sr).toInt(), frag.size)
        var peak = 0f
        for (i in 0 until head) peak = maxOf(peak, abs(frag[i]))
        if (peak <= 0f) return Float.MAX_VALUE
        val target = peak * 0.8f
        for (i in 0 until head) {
            if (abs(frag[i]) >= target) return i * 1000f / sr
        }
        return Float.MAX_VALUE
    }

    /** 앞머리의 실효값 ÷ 앞머리의 최고점. 딸깍 하나뿐인 파편은 이 값이 아주 작다. */
    private fun headBodyRatio(frag: FloatArray, sr: Int): Float {
        val head = min((HEAD_MS * 0.001f * sr).toInt(), frag.size)
        if (head <= 0) return 0f
        var peak = 0f
        var sum = 0.0
        for (i in 0 until head) {
            val v = frag[i]
            peak = maxOf(peak, abs(v))
            sum += v.toDouble() * v
        }
        if (peak <= 1e-9f) return 0f
        return (sqrt(sum / head) / peak).toFloat()
    }

    private fun noiseFloor(x: FloatArray): Float {
        val block = 1024
        if (x.size < block * 4) return 1e-5f
        val levels = ArrayList<Float>(x.size / block)
        var i = 0
        while (i + block <= x.size) {
            var peak = 0f
            for (j in i until i + block) peak = maxOf(peak, abs(x[j]))
            levels.add(peak)
            i += block
        }
        levels.sort()
        return levels[(levels.size * 0.1f).toInt().coerceIn(0, levels.size - 1)].coerceAtLeast(1e-6f)
    }

    /**
     * 뱅크를 assets에 쓴다.
     *
     * bin  : 파편 샘플을 죽 이어붙인 16bit PCM
     * idx  : 한 줄에 파편 하나 — 시작오프셋,길이,중심주파수,출처
     */
    fun writeBank(fragments: List<Fragment>, binFile: File, idxFile: File) {
        binFile.parentFile?.mkdirs()

        var total = 0
        for (f in fragments) total += f.samples.size
        val bytes = ByteArray(total * 2)
        val idx = StringBuilder()

        var offset = 0
        for (f in fragments) {
            idx.append(offset).append(',')
                .append(f.samples.size).append(',')
                .append(f.centroidHz.toInt()).append(',')
                .append(f.sourceIndex).append('\n')
            for (s in f.samples) {
                val v = s.toInt()
                bytes[offset * 2] = (v and 0xFF).toByte()
                bytes[offset * 2 + 1] = ((v shr 8) and 0xFF).toByte()
                offset++
            }
        }

        binFile.writeBytes(bytes)
        idxFile.writeText(idx.toString())
    }
}
