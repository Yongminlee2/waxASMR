package com.waxball.asmr.core

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * 도구별로 한 번 썼을 때 얼마나 깨지는지 잰다.
 *
 * 도구를 바꿔도 결과가 비슷하면 고르는 의미가 없고, 반대로 한 방에 다 부서지면
 * 만질 거리가 사라진다. 숫자로 확인해 둔다.
 */
class WeaponBalanceTest {

    private fun freshModel() = BreakModel(
        ShardSplitter.split(Icosphere.build(4), 150, Random(3)),
        SoundProfile.hardWax(),
        EventQueue(16384),
    )

    /** 도구를 한 번 쓴다. 찍는 도구는 한 방, 문지르는 도구는 0.25초. */
    private fun singleUse(w: Weapon): Float {
        val m = freshModel()
        val hit = m.shards.shards[0].center
        if (w.continuous) {
            repeat(15) { m.pressArea(hit, w.contactCos, 2.5f * w.forceScale, 0.016f, 0f) }
        } else {
            m.strikeArea(hit, w.contactCos, w.strikeDamage, 0f)
        }
        return m.shellProgress
    }

    @Test
    fun weaponsProduceDistinctResults() {
        val results = Weapon.entries.associateWith { singleUse(it) }
        val values = results.values.sorted()
        assertTrue(
            "도구를 바꿔도 결과가 비슷하다: $results",
            values.last() > values.first() * 2.5f,
        )
    }

    @Test
    fun noWeaponClearsTheBallInOneUse() {
        // 한 방에 다 부서지면 만질 거리가 없다.
        for (w in Weapon.entries) {
            val progress = singleUse(w)
            assertTrue("${w.labelKo} 한 번에 ${(progress * 100).toInt()}% 가 날아감", progress < 0.35f)
        }
    }

    @Test
    fun everyWeaponActuallyBreaksSomething() {
        for (w in Weapon.entries) {
            assertTrue("${w.labelKo} 로는 아무것도 안 깨진다", singleUse(w) > 0f)
        }
    }

    @Test
    fun hammerHitsHarderThanFinger() {
        assertTrue(singleUse(Weapon.HAMMER) > singleUse(Weapon.FINGER))
    }

    @Test
    fun weaponsDifferInHowWideTheyReach() {
        // "정밀하다"는 건 닿는 넓이의 문제다. 한 프레임에 몇 개 깨지느냐로 재면
        // 어느 도구도 임계를 못 넘어서 전부 0이 나온다.
        val m = freshModel()
        val hit = m.shards.shards[0].center

        fun reach(w: Weapon) = m.shards.shards.count { (hit dot it.center) >= w.contactCos }

        val nail = reach(Weapon.NAIL)
        val finger = reach(Weapon.FINGER)
        val fist = reach(Weapon.FIST)

        assertTrue("손톱이 손가락보다 넓게 닿는다 (손톱 $nail, 손가락 $finger)", nail < finger)
        assertTrue("손가락이 주먹보다 넓게 닿는다 (손가락 $finger, 주먹 $fist)", finger < fist)
        assertTrue("손톱이 조각 하나도 못 건드린다", nail >= 1)
    }

    @Test
    fun impactScaleDoesNotSaturateOnSmallChips() {
        // 작은 부스러기 하나에도 화면이 최대로 흔들리면 금방 피로해진다.
        val smallChipArea = 0.002f
        for (w in Weapon.entries) {
            val magnitude = ((smallChipArea / 0.03f) * w.impactScale).coerceIn(0f, 1f)
            assertTrue("${w.labelKo}: 작은 조각에 흔들림이 $magnitude", magnitude < 0.4f)
        }
    }
}
