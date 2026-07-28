package com.waxball.asmr.ar

import com.waxball.asmr.core.Weapon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HandGestureTest {

    /** 손 모양 하나를 충분히 오래 보여 준다. */
    private fun hold(gesture: HandGesture, pose: PalmPose, hand: HandLandmarks, frames: Int = 12) {
        repeat(frames) {
            pose.update(hand, 1f / 60f)
            gesture.update(pose)
        }
    }

    private fun settled(hand: HandLandmarks): Pair<HandGesture, PalmPose> {
        val pose = PalmPose()
        val gesture = HandGesture()
        TestHand.settle(pose, hand)
        repeat(12) { gesture.update(pose) }
        return gesture to pose
    }

    @Test
    fun anOpenRestingHandGripsNothing() {
        val (gesture, _) = settled(TestHand.of(curl = 0f, pinch = 0f))
        assertEquals(Grip.NONE, gesture.grip)
    }

    @Test
    fun thumbAndIndexTogetherIsAPinch() {
        val (gesture, _) = settled(TestHand.of(curl = 0.2f, pinch = 1f))
        assertEquals(Grip.PINCH, gesture.grip)
    }

    @Test
    fun aSlidingFingertipIsAScratch() {
        val pose = PalmPose()
        val gesture = HandGesture()
        TestHand.settle(pose, TestHand.of(curl = 0.4f))
        for (step in 1..12) {
            pose.update(TestHand.of(curl = 0.4f, indexTipSweep = step * 0.06f), 1f / 60f)
            gesture.update(pose)
        }
        assertEquals(Grip.SCRATCH, gesture.grip)
    }

    @Test
    fun closingTheHandIsASqueeze() {
        val pose = PalmPose()
        val gesture = HandGesture()
        TestHand.settle(pose, TestHand.of(curl = 0f))
        for (step in 1..12) {
            pose.update(TestHand.of(curl = step / 12f), 1f / 60f)
            gesture.update(pose)
        }
        assertEquals(Grip.SQUEEZE, gesture.grip)
    }

    @Test
    fun pinchWinsOverSqueeze() {
        // 집으면서 손을 오므리면 집기로 본다. 좁게 뜯는 것이 의도다.
        val pose = PalmPose()
        val gesture = HandGesture()
        TestHand.settle(pose, TestHand.of(curl = 0f, pinch = 0f))
        for (step in 1..12) {
            pose.update(TestHand.of(curl = step / 24f, pinch = step / 12f), 1f / 60f)
            gesture.update(pose)
        }
        assertEquals(Grip.PINCH, gesture.grip)
    }

    @Test
    fun oneStrayFrameDoesNotChangeTheGrip() {
        val (gesture, pose) = settled(TestHand.of(curl = 0.2f, pinch = 1f))
        assertEquals(Grip.PINCH, gesture.grip)

        // 인식이 한 프레임 튀어 엄지를 놓친 상황
        pose.update(TestHand.of(curl = 0.2f, pinch = 0f), 1f / 60f)
        gesture.update(pose)
        assertEquals("한 프레임 튄 것으로 도구가 바뀜", Grip.PINCH, gesture.grip)
    }

    @Test
    fun alternatingNoiseDoesNotFlipTheGripBackAndForth() {
        val pose = PalmPose()
        val gesture = HandGesture()
        TestHand.settle(pose, TestHand.of(curl = 0.2f, pinch = 1f))
        repeat(12) { gesture.update(pose) }
        val before = gesture.grip

        var flips = 0
        var previous = before
        repeat(30) { i ->
            pose.update(TestHand.of(curl = 0.2f, pinch = if (i % 2 == 0) 1f else 0.45f), 1f / 60f)
            gesture.update(pose)
            if (gesture.grip != previous) { flips++; previous = gesture.grip }
        }
        assertEquals("떨리는 입력에 도구가 계속 바뀜 ($flips 번)", 0, flips)
    }

    @Test
    fun aSustainedChangeDoesSwitchTheGrip() {
        val (gesture, pose) = settled(TestHand.of(curl = 0.2f, pinch = 1f))
        assertEquals(Grip.PINCH, gesture.grip)
        hold(gesture, pose, TestHand.of(curl = 0f, pinch = 0f), frames = 20)
        assertEquals("계속 폈는데도 집기가 안 풀림", Grip.NONE, gesture.grip)
    }

    @Test
    fun losingTheHandDropsTheGripAtOnce() {
        val (gesture, _) = settled(TestHand.of(curl = 0.2f, pinch = 1f))
        gesture.reset()
        assertEquals(Grip.NONE, gesture.grip)
    }

    @Test
    fun eachGripMapsToItsTool() {
        assertEquals(Weapon.NAIL, Grip.PINCH.weapon())
        assertEquals(Weapon.FINGER, Grip.SCRATCH.weapon())
        assertEquals(Weapon.FIST, Grip.SQUEEZE.weapon())
        assertNull(Grip.NONE.weapon())
    }
}
