package com.waxball.asmr.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Mat4Test {

    @Test
    fun identityIsMultiplicativeNeutral() {
        val id = FloatArray(16); Mat4.identity(id)
        val p = FloatArray(16); Mat4.perspective(p, 45f, 1.5f, 0.1f, 100f)
        val out = FloatArray(16); Mat4.multiply(out, p, id)
        for (i in 0 until 16) assertEquals(p[i], out[i], 1e-5f)
    }

    @Test
    fun lookAtPutsTargetOnNegativeZAxis() {
        val view = FloatArray(16)
        Mat4.lookAt(view, Vec3(0f, 0f, 5f), Vec3.ZERO, Vec3.UP)
        val target = Mat4.transformPoint(view, Vec3.ZERO)
        assertEquals(0f, target.x, 1e-5f)
        assertEquals(0f, target.y, 1e-5f)
        assertEquals(-5f, target.z, 1e-5f)
    }

    @Test
    fun perspectiveKeepsNearPlaneInsideClipSpace() {
        val p = FloatArray(16); Mat4.perspective(p, 60f, 1f, 1f, 50f)
        val near = Mat4.transformPoint(p, Vec3(0f, 0f, -1f))
        val far = Mat4.transformPoint(p, Vec3(0f, 0f, -50f))
        assertEquals(-1f, near.z, 1e-4f)
        assertEquals(1f, far.z, 1e-4f)
    }

    @Test
    fun invertRoundTripsToIdentity() {
        val view = FloatArray(16)
        Mat4.lookAt(view, Vec3(2f, 3f, 5f), Vec3(0f, 1f, 0f), Vec3.UP)
        val inv = FloatArray(16)
        assertTrue(Mat4.invert(inv, view))
        val out = FloatArray(16); Mat4.multiply(out, view, inv)
        val id = FloatArray(16); Mat4.identity(id)
        for (i in 0 until 16) assertEquals(id[i], out[i], 1e-4f)
    }

    @Test
    fun quaternionIdentityLeavesVectorsUntouched() {
        val m = FloatArray(16); Mat4.fromQuaternion(m, 0f, 0f, 0f, 1f)
        val v = Mat4.transformPoint(m, Vec3(1f, 2f, 3f))
        assertEquals(1f, v.x, 1e-6f); assertEquals(2f, v.y, 1e-6f); assertEquals(3f, v.z, 1e-6f)
    }

    @Test
    fun quaternionHalfTurnAboutYFlipsXAndZ() {
        val m = FloatArray(16); Mat4.fromQuaternion(m, 0f, 1f, 0f, 0f)
        val v = Mat4.transformPoint(m, Vec3(1f, 0f, 0f))
        assertEquals(-1f, v.x, 1e-5f)
        assertEquals(0f, v.y, 1e-5f)
        assertEquals(0f, v.z, 1e-5f)
    }
}
