package com.waxball.asmr.core

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * 한 번 문지를 때 소리 이벤트가 몇 개나 쏟아지는지 본다.
 *
 * 붓 반경을 넣은 뒤로 한 번에 조각 열몇 개가 동시에 깨지는데, 조각마다 파열음이
 * 따로 나가면 소리가 겹쳐서 뭉개진다. 실제로는 한 번 문지르면 한 번 "와작"이지
 * 열몇 번이 아니다.
 */
class BreakSoundLoadTest {

    private class Counter : EventQueue.Sink {
        var cracks = 0
        var detaches = 0
        override fun on(kind: Int, shardId: Int, level: Int, energy: Float, pan: Float, areaFrac: Float) {
            when (kind) {
                EventKind.CRACK -> cracks++
                EventKind.DETACH -> detaches++
            }
        }
    }

    private fun model(queue: EventQueue) = BreakModel(
        ShardSplitter.split(Icosphere.build(4), 150, Random(3)),
        SoundProfile.hardWax(),
        queue,
    )

    @Test
    fun oneBrushStrokeDoesNotFloodTheAudioQueue() {
        val q = EventQueue(8192)
        val m = model(q)
        val hit = m.shards.shards[0].center

        m.pressArea(hit, 0.955f, 3f, 0.016f, 0f)

        val c = Counter()
        q.drain(c)
        val total = c.cracks + c.detaches
        assertTrue(
            "한 번 문질렀는데 소리 이벤트가 ${total}개 나감. 겹쳐서 뭉개진다",
            total <= 3,
        )
    }

    @Test
    fun brushStrokeStillBreaksManyShards() {
        // 소리를 묶는다고 해서 깨지는 양까지 줄면 안 된다.
        val q = EventQueue(8192)
        val m = model(q)
        val hit = m.shards.shards[0].center
        repeat(30) { m.pressArea(hit, 0.955f, 3f, 0.016f, 0f) }

        val touched = m.state.count { it > ShardState.INTACT }
        assertTrue("문질렀는데 거의 안 깨짐 (${touched}개)", touched >= 6)
    }

    @Test
    fun continuousRubbingStaysWithinAReasonableRate() {
        // 1초 동안 문질렀을 때 초당 파열음이 사람이 낱개로 들을 수 있는 범위여야 한다.
        // 실제 왁뿌볼 녹음은 초당 4~12회였다.
        val q = EventQueue(16384)
        val m = model(q)
        var frame = 0
        repeat(60) {
            val hit = m.shards.shards[(frame * 7) % m.shards.size].center
            m.pressArea(hit, 0.955f, 3f, 0.016f, 0f)
            frame++
        }

        val c = Counter()
        q.drain(c)
        val perSecond = c.cracks + c.detaches
        assertTrue("1초에 소리 이벤트가 ${perSecond}개. 너무 많아 뭉개진다", perSecond <= 90)
    }
}
