package com.waxball.asmr.audio

import com.waxball.asmr.core.EventKind
import com.waxball.asmr.core.SoundProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SynthTest {

    private val sr = 48000

    private fun synth(p: SoundProfile = SoundProfile.hardWax(), capacity: Int = 192) =
        Synth(sr, capacity).apply { setProfile(p) }

    @Test
    fun silenceWhenNoEvents() {
        val buf = synth().renderOffline(0.5f)
        assertEquals(0f, Spectrum.rms(buf), 1e-7f)
    }

    @Test
    fun neverClips() {
        val s = synth(SoundProfile.sugarGlass())
        repeat(60) { s.on(EventKind.CRACK, it, 2, 1f, 0f, 0.05f) }
        val buf = s.renderOffline(1f)
        val peak = buf.maxOf { abs(it) }
        assertTrue("클리핑 발생: peak=$peak", peak < 1.0f)
    }

    @Test
    fun louderPressProducesMoreEnergy() {
        fun energyOf(e: Float): Float {
            val s = synth()
            s.on(EventKind.CRACK, 0, 1, e, 0f, 0.02f)
            return Spectrum.rms(s.renderOffline(0.5f))
        }
        val soft = energyOf(0.2f)
        val hard = energyOf(1.0f)
        assertTrue("세게 눌러도 안 커짐 (약함=$soft, 셈=$hard)", hard > soft * 2f)
    }

    @Test
    fun panPlacesEnergyOnCorrectSide() {
        val s = synth()
        s.on(EventKind.CRACK, 0, 1, 1f, -1f, 0.02f)
        val b = s.renderOffline(0.4f)
        var l = 0.0; var r = 0.0
        var i = 0
        while (i < b.size) { l += abs(b[i]); r += abs(b[i + 1]); i += 2 }
        assertTrue("왼쪽으로 팬했는데 오른쪽이 더 큼 (L=$l, R=$r)", l > r * 3)
    }

    @Test
    fun centerPanIsBalanced() {
        val s = synth()
        repeat(10) { s.on(EventKind.CRACK, it, 1, 1f, 0f, 0.02f) }
        val b = s.renderOffline(0.5f)
        var l = 0.0; var r = 0.0
        var i = 0
        while (i < b.size) { l += abs(b[i]); r += abs(b[i + 1]); i += 2 }
        assertEquals(1.0, l / r, 0.15)
    }

    @Test
    fun materialsDifferInBrightness() {
        fun centroidOf(p: SoundProfile): Float {
            val s = synth(p)
            repeat(20) { s.on(EventKind.CRACK, it, 1, 1f, 0f, 0.02f) }
            return Spectrum.centroid(s.renderOffline(1f), sr)
        }
        val soft = centroidOf(SoundProfile.softWax())
        val hard = centroidOf(SoundProfile.hardWax())
        val glass = centroidOf(SoundProfile.sugarGlass())
        assertTrue("무른 왁스가 굳은 왁스보다 어두워야 함 ($soft vs $hard)", soft < hard)
        assertTrue("설탕유리가 가장 밝아야 함 ($glass vs $hard)", glass > hard)
        assertTrue("설탕유리와 무른 왁스 차이가 너무 작음", glass > soft * 1.3f)
    }

    @Test
    fun biggerShardDetachesLower() {
        fun centroidOfArea(a: Float): Float {
            val s = synth()
            s.on(EventKind.DETACH, 0, 4, 1f, 0f, a)
            return Spectrum.centroid(s.renderOffline(1f), sr)
        }
        val big = centroidOfArea(0.15f)
        val small = centroidOfArea(0.005f)
        assertTrue("큰 조각이 더 낮게 나야 함 (큼=$big, 작음=$small)", big < small)
    }

    @Test
    fun harderPressPacksCracksTighter() {
        val s = synth()
        s.on(EventKind.CRACK, 0, 2, 0.25f, 0f, 0.02f)
        val softCount = s.lastGrainCount
        val softGap = s.lastMeanGapMs

        s.on(EventKind.CRACK, 0, 2, 1f, 0f, 0.02f)
        val hardCount = s.lastGrainCount
        val hardGap = s.lastMeanGapMs

        assertTrue("세게 눌러도 파열 수가 안 늘어남 ($softCount → $hardCount)", hardCount > softCount * 3)
        assertTrue("세게 눌러도 간격이 안 촘촘해짐 ($softGap → $hardGap)", hardGap < softGap * 0.5f)
        assertTrue("초당 파열이 500회 미만이면 빠자자작으로 안 들림", hardCount / (hardGap * 0.001f * hardCount) > 500f)
    }

    @Test
    fun voiceStealingKeepsOutputBounded() {
        val s = synth(capacity = 64)
        repeat(500) { s.on(EventKind.CRACK, it, 2, 1f, 0f, 0.02f) }
        val b = s.renderOffline(0.2f)
        assertTrue(b.all { abs(it) < 1.0f })
    }

    @Test
    fun noDcOffset() {
        val s = synth()
        repeat(30) { s.on(EventKind.CRACK, it, 1, 1f, 0f, 0.02f) }
        val b = s.renderOffline(1f)
        var sum = 0.0
        for (v in b) sum += v
        assertEquals(0.0, sum / b.size, 1e-3)
    }

    @Test
    fun coreSqueezeIsMuchDullerThanCracking() {
        val core = synth().let { it.on(EventKind.CORE, -1, 0, 1f, 0f, 0f); Spectrum.centroid(it.renderOffline(1f), sr) }
        val crack = synth().let { it.on(EventKind.CRACK, 0, 2, 1f, 0f, 0.02f); Spectrum.centroid(it.renderOffline(1f), sr) }
        assertTrue("코어를 누를 때도 껍질 깨는 소리만큼 밝음 (코어=$core, 크랙=$crack)", core < crack * 0.6f)
        assertTrue("코어 마찰음이 나지 않음", core > 100f)
    }

    @Test
    fun rubIsQuieterThanCrack() {
        fun energyOf(kind: Int): Float {
            val s = synth()
            s.on(kind, 0, 1, 1f, 0f, 0.02f)
            return Spectrum.rms(s.renderOffline(0.4f))
        }
        assertTrue(energyOf(EventKind.RUB) < energyOf(EventKind.CRACK))
    }

    @Test
    fun landIsQuieterThanDetach() {
        fun energyOf(kind: Int): Float {
            val s = synth()
            s.on(kind, 0, 4, 1f, 0f, 0.02f)
            return Spectrum.rms(s.renderOffline(0.4f))
        }
        assertTrue(energyOf(EventKind.LAND) < energyOf(EventKind.DETACH))
    }

    @Test
    fun masterGainScalesOutput() {
        fun energyAt(g: Float): Float {
            val s = synth()
            s.masterGain = g
            repeat(5) { s.on(EventKind.CRACK, it, 1, 1f, 0f, 0.02f) }
            return Spectrum.rms(s.renderOffline(0.4f))
        }
        assertTrue(energyAt(0.2f) < energyAt(0.9f))
    }

    @Test
    fun repeatedEventsNeverSoundIdentical() {
        // 같은 이벤트라도 매번 다른 파형이어야 "아까 그 소리"로 들리지 않는다.
        val s = synth()
        s.on(EventKind.CRACK, 0, 1, 1f, 0f, 0.02f)
        val a = s.renderOffline(0.3f)
        s.on(EventKind.CRACK, 0, 1, 1f, 0f, 0.02f)
        val b = s.renderOffline(0.3f)
        var identical = 0
        for (i in a.indices) if (a[i] == b[i]) identical++
        assertTrue("두 번의 파열이 완전히 동일함", identical < a.size / 2)
    }

    @Test
    fun renderDoesNotLeakBetweenBuffers() {
        val s = synth()
        s.on(EventKind.CRACK, 0, 1, 1f, 0f, 0.02f)
        val buf = FloatArray(512)
        s.render(buf, 256)
        // 그레인이 다 끝난 뒤에는 완전한 무음이어야 한다.
        repeat(400) { s.render(buf, 256) }
        assertEquals(0f, buf.maxOf { abs(it) }, 1e-6f)
    }
}
