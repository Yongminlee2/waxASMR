package com.waxball.asmr.ui

import kotlin.math.abs
import kotlin.math.sqrt

/** MotionEvent에 기대지 않는 터치 종류. 순수 코틀린으로 두어야 PC에서 검증할 수 있다. */
object TouchAction {
    const val DOWN = 0
    const val MOVE = 1
    const val UP = 2
    const val POINTER_DOWN = 3
    const val POINTER_UP = 4
    const val CANCEL = 5
}

/**
 * 터치를 "깨기 / 굴리기 / 확대"로 가른다.
 *
 * 이런 앱의 고질병이 깨기와 굴리기가 서로 먹는 것이다.
 * 여기서는 볼 안쪽을 한 손가락으로 만지면 깨기, 볼 바깥 여백을 끌면 굴리기,
 * 두 손가락은 어디서든 굴리기·확대로 나눠서 해결한다.
 *
 * 누르고 있으면 힘이 쌓이고, 빠르게 문지르면 힘 대신 속도가 올라간다.
 * 실제로 왁스를 깰 때도 꾹 누르는 것과 쓸어내리는 것은 다른 소리가 난다.
 */
class InputRouter(private val listener: Listener) {

    interface Listener {
        fun onBreak(x: Float, y: Float, force: Float, speed: Float, dt: Float)
        fun onOrbit(dx: Float, dy: Float)
        fun onZoom(scale: Float)
        fun onRelease()
    }

    /** 화면 좌표계에서의 볼 중심과 반지름. 렌더러가 매 프레임 갱신한다. */
    var ballCenterX = 0f
    var ballCenterY = 0f
    var ballRadius = 1f

    /** 굴리기 잠금. 켜면 볼 밖을 끌어도 회전하지 않는다. */
    var orbitLocked = false

    private var mode = MODE_NONE
    private var lastX = 0f
    private var lastY = 0f
    private var lastTimeNs = 0L
    private var holdSeconds = 0f
    private var pinchStart = 0f

    /** 손을 떼지 않고 이어지는 한 번의 문지름. 미션 판정에 쓴다. */
    var strokeId = 0
        private set

    fun onTouch(action: Int, pointerCount: Int, xs: FloatArray, ys: FloatArray, timeNs: Long) {
        when (action) {
            TouchAction.DOWN -> begin(xs[0], ys[0], timeNs)
            TouchAction.POINTER_DOWN -> beginPinch(pointerCount, xs, ys)
            TouchAction.MOVE -> move(pointerCount, xs, ys, timeNs)
            TouchAction.POINTER_UP -> if (pointerCount <= 2) endPinch(xs, ys, timeNs)
            TouchAction.UP, TouchAction.CANCEL -> end()
        }
    }

    private fun begin(x: Float, y: Float, timeNs: Long) {
        strokeId++
        lastX = x; lastY = y; lastTimeNs = timeNs; holdSeconds = 0f
        mode = if (isInsideBall(x, y)) MODE_BREAK else MODE_ORBIT
        if (mode == MODE_BREAK) listener.onBreak(x, y, INITIAL_FORCE, 0f, 0f)
    }

    private fun beginPinch(pointerCount: Int, xs: FloatArray, ys: FloatArray) {
        if (pointerCount < 2) return
        mode = MODE_ORBIT
        pinchStart = distance(xs, ys)
    }

    private fun endPinch(xs: FloatArray, ys: FloatArray, timeNs: Long) {
        // 두 번째 손가락을 떼면 남은 손가락으로 굴리기를 이어간다. 깨기로 돌아가지 않는다.
        mode = MODE_ORBIT
        lastX = xs[0]; lastY = ys[0]; lastTimeNs = timeNs
    }

    private fun move(pointerCount: Int, xs: FloatArray, ys: FloatArray, timeNs: Long) {
        if (mode == MODE_NONE) return

        val dt = ((timeNs - lastTimeNs).coerceAtLeast(0L)) / 1_000_000_000f
        val dx = xs[0] - lastX
        val dy = ys[0] - lastY

        if (pointerCount >= 2) {
            val d = distance(xs, ys)
            if (pinchStart > 1f) listener.onZoom(d / pinchStart)
            pinchStart = d
            if (!orbitLocked) listener.onOrbit(dx * ORBIT_GAIN, dy * ORBIT_GAIN)
            lastX = xs[0]; lastY = ys[0]; lastTimeNs = timeNs
            return
        }

        when (mode) {
            MODE_ORBIT -> if (!orbitLocked) listener.onOrbit(dx * ORBIT_GAIN, dy * ORBIT_GAIN)

            MODE_BREAK -> {
                val moved = sqrt(dx * dx + dy * dy)
                val speed = if (dt > 1e-4f) (moved / dt) / ballRadius else 0f

                // 가만히 누르고 있으면 힘이 쌓이고, 움직이면 쌓인 힘이 풀린다.
                if (speed < STILL_SPEED) holdSeconds += dt else holdSeconds *= 0.4f

                val force = (INITIAL_FORCE + holdSeconds * FORCE_RAMP).coerceAtMost(MAX_FORCE)
                listener.onBreak(xs[0], ys[0], force, speed.coerceAtMost(MAX_SPEED), dt)
            }
        }

        lastX = xs[0]; lastY = ys[0]; lastTimeNs = timeNs
    }

    private fun end() {
        if (mode != MODE_NONE) listener.onRelease()
        mode = MODE_NONE
        holdSeconds = 0f
        pinchStart = 0f
    }

    private fun isInsideBall(x: Float, y: Float): Boolean {
        if (orbitLocked) return true   // 굴리기를 잠그면 화면 전체가 깨기 영역이 된다
        val dx = x - ballCenterX
        val dy = y - ballCenterY
        return dx * dx + dy * dy <= ballRadius * ballRadius
    }

    private fun distance(xs: FloatArray, ys: FloatArray): Float {
        val dx = xs[1] - xs[0]
        val dy = ys[1] - ys[0]
        return sqrt(dx * dx + dy * dy)
    }

    /** 테스트와 디버그용. 지금 무슨 동작으로 해석하고 있는지. */
    val currentMode: Int get() = mode

    companion object {
        const val MODE_NONE = 0
        const val MODE_BREAK = 1
        const val MODE_ORBIT = 2

        private const val INITIAL_FORCE = 0.9f
        private const val FORCE_RAMP = 2.6f
        private const val MAX_FORCE = 4.5f
        private const val MAX_SPEED = 6f
        private const val STILL_SPEED = 0.35f
        private const val ORBIT_GAIN = 0.006f
    }
}
