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
     *
     * **이 값은 올리지 말 것.** "천천히 깨지게" 하려고 1.25배로 올려 봤더니
     * "한 번 문지르면 뭔가는 깨진다"와 "도구 다섯 종이 각각 무언가는 깬다"를
     * 잠가 둔 테스트가 걸렸다. 이 값이 그 경계에 맞춰져 있다.
     * 속도는 조각이 매달려 있는 시간([Debris])으로 조절한다.
     */
    private var thresholds = floatArrayOf(0.22f, 0.40f, 0.58f, 0.76f)

    /** 예전 값과 비교해 재보려고 열어 둔다. 앱에서는 쓰지 않는다. */
    internal fun overrideThresholdsForTest(values: FloatArray) {
        thresholds = values
    }

    private var cascadeEnabled = true

    internal fun disableCascadeForTest() {
        cascadeEnabled = false
    }

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

        // 한 번 누를 때 조각 여럿이 같이 떨어지는데, 조각마다 분리음을 따로 내면
        // 저역 몸통이 여섯 겹으로 쌓여 뭉쳐 울린다. 한 덩어리가 떨어지는 건 소리 하나다.
        beginDetachBatch()
        for (s in shards.shards) {
            if (state[s.id] >= ShardState.DETACHED) continue
            val d = hit dot s.center
            if (d < radiusCos) continue
            val falloff = ((d - radiusCos) / span).coerceIn(0f, 1f)
            // 조각마다 버티는 힘이 다르다. 균일하게 누르면 모든 조각이 같은 쥐기에서
            // 임계를 함께 넘어, 첫 쥐기엔 금만 가고 둘째 쥐기에 한꺼번에 전멸한다.
            // 실제 왁뿌볼은 두께가 제각각이라 무른 데부터 차례로 떨어져 나간다.
            press(s.id, force * (0.3f + 0.7f * falloff) * toughness(s.id), dt, pan)
        }
        endDetachBatch(pan)
    }

    private var batchingDetach = false
    private var batchArea = 0f
    private var batchCount = 0
    private var batchEnergy = 0f
    private var batchShard = -1

    private fun beginDetachBatch() {
        batchingDetach = true
        batchArea = 0f
        batchCount = 0
        batchEnergy = 0f
        batchShard = -1
    }

    /** 모아 둔 분리를 소리 하나로 낸다. 넓이는 합치되 상한을 둬서 저역이 폭주하지 않게 한다. */
    private fun endDetachBatch(pan: Float) {
        batchingDetach = false
        if (batchCount == 0) return
        out.push(
            EventKind.DETACH,
            batchShard,
            ShardState.DETACHED,
            min(1f, batchEnergy),
            pan,
            min(batchArea, MAX_DETACH_SOUND_AREA),
        )
    }

    /**
     * 한 번 내려친다. 누르는 것과 달리 시간에 비례하지 않고 정해진 충격량이 한꺼번에 들어간다.
     *
     * 망치질을 "한 프레임 동안 누르기"로 다루면 손상이 힘×0.016초라 터무니없이 작아진다.
     * 실제로 그렇게 만들었더니 망치로 찍어도 아무것도 안 깨졌다.
     *
     * @param damage 한가운데에 들어가는 손상. 가장자리로 갈수록 줄어든다
     */
    fun strikeArea(hit: Vec3, radiusCos: Float, damage: Float, pan: Float) {
        if (damage <= 0f) return
        val span = (1f - radiusCos).coerceAtLeast(1e-4f)

        beginDetachBatch()
        for (s in shards.shards) {
            if (state[s.id] >= ShardState.DETACHED) continue
            val d = hit dot s.center
            if (d < radiusCos) continue
            val falloff = ((d - radiusCos) / span).coerceIn(0f, 1f)

            this.damage[s.id] += damage * (0.25f + 0.75f * falloff)
            advance(s.id, min(1f, damage * 0.5f), pan, spread = true)
        }
        endDetachBatch(pan)
    }

    /** 코어를 누른다. 껍질이 다 벗겨진 뒤에만 소리가 난다. */
    fun squeezeCore(amount: Float, pan: Float) {
        if (!coreExposed) return
        out.push(EventKind.CORE, -1, 0, min(1f, amount), pan, 0f)
    }

    /** 손가락이 표면을 스치는 마찰음. 파괴와 별개로 계속 깔린다. */
    /**
     * 조각별로 다른 "버티는 힘" 0.45~1.0. 조각 번호에서 결정적으로 나온다 —
     * 무작위면 같은 볼을 다시 만들 때마다 다른 자리가 무르게 된다.
     */
    private fun toughness(id: Int): Float {
        var h = id * -0x61c88647
        h = h xor (h ushr 16)
        h *= -0x7ee3623b
        h = h xor (h ushr 13)
        return 0.45f + 0.55f * ((h ushr 8) and 0xFFFF) / 65536f
    }

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

    private fun advance(
        shardId: Int,
        energy: Float,
        pan: Float,
        spread: Boolean,
        allowCascade: Boolean = true,
    ) {
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
                if (batchingDetach) {
                    batchArea += shard.areaFrac
                    batchCount++
                    if (energy > batchEnergy) batchEnergy = energy
                    if (batchShard < 0) batchShard = shardId
                } else {
                    out.push(EventKind.DETACH, shardId, level, energy, pan, shard.areaFrac)
                }
            } else {
                out.push(EventKind.CRACK, shardId, level, energy, pan, shard.areaFrac)
            }

            if (spread) propagate(shardId, level, energy, pan)
            if (allowCascade && level >= ShardState.DETACHED) cascade(shardId, energy, pan)
        }
    }

    /**
     * 넓은 판이 떨어질 때, 이미 들뜬 이웃을 같이 데려간다.
     *
     * 조각이 하나씩 또박또박 떨어지면 사건이 없어 지루하다. 큰 판이 갈 때
     * 옆이 우수수 따라 무너져야 "한 방"이 생긴다.
     * 이미 들뜬(LOOSE) 조각만 데려가므로 아무 데나 무너지지는 않는다.
     */
    private fun cascade(from: Int, energy: Float, pan: Float) {
        if (!cascadeEnabled) return
        if (shards.shards[from].areaFrac < CASCADE_MIN_AREA) return
        for (n in shards.adjacency[from]) {
            if (state[n] != ShardState.LOOSE) continue
            damage[n] = thresholds[ShardState.LOOSE] * profile.toughness + 1e-3f
            // 연쇄가 연쇄를 부르면 한 번에 볼이 통째로 무너진다. 한 겹까지만.
            advance(n, energy * 0.85f, pan, spread = false, allowCascade = false)
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

    /** 이 크기를 넘는 판이 떨어질 때 이웃을 데려간다. */
    private val CASCADE_MIN_AREA = 0.012f

    /**
     * 분리음 하나가 쓸 수 있는 최대 넓이.
     * 이걸 안 걸면 연쇄로 한 번에 볼의 10%가 떨어질 때 저역이 통째로 울려 소리가 뭉갠다.
     */
    private val MAX_DETACH_SOUND_AREA = 0.035f

    /** 조각 중심의 경도로 4분면을 가른다. 사방을 까려면 볼을 굴려야 한다. */
    private fun quadrantIndex(center: Vec3): Int {
        val angle = atan2(center.z, center.x)
        val normalized = (angle + Math.PI.toFloat() * 2f) % (Math.PI.toFloat() * 2f)
        return min(3, (normalized / (Math.PI.toFloat() / 2f)).toInt())
    }
}
