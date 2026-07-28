package com.waxball.asmr.ar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos

class PalmPoseGestureInputTest {

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
    fun openHandIsNotPinching() {
        val pose = PalmPose()
        TestHand.settle(pose, TestHand.of(pinch = 0f))
        assertTrue("엄지를 벌렸는데 집기 비율이 ${pose.pinchRatio}", pose.pinchRatio > 0.7f)
    }

    @Test
    fun pinchingBringsTheRatioDown() {
        val pose = PalmPose()
        TestHand.settle(pose, TestHand.of(pinch = 1f))
        assertTrue("집었는데 비율이 ${pose.pinchRatio}", pose.pinchRatio < 0.35f)
    }

    @Test
    fun pinchRatioSurvivesDistance() {
        // 비율이므로 손이 멀어져도 값이 유지돼야 한다.
        val near = PalmPose().also { TestHand.settle(it, TestHand.of(pinch = 1f, scale = 0.3f)) }
        val far = PalmPose().also { TestHand.settle(it, TestHand.of(pinch = 1f, scale = 0.12f)) }
        assertEquals("거리에 따라 집기 판정이 달라짐", near.pinchRatio, far.pinchRatio, 0.08f)
    }

    @Test
    fun stillFingersHaveNoTipSpeed() {
        val pose = PalmPose()
        TestHand.settle(pose, TestHand.of(curl = 0.4f))
        repeat(20) { pose.update(TestHand.of(curl = 0.4f), 1f / 60f) }
        assertTrue("가만히 있는데 손끝 속도가 ${pose.tipSpeed}", pose.tipSpeed < 0.15f)
    }

    @Test
    fun slidingTheFingertipRaisesTipSpeed() {
        val pose = PalmPose()
        TestHand.settle(pose, TestHand.of(curl = 0.4f))
        var peak = 0f
        for (step in 1..10) {
            pose.update(TestHand.of(curl = 0.4f, indexTipShiftX = step * 0.09f), 1f / 60f)
            if (pose.tipSpeed > peak) peak = pose.tipSpeed
        }
        assertTrue("손끝을 문질렀는데 속도가 $peak", peak > 1.2f)
    }

    @Test
    fun movingTheWholeHandDoesNotCountAsScratching() {
        // 손 전체가 움직이는 것은 긁는 것이 아니다. 손바닥 기준 상대 속도여야 한다.
        val pose = PalmPose()
        TestHand.settle(pose, TestHand.of(curl = 0.4f, centerX = 0.3f))
        var peak = 0f
        for (step in 1..10) {
            pose.update(TestHand.of(curl = 0.4f, centerX = 0.3f + step * 0.04f), 1f / 60f)
            if (pose.tipSpeed > peak) peak = pose.tipSpeed
        }
        assertTrue("손을 통째로 움직였는데 긁는 것으로 잡힘 ($peak)", peak < 1.0f)
    }

    @Test
    fun tipsAreReportedInScreenCoordinates() {
        val pose = PalmPose()
        TestHand.settle(pose, TestHand.of(curl = 0f, centerX = 0.4f, centerY = 0.6f))
        assertTrue("검지 끝이 손바닥 위쪽에 있어야 함", pose.indexTipY < pose.centerY)
        assertTrue("검지 끝 x가 화면 안에 없음", pose.indexTipX in 0f..1f)
        assertTrue("엄지 끝 x가 화면 안에 없음", pose.thumbTipX in 0f..1f)
    }

    @Test
    fun degenerateInputDoesNotProduceNaN() {
        val pose = PalmPose()
        val flat = HandLandmarks(FloatArray(HandLandmarks.COUNT), FloatArray(HandLandmarks.COUNT))
        TestHand.settle(pose, flat)
        assertFalse("롤이 NaN", pose.roll.isNaN())
        assertFalse("집기 비율이 NaN", pose.pinchRatio.isNaN())
        assertFalse("손끝 속도가 NaN", pose.tipSpeed.isNaN())
    }

    @Test
    fun resetClearsTheNewValues() {
        val pose = PalmPose()
        TestHand.settle(pose, TestHand.of(curl = 1f, pinch = 1f, rollDeg = 40f))
        pose.reset()
        assertEquals(0f, pose.roll, 1e-6f)
        assertEquals(0f, pose.tipSpeed, 1e-6f)
        assertEquals(1f, pose.pinchRatio, 1e-6f)
    }
}
