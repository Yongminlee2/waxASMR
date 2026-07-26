package com.waxball.asmr.gl

import com.waxball.asmr.core.Quat
import com.waxball.asmr.core.Vec3
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * 떨어져 나간 조각의 낙하와 누적.
 *
 * 조각은 사라지지 않는다. 화면 아래에 부스러기가 쌓이는 게 눈에 보이는 진행감이다.
 * 슬롯은 조각 번호와 1:1이라 따로 관리할 목록이 없다.
 *
 * 물리엔진을 쓰지 않는다. 필요한 건 중력·회전·바닥 튕김뿐이다.
 */
class Debris(private val capacity: Int, private val rng: Random = Random(0)) {

    private val active = BooleanArray(capacity)
    private val resting = BooleanArray(capacity)
    private val landed = BooleanArray(capacity)

    // 낙하 오프셋과 속도
    private val ox = FloatArray(capacity); private val oy = FloatArray(capacity); private val oz = FloatArray(capacity)
    private val vx = FloatArray(capacity); private val vy = FloatArray(capacity); private val vz = FloatArray(capacity)

    // 분리 순간의 볼 회전(R0)과 그 뒤 구르는 회전
    private val r0x = FloatArray(capacity); private val r0y = FloatArray(capacity)
    private val r0z = FloatArray(capacity); private val r0w = FloatArray(capacity)
    private val tx = FloatArray(capacity); private val ty = FloatArray(capacity)
    private val tz = FloatArray(capacity); private val tw = FloatArray(capacity)
    private val wx = FloatArray(capacity); private val wy = FloatArray(capacity); private val wz = FloatArray(capacity)

    private val halfSize = FloatArray(capacity)
    private val bounces = IntArray(capacity)

    /**
     * 떨어지기 직전 매달려 있는 시간(프레임).
     * 금이 다 갔는데도 잠깐 버티다 놓이는 그 순간이 깨는 맛의 정체다.
     * 바로 떨어뜨리면 "툭" 하고 사라질 뿐 아무 감흥이 없다.
     */
    private val hang = IntArray(capacity)

    /** 바닥에 쌓인 뒤 몇 번 더 뭉갰는지. 뭉갤수록 작아지다 가루가 된다. */
    private val crush = IntArray(capacity)

    var count = 0
        private set

    fun isActive(shardId: Int) = shardId in 0 until capacity && active[shardId]

    /**
     * 조각이 떨어지기 시작한다.
     *
     * @param outward 조각이 붙어 있던 방향. 이쪽으로 살짝 튕겨 나간다
     * @param ballRotation 분리 순간의 볼 회전. 이 자세로 굳은 채 떨어진다
     */
    fun spawn(shardId: Int, outward: Vec3, areaFrac: Float, ballRotation: Quat, hangFrames: Int = 0) {
        if (shardId < 0 || shardId >= capacity || active[shardId]) return

        active[shardId] = true
        resting[shardId] = false
        landed[shardId] = false
        bounces[shardId] = 0
        crush[shardId] = 0
        // 큰 판일수록 오래 매달렸다 떨어져야 무게가 느껴진다.
        hang[shardId] = hangFrames + (areaFrac * 260f).toInt().coerceAtMost(14)
        count++

        val n = outward.normalized()
        ox[shardId] = 0f; oy[shardId] = 0f; oz[shardId] = 0f
        vx[shardId] = n.x * 0.35f + jitter(0.25f)
        vy[shardId] = n.y * 0.15f + 0.1f
        vz[shardId] = n.z * 0.35f + jitter(0.25f)

        r0x[shardId] = ballRotation.x; r0y[shardId] = ballRotation.y
        r0z[shardId] = ballRotation.z; r0w[shardId] = ballRotation.w
        tx[shardId] = 0f; ty[shardId] = 0f; tz[shardId] = 0f; tw[shardId] = 1f
        wx[shardId] = jitter(6f); wy[shardId] = jitter(6f); wz[shardId] = jitter(6f)

        // 면적이 넓을수록 큰 조각이라 바닥에서 더 높이 눕는다.
        halfSize[shardId] = 0.05f + sqrt(areaFrac.coerceAtLeast(0f)) * 0.6f
    }

