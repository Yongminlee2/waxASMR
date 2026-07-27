package com.waxball.asmr.ar

import kotlin.math.cos
import kotlin.math.sin

/**
 * 손바닥 위에 공을 몇 개, 어떤 크기로, 어디에 놓을지 정한다.
 *
 * 액티비티 안에 두면 검증할 방법이 없어서 따로 뺐다.
 * 안드로이드 API를 쓰지 않으므로 PC에서 그대로 확인한다.
 */
object ArLayout {

    /** 손바닥 위에 한꺼번에 올릴 수 있는 개수. 더 늘리면 하나하나가 너무 작아진다. */
    const val MAX_BALLS = 3

    /** 공 하나일 때 손 너비 대비 지름 비율. 1을 넘으면 손 밖으로 삐져나온다. */
    private const val BALL_TO_HAND = 0.9f

    /** 개수가 늘수록 하나하나를 줄이는 정도. */
    private const val SHRINK_PER_EXTRA = 0.55f

    /** 손 너비 대비 흩어 놓는 반경. */
    private const val SPREAD_TO_HAND = 0.55f

    /**
     * 공 [count] 개를 올릴 때 하나의 반지름(픽셀).
     * 개수가 늘면 하나하나가 작아져야 손바닥을 넘치지 않는다.
     */
    fun radiusPx(handSpanPx: Float, count: Int): Float {
        val n = count.coerceIn(1, MAX_BALLS)
        return handSpanPx * BALL_TO_HAND / (1f + SHRINK_PER_EXTRA * (n - 1))
    }

    /**
     * [index] 번째 공을 손바닥 중심에서 얼마나 밀어 놓을지. 결과는 [out]에 x, y.
     *
     * 전부 같은 자리에 두면 하나로 겹쳐 보인다. 손 크기에 비례해 벌려야
     * 손이 멀어지거나 가까워져도 배치가 유지된다.
     * 세로로 덜 벌리는 것은 손바닥이 세로로 좁기 때문이다.
     */
    fun offsetPx(index: Int, count: Int, handSpanPx: Float, out: FloatArray) {
        if (count <= 1) {
            out[0] = 0f
            out[1] = 0f
            return
        }
        val n = count.coerceIn(1, MAX_BALLS)
        val spread = handSpanPx * SPREAD_TO_HAND
        val angle = (index.toFloat() / n) * TWO_PI - HALF_PI
        out[0] = cos(angle) * spread
        out[1] = sin(angle) * spread * 0.6f
    }

    /** 1 → 2 → 3 → 1 로 돌린다. */
    fun nextCount(current: Int): Int = if (current >= MAX_BALLS) 1 else current + 1

    /**
     * 공이 많아지면 조각을 줄인다.
     * 손바닥 위라 하나가 작게 보이므로 조각을 줄여도 티가 안 나고 프레임이 버틴다.
     */
    fun qualityFor(count: Int): Int = if (count >= 3) 0 else 1

    private const val TWO_PI = (2.0 * Math.PI).toFloat()
    private const val HALF_PI = (Math.PI / 2.0).toFloat()
}
