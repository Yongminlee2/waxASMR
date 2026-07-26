package com.waxball.asmr.audio

import com.waxball.asmr.core.EventKind
import com.waxball.asmr.core.SoundProfile
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 귀로 확인하기 위한 산출물을 만든다. `app/build/audition/`에 WAV가 떨어진다.
 *
 * 합성 파라미터가 수치 기준을 통과해도 실제로 왁뿌볼처럼 들리는지는 사람이 판단해야 한다.
 * 이 테스트는 그 판단 재료를 매 빌드마다 자동으로 만들어 둔다.
 */
class SynthAuditionTest {

    private val outDir = File("build/audition")

    @Test
    fun writeMaterialSamples() {
        val materials = listOf(
            "1-굳은왁스" to SoundProfile.hardWax(),
            "2-무른왁스" to SoundProfile.softWax(),
            "3-반짝이" to SoundProfile.glitter(),
            "4-알갱이" to SoundProfile.crunchBeads(),
            "5-설탕유리" to SoundProfile.sugarGlass(),
        )
        for ((name, profile) in materials) {
            val buf = Audition.playSession(profile, seconds = 6f, seed = 7)
            Audition.writeWav(File(outDir, "$name.wav"), buf)
            assertTrue("$name 이 무음", Spectrum.rms(buf) > 1e-4f)
        }
    }

    @Test
    fun writeGestureSamples() {
        val p = SoundProfile.hardWax()

        Audition.writeWav(File(outDir, "동작-살살탭.wav"), burst(p) { s ->
            repeat(6) { s.on(EventKind.CRACK, it, 1, 0.15f, 0f, 0.02f) }
        })
        Audition.writeWav(File(outDir, "동작-꾹누르기.wav"), burst(p) { s ->
            repeat(6) { s.on(EventKind.CRACK, it, 2, 1f, 0f, 0.02f) }
        })
        Audition.writeWav(File(outDir, "동작-큰조각분리.wav"), burst(p) { s ->
            s.on(EventKind.DETACH, 0, 4, 1f, 0f, 0.18f)
        })
        Audition.writeWav(File(outDir, "동작-코어누르기.wav"), burst(p) { s ->
            repeat(4) { s.on(EventKind.CORE, -1, 0, 0.8f, 0f, 0f) }
        })
    }

    /** 이벤트를 넣고 3초를 렌더한다. */
    private fun burst(profile: SoundProfile, feed: (Synth) -> Unit): FloatArray {
        val s = Synth(Audition.SAMPLE_RATE).apply { setProfile(profile) }
        feed(s)
        val buf = s.renderOffline(3f)
        assertTrue("무음", buf.maxOf { kotlin.math.abs(it) } > 1e-4f)
        return buf
    }
}
