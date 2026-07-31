package com.waxball.asmr.core

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * 껍질 재질. 소리 차이가 가장 크게 느껴지는 축이다.
 *
 * 재질마다 **따로 녹음한 덩어리 뱅크**가 있다([bank]가 그 번호다).
 * 예전에는 녹음 하나에서 밝은 파편/어두운 파편을 골라 재질을 흉내 냈는데,
 * 그러면 결국 같은 소리의 변주라서 볼을 바꿔도 다르게 들리지 않았다.
 *
 * 괄호 안 수치는 그 뱅크 덩어리들의 스펙트럼 중심 중앙값이다. 이름을 그 값에 맞췄다.
 *
 * @param bank chunks.idx의 재질 번호. 뱅크가 없으면 [profile]로 되돌아간다
 */
enum class Material(val labelKo: String, val bank: Int) {
    HARD_WAX("굳은 왁스", 0),          // 2509Hz
    GLITTER("반짝이 왁스", 1),         // 3716Hz
    SUGAR_GLASS("설탕 유리", 2),       // 3280Hz
    CHEWY_WAX("쫀득 왁스", 3),         // 2239Hz · 파열이 가장 촘촘하다(초당 10회)
    THICK_WAX("두꺼운 왁스", 4),       // 1195Hz
    GLASS_BEAD("유리알", 5),           // 4261Hz · 가장 밝다. 원재료가 10초뿐이라 반복이 들릴 수 있다
    CRUNCH_BEADS("알갱이 왁스", 6),    // 1212Hz
    SOFT_WAX("무른 왁스", 7),          // 1346Hz
    SQUISHY_WAX("말랑 왁스", 8),       // 1185Hz
    CLAY_WAX("찰흙 왁스", 9);          // 533Hz · 가장 둔하다

    /** 덩어리 뱅크를 못 읽었을 때 쓰는 합성 프로파일. */
    fun profile(): SoundProfile = when (this) {
        HARD_WAX, THICK_WAX -> SoundProfile.hardWax()
        SOFT_WAX, SQUISHY_WAX, CLAY_WAX -> SoundProfile.softWax()
        GLITTER, GLASS_BEAD -> SoundProfile.glitter()
        CRUNCH_BEADS, CHEWY_WAX -> SoundProfile.crunchBeads()
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

/**
 * 껍질 바깥면 무늬. 셰이더가 절차적으로 그린다.
 *
 * 텍스처를 쓰면 볼 30종에 이미지가 30장 붙어 용량이 감당이 안 된다.
 * 잡음 함수로 만들면 공짜이고, 볼마다 무늬가 미세하게 달라 반복도 안 보인다.
 *
 * @param code 셰이더의 uSurface 값. 순서를 바꾸면 무늬가 어긋난다
 */
enum class SurfaceKind(val labelKo: String, val code: Int) {
    PLAIN("민무늬", 0),
    BANDED("가로 띠", 1),
    SWIRL("소용돌이", 2),
    CRATER("분화구", 3),
    ICY("얼음 균열", 4),
    LAVA("용암", 5),
    FLARE("불꽃", 6),
    SPECKLE("점박이", 7),
    TERRA("바다와 대륙", 8),
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
    /** 껍질 바깥면 무늬. 사진이 있으면([textureAsset]) 그쪽이 이긴다. */
    val surface: SurfaceKind = SurfaceKind.PLAIN,
    /** 무늬에 섞이는 두 번째 색. 띠 사이나 균열 안쪽에 들어간다. */
    val accentColor: Int = shellColor,
    /**
     * 실제 탐사선·위성이 찍은 표면 지도(assets/planets/ 안 파일 이름).
     *
     * 잡음 함수로는 "그럴듯한 가짜"까지가 한계다. 실존 천체는 진짜 지도를 입힌다.
     * 사진이 없는 천체(항성·성운·블랙홀 등)만 절차적 무늬로 남는다.
     */
    val textureAsset: String? = null,
    /**
     * 반죽에 섞여 있는 재료색 1~4개.
     *
     * 실제 점토 왁뿌볼은 단색도 있고 두어 색을 뭉친 것도 있다. 주무를수록 속살이
     * 이 색들을 차례로 배어들다 끝에는 전부 섞인 색으로 굳는다([KneadMix]).
     */
    val kneadColors: List<Int> = listOf(shellColor, fleshColor),
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
}
