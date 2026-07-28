package com.waxball.asmr.ar

import kotlin.math.atan2
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

    /**
     * 손바닥 롤(라디안). 검지뿌리 → 새끼뿌리 벡터의 화면 각도다.
     *
     * 2D 관절만으로 안정적으로 나오는 유일한 자세 신호다. 앞뒤 기울기는
     * 손 길이 대 폭 비율로 추정할 수 있지만 손가락을 굽히기만 해도 값이 흔들린다.
     */
    var roll = 0f
        private set

    /**
     * 엄지 끝과 검지 끝 사이 거리 ÷ 손 폭. 집으면 작아진다.
     * 비율이라 손이 카메라에서 멀어져도 값이 유지된다.
     */
    var pinchRatio = 1f
        private set

    /** 손끝 화면 좌표. 만진 자리를 정할 때 쓴다. */
    var indexTipX = 0f
        private set
    var indexTipY = 0f
        private set
    var thumbTipX = 0f
        private set
    var thumbTipY = 0f
        private set

    /**
     * 검지 끝이 손바닥 기준으로 움직이는 속도. 손 폭을 1로 본 초당 배율.
     *
     * 손 전체가 움직이는 것은 긁는 게 아니다. 그래서 절대 좌표가 아니라
     * 손바닥 중심에서 본 상대 위치의 변화로 잰다.
     */
    var tipSpeed = 0f
        private set

    private var started = false
    private var previousSqueeze = 0f

    // 각도를 직접 평활하면 ±π 경계에서 한 바퀴 튄다. 벡터로 평활한 뒤 각도를 낸다.
    private var rollX = 1f
    private var rollY = 0f

    private var previousRelX = 0f
    private var previousRelY = 0f

    fun reset() {
        hasHand = false
        centerX = 0f; centerY = 0f
        span = 0f; squeeze = 0f; force = 0f
        started = false
        previousSqueeze = 0f
        roll = 0f
        pinchRatio = 1f
        indexTipX = 0f; indexTipY = 0f
        thumbTipX = 0f; thumbTipY = 0f
        tipSpeed = 0f
        rollX = 1f; rollY = 0f
        previousRelX = 0f; previousRelY = 0f
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
            tipSpeed = 0f
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
        val rawRollX = hand.x[HandLandmarks.PINKY_MCP] - hand.x[HandLandmarks.INDEX_MCP]
        val rawRollY = hand.y[HandLandmarks.PINKY_MCP] - hand.y[HandLandmarks.INDEX_MCP]

        if (!started) {
            started = true
            centerX = rawCenterX; centerY = rawCenterY
            span = rawSpan; squeeze = rawSqueeze
            previousSqueeze = rawSqueeze
            force = 0f

            rollX = rawRollX; rollY = rawRollY
            roll = angleOf(rollX, rollY)
            readTips(hand)
            val firstWidth = span.coerceAtLeast(1e-4f)
            pinchRatio = distance(hand, HandLandmarks.THUMB_TIP, HandLandmarks.INDEX_TIP) / firstWidth
            // 첫 프레임에 손끝 속도가 폭발하면 대뜸 긁는 것으로 잡힌다.
            previousRelX = (indexTipX - rawCenterX) / firstWidth
            previousRelY = (indexTipY - rawCenterY) / firstWidth
            tipSpeed = 0f
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

        rollX += (rawRollX - rollX) * SMOOTH
        rollY += (rawRollY - rollY) * SMOOTH
        roll = angleOf(rollX, rollY)

        readTips(hand)

        val width = span.coerceAtLeast(1e-4f)
        pinchRatio = distance(hand, HandLandmarks.THUMB_TIP, HandLandmarks.INDEX_TIP) / width

        // 상대 위치는 평활 전 중심으로 잰다. 평활된 중심은 손을 따라오는 데 몇 프레임
        // 걸리는데, 그 지연이 그대로 "손끝이 손바닥 위를 미끄러졌다"로 잡혀서
        // 손을 통째로 옮기기만 해도 긁는 것으로 판정된다.
        val relX = (indexTipX - rawCenterX) / width
        val relY = (indexTipY - rawCenterY) / width
        val moveX = relX - previousRelX
        val moveY = relY - previousRelY
        val rawSpeed = if (dt > 1e-4f) sqrt(moveX * moveX + moveY * moveY) / dt else 0f
        tipSpeed += (rawSpeed - tipSpeed) * SMOOTH
        previousRelX = relX
        previousRelY = relY
    }

    private fun readTips(hand: HandLandmarks) {
        indexTipX = hand.x[HandLandmarks.INDEX_TIP]
        indexTipY = hand.y[HandLandmarks.INDEX_TIP]
        thumbTipX = hand.x[HandLandmarks.THUMB_TIP]
        thumbTipY = hand.y[HandLandmarks.THUMB_TIP]
    }

    /** 길이가 0인 벡터에서 atan2를 부르면 방향이 제멋대로 나온다. */
    private fun angleOf(x: Float, y: Float): Float =
        if (x * x + y * y < 1e-10f) 0f else atan2(y, x)

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
         *
         * 처음에 2.5로 뒀더니 한 번 쥐는 것만으로 볼이 사라졌다. 쥐는 힘이 구 전체에
         * 걸리도록 바꾸면서 실제로 들어가는 손상이 배로 늘어난 탓이다.
         * 여러 번 쥐어야 다 부서지도록 낮춘다.
         */
        const val FORCE_GAIN = 0.9f
    }
}
