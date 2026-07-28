package com.waxball.asmr.core

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * 화면 조각 하나.
 *
 * @param polygon 정점 x0,y0,x1,y1,… 화면 정규 좌표 0~1
 * @param centerX 폴리곤 무게중심. 손가락이 어느 조각을 눌렀는지 고를 때 쓴다
 * @param areaFrac 화면 전체 대비 면적 비율. 소리 크기와 진행률에 쓴다
 */
class PlaneCell(
    val id: Int,
    val polygon: FloatArray,
    val centerX: Float,
    val centerY: Float,
    val areaFrac: Float,
    val neighbours: IntArray,
)

/**
 * 화면 사각형을 불규칙 조각으로 나눈다.
 *
 * 시드를 흩뿌리고, 시드마다 사각형을 다른 시드와의 수직이등분선으로 잘라낸다.
 * 남은 폴리곤이 그 시드의 영역이다. 시드가 150개여도 정점이 몇 개뿐이라 금방 끝난다.
 *
 * 안드로이드 API를 쓰지 않으므로 PC에서 그대로 검증한다.
 */
object PlaneSplitter {

    fun split(count: Int, rng: Random): List<PlaneCell> {
        val n = count.coerceAtLeast(2)
        val seeds = seedPoints(n, rng)

        val polygons = ArrayList<FloatArray>(n)
        for (i in 0 until n) {
            var poly = floatArrayOf(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f)
            for (j in 0 until n) {
                if (i == j) continue
                poly = clipToNearer(poly, seeds[i * 2], seeds[i * 2 + 1], seeds[j * 2], seeds[j * 2 + 1])
                if (poly.size < 6) break
            }
            polygons.add(poly)
        }

        // 시드가 겹치면 면적 0짜리가 나온다. 버리고 번호를 다시 매긴다.
        val kept = ArrayList<Int>(n)
        for (i in 0 until n) {
            if (polygons[i].size >= 6 && abs(area(polygons[i])) > 1e-6f) kept.add(i)
        }

        val newIndex = IntArray(n) { -1 }
        for (k in kept.indices) newIndex[kept[k]] = k

        var total = 0f
        for (i in kept) total += abs(area(polygons[i]))
        if (total <= 0f) total = 1f

        // 이웃은 정의상 대칭이다. 변을 공유하는지 보는 판정에는 부동소수 허용치가 들어가서
        // 한쪽에서만 걸리는 일이 생기므로, 한 번이라도 걸리면 양쪽에 다 넣는다.
        val links = Array(kept.size) { HashSet<Int>() }
        for (k in kept.indices) {
            val old = kept[k]
            val poly = polygons[old]
            for (j in 0 until n) {
                if (j == old || newIndex[j] < 0) continue
                if (sharesEdgeWith(poly, seeds[old * 2], seeds[old * 2 + 1], seeds[j * 2], seeds[j * 2 + 1])) {
                    links[k].add(newIndex[j])
                    links[newIndex[j]].add(k)
                }
            }
        }

        return kept.mapIndexed { newId, old ->
            val poly = polygons[old]
            val c = centroid(poly)
            PlaneCell(
                id = newId,
                polygon = poly,
                centerX = c[0],
                centerY = c[1],
                areaFrac = abs(area(poly)) / total,
                neighbours = links[newId].toIntArray(),
            )
        }
    }

    /**
     * 시드를 일부러 고르지 않게 뿌린다.
     *
     * 균등하게 뿌리면 조각이 다 비슷해지고, 그러면 어디를 깨도 똑같아서 금방 지루해진다.
     * 일부는 화면 전체에 넓게, 나머지는 몇 군데에 몰아서 큰 판과 잔조각이 섞이게 한다.
     */
    private fun seedPoints(n: Int, rng: Random): FloatArray {
        val out = FloatArray(n * 2)
        val coarse = (n * COARSE_RATIO).toInt().coerceAtLeast(1)
        for (i in 0 until coarse) {
            out[i * 2] = rng.nextFloat()
            out[i * 2 + 1] = rng.nextFloat()
        }

        val zones = 3
        val zoneX = FloatArray(zones) { 0.15f + rng.nextFloat() * 0.7f }
        val zoneY = FloatArray(zones) { 0.15f + rng.nextFloat() * 0.7f }
        for (i in coarse until n) {
            val z = i % zones
            val angle = rng.nextFloat() * TWO_PI
            val r = rng.nextFloat() * ZONE_RADIUS
            out[i * 2] = (zoneX[z] + cos(angle) * r).coerceIn(0.001f, 0.999f)
            out[i * 2 + 1] = (zoneY[z] + sin(angle) * r).coerceIn(0.001f, 0.999f)
        }
        return out
    }

