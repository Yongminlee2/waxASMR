package com.waxball.asmr.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BreakModelTest {

    private fun model(
        seed: Long = 1,
        seeds: Int = 60,
        profile: SoundProfile = SoundProfile.hardWax(),
        queue: EventQueue = EventQueue(4096),
    ) = BreakModel(ShardSplitter.split(Icosphere.build(3), seeds, Random(seed)), profile, queue)

    private class Counter : EventQueue.Sink {
        val byKind = IntArray(5)
        val detachedIds = ArrayList<Int>()
        override fun on(kind: Int, shardId: Int, level: Int, energy: Float, pan: Float, areaFrac: Float) {
            byKind[kind]++
            if (kind == EventKind.DETACH) detachedIds.add(shardId)
        }
    }

    @Test
    fun statesAdvanceInOrderAndNeverGoBack() {
        val m = model()
        var prev = 0
        repeat(300) {
            m.press(0, 5f, 0.016f, 0f)
            assertTrue("상태가 역행함", m.state[0] >= prev)
            prev = m.state[0]
        }
        assertEquals(ShardState.DETACHED, m.state[0])
    }

    @Test
    fun lightTouchDoesNotDetachImmediately() {
        val m = model()
        m.press(0, 0.05f, 0.016f, 0f)
        assertTrue(m.state[0] < ShardState.DETACHED)
    }

    @Test
    fun crackPropagatesToNeighbours() {
        val m = model()
        while (m.state[0] < ShardState.CRACKED) m.press(0, 3f, 0.016f, 0f)
        assertTrue(
            "이웃으로 금이 번지지 않음",
            m.shards.adjacency[0].any { m.state[it] > ShardState.INTACT },
        )
    }

    @Test
    fun softMaterialSpreadsLessThanHardMaterial() {
        fun spread(p: SoundProfile): Int {
            val m = model(profile = p)
            repeat(60) { m.press(0, 3f, 0.016f, 0f) }
            return m.shards.adjacency[0].count { m.state[it] > ShardState.INTACT }
        }
        assertTrue(spread(SoundProfile.softWax()) <= spread(SoundProfile.sugarGlass()))
    }

    @Test
    fun propagationDoesNotCascadeBeyondOneRing() {
        val m = model(seeds = 120)
        repeat(400) { m.press(0, 5f, 0.016f, 0f) }
        val ring = m.shards.adjacency[0].toSet()
        val outside = m.state.indices.filter { it != 0 && it !in ring }
        assertTrue(
            "한 조각만 눌렀는데 두 겹 밖까지 부서짐",
            outside.none { m.state[it] > ShardState.INTACT },
        )
    }

    @Test
    fun pressedShardDetachesExactlyOnce() {
        val q = EventQueue(8192)
        val m = model(queue = q)
        repeat(400) { m.press(0, 5f, 0.016f, 0f) }
        val c = Counter()
        q.drain(c)
        assertEquals("조각 0이 여러 번 분리됨", 1, c.detachedIds.count { it == 0 })
    }

    @Test
    fun everyTransitionEmitsExactlyOneEvent() {
        val q = EventQueue(8192)
        val m = model(seeds = 40, queue = q)
        repeat(400) { m.press(0, 5f, 0.016f, 0f) }
        val c = Counter()
        q.drain(c)
        val totalTransitions = m.state.sum()
        assertEquals(totalTransitions, c.byKind[EventKind.CRACK] + c.byKind[EventKind.DETACH])
    }

    @Test
    fun shellProgressReachesOneWhenAllDetached() {
        val m = model(seeds = 30)
        m.shards.shards.indices.forEach { i -> repeat(400) { m.press(i, 5f, 0.016f, 0f) } }
        assertEquals(1.0f, m.shellProgress, 1e-3f)
        assertTrue(m.coreExposed)
        assertEquals(30, m.detachedCount)
    }

    @Test
    fun quadrantProgressNeedsAllSides() {
        val m = model(seeds = 60)
        // 한쪽 반구만 깬다.
        m.shards.shards.filter { it.center.x > 0.3f }.forEach { s ->
            repeat(400) { m.press(s.id, 5f, 0.016f, 0f) }
        }
        val q = m.quadrantProgress()
        assertTrue("반대편이 이미 다 깨졌다고 나옴", q.any { it < 0.6f })
    }

    @Test
    fun coreIsSilentUntilShellIsGone() {
        val q = EventQueue(1024)
        val m = model(seeds = 30, queue = q)
        m.squeezeCore(1f, 0f)
        assertTrue("껍질이 남았는데 코어 소리가 남", q.isEmpty)

        m.shards.shards.indices.forEach { i -> repeat(400) { m.press(i, 5f, 0.016f, 0f) } }
        q.clear()
        m.squeezeCore(1f, 0f)
        assertFalse("껍질이 다 벗겨졌는데 코어 소리가 안 남", q.isEmpty)
    }

    @Test
    fun detachedShardIgnoresFurtherPresses() {
        val q = EventQueue(8192)
        val m = model(queue = q)
        repeat(400) { m.press(0, 5f, 0.016f, 0f) }
        q.clear()
        repeat(50) { m.press(0, 5f, 0.016f, 0f) }
        assertTrue("이미 떨어진 조각에서 소리가 남", q.isEmpty)
    }

    @Test
    fun bigPlateTakesLooseNeighboursWithIt() {
        // 조각이 하나씩 또박또박 떨어지면 사건이 없어 지루하다.
        // 넓은 판이 갈 때 이미 들뜬 이웃이 우수수 따라 무너져야 한 방이 생긴다.
        val m = model(seeds = 90)
        val big = m.shards.shards.maxByOrNull { it.areaFrac }!!

        // 이웃들을 들뜬 상태까지만 만들어 둔다.
        val neighbours = m.shards.adjacency[big.id]
        for (n in neighbours) {
            while (m.state[n] < ShardState.LOOSE) m.press(n, 2f, 0.016f, 0f)
        }
        val looseBefore = neighbours.count { m.state[it] == ShardState.LOOSE }
        assertTrue("들뜬 이웃을 만들지 못함", looseBefore >= 2)

        while (m.state[big.id] < ShardState.DETACHED) m.press(big.id, 2f, 0.016f, 0f)

        val fellTogether = neighbours.count { m.state[it] == ShardState.DETACHED }
        assertTrue("넓은 판이 떨어졌는데 들뜬 이웃이 그대로 남음", fellTogether >= 2)
    }

    @Test
    fun bigPlateCollapsesMoreThanASmallChip() {
        // 들뜬 이웃은 금이 번지는 것만으로도 넘어갈 수 있다. 그건 의도한 동작이다.
        // 확인할 것은 "크기에 따라 데려가는 양이 다른가"다. 다 똑같이 무너지면
        // 넓은 판이 떨어지는 한 방이 안 생긴다.
        fun collapsedFractionOf(pickBiggest: Boolean): Float {
            val m = model(seeds = 120)
            val target = if (pickBiggest) {
                m.shards.shards.maxByOrNull { it.areaFrac }!!
            } else {
                m.shards.shards.minByOrNull { it.areaFrac }!!
            }
            val neighbours = m.shards.adjacency[target.id]
            for (n in neighbours) {
                while (m.state[n] < ShardState.LOOSE) m.press(n, 2f, 0.016f, 0f)
            }
            while (m.state[target.id] < ShardState.DETACHED) m.press(target.id, 2f, 0.016f, 0f)
            return neighbours.count { m.state[it] == ShardState.DETACHED }.toFloat() / neighbours.size
        }

        val big = collapsedFractionOf(pickBiggest = true)
        val small = collapsedFractionOf(pickBiggest = false)
        assertTrue("넓은 판이 이웃을 다 데려가지 못함 ($big)", big >= 0.9f)
        assertTrue("크기와 상관없이 똑같이 무너짐 (큼=$big, 작음=$small)", big > small)
    }

    @Test
    fun brushPressBreaksAWholeAreaNotASingleShard() {
        // 조각 하나만 콕 누르는 건 손가락이 아니라 바늘이다.
        //
        // 힘 10은 예전 3과 같은 세기다. 조각별 편차를 벌리면서 모델이 기대하는 힘의
        // 눈금이 3.4배로 커졌다(PalmPose.FORCE_GAIN 참고). 이 테스트가 지키는 것은
        // "누른 자리 주변이 함께 반응하는가"이지 절대 세기가 아니다.
        val m = model(seeds = 120)
        val hit = m.shards.shards[0].center
        repeat(40) { m.pressArea(hit, 0.955f, 10f, 0.016f, 0f) }

        val touched = m.state.count { it > ShardState.INTACT }
        assertTrue("한 번 문질렀는데 조각 하나만 반응함 (${touched}개)", touched >= 4)
    }

    @Test
    fun randomHammeringKeepsInvariants() {
        val q = EventQueue(64)   // 일부러 작게: 넘쳐도 상태가 망가지면 안 된다
        val m = model(seeds = 80, queue = q)
        val rng = Random(99)
        repeat(20000) {
            m.press(rng.nextInt(m.shards.size), rng.nextFloat() * 4f, 0.008f, rng.nextFloat() * 2 - 1)
            if (it % 7 == 0) q.clear()
        }
        m.state.forEach { assertTrue("상태 범위 이탈: $it", it in 0..4) }
        assertTrue(m.shellProgress in 0f..1.0001f)
        assertEquals(m.state.count { it == ShardState.DETACHED }, m.detachedCount)
    }

    @Test
    fun invalidShardIdIsIgnored() {
        val q = EventQueue(64)
        val m = model(queue = q)
        m.press(-1, 5f, 0.1f, 0f)
        m.press(9999, 5f, 0.1f, 0f)
        assertTrue(q.isEmpty)
    }

    @Test
    fun rubIsSilentWhenFingerBarelyMoves() {
        val q = EventQueue(64)
        val m = model(queue = q)
        m.rub(0.001f, 0f)
        assertTrue(q.isEmpty)
        m.rub(0.5f, 0f)
        assertFalse(q.isEmpty)
    }
}
