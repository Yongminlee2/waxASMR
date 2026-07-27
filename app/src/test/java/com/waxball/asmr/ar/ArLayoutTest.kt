package com.waxball.asmr.ar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class ArLayoutTest {

    private val handSpan = 300f
    private val out = FloatArray(2)

    private fun distanceFromCentre(index: Int, count: Int): Float {
        ArLayout.offsetPx(index, count, handSpan, out)
        return sqrt(out[0] * out[0] + out[1] * out[1])
    }

    @Test
    fun aSingleBallSitsAtTheCentre() {
        ArLayout.offsetPx(0, 1, handSpan, out)
        assertEquals(0f, out[0], 1e-6f)
        assertEquals(0f, out[1], 1e-6f)
    }

    @Test
    fun ballsGetSmallerAsCountRises() {
        val one = ArLayout.radiusPx(handSpan, 1)
        val two = ArLayout.radiusPx(handSpan, 2)
        val three = ArLayout.radiusPx(handSpan, 3)
        assertTrue("2개일 때 안 작아짐 ($one → $two)", two < one)
        assertTrue("3개일 때 안 작아짐 ($two → $three)", three < two)
    }

    @Test
    fun ballsAlwaysFitInsideTheHand() {
        // 중심에서 밀어낸 거리에 반지름을 더해도 손 너비를 크게 넘지 않아야 한다.
        for (count in 1..ArLayout.MAX_BALLS) {
            val radius = ArLayout.radiusPx(handSpan, count)
            for (i in 0 until count) {
                val reach = distanceFromCentre(i, count) + radius
                assertTrue("${count}개 중 ${i}번이 손 밖으로 나감 ($reach vs $handSpan)", reach <= handSpan)
            }
        }
    }

    @Test
    fun multipleBallsDoNotSitOnTopOfEachOther() {
        for (count in 2..ArLayout.MAX_BALLS) {
            val radius = ArLayout.radiusPx(handSpan, count)
            for (i in 0 until count) {
                for (j in i + 1 until count) {
                    ArLayout.offsetPx(i, count, handSpan, out)
                    val ax = out[0]; val ay = out[1]
                    ArLayout.offsetPx(j, count, handSpan, out)
                    val dx = ax - out[0]; val dy = ay - out[1]
                    val gap = sqrt(dx * dx + dy * dy)
                    assertTrue(
                        "${count}개일 때 ${i}번과 ${j}번이 겹침 (거리 $gap, 반지름 $radius)",
                        gap > radius,
                    )
                }
            }
        }
    }

    @Test
    fun layoutScalesWithHandSize() {
        // 손이 멀어져 작게 잡혀도 배치 비율이 유지돼야 한다.
        val near = FloatArray(2)
        ArLayout.offsetPx(1, 3, 400f, near)
        val far = FloatArray(2)
        ArLayout.offsetPx(1, 3, 200f, far)
        assertEquals("손 크기에 비례하지 않음", 2f, near[0] / far[0], 1e-3f)
    }

    @Test
    fun countCyclesBackToOne() {
        assertEquals(2, ArLayout.nextCount(1))
        assertEquals(3, ArLayout.nextCount(2))
        assertEquals(1, ArLayout.nextCount(ArLayout.MAX_BALLS))
    }

    @Test
    fun moreBallsUseFewerShards() {
        assertTrue(ArLayout.qualityFor(3) <= ArLayout.qualityFor(1))
    }

    @Test
    fun outOfRangeCountsDoNotCrash() {
        ArLayout.offsetPx(0, 0, handSpan, out)
        ArLayout.offsetPx(5, 99, handSpan, out)
        assertTrue(ArLayout.radiusPx(handSpan, 0) > 0f)
        assertTrue(ArLayout.radiusPx(handSpan, 99) > 0f)
    }
}
