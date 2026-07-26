package com.waxball.asmr.audio

import kotlin.math.cos
import kotlin.math.sin

/**
 * 실제 왁뿌볼 파열음 파편을 재생하는 그레인 풀.
 *
 * 파편을 그냥 재생하면 몇 번만 들어도 같은 소리인 게 귀에 잡힌다. 그래서
 * 매번 다른 파편을 고르고, 피치를 반음 단위로 미세하게 흔들고, 길이를 다르게 자르고,
 * 좌우 위치를 손가락에 맞춘다. 한 번의 터치가 파편 수십 개의 조합이라
 * 같은 조합이 두 번 나올 일이 없다.
 *
 * 오디오 스레드에서 도는 코드이므로 객체를 하나도 만들지 않는다.
 */
class SampleGrainPool(
    private val bank: GrainBank,
    override val capacity: Int = 96,
    private val sampleRate: Int = 48000,
) : GrainSink {

    private val active = BooleanArray(capacity)
    private val delay = IntArray(capacity)
    private val start = IntArray(capacity)
    private val length = IntArray(capacity)
    private val position = FloatArray(capacity)
    private val rate = FloatArray(capacity)
    private val amp = FloatArray(capacity)
    private val panL = FloatArray(capacity)
    private val panR = FloatArray(capacity)

    /** 이 위치를 지나면 잦아들기 시작한다. */
    private val fadeFrom = FloatArray(capacity)
    private val fadeLen = FloatArray(capacity)

    /** 마찰음처럼 앞머리를 눕혀야 하는 그레인용. */
    private val attackFrames = IntArray(capacity)
    private val elapsed = IntArray(capacity)

    private var rngState = 0x7F4A_7C15
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
        // q와 resonance는 노이즈 합성 전용이라 파편 방식에서는 쓰지 않는다.
        if (bank.size == 0) return
        val slot = allocate(amplitude)
        if (slot < 0) return

        val index = bank.pick(freq, SPREAD, nextInt())
        val fragLen = bank.lengthOf(index)

        // 고른 파편의 밝기와 목표가 어긋난 만큼 재생 속도로 메운다.
        // 반음 셋을 넘기면 늘어지거나 쇳소리가 나서 티가 난다.
        val ratio = (freq / bank.centroidOf(index).coerceAtLeast(1f))
            .coerceIn(PITCH_MIN, PITCH_MAX)
        // 같은 파편이라도 매번 조금씩 다르게 들리도록 흔든다.
        val jitter = 1f + (nextFloat() * 2f - 1f) * 0.04f

        start[slot] = bank.offsetOf(index)
        length[slot] = fragLen
        position[slot] = 0f
        rate[slot] = ratio * jitter
        amp[slot] = amplitude
        delay[slot] = delayFrames.coerceAtLeast(0)
        elapsed[slot] = 0
        attackFrames[slot] = (sampleRate * attackMs * 0.001f).toInt().coerceAtLeast(1)

        // decayMs가 파편보다 짧으면 그만큼만 쓰고 부드럽게 끊는다.
        val wanted = (decayMs * 0.001f * sampleRate * rate[slot]).coerceAtLeast(64f)
        val usable = minOf(wanted, fragLen.toFloat())
        fadeLen[slot] = (usable * 0.35f).coerceIn(64f, 2400f)
        fadeFrom[slot] = (usable - fadeLen[slot]).coerceAtLeast(0f)

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
            val step = rate[g]
            val base = start[g]
            val len = length[g]
            val a = amp[g]
            val pl = panL[g]
            val pr = panR[g]
            val from = fadeFrom[g]
            val flen = fadeLen[g]
            val atk = attackFrames[g]
            var el = elapsed[g]

            while (i < frames) {
                val idx = pos.toInt()
                if (idx >= len - 1) { active[g] = false; activeCount--; break }

                // 선형 보간. 피치를 흔들어도 지직거리지 않게 한다.
                val frac = pos - idx
                val s0 = data[base + idx].toInt() / 32768f
                val s1 = data[base + idx + 1].toInt() / 32768f
                var s = s0 + (s1 - s0) * frac

                if (el < atk) s *= el.toFloat() / atk
                if (pos > from) {
                    val fade = 1f - (pos - from) / flen
                    if (fade <= 0f) { active[g] = false; activeCount--; break }
                    s *= fade
                }

                s *= a
                out[i * 2] += s * pl
                out[i * 2 + 1] += s * pr

                pos += step
                el++
                i++
            }

            position[g] = pos
            elapsed[g] = el
        }
    }

    override fun reset() {
        java.util.Arrays.fill(active, false)
        activeCount = 0
    }

    /** 빈 자리 우선, 없으면 가장 조용한 것을 뺏는다. */
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

    private fun nextFloat() = ((nextInt() ushr 8) and 0xFFFFFF) / 16777216f

    private companion object {
        /** 밝기 순 이웃 몇 개까지 후보로 볼지. 넓을수록 음색이 흩어진다. */
        const val SPREAD = 6

        /** 재생 속도 범위. 반음 셋 정도를 넘기면 티가 난다. */
        const val PITCH_MIN = 0.84f
        const val PITCH_MAX = 1.19f
    }
}
