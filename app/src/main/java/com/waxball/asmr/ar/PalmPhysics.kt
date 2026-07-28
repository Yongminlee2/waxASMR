package com.waxball.asmr.ar

import kotlin.math.exp
import kotlin.math.sin

/**
 * 손바닥 기울기를 볼이 밀려나는 거리로 바꾼다.
 *
 * 볼이 손 위 화면 좌표만 따라다니면 손을 아무리 기울여도 가만히 있어서
 * 손 위에 "놓여" 있는 느낌이 안 난다.
 *
 * 한때 여기서 볼을 굴리기도 했다. 그런데 볼이 돌면 [BreakModel]에 넘기는 접촉 방향이
 * 모델 좌표계 기준이라 "앞쪽이 조금 더 세게 눌린다"는 기울기가 같이 돌아가고,
 * 쥘 때마다 깨지는 자리가 옮겨 다녔다. 쥐는 맛이 달라져서 뺐다.
 * 밀려나는 것은 배치만 바꾸므로 파괴에 영향이 없다.
 */
class PalmPhysics {

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

        // 목표 위치로 서서히 다가간다. 바로 옮기면 인식 떨림이 그대로 튄다.
        val target = tilt * MAX_SLIDE
        slideX += (target - slideX) * (1f - exp(-SLIDE_RATE * dt))
    }

    fun reset() {
        slideX = 0f
    }

    companion object {
        /** 손바닥 밖으로 나가지 않을 만큼만 민다. */
        const val MAX_SLIDE = 0.28f

        /** 밀려나는 속도. 클수록 즉각적이지만 떨림도 그대로 따라온다. */
        private const val SLIDE_RATE = 4f
    }
}
