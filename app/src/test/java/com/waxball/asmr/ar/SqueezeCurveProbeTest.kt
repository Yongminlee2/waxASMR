package com.waxball.asmr.ar

import com.waxball.asmr.core.BallCatalog
import com.waxball.asmr.core.BreakModel
import com.waxball.asmr.core.EventQueue
import com.waxball.asmr.core.Icosphere
import com.waxball.asmr.core.ShardSplitter
import com.waxball.asmr.core.Vec3
import org.junit.Test
import kotlin.random.Random

/**
 * 손을 몇 번 쥐면 껍질이 몇 % 벗겨지는지 잰다. 판정하지 않고 숫자만 찍는다.
 *
 * 손맛을 눈이 아니라 숫자로 맞추려고 만들었다. 실기기에서 "두 번 쥐면 다 부서진다"
 * 같은 말이 나올 때, 고치기 전에 여기서 먼저 곡선을 보고 고친 뒤 다시 본다.
 *
 * 앱과 같은 조건으로 세운다 — 볼 1개(화질 1), 지구, 실제 PalmPose 를 거친 힘.
 */
class SqueezeCurveProbeTest {

    private val dt = 1f / 60f

    /** 사용자가 사진으로 정해 준 곡선. 1~6번 쥐었을 때 벗겨져 있어야 할 비율. */
    private val target = floatArrayOf(0.12f, 0.30f, 0.50f, 0.68f, 0.85f, 0.93f)

    /** 앱이 볼 하나를 만들 때와 같은 파괴 모델. */
    private fun model(shape: Triple<Float, Float, Float>? = null): BreakModel {
        val spec = BallCatalog.byId(0)          // 지구
        val quality = 1                          // 볼 1개일 때
        val base = Icosphere.build(spec.baseSubdivision(quality))
        val shards = ShardSplitter.split(base, spec.shardCount(quality), Random(7L))
        val m = BreakModel(shards, spec.soundProfile(), EventQueue(16384))
        if (shape != null) m.overrideToughnessForTest(shape.first, shape.second, shape.third)
        return m
    }

    /**
     * 굽힘 [curl] 인 손 좌표. 손목-끝 / 손목-뿌리 비율이 실측 범위(0.85~1.9)에
     * 들어가도록 손끝을 연장선 위에 놓는다.
     */
    private fun hand(curl: Float): HandLandmarks {
        val x = FloatArray(HandLandmarks.COUNT)
        val y = FloatArray(HandLandmarks.COUNT)
        fun put(i: Int, dx: Float, dy: Float) { x[i] = 0.5f + dx * 0.2f; y[i] = 0.5f + dy * 0.2f }
        put(HandLandmarks.WRIST, 0f, 0.6f)
        put(HandLandmarks.INDEX_MCP, -0.45f, 0f)
        put(HandLandmarks.MIDDLE_MCP, -0.15f, 0f)
        put(HandLandmarks.RING_MCP, 0.15f, 0f)
        put(HandLandmarks.PINKY_MCP, 0.45f, 0f)
        val ratio = 1.85f - 1.0f * curl
        for ((mcp, tip) in listOf(
            HandLandmarks.INDEX_MCP to HandLandmarks.INDEX_TIP,
            HandLandmarks.MIDDLE_MCP to HandLandmarks.MIDDLE_TIP,
            HandLandmarks.RING_MCP to HandLandmarks.RING_TIP,
            HandLandmarks.PINKY_MCP to HandLandmarks.PINKY_TIP,
        )) {
            val dx = x[mcp] - x[HandLandmarks.WRIST]
            val dy = y[mcp] - y[HandLandmarks.WRIST]
            x[tip] = x[HandLandmarks.WRIST] + dx * ratio
            y[tip] = y[HandLandmarks.WRIST] + dy * ratio
        }
        return HandLandmarks(x, y)
    }

