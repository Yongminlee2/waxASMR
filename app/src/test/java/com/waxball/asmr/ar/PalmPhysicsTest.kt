package com.waxball.asmr.ar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PalmPhysicsTest {

    private fun run(physics: PalmPhysics, roll: Float, seconds: Float = 1f) {
        val dt = 1f / 60f
        repeat((seconds / dt).toInt()) { physics.update(roll, hasHand = true, dt = dt) }
    }

    @Test
    fun aLevelPalmDoesNotRollTheBall() {
        val physics = PalmPhysics()
        run(physics, roll = 0f)
        assertEquals("수평인데 볼이 굴러감", 0f, physics.spin, 1e-3f)
        assertEquals("수평인데 볼이 밀려남", 0f, physics.slideX, 1e-3f)
    }

    @Test
    fun tiltingOneWayRollsTheBallThatWay() {
        val left = PalmPhysics().also { run(it, roll = -0.5f) }
        val right = PalmPhysics().also { run(it, roll = 0.5f) }
        assertTrue("좌우로 눕혔는데 같은 쪽으로 구름", left.spin * right.spin < 0f)
        assertTrue("좌우로 눕혔는데 같은 쪽으로 밀림", left.slideX * right.slideX < 0f)
    }

    @Test
    fun theBallKeepsRollingWhileTheHandStaysTilted() {
        val physics = PalmPhysics()
        run(physics, roll = 0.5f, seconds = 0.5f)
        val half = physics.spin
        run(physics, roll = 0.5f, seconds = 0.5f)
        assertTrue("계속 눕혀 뒀는데 구르다 멈춤 ($half → ${physics.spin})", physics.spin > half * 1.5f)
    }

    @Test
    fun theBallDoesNotSlideOffThePalm() {
        val physics = PalmPhysics()
        run(physics, roll = 1.4f, seconds = 5f)
        assertTrue(
            "손바닥 밖으로 밀려남 (${physics.slideX})",
            abs(physics.slideX) <= PalmPhysics.MAX_SLIDE + 1e-3f,
        )
    }

    @Test
    fun losingTheHandFreezesEverything() {
        val physics = PalmPhysics()
        run(physics, roll = 0.5f)
        val frozen = physics.spin
        repeat(60) { physics.update(0.5f, hasHand = false, dt = 1f / 60f) }
        assertEquals("손이 없는데 볼이 계속 구름", frozen, physics.spin, 1e-4f)
    }

    @Test
    fun resetClearsEverything() {
        val physics = PalmPhysics()
        run(physics, roll = 0.7f)
        physics.reset()
        assertEquals(0f, physics.spin, 1e-6f)
        assertEquals(0f, physics.slideX, 1e-6f)
    }
}
