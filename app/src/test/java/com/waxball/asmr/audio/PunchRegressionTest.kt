package com.waxball.asmr.audio

import com.waxball.asmr.core.EventKind
import com.waxball.asmr.core.SoundProfile
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 소리의 "시원함"과 "깊이감"이 무너지지 않도록 잠가 둔다.
 *
 * 같은 종류의 앱이 업데이트로 소리를 바꿨다가 "터뜨리는 맛이 없어졌다",
 * "시원한 소리가 없다", "깊이감이 없어졌다"는 평을 받은 사례가 있다.
 * 소리는 코드를 조금만 건드려도 조용히 납작해지는데, 귀로만 확인하면 알아채기 어렵다.
 *
 * - 시원함 = 크레스트(피크 ÷ 실효값). 순간적으로 확 솟는 정도
 * - 깊이감 = 저역 비중. 많다고 좋은 게 아니다. 과하면 웅웅거려 오히려 시원함이 죽는다
 *
 * 기준은 실제 왁뿌볼 녹음이다. 크레스트 13.8, 저역 12%.
 */
class PunchRegressionTest {

    private val sr = Audition.SAMPLE_RATE

    private fun crestOf(stereo: FloatArray): Float {
        val mono = Spectrum.left(stereo)
        var peak = 0f
        var sum = 0.0
        for (v in mono) {
            val a = abs(v)
            if (a > peak) peak = a
            sum += v.toDouble() * v
        }
        val rms = sqrt(sum / mono.size).toFloat()
        return if (rms <= 1e-9f) 0f else peak / rms
    }

    private fun lowRatioOf(stereo: FloatArray): Float =
        ReferenceAnalyzer.analyze(LoadedWav(Spectrum.left(stereo), sr, 1)).lowRatio

    @Test
    fun everyMaterialKeepsItsPunch() {
        // 납작해지면 "터뜨리는 맛"이 사라진다. 녹음이 13.8이므로 그 언저리는 지켜야 한다.
        for ((name, profile) in materials()) {
            val crest = crestOf(Audition.playSession(profile, 6f, 7))
            assertTrue("$name 소리가 납작함: 크레스트 %.1f".format(crest), crest >= 10f)
        }
    }

    @Test
    fun lowEndStaysInRange() {
        // 저역이 없으면 얇고, 과하면 웅웅거려 시원함이 죽는다. 녹음은 12%다.
        for ((name, profile) in materials()) {
            val low = lowRatioOf(Audition.playSession(profile, 6f, 7))
            assertTrue("$name 저역이 과함: %.0f%%".format(low * 100), low <= 0.30f)
            assertTrue("$name 저역이 너무 얇음: %.0f%%".format(low * 100), low >= 0.03f)
        }
    }

    @Test
    fun aBigShardComingOffHitsHardest() {
        // 넓은 판이 떨어지는 순간이 이 앱에서 가장 시원해야 하는 지점이다.
        val synth = Synth(sr).apply { setProfile(SoundProfile.hardWax()) }
        synth.on(EventKind.DETACH, 0, 4, 1f, 0f, 0.18f)
        val crest = crestOf(synth.renderOffline(1f))
        assertTrue("큰 조각이 떨어지는데 밋밋함: 크레스트 %.1f".format(crest), crest >= 12f)
    }

    @Test
    fun softWaxIsDeeperThanSugarGlass() {
        // 재질별 성격은 유지돼야 한다. 무른 왁스가 설탕유리보다 저역이 많아야 맞다.
        val soft = lowRatioOf(Audition.playSession(SoundProfile.softWax(), 5f, 7))
        val glass = lowRatioOf(Audition.playSession(SoundProfile.sugarGlass(), 5f, 7))
        assertTrue("무른 왁스가 설탕유리보다 얇음 (%.0f%% vs %.0f%%)".format(soft * 100, glass * 100), soft > glass)
    }

    private fun materials() = listOf(
        "굳은왁스" to SoundProfile.hardWax(),
        "무른왁스" to SoundProfile.softWax(),
        "반짝이" to SoundProfile.glitter(),
        "알갱이" to SoundProfile.crunchBeads(),
        "설탕유리" to SoundProfile.sugarGlass(),
    )
}
