package com.waxball.asmr.gl

import com.waxball.asmr.core.Quat
import com.waxball.asmr.core.Vec3
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * 깨진 조각이 고무풍선 안에 갇혀 놀아나는 것.
 *
 * 진짜 왁뿌볼은 왁스 껍질 바깥에 고무풍선이 한 겹 씌워져 있다. 그래서 껍질이 깨져도
 * 조각이 밖으로 나오지 못하고 그 안에서 밀려다닌다. 바닥에 떨어지지 않으니 착지음도
 * 없고, 다 깨진 뒤에도 조각 든 물렁한 주머니가 남아서 계속 만질 거리가 있다.
 *
 * [Debris] 는 중력으로 떨어뜨려 바닥에 쌓는 것이라 여기 쓸 수 없다. 여기에는 바닥이
 * 없고, 대신 풍선 안쪽 벽이 있다.
 *
 * 물리엔진을 쓰지 않는다. 필요한 것은 구면 안에 가두기·감쇠·느린 뒹굴기뿐이다.
 */
class TrappedShards(
    private val capacity: Int,
    /** 풍선 안쪽 반지름(볼 좌표계). 조각 중심이 이 안에 머문다. */
    private val innerRadius: Float = 0.94f,
    private val rng: Random = Random(0),
) {
    private val active = BooleanArray(capacity)

    // 조각이 제자리에서 밀려난 양
    private val ox = FloatArray(capacity)
    private val oy = FloatArray(capacity)
    private val oz = FloatArray(capacity)
    private val vx = FloatArray(capacity)
    private val vy = FloatArray(capacity)
    private val vz = FloatArray(capacity)

    // 분리 순간의 볼 회전과 그 뒤 뒹구는 회전
    private val r0x = FloatArray(capacity); private val r0y = FloatArray(capacity)
    private val r0z = FloatArray(capacity); private val r0w = FloatArray(capacity)
    private val tx = FloatArray(capacity); private val ty = FloatArray(capacity)
    private val tz = FloatArray(capacity); private val tw = FloatArray(capacity)
    private val wx = FloatArray(capacity); private val wy = FloatArray(capacity)
    private val wz = FloatArray(capacity)

    private val halfSize = FloatArray(capacity)

    /**
     * 떨어지기 직전 매달려 있는 시간(프레임).
     * 금이 다 갔는데도 잠깐 버티다 놓이는 그 순간이 깨는 맛의 정체다.
     */
    private val hang = IntArray(capacity)

    var count = 0
        private set

    /**
     * 반죽된 정도 0~1.
     *
     * 실제 왁뿌볼은 껍질이 다 깨진 뒤에도 계속 주무르면 조각이 속 말랑이에
     * 치대져 섞이고, 색이 합쳐지며 바뀐다. 이 값이 오를수록 조각은 작아지고
     * 옅어지며, 속살은 껍질색을 머금는다.
     */
    @Volatile var knead = 0f
        private set

    /**
     * 상한 없이 계속 쌓이는 반죽 총량.
     *
     * [knead] 는 1에서 멈춘다 — 조각이 다 스며들고 색이 다 섞인 상태다.
     * 하지만 진짜 반죽은 거기서 끝이 아니라, 더 치대면 색이 조금씩 계속 달라진다.
     * 그 "1 이후"를 여기에 담는다.
     */
    @Volatile var kneadTotal = 0f
        private set

    fun addKnead(amount: Float) {
        // 반죽은 되돌아가지 않는다. 음수가 들어와도 섞인 색이 다시 갈라지면 이상하다.
        val add = amount.coerceAtLeast(0f)
        knead = (knead + add).coerceIn(0f, 1f)
        kneadTotal += add
    }

    /**
     * 반죽을 최소 이만큼으로 끌어올린다. 껍질이 부서진 비율이 여기로 들어온다.
     * 부수는 것 자체가 치대는 것이라, 처음 조각이 떨어질 때부터 색이 조금씩
     * 섞이기 시작해야 한다. 다 부순 뒤에야 섞이기 시작하면 순서가 어색하다.
     */
    fun raiseKneadTo(target: Float) {
        val t = target.coerceIn(0f, 1f)
        if (t > knead) {
            kneadTotal += t - knead
            knead = t
        }
    }

    /**
     * 우리(풍선 안쪽) 크기 배율. 쥐면 풍선이 눌리고 안의 공간도 같이 줄어야
     * 조각이 밀집된다. 렌더러의 눌림 변환과 같은 값에서 나온다.
     */
    @Volatile private var cageScale = 1f

    fun setCage(scale: Float) {
        cageScale = scale.coerceIn(0.5f, 1f)
    }

    /** 조각 중심이 볼 중심에서 떨어진 거리. 우리가 실제로 가두는지 검증할 때 쓴다. */
    fun radiusOf(shardId: Int, centers: FloatArray): Float {
        val r0 = Quat(r0x[shardId], r0y[shardId], r0z[shardId], r0w[shardId])
        val base = r0.rotate(Vec3(centers[shardId * 3], centers[shardId * 3 + 1], centers[shardId * 3 + 2]))
        val px = base.x + ox[shardId]
        val py = base.y + oy[shardId]
        val pz = base.z + oz[shardId]
        return sqrt(px * px + py * py + pz * pz)
    }

    fun isActive(shardId: Int) = shardId in 0 until capacity && active[shardId]

    /**
     * 조각이 껍질에서 떨어져 풍선 안으로 들어간다.
     *
     * @param outward 붙어 있던 방향. 안쪽으로 살짝 밀려 들어간다
     */
    fun spawn(shardId: Int, outward: Vec3, areaFrac: Float, ballRotation: Quat, hangFrames: Int = 0) {
        if (shardId < 0 || shardId >= capacity || active[shardId]) return

        active[shardId] = true
        hang[shardId] = hangFrames + HANG_BASE + (areaFrac * 520f).toInt().coerceAtMost(30)
        count++

        // 껍질이 있던 자리에서 안쪽으로 떨어진다. 밖으로는 못 나간다.
        val n = outward.normalized()
        ox[shardId] = 0f; oy[shardId] = 0f; oz[shardId] = 0f
        vx[shardId] = -n.x * 0.25f + jitter(0.18f)
        vy[shardId] = -n.y * 0.25f + jitter(0.18f)
        vz[shardId] = -n.z * 0.25f + jitter(0.18f)

        r0x[shardId] = ballRotation.x; r0y[shardId] = ballRotation.y
        r0z[shardId] = ballRotation.z; r0w[shardId] = ballRotation.w
        tx[shardId] = 0f; ty[shardId] = 0f; tz[shardId] = 0f; tw[shardId] = 1f
        wx[shardId] = jitter(2.2f); wy[shardId] = jitter(2.2f); wz[shardId] = jitter(2.2f)

        halfSize[shardId] = 0.04f + sqrt(areaFrac.coerceAtLeast(0f)) * 0.5f
    }

    /**
     * @param centers 조각별 회전 중심(볼 좌표계). BallGeometry가 준다
     */
    fun update(dt: Float, centers: FloatArray) {
        for (i in 0 until capacity) {
            if (!active[i]) continue
            if (hang[i] > 0) { hang[i]--; continue }

            // 풍선 안은 공기가 아니라 서로 눌린 조각들이라 금방 잦아든다.
            // 반죽될수록 속살에 치대져 끈적해지므로 더 빨리 멎는다.
            val damp = 1f - (DAMPING * (1f + KNEAD_STICKY * knead) * dt).coerceAtMost(0.9f)
            vx[i] *= damp; vy[i] *= damp; vz[i] *= damp

            ox[i] += vx[i] * dt
            oy[i] += vy[i] * dt
            oz[i] += vz[i] * dt

            integrateSpin(i, dt)
            clampInside(i, centers)
        }
    }

    /**
     * 손이 쥐는 방향으로 조각을 민다.
     *
     * 쥐면 풍선이 눌리고 안의 조각도 같이 밀린다. 이게 없으면 조각이 가만히 떠 있어서
     * 쥐는 것과 안에서 놀아나는 것이 따로 논다.
     */
    fun squeeze(strength: Float) {
        if (strength <= 0f) return
        // 반죽된 조각은 속살에 붙어 있어 흔들어도 덜 튄다.
        val push = strength.coerceAtMost(4f) * SQUEEZE_PUSH * (1f - 0.6f * knead)
        for (i in 0 until capacity) {
            if (!active[i] || hang[i] > 0) continue
            vx[i] += jitter(push)
            vy[i] += jitter(push)
            vz[i] += jitter(push)
        }
    }

    /** 조각 하나의 3x4 변환을 out[offset..offset+11]에 행 우선으로 쓴다. */
    fun writeMatrix(shardId: Int, centers: FloatArray, out: FloatArray, offset: Int) {
        val cx = centers[shardId * 3]
        val cy = centers[shardId * 3 + 1]
        val cz = centers[shardId * 3 + 2]

        val r0 = Quat(r0x[shardId], r0y[shardId], r0z[shardId], r0w[shardId])
        val a = Quat(tx[shardId], ty[shardId], tz[shardId], tw[shardId]) * r0
        a.toMatrix3(scratch3x3, 0)
        out[offset] = scratch3x3[0]; out[offset + 1] = scratch3x3[1]; out[offset + 2] = scratch3x3[2]
        out[offset + 4] = scratch3x3[3]; out[offset + 5] = scratch3x3[4]; out[offset + 6] = scratch3x3[5]
        out[offset + 8] = scratch3x3[6]; out[offset + 9] = scratch3x3[7]; out[offset + 10] = scratch3x3[8]

        val base = r0.rotate(Vec3(cx, cy, cz))
        val moved = a.rotate(Vec3(cx, cy, cz))

        out[offset + 3] = base.x + ox[shardId] - moved.x
        out[offset + 7] = base.y + oy[shardId] - moved.y
        out[offset + 11] = base.z + oz[shardId] - moved.z
    }

    /**
     * 떨어진 조각이 제 중심으로 오므라드는 정도.
     *
     * 원래 크기 그대로 두면 조각끼리, 그리고 아직 붙어 있는 껍질과 서로 뚫고
     * 지나가서 덩어리 하나로 뭉쳐 보인다. 조금 오므리면 조각 사이에 틈이 생겨
     * "따로따로 굴러다니는 조각"으로 읽힌다.
     */
    fun shrinkOf(shardId: Int): Float = if (isActive(shardId)) DETACHED_SHRINK else 0f

    fun clear() {
        java.util.Arrays.fill(active, false)
        java.util.Arrays.fill(hang, 0)
        cageScale = 1f
        knead = 0f
        kneadTotal = 0f
        count = 0
    }

    /**
     * 풍선 안쪽 벽 안으로 되돌린다.
     *
     * 조각이 원래 있던 자리(구면 위)에서 밀려난 결과가 풍선 밖으로 나가면 안 된다.
     * 벽에 닿으면 안쪽으로 미끄러지게 속도의 바깥 성분만 지운다. 튕기게 하면
     * 조각이 풍선 안을 통통 튀어 다녀서 물렁한 주머니로 안 보인다.
     */
    private fun clampInside(i: Int, centers: FloatArray) {
        val r0 = Quat(r0x[i], r0y[i], r0z[i], r0w[i])
        val base = r0.rotate(Vec3(centers[i * 3], centers[i * 3 + 1], centers[i * 3 + 2]))
        val px = base.x + ox[i]
        val py = base.y + oy[i]
        val pz = base.z + oz[i]

        // 반죽될수록 조각이 속살 쪽으로 조여든다. 흩어져 있던 조각이 가운데로
        // 모여드는 게 "속과 합쳐진다"를 몸으로 보여 준다.
        val limit = (innerRadius * cageScale * (1f - KNEAD_TIGHTEN * knead) - halfSize[i])
            .coerceAtLeast(0.05f)
        val d = sqrt(px * px + py * py + pz * pz)
        if (d <= limit || d < 1e-5f) return

        val nx = px / d
        val ny = py / d
        val nz = pz / d
        val pull = d - limit
        ox[i] -= nx * pull
        oy[i] -= ny * pull
        oz[i] -= nz * pull

        val outward = vx[i] * nx + vy[i] * ny + vz[i] * nz
        if (outward > 0f) {
            vx[i] -= nx * outward
            vy[i] -= ny * outward
            vz[i] -= nz * outward
        }
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
        // 뒹굴기도 같이 잦아든다.
        val d = 1f - (SPIN_DAMPING * dt).coerceAtMost(0.9f)
        wx[i] *= d; wy[i] *= d; wz[i] *= d
    }

    private val scratch3x3 = FloatArray(9)

    private fun jitter(scale: Float) = (rng.nextFloat() * 2f - 1f) * scale

    private companion object {
        /** 조각이 기본으로 매달려 있는 시간(프레임). 60fps에서 12프레임 = 0.2초. */
        const val HANG_BASE = 12

        /** 풍선 안은 조각끼리 눌려 있어 금방 멈춘다. */
        const val DAMPING = 3.2f
        const val SPIN_DAMPING = 2.4f

        /** 한 번 쥘 때 조각에 실리는 흔들림. */
        const val SQUEEZE_PUSH = 0.32f

        /** 떨어진 조각이 오므라드는 정도. 껍질 금(최대 0.06)보다 커야 낱개로 보인다. */
        const val DETACHED_SHRINK = 0.22f

        /** 반죽 1일 때 감쇠가 몇 배 세지는지. 끈적한 반죽은 흔들려도 금방 멎는다. */
        const val KNEAD_STICKY = 2f

        /** 반죽 1일 때 우리가 얼마나 조여드는지. 조각이 속살 쪽으로 모여든다. */
        const val KNEAD_TIGHTEN = 0.35f
    }
}
