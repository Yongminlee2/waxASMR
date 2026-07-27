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
 * @param body 으스러질 때 깔리는 저역 몸통의 양 0~1. 이게 없으면 파쇄음이 아니라
 *   쉬익거리는 잡음으로 들린다
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
    val body: Float = 0.5f,
    /** 파열 간격 배율. 크면 하나하나 굵게 터지고, 작으면 촘촘히 뭉쳐서 터진다. */
    val gapScale: Float = 1f,
) {
    companion object {

        /*
         * 아래 수치는 실제 왁뿌볼 녹음 세 개를 분석해 잡았다(2026-07-26).
         * 종류가 서로 달라서 재질별 성격을 추측이 아니라 실측으로 나눌 수 있었다.
         *
         *              파열 간격   중심     감쇠    저역/중역/고역   평탄도
         *   A(기준)     105ms    3497Hz    80ms    17/44/39%      0.28
         *   왁뿌볼2     208ms    3487Hz    56ms    21/41/38%      0.18  성기고 음정감 강함
         *   왁뿌볼3      99ms    6253Hz   216ms     6/20/74%      0.31  압도적으로 밝음
         *
         * 처음 감으로 잡았던 값은 크게 틀렸다. 산포가 2.4배 좁았고, 공명이 절반이라
         * 잡음처럼 들렸고, 파열이 열 배 넘게 촘촘했고, 저역이 아예 없었다.
         *
         * 합성 결과를 같은 분석기로 되재서 맞췄다. 설정값과 측정값 사이에는 일정한 배율이
         * 있어서(중심 ×1.17, 간격 ×11.7, 감쇠 ×3.5, 산포 ×0.84) 목표 수치를 그 배율로
         * 나눠 설정한다. 이 배율은 합성 구조에서 나오는 것이라 임의로 바꾸면 안 맞는다.
         */

        /**
         * 굳은 왁스. 기본 볼(노른자)이 쓴다.
         *
         * 녹음의 스펙트럼 중심이 3107Hz, 산포 1.79옥타브였다. 파편을 고르는 기준을
         * 여기에 맞춰야 녹음에 실제로 들어 있는 소리가 나온다. 임의로 다른 값을 주면
         * 뱅크의 한쪽 끝에 있는 파편만 골라서 녹음과 다른 소리가 된다.
         */
        fun hardWax() = SoundProfile(
            baseFreq = 3107f, freqSpread = 1.79f, q = 8.5f,
            decayMsMin = 12f, decayMsMax = 36f, resonance = 0.70f,
            density = 0.35f, toughness = 1.0f, propagation = 0.45f, brightness = 1.0f,
            body = 0.45f, gapScale = 9.0f,
        )

        /**
         * 무른 왁스. 기본 볼(찹쌀떡)이 쓴다.
         * 녹음 범위 안에서 조금 낮은 쪽 파편을 고르되, 벗어나지는 않는다.
         */
        fun softWax() = SoundProfile(
            baseFreq = 2600f, freqSpread = 1.70f, q = 5.7f,
            decayMsMin = 8f, decayMsMax = 24f, resonance = 0.85f,
            density = 0.25f, toughness = 0.75f, propagation = 0.22f, brightness = 0.7f,
            body = 0.50f, gapScale = 17.8f,
        )

        /** 반짝이 섞인 왁스. 왁뿌볼3 계열의 밝고 사각거리는 소리. */
        fun glitter() = SoundProfile(
            baseFreq = 3600f, freqSpread = 1.50f, q = 11f,
            decayMsMin = 40f, decayMsMax = 110f, resonance = 0.62f,
            density = 0.44f, toughness = 0.95f, propagation = 0.40f, brightness = 1.25f,
            body = 0.36f, gapScale = 6f,
        )

        /** 알갱이가 박힌 껍질. 다섯 중 파열이 가장 촘촘하다. */
        fun crunchBeads() = SoundProfile(
            baseFreq = 2400f, freqSpread = 2.50f, q = 7f,
            decayMsMin = 13f, decayMsMax = 39f, resonance = 0.80f,
            density = 0.56f, toughness = 1.10f, propagation = 0.30f, brightness = 0.95f,
            body = 0.55f, gapScale = 5.1f,
        )

        /** 설탕유리. 왁뿌볼3의 밝은 쪽 극단. 여운이 가장 길다. */
        fun sugarGlass() = SoundProfile(
            baseFreq = 4300f, freqSpread = 1.50f, q = 12f,
            decayMsMin = 55f, decayMsMax = 150f, resonance = 0.62f,
            density = 0.40f, toughness = 1.25f, propagation = 0.55f, brightness = 1.5f,
            body = 0.50f, gapScale = 6f,
        )
    }
}