    /**
     * (ax, ay) 쪽에 더 가까운 반평면만 남긴다. 자르는 선은 두 점의 수직이등분선이다.
     * 볼록 폴리곤을 직선으로 자르는 표준 방법(Sutherland–Hodgman)이다.
     */
    private fun clipToNearer(poly: FloatArray, ax: Float, ay: Float, bx: Float, by: Float): FloatArray {
        val nx = bx - ax
        val ny = by - ay
        val mx = (ax + bx) * 0.5f
        val my = (ay + by) * 0.5f

        val out = ArrayList<Float>(poly.size + 4)
        val n = poly.size / 2
        for (i in 0 until n) {
            val cx = poly[i * 2]; val cy = poly[i * 2 + 1]
            val j = (i + 1) % n
            val dx = poly[j * 2]; val dy = poly[j * 2 + 1]

            val sc = (cx - mx) * nx + (cy - my) * ny
            val sd = (dx - mx) * nx + (dy - my) * ny

            if (sc <= 0f) { out.add(cx); out.add(cy) }
            if ((sc <= 0f) != (sd <= 0f)) {
                val t = sc / (sc - sd)
                out.add(cx + (dx - cx) * t)
                out.add(cy + (dy - cy) * t)
            }
        }
        return FloatArray(out.size) { out[it] }
    }

    /**
     * 폴리곤의 변 하나가 두 시드의 수직이등분선 위에 놓여 있는가.
     *
     * 자르는 도중에 판정하면 나중 자르기에 그 변이 없어져도 이웃으로 남는다.
     * 다 자른 뒤 최종 폴리곤에서 확인해야 진짜 이웃이다.
     */
    private fun sharesEdgeWith(poly: FloatArray, ax: Float, ay: Float, bx: Float, by: Float): Boolean {
        val n = poly.size / 2
        for (i in 0 until n) {
            val j = (i + 1) % n
            val mx = (poly[i * 2] + poly[j * 2]) * 0.5f
            val my = (poly[i * 2 + 1] + poly[j * 2 + 1]) * 0.5f
            val da = (mx - ax) * (mx - ax) + (my - ay) * (my - ay)
            val db = (mx - bx) * (mx - bx) + (my - by) * (my - by)
            if (abs(da - db) < EDGE_EPS) return true
        }
        return false
    }

    /** 신발끈 공식. 부호는 정점 방향에 따라 달라지므로 쓰는 쪽에서 절댓값을 취한다. */
    private fun area(poly: FloatArray): Float {
        var sum = 0f
        val n = poly.size / 2
        for (i in 0 until n) {
            val j = (i + 1) % n
            sum += poly[i * 2] * poly[j * 2 + 1] - poly[j * 2] * poly[i * 2 + 1]
        }
        return sum * 0.5f
    }

    /** 폴리곤 무게중심. 정점 평균이 아니라 면적 가중이라야 실제 가운데에 온다. */
    private fun centroid(poly: FloatArray): FloatArray {
        var cx = 0f
        var cy = 0f
        var a = 0f
        val n = poly.size / 2
        for (i in 0 until n) {
            val j = (i + 1) % n
            val cross = poly[i * 2] * poly[j * 2 + 1] - poly[j * 2] * poly[i * 2 + 1]
            a += cross
            cx += (poly[i * 2] + poly[j * 2]) * cross
            cy += (poly[i * 2 + 1] + poly[j * 2 + 1]) * cross
        }
        if (abs(a) < 1e-9f) return floatArrayOf(poly[0], poly[1])
        return floatArrayOf(cx / (3f * a), cy / (3f * a))
    }

    /** 시드 중 이 비율만큼은 화면 전체에 넓게 뿌린다. 나머지는 몇 군데에 몰린다. */
    private const val COARSE_RATIO = 0.25f

    /** 몰아 뿌릴 때의 반경. */
    private const val ZONE_RADIUS = 0.22f

    /** 변의 중점이 두 시드에서 이만큼 안쪽으로 같은 거리면 그 변을 공유한 것으로 본다. */
    private const val EDGE_EPS = 1e-4f

    private const val TWO_PI = (2.0 * Math.PI).toFloat()
}