    /**
     * @param centers 조각별 회전 중심(볼 좌표계). BallGeometry가 준다
     * @param onLand 조각이 바닥에 자리잡는 순간 한 번만 호출된다
     */
    fun update(
        dt: Float,
        floorY: Float,
        centers: FloatArray,
        onLand: (shardId: Int, pan: Float, sizeHint: Float) -> Unit,
    ) {
        for (i in 0 until capacity) {
            if (!active[i] || resting[i]) continue

            if (hang[i] > 0) { hang[i]--; continue }

            vy[i] -= GRAVITY * dt
            ox[i] += vx[i] * dt
            oy[i] += vy[i] * dt
            oz[i] += vz[i] * dt

            integrateSpin(i, dt)

            // 분리 시점의 세계 좌표 중심 + 낙하 오프셋
            val baseY = rotatedY(i, centers)
            val y = baseY + oy[i]
            val rest = floorY + halfSize[i]
            if (y <= rest) {
                oy[i] = rest - baseY
                bounces[i]++
                vy[i] = -vy[i] * BOUNCE
                vx[i] *= FRICTION
                vz[i] *= FRICTION
                wx[i] *= FRICTION; wy[i] *= FRICTION; wz[i] *= FRICTION

                if (bounces[i] >= 2 || abs(vy[i]) < 0.4f) {
                    resting[i] = true
                    vx[i] = 0f; vy[i] = 0f; vz[i] = 0f
                    wx[i] = 0f; wy[i] = 0f; wz[i] = 0f
                    if (!landed[i]) {
                        landed[i] = true
                        val pan = (rotatedX(i, centers) + ox[i]).coerceIn(-1f, 1f)
                        onLand(i, pan, halfSize[i])
                    }
                }
            }
        }
    }

    /**
     * 조각 하나의 3x4 변환을 out[offset..offset+11]에 행 우선으로 쓴다.
     * 셰이더는 이 세 줄로 정점을 세계 좌표에 놓는다.
     */
    fun writeMatrix(shardId: Int, centers: FloatArray, out: FloatArray, offset: Int) {
        val cx = centers[shardId * 3]
        val cy = centers[shardId * 3 + 1]
        val cz = centers[shardId * 3 + 2]

        // A = 구르는 회전 × 분리 시점 회전
        val r0 = Quat(r0x[shardId], r0y[shardId], r0z[shardId], r0w[shardId])
        val a = Quat(tx[shardId], ty[shardId], tz[shardId], tw[shardId]) * r0
        a.toMatrix3(scratch3x3, 0)
        // 3x3을 3x4 배치의 회전 자리로 옮긴다. 네 번째 열은 평행이동이다.
        out[offset] = scratch3x3[0]; out[offset + 1] = scratch3x3[1]; out[offset + 2] = scratch3x3[2]
        out[offset + 4] = scratch3x3[3]; out[offset + 5] = scratch3x3[4]; out[offset + 6] = scratch3x3[5]
        out[offset + 8] = scratch3x3[6]; out[offset + 9] = scratch3x3[7]; out[offset + 10] = scratch3x3[8]

        // t = R0*c + 낙하오프셋 - A*c
        val base = r0.rotate(Vec3(cx, cy, cz))
        val moved = a.rotate(Vec3(cx, cy, cz))

        out[offset + 3] = base.x + ox[shardId] - moved.x
        out[offset + 7] = base.y + oy[shardId] - moved.y
        out[offset + 11] = base.z + oz[shardId] - moved.z
    }

    /** 뭉갠 정도에 따라 조각이 줄어드는 비율. 셰이더가 이만큼 조각을 오므린다. */
    fun shrinkOf(shardId: Int): Float =
        if (shardId in 0 until capacity) crush[shardId] * 0.3f else 0f

