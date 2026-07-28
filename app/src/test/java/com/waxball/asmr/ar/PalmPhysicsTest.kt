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
    fun aLevelPalmDoesNotMoveTheBall() {
        val physics = PalmPhysics()
        run(physics, roll = 0f)
        assertEquals("수평인데 볼이 밀려남", 0f, physics.slideX, 1e-3f)
    }

    @Test
    fun tiltingOneWayPushesTheBallThatWay() {
        val left = PalmPhysics().also { run(it, roll = -0.5f) }
        val right = PalmPhysics().also { run(it, roll = 0.5f) }
        assertTrue("좌우로 눕혔는데 같은 쪽으로 밀림", left.slideX * right.slideX < 0f)
    }

    @Test
    fun theBallSettlesInsteadOfDriftingForever() {
        // 계속 쌓이면 볼이 손 밖으로 나간다. 기울인 만큼에서 멈춰야 한다.
        val physics = PalmPhysics()
        run(physics, roll = 0.5f, seconds = 1f)
        val settled = physics.slideX
        run(physics, roll = 0.5f, seconds = 3f)
        assertEquals("같은 기울기인데 계속 밀려남", settled, physics.slideX, 0.01f)
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
        val frozen = physics.slideX
        repeat(60) { physics.update(0.5f, hasHand = false, dt = 1f / 60f) }
        assertEquals("손이 없는데 볼이 계속 움직임", frozen, physics.slideX, 1e-4f)
    }

    @Test
    fun resetClearsEverything() {
        val physics = PalmPhysics()
        run(physics, roll = 0.7f)
        physics.reset()
        assertEquals(0f, physics.slideX, 1e-6f)
    }
}
