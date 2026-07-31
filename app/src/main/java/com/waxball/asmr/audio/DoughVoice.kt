package com.waxball.asmr.audio

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/**
 * 밀가루 반죽을 치대는 소리를 합성한다.
 *
 * 껍질을 다 부수고 색까지 다 섞이면 남는 것은 말랑한 반죽 덩어리다. 거기서
 * 왁스 깨지는 파열음이 나면 거짓말이고, 반죽을 치대는 녹음은 따로 없다. 그래서 만든다.
 *
 * 반죽 소리는 파열음과 정반대다 — 파열은 짧고 밝고 뾰족한 것이 수백 개 터지는
 * 소리인 반면, 반죽은 축축하고 둔하고 뭉근하게 이어진다. 셋을 겹친다:
 *
 * 1. **눅진한 몸통** — 저역만 남긴 잡음. 손이 움직이는 만큼 커진다. 이것만으로는
 *    바람 소리라서, 느리게 오르내리는 컷오프로 질척이는 결을 만든다.
 * 2. **공기 방울** — 반죽에 갇힌 공기가 터지는 짧고 낮은 "뽁". 간격이 일정하면
 *    초침처럼 들려서, 활동에 따라 촘촘해지되 매번 다르게 띄운다.
 * 3. **늘어나는 결** — 붙었다 떨어지는 끈끈한 소리. 천천히 부풀었다 잦아든다.
 *
 * 오디오 스레드에서 도는 코드라 객체를 만들지 않는다.
 */
class DoughVoice(private val sampleRate: Int) {

    /** 0~1. 손이 주무르는 정도. 소리 크기와 사건 빈도를 함께 정한다. */
    @Volatile var activity = 0f

    // 손 입력은 프레임마다 튄다. 그대로 쓰면 지직거려서 완만하게 따라간다.
    private var level = 0f

    // --- 눅진한 몸통: 1극 저역통과 세 단 ---
    private var lp1 = 0f
    private var lp2 = 0f
    private var lp3 = 0f
    private var cutoffPhase = 0f

    /** 출력단 저역통과. 방울·결의 고역 꼬리까지 한 번에 눌러 준다. */
    private var outLp = 0f

    // --- 공기 방울: 한 번에 하나. 낮은 사인이 빠르게 잦아든다 ---
    private var popEnv = 0f
    private var popDecay = 0f
    private var popY1 = 0f
    private var popY2 = 0f
    private var popC = 0f
    private var nextPop = 0

    // --- 늘어나는 결: 중역 잡음이 부풀었다 꺼진다 ---
    private var pullPhase = 1f
    private var pullRate = 0f
    private var pullBp1 = 0f
    private var pullBp2 = 0f
    private var nextPull = 0

    private var noiseState = 0x51F3_A2C7.toInt()
    private var panPhase = 0f

    fun reset() {
        level = 0f
        lp1 = 0f; lp2 = 0f; lp3 = 0f; outLp = 0f
        popEnv = 0f; popY1 = 0f; popY2 = 0f
        pullPhase = 1f
        nextPop = 0; nextPull = 0
    }

    /** out(스테레오 인터리브)에 더한다. */
    fun render(out: FloatArray, frames: Int, gain: Float) {
        val target = activity.coerceIn(0f, 1f)
        // 손을 멈추면 서서히 잦아든다. 뚝 끊기면 반죽이 아니라 스위치처럼 들린다.
        val follow = if (target > level) RISE else FALL

        for (i in 0 until frames) {
            level += (target - level) * follow
            if (level < 1e-4f && popEnv < 1e-4f && pullPhase >= 1f) continue

            // 1) 눅진한 몸통. 컷오프가 느리게 오르내려 질척이는 결이 생긴다.
            cutoffPhase += CUTOFF_RATE / sampleRate
            if (cutoffPhase >= 1f) cutoffPhase -= 1f
            val wobble = 0.5f + 0.5f * lfo(cutoffPhase)
            val cutoff = BODY_CUTOFF_LOW + (BODY_CUTOFF_HIGH - BODY_CUTOFF_LOW) * wobble
            val k = (cutoff / sampleRate).coerceIn(0.002f, 0.4f)
            lp1 += (noise() - lp1) * k
            lp2 += (lp1 - lp2) * k
            lp3 += (lp2 - lp3) * k
            var sample = lp3 * BODY_AMP * level

            // 2) 공기 방울
            if (nextPop <= 0) {
                if (level > 0.05f) spawnPop()
                val mean = POP_GAP_SLOW - (POP_GAP_SLOW - POP_GAP_FAST) * level
                nextPop = ((mean * (0.35f + 1.3f * unit())) * sampleRate).toInt().coerceAtLeast(1)
            }
            nextPop--
            if (popEnv > 1e-4f) {
                // 2탭 재귀 사인. 매 샘플 sin() 을 부르지 않는다.
                val y = popC * popY1 - popY2
                popY2 = popY1
                popY1 = y
                sample += y * popEnv * POP_AMP * (0.3f + 0.7f * level)
                popEnv *= popDecay
            }

            // 3) 늘어나는 결
            if (nextPull <= 0) {
                if (level > 0.12f) {
                    pullPhase = 0f
                    pullRate = 1f / (sampleRate * (PULL_LEN_MIN + PULL_LEN_SPAN * unit()))
                }
                val mean = PULL_GAP_SLOW - (PULL_GAP_SLOW - PULL_GAP_FAST) * level
                nextPull = ((mean * (0.5f + unit())) * sampleRate).toInt().coerceAtLeast(1)
            }
            nextPull--
            if (pullPhase < 1f) {
                pullPhase += pullRate
                // 저역통과 두 개의 차이 = 중역만 남는 대역통과
                val n = noise()
                pullBp1 += (n - pullBp1) * PULL_HI
                pullBp2 += (pullBp1 - pullBp2) * PULL_LO
                // 0→1 위상을 반주기 사인으로 감싸 부풀었다 꺼지게 한다.
                val env = lfo(pullPhase * 0.5f)
                sample += (pullBp1 - pullBp2) * env * PULL_AMP * (0.3f + 0.7f * level)
            }

            // 방울과 결이 남긴 고역 꼬리를 마지막으로 눌러 준다. 이게 없으면
            // 아무리 저역을 깔아도 전체가 "쉬익" 하고 밝게 들린다.
            outLp += (sample - outLp) * OUT_CUTOFF / sampleRate
            sample = outLp

            // 손이 어느 쪽에 있는지까지 흉내 낼 필요는 없다. 아주 느리게 좌우로 흔든다.
            panPhase += PAN_RATE / sampleRate
            if (panPhase >= 1f) panPhase -= 1f
            val pan = lfo(panPhase) * 0.25f
            val s = sample * gain
            out[i * 2] += s * (1f - pan)
            out[i * 2 + 1] += s * (1f + pan)
        }
    }

