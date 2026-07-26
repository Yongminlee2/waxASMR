package com.waxball.asmr.core

import kotlin.math.sqrt

/**
 * 정이십면체를 재귀 분할해 만든 단위 구 메시.
 * 위도·경도 격자와 달리 삼각형 크기가 고르기 때문에 조각 분할이 균일하게 나온다.
 */
object Icosphere {

    fun build(subdivisions: Int): Mesh {
        val t = (1f + sqrt(5f)) / 2f

        val verts = ArrayList<Vec3>(12)
        verts.add(Vec3(-1f, t, 0f)); verts.add(Vec3(1f, t, 0f))
        verts.add(Vec3(-1f, -t, 0f)); verts.add(Vec3(1f, -t, 0f))
        verts.add(Vec3(0f, -1f, t)); verts.add(Vec3(0f, 1f, t))
        verts.add(Vec3(0f, -1f, -t)); verts.add(Vec3(0f, 1f, -t))
        verts.add(Vec3(t, 0f, -1f)); verts.add(Vec3(t, 0f, 1f))
        verts.add(Vec3(-t, 0f, -1f)); verts.add(Vec3(-t, 0f, 1f))
        for (i in verts.indices) verts[i] = verts[i].normalized()

        var faces = intArrayOf(
            0, 11, 5, 0, 5, 1, 0, 1, 7, 0, 7, 10, 0, 10, 11,
            1, 5, 9, 5, 11, 4, 11, 10, 2, 10, 7, 6, 7, 1, 8,
            3, 9, 4, 3, 4, 2, 3, 2, 6, 3, 6, 8, 3, 8, 9,
            4, 9, 5, 2, 4, 11, 6, 2, 10, 8, 6, 7, 9, 8, 1,
        )

        repeat(subdivisions) {
            val midCache = HashMap<Long, Int>(faces.size)
            val next = IntArray(faces.size * 4)
            var w = 0
            var f = 0
            while (f < faces.size) {
                val a = faces[f]; val b = faces[f + 1]; val c = faces[f + 2]
                val ab = midpoint(verts, midCache, a, b)
                val bc = midpoint(verts, midCache, b, c)
                val ca = midpoint(verts, midCache, c, a)
                next[w++] = a; next[w++] = ab; next[w++] = ca
                next[w++] = b; next[w++] = bc; next[w++] = ab
                next[w++] = c; next[w++] = ca; next[w++] = bc
                next[w++] = ab; next[w++] = bc; next[w++] = ca
                f += 3
            }
            faces = next
        }

        val positions = FloatArray(verts.size * 3)
        val normals = FloatArray(verts.size * 3)
        for (i in verts.indices) {
            val v = verts[i]
            positions[i * 3] = v.x; positions[i * 3 + 1] = v.y; positions[i * 3 + 2] = v.z
            // 단위 구이므로 위치가 곧 법선이다.
            normals[i * 3] = v.x; normals[i * 3 + 1] = v.y; normals[i * 3 + 2] = v.z
        }
        return Mesh(positions, normals, faces)
    }

    /** 에지 중점을 캐시해 정점이 중복 생성되지 않게 한다. */
    private fun midpoint(verts: ArrayList<Vec3>, cache: HashMap<Long, Int>, a: Int, b: Int): Int {
        val lo = minOf(a, b).toLong()
        val hi = maxOf(a, b).toLong()
        val key = (lo shl 32) or hi
        cache[key]?.let { return it }
        val m = ((verts[a] + verts[b]) * 0.5f).normalized()
        verts.add(m)
        val idx = verts.size - 1
        cache[key] = idx
        return idx
    }
}
