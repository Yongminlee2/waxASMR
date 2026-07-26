package com.waxball.asmr.gl

import com.waxball.asmr.core.ShardSet
import com.waxball.asmr.core.Vec3

/**
 * 정점 배열. 조각별로 정점을 따로 갖기 때문에 조각 하나만 따로 움직여도 된다.
 *
 * shrink는 "이 정점이 조각 중심에서 얼마나 떨어져 있는가" 벡터다. 금이 갈수록
 * 셰이더가 이 벡터만큼 정점을 중심으로 당겨 조각을 살짝 줄인다. 그러면 조각 사이에
 * 틈이 벌어져 금이 간 것처럼 보인다. 별도 텍스처 없이 금을 그리는 방법이다.
 *
 * face는 바깥면 1, 안쪽면 -1, 측면 0. 프래그먼트 셰이더가 색을 나눠 칠한다.
 */
class GeometryBuffers(
    val positions: FloatArray,
    val normals: FloatArray,
    val shrink: FloatArray,
    val shardAndFace: FloatArray,
    val indices: IntArray,
    /** 조각당 3개 float. 떨어진 조각이 제자리에서 구르게 하려면 회전 중심이 필요하다. */
    val shardCenters: FloatArray,
) {
    val vertexCount: Int get() = positions.size / 3
    val triangleCount: Int get() = indices.size / 3
}

object BallGeometry {

    /**
     * @param thickness 껍질 두께(반지름 대비 비율). 두꺼우면 깨진 자리에 단면이 두껍게 보인다.
     * @param warp 방향 → 반지름 배율. 달걀·각진 볼 같은 모양을 만든다.
     */
    fun build(set: ShardSet, thickness: Float, warp: (Vec3) -> Float): GeometryBuffers {
        val mesh = set.baseMesh
        val t = thickness.coerceIn(0.02f, 0.4f)

        val positions = ArrayList<Float>(1 shl 16)
        val normals = ArrayList<Float>(1 shl 16)
        val shrink = ArrayList<Float>(1 shl 16)
        val shardAndFace = ArrayList<Float>(1 shl 15)
        val indices = ArrayList<Int>(1 shl 16)
        val centers = FloatArray(set.size * 3)

        for (shard in set.shards) {
            val center = shardCenter(mesh, shard.triangles, warp, t)
            centers[shard.id * 3] = center.x
            centers[shard.id * 3 + 1] = center.y
            centers[shard.id * 3 + 2] = center.z
            val boundary = boundaryEdges(mesh, shard.triangles)

            for (tri in shard.triangles) {
                val ia = mesh.indices[tri * 3]
                val ib = mesh.indices[tri * 3 + 1]
                val ic = mesh.indices[tri * 3 + 2]
                val da = mesh.vertex(ia); val db = mesh.vertex(ib); val dc = mesh.vertex(ic)

                // 바깥면: 법선은 반지름 방향이라 정점을 나눠 써도 매끈하게 보인다.
                val base = positions.size / 3
                emit(positions, normals, shrink, shardAndFace, outer(da, warp), da, center, shard.id, 1f)
                emit(positions, normals, shrink, shardAndFace, outer(db, warp), db, center, shard.id, 1f)
                emit(positions, normals, shrink, shardAndFace, outer(dc, warp), dc, center, shard.id, 1f)
                indices.add(base); indices.add(base + 1); indices.add(base + 2)

                // 안쪽면: 감김을 뒤집어 안에서 봤을 때 보이게 한다.
                val inBase = positions.size / 3
                emit(positions, normals, shrink, shardAndFace, inner(da, warp, t), -da, center, shard.id, -1f)
                emit(positions, normals, shrink, shardAndFace, inner(db, warp, t), -db, center, shard.id, -1f)
                emit(positions, normals, shrink, shardAndFace, inner(dc, warp, t), -dc, center, shard.id, -1f)
                indices.add(inBase); indices.add(inBase + 2); indices.add(inBase + 1)
            }

            // 측면: 조각 가장자리를 막아 두께가 보이게 한다.
            for (e in boundary) {
                val u = mesh.vertex(e.first)
                val v = mesh.vertex(e.second)
                val ou = outer(u, warp); val ov = outer(v, warp)
                val iu = inner(u, warp, t); val iv = inner(v, warp, t)
                val n = ((ov - ou) cross (iu - ou)).normalized()

                val b = positions.size / 3
                emit(positions, normals, shrink, shardAndFace, ou, n, center, shard.id, 0f)
                emit(positions, normals, shrink, shardAndFace, ov, n, center, shard.id, 0f)
                emit(positions, normals, shrink, shardAndFace, iv, n, center, shard.id, 0f)
                emit(positions, normals, shrink, shardAndFace, iu, n, center, shard.id, 0f)
                indices.add(b); indices.add(b + 1); indices.add(b + 2)
                indices.add(b); indices.add(b + 2); indices.add(b + 3)
            }
        }

        return GeometryBuffers(
            positions.toFloatArray(),
            normals.toFloatArray(),
            shrink.toFloatArray(),
            shardAndFace.toFloatArray(),
            indices.toIntArray(),
            centers,
        )
    }

