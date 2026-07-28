package com.waxball.asmr.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class PlaneSplitterTest {

    private fun cells(count: Int = 120, seed: Int = 7) =
        PlaneSplitter.split(count, Random(seed))

    /** 점이 폴리곤 안에 있는지. 광선 교차 횟수로 판정한다. */
    private fun contains(polygon: FloatArray, x: Float, y: Float): Boolean {
        var inside = false
        val n = polygon.size / 2
        var j = n - 1
        for (i in 0 until n) {
            val xi = polygon[i * 2]; val yi = polygon[i * 2 + 1]
            val xj = polygon[j * 2]; val yj = polygon[j * 2 + 1]
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside
            j = i
        }
        return inside
    }

    @Test
    fun everyCellHasAPolygon() {
        for (c in cells()) {
            assertTrue("조각 ${c.id}의 정점이 ${c.polygon.size / 2}개뿐", c.polygon.size >= 6)
        }
    }

    @Test
    fun theCellsCoverTheWholeScreen() {
        // 빈틈이 있으면 깨도 안 없어지는 자리가 생긴다.
        val total = cells().sumOf { it.areaFrac.toDouble() }
        assertEquals("면적 합이 1이 아님", 1.0, total, 0.01)
    }

    @Test
    fun theCellsDoNotOverlap() {
        // 겹치면 같은 자리가 두 번 깨진다.
        val all = cells()
        val rng = Random(11)
        repeat(1000) {
            val x = rng.nextFloat()
            val y = rng.nextFloat()
            val hits = all.count { contains(it.polygon, x, y) }
            assertTrue("($x, $y) 가 ${hits}개 조각에 들어감", hits == 1)
        }
    }

    @Test
    fun noCellLeavesTheScreen() {
        for (c in cells()) {
            for (i in c.polygon.indices) {
                assertTrue("정점이 화면 밖: ${c.polygon[i]}", c.polygon[i] in -0.001f..1.001f)
            }
        }
    }

    @Test
    fun neighboursAreMutual() {
        val all = cells()
        for (c in all) {
            for (n in c.neighbours) {
                assertTrue(
                    "${c.id}는 $n 을 이웃이라 하는데 반대는 아님",
                    all[n].neighbours.contains(c.id),
                )
            }
        }
    }

    @Test
    fun everyCellHasAtLeastOneNeighbour() {
        // 이웃이 없으면 금이 번지지 않아 그 조각만 외톨이로 남는다.
        for (c in cells()) {
            assertTrue("조각 ${c.id}에 이웃이 없음", c.neighbours.isNotEmpty())
        }
    }

    @Test
    fun sizesAreUneven() {
        // 조각이 다 똑같으면 깨는 맛이 없다. ShardSplitter가 같은 이유로 그렇게 돼 있다.
        val areas = cells().map { it.areaFrac }.sorted()
        val median = areas[areas.size / 2]
        assertTrue(
            "가장 큰 조각이 중앙값의 %.1f배뿐".format(areas.last() / median),
            areas.last() >= median * 4f,
        )
    }

    @Test
    fun theCentreSitsInsideItsOwnCell() {
        // 중심이 밖에 있으면 손가락 판정이 엉뚱한 조각을 고른다.
        for (c in cells()) {
            assertTrue("조각 ${c.id}의 중심이 제 폴리곤 밖", contains(c.polygon, c.centerX, c.centerY))
        }
    }

    @Test
    fun idsAreContiguous() {
        val all = cells()
        for (i in all.indices) assertEquals(i, all[i].id)
    }

    @Test
    fun theSameSeedGivesTheSameSplit() {
        val a = PlaneSplitter.split(80, Random(3))
        val b = PlaneSplitter.split(80, Random(3))
        assertEquals(a.size, b.size)
        for (i in a.indices) {
            assertTrue("같은 시드인데 다른 결과", abs(a[i].areaFrac - b[i].areaFrac) < 1e-6f)
        }
    }

    @Test
    fun aTinyCountStillWorks() {
        val few = PlaneSplitter.split(3, Random(1))
        assertTrue("조각이 ${few.size}개", few.size >= 2)
        assertEquals(1.0, few.sumOf { it.areaFrac.toDouble() }, 0.01)
    }
}
