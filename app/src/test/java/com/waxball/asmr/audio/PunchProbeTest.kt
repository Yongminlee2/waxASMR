package com.waxball.asmr.audio

import com.waxball.asmr.core.SoundProfile
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * "시원함"과 "깊이감"을 수치로 재서 녹음과 맞대 본다.
 *
 * 같은 종류의 앱에서 소리를 바꿨다가 "터뜨리는 맛이 없다", "시원한 소리가 없다",
 * "깊이감이 없어졌다"는 평을 받은 사례가 있다. 둘 다 잴 수 있는 값이다.
 *
 * - 시원함 = 크레스트(피크 ÷ 실효값). 순간적으로 확 솟는 정도.
 *   리미터가 세게 걸리면 이 값이 무너지고 소리가 납작해진다
 * - 깊이감 = 저역 비중
 */
class PunchProbeTest {

    private val sr = Audition.SAMPLE_RATE

    private fun crest(mono: FloatArray): Float {
        var peak = 0f
        var sum = 0.0
        for (v in mono) {
            val a = abs(v)
            if (a > peak) peak = a
            sum += v.toDouble() * v
        }
        val rms = sqrt(sum / mono.size).toFloat()
        return if (rms <= 1e-9f) 0f else peak / rms
    }

    /** 500Hz 아래가 전체에서 차지하는 비중. */
    private fun lowRatio(stereo: FloatArray): Float {
        val report = ReferenceAnalyzer.analyze(LoadedWav(Spectrum.left(stereo), sr, 1))
        return report.lowRatio
    }

    @Test
    fun comparePunchAgainstTheRecording() {
        val lines = StringBuilder()

        val reference = File("reference/src1.wav")
        if (reference.exists()) {
            val wav = WavReader.read(reference)
            val stereo = FloatArray(wav.samples.size * 2)
            for (i in wav.samples.indices) {
                stereo[i * 2] = wav.samples[i]; stereo[i * 2 + 1] = wav.samples[i]
            }
            lines.appendLine(
                "녹음        크레스트 %.1f · 저역 %.0f%%".format(crest(wav.samples), lowRatio(stereo) * 100)
            )
        }

        for ((name, profile) in listOf(
            "굳은왁스" to SoundProfile.hardWax(),
            "무른왁스" to SoundProfile.softWax(),
            "설탕유리" to SoundProfile.sugarGlass(),
        )) {
            val stereo = Audition.playSession(profile, 6f, 7)
            lines.appendLine(
                "%-10s 크레스트 %.1f · 저역 %.0f%%".format(name, crest(Spectrum.left(stereo)), lowRatio(stereo) * 100)
            )
        }

        // 한 번의 큰 분리음만 따로. 여기가 "터뜨리는 맛"이 가장 잘 드러난다.
        val single = Synth(sr).apply { setProfile(SoundProfile.hardWax()) }
        single.on(com.waxball.asmr.core.EventKind.DETACH, 0, 4, 1f, 0f, 0.18f)
        val burst = single.renderOffline(1f)
        lines.appendLine(
            "큰조각분리  크레스트 %.1f · 저역 %.0f%%".format(crest(Spectrum.left(burst)), lowRatio(burst) * 100)
        )

        File("build/punch-report.txt").apply {
            parentFile?.mkdirs()
            writeText(lines.toString())
        }
    }
}
