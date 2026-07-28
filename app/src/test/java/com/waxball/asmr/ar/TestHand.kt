package com.waxball.asmr.ar

import kotlin.math.cos
import kotlin.math.sin

/**
 * 테스트용 손 좌표.
 *
 * 손목이 아래, 손가락이 위인 손을 만든다. [curl] 0이면 편 손, 1이면 주먹.
 * [rollDeg] 는 손바닥을 화면 안에서 돌린 각도다.
 */
object TestHand {

    fun of(
        curl: Float = 0f,
        rollDeg: Float = 0f,
        centerX: Float = 0.5f,
        centerY: Float = 0.5f,
        scale: Float = 0.2f,
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
        put(HandLandmarks.INDEX_TIP, -0.45f, -reach)
        put(HandLandmarks.MIDDLE_TIP, -0.15f, -reach * 1.05f)
        put(HandLandmarks.RING_TIP, 0.15f, -reach)
        put(HandLandmarks.PINKY_TIP, 0.45f, -reach * 0.9f)

        return HandLandmarks(x, y)
    }

    /** 평활이 수렴할 때까지 같은 손을 계속 넣는다. */
    fun settle(pose: PalmPose, hand: HandLandmarks?, frames: Int = 60) {
        repeat(frames) { pose.update(hand, 1f / 60f) }
    }
}
