package com.waxball.asmr.audio

import com.waxball.asmr.core.EventKind
import com.waxball.asmr.core.EventQueue
import com.waxball.asmr.core.SoundProfile
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * 파괴 이벤트를 소리로 바꾼다.
 *
 * 녹음 파일을 재생하지 않는다. 몇 번만 들어도 귀가 "아까 그 소리"라고 알아채는 순간
 * ASMR이 죽고, 손가락 힘에 따라 소리가 변할 수도 없기 때문이다.
 *
 * 대신 이벤트 하나마다 그레인 수십~수백 개를 지수분포 간격으로 흩뿌린다.
 * 간격이 균등하면 기계음으로 들린다. 불규칙해야 진짜 파쇄음이 된다.
 */
class Synth(
    private val sampleRate: Int,
    capacity: Int = 192,
) : EventQueue.Sink {

    private val pool = GrainPool(capacity, sampleRate)
    private var profile = SoundProfile.hardWax()
    private var rngState = 0x2545_F491

    /** 0~1. 설정의 음량 슬라이더가 조정한다. */
    var masterGain = 0.85f

    val activeGrains: Int get() = pool.activeCount

    /**
     * 마지막 크랙에서 예약한 그레인 수와 평균 간격.
     * 완성된 소리에서 초당 800회짜리 파열을 되짚어 세는 것은 신뢰할 수 없어서,
     * 스케줄러가 실제로 정한 값을 그대로 검증한다.
     */
    var lastGrainCount = 0
        private set
    var lastMeanGapMs = 0f
        private set

    fun setProfile(p: SoundProfile) {
        profile = p
    }

    fun reset() {
        pool.reset()
    }

    override fun on(kind: Int, shardId: Int, level: Int, energy: Float, pan: Float, areaFrac: Float) {
        when (kind) {
            EventKind.CRACK -> crack(level, energy, pan, areaFrac)
            EventKind.DETACH -> detach(energy, pan, areaFrac)
            EventKind.LAND -> land(pan, areaFrac)
            EventKind.RUB -> rub(energy, pan)
            EventKind.CORE -> core(energy, pan)
        }
    }

    /**
     * 금이 가는 소리. 세게 누를수록 그레인이 많아지고 촘촘해진다.
     * 단계가 올라갈수록(실금 → 쩍 갈라짐 → 들뜸) 저음이 붙고 길어진다.
     */
    private fun crack(level: Int, energy: Float, pan: Float, areaFrac: Float) {
        val e = energy.coerceIn(0f, 1f)
        val count = ((5f + 195f * e.pow(1.4f)) * profile.density * (0.6f + 0.18f * level)).toInt()
        // 세게 누를수록 파열이 촘촘해진다. 평균 간격 6ms → 1.2ms
        val meanGapMs = (6f - 4.8f * e) * profile.gapScale
        val grainAmp = 0.055f * (0.35f + 0.65f * e) * (0.85f + 0.1f * level)
        val sizeShift = sizeShift(areaFrac)
        lastGrainCount = count
        lastMeanGapMs = meanGapMs

        // 왁스가 으스러질 때 깔리는 저역 몸통. 이게 없으면 파쇄음이 아니라
        // 쉬익거리는 잡음처럼 들린다.
        if (profile.body > 0.01f && level >= 2) {
            pool.spawn(
                delayFrames = msToFrames(exponentialGap(2f)),
                freq = 110f + 190f * nextFloat(),
                q = 2.2f,
                decayMs = 45f + 55f * nextFloat(),
                amplitude = 0.075f * profile.body * (0.4f + 0.6f * e),
                pan = pan,
                resonance = 0.4f,
                attackMs = 2.5f,
            )
        }

        var t = 0f
        repeat(count) {
            t += exponentialGap(meanGapMs)
            val freq = randomFreq() * sizeShift
            pool.spawn(
                delayFrames = msToFrames(t),
                freq = freq,
                q = profile.q * jitter(0.25f),
                decayMs = randomDecay() * damping(freq) * (1f + 0.12f * level),
                amplitude = grainAmp * jitter(0.5f),
                pan = pan + jitter01() * 0.06f,
                resonance = profile.resonance * (0.5f + 0.2f * level),
            )
        }
    }

    /**
     * 조각이 떨어져 나가는 순간. 큰 조각일수록 낮고 여운이 길다.
     * 작은 파열 무리가 뒤따라 부스러기가 흩어지는 느낌을 만든다.
     */
    private fun detach(energy: Float, pan: Float, areaFrac: Float) {
        val sizeShift = sizeShift(areaFrac)
        val body = 1f + 8f * areaFrac.coerceIn(0f, 0.25f)

        pool.spawn(
            delayFrames = 0,
            freq = profile.baseFreq * 0.55f * sizeShift,
            q = profile.q * 1.6f,
            decayMs = profile.decayMsMax * 2.2f * body,
            amplitude = 0.16f * (0.5f + 0.5f * energy),
            pan = pan,
            resonance = (profile.resonance * 1.9f).coerceAtMost(0.9f),
        )

        if (profile.body > 0.01f) {
            pool.spawn(
                delayFrames = 0,
                freq = 90f + 130f * nextFloat(),
                q = 2.0f,
                decayMs = 90f + 90f * body,
                amplitude = 0.1f * profile.body,
                pan = pan,
                resonance = 0.45f,
                attackMs = 3f,
            )
        }

        val tail = (10f + 22f * profile.density).toInt()
        var t = 0f
        repeat(tail) {
            t += exponentialGap(4.5f)
            val freq = randomFreq() * sizeShift * 1.15f
            pool.spawn(
                delayFrames = msToFrames(t),
                freq = freq,
                q = profile.q * jitter(0.3f),
                decayMs = randomDecay() * damping(freq) * 0.7f,
                amplitude = 0.03f * jitter(0.6f),
                pan = pan + jitter01() * 0.12f,
                resonance = profile.resonance * 0.4f,
            )
        }
    }

    /** 떨어진 조각이 바닥에 닿는 소리. 작고 건조하게 계속 깔린다. */
    private fun land(pan: Float, areaFrac: Float) {
        val count = 2 + (3f * (areaFrac / 0.02f).coerceIn(0f, 2f)).toInt()
        var t = 0f
        repeat(count) {
            t += exponentialGap(3f)
            pool.spawn(
                delayFrames = msToFrames(t),
                freq = profile.baseFreq * 1.25f * profile.brightness * jitter(0.4f) * sizeShift(areaFrac),
                q = profile.q * 0.8f,
                decayMs = profile.decayMsMin * 0.8f,
                amplitude = 0.022f * jitter(0.5f),
                pan = pan + jitter01() * 0.2f,
                resonance = 0.15f,
            )
        }
    }

    /** 손가락이 표면을 스치는 마찰음. 빠르게 문지를수록 촘촘해진다. */
    private fun rub(speed: Float, pan: Float) {
        val s = speed.coerceIn(0f, 1f)
        val count = (2 + 10 * s).toInt()
        var t = 0f
        repeat(count) {
            t += exponentialGap(9f - 6f * s)
            pool.spawn(
                delayFrames = msToFrames(t),
                freq = profile.baseFreq * 1.6f * profile.brightness * jitter(0.5f),
                q = 2.2f,
                decayMs = 4f + 6f * nextFloat(),
                amplitude = 0.014f * (0.4f + 0.6f * s) * jitter(0.5f),
                pan = pan + jitter01() * 0.1f,
                resonance = 0.05f,
                attackMs = 1.6f,
            )
        }
    }

    /** 껍질을 다 벗긴 뒤 말랑이 코어를 누를 때. 크랙 없이 낮은 마찰음만 난다. */
    private fun core(amount: Float, pan: Float) {
        val a = amount.coerceIn(0f, 1f)
        val count = 3 + (6 * a).toInt()
        var t = 0f
        repeat(count) {
            t += exponentialGap(14f)
            pool.spawn(
                delayFrames = msToFrames(t),
                freq = 220f + 260f * nextFloat(),
                q = 4.5f,
                decayMs = 55f + 45f * nextFloat(),
                amplitude = 0.05f * (0.4f + 0.6f * a) * jitter(0.4f),
                pan = pan,
                resonance = 0.45f,
                attackMs = 7f,
            )
        }
    }

    /**
     * 스테레오 인터리브 버퍼를 채운다. 오디오 스레드에서만 호출한다.
     * 여기서는 어떤 객체도 만들지 않는다.
     */
    fun render(out: FloatArray, frames: Int) {
        java.util.Arrays.fill(out, 0, frames * 2, 0f)
        pool.render(out, frames)

        // 소프트 리미터. 그레인이 한꺼번에 몰려도 하드클립으로 찢어지지 않게 한다.
        val g = masterGain
        var i = 0
        val n = frames * 2
        while (i < n) {
            val x = out[i] * g
            out[i] = x / (1f + abs(x))
            i++
        }
    }

    /** 테스트·청취용 오프라인 렌더. 실시간 경로에서는 쓰지 않는다. */
    fun renderOffline(seconds: Float): FloatArray {
        val frames = (seconds * sampleRate).toInt()
        val result = FloatArray(frames * 2)
        val chunk = FloatArray(512 * 2)
        var done = 0
        while (done < frames) {
            val n = minOf(512, frames - done)
            render(chunk, n)
            System.arraycopy(chunk, 0, result, done * 2, n * 2)
            done += n
        }
        return result
    }

    // --- 잡다한 수치 도우미 ---

    private fun sizeShift(areaFrac: Float) = 1f / (1f + 6f * areaFrac.coerceIn(0f, 0.3f))

    /**
     * 점탄성 감쇠. 왁스 같은 무른 재질에서는 고주파 성분이 먼저 급격히 사라지고
     * 저주파만 남는다. 감쇠 시간을 주파수와 무관하게 두면 파쇄음이 아니라
     * 잡음 뭉치처럼 들린다.
     */
    internal fun damping(freq: Float): Float {
        if (freq <= 1f) return 1f
        val ratio = profile.baseFreq / freq
        return sqrt(ratio).coerceIn(0.35f, 2.2f)
    }

    private fun randomFreq(): Float {
        // 중심 주파수 주변으로 옥타브 단위로 흩뿌린다.
        val octaves = (nextFloat() * 2f - 1f) * profile.freqSpread
        return profile.baseFreq * 2f.pow(octaves)
    }

    private fun randomDecay() =
        profile.decayMsMin + (profile.decayMsMax - profile.decayMsMin) * nextFloat()

    /** 지수분포 간격(ms). 균등 간격이면 기계음으로 들린다. */
    private fun exponentialGap(meanMs: Float): Float {
        val u = nextFloat().coerceIn(1e-6f, 1f)
        return -meanMs.coerceAtLeast(0.2f) * ln(u)
    }

    private fun msToFrames(ms: Float) = (ms * 0.001f * sampleRate).toInt()

    /** 1을 중심으로 ±spread 비율만큼 흔든다. */
    private fun jitter(spread: Float) = 1f + (nextFloat() * 2f - 1f) * spread

    private fun jitter01() = nextFloat() * 2f - 1f

    private fun nextFloat(): Float {
        var n = rngState
        n = n xor (n shl 13); n = n xor (n ushr 17); n = n xor (n shl 5)
        rngState = n
        return ((n ushr 8) and 0xFFFFFF) / 16777216f
    }
}
