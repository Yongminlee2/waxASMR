package com.waxball.asmr.ar

import kotlin.math.cos
import kotlin.math.sin

/**
 * 테스트용 손 좌표.
 *
 * 손목이 아래, 손가락이 위인 손을 만든다. [curl] 0이면 편 손, 1이면 주먹.
 * [pinch] 0이면 엄지가 벌어져 있고, 1이면 엄지 끝이 검지 끝에 붙는다.
 * [rollDeg] 는 손바닥을 화면 안에서 돌린 각도다.
 *
 * [indexTipSweep] 은 검지 끝이 손목에서의 거리를 유지한 채 쓸고 지나가는 각도(라디안)다.
 * 손끝을 가로로 평행이동시키면 손목과의 거리가 변해서 "손가락을 굽혔다"로 읽힌다.
 * 긁는 동작은 굽히는 게 아니라 표면을 쓸고 지나가는 것이므로 회전으로 만든다.
 */
object TestHand {

    fun of(
        curl: Float = 0f,
        pinch: Float = 0f,
        rollDeg: Float = 0f,
        centerX: Float = 0.5f,
        centerY: Float = 0.5f,
        scale: Float = 0.2f,
        indexTipSweep: Float = 0f,
    ): HandLandmarks {
        val x = FloatArray(HandLandmarks.COUNT)
        val y = FloatArray(HandLandmarks.COUNT)
        val rad = Math.toRadians(rollDeg.toDouble()).toFloat()
        val c = cos(rad)
        val s = sin(rad)

        fun put(i: Int, dx: Float, dy: Float) {
            x[i] = centerX + (dx * c - dy * s) * scale
            y[i] = centerY + (dx * s + dy * c) * scale
        }

        put(HandLandmarks.WRIST, 0f, 0.6f)
        put(HandLandmarks.INDEX_MCP, -0.45f, 0f)
        put(HandLandmarks.MIDDLE_MCP, -0.15f, 0f)
        put(HandLandmarks.RING_MCP, 0.15f, 0f)
        put(HandLandmarks.PINKY_MCP, 0.45f, 0f)

        val reach = 1.1f - 0.95f * curl

        // 검지 끝은 손목을 중심으로 돌린다. 그래야 쓸고 지나가도 굽힘이 안 변한다.
        val wristY = 0.6f
        val armX = -0.45f
        val armY = -reach - wristY
        val sweepC = cos(indexTipSweep)
        val sweepS = sin(indexTipSweep)
        val indexTipX = armX * sweepC - armY * sweepS
        val indexTipY = wristY + armX * sweepS + armY * sweepC

        put(HandLandmarks.INDEX_TIP, indexTipX, indexTipY)
        put(HandLandmarks.MIDDLE_TIP, -0.15f, -reach * 1.05f)
        put(HandLandmarks.RING_TIP, 0.15f, -reach)
        put(HandLandmarks.PINKY_TIP, 0.45f, -reach * 0.9f)

        // 엄지는 검지 바깥에서 출발해 집을수록 검지 끝으로 붙는다.
        val restX = -1.1f
        val restY = 0.1f
        put(
            HandLandmarks.THUMB_TIP,
            restX + (indexTipX - restX) * pinch,
            restY + (indexTipY - restY) * pinch,
        )
        return HandLandmarks(x, y)
    }

    /** 평활이 수렴할 때까지 같은 손을 계속 넣는다. */
    fun settle(pose: PalmPose, hand: HandLandmarks?, frames: Int = 60) {
        repeat(frames) { pose.update(hand, 1f / 60f) }
    }
}
