package com.waxball.asmr.core

/**
 * 삼각형 메시. positions/normals는 정점당 3개 float, indices는 삼각형당 3개.
 */
class Mesh(
    val positions: FloatArray,
    val normals: FloatArray,
    val indices: IntArray,
) {
    val vertexCount: Int get() = positions.size / 3
    val triangleCount: Int get() = indices.size / 3

    fun vertex(i: Int) = Vec3(positions[i * 3], positions[i * 3 + 1], positions[i * 3 + 2])

    /** 삼각형 t의 세 정점 무게중심. */
    fun centroid(t: Int): Vec3 {
        val a = vertex(indices[t * 3])
        val b = vertex(indices[t * 3 + 1])
        val c = vertex(indices[t * 3 + 2])
        return Vec3((a.x + b.x + c.x) / 3f, (a.y + b.y + c.y) / 3f, (a.z + b.z + c.z) / 3f)
    }

    /** 삼각형 t의 면적. */
    fun area(t: Int): Float {
        val a = vertex(indices[t * 3])
        val b = vertex(indices[t * 3 + 1])
        val c = vertex(indices[t * 3 + 2])
        return ((b - a) cross (c - a)).length() * 0.5f
    }
}
