package com.waxball.asmr.core

/**
 * 뿌시는 도구.
 *
 * 손가락 하나로만 문지르면 어떻게 만져도 똑같이 깨져서 금방 지루해진다.
 * 도구마다 닿는 넓이와 힘이 달라지면, 같은 볼도 어떤 걸 쥐느냐에 따라
 * 깨지는 모양과 소리와 손맛이 전부 달라진다.
 *
 * @param contactCos 닿는 넓이. 값이 작을수록 넓게 닿는다(구면 내적 기준)
 * @param forceScale 누르는 힘 배율
 * @param impactScale 화면 흔들림과 진동 배율
 * @param brightness 파편을 고를 때의 음높이 배율. 쇠붙이는 밝고 뭉툭한 것은 둔하다
 * @param continuous 문지르는 동안 계속 먹히는지. false면 한 번 찍을 때만 세게 들어간다
 * @param strikeDamage 찍는 도구가 한 방에 넣는 손상. 조각이 떨어지는 임계가 0.76이다
 */
enum class Weapon(
    val labelKo: String,
    val icon: String,
    val contactCos: Float,
    val forceScale: Float,
    val impactScale: Float,
    val brightness: Float,
    val continuous: Boolean,
    val strikeDamage: Float,
    val descKo: String,
) {
    FINGER(
        "손가락", "👆",
        contactCos = 0.955f, forceScale = 1.0f, impactScale = 1.0f,
        brightness = 1.0f, continuous = true, strikeDamage = 0f,
        descKo = "쭉 문질러 넓게 까기. 기본",
    ),

    NAIL(
        "손톱", "💅",
        contactCos = 0.988f, forceScale = 1.45f, impactScale = 0.7f,
        brightness = 1.25f, continuous = true, strikeDamage = 0f,
        descKo = "한 조각씩 콕콕. 좁고 깊게 파인다",
    ),

    HAMMER(
        "망치", "🔨",
        contactCos = 0.90f, forceScale = 3.2f, impactScale = 2.4f,
        brightness = 0.85f, continuous = false, strikeDamage = 1.5f,
        descKo = "한 방에 우수수. 찍을 때만 들어간다",
    ),

    DRILL(
        "드릴", "🌀",
        contactCos = 0.975f, forceScale = 2.1f, impactScale = 1.3f,
        brightness = 1.4f, continuous = true, strikeDamage = 0f,
        descKo = "대고 있으면 계속 갈려 나간다",
    ),

    FIST(
        "주먹", "✊",
        contactCos = 0.87f, forceScale = 2.0f, impactScale = 1.8f,
        brightness = 0.7f, continuous = false, strikeDamage = 1.05f,
        descKo = "가장 넓게 뭉갠다. 둔탁하게",
    );

    companion object {
        fun byId(id: Int): Weapon = entries.getOrElse(id) { FINGER }
    }
}