    /**
     * 한 번 쥐었다 편다. [frames] 가 작을수록 빨리 쥐는 것이다.
     * [gain] 은 PalmPose 의 FORCE_GAIN 을 바꿔 보는 셈이다 — 1이면 지금 값 그대로.
     */
    private fun squeezeOnce(pose: PalmPose, m: BreakModel, frames: Int, gain: Float) {
        for (step in 0..frames) {
            pose.update(hand(step.toFloat() / frames), dt)
            if (pose.force > 0f) {
                m.pressArea(Vec3(0f, 0f, 1f), -1f, pose.force * gain, dt, 0f)
            }
        }
        repeat(20) { pose.update(hand(0f), dt) }   // 손을 다시 편다
    }

    private fun curve(
        frames: Int,
        gain: Float = 1f,
        shape: Triple<Float, Float, Float>? = null,
    ): FloatArray {
        val pose = PalmPose()
        val m = model(shape)
        repeat(60) { pose.update(hand(0f), dt) }   // 편 손으로 안정
        return FloatArray(6) {
            squeezeOnce(pose, m, frames, gain)
            m.shellProgress
        }
    }

    private fun show(c: FloatArray) =
        c.mapIndexed { i, v -> "${i + 1}번 ${(v * 100).toInt()}%" }.joinToString("  ")

    /**
     * 목표와 얼마나 떨어졌나. 작을수록 좋다.
     *
     * 첫 쥐기에 세 배 무게를 준다. 나머지가 아무리 잘 맞아도 첫 쥐기에 아무 일도
     * 없으면 "몇 번을 쥐어야 부서지냐"는 원래 불만이 그대로 남기 때문이다.
     */
    private fun error(c: FloatArray): Float {
        var sum = 0f
        for (i in target.indices) {
            val d = c[i] - target[i]
            sum += d * d * (if (i == 0) 3f else 1f)
        }
        return sum
    }

    @Test
    fun printSqueezeCurve() {
        println("쥔 횟수별 껍질 벗겨진 비율 (지금 값)")
        for ((label, frames) in listOf("빠르게" to 11, "보통" to 20, "천천히" to 34)) {
            println("  $label(${frames}프레임)  ${show(curve(frames))}")
        }
        println("  목표          ${show(target)}")
    }

    /**
     * 바닥값·지수·힘을 훑어 목표 곡선에 가장 가까운 조합을 찾는다.
     *
     * "보통 속도"를 기준으로 맞춘다. 빠르게 쥐면 더 부서지고 천천히 쥐면
     * 덜 부서지는 것은 그대로 둬야 한다 — 세게 쥔 보람이 있어야 하니까.
     */
    @Test
    fun searchBestToughness() {
        var bestErr = Float.MAX_VALUE
        var best = Triple(0f, 0f, 0f)
        var bestGain = 0f

        for (floor in listOf(0.07f, 0.10f, 0.13f)) {
            for (peak in listOf(1.2f, 1.4f, 1.7f, 2.1f)) {
                for (exponent in listOf(6.5f, 8.0f, 10f, 13f, 17f)) {
                    for (gain in listOf(2.6f, 3.0f, 3.4f, 3.8f)) {
                        val shape = Triple(floor, peak, exponent)
                        val e = error(curve(20, gain, shape))
                        if (e < bestErr) {
                            bestErr = e
                            best = shape
                            bestGain = gain
                        }
                    }
                }
            }
        }

        val (floor, peak, exponent) = best
        println("가장 가까운 조합  바닥 $floor  꼭대기 $peak  지수 $exponent  힘배수 $bestGain  (오차 $bestErr)")
        println("  보통    " + show(curve(20, bestGain, best)))
        println("  빠르게  " + show(curve(11, bestGain, best)))
        println("  천천히  " + show(curve(34, bestGain, best)))
        println("  목표    " + show(target))
        println("  => FORCE_GAIN 은 " + "%.3f".format(0.17f * bestGain))
    }
}
