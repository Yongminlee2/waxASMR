package com.waxball.asmr.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MissionsTest {

    private fun model(seeds: Int = 60): BreakModel =
        BreakModel(
            ShardSplitter.split(Icosphere.build(3), seeds, Random(1)),
            SoundProfile.hardWax(),
            EventQueue(8192).also { it.clear() },
        )

    /**
     * 금이 이웃으로 번지지 않는 모델. 조각을 정확히 N개만 떼고 싶은 테스트에 쓴다.
     * 번짐이 있으면 한 조각을 깼을 때 옆이 같이 떨어져서 개수를 셀 수 없다.
     */
    private fun isolatedModel(seeds: Int): BreakModel =
        BreakModel(
            ShardSplitter.split(Icosphere.build(3), seeds, Random(1)),
            SoundProfile.hardWax().copy(propagation = 0f),
            EventQueue(8192),
        )

    private fun gesture(force: Float = 1f, stroke: Int = 1) = Gesture().apply {
        touching = true; this.force = force; strokeId = stroke
    }

    /** 조각을 하나씩 완전히 떼어낸다. */
    private fun destroy(m: BreakModel, count: Int) {
        var done = 0
        for (i in m.state.indices) {
            if (done >= count) break
            if (m.state[i] >= ShardState.DETACHED) continue
            repeat(400) { m.press(i, 5f, 0.016f, 0f) }
            done++
        }
    }

    private fun destroyAll(m: BreakModel) = destroy(m, m.state.size)

    // --- ① 속도 ---

    @Test
    fun speedMissionClearsWhenThresholdReachedInTime() {
        val mission = SpeedMission()
        val m = model()
        destroyAll(m)
        mission.update(1f, m, gesture())
        assertEquals(Mission.CLEARED, mission.state)
    }

    @Test
    fun speedMissionFailsWhenTimeRunsOut() {
        val mission = SpeedMission()
        val m = model()
        repeat(50) { mission.update(1f, m, gesture()) }
        assertEquals(Mission.FAILED, mission.state)
    }

    @Test
    fun speedMissionProgressTracksShellProgress() {
        val mission = SpeedMission()
        val m = model(40)
        destroy(m, 16)
        mission.update(0.1f, m, gesture())
        assertTrue("진행률이 0", mission.progress > 0.1f)
        assertTrue("진행률이 1을 넘음", mission.progress <= 1f)
    }

    // --- ② 한 획 ---

    @Test
    fun singleStrokeClearsWhenEnoughShardsComeOffInOneStroke() {
        val mission = SingleStrokeMission()
        val m = isolatedModel(60)
        val g = gesture(stroke = 1)
        mission.update(0.016f, m, g)
        destroy(m, 26)
        mission.update(0.016f, m, g)
        assertEquals(Mission.CLEARED, mission.state)
    }

    @Test
    fun singleStrokeResetsWhenFingerLifts() {
        val mission = SingleStrokeMission()
        val m = isolatedModel(60)
        val g = gesture(stroke = 1)
        mission.update(0.016f, m, g)
        destroy(m, 20)
        mission.update(0.016f, m, g)

        g.strokeId = 2                    // 손을 뗐다 다시 눌렀다
        mission.update(0.016f, m, g)
        destroy(m, 4)
        mission.update(0.016f, m, g)
        assertEquals("손을 떼고도 이어서 세면 안 됨", Mission.RUNNING, mission.state)
    }

    @Test
    fun singleStrokeKeepsTheBestAttempt() {
        val mission = SingleStrokeMission()
        val m = isolatedModel(60)
        val g = gesture(stroke = 1)
        mission.update(0.016f, m, g)
        destroy(m, 20)
        mission.update(0.016f, m, g)
        val best = mission.progress

        g.strokeId = 2
        mission.update(0.016f, m, g)
        assertEquals("최고 기록이 사라짐", best, mission.progress, 1e-6f)
    }

    // --- ③ 정밀 ---

    @Test
    fun precisionFailsOnExcessiveForce() {
        val mission = PrecisionMission()
        val m = model()
        mission.update(0.016f, m, gesture(force = 3.0f))
        assertEquals(Mission.FAILED, mission.state)
    }

    @Test
    fun precisionSurvivesGentleTouches() {
        val mission = PrecisionMission()
        val m = model()
        repeat(100) { mission.update(0.016f, m, gesture(force = 1.5f)) }
        assertEquals(Mission.RUNNING, mission.state)
    }

    @Test
    fun precisionClearsOnFullShellRemoval() {
        val mission = PrecisionMission()
        val m = model(30)
        destroyAll(m)
        mission.update(0.016f, m, gesture(force = 1f))
        assertEquals(Mission.CLEARED, mission.state)
    }

    @Test
    fun precisionIgnoresForceWhenNotTouching() {
        val mission = PrecisionMission()
        val m = model()
        val g = Gesture().apply { touching = false; force = 9f }
        mission.update(0.016f, m, g)
        assertEquals(Mission.RUNNING, mission.state)
    }

    // --- ④ 사방 ---

    @Test
    fun quadrantNeedsAllFourSides() {
        val mission = QuadrantMission()
        val m = model(80)
        // 한쪽 반구만 깬다
        m.shards.shards.filter { it.center.x > 0.2f }.forEach { s ->
            repeat(400) { m.press(s.id, 5f, 0.016f, 0f) }
        }
        mission.update(0.016f, m, gesture())
        assertEquals("한쪽만 깼는데 통과함", Mission.RUNNING, mission.state)

        destroyAll(m)
        mission.update(0.016f, m, gesture())
        assertEquals(Mission.CLEARED, mission.state)
    }

    @Test
    fun quadrantProgressReflectsTheWeakestSide() {
        val mission = QuadrantMission()
        val m = model(80)
        m.shards.shards.filter { it.center.x > 0.2f }.forEach { s ->
            repeat(400) { m.press(s.id, 5f, 0.016f, 0f) }
        }
        mission.update(0.016f, m, gesture())
        assertTrue("가장 덜 깬 쪽이 진행률에 반영되지 않음", mission.progress < 1f)
    }

    // --- ⑤ 콤보 ---

    @Test
    fun comboClearsWhenCrackingNeverStops() {
        val mission = ComboMission()
        // 파괴 임계를 실제 영상에 맞춰 낮춘 뒤로는 조각이 훨씬 빨리 떨어진다.
        // 12초를 이어가려면 깰 조각이 그만큼 많아야 한다.
        val m = model(600)
        // 실제로 문지를 때처럼, 한 조각이 떨어지면 바로 옆 조각으로 옮겨 계속 깬다.
        var cursor = 0
        repeat(900) {
            while (cursor < m.state.size - 1 && m.state[cursor] >= ShardState.DETACHED) cursor++
            m.press(cursor, 5f, 0.016f, 0f)
            mission.update(0.016f, m, gesture())
        }
        assertEquals(Mission.CLEARED, mission.state)
    }

    @Test
    fun comboBreaksAfterAGap() {
        val mission = ComboMission()
        val m = model(200)
        repeat(300) { mission.update(0.016f, m, gesture()) }   // 아무것도 안 깸
        val stalled = mission.progress
        assertEquals("안 깨는데 콤보가 쌓임", 0f, stalled, 1e-6f)
    }

    @Test
    fun comboResetsWhenPlayerPauses() {
        val mission = ComboMission()
        val m = model(200)
        var cursor = 0
        repeat(200) {
            while (cursor < m.state.size - 1 && m.state[cursor] >= ShardState.DETACHED) cursor++
            m.press(cursor, 5f, 0.016f, 0f)
            mission.update(0.016f, m, gesture())
        }
        assertTrue(mission.progress > 0f)
        repeat(60) { mission.update(0.016f, m, gesture()) }    // 1초 쉼
        assertEquals("쉬었는데 콤보가 안 끊김", 0f, mission.progress, 1e-6f)
    }

    // --- ⑥ 껍질만 ---

    @Test
    fun shellOnlyFailsAfterTooManyCoreTouches() {
        val mission = ShellOnlyMission()
        val m = model()
        val g = gesture()
        g.coreTouches = 4
        mission.update(0.016f, m, g)
        assertEquals(Mission.FAILED, mission.state)
    }

    @Test
    fun shellOnlyForgivesAFewSlips() {
        val mission = ShellOnlyMission()
        val m = model()
        val g = gesture()
        g.coreTouches = 3
        mission.update(0.016f, m, g)
        assertEquals(Mission.RUNNING, mission.state)
    }

    @Test
    fun shellOnlyClearsWhenShellFullyRemoved() {
        val mission = ShellOnlyMission()
        val m = model(30)
        destroyAll(m)
        mission.update(0.016f, m, gesture())
        assertEquals(Mission.CLEARED, mission.state)
    }

    // --- 공통 ---

    @Test
    fun finishedMissionStopsUpdating() {
        val mission = PrecisionMission()
        val m = model()
        mission.update(0.016f, m, gesture(force = 9f))
        assertEquals(Mission.FAILED, mission.state)
        val at = mission.elapsed
        repeat(50) { mission.update(0.016f, m, gesture(force = 1f)) }
        assertEquals("끝난 미션이 계속 돌아감", at, mission.elapsed, 1e-6f)
        assertEquals(Mission.FAILED, mission.state)
    }

    @Test
    fun everyMissionHasKoreanTitleAndReward() {
        Missions.factories.forEachIndexed { i, f ->
            val m = f()
            assertEquals(i, m.id)
            assertTrue("미션 $i 제목이 짧음", m.titleKo.length >= 6)
            assertTrue("미션 $i 보상이 없음", m.reward > 0)
            assertEquals(0f, m.progress, 1e-6f)
        }
    }

    @Test
    fun dailySelectionIsStableForTheSameDay() {
        assertEquals(Missions.dailyIdsFor(20300), Missions.dailyIdsFor(20300))
        assertEquals(Missions.dailyIdsFor(1), Missions.dailyIdsFor(1))
    }

    @Test
    fun dailySelectionHasThreeDistinctMissions() {
        for (day in 0L..400L) {
            val ids = Missions.dailyIdsFor(day)
            assertEquals("$day 일: 3개가 아님", 3, ids.size)
            assertEquals("$day 일: 같은 미션이 겹침", 3, ids.toSet().size)
            ids.forEach { assertTrue("$day 일: 미션 번호 이탈 $it", it in Missions.factories.indices) }
        }
    }

    @Test
    fun dailySelectionActuallyVariesAcrossDays() {
        val combos = (0L..60L).map { Missions.dailyIdsFor(it) }.toSet()
        assertTrue("매일 같은 미션만 나옴", combos.size > 5)
    }

    @Test
    fun differentDaysUsuallyDiffer() {
        assertNotEquals(Missions.dailyIdsFor(100), Missions.dailyIdsFor(101))
    }

    @Test
    fun createClampsUnknownIds() {
        assertEquals(0, Missions.create(-3).id)
        assertEquals(Missions.factories.size - 1, Missions.create(99).id)
    }

    @Test
    fun timedMissionReportsRemainingTime() {
        val mission = SpeedMission()
        val m = model()
        mission.update(5f, m, gesture())
        assertEquals(40f, mission.remainingSeconds, 1e-4f)
    }
}
