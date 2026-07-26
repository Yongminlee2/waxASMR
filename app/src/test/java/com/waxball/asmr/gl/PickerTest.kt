package com.waxball.asmr.gl

import com.waxball.asmr.core.Icosphere
import com.waxball.asmr.core.ShardSplitter
import com.waxball.asmr.core.ShardState
import com.waxball.asmr.core.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PickerTest {

    private val set = ShardSplitter.split(Icosphere.build(3), 80, Random(1))
    private val intact = IntArray(set.size)

    @Test
    fun rayThroughCentreHitsTheNearSide() {
        val id = Picker.pick(Vec3(0f, 0f, 5f), Vec3(0f, 0f, -1f), set, intact)
        assertTrue(id >= 0)
        assertTrue("카메라 쪽이 아니라 뒤통수를 맞춤", set.shards[id].center.z > 0.5f)
    }

    @Test
    fun rayMissingTheBallReturnsMiss() {
        assertEquals(Picker.MISS, Picker.pick(Vec3(0f, 4f, 5f), Vec3(0f, 0f, -1f), set, intact))
    }

    @Test
    fun rayPointingAwayReturnsMiss() {
        assertEquals(Picker.MISS, Picker.pick(Vec3(0f, 0f, 5f), Vec3(0f, 0f, 1f), set, intact))
    }

    @Test
    fun detachedShardExposesTheCore() {
        val state = IntArray(set.size)
        val first = Picker.pick(Vec3(0f, 0f, 5f), Vec3(0f, 0f, -1f), set, state)
        state[first] = ShardState.DETACHED
        assertEquals(Picker.CORE, Picker.pick(Vec3(0f, 0f, 5f), Vec3(0f, 0f, -1f), set, state))
    }

    @Test
    fun crackedButAttachedShardIsStillPickable() {
        val state = IntArray(set.size)
        val first = Picker.pick(Vec3(0f, 0f, 5f), Vec3(0f, 0f, -1f), set, state)
        state[first] = ShardState.LOOSE
        assertEquals(first, Picker.pick(Vec3(0f, 0f, 5f), Vec3(0f, 0f, -1f), set, state))
    }

    @Test
    fun differentDirectionsHitDifferentShards() {
        val front = Picker.pick(Vec3(0f, 0f, 5f), Vec3(0f, 0f, -1f), set, intact)
        val side = Picker.pick(Vec3(5f, 0f, 0f), Vec3(-1f, 0f, 0f), set, intact)
        val top = Picker.pick(Vec3(0f, 5f, 0f), Vec3(0f, -1f, 0f), set, intact)
        assertNotEquals(front, side)
        assertNotEquals(front, top)
        assertNotEquals(side, top)
    }

    @Test
    fun pickedShardIsTheClosestSeedToTheHitPoint() {
        val rng = Random(5)
        repeat(200) {
            val dir = Vec3(rng.nextFloat() * 2 - 1, rng.nextFloat() * 2 - 1, rng.nextFloat() * 2 - 1).normalized()
            val origin = dir * 5f
            val id = Picker.pick(origin, -dir, set, intact)
            if (id < 0) return@repeat
            val hitDot = dir dot set.shards[id].center
            val bestDot = set.shards.maxOf { dir dot it.center }
            assertEquals("가장 가까운 조각을 못 고름", bestDot, hitDot, 1e-5f)
        }
    }

    @Test
    fun grazingRayStillHits() {
        // 볼 가장자리를 스치듯 지나가는 광선도 잡혀야 손맛이 자연스럽다.
        val id = Picker.pick(Vec3(1.1f, 0f, 5f), Vec3(0f, 0f, -1f), set, intact)
        assertTrue(id >= 0)
    }

    @Test
    fun unnormalizedDirectionWorks() {
        val a = Picker.pick(Vec3(0f, 0f, 5f), Vec3(0f, 0f, -1f), set, intact)
        val b = Picker.pick(Vec3(0f, 0f, 5f), Vec3(0f, 0f, -37f), set, intact)
        assertEquals(a, b)
    }
}
