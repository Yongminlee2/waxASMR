package com.waxball.asmr.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QualityProbeTest {

    private fun probeFed(times: Int, ms: Float, target: Int = 240) =
        QualityProbe(target).apply { repeat(times) { feed(ms) } }

    @Test
    fun defaultsToMediumBeforeEnoughSamples() {
        assertEquals(1, QualityProbe().tier)
        assertEquals(1, probeFed(10, 40f).tier)
    }

    @Test
    fun fastDeviceGetsHighTier() {
        assertEquals(2, probeFed(240, 9f).tier)
    }

    @Test
    fun mediumDeviceGetsMediumTier() {
        assertEquals(1, probeFed(240, 18f).tier)
    }

    @Test
    fun slowDeviceGetsLowTier() {
        assertEquals(0, probeFed(240, 30f).tier)
    }

    @Test
    fun tierBoundariesAreWhereExpected() {
        assertEquals(2, probeFed(240, 15f).tier)
        assertEquals(1, probeFed(240, 15.5f).tier)
        assertEquals(1, probeFed(240, 22f).tier)
        assertEquals(0, probeFed(240, 22.5f).tier)
    }

    @Test
    fun slowStartupFramesDoNotDragDownAFastDevice() {
        // 첫 프레임 몇 개는 셰이더 컴파일로 항상 느리다. 중앙값이라 흔들리지 않아야 한다.
        val p = QualityProbe(240)
        repeat(8) { p.feed(180f) }
        repeat(232) { p.feed(9f) }
        assertEquals(2, p.tier)
    }

    @Test
    fun outliersAreDiscarded() {
        val p = QualityProbe(240)
        p.feed(9000f)
        p.feed(-5f)
        p.feed(0f)
        repeat(240) { p.feed(10f) }
        assertEquals(2, p.tier)
    }

    @Test
    fun stopsCollectingWhenFull() {
        val p = probeFed(240, 10f)
        assertTrue(p.ready)
        repeat(500) { p.feed(300f) }
        assertEquals("가득 찬 뒤 값에 흔들림", 2, p.tier)
    }

    @Test
    fun resetStartsOver() {
        val p = probeFed(240, 30f)
        assertTrue(p.ready)
        p.reset()
        assertFalse(p.ready)
        assertEquals(1, p.tier)
    }

    @Test
    fun medianOfEmptyProbeIsZero() {
        assertEquals(0f, QualityProbe().medianOfCollected(), 1e-6f)
    }

    @Test
    fun medianHandlesEvenCounts() {
        val p = QualityProbe(4)
        p.feed(10f); p.feed(20f); p.feed(30f); p.feed(40f)
        assertEquals(25f, p.medianOfCollected(), 1e-4f)
    }
}
