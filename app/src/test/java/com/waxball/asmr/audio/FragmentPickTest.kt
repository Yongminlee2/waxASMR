package com.waxball.asmr.audio

import com.waxball.asmr.core.SoundProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 파편을 어떤 분포로 고르는지 잰다.
 *
 * 파편은 이미 녹음의 스펙트럼을 갖고 있다. 그런데 고르는 기준이 노이즈 합성 시절의
 * 치우침(SKEW)을 그대로 쓰고 있어서, 어두운 파편만 계속 뽑혀 원본보다 둔하게 들렸다.
 * 눈으로도 귀로도 안 보이는 문제라 숫자로 잡는다.
 */
class FragmentPickTest {

    /** 밝기가 고르게 퍼진 가짜 뱅크. 실제 뱅크가 없어도 분포를 검증할 수 있다. */
    private fun evenBank(n: Int): GrainBank {
        val len = 64
        val offsets = IntArray(n) { it * len }
        val lengths = IntArray(n) { len }
        val centroids = FloatArray(n) { 1000f + it * (5000f / n) }
        return GrainBank.of(ShortArray(n * len), offsets, lengths, centroids)
    }

    private fun medianCentroid(bank: GrainBank, rank: Float, spread: Float, draws: Int = 4000): Float {
        var seed = 12345
        val picked = FloatArray(draws)
        for (i in 0 until draws) {
            seed = seed xor (seed shl 13); seed = seed xor (seed ushr 17); seed = seed xor (seed shl 5)
            picked[i] = bank.centroidOf(bank.pickByRank(rank, spread, seed))
        }
        picked.sort()
        return picked[draws / 2]
    }

    private fun bankMedian(bank: GrainBank): Float {
        val all = FloatArray(bank.size) { bank.centroidOf(it) }
        all.sort()
        return all[bank.size / 2]
    }

    @Test
    fun pickingTheWholeBankMatchesTheBankItself() {
        val bank = evenBank(300)
        val expected = bankMedian(bank)
        val actual = medianCentroid(bank, rank = 0.5f, spread = 0.5f)
        assertEquals("고르게 뽑았는데 뱅크 중앙값과 다름", expected, actual, expected * 0.1f)
    }

    @Test
    fun aLowerRankPicksDarkerFragments() {
        val bank = evenBank(300)
        val dark = medianCentroid(bank, rank = 0.2f, spread = 0.15f)
        val bright = medianCentroid(bank, rank = 0.8f, spread = 0.15f)
        assertTrue("순위를 낮췄는데 더 밝게 뽑힘 ($dark vs $bright)", dark < bright)
    }

    @Test
    fun everyRankStaysInsideTheBank() {
        val bank = evenBank(120)
        for (step in 0..20) {
            val index = bank.pickByRank(step / 20f, 0.35f, step * 7919)
            assertTrue("뱅크 밖 번호가 나옴: $index", index in 0 until bank.size)
        }
    }

    @Test
    fun extremeArgumentsDoNotCrash() {
        val bank = evenBank(50)
        assertTrue(bank.pickByRank(-5f, -1f, 0) in 0 until bank.size)
        assertTrue(bank.pickByRank(9f, 9f, -1) in 0 until bank.size)
    }

    @Test
    fun anEmptyBankIsSafe() {
        val empty = GrainBank.of(ShortArray(0), IntArray(0), IntArray(0), FloatArray(0))
        assertEquals(0, empty.pickByRank(0.5f, 0.35f, 1))
    }

    /**
     * 실제 뱅크로 두 방식을 나란히 재서 보고서에 남긴다.
     * 바꾼 것이 실제로 효과가 있었는지는 이 숫자로만 알 수 있다.
     */
    @Test
    fun compareOldAndNewPickingOnTheRealBank() {
        val bank = TestGrainBank.load() ?: return
        val lines = StringBuilder()

        val all = FloatArray(bank.size) { bank.centroidOf(it) }
        all.sort()
        val bankMid = all[bank.size / 2]
        lines.appendLine(
            "뱅크 %d개 · 밝기 중앙 %.0fHz (%.0f~%.0f)".format(
                bank.size, bankMid, all.first(), all.last()
            )
        )

        val profile = SoundProfile.hardWax()
        val synth = Synth(Audition.SAMPLE_RATE, bank = bank).apply { setProfile(profile) }

        // 예전 방식: 절대 Hz 목표로 고른다
        var seed = 999
        val old = FloatArray(4000)
        for (i in old.indices) {
            seed = seed xor (seed shl 13); seed = seed xor (seed ushr 17); seed = seed xor (seed shl 5)
            val unit = ((seed ushr 8) and 0xFFFFFF) / 16777216f
            val octaves = (unit * 2f - 1f) * profile.freqSpread - profile.freqSpread * 0.42f
            val hz = (profile.baseFreq * Math.pow(2.0, octaves.toDouble()).toFloat())
                .coerceIn(150f, 9000f)
            old[i] = bank.centroidOf(bank.pick(hz, 6, seed))
        }
        old.sort()
        lines.appendLine("예전(절대 Hz + SKEW) 중앙 %.0fHz".format(old[old.size / 2]))

        val new = medianCentroid(bank, profile.brightnessRank, 0.35f)
        lines.appendLine("지금(밝기 순위)      중앙 %.0fHz".format(new))
        lines.appendLine("합성기가 파편 방식으로 도는가: ${synth.usingRecordedGrains}")

        File("build/pick-report.txt").apply { parentFile?.mkdirs(); writeText(lines.toString()) }

        assertEquals("순위 방식이 뱅크 분포를 안 따라감", bankMid, new, bankMid * 0.2f)
    }
}
