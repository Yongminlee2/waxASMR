package com.waxball.asmr.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PlaneShardsTest {

    private val cells = PlaneSplitter.split(120, Random(7))

    private fun model(): BreakModel {
        val set = PlaneShards.toShardSet(cells)
        return BreakModel(set, SmashProfile.of(), EventQueue(4096))
    }

    /** 한 자리를 충분히 오래 문지른다. */
    private fun rub(model: BreakModel, x: Float, y: Float, seconds: Float = 1f) {
        val dt = 1f / 60f
        repeat((seconds / dt).toInt()) {
            PlaneShards.pressAt(model, cells, x, y, radius = 0.12f, force = 3f, dt = dt)
        }
    }

    private fun detachedNear(model: BreakModel, x: Float, y: Float, radius: Float): Int {
        var n = 0
        for (c in cells) {
            val dx = c.centerX - x
            val dy = c.centerY - y
            if (dx * dx + dy * dy > radius * radius) continue
            if (model.state[c.id] >= ShardState.DETACHED) n++
        }
        return n
    }

    @Test
    fun theShardSetKeepsEveryCell() {
        val set = PlaneShards.toShardSet(cells)
        assertEquals(cells.size, set.size)
        for (i in cells.indices) assertEquals(i, set.shards[i].id)
    }

    @Test
    fun theShardSetKeepsTheAreas() {
        val set = PlaneShards.toShardSet(cells)
        val total = set.shards.sumOf { it.areaFrac.toDouble() }
        assertEquals("면적 합이 1이 아님", 1.0, total, 0.01)
    }

    @Test
    fun theShardSetKeepsTheNeighbours() {
        val set = PlaneShards.toShardSet(cells)
        for (c in cells) {
            assertTrue(
                "조각 ${c.id}의 이웃이 안 옮겨짐",
                set.adjacency[c.id].toSortedSet() == c.neighbours.toSortedSet(),
            )
        }
    }

    @Test
    fun theBaseMeshCoversEveryShard() {
        // BallGeometry와 같은 규약이다. 조각마다 자기 삼각형을 갖고 있어야 그릴 수 있다.
        val set = PlaneShards.toShardSet(cells)
        var triangles = 0
        for (s in set.shards) {
            assertTrue("조각 ${s.id}에 삼각형이 없음", s.triangles.isNotEmpty())
            triangles += s.triangles.size
        }
        assertEquals("메시 삼각형과 조각 삼각형 수가 안 맞음", set.baseMesh.triangleCount, triangles)
    }

    @Test
    fun rubbingOneSpotBreaksThatSpot() {
        val m = model()
        rub(m, 0.3f, 0.3f)
        assertTrue("문질렀는데 아무것도 안 떨어짐", detachedNear(m, 0.3f, 0.3f, 0.15f) > 0)
    }

    @Test
    fun theFarSideStaysWhole() {
        // 만진 자리만 깨져야 한다. 화면 전체가 한꺼번에 깨지면 깨는 맛이 없다.
        val m = model()
        rub(m, 0.2f, 0.2f, seconds = 1.5f)
        assertEquals("반대쪽까지 떨어짐", 0, detachedNear(m, 0.85f, 0.85f, 0.12f))
    }

    @Test
    fun rubbingAcrossBreaksTheWholeStripe() {
        val m = model()
        val dt = 1f / 60f
        for (step in 0..40) {
            val x = step / 40f
            repeat(3) { PlaneShards.pressAt(m, cells, x, 0.5f, 0.1f, 3f, dt) }
        }
        assertTrue("가로로 훑었는데 왼쪽이 안 깨짐", detachedNear(m, 0.1f, 0.5f, 0.12f) > 0)
        assertTrue("가로로 훑었는데 오른쪽이 안 깨짐", detachedNear(m, 0.9f, 0.5f, 0.12f) > 0)
    }

    @Test
    fun pressingOutsideTheScreenDoesNothing() {
        val m = model()
        val touched = PlaneShards.pressAt(m, cells, -0.5f, -0.5f, 0.05f, 3f, 1f / 60f)
        assertEquals("화면 밖을 눌렀는데 조각이 눌림", 0, touched)
    }

    @Test
    fun theWholeScreenCanBeCleared() {
        // 다 못 깨면 끝이 안 난다.
        val m = model()
        val dt = 1f / 60f
        repeat(6) {
            for (c in cells) {
                repeat(4) { PlaneShards.pressAt(m, cells, c.centerX, c.centerY, 0.08f, 4f, dt) }
            }
        }
        assertTrue("다 깼는데 진행률이 %.2f".format(m.shellProgress), m.shellProgress >= 0.99f)
    }
}
