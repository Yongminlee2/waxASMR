package com.waxball.asmr.core

import kotlin.math.sqrt

/**
 * 화면 뿌시기 모드가 쓰는 재질.
 *
 * 유리 깨지는 쪽에 가장 가까운 것이 설탕유리인데, 그대로 쓰면 단단함이 1.25라
 * 잘 안 깨진다. 이 모드는 스트레스 해소가 목적이라 왁뿌볼보다 시원하게 깨져야 한다.
 */
object SmashProfile {
    fun of(): SoundProfile = SoundProfile.sugarGlass().copy(toughness = 0.55f)
}

/**
 * 평면 조각을 기존 파괴 모델에 연결한다.
 *
 * [BreakModel] 은 손상 누적·금 전파·연쇄 붕괴·소리를 전부 갖고 있고, 조각이 구면에
 * 있든 평면에 있든 상관하지 않는다. 구면을 전제하는 것은 접촉 범위를 내적으로 정하는
 * `pressArea` 하나뿐이라, 그것만 여기서 화면 거리로 다시 만든다.
 */
object PlaneShards {

    /** 셀을 [BreakModel] 이 받는 형태로 바꾼다. 조각마다 부채꼴 삼각형을 갖는다. */
    fun toShardSet(cells: List<PlaneCell>): ShardSet {
        val positions = ArrayList<Float>(cells.size * 24)
        val indices = ArrayList<Int>(cells.size * 18)
        val shards = ArrayList<Shard>(cells.size)

        for (c in cells) {
            val first = positions.size / 3
            val verts = c.polygon.size / 2

            // 무게중심을 가운데 정점으로 두고 부채꼴로 자른다. 볼록 폴리곤이라 이걸로 충분하다.
            positions.add(c.centerX); positions.add(c.centerY); positions.add(0f)
            for (i in 0 until verts) {
                positions.add(c.polygon[i * 2]); positions.add(c.polygon[i * 2 + 1]); positions.add(0f)
            }

            val triangles = IntArray(verts)
            for (i in 0 until verts) {
                triangles[i] = indices.size / 3
                indices.add(first)
                indices.add(first + 1 + i)
                indices.add(first + 1 + (i + 1) % verts)
            }

            shards.add(
                Shard(
                    id = c.id,
                    // 평면이므로 z는 0이다. BreakModel은 초기화 때 4분면을 계산하는데,
                    // 그 값은 미션에서만 쓰이고 이 모드는 미션을 쓰지 않는다.
                    center = Vec3(c.centerX, c.centerY, 0f),
                    areaFrac = c.areaFrac,
                    triangles = triangles,
                )
            )
        }

        val normals = FloatArray(positions.size)
        var i = 2
        while (i < normals.size) { normals[i] = 1f; i += 3 }

        val mesh = Mesh(
            positions = FloatArray(positions.size) { positions[it] },
            normals = normals,
            indices = IntArray(indices.size) { indices[it] },
        )
        val adjacency = Array(cells.size) { cells[it].neighbours }
        return ShardSet(shards.toTypedArray(), adjacency, mesh)
    }

    /**
     * 손가락이 닿은 자리의 조각을 누른다.
     *
     * 가운데가 가장 세고 가장자리로 갈수록 약하다. 조각 하나만 정확히 누르는 것은
     * 손가락이 아니라 바늘이다. 기존 화면이 붓 반경을 두는 것과 같은 이유다.
     *
     * @param x 화면 정규 좌표 0~1
     * @param radius 붓 반경(화면 정규 단위)
     * @return 이번에 힘이 들어간 조각 수
     */
    fun pressAt(
        model: BreakModel,
        cells: List<PlaneCell>,
        x: Float,
        y: Float,
        radius: Float,
        force: Float,
        dt: Float,
    ): Int {
        if (force <= 0f || dt <= 0f || radius <= 0f) return 0
        val pan = (x * 2f - 1f).coerceIn(-1f, 1f)
        var touched = 0

        for (c in cells) {
            val dx = c.centerX - x
            val dy = c.centerY - y
            val distance = sqrt(dx * dx + dy * dy)
            if (distance > radius) continue

            val falloff = 1f - distance / radius
            model.press(c.id, force * (EDGE_SHARE + (1f - EDGE_SHARE) * falloff), dt, pan)
            touched++
        }
        return touched
    }

    /** 붓 가장자리에 들어가는 힘의 비율. */
    private const val EDGE_SHARE = 0.3f
}
