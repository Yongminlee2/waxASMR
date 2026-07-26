package com.waxball.asmr.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IcosphereTest {

    @Test
    fun subdivisionZeroIsIcosahedron() {
        val m = Icosphere.build(0)
        assertEquals(12, m.vertexCount)
        assertEquals(20, m.triangleCount)
    }

    @Test
    fun triangleCountQuadruplesPerSubdivision() {
        assertEquals(80, Icosphere.build(1).triangleCount)
        assertEquals(320, Icosphere.build(2).triangleCount)
        assertEquals(5120, Icosphere.build(4).triangleCount)
    }

    @Test
    fun allVerticesOnUnitSphere() {
        val m = Icosphere.build(3)
        for (i in 0 until m.vertexCount) {
            assertEquals(1.0f, m.vertex(i).length(), 1e-4f)
        }
    }

    @Test
    fun noDuplicateVertices() {
        val m = Icosphere.build(2)
        val seen = HashSet<String>()
        for (i in 0 until m.vertexCount) {
            val v = m.vertex(i)
            val key = "%.5f,%.5f,%.5f".format(v.x, v.y, v.z)
            assertTrue("중복 정점 $key", seen.add(key))
        }
    }

    @Test
    fun indicesStayInRange() {
        val m = Icosphere.build(3)
        assertTrue(m.indices.all { it in 0 until m.vertexCount })
    }

    @Test
    fun totalAreaApproachesSphereSurface() {
        val m = Icosphere.build(4)
        var total = 0f
        for (t in 0 until m.triangleCount) total += m.area(t)
        // 내접 다면체이므로 4π보다 약간 작다.
        val sphere = (4.0 * Math.PI).toFloat()
        assertTrue("면적 $total 이 구 표면적 $sphere 에 가깝지 않음", total in sphere * 0.99f..sphere)
    }

    @Test
    fun normalsMatchPositionsOnUnitSphere() {
        val m = Icosphere.build(2)
        for (i in 0 until m.vertexCount) {
            assertEquals(m.positions[i * 3], m.normals[i * 3], 1e-6f)
            assertEquals(m.positions[i * 3 + 1], m.normals[i * 3 + 1], 1e-6f)
            assertEquals(m.positions[i * 3 + 2], m.normals[i * 3 + 2], 1e-6f)
        }
    }
}
