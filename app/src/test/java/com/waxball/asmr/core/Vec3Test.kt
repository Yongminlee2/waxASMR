package com.waxball.asmr.core

import org.junit.Assert.assertEquals
import org.junit.Test

class Vec3Test {

    @Test
    fun crossProductFollowsRightHandRule() {
        val c = Vec3(1f, 0f, 0f) cross Vec3(0f, 1f, 0f)
        assertEquals(0f, c.x, 1e-6f); assertEquals(0f, c.y, 1e-6f); assertEquals(1f, c.z, 1e-6f)
    }

    @Test
    fun normalizedHasUnitLength() {
        assertEquals(1f, Vec3(3f, -4f, 12f).normalized().length(), 1e-6f)
    }

    @Test
    fun normalizingZeroDoesNotProduceNaN() {
        val n = Vec3.ZERO.normalized()
        assertEquals(0f, n.length(), 1e-9f)
    }

    @Test
    fun dotOfPerpendicularVectorsIsZero() {
        assertEquals(0f, Vec3(1f, 0f, 0f) dot Vec3(0f, 0f, 1f), 1e-6f)
    }
}