    /**
     * 방울 하나를 띄운다. 사건마다 한 번뿐이라 여기서는 진짜 sin/cos 을 쓴다 —
     * 근사식은 작은 각도에서 오차가 커서 재귀 사인의 음높이가 어긋난다.
     */
    private fun spawnPop() {
        val freq = POP_FREQ_LOW + (POP_FREQ_HIGH - POP_FREQ_LOW) * unit()
        val w = 2.0 * Math.PI * freq / sampleRate
        popC = (2.0 * cos(w)).toFloat()
        popY1 = 0f
        popY2 = (-sin(w)).toFloat()
        popEnv = 0.5f + 0.5f * unit()
        popDecay = exp(-1f / (sampleRate * (POP_DECAY_MIN + POP_DECAY_SPAN * unit())))
    }

    /** -1~1 균등 잡음. xorshift라 주기가 길어 규칙이 안 들린다. */
    private fun noise(): Float {
        var n = noiseState
        n = n xor (n shl 13); n = n xor (n ushr 17); n = n xor (n shl 5)
        noiseState = n
        return ((n ushr 8) and 0xFFFFFF) / 8388608f - 1f
    }

    private fun unit(): Float = abs(noise())

    /**
     * 0~1 위상 → sin(2πx) 포물선 근사.
     * 느린 흔들림과 포락선에만 쓴다. 파형 모양이 조금 달라도 들리지 않는다.
     */
    private fun lfo(x: Float): Float {
        var p = x - x.toInt()
        if (p < 0f) p += 1f
        val t = 2f * p - 1f
        return -4f * t * (1f - abs(t))
    }

    private companion object {
        /** 손을 대면 빨리 차오르고, 떼면 천천히 잦아든다. */
        const val RISE = 0.004f
        const val FALL = 0.0015f

        /** 몸통 컷오프가 오가는 범위(Hz). 위로 열면 바람 소리가 된다. */
        const val BODY_CUTOFF_LOW = 150f
        const val BODY_CUTOFF_HIGH = 520f

        /** 컷오프가 오르내리는 속도(Hz). 빠르면 떨림처럼 들린다. */
        const val CUTOFF_RATE = 0.7f
        const val BODY_AMP = 5.5f

        /**
         * 출력단 저역통과 컷오프(Hz). 밝기 측정이 크기 가중이라, 감쇠가 완만하면
         * 아주 작은 고역 꼬리도 중심 주파수를 수천 Hz까지 끌어올린다.
         */
        const val OUT_CUTOFF = 700f

        /** 방울 음높이. 낮아야 "뽁"이지 "딱"이 아니다. */
        const val POP_FREQ_LOW = 90f
        const val POP_FREQ_HIGH = 260f
        const val POP_DECAY_MIN = 0.012f
        const val POP_DECAY_SPAN = 0.045f
        const val POP_AMP = 0.55f

        /** 방울 사이 평균 간격(초). 가만히 있으면 느리게, 주무르면 촘촘하게. */
        const val POP_GAP_SLOW = 0.55f
        const val POP_GAP_FAST = 0.09f

        const val PULL_LEN_MIN = 0.18f
        const val PULL_LEN_SPAN = 0.25f
        const val PULL_AMP = 0.7f

        /**
         * 늘어나는 결의 대역. 두 저역통과의 차이로 만드는데, 위쪽 계수가 크면
         * 거의 백색잡음이 그대로 남아 "쉬익" 하는 바람 소리가 된다.
         * 0.35로 뒀다가 중심 주파수가 7.5kHz까지 올라갔다. 대략 60~380Hz로 잡는다.
         */
        const val PULL_HI = 0.03f
        const val PULL_LO = 0.006f
        const val PULL_GAP_SLOW = 1.6f
        const val PULL_GAP_FAST = 0.45f

        const val PAN_RATE = 0.15f
    }
}
