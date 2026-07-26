package com.waxball.asmr.core

import kotlin.math.min

/** 지금 손가락이 뭘 하고 있는지. 플레이 화면이 매 프레임 채워 준다. */
class Gesture {
    var touching = false
    var force = 0f
    var speed = 0f
    var strokeId = 0
    var coreTouches = 0

    fun reset() {
        touching = false; force = 0f; speed = 0f; strokeId = 0; coreTouches = 0
    }
}

/**
 * 미션 판정기.
 *
 * 파괴 모델을 들여다볼 뿐 건드리지 않는다. 미션을 고치다 샌드박스가 망가지는 일을 막기 위해서다.
 * 판정에 필요한 것은 전부 BreakModel의 읽기 전용 값과 Gesture로 얻는다.
 */
abstract class Mission(
    val id: Int,
    val titleKo: String,
    val reward: Int,
    /** 0이면 시간 제한 없음. */
    val timeLimitSeconds: Float = 0f,
) {
    companion object {
        const val RUNNING = 0
        const val CLEARED = 1
        const val FAILED = 2
    }

    var state = RUNNING
        protected set

    var elapsed = 0f
        private set

    /** 0~1. HUD 진행바에 그대로 쓴다. */
    abstract val progress: Float

    fun update(dt: Float, model: BreakModel, gesture: Gesture) {
        if (state != RUNNING) return
        elapsed += dt
        onFrame(dt, model, gesture)
        if (state == RUNNING && timeLimitSeconds > 0f && elapsed >= timeLimitSeconds) {
            state = FAILED
        }
    }

    protected abstract fun onFrame(dt: Float, model: BreakModel, gesture: Gesture)

    protected fun clear() { state = CLEARED }
    protected fun fail() { state = FAILED }

    val remainingSeconds: Float
        get() = if (timeLimitSeconds <= 0f) 0f else (timeLimitSeconds - elapsed).coerceAtLeast(0f)
}

/** ① 속도 — 제한시간 안에 껍질 80% 까기. */
class SpeedMission : Mission(0, "45초 안에 껍질 80% 까기", 40, 45f) {
    private val target = 0.8f
    override val progress: Float get() = last

    private var last = 0f

    override fun onFrame(dt: Float, model: BreakModel, gesture: Gesture) {
        last = min(1f, model.shellProgress / target)
        if (model.shellProgress >= target) clear()
    }
}

/** ② 한 획 — 손 떼지 않고 한 번에 조각 25개 떼기. */
class SingleStrokeMission : Mission(1, "손 떼지 않고 한 번에 25조각", 55) {
    private val target = 25
    private var strokeStartCount = 0
    private var currentStroke = -1
    private var best = 0

    override val progress: Float get() = min(1f, best.toFloat() / target)

    override fun onFrame(dt: Float, model: BreakModel, gesture: Gesture) {
        if (gesture.strokeId != currentStroke) {
            currentStroke = gesture.strokeId
            strokeStartCount = model.detachedCount
        }
        val inThisStroke = model.detachedCount - strokeStartCount
        if (inThisStroke > best) best = inThisStroke
        if (best >= target) clear()
    }
}

/** ③ 정밀 — 세게 누르지 않고 완파. 꾹 누르면 그 자리에서 실패한다. */
class PrecisionMission : Mission(2, "세게 누르지 않고 완파하기", 60) {
    private val maxForce = 2.2f
    override val progress: Float get() = last
    private var last = 0f

    override fun onFrame(dt: Float, model: BreakModel, gesture: Gesture) {
        if (gesture.touching && gesture.force > maxForce) { fail(); return }
        last = model.shellProgress
        if (model.shellProgress >= 0.999f) clear()
    }
}

/** ④ 사방 — 볼을 굴려가며 네 방향을 고루 까기. */
class QuadrantMission : Mission(3, "볼을 굴려 사방을 60%씩 까기", 50) {
    private val target = 0.6f
    override val progress: Float get() = last
    private var last = 0f

    override fun onFrame(dt: Float, model: BreakModel, gesture: Gesture) {
        val q = model.quadrantProgress()
        last = min(1f, (q.min() / target))
        if (q.all { it >= target }) clear()
    }
}

/** ⑤ 콤보 — 금이 끊기지 않게 12초 이어가기. */
class ComboMission : Mission(4, "끊기지 않게 12초 이어 깨기", 65) {
    private val holdTarget = 12f
    private val maxGap = 0.45f

    private var lastTransitions = -1
    private var gap = 0f
    private var combo = 0f

    override val progress: Float get() = min(1f, combo / holdTarget)

    override fun onFrame(dt: Float, model: BreakModel, gesture: Gesture) {
        if (lastTransitions < 0) lastTransitions = model.transitions

        if (model.transitions != lastTransitions) {
            lastTransitions = model.transitions
            gap = 0f
        } else {
            gap += dt
        }

        if (gap > maxGap) combo = 0f else combo += dt
        if (combo >= holdTarget) clear()
    }
}

/** ⑥ 껍질만 — 벗겨진 구멍을 세 번 넘게 건드리면 실패. */
class ShellOnlyMission : Mission(5, "구멍 건드리지 않고 껍질만 100%", 70) {
    private val allowance = 3
    override val progress: Float get() = last
    private var last = 0f

    override fun onFrame(dt: Float, model: BreakModel, gesture: Gesture) {
        if (gesture.coreTouches > allowance) { fail(); return }
        last = model.shellProgress
        if (model.shellProgress >= 0.999f) clear()
    }
}

object Missions {

    val factories: List<() -> Mission> = listOf(
        ::SpeedMission,
        ::SingleStrokeMission,
        ::PrecisionMission,
        ::QuadrantMission,
        ::ComboMission,
        ::ShellOnlyMission,
    )

    fun create(id: Int): Mission = factories[id.coerceIn(factories.indices)]()

    /**
     * 그날의 미션 3개. 날짜만으로 정해지므로 앱을 껐다 켜도 같은 조합이 나온다.
     * 서버도 난수 저장도 필요 없다.
     */
    fun dailyIdsFor(day: Long): List<Int> {
        val pool = factories.indices.toMutableList()
        var seed = day * 6364136223846793005L + 1442695040888963407L
        val picked = ArrayList<Int>(3)
        repeat(3) {
            seed = seed * 6364136223846793005L + 1442695040888963407L
            val index = ((seed ushr 33).toInt() and 0x7FFFFFFF) % pool.size
            picked.add(pool.removeAt(index))
        }
        return picked
    }

    fun dailyFor(day: Long): List<Mission> = dailyIdsFor(day).map { create(it) }
}
