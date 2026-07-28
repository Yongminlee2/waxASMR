package com.waxball.asmr.gl

import com.waxball.asmr.core.Quat
import com.waxball.asmr.core.Vec3
import kotlin.math.tan

/**
 * 화면 좌표를 볼 좌표계 광선으로 바꾼다.
 *
 * BallRenderer 안에 두면 GL import 때문에 PC에서 검증할 수 없다. 계산만 떼어 둔다.
 * 손끝이 어느 조각에 닿았는지가 여기서 갈리므로, 눈으로 확인할 일이 아니다.
 */
object RayMath {

    /**
     * @param rotation 볼이 돌아간 자세. 광선을 거꾸로 돌려 볼 좌표계로 옮긴다
     * @param scale 볼을 그릴 때의 크기 배율
     * @param offsetX 볼을 세계 좌표에서 옮겨 놓은 거리. 손바닥 모드가 쓴다
     * @param out 원점 3개 + 방향 3개
     */
    fun screenToRay(
        x: Float,
        y: Float,
        viewportWidth: Int,
        viewportHeight: Int,
        fovDeg: Float,
        cameraDistance: Float,
        rotation: Quat,
        scale: Float,
        offsetX: Float,
        offsetY: Float,
        offsetZ: Float,
        out: FloatArray,
    ) {
        if (viewportWidth <= 0 || viewportHeight <= 0) return
        val tanHalf = tan(Math.toRadians(fovDeg / 2.0)).toFloat()
        val aspect = viewportWidth.toFloat() / viewportHeight

        val ndcX = (2f * x / viewportWidth - 1f) * tanHalf * aspect
        val ndcY = (1f - 2f * y / viewportHeight) * tanHalf

        // 볼을 옮겨 놓은 것은 카메라를 반대로 옮긴 것과 같다.
        val worldOrigin = Vec3(-offsetX, -offsetY, cameraDistance - offsetZ)
        val worldDir = Vec3(ndcX, ndcY, -1f)

        val inv = Quat(-rotation.x, -rotation.y, -rotation.z, rotation.w)
        val s = scale.coerceAtLeast(1e-4f)
        val o = inv.rotate(worldOrigin) * (1f / s)
        val d = inv.rotate(worldDir)

        out[0] = o.x; out[1] = o.y; out[2] = o.z
        out[3] = d.x; out[4] = d.y; out[5] = d.z
    }
}
