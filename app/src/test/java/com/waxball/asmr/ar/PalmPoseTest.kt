package com.waxball.asmr.ar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PalmPoseTest {

    /**
     * 손 하나를 만든다. [curl] 0이면 편 손, 1이면 주먹.
     * 손목을 아래에 두고 손가락을 위로 뻗은 모양이다. 쥘수록 끝이 뿌리 쪽으로 온다.
     */
    private fun hand(
        curl: Float,
        centerX: Float = 0.5f,
        centerY: Float = 0.5f,
        scale: Float = 0.2f,
    ): HandLandmarks {
        val x = FloatArray(HandLandmarks.COUNT)
        val y = FloatArray(HandLandmarks.COUNT)

        fun put(i: Int, dx: Float, dy: Float) {
            x[i] = centerX + dx * scale
            y[i] = centerY + dy * scale
        }

        put(HandLandmarks.WRIST, 0f, 0.6f)
        // 손가락뿌리 넷은 손목 위에 가로로 늘어선다. 쥐어도 거의 안 움직인다.
        put(HandLandmarks.INDEX_MCP, -0.45f, 0f)
        put(HandLandmarks.MIDDLE_MCP, -0.15f, 0f)
        put(HandLandmarks.RING_MCP, 0.15f, 0f)
        put(HandLandmarks.PINKY_MCP, 0.45f, 0f)

        // 손가락 끝은 펴면 멀고 쥐면 뿌리 근처로 온다.
        // 손끝을 손목-뿌리 연장선에 놓아 굽힘 비율이 실측 범위(0.85~1.9)로 나오게 한다.
        val ratio = 1.85f - 1.0f * curl
        for ((mcp, tip) in listOf(
            HandLandmarks.INDEX_MCP to HandLandmarks.INDEX_TIP,
            HandLandmarks.MIDDLE_MCP to HandLandmarks.MIDDLE_TIP,
            HandLandmarks.RING_MCP to HandLandmarks.RING_TIP,
            HandLandmarks.PINKY_MCP to HandLandmarks.PINKY_TIP,
        )) {
            val dx = x[mcp] - x[HandLandmarks.WRIST]
            val dy = y[mcp] - y[HandLandmarks.WRIST]
            x[tip] = x[HandLandmarks.WRIST] + dx * ratio
            y[tip] = y[HandLandmarks.WRIST] + dy * ratio
        }

        return HandLandmarks(x, y)
    }

    /** 평활이 수렴할 때까지 같은 손을 계속 넣는다. */
    private fun settle(pose: PalmPose, hand: HandLandmarks?, frames: Int = 60) {
        repeat(frames) { pose.update(hand, 1f / 60f) }
    }

    @Test
    fun openHandIsNotSqueezed() {
        val pose = PalmPose()
        settle(pose, hand(curl = 0f))
        assertTrue("편 손인데 쥠이 ${pose.squeeze}", pose.squeeze < 0.15f)
    }

    @Test
    fun fistIsFullySqueezed() {
        val pose = PalmPose()
        settle(pose, hand(curl = 1f))
        assertTrue("주먹인데 쥠이 ${pose.squeeze}", pose.squeeze > 0.85f)
    }

    @Test
    fun squeezeRisesMonotonically() {
        var previous = -1f
        for (step in 0..10) {
            val pose = PalmPose()
            settle(pose, hand(curl = step / 10f))
            assertTrue(
                "쥠이 단조 증가하지 않음 ($previous → ${pose.squeeze})",
                pose.squeeze >= previous - 1e-3f,
            )
            previous = pose.squeeze
        }
    }

    @Test
    fun centreSitsInsideTheHand() {
        val pose = PalmPose()
        settle(pose, hand(curl = 0f, centerX = 0.3f, centerY = 0.7f))
        assertEquals(0.3f, pose.centerX, 0.06f)
        assertTrue("손바닥 중심이 손 밖에 있음: ${pose.centerY}", pose.centerY in 0.68f..0.78f)
    }

    @Test
    fun centreDoesNotDriftWhenTheHandCloses() {
        // 손가락 끝을 쓰면 쥘 때마다 볼이 딸려 들어간다. 뿌리와 손목만 써야 한다.
        val open = PalmPose().also { settle(it, hand(curl = 0f)) }
        val closed = PalmPose().also { settle(it, hand(curl = 1f)) }
        assertEquals("쥐었더니 볼 위치가 움직임", open.centerX, closed.centerX, 0.02f)
        assertEquals("쥐었더니 볼 위치가 움직임", open.centerY, closed.centerY, 0.02f)
    }

    @Test
    fun nearerHandGivesBiggerSpan() {
        val far = PalmPose().also { settle(it, hand(curl = 0f, scale = 0.15f)) }
        val near = PalmPose().also { settle(it, hand(curl = 0f, scale = 0.30f)) }
        assertTrue(
            "가까운 손이 더 크게 잡히지 않음 (${far.span} vs ${near.span})",
            near.span > far.span * 1.5f,
        )
    }

    @Test
    fun spanDoesNotCollapseWhenTheHandCloses() {
        // 뿌리 사이 거리로 재므로 쥐어도 크기가 유지돼야 한다.
        val open = PalmPose().also { settle(it, hand(curl = 0f)) }
        val closed = PalmPose().also { settle(it, hand(curl = 1f)) }
        assertEquals("쥐었더니 볼 크기가 변함", open.span, closed.span, open.span * 0.1f)
    }

    @Test
    fun holdingAFistProducesNoForce() {
        // 가만히 쥐고만 있는데 계속 부서지면 조작하는 느낌이 사라진다.
        val pose = PalmPose()
        settle(pose, hand(curl = 1f))
        repeat(30) {
            pose.update(hand(curl = 1f), 1f / 60f)
            assertEquals("쥔 채 가만히 있는데 힘이 들어감", 0f, pose.force, 1e-4f)
        }
    }

    @Test
    fun closingTheHandProducesForce() {
        val pose = PalmPose()
        settle(pose, hand(curl = 0f))
        var peak = 0f
        for (step in 1..10) {
            pose.update(hand(curl = step / 10f), 1f / 60f)
            if (pose.force > peak) peak = pose.force
        }
        assertTrue("쥐는데 힘이 안 들어감", peak > 0.5f)
    }

    @Test
    fun openingTheHandProducesNoForce() {
        val pose = PalmPose()
        settle(pose, hand(curl = 1f))
        for (step in 10 downTo 0) {
            pose.update(hand(curl = step / 10f), 1f / 60f)
            assertEquals("손을 펴는데 힘이 들어감", 0f, pose.force, 1e-4f)
        }
    }

    @Test
    fun repeatedSqueezingProducesForceEachTime() {
        val pose = PalmPose()
        settle(pose, hand(curl = 0f))
        var bursts = 0
        repeat(3) {
            for (step in 0..10) {
                pose.update(hand(curl = step / 10f), 1f / 60f)
                if (pose.force > 0.5f) { bursts++; break }
            }
            settle(pose, hand(curl = 0f), frames = 20)
        }
        assertEquals("쥐었다 폈다를 반복해도 매번 힘이 들어가야 함", 3, bursts)
    }

    @Test
    fun aSingleJitteryFrameDoesNotJumpTheBall() {
        val pose = PalmPose()
        settle(pose, hand(curl = 0f, centerX = 0.5f))
        val before = pose.centerX
        pose.update(hand(curl = 0f, centerX = 0.9f), 1f / 60f)
        assertTrue("한 프레임 튄 것이 그대로 반영됨", pose.centerX - before < 0.2f)
    }

    @Test
    fun losingTheHandKeepsTheLastPositionAndStopsForce() {
        val pose = PalmPose()
        settle(pose, hand(curl = 0.5f, centerX = 0.4f))
        val lastX = pose.centerX
        pose.update(null, 1f / 60f)
        assertFalse(pose.hasHand)
        assertEquals("손을 놓쳤는데 볼이 순간이동함", lastX, pose.centerX, 1e-4f)
        assertEquals("손이 없는데 힘이 들어감", 0f, pose.force, 1e-4f)
    }

    @Test
    fun regainingTheHandWorksAgain() {
        val pose = PalmPose()
        settle(pose, hand(curl = 0f))
        repeat(10) { pose.update(null, 1f / 60f) }
        settle(pose, hand(curl = 0f))
        assertTrue(pose.hasHand)
    }

    @Test
    fun resetClearsEverything() {
        val pose = PalmPose()
        settle(pose, hand(curl = 1f))
        pose.reset()
        assertFalse(pose.hasHand)
        assertEquals(0f, pose.squeeze, 1e-6f)
        assertEquals(0f, pose.force, 1e-6f)
    }

    @Test
    fun degenerateInputDoesNotCrashOrProduceNaN() {
        val pose = PalmPose()
        val flat = HandLandmarks(FloatArray(HandLandmarks.COUNT), FloatArray(HandLandmarks.COUNT))
        settle(pose, flat)
        assertFalse("쥠이 NaN", pose.squeeze.isNaN())
        assertFalse("크기가 NaN", pose.span.isNaN())
        assertFalse("힘이 NaN", pose.force.isNaN())
    }
}
