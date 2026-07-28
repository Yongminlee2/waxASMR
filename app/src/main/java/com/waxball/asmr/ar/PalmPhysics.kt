package com.waxball.asmr.ar

import kotlin.math.exp
import kotlin.math.sin

/**
 * 손바닥 기울기를 볼의 구름과 부스러기 흐름으로 바꾼다.
 *
 * 지금까지 볼은 손 위 화면 좌표만 따라다녔다. 손을 아무리 기울여도 볼이 가만히
 * 있으니 손 위에 "놓여" 있는 느낌이 안 났다.
 */
class PalmPhysics {

    /** 볼이 굴러간 각도(라디안). 눕혀 두면 계속 쌓인다. */
    var spin = 0f
        private set

    /** 손바닥 중심에서 밀려난 거리. 손 폭을 1로 본 비율. 음수면 왼쪽. */
    var slideX = 0f
        private set

    /**
     * @param roll 손바닥 롤(라디안). [PalmPose.roll]
     * @param hasHand 손을 놓쳤으면 false. 그대로 멈춘다
     */
    fun update(roll: Float, hasHand: Boolean, dt: Float) {
        if (!hasHand || dt <= 0f) return

        // 손바닥 축이 수평에서 벗어난 정도. 수평이면 0, 세우면 ±1.
        val tilt = sin(roll)

        spin += tilt * ROLL_TO_SPIN * dt

        // 목표 위치로 서서히 다가간다. 바로 옮기면 인식 떨림이 그대로 튄다.
        val target = tilt * MAX_SLIDE
        slideX += (target - slideX) * (1f - exp(-SLIDE_RATE * dt))
    }

    fun reset() {
        spin = 0f
        slideX = 0f
    }

    companion object {
        /** 손바닥 밖으로 나가지 않을 만큼만 민다. */
        const val MAX_SLIDE = 0.28f

        /** 완전히 눕혔을 때 초당 구르는 각도(라디안). */
        private const val ROLL_TO_SPIN = 2.2f

        /** 밀려나는 속도. 클수록 즉각적이지만 떨림도 그대로 따라온다. */
        private const val SLIDE_RATE = 4f
    }
}
