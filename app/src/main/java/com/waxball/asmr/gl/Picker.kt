package com.waxball.asmr.gl

import com.waxball.asmr.core.ShardSet
import com.waxball.asmr.core.ShardState
import com.waxball.asmr.core.Vec3
import kotlin.math.sqrt

/**
 * 화면을 누른 지점이 어느 조각인지 찾는다.
 *
 * 조각 메시와 일일이 교차 판정하지 않는다. 조각은 결국 구면을 시드 기준으로 나눈 것이라,
 * 구와 광선의 교점 방향에서 가장 가까운 시드를 찾으면 그게 그 조각이다.
 * 조각이 300개여도 내적 300번이면 끝난다.
 */
object Picker {

    /** 광선이 볼을 완전히 빗나갔다. */
    const val MISS = -2

    /** 껍질이 이미 떨어져 나간 자리다. 안쪽 말랑이를 누른 것으로 본다. */
    const val CORE = -1

    /**
     * @param origin 볼 좌표계에서의 광선 시작점
     * @param dir 볼 좌표계에서의 광선 방향(정규화되어 있지 않아도 된다)
     * @param maxRadius 모양 왜곡까지 감안한 가장 바깥 반지름
     */
    fun pick(
        origin: Vec3,
        dir: Vec3,
        set: ShardSet,
        state: IntArray,
        maxRadius: Float = 1.15f,
    ): Int {
        val d = dir.normalized()
        val b = origin dot d
        val c = origin.lengthSq() - maxRadius * maxRadius
        val disc = b * b - c
        if (disc < 0f) return MISS

        val sq = sqrt(disc)
        var t = -b - sq
        if (t < 0f) t = -b + sq
        if (t < 0f) return MISS

        val hit = (origin + d * t).normalized()

        var best = -1
        var bestDot = -2f
        for (s in set.shards) {
            val dot = hit dot s.center
            if (dot > bestDot) { bestDot = dot; best = s.id }
        }
        if (best < 0) return MISS

        return if (state[best] >= ShardState.DETACHED) CORE else best
    }
}
