package com.waxball.asmr.gl

import com.waxball.asmr.core.Quat
import com.waxball.asmr.core.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class DebrisPileTest {

    private val floorY = -2f

    /** 조각 [n]개를 떨어뜨려 바닥에 앉힌다. */
    private fun settled(n: Int): Pair<Debris, FloatArray> {
        val debris = Debris(n, Random(3))
        val centers = FloatArray(n * 3)
        for (i in 0 until n) {
            centers[i * 3] = (i % 5) * 0.2f - 0.4f
            centers[i * 3 + 1] = 0.5f
            centers[i * 3 + 2] = 0f
            debris.spawn(i, Vec3(0f, 1f, 0f), 0.01f, Quat.IDENTITY)
        }
        repeat(600) { debris.update(1f / 60f, floorY, centers) { _, _, _ -> } }
        return debris to centers
    }

    @Test
    fun piecesPileUpOnTheFloor() {
        val (debris, _) = settled(10)
        assertEquals("바닥에 안 쌓임", 10, debris.count)
    }

    @Test
    fun trimmingRemovesTheOldestFirst() {
        val (debris, _) = settled(10)
        val removed = debris.trimTo(6)
        assertEquals("치운 개수가 안 맞음", 4, removed)
        assertEquals(6, debris.count)
        assertFalse("가장 먼저 쌓인 것이 안 치워짐", debris.isActive(0))
        assertTrue("가장 나중에 쌓인 것이 치워짐", debris.isActive(9))
    }

    @Test
    fun trimmingBelowTheCapDoesNothing() {
        val (debris, _) = settled(4)
        assertEquals(0, debris.trimTo(60))
        assertEquals(4, debris.count)
    }

    @Test
    fun slidingMovesRestingPiecesSideways() {
        val (debris, centers) = settled(6)
        val out = FloatArray(12)
        debris.writeMatrix(0, centers, out, 0)
        val before = out[3]

        debris.slideResting(0.4f)
        debris.writeMatrix(0, centers, out, 0)
        assertEquals("기울였는데 부스러기가 안 미끄러짐", before + 0.4f, out[3], 1e-4f)
    }

    @Test
    fun slidingDoesNotMovePiecesStillInTheAir() {
        val n = 3
        val debris = Debris(n, Random(3))
        val centers = FloatArray(n * 3) { if (it % 3 == 1) 0.5f else 0f }
        for (i in 0 until n) debris.spawn(i, Vec3(0f, 1f, 0f), 0.01f, Quat.IDENTITY)
        // 아직 떨어지는 중
        repeat(3) { debris.update(1f / 60f, floorY, centers) { _, _, _ -> } }

        val out = FloatArray(12)
        debris.writeMatrix(0, centers, out, 0)
        val before = out[3]
        debris.slideResting(0.4f)
        debris.writeMatrix(0, centers, out, 0)
        assertEquals("공중에 있는 조각까지 밀림", before, out[3], 1e-4f)
    }

    @Test
    fun clearResetsTheTrimOrder() {
        val (debris, _) = settled(5)
        debris.clear()
        assertEquals(0, debris.count)
        assertEquals("비운 뒤인데 치울 게 있다고 나옴", 0, debris.trimTo(1))
    }
}
