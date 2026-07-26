package com.waxball.asmr.audio

import com.waxball.asmr.core.SoundProfile
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 소리 성격이 뚜렷하게 다른 후보들을 만들어 `app/build/variants/`에 떨어뜨린다.
 *
 * 합성 파라미터가 수치 기준을 다 통과해도 "진짜 왁뿌볼 같은가"는 사람이 골라야 한다.
 * 축을 하나씩만 건드린 후보를 나란히 두면 어느 방향으로 가야 할지 바로 나온다.
 */
class SoundVariantsTest {

    private val outDir = File("build/variants")
    private val base = SoundProfile.hardWax()

    private val variants: List<Pair<String, SoundProfile>> = listOf(
        "1-현재" to base,

        // 지금 소리가 높고 쉬익거린다는 지적에 대한 후보
        "2-낮고건조하게" to base.copy(
            baseFreq = 1450f,
            freqSpread = 0.55f,
            q = 4.5f,
            decayMsMin = 10f, decayMsMax = 34f,
            resonance = 0.5f,
            body = 0.8f,
        ),

        // 파열이 뭉쳐서 잡음처럼 들린다는 지적에 대한 후보
        "3-굵고성기게" to base.copy(
            density = 0.4f,
            gapScale = 2.8f,
            resonance = 0.5f,
            body = 0.7f,
        ),

        // 저역 몸통이 없어서 얇다는 지적에 대한 후보
        "4-으스러짐강조" to base.copy(
            baseFreq = 1900f,
            body = 1.0f,
            resonance = 0.55f,
            decayMsMin = 10f, decayMsMax = 32f,
        ),

        // 위 셋을 다 반영한 후보. 지금과 가장 멀다
        "5-전부반영" to base.copy(
            baseFreq = 1350f,
            freqSpread = 0.5f,
            q = 4f,
            decayMsMin = 12f, decayMsMax = 38f,
            resonance = 0.6f,
            density = 0.5f,
            gapScale = 2.2f,
            body = 1.0f,
        ),
    )

    @Test
    fun writeVariantSamples() {
        outDir.mkdirs()
        for ((name, profile) in variants) {
            val buf = Audition.playSession(profile, seconds = 7f, seed = 11)
            Audition.writeWav(File(outDir, "$name.wav"), buf)

            val rms = Spectrum.rms(buf)
            val peak = buf.maxOf { kotlin.math.abs(it) }
            assertTrue("$name 이 무음", rms > 1e-4f)
            assertTrue("$name 이 클리핑", peak < 1f)
        }
    }

    @Test
    fun variantsActuallySoundDifferent() {
        // 후보끼리 구분이 안 되면 고르게 하는 의미가 없다.
        val centroids = variants.map { (name, profile) ->
            name to Spectrum.centroid(Audition.playSession(profile, 3f, 11), Audition.SAMPLE_RATE)
        }
        val values = centroids.map { it.second }
        assertTrue(
            "후보들의 음색 차이가 너무 작다: $centroids",
            values.max() > values.min() * 1.4f,
        )
    }

    @Test
    fun lowerVariantsAreActuallyLower() {
        fun centroidOf(name: String): Float {
            val p = variants.first { it.first == name }.second
            return Spectrum.centroid(Audition.playSession(p, 3f, 11), Audition.SAMPLE_RATE)
        }
        assertTrue("낮게 만든 후보가 안 낮음", centroidOf("2-낮고건조하게") < centroidOf("1-현재"))
        assertTrue("전부반영이 현재보다 안 낮음", centroidOf("5-전부반영") < centroidOf("1-현재"))
    }
}
