package com.waxball.asmr.gl

import com.waxball.asmr.core.Quat
import com.waxball.asmr.core.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RayMathTest {

    private val width = 1000
    private val height = 1000
    private val fov = 42f
    private val distance = 6f

    private fun ray(
        x: Float,
        y: Float,
        scale: Float = 1f,
        ox: Float = 0f,
        oy: Float = 0f,
        oz: Float = 0f,
        rotation: Quat = Quat.IDENTITY,
    ): Pair<Vec3, Vec3> {
        val out = FloatArray(6)
        RayMath.screenToRay(x, y, width, height, fov, distance, rotation, scale, ox, oy, oz, out)
        return Vec3(out[0], out[1], out[2]) to Vec3(out[3], out[4], out[5])
    }

    @Test
    fun theCentreOfTheScreenHitsTheFrontOfTheBall() {
        val (origin, dir) = ray(500f, 500f)
        val hit = Picker.hitDirection(origin, dir)
        assertNotNull("화면 한가운데인데 볼을 빗나감", hit)
        assertTrue("앞면이 아니라 ${hit!!.z} 쪽에 맞음", hit.z > 0.9f)
    }

    @Test
    fun touchingTheLeftOfTheBallHitsItsLeftSide() {
        val (origin, dir) = ray(430f, 500f)
        val hit = Picker.hitDirection(origin, dir)
        assertNotNull("볼 왼쪽을 눌렀는데 빗나감", hit)
        assertTrue("왼쪽을 눌렀는데 x가 ${hit!!.x}", hit.x < -0.15f)
    }

    @Test
    fun touchingTheRightOfTheBallHitsItsRightSide() {
        val (origin, dir) = ray(570f, 500f)
        val hit = Picker.hitDirection(origin, dir)
        assertNotNull("볼 오른쪽을 눌렀는데 빗나감", hit)
        assertTrue("오른쪽을 눌렀는데 x가 ${hit!!.x}", hit.x > 0.15f)
    }

    @Test
    fun farFromTheBallMissesIt() {
        val (origin, dir) = ray(20f, 20f)
        assertEquals("볼에서 한참 떨어진 곳이 맞았다고 나옴", null, Picker.hitDirection(origin, dir))
    }

    @Test
    fun aBallMovedAsideIsStillHitAtItsOwnPlace() {
        // 손바닥 모드는 볼을 세계 좌표로 옮겨 놓는다. 오프셋을 안 빼면
        // 손끝이 볼 위에 있어도 늘 빗나가거나 엉뚱한 자리가 깨진다.
        val offsetX = 1.4f
        val tanHalf = Math.tan(Math.toRadians(fov / 2.0)).toFloat()
        // 볼 중심이 찍히는 화면 x
        val screenX = (offsetX / (distance * tanHalf) + 1f) * 0.5f * width

        val (origin, dir) = ray(screenX, 500f, ox = offsetX)
        val hit = Picker.hitDirection(origin, dir)
        assertNotNull("옮겨 놓은 볼의 한가운데를 눌렀는데 빗나감", hit)
        assertTrue("옮겨 놓은 볼의 앞면이 아니라 ${hit!!.z}", hit.z > 0.9f)
    }

    @Test
    fun aBallMovedAsideIsMissedWhereItNoLongerIs() {
        val (origin, dir) = ray(500f, 500f, ox = 3.5f)
        assertEquals("볼이 없는 자리가 맞았다고 나옴", null, Picker.hitDirection(origin, dir))
    }

    @Test
    fun aSmallerBallIsStillHitAtItsCentre() {
        val (origin, dir) = ray(500f, 500f, scale = 0.4f)
        val hit = Picker.hitDirection(origin, dir)
        assertNotNull("작은 볼의 한가운데를 눌렀는데 빗나감", hit)
        assertTrue(hit!!.z > 0.9f)
    }

    @Test
    fun rotatingTheBallRotatesWhichShardIsHit() {
        val straight = ray(430f, 500f)
        val turned = ray(430f, 500f, rotation = Quat.axisAngle(Vec3(0f, 1f, 0f), Math.PI.toFloat()))
        val a = Picker.hitDirection(straight.first, straight.second)!!
        val b = Picker.hitDirection(turned.first, turned.second)!!
        assertTrue("볼을 반 바퀴 돌렸는데 같은 자리가 맞음", a.x * b.x < 0f)
    }
}
