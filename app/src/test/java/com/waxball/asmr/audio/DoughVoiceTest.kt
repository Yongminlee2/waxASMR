package com.waxball.asmr.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 합성한 반죽 치대는 소리를 검증한다.
 *
 * 반죽 소리는 파열음과 정반대여야 한다 — 낮고 둔하고 이어진다. 여기서 안 재면
 * "쉬익거리는 바람 소리"나 "자갈 소리"가 나와도 기기에 올리기 전에는 모른다.
 * 파편 시절에 똑같이 당했다.
 */
class DoughVoiceTest {

    private val sampleRate = 48000
    private val outDir = File("build/audition")

    /** [seconds] 초를 렌더한다. 스테레오 인터리브. */
    private fun render(activity: Float, seconds: Float): FloatArray {
        val voice = DoughVoice(sampleRate)
        voice.activity = activity
        val frames = (sampleRate * seconds).toInt()
        val out = FloatArray(frames * 2)
        val block = FloatArray(1024 * 2)
        var done = 0
        while (done < frames) {
            val n = minOf(1024, frames - done)
            java.util.Arrays.fill(block, 0f)
            voice.render(block, n, 1f)
            System.arraycopy(block, 0, out, done * 2, n * 2)
            done += n
        }
        return out
    }

    private fun peakOf(stereo: FloatArray): Float {
        var peak = 0f
        for (v in stereo) if (kotlin.math.abs(v) > peak) peak = kotlin.math.abs(v)
        return peak
    }

    @Test
    fun kneadingIsLowAndDull() {
        // 왁뿌볼 파열음은 중심이 1200~4300Hz다. 반죽은 그보다 훨씬 아래여야
        // 축축하게 들린다. 위로 올라가면 바람 새는 소리가 된다.
        val stereo = render(activity = 1f, seconds = 4f)
        val centroid = Spectrum.centroid(stereo, sampleRate)
        assertTrue("반죽 소리가 너무 밝다: ${centroid}Hz", centroid < 1200f)
        assertTrue("반죽 소리에 저역이 없다: ${centroid}Hz", centroid > 60f)
    }

    @Test
    fun itDoesNotClip() {
        val stereo = render(activity = 1f, seconds = 4f)
        assertTrue("반죽 소리가 클리핑: ${peakOf(stereo)}", peakOf(stereo) < 1f)
        assertTrue("반죽 소리가 들리지 않음: ${peakOf(stereo)}", peakOf(stereo) > 0.02f)
    }

    @Test
    fun stoppedHandsFallSilent() {
        // 가만히 있는데 계속 나면 유령이다.
        val stereo = render(activity = 0f, seconds = 3f)
        assertEquals("손을 멈췄는데 소리가 남", 0f, peakOf(stereo), 1e-3f)
    }

    @Test
    fun harderKneadingIsLouder() {
        val soft = render(activity = 0.25f, seconds = 4f)
        val hard = render(activity = 1f, seconds = 4f)
        assertTrue(
            "세게 주무르는데 더 크지 않다: ${peakOf(soft)} vs ${peakOf(hard)}",
            peakOf(hard) > peakOf(soft) * 1.3f,
        )
    }

    @Test
    fun writeAudition() {
        // 수치가 맞아도 진짜 반죽 소리로 들리는지는 사람이 판단해야 한다.
        outDir.mkdirs()
        Audition.writeWav(File(outDir, "검수-반죽치대기.wav"), render(1f, 6f))
    }
}
