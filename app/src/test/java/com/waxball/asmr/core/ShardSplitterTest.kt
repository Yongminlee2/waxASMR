package com.waxball.asmr.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ShardSplitterTest {

    private val base = Icosphere.build(4)

    @Test
    fun everyTriangleBelongsToExactlyOneShard() {
        val s = ShardSplitter.split(base, 120, Random(1))
        assertEquals(base.triangleCount, s.shards.sumOf { it.triangles.size })
        val seen = BooleanArray(base.triangleCount)
        s.shards.forEach { sh ->
            sh.triangles.forEach { t ->
                assertFalse("삼각형 $t 이 두 조각에 배정됨", seen[t])
                seen[t] = true
            }
        }
    }

    @Test
    fun noEmptyShards() {
        s@ for (seed in 1..20) {
            val s = ShardSplitter.split(base, 300, Random(seed.toLong()))
            s.shards.forEach { assertTrue("빈 조각 ${it.id} (seed=$seed)", it.triangles.isNotEmpty()) }
        }
    }

    @Test
    fun areaFractionsSumToOne() {
        val s = ShardSplitter.split(base, 200, Random(3))
        assertEquals(1.0f, s.shards.map { it.areaFrac }.sum(), 1e-3f)
    }

    @Test
    fun adjacencyIsSymmetricAndNonSelf() {
        val s = ShardSplitter.split(base, 150, Random(5))
        s.adjacency.forEachIndexed { i, ns ->
            ns.forEach { j ->
                assertNotEquals("자기 자신이 이웃", i, j)
                assertTrue("$j 는 $i 를 이웃으로 갖지 않음", s.adjacency[j].contains(i))
            }
        }
    }

    @Test
    fun everyShardHasAtLeastOneNeighbour() {
        val s = ShardSplitter.split(base, 100, Random(11))
        s.adjacency.forEachIndexed { i, ns ->
            assertTrue("고립된 조각 $i", ns.isNotEmpty())
        }
    }

    @Test
    fun sameSeedGivesSameResult() {
        val a = ShardSplitter.split(base, 80, Random(42))
        val b = ShardSplitter.split(base, 80, Random(42))
        assertArrayEquals(
            a.shards.map { it.triangles.size }.toIntArray(),
            b.shards.map { it.triangles.size }.toIntArray(),
        )
    }

    @Test
    fun differentSeedsGiveDifferentShapes() {
        val a = ShardSplitter.split(base, 80, Random(1))
        val b = ShardSplitter.split(base, 80, Random(2))
        val sameSizes = a.shards.map { it.triangles.size } == b.shards.map { it.triangles.size }
        assertFalse("시드가 달라도 분할이 동일함", sameSizes)
    }

    @Test
    fun shardCentersAreUnitVectors() {
        val s = ShardSplitter.split(base, 90, Random(9))
        s.shards.forEach { assertEquals(1f, it.center.length(), 1e-4f) }
    }

    @Test
    fun shardSizesAreIrregularNotUniform() {
        // 유기적으로 보이려면 조각 크기가 제각각이어야 한다.
        val s = ShardSplitter.split(base, 120, Random(4))
        val sizes = s.shards.map { it.triangles.size }
        assertTrue("조각 크기가 지나치게 균일함", sizes.max() > sizes.min() * 2)
    }

    @Test
    fun someShardsAreMuchBiggerThanTheRest() {
        // 크기가 다 비슷하면 큰 판이 통째로 벗겨지는 순간이 없어서 금방 지루해진다.
        for (seed in 1..10) {
            val s = ShardSplitter.split(base, 150, Random(seed.toLong()))
            val areas = s.shards.map { it.areaFrac }.sorted()
            val median = areas[areas.size / 2]
            val biggest = areas.last()
            assertTrue(
                "가장 큰 조각이 중간 크기의 6배도 안 됨 (seed=$seed, 최대=$biggest, 중앙=$median)",
                biggest > median * 6f,
            )
        }
    }

    @Test
    fun bothLargePlatesAndFineChipsExist() {
        val s = ShardSplitter.split(base, 150, Random(3))
        val areas = s.shards.map { it.areaFrac }
        val plates = areas.count { it > 0.015f }
        val chips = areas.count { it < 0.004f }
        assertTrue("넓은 판이 없음 (${plates}개)", plates >= 3)
        assertTrue("자잘한 부스러기가 없음 (${chips}개)", chips >= 20)
    }

    @Test
    fun coarseBaseMeshStillSplitsCleanly() {
        val coarse = Icosphere.build(2)
        val s = ShardSplitter.split(coarse, 60, Random(6))
        assertEquals(coarse.triangleCount, s.shards.sumOf { it.triangles.size })
        s.shards.forEach { assertTrue(it.triangles.isNotEmpty()) }
    }
}
