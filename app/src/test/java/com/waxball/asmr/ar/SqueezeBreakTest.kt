package com.waxball.asmr.ar

import com.waxball.asmr.core.BreakModel
import com.waxball.asmr.core.EventQueue
import com.waxball.asmr.core.Icosphere
import com.waxball.asmr.core.ShardSplitter
import com.waxball.asmr.core.SoundProfile
import com.waxball.asmr.core.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * 손을 쥐었을 때 기존 파괴 규칙 위에서 의도대로 부서지는지 고정한다.
 * 손바닥 모드가 파괴 쪽 코드를 고치지 않는다는 것도 이 테스트가 지켜 준다.
 */
class SqueezeBreakTest {

    private fun model() = BreakModel(
        ShardSplitter.split(Icosphere.build(4), 150, Random(3)),
        SoundProfile.hardWax(),
        EventQueue(16384),
    )

    /** 손바닥 모드가 하는 것과 같은 호출. 손가락이 감싸므로 구 전체가 대상이다. */
    private fun squeeze(m: BreakModel, force: Float, dt: Float = 1f / 60f) {
        m.pressArea(Vec3(0f, 0f, 1f), -1f, force, dt, 0f)
    }

    @Test
    fun squeezingBreaksTheShell() {
        val m = model()
        repeat(30) { squeeze(m, 3f) }
        assertTrue("쥐었는데 안 부서짐", m.shellProgress > 0f)
    }

    @Test
    fun zeroForceBreaksNothing() {
        val m = model()
        repeat(60) { squeeze(m, 0f) }
        assertEquals("힘이 0인데 부서짐", 0f, m.shellProgress, 1e-6f)
    }

    @Test
    fun squeezingCrushesTheWholeFacingSideNotOneSpot() {
        // 손에 쥐면 닿는 면 전체가 으스러진다. 한 점만 파이면 안 된다.
        val m = model()
        repeat(30) { squeeze(m, 3f) }
        val touched = m.state.count { it > 0 }
        assertTrue("한 점만 부서짐 (${touched}개)", touched >= 15)
    }

    @Test
    fun squeezingReachesTheFarSideToo() {
        // 손에 쥐면 손가락이 감싸므로 뒤쪽도 부서져야 한다.
        // 앞쪽 뚜껑만 깨지면 쥐는 게 아니라 파는 것처럼 보인다.
        val m = model()
        repeat(60) { squeeze(m, 3f) }

        val back = m.shards.shards.filter { it.center.z < -0.5f }
        assertTrue("뒤쪽 조각이 없는 볼이라 판정 불가", back.size >= 5)
        assertTrue(
            "뒤쪽은 하나도 안 부서짐",
            back.count { m.state[it.id] > 0 } >= back.size / 2,
        )
    }

    @Test
    fun theFrontStillCrushesHarderThanTheBack() {
        // 전체가 대상이되 닿는 세기는 앞쪽이 강해야 자연스럽다.
        // 오래 쥐면 앞뒤가 다 완파돼서 차이를 잴 수 없다. 포화 전에 본다.
        val m = model()
        repeat(8) { squeeze(m, 2f) }

        val front = m.shards.shards.filter { it.center.z > 0.5f }
        val back = m.shards.shards.filter { it.center.z < -0.5f }
        val frontDamage = front.sumOf { m.state[it.id] }.toFloat() / front.size
        val backDamage = back.sumOf { m.state[it.id] }.toFloat() / back.size
        assertTrue("앞뒤 세기가 같음 (앞 $frontDamage, 뒤 $backDamage)", frontDamage > backDamage)
    }

    @Test
    fun harderSqueezeBreaksMore() {
        fun progressAt(force: Float): Float {
            val m = model()
            repeat(30) { squeeze(m, force) }
            return m.shellProgress
        }
        assertTrue(progressAt(4f) > progressAt(1.5f))
    }

    @Test
    fun aRealisticSqueezeGestureActuallyBreaksSomething() {
        // PalmPose가 내는 힘이 BreakModel이 기대하는 크기인지 끝에서 끝까지 확인한다.
        // 여기가 어긋나면 손을 쥐어도 아무 일도 안 일어난다.
        val pose = PalmPose()
        val m = model()
        val dt = 1f / 60f

        fun hand(curl: Float): HandLandmarks {
            val x = FloatArray(HandLandmarks.COUNT)
            val y = FloatArray(HandLandmarks.COUNT)
            fun put(i: Int, dx: Float, dy: Float) { x[i] = 0.5f + dx * 0.2f; y[i] = 0.5f + dy * 0.2f }
            put(HandLandmarks.WRIST, 0f, 0.6f)
            put(HandLandmarks.INDEX_MCP, -0.45f, 0f)
            put(HandLandmarks.MIDDLE_MCP, -0.15f, 0f)
            put(HandLandmarks.RING_MCP, 0.15f, 0f)
            put(HandLandmarks.PINKY_MCP, 0.45f, 0f)
            val reach = 1.1f - 0.95f * curl
            put(HandLandmarks.INDEX_TIP, -0.45f, -reach)
            put(HandLandmarks.MIDDLE_TIP, -0.15f, -reach * 1.05f)
            put(HandLandmarks.RING_TIP, 0.15f, -reach)
            put(HandLandmarks.PINKY_TIP, 0.45f, -reach * 0.9f)
            return HandLandmarks(x, y)
        }

        repeat(60) { pose.update(hand(0f), dt) }          // 편 손으로 안정
        repeat(3) {                                        // 쥐었다 폈다 세 번
            for (step in 0..10) {
                pose.update(hand(step / 10f), dt)
                if (pose.force > 0f) squeeze(m, pose.force, dt)
            }
            repeat(20) { pose.update(hand(0f), dt) }
        }

        assertTrue("손을 세 번 쥐었는데 아무것도 안 부서짐", m.shellProgress > 0f)
    }
}
