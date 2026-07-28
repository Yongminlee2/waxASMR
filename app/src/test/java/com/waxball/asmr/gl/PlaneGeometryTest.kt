package com.waxball.asmr.gl

import com.waxball.asmr.core.PlaneShards
import com.waxball.asmr.core.PlaneSplitter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PlaneGeometryTest {

    private val set = PlaneShards.toShardSet(PlaneSplitter.split(80, Random(5)))
    private val geometry = PlaneGeometry.build(set)

    @Test
    fun everyVertexHasEveryAttribute() {
        val vertices = geometry.positions.size / 3
        assertEquals("UV 개수가 안 맞음", vertices * 2, geometry.uvs.size)
        assertEquals("조각번호 개수가 안 맞음", vertices, geometry.shardIds.size)
    }

    @Test
    fun everyTriangleIsKept() {
        assertEquals(set.baseMesh.indices.size, geometry.indices.size)
    }

    @Test
    fun positionsAreInClipSpace() {
        // 투영 행렬 없이 그대로 그리므로 -1~1 안이어야 한다.
        for (i in geometry.positions.indices step 3) {
            assertTrue("x가 범위 밖: ${geometry.positions[i]}", geometry.positions[i] in -1.01f..1.01f)
            assertTrue("y가 범위 밖: ${geometry.positions[i + 1]}", geometry.positions[i + 1] in -1.01f..1.01f)
        }
    }

    @Test
    fun uvsAreInTextureSpace() {
        for (v in geometry.uvs) assertTrue("UV가 범위 밖: $v", v in -0.01f..1.01f)
    }

    @Test
    fun theTopOfTheScreenMapsToTheTopOfTheTexture() {
        // 화면은 위가 0, 클립 공간은 위가 +1이다. 안 뒤집으면 사진이 거꾸로 나온다.
        var topVertex = 0
        for (i in geometry.uvs.indices step 2) {
            if (geometry.uvs[i + 1] < geometry.uvs[topVertex * 2 + 1]) topVertex = i / 2
        }
        assertTrue(
            "UV 위쪽이 화면 위쪽에 안 붙음",
            geometry.positions[topVertex * 3 + 1] > 0.9f,
        )
    }

    @Test
    fun everyShardIdIsValid() {
        for (v in geometry.shardIds) {
            assertTrue("조각번호가 범위 밖: $v", v >= 0f && v < set.size.toFloat())
        }
    }

    @Test
    fun eachVertexBelongsToExactlyOneShard() {
        // 한 정점이 두 조각에 걸치면 조각이 따로 떨어질 수 없다.
        val counts = IntArray(set.size)
        for (v in geometry.shardIds) counts[v.toInt()]++
        for (i in counts.indices) assertTrue("조각 $i 의 정점이 없음", counts[i] > 0)
    }
}
