package com.waxball.asmr.core

/**
 * 왁스 껍질 조각 하나. 기반 구 메시의 삼각형 묶음으로 표현된다.
 *
 * @param center 조각 중심 방향(정규화). 4분면 판정과 낙하 방향에 쓴다.
 * @param areaFrac 전체 껍질 대비 면적 비율. 소리 크기·음높이와 진행률 계산에 쓴다.
 */
class Shard(
    val id: Int,
    val center: Vec3,
    val areaFrac: Float,
    val triangles: IntArray,
)

class ShardSet(
    val shards: Array<Shard>,
    val adjacency: Array<IntArray>,
    val baseMesh: Mesh,
) {
    val size: Int get() = shards.size
}
