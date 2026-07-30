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
    /** 실제 파열음 파편. 없으면 노이즈 합성으로 되돌아간다. */
    bank: GrainBank? = null,
    /** 재질별 동작 덩어리. 있으면 이걸 최우선으로 쓴다. */
    chunks: ChunkBank? = null,
) : EventQueue.Sink {

    private val chunkPool: ChunkPool? =
        if (chunks != null && chunks.size > 0) ChunkPool(chunks) else null

    private val pool: GrainSink = chunkPool
        ?: if (bank != null && bank.size > 0) SampleGrainPool(bank, 96, sampleRate)
        else GrainPool(capacity, sampleRate)

    /** 실제 파편을 쓰고 있는지. 로그와 테스트용. */
    val usingRecordedGrains: Boolean = pool is SampleGrainPool || pool is ChunkPool

    /** 덩어리 방식으로 도는지. */
    val usingChunks: Boolean = chunkPool != null

    /** 쥔 볼의 재질. 덩어리 방식에서만 뜻이 있다. */
    fun setMaterial(index: Int) {
        chunkPool?.material = index
    }

    /**
     * 켜면 파편을 뿌리지 않고 녹음을 통째로 튼다. 비교용이다.
     * 소리가 이상할 때 원인이 뿌리는 방식인지 원본 자체인지 가른다.
     */
    var raw: RawRecording? = null
    var useRaw = false
    private var profile = SoundProfile.hardWax()
    private var rngState = 0x2545_F491

    /**
     * 0~1. 설정의 음량 슬라이더가 조정한다.
     *
     * 리미터가 세게 걸리면 고조파가 생겨 소리가 지직거린다. 공명이 크고 감쇠가 긴
     * 프로파일에서 그레인이 겹쳐 쌓일 때 특히 심해서, 측정해 보니 중심 주파수가
     * 그레인 최고 주파수보다 높게 나올 지경이었다. 여유를 두고 낮게 잡는다.
     */
    var masterGain = 0.7f

    /**
     * 쥐고 있는 도구에 따른 음높이 배율. 쇠붙이는 밝게, 뭉툭한 것은 둔하게 들린다.
     * 파편을 고르는 기준 주파수에 곱해진다.
     */
    var toolBrightness = 1f

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
        if (useRaw) {
            // 원본 그대로 듣는 모드. 부수는 동안만 녹음이 흐른다.
            if (kind != EventKind.RUB) raw?.keepAlive()
            return
        }
        when (kind) {
            EventKind.CRACK -> crack(level, energy, pan, areaFrac)
            EventKind.DETACH -> detach(energy, pan, areaFrac)
            // 진짜 왁뿌볼은 고무풍선 껍질이 씌워져 있어서 깨진 조각이 그 안에 갇힌다.
            // 조각이 바닥에 떨어지지 않으니 착지음도 없다. 덩어리 안에 이미 조각들이
            // 안에서 부딪히는 소리까지 들어 있으므로 따로 낼 것이 없다.
            EventKind.LAND -> if (!usingChunks) land(pan, areaFrac)
            EventKind.RUB -> rub(energy, pan)
            EventKind.CORE -> core(energy, pan)
        }
    }

    /**
     * 금이 가는 소리. 세게 누를수록 그레인이 많아지고 촘촘해진다.
     * 단계가 올라갈수록(실금 → 쩍 갈라짐 → 들뜸) 저음이 붙고 길어진다.
     */
    private fun crack(level: Int, energy: Float, pan: Float, areaFrac: Float) {
        if (usingChunks) { crackFromChunk(energy, pan, level); return }
        if (usingRecordedGrains) { crackFromRecording(level, energy, pan, areaFrac); return }
        val e = energy.coerceIn(0f, 1f)
        val count = ((5f + 195f * e.pow(1.4f)) * profile.density * (0.6f + 0.18f * level)).toInt()
        // 세게 누를수록 파열이 촘촘해진다. 평균 간격 6ms → 1.2ms
        val meanGapMs = (6f - 4.8f * e) * profile.gapScale
        // 공명이 큰 재질은 그레인이 서로 더 잘 쌓이므로 진폭을 그만큼 덜어 낸다.
        val stacking = 1f / (1f + profile.resonance * 1.6f)
        val grainAmp = 0.055f * stacking * (0.35f + 0.65f * e) * (0.85f + 0.1f * level)
        val sizeShift = sizeShift(areaFrac)
        lastGrainCount = count
        lastMeanGapMs = meanGapMs

        // 왁스가 으스러질 때 깔리는 저역 몸통. 녹음에서 저역이 전체의 14~24%를 차지했는데
        // 이 층이 없으면 0%가 나오고, 파쇄음이 아니라 쉬익거리는 잡음처럼 들린다.
        if (profile.body > 0.01f) {
            repeat(if (level >= 2) 2 else 1) {
                pool.spawn(
                    delayFrames = msToFrames(exponentialGap(3f)),
                    freq = 90f + 170f * nextFloat(),
                    q = 2.2f,
                    decayMs = 70f + 80f * nextFloat(),
                    amplitude = 0.15f * profile.body * (0.4f + 0.6f * e),
                    pan = pan,
                    resonance = 0.45f,
                    attackMs = 2.5f,
                )
            }
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
     * 녹음 파편으로 낼 때의 크랙.
     *
     * 파편 하나가 이미 완성된 파열음이다. 노이즈 시절처럼 수십 개를 뿌리면
     * 파열이 겹겹이 쌓여 자갈이 쏟아지는 소리가 된다. 실제로 크랙 한 번에
     * 67개를 뿌리고 있었고, 그래서 공사장 콘크리트 깨는 소리처럼 들렸다.
     *
     * 세게 눌러도 두세 개까지만 겹친다. 그 이상은 왁스가 아니라 무더기가 된다.
     */
    /**
     * 덩어리 방식의 크랙.
     *
     * 덩어리 하나가 "쥐어서 쭉 찢어지는 동작" 전체다. 두 개를 겹치면 두 손으로
     * 동시에 쥐는 소리가 되므로 **한 번에 하나만** 튼다. 세게 누르면 크게 틀 뿐이다.
     *
     * 이미 울리는 중이면 새로 얹지 않는다. 문지르는 동안 프레임마다 얹으면
     * 덩어리 스무 개가 한꺼번에 울려 소리가 뭉갠다.
     */
    private fun crackFromChunk(energy: Float, pan: Float, level: Int) {
        val e = energy.coerceIn(0f, 1f)
        lastGrainCount = 1
        lastMeanGapMs = 0f
        if ((chunkPool?.activeCount ?: 0) >= MAX_CHUNKS_AT_ONCE) return

        pool.spawn(
            delayFrames = 0,
            freq = profile.baseFreq,
            q = profile.q,
            decayMs = 0f,
            amplitude = CHUNK_AMP * (0.5f + 0.5f * e) * (0.85f + 0.08f * level),
            pan = pan,
            resonance = profile.resonance,
        )
    }

    private fun crackFromRecording(level: Int, energy: Float, pan: Float, areaFrac: Float) {
        val e = energy.coerceIn(0f, 1f)
        val count = (1 + (e * 2.2f).toInt()).coerceIn(1, MAX_FRAGMENTS_PER_CRACK)
        val sizeShift = sizeShift(areaFrac)

        lastGrainCount = count
        lastMeanGapMs = FRAGMENT_GAP_MS

        var t = 0f
        repeat(count) {
            pool.spawn(
                delayFrames = msToFrames(t),
                freq = randomFreq() * sizeShift,
                q = profile.q,
                decayMs = FULL_FRAGMENT_MS,
                amplitude = FRAGMENT_AMP * (0.45f + 0.55f * e) * (0.85f + 0.08f * level),
                pan = pan + jitter01() * 0.05f,
                resonance = profile.resonance,
            )
            // 겹칠 때도 앞 파편이 어느 정도 지난 뒤에 놓는다. 붙여 놓으면 뭉친다.
            t += FRAGMENT_GAP_MS * jitter(0.4f)
        }
    }

    /**
     * 조각이 떨어져 나가는 순간. 큰 조각일수록 낮고 여운이 길다.
     * 작은 파열 무리가 뒤따라 부스러기가 흩어지는 느낌을 만든다.
     */
    /**
     * 녹음 파편으로 낼 때의 조각 분리.
     *
     * 큰 조각이 떨어지는 소리도 결국 파열음 하나다. 부스러기가 뒤따르는 느낌만
     * 두어 개로 덧붙인다. 노이즈 시절에는 잔파편을 서른 개까지 뿌렸는데,
     * 파편 방식에서 그러면 조각 하나 떨어질 때마다 무더기가 쏟아진다.
     */
    private fun detachFromRecording(energy: Float, pan: Float, areaFrac: Float) {
        val sizeShift = sizeShift(areaFrac)

        pool.spawn(
            delayFrames = 0,
            freq = profile.baseFreq * 0.8f * sizeShift,
            q = profile.q,
            decayMs = FULL_FRAGMENT_MS,
            amplitude = FRAGMENT_AMP * 1.3f * (0.5f + 0.5f * energy),
            pan = pan,
            resonance = profile.resonance,
        )

        // 넓은 판일수록 부스러기가 더 따라온다.
        val tail = if (areaFrac > 0.02f) 2 else 1
        var t = FRAGMENT_GAP_MS * 1.4f
        repeat(tail) {
            pool.spawn(
                delayFrames = msToFrames(t),
                freq = randomFreq() * sizeShift * 1.2f,
                q = profile.q,
                decayMs = FULL_FRAGMENT_MS,
                amplitude = FRAGMENT_AMP * 0.5f * jitter(0.3f),
                pan = pan + jitter01() * 0.15f,
                resonance = profile.resonance * 0.7f,
            )
            t += FRAGMENT_GAP_MS * jitter(0.5f)
        }
    }

    private fun detach(energy: Float, pan: Float, areaFrac: Float) {
        if (usingChunks) {
            // 조각이 떨어지는 것도 같은 동작의 일부다. 덩어리를 조금 더 크게 튼다.
            if ((chunkPool?.activeCount ?: 0) < MAX_CHUNKS_AT_ONCE) {
                pool.spawn(
                    delayFrames = 0,
                    freq = profile.baseFreq,
                    q = profile.q,
                    decayMs = 0f,
                    amplitude = CHUNK_AMP * 1.25f * (0.5f + 0.5f * energy),
                    pan = pan,
                    resonance = profile.resonance,
                )
            }
            return
        }
        if (usingRecordedGrains) { detachFromRecording(energy, pan, areaFrac); return }
        val sizeShift = sizeShift(areaFrac)
        val sizeBody = 1f + 8f * areaFrac.coerceIn(0f, 0.25f)

        pool.spawn(
            delayFrames = 0,
            freq = profile.baseFreq * 0.55f * sizeShift,
            q = profile.q * 1.6f,
            // 녹음에서 잰 파열 감쇠가 100~170ms였다. 큰 조각이라도 그 두 배를 넘으면
            // 왁스가 아니라 징 소리가 되고, 뒤따르는 부스러기 소리를 통째로 덮어버린다.
            decayMs = (profile.decayMsMax * 1.2f * sizeBody).coerceAtMost(260f),
            amplitude = 0.16f * (0.5f + 0.5f * energy),
            pan = pan,
            resonance = (profile.resonance * 1.9f).coerceAtMost(0.9f),
        )

        if (profile.body > 0.01f) {
            pool.spawn(
                delayFrames = 0,
                freq = 90f + 130f * nextFloat(),
                q = 2.0f,
                decayMs = (70f + 50f * sizeBody).coerceAtMost(200f),
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
        // 파편 하나가 이미 완성된 소리다. 착지마다 여러 개를 뿌리면 바닥이 소란스러워진다.
        val count = if (usingRecordedGrains) 1
        else 2 + (3f * (areaFrac / 0.02f).coerceIn(0f, 2f)).toInt()
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
        if (usingChunks) {
            // 잔여물을 쥘 때 안의 조각이 바스락거리는 소리. 문지름 이벤트는 프레임마다
            // 오므로, 올 때마다 얹으면 순식간에 덩어리가 쌓여 뭉갠다.
            // 조용할 때만 하나를, 파열보다 작게 튼다.
            if ((chunkPool?.activeCount ?: 0) > 0) return
            pool.spawn(
                delayFrames = 0,
                freq = profile.baseFreq,
                q = profile.q,
                decayMs = 0f,
                amplitude = CHUNK_AMP * 0.45f * (0.5f + 0.5f * s),
                pan = pan,
                resonance = profile.resonance,
            )
            return
        }
        // 문지르는 소리는 계속 나므로 파편 방식에서는 하나씩만 얹는다.
        val count = if (usingRecordedGrains) 1 else (2 + 10 * s).toInt()
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
        if (useRaw) raw?.render(out, frames, RAW_GAIN) else pool.render(out, frames)

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
        // 보정 폭이 크면 고역 그레인이 지나치게 짧아져 전체 감쇠가 녹음의 1/8까지 줄어든다.
        return sqrt(ratio).coerceIn(0.6f, 1.6f)
    }

    private companion object {
        /** 주파수 분포를 아래로 치우치게 하는 정도(옥타브 배율). */
        const val SKEW = 0.42f

        /**
         * 녹음 파편으로 낼 때 크랙 한 번에 겹치는 최대 개수.
         * 파편 하나가 이미 완성된 파열음이라, 이 이상 겹치면 무더기가 쏟아지는 소리가 된다.
         */
        const val MAX_FRAGMENTS_PER_CRACK = 3

        /** 겹칠 때 파편 사이 간격(ms). 붙여 놓으면 뭉친다. */
        const val FRAGMENT_GAP_MS = 26f

        /**
         * 파편 하나의 기본 진폭.
         *
         * 파편이 저마다 녹음에서 가지고 있던 크기를 그대로 들고 있다(전에는 하나하나
         * 최대로 키워 놨었다). 그래서 예전 값보다 올려야 같은 음량이 된다.
         * 측정값은 build/punch-report.txt를 본다.
         */
        const val FRAGMENT_AMP = 0.62f

        /** 파편을 끝까지 틀라는 뜻. 어떤 파편보다도 길다. */
        const val FULL_FRAGMENT_MS = 5000f

        /** 원본을 통째로 틀 때의 크기. 녹음이 이미 정규화돼 있어 그대로 두면 크다. */
        const val RAW_GAIN = 0.8f

        /**
         * 덩어리를 동시에 몇 개까지 울릴지.
         *
         * 덩어리 하나가 0.7초씩 울리는데 문지르는 동안 프레임마다 얹으면 스무 개가
         * 한꺼번에 울려 뭉갠다. 두세 개까지가 "여러 군데가 같이 찢어진다"로 들린다.
         */
        const val MAX_CHUNKS_AT_ONCE = 3

        /**
         * 덩어리 하나의 기본 크기.
         *
         * 소리가 작다는 지적에 0.55에서 올렸다. 겹칠 때는 리미터가 받아 준다.
         * 덩어리를 3초로 늘리고 덩어리마다 정규화했던 판은 셈여림이 죽어서 되돌렸다 —
         * 파편 시절 "전부 균일하게 시끄러워진다"와 같은 실수였다. 정규화는 재질 단위다.
         */
        const val CHUNK_AMP = 0.85f
    }

    private fun randomFreq(): Float {
        // 옥타브(로그축)에서 대칭으로 뿌리면 실제 스펙트럼 중심은 훨씬 위로 쏠린다.
        // 고역 성분이 중심 계산에서 더 크게 잡히기 때문이다.
        // 녹음과 중심 주파수를 맞추려면 아래쪽으로 치우쳐 뿌려야 한다.
        val octaves = (nextFloat() * 2f - 1f) * profile.freqSpread - profile.freqSpread * SKEW
        // 산포를 넓게 두면 그레인이 나이퀴스트 근처까지 밀려 올라가 고역만 98%가 되고
        // 중심 주파수가 설정값의 두 배 넘게 나온다. 실제 왁뿌볼 녹음의 에너지는
        // 대체로 이 대역 안에 있으므로 여기서 잘라 준다.
        return (profile.baseFreq * toolBrightness * 2f.pow(octaves)).coerceIn(150f, 9000f)
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
