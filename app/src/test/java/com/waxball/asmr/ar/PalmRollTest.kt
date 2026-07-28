package com.waxball.asmr.ar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos

/**
 * 손바닥 롤. 볼이 손 위에서 구르고 부스러기가 흐르는 방향이 여기서 나온다.
 */
class PalmRollTest {

    @Test
    fun flatHandHasNoRoll() {
        val pose = PalmPose()
        TestHand.settle(pose, TestHand.of(rollDeg = 0f))
        assertEquals("눕히지 않은 손인데 롤이 ${pose.roll}", 0f, pose.roll, 0.05f)
    }

    @Test
    fun tiltingTheHandFlipsTheSignOfRoll() {
        val left = PalmPose().also { TestHand.settle(it, TestHand.of(rollDeg = -30f)) }
        val right = PalmPose().also { TestHand.settle(it, TestHand.of(rollDeg = 30f)) }
        assertTrue(
            "좌우로 눕혔는데 롤 부호가 같음 (${left.roll} vs ${right.roll})",
            left.roll * right.roll < 0f,
        )
    }

    @Test
    fun rollSurvivesTheHandClosing() {
        // 뿌리 사이 벡터로 재므로 손가락을 굽혀도 자세는 그대로여야 한다.
        val open = PalmPose().also { TestHand.settle(it, TestHand.of(curl = 0f, rollDeg = 25f)) }
        val closed = PalmPose().also { TestHand.settle(it, TestHand.of(curl = 1f, rollDeg = 25f)) }
        assertEquals("쥐었더니 볼 구르는 방향이 바뀜", open.roll, closed.roll, 0.05f)
    }

    @Test
    fun theHandDoesNotSpinRightAroundAtTheAngleBoundary() {
        // atan2 값 자체는 ±π에서 부호가 뒤집힌다. 그건 표현의 문제라 어쩔 수 없고,
        // 쓰는 쪽은 sin·cos으로 받는다. 정작 막아야 하는 것은 평활을 각도에 직접 걸었을 때
        // 손이 +178°에서 -178°로 가는 짧은 길 대신 0°를 거쳐 한 바퀴 도는 것이다.
        // 그때 cos(roll)이 -1에서 +1까지 넘어간다.
        val pose = PalmPose()
        TestHand.settle(pose, TestHand.of(rollDeg = 178f))
        var worst = -1f
        repeat(10) {
            pose.update(TestHand.of(rollDeg = -178f), 1f / 60f)
            val c = cos(pose.roll)
            if (c > worst) worst = c
        }
        assertTrue("경계를 지나며 손이 한 바퀴 돌았다 (cos 최대 $worst)", worst < -0.9f)
    }

    @Test
    fun degenerateInputDoesNotProduceNaN() {
        val pose = PalmPose()
        val flat = HandLandmarks(FloatArray(HandLandmarks.COUNT), FloatArray(HandLandmarks.COUNT))
        TestHand.settle(pose, flat)
        assertFalse("롤이 NaN", pose.roll.isNaN())
    }

    @Test
    fun resetClearsTheRoll() {
        val pose = PalmPose()
        TestHand.settle(pose, TestHand.of(curl = 1f, rollDeg = 40f))
        pose.reset()
        assertEquals(0f, pose.roll, 1e-6f)
    }
}
