package com.waxball.asmr.core

import org.junit.Test
import kotlin.random.Random

/**
 * 실제로 초당 몇 번 소리가 나는지 잰다.
 *
 * 실제 왁뿌볼 녹음은 귀에 들리는 파열이 초당 4~12회였다.
 * 그보다 훨씬 잦으면 낱개 크런치가 아니라 연속된 소음으로 들린다.
 */
class BreakRateProbeTest {

    private class Counter : EventQueue.Sink {
        val byKind = IntArray(5)
        override fun on(kind: Int, shardId: Int, level: Int, energy: Float, pan: Float, areaFrac: Float) {
            byKind[kind]++
        }
    }

    private fun measure(label: String, thresholds: FloatArray?, useBrush: Boolean): String {
        val q = EventQueue(65536)
        val shards = ShardSplitter.split(Icosphere.build(4), 150, Random(3))
        val m = BreakModel(shards, SoundProfile.hardWax(), q)
        thresholds?.let { m.overrideThresholdsForTest(it) }

        // 1초 동안 손가락이 표면을 훑는다.
        var frame = 0
        repeat(60) {
            val hit = shards.shards[(frame * 11) % shards.size].center
            if (useBrush) m.pressArea(hit, 0.955f, 3f, 0.016f, 0f)
            else m.press((frame * 11) % shards.size, 3f, 0.016f, 0f)
            frame++
        }

        val c = Counter()
        q.drain(c)
        val total = c.byKind[EventKind.CRACK] + c.byKind[EventKind.DETACH]
        return "$label → 초당 ${total}회 (크랙 ${c.byKind[EventKind.CRACK]}, 분리 ${c.byKind[EventKind.DETACH]})"
    }

    /** 같은 프레임에 분리음이 몇 개나 겹치는지, 그 조각들이 얼마나 큰지 본다. */
    private fun measureDetachBursts(label: String, cascade: Boolean): String {
        val shards = ShardSplitter.split(Icosphere.build(4), 150, Random(3))
        val q = EventQueue(65536)
        val m = BreakModel(shards, SoundProfile.hardWax(), q)
        if (!cascade) m.disableCascadeForTest()

        var worstBurst = 0
        var biggestArea = 0f
        var totalDetach = 0

        repeat(600) { frame ->
            val hit = shards.shards[(frame * 11) % shards.size].center
            m.pressArea(hit, 0.955f, 3f, 0.016f, 0f)

            var burst = 0
            var area = 0f
            q.drain(object : EventQueue.Sink {
                override fun on(kind: Int, shardId: Int, level: Int, energy: Float, pan: Float, areaFrac: Float) {
                    if (kind == EventKind.DETACH) { burst++; area += areaFrac }
                }
            })
            if (burst > worstBurst) worstBurst = burst
            if (area > biggestArea) biggestArea = area
            totalDetach += burst
        }
        return "$label → 한 프레임 최대 분리음 ${worstBurst}개, 그때 합친 넓이 %.3f, 총 분리 ${totalDetach}회"
            .format(biggestArea)
    }

    @Test
    fun compareDetachBursts() {
        val report = buildString {
            appendLine(measureDetachBursts("연쇄 끔", cascade = false))
            appendLine(measureDetachBursts("연쇄 켬(현재)", cascade = true))
        }
        java.io.File("build/burst-report.txt").apply {
            parentFile?.mkdirs()
            writeText(report)
        }
    }

    @Test
    fun compareCurrentAgainstPrevious() {
        val report = buildString {
            appendLine(measure("이전(높은 임계, 붓 없음)", floatArrayOf(1.0f, 1.7f, 2.5f, 3.4f), useBrush = false))
            appendLine(measure("임계만 낮춤", null, useBrush = false))
            appendLine(measure("현재(임계 낮춤 + 붓 반경)", null, useBrush = true))
        }
        java.io.File("build/rate-report.txt").apply {
            parentFile?.mkdirs()
            writeText(report)
        }
    }
}
