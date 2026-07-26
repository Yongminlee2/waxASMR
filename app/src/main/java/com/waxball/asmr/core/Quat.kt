package com.waxball.asmr.core

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 회전 쿼터니언. 볼을 굴리는 회전과 떨어진 조각이 구르는 회전에 쓴다.
 * 오일러 각을 쓰면 위아래로 많이 굴렸을 때 축이 엉키는데(짐벌락), 쿼터니언은 그 문제가 없다.
 */
data class Quat(val x: Float, val y: Float, val z: Float, val w: Float) {

    operator fun times(o: Quat) = Quat(
        w * o.x + x * o.w + y * o.z - z * o.y,
        w * o.y - x * o.z + y * o.w + z * o.x,
        w * o.z + x * o.y - y * o.x + z * o.w,
        w * o.w - x * o.x - y * o.y - z * o.z,
    )

    fun normalized(): Quat {
        val n = sqrt(x * x + y * y + z * z + w * w)
        return if (n < 1e-9f) IDENTITY else Quat(x / n, y / n, z / n, w / n)
    }

    fun rotate(v: Vec3): Vec3 {
        val u = Vec3(x, y, z)
        val uv = u cross v
        val uuv = u cross uv
        return v + (uv * w + uuv) * 2f
    }

    /** 3x3 회전 행렬을 out[offset..offset+8]에 행 우선으로 쓴다. */
    fun toMatrix3(out: FloatArray, offset: Int) {
        val xx = x * x; val yy = y * y; val zz = z * z
        val xy = x * y; val xz = x * z; val yz = y * z
        val wx = w * x; val wy = w * y; val wz = w * z
        out[offset] = 1f - 2f * (yy + zz); out[offset + 1] = 2f * (xy - wz); out[offset + 2] = 2f * (xz + wy)
        out[offset + 3] = 2f * (xy + wz); out[offset + 4] = 1f - 2f * (xx + zz); out[offset + 5] = 2f * (yz - wx)
        out[offset + 6] = 2f * (xz - wy); out[offset + 7] = 2f * (yz + wx); out[offset + 8] = 1f - 2f * (xx + yy)
    }

    companion object {
        val IDENTITY = Quat(0f, 0f, 0f, 1f)

        fun axisAngle(axis: Vec3, radians: Float): Quat {
            val a = axis.normalized()
            val h = radians * 0.5f
            val s = sin(h)
            return Quat(a.x * s, a.y * s, a.z * s, cos(h))
        }
    }
}
