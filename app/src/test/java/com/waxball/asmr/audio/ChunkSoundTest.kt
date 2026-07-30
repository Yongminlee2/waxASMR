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

    /** 덩어리는 전부 무음, 깔개 녹음만 소리가 있는 신스. 깔개가 나오는지 가른다. */
    private fun bedSynth(): Synth {
        val len = 48000
        val silent = Synth(
            48000,
            chunks = ChunkBank.of(
                samples = ShortArray(len) { 0 },
                offsets = intArrayOf(0),
                lengths = intArrayOf(len),
                materials = intArrayOf(0),
                centroids = floatArrayOf(2000f),
            ),
        )
        silent.setProfile(SoundProfile.hardWax())
        silent.raw = RawRecording.of(ShortArray(48000 * 4) { 8000 })
        return silent
    }

    @Test
    fun kneadingKeepsTheBedFlowing() {
        // 주무르는 내내 소리가 이어져야 한다. 문지름 이벤트가 깔개 녹음을 살려 두고,
        // 덩어리 출력 밑에 원본 결이 깔린다.
        val s = bedSynth()
        s.on(EventKind.RUB, -1, 0, 0.5f, 0f, 0f)
        val out = FloatArray(512 * 2)
        s.render(out, 512)
        var peak = 0f
        for (v in out) if (kotlin.math.abs(v) > peak) peak = kotlin.math.abs(v)
        assertTrue("문지르는데 깔개가 무음", peak > 0.01f)
    }

    @Test
    fun bedStopsWhenHandsAreStill() {
        // 이벤트 없이 시간이 지나면 깔개도 멎어야 한다. 가만히 있는데 소리가 나면 유령이다.
        val s = bedSynth()
        s.on(EventKind.RUB, -1, 0, 0.5f, 0f, 0f)
        // 침묵 한계(0.15초)를 넘길 만큼 렌더한다
        val out = FloatArray(512 * 2)
        repeat(20) { s.render(out, 512) }
        var peak = 0f
        for (v in out) if (kotlin.math.abs(v) > peak) peak = kotlin.math.abs(v)
        assertEquals("손을 뗐는데 깔개가 계속 남", 0f, peak, 1e-4f)
    }

    @Test
    fun landIsSilentInChunkMode() {
        // 조각은 풍선 안에 갇혀 바닥에 닿지 않는다. 착지음이 나면 모델이 깨진 것이다.
        val s = synth()
        s.on(EventKind.LAND, 0, 0, 1f, 0f, 0.01f)
        assertEquals("덩어리 모드인데 착지음이 남", 0, s.activeGrains)
    }
}
