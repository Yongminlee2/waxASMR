package com.waxball.asmr.audio

import kotlin.math.cos
import kotlin.math.sin

/**
 * 녹음 덩어리를 통째로 재생한다.
 *
 * [SampleGrainPool] 은 파편을 골라 피치를 흔들고 감쇠값으로 잘랐다. 그 과정에서
 * 원본의 셈여림·여운·음색이 차례로 깎였고, 그래서 녹음을 통째로 튼 쪽이 더
 * 왁뿌볼 같게 들렸다. 여기서는 아무것도 하지 않는다 — 고르고, 크기만 맞추고, 끝까지 튼다.
 *
 * 오디오 스레드에서 도는 코드이므로 객체를 하나도 만들지 않는다.
 */
class ChunkPool(
    private val bank: ChunkBank,
    override val capacity: Int = 24,
) : GrainSink {

    private val active = BooleanArray(capacity)
    private val delay = IntArray(capacity)
    private val start = IntArray(capacity)
    private val length = IntArray(capacity)
    private val position = IntArray(capacity)
    private val amp = FloatArray(capacity)
    private val panL = FloatArray(capacity)
    private val panR = FloatArray(capacity)

    /** 지금 쥔 볼의 재질. 화면이 볼을 바꿀 때 갈아 준다. */
    @Volatile var material = 0

    private var rngState = 0x1B87_3C29
    private var cursor = 0

    override var activeCount = 0
        private set

    override fun spawn(
        delayFrames: Int,
        freq: Float,
        q: Float,
        decayMs: Float,
        amplitude: Float,
        pan: Float,
        resonance: Float,
        attackMs: Float,
    ) {
        // freq·q·decayMs·resonance·attackMs 는 합성 전용이라 여기서는 쓰지 않는다.
        // 덩어리는 이미 완성된 소리다.
        if (bank.size == 0) return
        val slot = allocate(amplitude)
        if (slot < 0) return

        val index = bank.pick(material, nextInt())
        start[slot] = bank.offsetOf(index)
        length[slot] = bank.lengthOf(index)
        position[slot] = 0
        amp[slot] = amplitude
        delay[slot] = delayFrames.coerceAtLeast(0)

        val angle = (pan.coerceIn(-1f, 1f) + 1f) * (Math.PI.toFloat() / 4f)
        panL[slot] = cos(angle)
        panR[slot] = sin(angle)

        active[slot] = true
        activeCount++
    }

    override fun render(out: FloatArray, frames: Int) {
        val data = bank.samples
        for (g in 0 until capacity) {
            if (!active[g]) continue

            var i = 0
            if (delay[g] > 0) {
                val skip = if (delay[g] < frames) delay[g] else frames
                delay[g] -= skip
                i = skip
                if (i >= frames) continue
            }

            var pos = position[g]
            val base = start[g]
            val len = length[g]
            val a = amp[g]
            val pl = panL[g]
            val pr = panR[g]

            while (i < frames) {
                if (pos >= len) { active[g] = false; activeCount--; break }
                val s = data[base + pos].toInt() / 32768f * a
                out[i * 2] += s * pl
                out[i * 2 + 1] += s * pr
                pos++
                i++
            }

            position[g] = pos
        }
    }

    override fun reset() {
        java.util.Arrays.fill(active, false)
        activeCount = 0
    }

    /**
     * 빈 자리 우선, 없으면 가장 조용한 것을 뺏는다.
     *
     * 덩어리가 0.7초씩 울리므로 자리가 금방 찬다. 자리가 없을 때 새 소리를 버리면
     * 문지르는 동안 소리가 뚝뚝 끊긴다. 조용한 것을 밀어내는 편이 낫다.
     */
    private fun allocate(newAmp: Float): Int {
        for (n in 0 until capacity) {
            val i = cursor
            cursor = if (cursor + 1 == capacity) 0 else cursor + 1
            if (!active[i]) return i
        }
        var weakest = -1
        var level = Float.MAX_VALUE
        for (i in 0 until capacity) {
            if (amp[i] < level) { level = amp[i]; weakest = i }
        }
        if (weakest >= 0 && level < newAmp) {
            activeCount--
            return weakest
        }
        return -1
    }

    private fun nextInt(): Int {
        var n = rngState
        n = n xor (n shl 13); n = n xor (n ushr 17); n = n xor (n shl 5)
        rngState = n
        return n
    }
}
