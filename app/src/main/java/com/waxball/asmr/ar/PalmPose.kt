package com.waxball.asmr.ar

import kotlin.math.sqrt

/**
 * 손 관절을 볼의 위치·크기·쥐는 힘으로 바꾼다. 이 모드의 핵심이다.
 *
 * 안드로이드 API를 쓰지 않아 카메라 없이 PC에서 전부 검증한다.
 * 인식기는 좌표만 넘겨주고, 여기서 나온 값이 그대로 기존 파괴 모델로 들어간다.
 */
class PalmPose {

    var hasHand = false
        private set

    /** 볼을 놓을 자리. 정규화 화면 좌표. */
    var centerX = 0f
        private set
    var centerY = 0f
        private set

    /** 손 너비. 볼 크기를 여기에 맞춘다. */
    var span = 0f
        private set

    /** 쥔 정도. 편 손 0, 주먹 1. */
    var squeeze = 0f
        private set

    /** 이번 프레임에 들어갈 힘. 쥐는 세기가 늘어나는 동안에만 0보다 크다. */
    var force = 0f
        private set

    private var started = false
    private var previousSqueeze = 0f

    fun reset() {
        hasHand = false
        centerX = 0f; centerY = 0f
        span = 0f; squeeze = 0f; force = 0f
        started = false
        previousSqueeze = 0f
    }

    /**
     * @param hand 이번 프레임에 잡힌 손. 못 잡았으면 null
     * @param dt 지난 프레임과의 간격(초)
     */
    fun update(hand: HandLandmarks?, dt: Float) {
        if (hand == null) {
            // 볼을 마지막 자리에 그대로 두고 힘만 끊는다.
            // 손을 잠깐 놓쳤다고 볼이 사라지거나 튀면 거슬린다.
            hasHand = false
            force = 0f
            previousSqueeze = squeeze
            return
        }

        hasHand = true

        val rawCenterX = (hand.x[HandLandmarks.WRIST] +
            hand.x[HandLandmarks.INDEX_MCP] +
            hand.x[HandLandmarks.PINKY_MCP]) / 3f
        val rawCenterY = (hand.y[HandLandmarks.WRIST] +
            hand.y[HandLandmarks.INDEX_MCP] +
            hand.y[HandLandmarks.PINKY_MCP]) / 3f

        val rawSpan = distance(hand, HandLandmarks.INDEX_MCP, HandLandmarks.PINKY_MCP)
        val rawSqueeze = curlOf(hand)

        if (!started) {
            started = true
            centerX = rawCenterX; centerY = rawCenterY
            span = rawSpan; squeeze = rawSqueeze
            previousSqueeze = rawSqueeze
            force = 0f
            return
        }

        // 인식은 프레임마다 미세하게 떨린다. 평활이 없으면 볼이 계속 지글거리고,
        // 쥐는 세기가 튀어서 쥐지도 않았는데 부서진다.
        centerX += (rawCenterX - centerX) * SMOOTH
        centerY += (rawCenterY - centerY) * SMOOTH
        span += (rawSpan - span) * SMOOTH
        squeeze += (rawSqueeze - squeeze) * SMOOTH

        // 쥐는 세기가 늘어나는 동안에만 힘을 준다.
        // 쥔 채 가만히 있어도 계속 부서지면 조작하는 느낌이 사라진다.
        val delta = squeeze - previousSqueeze
        force = if (delta > 0f && dt > 1e-4f) {
            (delta / dt) * sqrt(squeeze.coerceAtLeast(0f)) * FORCE_GAIN
        } else {
            0f
        }
        previousSqueeze = squeeze
    }

    /**
     * 손가락 네 개의 굽힘 평균.
     *
     * 손목에서 손가락 끝까지의 거리를 손목에서 뿌리까지의 거리로 나눈다.
     * 비율로 재기 때문에 손이 카메라에서 멀어져도 값이 흔들리지 않는다.
     * 각도로 재면 손을 기울일 때마다 값이 튄다.
     */
    private fun curlOf(hand: HandLandmarks): Float {
        var sum = 0f
        var counted = 0

        for (i in FINGER_MCP.indices) {
            val toKnuckle = distance(hand, HandLandmarks.WRIST, FINGER_MCP[i])
            if (toKnuckle < 1e-5f) continue

            val ratio = distance(hand, HandLandmarks.WRIST, FINGER_TIP[i]) / toKnuckle
            sum += ((OPEN_RATIO - ratio) / (OPEN_RATIO - FIST_RATIO)).coerceIn(0f, 1f)
            counted++
        }

        return if (counted == 0) 0f else sum / counted
    }

    private fun distance(hand: HandLandmarks, a: Int, b: Int): Float {
        val dx = hand.x[a] - hand.x[b]
        val dy = hand.y[a] - hand.y[b]
        return sqrt(dx * dx + dy * dy)
    }

    private companion object {
        val FINGER_MCP = intArrayOf(
            HandLandmarks.INDEX_MCP,
            HandLandmarks.MIDDLE_MCP,
            HandLandmarks.RING_MCP,
            HandLandmarks.PINKY_MCP,
        )
        val FINGER_TIP = intArrayOf(
            HandLandmarks.INDEX_TIP,
            HandLandmarks.MIDDLE_TIP,
            HandLandmarks.RING_TIP,
            HandLandmarks.PINKY_TIP,
        )

        /** 편 손과 주먹일 때의 손목-끝 / 손목-뿌리 비율. */
        const val OPEN_RATIO = 2.1f
        const val FIST_RATIO = 1.05f

        const val SMOOTH = 0.35f

        /**
         * dt로 나눈 뒤 곱하는 값이라 프레임 속도가 달라져도 손맛이 같다.
         *
         * BreakModel이 기대하는 힘은 1~4다. 조각에 금이 가는 임계가 0.22라
         * 0.2초쯤 눌러서 넘기려면 힘이 1은 돼야 한다.
         * 이 배율은 손을 한 번 빠르게 쥐었을 때 힘이 3 안팎으로 나오도록 맞춘 값이다.
         */
        const val FORCE_GAIN = 2.5f
    }
}
