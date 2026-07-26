package com.waxball.asmr.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EventQueueTest {

    private class Recorder : EventQueue.Sink {
        val kinds = ArrayList<Int>()
        val ids = ArrayList<Int>()
        override fun on(kind: Int, shardId: Int, level: Int, energy: Float, pan: Float, areaFrac: Float) {
            kinds.add(kind); ids.add(shardId)
        }
    }

    @Test
    fun pushThenPollReturnsSameValues() {
        val q = EventQueue(8)
        assertTrue(q.push(EventKind.DETACH, 7, 4, 0.5f, -0.3f, 0.02f))
        var seen = false
        q.poll(object : EventQueue.Sink {
            override fun on(kind: Int, shardId: Int, level: Int, energy: Float, pan: Float, areaFrac: Float) {
                seen = true
                assertEquals(EventKind.DETACH, kind)
                assertEquals(7, shardId)
                assertEquals(4, level)
                assertEquals(0.5f, energy, 1e-6f)
                assertEquals(-0.3f, pan, 1e-6f)
                assertEquals(0.02f, areaFrac, 1e-6f)
            }
        })
        assertTrue(seen)
    }

    @Test
    fun preservesFifoOrder() {
        val q = EventQueue(16)
        repeat(10) { q.push(EventKind.CRACK, it, 1, 1f, 0f, 0.01f) }
        val r = Recorder()
        q.drain(r)
        assertEquals((0 until 10).toList(), r.ids)
    }

    @Test
    fun pushFailsWhenFull() {
        val q = EventQueue(4)
        repeat(4) { assertTrue("$it 번째 push 실패", q.push(EventKind.CRACK, it, 1, 1f, 0f, 0.01f)) }
        assertFalse("가득 찼는데 push가 성공함", q.push(EventKind.CRACK, 99, 1, 1f, 0f, 0.01f))
    }

    @Test
    fun pollOnEmptyReturnsFalse() {
        assertFalse(EventQueue(4).poll(Recorder()))
    }

    @Test
    fun wrapsAroundCorrectly() {
        val q = EventQueue(4)
        repeat(20) { i ->
            assertTrue(q.push(EventKind.CRACK, i, 1, 1f, 0f, 0.01f))
            val r = Recorder()
            assertTrue(q.poll(r))
            assertEquals(i, r.ids[0])
        }
    }

    @Test
    fun sizeTracksContents() {
        val q = EventQueue(8)
        assertTrue(q.isEmpty)
        repeat(5) { q.push(EventKind.CRACK, it, 1, 1f, 0f, 0.01f) }
        assertEquals(5, q.size)
        q.drain(Recorder())
        assertEquals(0, q.size)
        assertTrue(q.isEmpty)
    }

    @Test
    fun clearDiscardsPending() {
        val q = EventQueue(8)
        repeat(5) { q.push(EventKind.CRACK, it, 1, 1f, 0f, 0.01f) }
        q.clear()
        assertTrue(q.isEmpty)
        assertFalse(q.poll(Recorder()))
    }

    @Test
    fun droppedEventsDoNotCorruptLaterOnes() {
        val q = EventQueue(3)
        repeat(3) { q.push(EventKind.CRACK, it, 1, 1f, 0f, 0.01f) }
        q.push(EventKind.CRACK, 99, 1, 1f, 0f, 0.01f) // 버려짐
        val r = Recorder()
        q.drain(r)
        assertEquals(listOf(0, 1, 2), r.ids)
    }
}
