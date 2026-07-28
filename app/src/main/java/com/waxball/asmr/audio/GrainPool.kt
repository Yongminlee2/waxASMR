package com.waxball.asmr.audio

import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/**
 * 크랙 그레인의 고정 크기 풀.
 *
 * 그레인 하나 = 대역통과된 노이즈 순간 폭발 + 짧게 울리는 공명음.
 * 왁스가 깨질 때 나는 소리는 한 번의 "빵"이 아니라 이런 미세 파열 수백 개가
 * 불규칙하게 연달아 터지는 소리다. 그래서 "빠자자작"으로 들린다.
 *
 * 오디오 스레드가 매 버퍼마다 도는 코드이므로 객체를 하나도 만들지 않는다.
 * 그레인 상태는 전부 병렬 원시 배열에 들어 있다.
 */
class GrainPool(override val capacity: Int, private val sampleRate: Int) : GrainSink {

    private val active = BooleanArray(capacity)
    private val delay = IntArray(capacity)      // 시작까지 남은 프레임
    private val attack = IntArray(capacity)     // 어택 프레임 수
    private val elapsed = IntArray(capacity)
    private val amp = FloatArray(capacity)
    private val env = FloatArray(capacity)
    private val decayCoef = FloatArray(capacity)
    private val panL = FloatArray(capacity)
    private val panR = FloatArray(capacity)

    // 밴드패스 계수와 상태
    private val b0 = FloatArray(capacity)
    private val b2 = FloatArray(capacity)
    private val a1 = FloatArray(capacity)
    private val a2 = FloatArray(capacity)
    private val x1 = FloatArray(capacity)
    private val x2 = FloatArray(capacity)
    private val y1 = FloatArray(capacity)
    private val y2 = FloatArray(capacity)

    // 공명 사인. sin() 호출 없이 2탭 재귀로 만든다.
    private val resMix = FloatArray(capacity)
    private val resC = FloatArray(capacity)
    private val resY1 = FloatArray(capacity)
    private val resY2 = FloatArray(capacity)

    private var noiseState = 0x1234_5678
    private var cursor = 0

    override var activeCount = 0
        private set

    /**
     * 그레인 하나를 예약한다. 빈 슬롯이 없으면 가장 조용한 슬롯을 뺏는다.
     * 소리가 끊기는 것보다 약한 그레인 하나를 잃는 편이 낫다.
     */
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
        val slot = allocate(amplitude)
        if (slot < 0) return

        val nyquist = sampleRate * 0.5f
        val f = freq.coerceIn(40f, nyquist * 0.92f)
        val w = 2f * Math.PI.toFloat() * f / sampleRate
        val alpha = sin(w) / (2f * q.coerceAtLeast(0.5f))
        val a0 = 1f + alpha

        // RBJ 쿡북 밴드패스(정점 이득 = Q). b1은 항상 0이라 저장하지 않는다.
        b0[slot] = alpha / a0
        b2[slot] = -alpha / a0
        a1[slot] = -2f * cos(w) / a0
        a2[slot] = (1f - alpha) / a0
        x1[slot] = 0f; x2[slot] = 0f; y1[slot] = 0f; y2[slot] = 0f

        val decaySamples = (decayMs * 0.001f * sampleRate).coerceAtLeast(4f)
        decayCoef[slot] = exp(-6.9f / decaySamples)   // 감쇠 시간 끝에서 -60dB

        // 크랙은 어택 1ms 이하. 딱 하고 서는 앞머리가 파쇄음의 정체다.
        // 반대로 마찰음은 어택을 길게 줘야 클릭이 안 섞이고 둔하게 들린다.
        attack[slot] = (sampleRate * attackMs * 0.001f).toInt().coerceAtLeast(1)
        elapsed[slot] = 0
        env[slot] = 0f
        delay[slot] = delayFrames.coerceAtLeast(0)
        amp[slot] = amplitude

        val angle = (pan.coerceIn(-1f, 1f) + 1f) * (Math.PI.toFloat() / 4f)
        panL[slot] = cos(angle)
        panR[slot] = sin(angle)

        resMix[slot] = resonance.coerceIn(0f, 1f)
        resC[slot] = 2f * cos(w)
        resY1[slot] = sin(w)
        resY2[slot] = 0f

        active[slot] = true
        activeCount++
    }

    /** out에 스테레오 인터리브로 더한다. out은 호출자가 미리 0으로 채운다. */
    override fun render(out: FloatArray, frames: Int) {
        for (g in 0 until capacity) {
            if (!active[g]) continue

            var start = 0
            if (delay[g] > 0) {
                val skip = if (delay[g] < frames) delay[g] else frames
                delay[g] -= skip
                start = skip
                if (start >= frames) continue
            }

            var e = env[g]
            var el = elapsed[g]
            val atk = attack[g]
            val dc = decayCoef[g]
            val a = amp[g]
            val pl = panL[g]
            val pr = panR[g]
            val rm = resMix[g]
            val nm = 1f - rm

            val vb0 = b0[g]; val vb2 = b2[g]; val va1 = a1[g]; val va2 = a2[g]
            var vx1 = x1[g]; var vx2 = x2[g]; var vy1 = y1[g]; var vy2 = y2[g]
            val rc = resC[g]; var r1 = resY1[g]; var r2 = resY2[g]

            var i = start
            while (i < frames) {
                // xorshift 노이즈. java.util.Random은 동기화 비용이 있어 쓰지 않는다.
                var n = noiseState
                n = n xor (n shl 13); n = n xor (n ushr 17); n = n xor (n shl 5)
                noiseState = n
                val noise = n * 4.6566129e-10f   // -1..1

                val bp = vb0 * noise + vb2 * vx2 - va1 * vy1 - va2 * vy2
                vx2 = vx1; vx1 = noise
                vy2 = vy1; vy1 = bp

                val res = rc * r1 - r2
                r2 = r1; r1 = res

                e = if (el < atk) (el + 1).toFloat() / atk else e * dc
                el++

                val s = (bp * nm + res * rm) * e * a
                out[i * 2] += s * pl
                out[i * 2 + 1] += s * pr

                if (el > atk && e < 1e-5f) {
                    active[g] = false
                    activeCount--
                    break
                }
                i++
            }

            env[g] = e; elapsed[g] = el
            x1[g] = vx1; x2[g] = vx2; y1[g] = vy1; y2[g] = vy2
            resY1[g] = r1; resY2[g] = r2
        }
    }

    override fun reset() {
        java.util.Arrays.fill(active, false)
        activeCount = 0
    }

    /** 빈 슬롯 우선, 없으면 진행이 많이 된 것 중 가장 조용한 슬롯을 재사용한다. */
    private fun allocate(newAmp: Float): Int {
        for (n in 0 until capacity) {
            val i = cursor
            cursor = if (cursor + 1 == capacity) 0 else cursor + 1
            if (!active[i]) return i
        }
        var weakest = -1
        var weakestLevel = Float.MAX_VALUE
        for (i in 0 until capacity) {
            val level = env[i] * amp[i]
            if (level < weakestLevel) { weakestLevel = level; weakest = i }
        }
        if (weakest >= 0 && weakestLevel < newAmp) {
            activeCount--
            return weakest
        }
        return -1
    }
}
