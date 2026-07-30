package com.waxball.asmr.gl

import com.waxball.asmr.core.Quat
import com.waxball.asmr.core.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * 깨진 조각이 고무풍선 안에 갇혀 있는지.
 *
 * 조각이 풍선 밖으로 새면 "안에서 놀아난다"가 통째로 깨지고,
 * 쥘 때 우리가 안 줄면 풍선만 눌리고 조각은 그대로라 물리가 따로 논다.
 */
class TrappedShardsTest {

    private val inner = 0.94f

    private fun centers(n: Int): FloatArray {
        val c = FloatArray(n * 3)
        for (i in 0 until n) {
            c[i * 3] = 0.9f      // 껍질 근처에서 출발한다
            c[i * 3 + 1] = 0f
            c[i * 3 + 2] = 0f
        }
        return c
    }

    private fun settled(n: Int = 4, frames: Int = 240): Pair<TrappedShards, FloatArray> {
        val t = TrappedShards(n, innerRadius = inner, rng = Random(5))
        val c = centers(n)
        for (i in 0 until n) t.spawn(i, Vec3(1f, 0f, 0f), 0.01f, Quat.IDENTITY)
        repeat(frames) { t.update(1f / 60f, c) }
        return t to c
    }

    @Test
    fun piecesStayInsideTheBalloon() {
        val (t, c) = settled()
        for (i in 0 until 4) {
            assertTrue("조각 $i 가 풍선 밖: ${t.radiusOf(i, c)}", t.radiusOf(i, c) <= inner + 0.01f)
        }
    }

    @Test
    fun squeezingDoesNotPushPiecesOut() {
        val (t, c) = settled()
        repeat(120) {
            t.squeeze(3f)
            t.update(1f / 60f, c)
        }
        for (i in 0 until 4) {
            assertTrue("쥐었더니 조각이 샜다: ${t.radiusOf(i, c)}", t.radiusOf(i, c) <= inner + 0.01f)
        }
    }

    @Test
    fun shrinkingTheCagePullsPiecesInward() {
        // 쥐면 풍선이 눌리고 안의 공간도 같이 줄어야 조각이 밀집된다.
        val (t, c) = settled()
        t.setCage(0.6f)
        repeat(120) { t.update(1f / 60f, c) }
        for (i in 0 until 4) {
            assertTrue(
                "우리를 줄였는데 조각 $i 가 안 들어옴: ${t.radiusOf(i, c)}",
                t.radiusOf(i, c) <= inner * 0.6f + 0.01f,
            )
        }
    }

    @Test
    fun releasingTheCageLetsPiecesSpreadAgain() {
        val (t, c) = settled()
        t.setCage(0.6f)
        repeat(120) { t.update(1f / 60f, c) }
        t.setCage(1f)
        repeat(120) {
            t.squeeze(2f)
            t.update(1f / 60f, c)
        }
        // 다시 벌어질 수는 있어야 하고, 그래도 풍선 안이어야 한다.
        for (i in 0 until 4) {
            assertTrue(t.radiusOf(i, c) <= inner + 0.01f)
        }
    }

    @Test
    fun kneadAccumulatesAndSaturates() {
        // 주무를수록 조각이 속과 섞여 간다. 1을 넘으면 색이 뒤집히므로 거기서 멈춘다.
        val (t, _) = settled()
        assertEquals(0f, t.knead, 1e-6f)
        t.addKnead(0.4f)
        assertEquals(0.4f, t.knead, 1e-6f)
        repeat(10) { t.addKnead(0.4f) }
        assertEquals("반죽이 1을 넘음", 1f, t.knead, 1e-6f)
        t.addKnead(-5f)
        assertEquals("음수 입력에 반죽이 줄어듦", 1f, t.knead, 1e-6f)
    }

    @Test
    fun clearResetsKnead() {
        // 새 볼은 아직 안 주무른 볼이다. 반죽이 남아 있으면 새 볼이 처음부터 섞여 보인다.
        val (t, _) = settled()
        t.addKnead(0.8f)
        t.clear()
        assertEquals(0f, t.knead, 1e-6f)
    }

    @Test
    fun kneadingPullsPiecesTowardTheCentre() {
        // 반죽될수록 조각이 속살 쪽으로 조여들어야 "속과 합쳐진다"로 보인다.
        val (t, c) = settled()
        t.addKnead(1f)
        repeat(240) { t.update(1f / 60f, c) }
        for (i in 0 until 4) {
            assertTrue(
                "반죽했는데 조각 $i 가 안 조여듦: ${t.radiusOf(i, c)}",
                t.radiusOf(i, c) <= inner * (1f - 0.35f) + 0.01f,
            )
        }
    }

    @Test
    fun clearResetsTheCage() {
        val (t, c) = settled()
        t.setCage(0.6f)
        t.clear()
        assertEquals(0, t.count)
        // 새로 넣은 조각이 원래 크기의 우리를 쓴다.
        t.spawn(0, Vec3(1f, 0f, 0f), 0.01f, Quat.IDENTITY)
        repeat(240) { t.update(1f / 60f, c) }
        assertTrue(t.radiusOf(0, c) <= inner + 0.01f)
    }
}
