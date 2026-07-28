package com.waxball.asmr.ar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArSessionTest {

    @Test
    fun breakingRepeatedlyBuildsACombo() {
        val session = ArSession()
        session.onNewBall(0L)
        session.onBreak(100L)
        session.onBreak(400L)
        session.onBreak(900L)
        assertEquals(3, session.combo)
    }

    @Test
    fun aPauseEndsTheCombo() {
        val session = ArSession()
        session.onNewBall(0L)
        session.onBreak(100L)
        session.onBreak(400L)
        session.tick(0.1f, 2000L)
        assertEquals("쉬었는데 콤보가 안 끊김", 0, session.combo)
    }

    @Test
    fun breakingAfterAPauseStartsANewCombo() {
        val session = ArSession()
        session.onNewBall(0L)
        session.onBreak(100L)
        session.tick(0.1f, 2000L)
        session.onBreak(2100L)
        assertEquals(1, session.combo)
    }

    @Test
    fun theBestComboIsKept() {
        val session = ArSession()
        session.onNewBall(0L)
        session.onBreak(100L)
        session.onBreak(300L)
        session.onBreak(500L)
        session.tick(0.1f, 3000L)
        session.onBreak(3100L)
        assertEquals("최고 콤보가 안 남음", 3, session.bestCombo)
        assertEquals(1, session.combo)
    }

    @Test
    fun timeRunsWhileTheBallIsAlive() {
        val session = ArSession()
        session.onNewBall(0L)
        repeat(60) { session.tick(1f / 60f, 0L) }
        assertEquals(1f, session.elapsedSec, 0.05f)
    }

    @Test
    fun clearingRecordsTheTime() {
        val session = ArSession()
        session.onNewBall(0L)
        repeat(120) { session.tick(1f / 60f, 0L) }
        assertTrue("첫 기록인데 갱신이 아니라고 나옴", session.onCleared(2000L))
        assertEquals(2f, session.bestClearSec, 0.05f)
    }

    @Test
    fun aSlowerRunDoesNotReplaceTheRecord() {
        val session = ArSession()
        session.onNewBall(0L)
        repeat(120) { session.tick(1f / 60f, 0L) }
        session.onCleared(2000L)

        session.onNewBall(2000L)
        repeat(300) { session.tick(1f / 60f, 0L) }
        assertFalse("느린 기록이 갱신으로 잡힘", session.onCleared(7000L))
        assertEquals("느린 기록이 최고 기록을 덮음", 2f, session.bestClearSec, 0.05f)
    }

    @Test
    fun aFasterRunReplacesTheRecord() {
        val session = ArSession()
        session.onNewBall(0L)
        repeat(300) { session.tick(1f / 60f, 0L) }
        session.onCleared(5000L)

        session.onNewBall(5000L)
        repeat(60) { session.tick(1f / 60f, 0L) }
        assertTrue(session.onCleared(6000L))
        assertEquals(1f, session.bestClearSec, 0.05f)
    }

    @Test
    fun aNewBallResetsTheClockButNotTheRecords() {
        val session = ArSession()
        session.onNewBall(0L)
        session.onBreak(100L)
        session.onBreak(300L)
        repeat(120) { session.tick(1f / 60f, 400L) }
        session.onCleared(2000L)

        session.onNewBall(2000L)
        assertEquals("새 볼인데 시계가 안 돌아감", 0f, session.elapsedSec, 1e-4f)
        assertEquals("새 볼인데 콤보가 남음", 0, session.combo)
        assertEquals("새 볼이라고 최고 콤보가 지워짐", 2, session.bestCombo)
        assertEquals("새 볼이라고 최고 기록이 지워짐", 2f, session.bestClearSec, 0.05f)
    }
}
