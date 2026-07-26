package com.waxball.asmr.core

import java.util.concurrent.atomic.AtomicInteger

/**
 * 생산자 하나(터치/렌더 스레드), 소비자 하나(오디오 스레드)를 전제로 한 링 버퍼.
 *
 * 오디오 스레드에서 객체를 만들면 GC가 콜백을 멈춰 소리가 뚝뚝 끊긴다.
 * 그래서 이벤트를 객체가 아니라 병렬 원시 배열의 한 줄로 저장하고,
 * 소비 측에는 값만 넘긴다. 큐가 가득 차면 이벤트를 버린다.
 * 소리 하나를 잃는 것이 오디오가 멈추는 것보다 낫다.
 */
class EventQueue(private val capacity: Int) {

    interface Sink {
        fun on(kind: Int, shardId: Int, level: Int, energy: Float, pan: Float, areaFrac: Float)
    }

    private val slots = capacity + 1

    private val kinds = IntArray(slots)
    private val shardIds = IntArray(slots)
    private val levels = IntArray(slots)
    private val energies = FloatArray(slots)
    private val pans = FloatArray(slots)
    private val areas = FloatArray(slots)

    private val writeIndex = AtomicInteger(0)
    private val readIndex = AtomicInteger(0)

    /** 넘치면 false를 반환하고 이벤트를 버린다. */
    fun push(kind: Int, shardId: Int, level: Int, energy: Float, pan: Float, areaFrac: Float): Boolean {
        val w = writeIndex.get()
        val next = if (w + 1 == slots) 0 else w + 1
        if (next == readIndex.get()) return false

        kinds[w] = kind
        shardIds[w] = shardId
        levels[w] = level
        energies[w] = energy
        pans[w] = pan
        areas[w] = areaFrac

        writeIndex.set(next)
        return true
    }

    /** 하나를 꺼내 sink에 넘긴다. 비어 있으면 false. */
    fun poll(sink: Sink): Boolean {
        val r = readIndex.get()
        if (r == writeIndex.get()) return false

        sink.on(kinds[r], shardIds[r], levels[r], energies[r], pans[r], areas[r])

        readIndex.set(if (r + 1 == slots) 0 else r + 1)
        return true
    }

    fun drain(sink: Sink) {
        while (poll(sink)) { /* 비울 때까지 */ }
    }

    fun clear() {
        readIndex.set(writeIndex.get())
    }

    val size: Int
        get() {
            val w = writeIndex.get()
            val r = readIndex.get()
            return if (w >= r) w - r else slots - r + w
        }

    val isEmpty: Boolean get() = readIndex.get() == writeIndex.get()

    val maxSize: Int get() = capacity
}
