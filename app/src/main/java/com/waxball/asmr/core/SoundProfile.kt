package com.waxball.asmr.core

/**
 * 재질 하나의 물성. 소리(주파수·감쇠·밀도)와 파괴 거동(단단함·금 전파)을 함께 기술한다.
 * 둘을 한 곳에 두는 이유는 실제로 같은 물성에서 나오는 결과이기 때문이다.
 * 잘 부서지는 재질은 소리도 잘게 나고 금도 잘 번진다.
 *
 * @param baseFreq 크랙 그레인의 중심 주파수(Hz)
 * @param freqSpread 중심 주파수 주변 산포(옥타브 배율). 클수록 음색이 넓게 흩어진다
 * @param q 밴드패스 첨예도. 클수록 유리처럼 뾰족하다
 * @param decayMsMin 그레인 최소 감쇠 시간(ms)
 * @param decayMsMax 그레인 최대 감쇠 시간(ms)
 * @param resonance 공명음을 섞는 비율 0~1. 클수록 "뽀각"에 가깝다
 * @param density 한 번의 파괴에서 터지는 그레인 수 배율
 * @param toughness 파괴 임계 배율. 클수록 잘 안 깨진다
 * @param propagation 전이 시 이웃 조각에 전달되는 압력 비율
 * @param brightness 고역 강조. 착지음·문지름 소리의 밝기에 쓴다
 */
data class SoundProfile(
    val baseFreq: Float,
    val freqSpread: Float,
    val q: Float,
    val decayMsMin: Float,
    val decayMsMax: Float,
    val resonance: Float,
    val density: Float,
    val toughness: Float,
    val propagation: Float,
    val brightness: Float,
) {
    companion object {

        /** 굳은 왁스. 기준이 되는 소리. 딱딱하고 금이 잘 번진다. */
        fun hardWax() = SoundProfile(
            baseFreq = 2600f, freqSpread = 0.8f, q = 6f,
            decayMsMin = 8f, decayMsMax = 26f, resonance = 0.35f,
            density = 1.0f, toughness = 1.0f, propagation = 0.45f, brightness = 1.0f,
        )

        /** 무른 왁스. 낮고 둔한 소리, 금이 잘 안 번진다. */
        fun softWax() = SoundProfile(
            baseFreq = 1300f, freqSpread = 0.7f, q = 4f,
            decayMsMin = 12f, decayMsMax = 40f, resonance = 0.25f,
            density = 0.7f, toughness = 0.75f, propagation = 0.22f, brightness = 0.7f,
        )

        /** 반짝이 섞인 왁스. 자잘하고 밝은 소리. */
        fun glitter() = SoundProfile(
            baseFreq = 3800f, freqSpread = 1.0f, q = 8f,
            decayMsMin = 5f, decayMsMax = 18f, resonance = 0.30f,
            density = 1.25f, toughness = 0.95f, propagation = 0.40f, brightness = 1.25f,
        )

        /** 알갱이가 박힌 껍질. 그레인이 가장 많아 "빠자자작"이 길게 이어진다. */
        fun crunchBeads() = SoundProfile(
            baseFreq = 1900f, freqSpread = 1.3f, q = 5f,
            decayMsMin = 6f, decayMsMax = 22f, resonance = 0.50f,
            density = 1.6f, toughness = 1.10f, propagation = 0.30f, brightness = 0.95f,
        )

        /** 설탕유리. 가장 높고 뾰족하며 금이 제일 잘 번진다. */
        fun sugarGlass() = SoundProfile(
            baseFreq = 5200f, freqSpread = 0.9f, q = 10f,
            decayMsMin = 4f, decayMsMax = 14f, resonance = 0.55f,
            density = 1.15f, toughness = 1.25f, propagation = 0.55f, brightness = 1.5f,
        )
    }
}
