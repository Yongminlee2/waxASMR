package com.waxball.asmr.audio

import com.waxball.asmr.core.EventKind
import com.waxball.asmr.core.SoundProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 덩어리 모드의 소리 규칙.
 *
 * 잔여물을 쥘 때의 문지름 이벤트는 프레임마다 온다. 올 때마다 덩어리를 얹으면
 * 순식간에 수십 개가 쌓여 뭉개진다 — 파편 시절 크랙 67개 사건과 같은 함정이다.
 */
class ChunkSoundTest {

    /** 1초짜리 덩어리 두 개(재질 0). 게이트 검증에는 이걸로 충분하다. */
    private fun bank(): ChunkBank {
        val len = 48000
        return ChunkBank.of(
            samples = ShortArray(len * 2) { 1000 },
            offsets = intArrayOf(0, len),
            lengths = intArrayOf(len, len),
            materials = intArrayOf(0, 0),
            centroids = floatArrayOf(2000f, 2100f),
        )
    }

    private fun synth() = Synth(48000, chunks = bank()).apply {
        setProfile(SoundProfile.hardWax())
    }

    @Test
    fun theChunkPathIsActuallyUsed() {
        val s = synth()
        assertTrue("덩어리 모드가 아님", s.usingChunks)
    }

    @Test
    fun rubPlaysOneChunkWhenQuiet() {
        val s = synth()
        s.on(EventKind.RUB, -1, 0, 0.5f, 0f, 0f)
        assertEquals("조용한데 문지름이 소리를 안 냄", 1, s.activeGrains)
    }

    @Test
    fun rubDoesNotStackWhileAChunkIsPlaying() {
        val s = synth()
        // 문지름 이벤트가 프레임마다 오는 상황
        repeat(60) { s.on(EventKind.RUB, -1, 0, 0.8f, 0f, 0f) }
        assertEquals("문지름이 겹겹이 쌓임", 1, s.activeGrains)
    }

    @Test
    fun rubYieldsToACrackAlreadyPlaying() {
        val s = synth()
        s.on(EventKind.CRACK, 0, 1, 1f, 0f, 0.01f)
        val after = s.activeGrains
        s.on(EventKind.RUB, -1, 0, 0.8f, 0f, 0f)
        assertEquals("파열 중인데 문지름이 또 얹힘", after, s.activeGrains)
    }

    @Test
    fun landIsSilentInChunkMode() {
        // 조각은 풍선 안에 갇혀 바닥에 닿지 않는다. 착지음이 나면 모델이 깨진 것이다.
        val s = synth()
        s.on(EventKind.LAND, 0, 0, 1f, 0f, 0.01f)
        assertEquals("덩어리 모드인데 착지음이 남", 0, s.activeGrains)
    }
}