    private fun emit(
        positions: ArrayList<Float>,
        normals: ArrayList<Float>,
        shrink: ArrayList<Float>,
        shardAndFace: ArrayList<Float>,
        p: Vec3,
        n: Vec3,
        center: Vec3,
        shardId: Int,
        face: Float,
    ) {
        positions.add(p.x); positions.add(p.y); positions.add(p.z)
        val nn = n.normalized()
        normals.add(nn.x); normals.add(nn.y); normals.add(nn.z)
        val d = p - center
        shrink.add(d.x); shrink.add(d.y); shrink.add(d.z)
        shardAndFace.add(shardId.toFloat()); shardAndFace.add(face)
    }

    private fun outer(dir: Vec3, warp: (Vec3) -> Float) = dir * warp(dir)

    private fun inner(dir: Vec3, warp: (Vec3) -> Float, t: Float) = dir * (warp(dir) * (1f - t))

    /** 조각 껍질의 무게중심. 금이 갈 때 정점을 여기로 당긴다. */
    private fun shardCenter(
        mesh: com.waxball.asmr.core.Mesh,
        triangles: IntArray,
        warp: (Vec3) -> Float,
        t: Float,
    ): Vec3 {
        var x = 0f; var y = 0f; var z = 0f
        var n = 0
        val mid = 1f - t * 0.5f
        for (tri in triangles) {
            for (k in 0 until 3) {
                val d = mesh.vertex(mesh.indices[tri * 3 + k])
                val p = d * (warp(d) * mid)
                x += p.x; y += p.y; z += p.z; n++
            }
        }
        return if (n == 0) Vec3.ZERO else Vec3(x / n, y / n, z / n)
    }

    /** 조각 안에서 한 번만 등장하는 에지 = 조각의 바깥 테두리. */
    private fun boundaryEdges(
        mesh: com.waxball.asmr.core.Mesh,
        triangles: IntArray,
    ): List<Pair<Int, Int>> {
        val count = HashMap<Long, Pair<Int, Int>>(triangles.size * 3)
        val seen = HashMap<Long, Int>(triangles.size * 3)
        for (tri in triangles) {
            val a = mesh.indices[tri * 3]
            val b = mesh.indices[tri * 3 + 1]
            val c = mesh.indices[tri * 3 + 2]
            for (pair in listOf(a to b, b to c, c to a)) {
                val k = key(pair.first, pair.second)
                seen[k] = (seen[k] ?: 0) + 1
                count[k] = pair
            }
        }
        return seen.filterValues { it == 1 }.keys.map { count[it]!! }
    }

    private fun key(a: Int, b: Int): Long {
        val lo = minOf(a, b).toLong()
        val hi = maxOf(a, b).toLong()
        return (lo shl 32) or hi
    }
}
