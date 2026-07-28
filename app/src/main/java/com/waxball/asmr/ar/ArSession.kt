package com.waxball.asmr.ar

/**
 * 손바닥 모드의 진행 상태.
 *
 * 손바닥 모드에는 할 일이 없었다. 그렇다고 미션을 통째로 들여오면 판정·보상·UI가
 * 줄줄이 딸려 오는데, ASMR을 하러 온 화면에 그만한 것이 필요하지는 않다.
 * 볼 하나를 얼마 만에 깠는지와 몇 개를 연달아 깼는지, 둘이면 충분하다.
 */
class ArSession(private val comboWindowMs: Long = COMBO_WINDOW_MS) {

    var combo = 0
        private set
    var bestCombo = 0
        private set

    /** 지금 볼을 깔기 시작한 뒤 흐른 시간(초). */
    var elapsedSec = 0f
        private set

    /** 볼 하나를 다 까는 데 걸린 가장 짧은 시간(초). 아직 없으면 0. */
    var bestClearSec = 0f
        private set

    private var lastBreakAt = 0L

    fun onNewBall(nowMs: Long) {
        elapsedSec = 0f
        combo = 0
        lastBreakAt = nowMs
    }

    fun onBreak(nowMs: Long) {
        if (combo > 0 && nowMs - lastBreakAt > comboWindowMs) combo = 0
        combo++
        if (combo > bestCombo) bestCombo = combo
        lastBreakAt = nowMs
    }

    /** 매 프레임 부른다. 시계를 돌리고 끊긴 콤보를 정리한다. */
    fun tick(dt: Float, nowMs: Long) {
        elapsedSec += dt
        if (combo > 0 && nowMs - lastBreakAt > comboWindowMs) combo = 0
    }

    /**
     * 볼 하나를 다 깠다.
     * @return 최고 기록을 갱신했으면 true
     */
    fun onCleared(nowMs: Long): Boolean {
        val taken = elapsedSec
        combo = 0
        lastBreakAt = nowMs
        if (taken <= 0f) return false
        if (bestClearSec <= 0f || taken < bestClearSec) {
            bestClearSec = taken
            return true
        }
        return false
    }

    private companion object {
        /** 이 안에 다음 파괴가 이어지면 콤보가 유지된다. */
        const val COMBO_WINDOW_MS = 1200L
    }
}
