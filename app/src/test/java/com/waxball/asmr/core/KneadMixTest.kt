package com.waxball.asmr.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 반죽 색 여정의 규칙.
 *
 * 시작은 속색 그대로, 끝은 전부 섞인 색 하나. 중간에는 팔레트 색들이 차례로
 * 배어 나와야 "여러 색 점토를 주무르면 색이 변한다"가 성립한다.
 */
class KneadMixTest {

    private val red = 0xFFD84040.toInt()
    private val blue = 0xFF3A6AD8.toInt()
    private val yellow = 0xFFF0C030.toInt()
    private val core = 0xFF808080.toInt()

    @Test
    fun startsAtTheCoreColour() {
        assertEquals(core, KneadMix.colorAt(core, listOf(red, blue), 0f))
    }

    @Test
    fun passesThroughEveryPaletteColour() {
        // 세 색 팔레트면 정거장이 [속, 빨, 파, 노, 평균] 다섯. 중간 지점마다 그 색이어야 한다.
        val palette = listOf(red, blue, yellow)
        assertEquals(red, KneadMix.colorAt(core, palette, 0.25f))
        assertEquals(blue, KneadMix.colorAt(core, palette, 0.5f))
        assertEquals(yellow, KneadMix.colorAt(core, palette, 0.75f))
    }

    @Test
    fun endsFullyMixed() {
        // 끝 색은 어느 재료색과도 같지 않은 "다 섞인" 색이다.
        val end = KneadMix.colorAt(core, listOf(red, blue), 1f)
        assertNotEquals(red, end)
        assertNotEquals(blue, end)
        assertNotEquals(core, end)
    }

    @Test
    fun singleColourClayBarelyChanges() {
        // 단색 반죽은 처음부터 끝까지 속색과 그 색 사이에만 머문다.
        val end = KneadMix.colorAt(core, listOf(red), 1f)
        val endR = (end shr 16) and 0xFF
        assertTrue("단색 반죽이 팔레트 밖 색으로 감", endR in 0x80..0xD8)
    }

    @Test
    fun journeyIsContinuous() {
        // 한 걸음(0.01)에 채널이 확 뛰면 화면에서 색이 툭툭 끊겨 보인다.
        val palette = listOf(red, blue, yellow, 0xFF3AA850.toInt())
        var prev = KneadMix.colorAt(core, palette, 0f)
        var t = 0.01f
        while (t <= 1f) {
            val cur = KneadMix.colorAt(core, palette, t)
            for (shift in intArrayOf(16, 8, 0)) {
                val d = Math.abs(((cur shr shift) and 0xFF) - ((prev shr shift) and 0xFF))
                assertTrue("t=$t 에서 채널이 $d 만큼 뜀", d <= 12)
            }
            prev = cur
            t += 0.01f
        }
    }

    @Test
    fun emptyPaletteFallsBackToCore() {
        assertEquals(core, KneadMix.colorAt(core, emptyList(), 0.7f))
    }
}
