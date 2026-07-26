package com.waxball.asmr.gl

import com.waxball.asmr.core.Icosphere
import com.waxball.asmr.core.ShardSplitter
import com.waxball.asmr.core.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class BallGeometryTest {

    private fun set(seeds: Int = 50, subdiv: Int = 3, seed: Long = 1) =
        ShardSplitter.split(Icosphere.build(subdiv), seeds, Random(seed))

    private val sphere: (Vec3) -> Float = { 1f }

    @Test
    fun everyVertexCarriesItsShardId() {
        val s = set()
        val g = BallGeometry.build(s, 0.12f, sphere)
        assertEquals(g.vertexCount * 2, g.shardAndFace.size)
        for (i in 0 until g.vertexCount) {
            val id = g.shardAndFace[i * 2]
            assertTrue("조각 번호 이탈: $id", id >= 0f && id < s.size)
        }
    }

    @Test
    fun everyShardAppearsInTheBuffer() {
        val s = set()
        val g = BallGeometry.build(s, 0.12f, sphere)
        val seen = BooleanArray(s.size)
        for (i in 0 until g.vertexCount) seen[g.shardAndFace[i * 2].toInt()] = true
        assertTrue("버퍼에 빠진 조각이 있음", seen.all { it })
    }

    @Test
    fun innerSurfaceSitsBelowOuterSurface() {
        val g = BallGeometry.build(set(30, 2), 0.2f, sphere)
        var maxR = 0f
        var minR = Float.MAX_VALUE
        for (i in 0 until g.vertexCount) {
            val r = Vec3(g.positions[i * 3], g.positions[i * 3 + 1], g.positions[i * 3 + 2]).length()
            if (r > maxR) maxR = r
            if (r < minR) minR = r
        }
        assertEquals(1.0f, maxR, 1e-3f)
        assertEquals(0.8f, minR, 1e-3f)
    }

    @Test
    fun facesAreLabelledConsistently() {
        val g = BallGeometry.build(set(), 0.12f, sphere)
        val faces = HashSet<Float>()
        for (i in 0 until g.vertexCount) faces.add(g.shardAndFace[i * 2 + 1])
        assertEquals("바깥면·안쪽면·측면 세 종류가 나와야 함", setOf(1f, -1f, 0f), faces)
    }

    @Test
    fun outerFaceVerticesSitOnTheOuterRadius() {
        val g = BallGeometry.build(set(), 0.15f, sphere)
        for (i in 0 until g.vertexCount) {
            if (g.shardAndFace[i * 2 + 1] != 1f) continue
            val r = Vec3(g.positions[i * 3], g.positions[i * 3 + 1], g.positions[i * 3 + 2]).length()
            assertEquals(1f, r, 1e-3f)
        }
    }

    @Test
    fun indicesAreInRangeAndFormTriangles() {
        val g = BallGeometry.build(set(40), 0.15f, sphere)
        assertEquals(0, g.indices.size % 3)
        assertTrue(g.indices.all { it in 0 until g.vertexCount })
    }

    @Test
    fun normalsAreUnitLength() {
        val g = BallGeometry.build(set(40), 0.15f, sphere)
        for (i in 0 until g.vertexCount) {
            val n = Vec3(g.normals[i * 3], g.normals[i * 3 + 1], g.normals[i * 3 + 2])
            assertEquals("법선 길이가 1이 아님", 1f, n.length(), 1e-3f)
        }
    }

    @Test
    fun shrinkVectorsPointAwayFromShardCentre() {
        // 셰이더가 이 벡터를 빼서 조각을 줄인다. 평균이 0에 가까워야 조각이 제자리에서 줄어든다.
        val s = set(40)
        val g = BallGeometry.build(s, 0.12f, sphere)
        val sumX = FloatArray(s.size); val counts = IntArray(s.size)
        for (i in 0 until g.vertexCount) {
            val id = g.shardAndFace[i * 2].toInt()
            sumX[id] += g.shrink[i * 3]
            counts[id]++
        }
        for (i in s.shards.indices) {
            assertTrue("조각 $i 의 수축 중심이 치우침", abs(sumX[i] / counts[i]) < 0.35f)
        }
    }

    @Test
    fun eggShapeStretchesAlongY() {
        val egg: (Vec3) -> Float = { 1f + 0.2f * it.y * it.y }
        val g = BallGeometry.build(set(30, 2), 0.1f, egg)
        var maxY = 0f; var maxX = 0f
        for (i in 0 until g.vertexCount) {
            if (g.shardAndFace[i * 2 + 1] != 1f) continue
            maxY = maxOf(maxY, abs(g.positions[i * 3 + 1]))
            maxX = maxOf(maxX, abs(g.positions[i * 3]))
        }
        assertTrue("달걀 모양이 세로로 늘어나지 않음 (y=$maxY, x=$maxX)", maxY > maxX * 1.1f)
    }

    @Test
    fun thickerShellProducesMoreRimGeometry() {
        // 두께가 달라도 측면 삼각형 수는 같아야 한다. 테두리 개수는 두께와 무관하기 때문이다.
        val s = set(40)
        val thin = BallGeometry.build(s, 0.05f, sphere)
        val thick = BallGeometry.build(s, 0.25f, sphere)
        assertEquals(thin.triangleCount, thick.triangleCount)
    }

    @Test
    fun geometrySizeStaysWithinBudgetAtMaxQuality() {
        val g = BallGeometry.build(set(300, 4), 0.12f, sphere)
        assertTrue("정점이 너무 많음: ${g.vertexCount}", g.vertexCount < 120_000)
        assertTrue("삼각형이 너무 많음: ${g.triangleCount}", g.triangleCount < 60_000)
    }
}