    /**
     * 바닥에 쌓인 부스러기를 문질러 더 잘게 부순다.
     *
     * 영상에서 진짜 재미있는 구간은 손 안의 무더기를 계속 비비는 부분이다.
     * 다 깨고 나면 할 게 없다는 문제가 여기서 풀린다.
     *
     * @param worldX 손가락이 닿은 가로 위치(볼 좌표계)
     * @return 이번에 부순 조각 수
     */
    fun crushNear(
        worldX: Float,
        radius: Float,
        maxCount: Int,
        centers: FloatArray,
        onCrunch: (shardId: Int, pan: Float, sizeHint: Float) -> Unit,
    ): Int {
        var crushed = 0
        for (i in 0 until capacity) {
            if (crushed >= maxCount) break
            if (!active[i] || !resting[i]) continue
            if (crush[i] >= MAX_CRUSH) continue

            val x = rotatedX(i, centers) + ox[i]
            if (abs(x - worldX) > radius) continue

            crush[i]++
            crushed++
            onCrunch(i, x.coerceIn(-1f, 1f), halfSize[i] * (1f - crush[i] * 0.3f))

            // 완전히 가루가 되면 사라진다.
            if (crush[i] >= MAX_CRUSH) {
                active[i] = false
                count--
            }
        }
        return crushed
    }

    /** 바닥에 자리잡아 뭉갤 수 있는 조각이 남았는지. */
    fun hasCrushableDebris(): Boolean {
        for (i in 0 until capacity) {
            if (active[i] && resting[i] && crush[i] < MAX_CRUSH) return true
        }
        return false
    }

    fun clear() {
        java.util.Arrays.fill(active, false)
        java.util.Arrays.fill(resting, false)
        java.util.Arrays.fill(landed, false)
        java.util.Arrays.fill(hang, 0)
        java.util.Arrays.fill(crush, 0)
        count = 0
    }

    private fun integrateSpin(i: Int, dt: Float) {
        val h = 0.5f * dt
        val qx = tx[i]; val qy = ty[i]; val qz = tz[i]; val qw = tw[i]
        var nx = qx + h * (wx[i] * qw + wy[i] * qz - wz[i] * qy)
        var ny = qy + h * (wy[i] * qw + wz[i] * qx - wx[i] * qz)
        var nz = qz + h * (wz[i] * qw + wx[i] * qy - wy[i] * qx)
        var nw = qw - h * (wx[i] * qx + wy[i] * qy + wz[i] * qz)
        val len = sqrt(nx * nx + ny * ny + nz * nz + nw * nw)
        if (len > 1e-9f) { nx /= len; ny /= len; nz /= len; nw /= len } else { nx = 0f; ny = 0f; nz = 0f; nw = 1f }
        tx[i] = nx; ty[i] = ny; tz[i] = nz; tw[i] = nw
    }

    private fun rotatedY(i: Int, centers: FloatArray): Float {
        val r0 = Quat(r0x[i], r0y[i], r0z[i], r0w[i])
        return r0.rotate(Vec3(centers[i * 3], centers[i * 3 + 1], centers[i * 3 + 2])).y
    }

    private fun rotatedX(i: Int, centers: FloatArray): Float {
        val r0 = Quat(r0x[i], r0y[i], r0z[i], r0w[i])
        return r0.rotate(Vec3(centers[i * 3], centers[i * 3 + 1], centers[i * 3 + 2])).x
    }

    private val scratch3x3 = FloatArray(9)

    private fun jitter(scale: Float) = (rng.nextFloat() * 2f - 1f) * scale

    private companion object {
        /** 이만큼 뭉개면 가루가 되어 사라진다. */
        const val MAX_CRUSH = 3
        const val GRAVITY = 9.8f
        const val BOUNCE = 0.25f
        const val FRICTION = 0.55f
    }
}
