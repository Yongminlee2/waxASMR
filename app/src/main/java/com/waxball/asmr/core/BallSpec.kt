package com.waxball.asmr.core

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/** 껍질 재질. 소리 차이가 가장 크게 느껴지는 축이다. */
enum class Material(val labelKo: String) {
    HARD_WAX("굳은 왁스"),
    SOFT_WAX("무른 왁스"),
    GLITTER("반짝이 왁스"),
    CRUNCH_BEADS("알갱이 왁스"),
    SUGAR_GLASS("설탕 유리");

    fun profile(): SoundProfile = when (this) {
        HARD_WAX -> SoundProfile.hardWax()
        SOFT_WAX -> SoundProfile.softWax()
        GLITTER -> SoundProfile.glitter()
        CRUNCH_BEADS -> SoundProfile.crunchBeads()
        SUGAR_GLASS -> SoundProfile.sugarGlass()
    }
}

/**
 * 겉모양. 방향에 따라 반지름을 바꿔서 만든다.
 *
 * 모양은 눈에만 보이는 게 아니라 소리도 바꾼다. 각진 볼은 모서리에 힘이 몰려 금이 잘 번지고,
 * 길쭉한 볼은 공명이 더 살고, 울퉁불퉁한 볼은 두께가 제각각이라 음높이가 넓게 흩어진다.
 *
 * @param propagationScale 금이 번지는 정도
 * @param resonanceScale 공명이 실리는 정도
 * @param spreadScale 파열 음높이가 흩어지는 폭
 */
enum class ShapeKind(
    val labelKo: String,
    val propagationScale: Float,
    val resonanceScale: Float,
    val spreadScale: Float,
) {
    SPHERE("동그란", 1.0f, 1.0f, 1.0f),
    EGG("달걀형", 0.95f, 1.15f, 0.92f),
    FACETED("각진", 1.2f, 0.88f, 1.1f),
    LUMPY("울퉁불퉁한", 0.85f, 1.0f, 1.25f);

    /** 방향 → 반지름 배율. */
    fun warp(v: Vec3): Float = when (this) {
        SPHERE -> 1f
        EGG -> 1f + 0.22f * v.y * v.y
        // 지수를 올리면 구가 모서리 둥근 정육면체로 간다.
        FACETED -> {
            val p = 4f
            val d = (abs(v.x).pow(p) + abs(v.y).pow(p) + abs(v.z).pow(p)).pow(1f / p)
            if (d < 1e-4f) 1f else 1f / d * 0.92f
        }
        LUMPY -> 1f + 0.075f * sin(3.4f * v.x + 1.1f) * cos(4.1f * v.y) * cos(2.7f * v.z)
    }
}

enum class SizeClass(val labelKo: String, val radius: Float, val shardBase: Int, val freqScale: Float, val decayScale: Float) {
    S("작은", 0.75f, 90, 1.25f, 0.85f),
    M("보통", 1.0f, 150, 1.0f, 1.0f),
    L("큰", 1.25f, 220, 0.82f, 1.2f),
    XL("왕", 1.5f, 300, 0.68f, 1.45f),
}

enum class Thickness(val labelKo: String, val value: Float) {
    THIN("얇은", 0.06f),
    NORMAL("보통", 0.11f),
    THICK("두꺼운", 0.18f),
}

/**
 * 볼 한 종류. 색만 다른 게 아니라 소리 파라미터 세트가 통째로 다르다.
 */
data class BallSpec(
    val id: Int,
    val nameKo: String,
    val size: SizeClass,
    val thickness: Thickness,
    val shape: ShapeKind,
    val material: Material,
    val shellColor: Int,
    val fleshColor: Int,
    val coreColor: Int,
    val capsule: String,
    val price: Int,
    val soundDesc: String,
) {
    /** 재질 기본값에서 출발해 크기·두께·모양으로 보정한다. */
    fun soundProfile(): SoundProfile {
        val p = material.profile()

        val thickFreq = when (thickness) {
            Thickness.THIN -> 1.15f
            Thickness.NORMAL -> 1f
            Thickness.THICK -> 0.85f
        }
        val thickDecay = when (thickness) {
            Thickness.THIN -> 0.8f
            Thickness.NORMAL -> 1f
            Thickness.THICK -> 1.3f
        }
        val thickBright = when (thickness) {
            Thickness.THIN -> 1.25f
            Thickness.NORMAL -> 1f
            Thickness.THICK -> 0.8f
        }
        val thickRes = if (thickness == Thickness.THICK) 1.3f else 1f

        return p.copy(
            baseFreq = p.baseFreq * size.freqScale * thickFreq,
            freqSpread = p.freqSpread * shape.spreadScale,
            decayMsMin = p.decayMsMin * size.decayScale * thickDecay,
            decayMsMax = p.decayMsMax * size.decayScale * thickDecay,
            resonance = (p.resonance * thickRes * shape.resonanceScale).coerceAtMost(0.9f),
            brightness = p.brightness * thickBright,
            propagation = (p.propagation * shape.propagationScale).coerceIn(0.05f, 0.8f),
        )
    }

    /** 화질 등급(0=낮음,1=보통,2=높음)에 맞춘 조각 수. */
    fun shardCount(quality: Int): Int {
        val cap = when (quality) {
            0 -> 100
            1 -> 180
            else -> 300
        }
        return minOf(size.shardBase, cap)
    }

    /** 조각을 그만큼 만들 수 있을 만큼만 기반 메시를 잘게 나눈다. */
    fun baseSubdivision(quality: Int): Int = if (quality <= 0) 3 else 4

    val shellThickness: Float get() = thickness.value

    val summaryKo: String
        get() = "${size.labelKo} · ${thickness.labelKo} 껍질 · ${shape.labelKo} · ${material.labelKo}"
}
