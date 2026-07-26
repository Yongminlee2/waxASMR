package com.waxball.asmr.audio

import com.waxball.asmr.core.SoundProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 실제 녹음이 있으면 거기서 합성 파라미터를 뽑아 출력한다.
 *
 * `app/reference/` 에 WAV를 넣고 `test.bat`을 돌리면
 * 그대로 붙여 넣을 수 있는 SoundProfile 초안이 콘솔과
 * `app/build/reference-report.txt` 에 나온다.
 *
 * 녹음이 없으면 조용히 지나간다. 분석기 자체는 아래에서 합성음으로 검증한다.
 */
class ReferenceAnalyzerTest {

    private val referenceDir = File("reference")

    @Test
    fun analyzeRecordingsIfPresent() {
        val wavs = referenceDir.listFiles { f -> f.extension.lowercase() == "wav" }
            ?.sortedBy { it.name }
            ?: emptyList()

        if (wavs.isEmpty()) {
            println("[분석] app/reference/ 에 WAV가 없어 건너뜀. 실제 왁뿌볼 녹음을 넣으면 파라미터를 뽑아 준다.")
            return
        }

        val report = StringBuilder()
        for (file in wavs) {
            val wav = WavReader.read(file)
            val r = ReferenceAnalyzer.analyze(wav)
            val name = file.nameWithoutExtension.filter { it.isLetterOrDigit() }.ifBlank { "recorded" }

            report.appendLine("=== ${file.name} (${wav.sampleRate}Hz, ${wav.channels}ch) ===")
            report.appendLine(r.toString())
            report.appendLine(r.toProfileSource(name))
            report.appendLine()
        }

        print(report)
        File("build/reference-report.txt").apply {
            parentFile?.mkdirs()
            writeText(report.toString())
        }
    }

    // --- 분석기 자체 검증: 특성을 아는 합성음을 넣어 되찾아오는지 본다 ---

    private fun renderedSession(profile: SoundProfile, seconds: Float = 5f) =
        LoadedWav(
            Spectrum.left(Audition.playSession(profile, seconds, 3)),
            Audition.SAMPLE_RATE,
            1,
        )

    @Test
    fun recoversDurationAndFindsCracks() {
        val r = ReferenceAnalyzer.analyze(renderedSession(SoundProfile.hardWax()))
        assertEquals(5f, r.durationSec, 0.05f)
        assertTrue("파열을 하나도 못 찾음", r.onsetsPerSecond > 1f)
        assertTrue("파열이 비현실적으로 많음: ${r.onsetsPerSecond}", r.onsetsPerSecond < 400f)
    }

    @Test
    fun brightMaterialMeasuresHigherThanDarkOne() {
        val dark = ReferenceAnalyzer.analyze(renderedSession(SoundProfile.softWax()))
        val bright = ReferenceAnalyzer.analyze(renderedSession(SoundProfile.sugarGlass()))
        assertTrue(
            "밝은 재질을 더 낮게 잼 (어두움 ${dark.centroidHz}Hz, 밝음 ${bright.centroidHz}Hz)",
            bright.centroidHz > dark.centroidHz * 1.3f,
        )
    }

    /**
     * 정답을 아는 신호로 분석기를 검증한다.
     * 플레이 전체를 넣으면 손가락 옮기는 간격이 파열 간격을 덮어써서 측정이 흐려진다.
     */
    private fun clicks(intervalMs: Float, freqHz: Float, seconds: Float = 4f): LoadedWav {
        val sr = Audition.SAMPLE_RATE
        val x = FloatArray((seconds * sr).toInt())
        val step = (intervalMs * 0.001f * sr).toInt().coerceAtLeast(16)
        val decay = kotlin.math.exp(-6.9f / (0.012f * sr))
        var at = step
        while (at < x.size - 1) {
            var env = 1f
            var i = at
            while (i < x.size && env > 1e-4f) {
                x[i] += kotlin.math.sin(2f * Math.PI.toFloat() * freqHz * (i - at) / sr) * env
                env *= decay
                i++
            }
            at += step
        }
        return LoadedWav(x, sr, 1)
    }

    @Test
    fun measuresKnownClickInterval() {
        val r = ReferenceAnalyzer.analyze(clicks(intervalMs = 60f, freqHz = 2000f))
        assertEquals("파열 간격을 잘못 잼", 60f, r.meanGapMs, 12f)
        assertEquals("파열 빈도를 잘못 잼", 16.7f, r.onsetsPerSecond, 4f)
    }

    @Test
    fun measuresKnownPitch() {
        val low = ReferenceAnalyzer.analyze(clicks(60f, 300f))
        val high = ReferenceAnalyzer.analyze(clicks(60f, 6000f))
        assertTrue("낮은 소리를 낮게 못 잼: ${low.centroidHz}Hz", low.centroidHz < 1500f)
        assertTrue("높은 소리를 높게 못 잼: ${high.centroidHz}Hz", high.centroidHz > 3000f)
        assertTrue("저역 신호인데 저역 비중이 낮음: ${low.lowRatio}", low.lowRatio > high.lowRatio * 3f)
    }

    @Test
    fun measuresKnownDecay() {
        // 위 clicks()는 T60 12ms로 감쇠한다.
        val r = ReferenceAnalyzer.analyze(clicks(120f, 2000f))
        assertTrue("감쇠 시간이 비현실적: ${r.medianDecayMs}ms", r.medianDecayMs in 3f..60f)
    }

    @Test
    fun tonalSignalMeasuresLessFlatThanNoise() {
        val tonal = ReferenceAnalyzer.analyze(clicks(80f, 1500f))
        val noisy = ReferenceAnalyzer.analyze(renderedSession(SoundProfile.hardWax()))
        assertTrue(
            "음정 있는 소리와 잡음을 구분 못 함 (음정 ${tonal.flatness}, 잡음 ${noisy.flatness})",
            tonal.flatness < noisy.flatness,
        )
    }

    @Test
    fun producesCompilableLookingProfileSource() {
        val src = ReferenceAnalyzer.analyze(renderedSession(SoundProfile.hardWax())).toProfileSource("test")
        listOf("baseFreq", "freqSpread", "decayMsMin", "decayMsMax", "resonance", "density", "body", "gapScale")
            .forEach { assertTrue("$it 누락", src.contains(it)) }
        assertTrue(src.contains("fun test()"))
    }

    @Test
    fun wavRoundTripsThroughWriterAndReader() {
        val original = Audition.playSession(SoundProfile.hardWax(), 1f, 5)
        val file = File("build/roundtrip.wav")
        Audition.writeWav(file, original)

        val back = WavReader.read(file)
        assertEquals(Audition.SAMPLE_RATE, back.sampleRate)
        assertEquals(2, back.channels)
        assertEquals(original.size / 2, back.samples.size)
        assertTrue("읽어들인 소리가 무음", Spectrum.rms(back.samples) > 1e-4f)
    }
}
