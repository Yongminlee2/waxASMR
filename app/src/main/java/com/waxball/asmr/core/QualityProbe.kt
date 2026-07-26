package com.waxball.asmr.core

/**
 * 기기가 얼마나 빠른지 실제 프레임 시간으로 판정한다.
 *
 * 기기명이나 API 레벨로 성능을 짐작하면 틀린다. 같은 칩이라도 발열 상태에 따라 다르다.
 * 처음 몇 초를 실제로 그려 보고 정하는 편이 정확하다.
 *
 * 평균이 아니라 중앙값을 쓴다. 첫 프레임 몇 개는 셰이더 컴파일 때문에 항상 느려서
 * 평균을 쓰면 멀쩡한 기기도 저사양으로 판정된다.
 */
class QualityProbe(private val sampleTarget: Int = 240) {

    private val samples = FloatArray(sampleTarget)
    private var count = 0

    val ready: Boolean get() = count >= sampleTarget

    fun feed(frameTimeMs: Float) {
        if (count >= sampleTarget) return
        // 앱 전환 같은 튀는 값은 버린다. 성능 판정과 무관하다.
        if (frameTimeMs <= 0f || frameTimeMs > 500f) return
        samples[count++] = frameTimeMs
    }

    fun reset() {
        count = 0
    }

    /** 0=낮음 1=보통 2=높음. 판정이 끝나기 전에는 보통으로 둔다. */
    val tier: Int
        get() {
            if (count < WARMUP) return 1
            val median = medianOfCollected()
            return when {
                median > 22f -> 0
                median > 15f -> 1
                else -> 2
            }
        }

    fun medianOfCollected(): Float {
        if (count == 0) return 0f
        val copy = samples.copyOf(count)
        copy.sort()
        return if (count % 2 == 1) copy[count / 2] else (copy[count / 2 - 1] + copy[count / 2]) * 0.5f
    }

    private companion object {
        /** 셰이더 컴파일로 느린 초반 프레임을 지나야 판정이 의미 있다. */
        const val WARMUP = 60
    }
}
