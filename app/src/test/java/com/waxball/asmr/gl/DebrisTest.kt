package com.waxball.asmr.gl

import com.waxball.asmr.core.Quat
import com.waxball.asmr.core.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class DebrisTest {

    private val floorY = -2.2f

    /** 조각 8개가 반지름 1 구 위에 흩어져 있다고 가정한다. */
    private fun centers(n: Int) = FloatArray(n * 3) { i ->
        val shard = i / 3
        when (i % 3) {
            0 -> if (shard % 2 == 0) 0.9f else -0.9f
            1 -> 0.3f
            else -> 0.2f
        }
    }

    private fun debris(n: Int = 8) = Debris(n, Random(1))

    @Test
    fun spawnActivatesOnlyThatShard() {
        val d = debris()
        d.spawn(3, Vec3(1f, 0f, 0f), 0.02f, Quat.IDENTITY)
        assertTrue(d.isActive(3))
        assertFalse(d.isActive(2))
        assertEquals(1, d.count)
    }

    @Test
    fun spawningTwiceDoesNotDoubleCount() {
        val d = debris()
        d.spawn(3, Vec3(1f, 0f, 0f), 0.02f, Quat.IDENTITY)
        d.spawn(3, Vec3(1f, 0f, 0f), 0.02f, Quat.IDENTITY)
        assertEquals(1, d.count)
    }

    @Test
    fun fallsDownwardOverTime() {
        val d = debris(); val c = centers(8)
        d.spawn(0, Vec3(1f, 0f, 0f), 0.02f, Quat.IDENTITY)
        val m = FloatArray(12)
        d.writeMatrix(0, c, m, 0)
        val startY = m[7]
        repeat(20) { d.update(0.016f, floorY, c) { _, _, _ -> } }
        d.writeMatrix(0, c, m, 0)
        assertTrue("아래로 떨어지지 않음", m[7] < startY)
    }

    @Test
    fun landsAndFiresCallbackExactlyOnce() {
        val d = debris(); val c = centers(8)
        d.spawn(0, Vec3(1f, 0f, 0f), 0.02f, Quat.IDENTITY)
        var landings = 0
        repeat(600) { d.update(0.016f, floorY, c) { _, _, _ -> landings++ } }
        assertEquals(1, landings)
    }

    @Test
    fun comesToRestOnTheFloor() {
        val d = debris(); val c = centers(8)
        d.spawn(0, Vec3(1f, 0f, 0f), 0.05f, Quat.IDENTITY)
        repeat(600) { d.update(0.016f, floorY, c) { _, _, _ -> } }
        val m = FloatArray(12)
        d.writeMatrix(0, c, m, 0)
        assertTrue("바닥을 뚫고 내려감: y=${m[7]}", m[7] >= floorY - 0.01f)
        assertTrue("바닥 위에 떠 있음: y=${m[7]}", m[7] < floorY + 0.7f)

        // 자리잡은 뒤에는 더 움직이지 않는다.
        val settled = m[7]
        repeat(120) { d.update(0.016f, floorY, c) { _, _, _ -> } }
        d.writeMatrix(0, c, m, 0)
        assertEquals(settled, m[7], 1e-5f)
    }

    @Test
    fun landingPanFollowsWhereTheShardWas() {
        val d = debris(); val c = centers(8)
        var leftPan = 0f; var rightPan = 0f
        d.spawn(0, Vec3(1f, 0f, 0f), 0.02f, Quat.IDENTITY)   // center.x = +0.9
        d.spawn(1, Vec3(-1f, 0f, 0f), 0.02f, Quat.IDENTITY)  // center.x = -0.9
        repeat(600) {
            d.update(0.016f, floorY, c) { id, pan, _ ->
                if (id == 0) rightPan = pan else if (id == 1) leftPan = pan
            }
        }
        assertTrue("좌우 위치가 소리에 반영되지 않음 (L=$leftPan, R=$rightPan)", rightPan > leftPan)
    }

    @Test
    fun matrixIsIdentityLikeAtSpawn() {
        val d = debris(); val c = centers(8)
        d.spawn(0, Vec3(1f, 0f, 0f), 0.02f, Quat.IDENTITY)
        val m = FloatArray(12)
        d.writeMatrix(0, c, m, 0)
        // 갓 떨어진 조각은 원래 자리 그대로여야 한다. 튀어 보이면 안 된다.
        assertEquals(1f, m[0], 1e-4f); assertEquals(1f, m[5], 1e-4f); assertEquals(1f, m[10], 1e-4f)
        assertEquals(0f, m[3], 1e-4f); assertEquals(0f, m[7], 1e-4f); assertEquals(0f, m[11], 1e-4f)
    }

    @Test
    fun spawningWithRotatedBallKeepsShardInPlace() {
        // 볼을 굴린 상태에서 조각이 떨어져도 처음 위치는 굴린 그 자리여야 한다.
        val d = debris(); val c = centers(8)
        val rot = Quat.axisAngle(Vec3(0f, 1f, 0f), 1.2f)
        d.spawn(0, Vec3(1f, 0f, 0f), 0.02f, rot)
        val m = FloatArray(12)
        d.writeMatrix(0, c, m, 0)
        val cVec = Vec3(c[0], c[1], c[2])
        val expected = rot.rotate(cVec)
        val actual = Vec3(
            m[0] * cVec.x + m[1] * cVec.y + m[2] * cVec.z + m[3],
            m[4] * cVec.x + m[5] * cVec.y + m[6] * cVec.z + m[7],
            m[8] * cVec.x + m[9] * cVec.y + m[10] * cVec.z + m[11],
        )
        assertEquals(expected.x, actual.x, 1e-4f)
        assertEquals(expected.y, actual.y, 1e-4f)
        assertEquals(expected.z, actual.z, 1e-4f)
    }

    @Test
    fun rotationMatrixStaysOrthonormalWhileTumbling() {
        val d = debris(); val c = centers(8)
        d.spawn(0, Vec3(0f, 1f, 0f), 0.02f, Quat.IDENTITY)
        repeat(300) { d.update(0.016f, floorY, c) { _, _, _ -> } }
        val m = FloatArray(12)
        d.writeMatrix(0, c, m, 0)
        val row0 = Vec3(m[0], m[1], m[2])
        val row1 = Vec3(m[4], m[5], m[6])
        assertEquals(1f, row0.length(), 1e-3f)
        assertEquals(1f, row1.length(), 1e-3f)
        assertTrue("회전 행렬이 직교성을 잃음", abs(row0 dot row1) < 1e-3f)
    }

    @Test
    fun bigShardHangsBeforeItDrops() {
        // 바로 떨어뜨리면 "툭" 하고 사라질 뿐이다. 버티다 놓이는 순간이 있어야 한다.
        val d = debris(); val c = centers(8)
        d.spawn(0, Vec3(1f, 0f, 0f), 0.05f, Quat.IDENTITY)
        val m = FloatArray(12)
        d.writeMatrix(0, c, m, 0)
        val startY = m[7]

        d.update(0.016f, floorY, c) { _, _, _ -> }
        d.writeMatrix(0, c, m, 0)
        assertEquals("매달려 있어야 할 프레임에 이미 떨어짐", startY, m[7], 1e-6f)

        repeat(40) { d.update(0.016f, floorY, c) { _, _, _ -> } }
        d.writeMatrix(0, c, m, 0)
        assertTrue("매달린 뒤에도 안 떨어짐", m[7] < startY)
    }

    @Test
    fun crushingShrinksDebrisThenRemovesIt() {
        val d = debris(); val c = centers(8)
        d.spawn(0, Vec3(1f, 0f, 0f), 0.02f, Quat.IDENTITY)
        repeat(600) { d.update(0.016f, floorY, c) { _, _, _ -> } }
        assertTrue("바닥에 자리잡지 않아 뭉갤 수 없음", d.hasCrushableDebris())

        val worldX = 0.9f
        var crunches = 0
        var previousShrink = d.shrinkOf(0)
        repeat(2) {
            d.crushNear(worldX, 0.5f, 3, c) { _, _, _ -> crunches++ }
            assertTrue("뭉갰는데 안 작아짐", d.shrinkOf(0) > previousShrink)
            previousShrink = d.shrinkOf(0)
        }

        d.crushNear(worldX, 0.5f, 3, c) { _, _, _ -> crunches++ }
        assertEquals("세 번 뭉개면 가루가 되어 사라져야 함", 0, d.count)
        assertEquals(3, crunches)
        assertFalse(d.hasCrushableDebris())
    }

    @Test
    fun crushingOnlyAffectsDebrisUnderTheFinger() {
        val d = debris(); val c = centers(8)
        d.spawn(0, Vec3(1f, 0f, 0f), 0.02f, Quat.IDENTITY)   // center.x = +0.9
        d.spawn(1, Vec3(-1f, 0f, 0f), 0.02f, Quat.IDENTITY)  // center.x = -0.9
        repeat(600) { d.update(0.016f, floorY, c) { _, _, _ -> } }

        d.crushNear(0.9f, 0.4f, 3, c) { _, _, _ -> }
        assertTrue("손가락 아래 것이 안 뭉개짐", d.shrinkOf(0) > 0f)
        assertEquals("멀리 있는 것까지 뭉개짐", 0f, d.shrinkOf(1), 1e-6f)
    }

    @Test
    fun airborneDebrisCannotBeCrushed() {
        val d = debris(); val c = centers(8)
        d.spawn(0, Vec3(1f, 0f, 0f), 0.02f, Quat.IDENTITY)
        d.update(0.016f, floorY, c) { _, _, _ -> }
        assertFalse("아직 떨어지는 중인데 뭉갤 수 있다고 나옴", d.hasCrushableDebris())
        d.crushNear(0.9f, 1f, 3, c) { _, _, _ -> throw AssertionError("공중에 있는 걸 뭉갬") }
    }

    @Test
    fun clearRemovesEverything() {
        val d = debris(); val c = centers(8)
        repeat(8) { d.spawn(it, Vec3(1f, 0f, 0f), 0.02f, Quat.IDENTITY) }
        assertEquals(8, d.count)
        d.clear()
        assertEquals(0, d.count)
        repeat(8) { assertFalse(d.isActive(it)) }
        d.update(0.016f, floorY, c) { _, _, _ -> throw AssertionError("치운 뒤에 착지가 발생함") }
    }

    @Test
    fun outOfRangeSpawnIsIgnored() {
        val d = debris()
        d.spawn(-1, Vec3(1f, 0f, 0f), 0.02f, Quat.IDENTITY)
        d.spawn(99, Vec3(1f, 0f, 0f), 0.02f, Quat.IDENTITY)
        assertEquals(0, d.count)
    }

    @Test
    fun biggerShardsRestHigherThanSmallOnes() {
        val d = debris(); val c = centers(8)
        d.spawn(0, Vec3(1f, 0f, 0f), 0.25f, Quat.IDENTITY)
        d.spawn(2, Vec3(1f, 0f, 0f), 0.001f, Quat.IDENTITY)
        repeat(600) { d.update(0.016f, floorY, c) { _, _, _ -> } }
        val big = FloatArray(12); val small = FloatArray(12)
        d.writeMatrix(0, c, big, 0)
        d.writeMatrix(2, c, small, 0)
        assertTrue("큰 조각이 작은 조각보다 낮게 깔림", big[7] > small[7])
    }
}
