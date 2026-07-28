package com.waxball.asmr.gl

import com.waxball.asmr.core.ShardSet

/**
 * 평면 조각을 GL이 먹는 배열로 바꾼다.
 *
 * 위치는 클립 공간(-1~1)으로 낸다. 화면을 꽉 채우는 사진이라 투영 행렬을 쓸 이유가 없다.
 * 그냥 정점을 그대로 그리면 사진이 화면에 1:1로 깔린다.
 *
 * GL을 부르지 않으므로 PC에서 검증한다.
 */
class PlaneGeometry(
    val positions: FloatArray,
    val uvs: FloatArray,
    val shardIds: FloatArray,
    val indices: IntArray,
) {
    companion object {
        fun build(set: ShardSet): PlaneGeometry {
            val mesh = set.baseMesh
            val vertices = mesh.vertexCount

            val positions = FloatArray(vertices * 3)
            val uvs = FloatArray(vertices * 2)
            val shardIds = FloatArray(vertices)

            for (v in 0 until vertices) {
                val x = mesh.positions[v * 3]
                val y = mesh.positions[v * 3 + 1]
                // 화면은 위가 0, 클립 공간은 위가 +1이다. 안 뒤집으면 사진이 거꾸로 나온다.
                positions[v * 3] = x * 2f - 1f
                positions[v * 3 + 1] = 1f - y * 2f
                positions[v * 3 + 2] = 0f
                uvs[v * 2] = x
                uvs[v * 2 + 1] = y
            }

            // 조각 번호는 정점마다 실어 보낸다. 셰이더가 이걸로 자기 변환을 찾아온다.
            for (s in set.shards) {
                for (t in s.triangles) {
                    for (k in 0 until 3) shardIds[mesh.indices[t * 3 + k]] = s.id.toFloat()
                }
            }

            return PlaneGeometry(positions, uvs, shardIds, mesh.indices.copyOf())
        }
    }
}
