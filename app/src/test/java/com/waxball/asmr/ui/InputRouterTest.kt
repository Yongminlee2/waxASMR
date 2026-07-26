package com.waxball.asmr.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class InputRouterTest {

    private class Log : InputRouter.Listener {
        var breaks = 0
        var orbits = 0
        var zooms = 0
        var releases = 0
        var lastForce = 0f
        var lastSpeed = 0f
        var orbitDx = 0f
        var lastZoom = 1f

        override fun onBreak(x: Float, y: Float, force: Float, speed: Float, dt: Float) {
            breaks++; lastForce = force; lastSpeed = speed
        }
        override fun onOrbit(dx: Float, dy: Float) { orbits++; orbitDx += dx }
        override fun onZoom(scale: Float) { zooms++; lastZoom = scale }
        override fun onRelease() { releases++ }
    }

    private lateinit var log: Log
    private lateinit var router: InputRouter

    /** 볼은 화면 (500,500)에 반지름 300으로 있다고 둔다. */
    @Before
    fun setUp() {
        log = Log()
        router = InputRouter(log).apply {
            ballCenterX = 500f; ballCenterY = 500f; ballRadius = 300f
        }
    }

    private fun down(x: Float, y: Float, t: Long = 0) =
        router.onTouch(TouchAction.DOWN, 1, floatArrayOf(x), floatArrayOf(y), t)

    private fun move(x: Float, y: Float, t: Long) =
        router.onTouch(TouchAction.MOVE, 1, floatArrayOf(x), floatArrayOf(y), t)

    private fun up(t: Long = 0) =
        router.onTouch(TouchAction.UP, 1, floatArrayOf(0f), floatArrayOf(0f), t)

    private val ms = 1_000_000L

    @Test
    fun singleFingerInsideBallBreaks() {
        down(500f, 500f)
        move(510f, 500f, 16 * ms)
        assertTrue(log.breaks > 0)
        assertEquals(0, log.orbits)
        assertEquals(InputRouter.MODE_BREAK, router.currentMode)
    }

    @Test
    fun singleFingerOutsideBallOrbits() {
        down(900f, 500f)
        move(920f, 500f, 16 * ms)
        assertTrue(log.orbits > 0)
        assertEquals(0, log.breaks)
        assertEquals(InputRouter.MODE_ORBIT, router.currentMode)
    }

    @Test
    fun touchDownInsideBallBreaksImmediately() {
        down(500f, 500f)
        assertEquals("눌린 순간 바로 소리가 나야 함", 1, log.breaks)
    }

    @Test
    fun twoFingersAlwaysOrbitEvenInsideBall() {
        down(500f, 500f)
        val before = log.breaks
        router.onTouch(TouchAction.POINTER_DOWN, 2, floatArrayOf(480f, 520f), floatArrayOf(500f, 500f), 10 * ms)
        router.onTouch(TouchAction.MOVE, 2, floatArrayOf(500f, 540f), floatArrayOf(500f, 500f), 26 * ms)
        assertTrue(log.orbits > 0)
        assertEquals("두 손가락일 때 깨지면 안 됨", before, log.breaks)
    }

    @Test
    fun pinchReportsZoom() {
        down(500f, 500f)
        router.onTouch(TouchAction.POINTER_DOWN, 2, floatArrayOf(450f, 550f), floatArrayOf(500f, 500f), 10 * ms)
        router.onTouch(TouchAction.MOVE, 2, floatArrayOf(400f, 600f), floatArrayOf(500f, 500f), 26 * ms)
        assertTrue(log.zooms > 0)
        assertTrue("벌리는데 축소로 나옴: ${log.lastZoom}", log.lastZoom > 1f)
    }

    @Test
    fun orbitLockedIgnoresOutsideDrag() {
        router.orbitLocked = true
        down(900f, 500f)
        move(920f, 500f, 16 * ms)
        assertEquals(0, log.orbits)
    }

    @Test
    fun orbitLockedTurnsWholeScreenIntoBreakArea() {
        router.orbitLocked = true
        down(900f, 500f)
        assertTrue("굴리기를 잠갔으면 볼 밖도 깨기여야 함", log.breaks > 0)
    }

    @Test
    fun holdingStillBuildsUpForce() {
        down(500f, 500f)
        val first = log.lastForce
        var t = 0L
        repeat(30) { t += 16 * ms; move(500f, 500f, t) }
        assertTrue("가만히 눌러도 힘이 안 쌓임 ($first → ${log.lastForce})", log.lastForce > first * 1.5f)
    }

    @Test
    fun forceIsCappedSoItCannotGrowForever() {
        down(500f, 500f)
        var t = 0L
        repeat(600) { t += 16 * ms; move(500f, 500f, t) }
        assertTrue("힘이 무한정 커짐: ${log.lastForce}", log.lastForce <= 4.5f)
    }

    @Test
    fun fastDragReportsHigherSpeedThanSlowDrag() {
        down(400f, 500f)
        move(410f, 500f, 100 * ms)
        val slow = log.lastSpeed

        setUp()
        down(400f, 500f)
        move(560f, 500f, 16 * ms)
        val fast = log.lastSpeed
        assertTrue("빠르게 문질러도 속도가 안 올라감 ($slow → $fast)", fast > slow * 2f)
    }

    @Test
    fun movingReleasesAccumulatedForce() {
        down(500f, 500f)
        var t = 0L
        repeat(30) { t += 16 * ms; move(500f, 500f, t) }
        val held = log.lastForce
        repeat(5) { t += 16 * ms; move(500f + it * 60f, 500f, t) }
        assertTrue("문지르는데도 눌린 힘이 그대로 남음", log.lastForce < held)
    }

    @Test
    fun releaseIsReportedOnce() {
        down(500f, 500f)
        up(20 * ms)
        assertEquals(1, log.releases)
        up(30 * ms)
        assertEquals("떼지도 않았는데 또 보고함", 1, log.releases)
    }

    @Test
    fun strokeIdChangesEachTimeFingerLands() {
        down(500f, 500f)
        val first = router.strokeId
        move(520f, 500f, 16 * ms)
        assertEquals("손을 안 뗐는데 스트로크가 바뀜", first, router.strokeId)
        up(20 * ms)
        down(500f, 500f, 30 * ms)
        assertTrue("손을 뗐다 다시 눌렀는데 같은 스트로크", router.strokeId > first)
    }

    @Test
    fun liftingSecondFingerKeepsOrbitingInsteadOfBreaking() {
        down(500f, 500f)
        router.onTouch(TouchAction.POINTER_DOWN, 2, floatArrayOf(480f, 520f), floatArrayOf(500f, 500f), 10 * ms)
        router.onTouch(TouchAction.POINTER_UP, 2, floatArrayOf(480f, 520f), floatArrayOf(500f, 500f), 20 * ms)
        val before = log.breaks
        move(540f, 500f, 40 * ms)
        assertEquals("손가락 하나 뗐다고 갑자기 깨지면 당황스럽다", before, log.breaks)
        assertTrue(log.orbits > 0)
    }

    @Test
    fun cancelEndsTheGestureCleanly() {
        down(500f, 500f)
        router.onTouch(TouchAction.CANCEL, 1, floatArrayOf(500f), floatArrayOf(500f), 20 * ms)
        assertEquals(1, log.releases)
        assertEquals(InputRouter.MODE_NONE, router.currentMode)
    }

    @Test
    fun moveWithoutDownIsIgnored() {
        move(500f, 500f, 16 * ms)
        assertEquals(0, log.breaks)
        assertEquals(0, log.orbits)
    }
}
