package com.waxball.asmr.core

/**
 * 반죽 색 여정.
 *
 * 실제 점토 왁뿌볼은 속색 하나로 시작해 반죽할수록 재료색들이 차례로 배어 나오고,
 * 끝까지 치대면 전부 섞인 탁한 색 하나로 수렴한다. 그 순서를 그대로 만든다:
 * 속색 → 팔레트 색들을 차례로 → 전체 평균색.
 *
 * 한 색짜리 반죽은 거의 안 변하고, 서너 색짜리는 주무르는 내내 색이 갈아든다.
 */
object KneadMix {

    /**
     * @param coreColor 반죽 전 속색
     * @param palette 반죽에 섞여 있는 재료색 1~4개
     * @param t 반죽된 정도 0~1
     */
    fun colorAt(coreColor: Int, palette: List<Int>, t: Float): Int {
        if (palette.isEmpty()) return coreColor
        val k = t.coerceIn(0f, 1f)

        val stops = ArrayList<Int>(palette.size + 2)
        stops.add(coreColor)
        stops.addAll(palette)
        stops.add(average(stops))

        val pos = k * (stops.size - 1)
        val idx = pos.toInt().coerceAtMost(stops.size - 2)
        return blend(stops[idx], stops[idx + 1], pos - idx)
    }

    /** ARGB 두 색을 t만큼 섞는다. 결과는 항상 불투명이다. */
    fun blend(a: Int, b: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        fun ch(shift: Int): Int {
            val x = (a shr shift) and 0xFF
            val y = (b shr shift) and 0xFF
            return (x + ((y - x) * k)).toInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    private fun average(colors: List<Int>): Int {
        var r = 0; var g = 0; var b = 0
        for (c in colors) {
            r += (c shr 16) and 0xFF
            g += (c shr 8) and 0xFF
            b += c and 0xFF
        }
        val n = colors.size
        return (0xFF shl 24) or ((r / n) shl 16) or ((g / n) shl 8) or (b / n)
    }
}
