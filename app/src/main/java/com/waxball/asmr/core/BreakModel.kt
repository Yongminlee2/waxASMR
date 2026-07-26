package com.waxball.asmr.core

import kotlin.math.atan2
import kotlin.math.min

/**
 * 껍질이 깨지는 규칙 전부. 안드로이드 API를 쓰지 않으므로 PC에서 그대로 검증할 수 있다.
 *
 * 조각은 누를 때마다 손상이 쌓이고, 임계를 넘을 때마다 한 단계씩 진행한다.
 * 단계가 넘어갈 때 이웃 조각에도 손상이 번진다. 한 곳을 세게 누르면 옆이 같이 금 가고,
 * 다음에 그쪽을 건드리면 우수수 떨어진다. 이게 깨는 맛의 정체다.
 *
 * 번짐은 한 겹까지만 간다. 연쇄가 연쇄를 부르면 한 번 누를 때 볼이 통째로 무너진다.
 */
class BreakModel(
    val shards: ShardSet,
    val profile: SoundProfile,
    private val out: EventQueue,
) {
    /**
     * 누적 손상이 이 값을 넘을 때마다 다음 단계로 간다. toughness가 곱해진다.
     *
     * 처음에는 열 배쯤 높게 잡아서 같은 자리를 몇 초씩 눌러야 조각 하나가 떨어졌다.
     * 실제 영상에서는 손가락이 한 번 스치면 우수수 떨어진다. 거기에 맞춰 낮췄다.
     */
    private val thresholds = floatArrayOf(0.22f, 0.40f, 0.58f, 0.76f)

    val state = IntArray(shards.size)
    private val damage = FloatArray(shards.size)

    private val quadrantOf = IntArray(shards.size)
    private val quadrantArea = FloatArray(4)
    private val quadrantDetached = FloatArray(4)

    var detachedArea = 0f
        private set

    var detachedCount = 0
        private set

    /** 상태가 한 단계 넘어간 총 횟수. 미션이 "금이 끊기지 않았는지" 볼 때 쓴다. */
    var transitions = 0
        private set

    init {
        for (s in shards.shards) {
            val q = quadrantIndex(s.center)
            quadrantOf[s.id] = q
            quadrantArea[q] += s.areaFrac
        }
    }

    /** 껍질이 벗겨진 비율 0~1. */
    val shellProgress: Float get() = min(1f, detachedArea)

    /** 말랑이 코어가 드러났는가. */
    val coreExposed: Boolean get() = shellProgress >= 0.98f

    /**
     * 조각 하나를 누른다.
     *
     * @param force 누르는 세기. 1이 보통, 4 이상이면 아주 세게
     * @param dt 눌린 시간(초)
     * @param pan 스테레오 위치 -1(왼쪽)~1(오른쪽)
     */
    fun press(shardId: Int, force: Float, dt: Float, pan: Float) {
        if (shardId < 0 || shardId >= state.size) return
        if (state[shardId] >= ShardState.DETACHED) return
        if (force <= 0f || dt <= 0f) return

        damage[shardId] += force * dt
        val energy = min(1f, force / 4f)
        advance(shardId, energy, pan, spread = true)
    }

    /**
     * 손가락이 닿은 면적만큼 한꺼번에 누른다.
     *
     * 조각 하나만 정확히 누르는 것은 손가락이 아니라 바늘이다. 실제로는 한 번 스치면
     * 닿은 자리 전체가 우수수 떨어진다. 가운데가 가장 세고 가장자리로 갈수록 약해진다.
     *
     * @param hit 손가락이 닿은 방향(정규화)
     * @param radiusCos 붓 반경. 이 값보다 내적이 큰 조각이 닿은 것으로 본다
     */
    fun pressArea(hit: Vec3, radiusCos: Float, force: Float, dt: Float, pan: Float) {
        if (force <= 0f || dt <= 0f) return
        val span = (1f - radiusCos).coerceAtLeast(1e-4f)
        for (s in shards.shards) {
            if (state[s.id] >= ShardState.DETACHED) continue
            val d = hit dot s.center
            if (d < radiusCos) continue
            val falloff = ((d - radiusCos) / span).coerceIn(0f, 1f)
            press(s.id, force * (0.3f + 0.7f * falloff), dt, pan)
        }
    }

    /** 코어를 누른다. 껍질이 다 벗겨진 뒤에만 소리가 난다. */
    fun squeezeCore(amount: Float, pan: Float) {
        if (!coreExposed) return
        out.push(EventKind.CORE, -1, 0, min(1f, amount), pan, 0f)
    }

    /** 손가락이 표면을 스치는 마찰음. 파괴와 별개로 계속 깔린다. */
    fun rub(speed: Float, pan: Float) {
        if (speed <= 0.01f) return
        out.push(EventKind.RUB, -1, 0, min(1f, speed), pan, 0f)
    }

    /** 떨어진 조각이 바닥에 닿았을 때. Debris가 호출한다. */
    fun land(shardId: Int, pan: Float, areaFrac: Float) {
        out.push(EventKind.LAND, shardId, 0, 1f, pan, areaFrac)
    }

    /** 4분면별 벗겨진 비율. 볼을 굴려가며 사방을 까는 미션에 쓴다. */
    fun quadrantProgress(): FloatArray = FloatArray(4) { q ->
        if (quadrantArea[q] <= 0f) 1f else min(1f, quadrantDetached[q] / quadrantArea[q])
    }

    private fun advance(shardId: Int, energy: Float, pan: Float, spread: Boolean) {
        val toughness = profile.toughness
        while (state[shardId] < ShardState.DETACHED &&
            damage[shardId] >= thresholds[state[shardId]] * toughness
        ) {
            val level = state[shardId] + 1
            state[shardId] = level
            transitions++

            val shard = shards.shards[shardId]
            if (level >= ShardState.DETACHED) {
                detachedArea += shard.areaFrac
                detachedCount++
                quadrantDetached[quadrantOf[shardId]] += shard.areaFrac
                out.push(EventKind.DETACH, shardId, level, energy, pan, shard.areaFrac)
            } else {
                out.push(EventKind.CRACK, shardId, level, energy, pan, shard.areaFrac)
            }

            if (spread) propagate(shardId, level, energy, pan)
        }
    }

    /** 한 겹 이웃에만 손상을 전달한다. 전달받은 조각은 다시 전달하지 않는다. */
    private fun propagate(from: Int, level: Int, energy: Float, pan: Float) {
        val transfer = profile.propagation * thresholds[level - 1] * profile.toughness
        for (n in shards.adjacency[from]) {
            if (state[n] >= ShardState.DETACHED) continue
            damage[n] += transfer
            advance(n, energy * 0.6f, pan, spread = false)
        }
    }

    /** 조각 중심의 경도로 4분면을 가른다. 사방을 까려면 볼을 굴려야 한다. */
    private fun quadrantIndex(center: Vec3): Int {
        val angle = atan2(center.z, center.x)
        val normalized = (angle + Math.PI.toFloat() * 2f) % (Math.PI.toFloat() * 2f)
        return min(3, (normalized / (Math.PI.toFloat() / 2f)).toInt())
    }
}
