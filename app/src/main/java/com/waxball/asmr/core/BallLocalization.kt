package com.waxball.asmr.core

import android.content.Context
import com.waxball.asmr.R

/**
 * 볼 카탈로그의 이름·설명·재질/모양/크기/두께 이름표를 언어별로 옮긴다.
 *
 * [BallSpec]의 nameKo·soundDesc·labelKo 필드는 한국어 원본이자 폴백이다.
 * 실제 화면에는 여기를 거쳐서 나가야 언어 설정이 반영된다. 배열 인덱스는
 * [BallCatalog.all]의 순서·enum 선언 순서와 반드시 같아야 한다 — 어긋나면
 * 엉뚱한 이름이 붙는다.
 */
object BallLocalization {

    fun name(context: Context, spec: BallSpec): String =
        pick(context, R.array.ball_names, spec.id, spec.nameKo)

    fun soundDesc(context: Context, spec: BallSpec): String =
        pick(context, R.array.ball_sound_descs, spec.id, spec.soundDesc)

    /** "%1$s · %2$s · %3$s · %4$s" 형태의 요약 한 줄. */
    fun summary(context: Context, spec: BallSpec): String {
        val size = pick(context, R.array.size_labels, spec.size.ordinal, spec.size.labelKo)
        val thickness = pick(
            context, R.array.thickness_shell_labels, spec.thickness.ordinal,
            "${spec.thickness.labelKo} 껍질",
        )
        val shape = pick(context, R.array.shape_labels, spec.shape.ordinal, spec.shape.labelKo)
        val material = pick(context, R.array.material_labels, spec.material.ordinal, spec.material.labelKo)
        return "$size · $thickness · $shape · $material"
    }

    private fun pick(context: Context, arrayRes: Int, index: Int, fallback: String): String {
        val arr = context.resources.getStringArray(arrayRes)
        return arr.getOrNull(index) ?: fallback
    }
}
