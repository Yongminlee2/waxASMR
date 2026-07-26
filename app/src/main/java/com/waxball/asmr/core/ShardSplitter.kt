package com.waxball.asmr.core

import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * 구 표면을 불규칙한 조각으로 나눈다.
 *
 * 구면을 직접 자르지 않고, 세분화된 구 메시의 각 삼각형을 가장 가까운 시드에 배정해
 * 군집을 만드는 방식이다. 격자 무늬가 생기지 않고, 축퇴 폴리곤 같은 기하 예외가
 * 원천적으로 발생하지 않는다.
 *
 * 시드는 매번 새로 뽑기 때문에 같은 볼을 두 번 깨도 갈라지는 모양이 다르다.
 */
object ShardSplitter {

    fun split(base: Mesh, seedCount: Int, rng: Random): ShardSet {
        require(seedCount >= 4) { "조각이 너무 적다: $seedCount" }
        val triCount = base.triangleCount
        require(seedCount <= triCount) { "조각 수가 삼각형 수보다 많을 수 없다" }

        val seeds = sampleSphere(seedCount, rng)

        // 삼각형 무게중심을 정규화해 가장 가까운(내적이 가장 큰) 시드에 배정한다.
        val owner = IntArray(triCount)
        val centroids = arrayOfNulls<Vec3>(triCount)
        for (t in 0 until triCount) {
            val c = base.centroid(t).normalized()
            centroids[t] = c
            var best = 0
            var bestDot = -2f
            for (s in 0 until seedCount) {
                val d = c dot seeds[s]
                if (d > bestDot) { bestDot = d; best = s }
            }
            owner[t] = best
        }

        rescueEmptySeeds(owner, centroids, seeds, seedCount)

        // 조각별 삼각형 목록, 면적, 중심 방향
        val lists = Array(seedCount) { ArrayList<Int>() }
        for (t in 0 until triCount) lists[owner[t]].add(t)

        val areas = FloatArray(seedCount)
        var totalArea = 0f
        for (t in 0 until triCount) {
            val a = base.area(t)
            areas[owner[t]] += a
            totalArea += a
        }

        val shards = Array(seedCount) { s ->
            var cx = 0f; var cy = 0f; var cz = 0f
            for (t in lists[s]) {
                val c = centroids[t]!!
                cx += c.x; cy += c.y; cz += c.z
            }
            val center = Vec3(cx, cy, cz).normalized()
            Shard(s, center, areas[s] / totalArea, lists[s].toIntArray())
        }

        return ShardSet(shards, buildAdjacency(base, owner, seedCount), base)
    }

    /**
     * 시드가 하나도 삼각형을 못 가져간 경우, 가장 큰 조각에서 그 시드에 가장 가까운
     * 삼각형을 떼어 준다. 빈 조각이 남으면 렌더·소리·진행률 계산이 모두 어긋난다.
     */
    private fun rescueEmptySeeds(
        owner: IntArray,
        centroids: Array<Vec3?>,
        seeds: Array<Vec3>,
        seedCount: Int,
    ) {
        val counts = IntArray(seedCount)
        for (o in owner) counts[o]++

        for (s in 0 until seedCount) {
            if (counts[s] > 0) continue
            var donor = -1
            for (i in 0 until seedCount) if (donor < 0 || counts[i] > counts[donor]) donor = i
            if (counts[donor] < 2) continue

            var bestTri = -1
            var bestDot = -2f
            for (t in owner.indices) {
                if (owner[t] != donor) continue
                val d = centroids[t]!! dot seeds[s]
                if (d > bestDot) { bestDot = d; bestTri = t }
            }
            if (bestTri >= 0) {
                owner[bestTri] = s
                counts[donor]--
                counts[s]++
            }
        }
    }

    /** 에지를 공유하면서 서로 다른 조각에 속한 삼각형들로 인접 그래프를 만든다. */
    private fun buildAdjacency(base: Mesh, owner: IntArray, seedCount: Int): Array<IntArray> {
        val edgeOwner = HashMap<Long, Int>(base.triangleCount * 2)
        val neighbours = Array(seedCount) { HashSet<Int>() }

        for (t in 0 until base.triangleCount) {
            val a = base.indices[t * 3]
            val b = base.indices[t * 3 + 1]
            val c = base.indices[t * 3 + 2]
            for (e in edgeKeys(a, b, c)) {
                val prev = edgeOwner[e]
                if (prev == null) {
                    edgeOwner[e] = owner[t]
                } else if (prev != owner[t]) {
                    neighbours[prev].add(owner[t])
                    neighbours[owner[t]].add(prev)
                }
            }
        }
        return Array(seedCount) { neighbours[it].toIntArray().also { a -> a.sort() } }
    }

    private fun edgeKeys(a: Int, b: Int, c: Int) = longArrayOf(key(a, b), key(b, c), key(c, a))

    private fun key(a: Int, b: Int): Long {
        val lo = minOf(a, b).toLong()
        val hi = maxOf(a, b).toLong()
        return (lo shl 32) or hi
    }

    /**
     * 시드를 균등하게 뿌리지 않는다.
     *
     * 균등하게 뿌리면 조각 크기가 죄다 비슷해져서, 어디를 깨도 같은 크기가 떨어진다.
     * 큰 판이 통째로 벗겨지는 순간이 아예 생기지 않아 금방 지루해진다.
     *
     * 실제 왁스는 어떤 데는 넓은 판으로 떨어지고 어떤 데는 자잘하게 바스러진다.
     * 그래서 시드 일부만 전체에 고루 뿌려 넓은 판을 만들고, 나머지는 몇 군데
     * 좁은 구역에 몰아넣어 그 자리만 잘게 부서지게 한다.
     * 보로노이 조각 크기는 시드 밀도에 반비례하므로 이것만으로 크기가 갈린다.
     */
    private fun sampleSphere(n: Int, rng: Random): Array<Vec3> {
        val coarseCount = (n * COARSE_RATIO).toInt().coerceAtLeast(4)
        val fineCount = n - coarseCount

        val seeds = arrayOfNulls<Vec3>(n)
        for (i in 0 until coarseCount) seeds[i] = randomDirection(rng)

        if (fineCount > 0) {
            val zoneCount = 2 + rng.nextInt(3)
            val zones = Array(zoneCount) { randomDirection(rng) }
            // 구역 크기를 다르게 해야 자잘한 정도도 자리마다 달라진다.
            val zoneCos = FloatArray(zoneCount) { 0.55f + rng.nextFloat() * 0.3f }

            for (i in 0 until fineCount) {
                val z = i % zoneCount
                seeds[coarseCount + i] = randomInCap(zones[z], zoneCos[z], rng)
            }
        }

        @Suppress("UNCHECKED_CAST")
        return seeds as Array<Vec3>
    }

    private fun randomDirection(rng: Random): Vec3 {
        val z = rng.nextFloat() * 2f - 1f
        val theta = rng.nextFloat() * 2f * Math.PI.toFloat()
        val r = sin(acos(z))
        return Vec3(r * cos(theta), r * sin(theta), z)
    }

    /** 축을 중심으로 한 원뿔(캡) 안에서 균등하게 한 점을 뽑는다. */
    private fun randomInCap(axis: Vec3, cosMax: Float, rng: Random): Vec3 {
        val z = cosMax + rng.nextFloat() * (1f - cosMax)
        val theta = rng.nextFloat() * 2f * Math.PI.toFloat()
        val r = sin(acos(z))
        val local = Vec3(r * cos(theta), r * sin(theta), z)

        // z축 기준으로 뽑은 점을 axis 방향으로 돌린다.
        val a = axis.normalized()
        val reference = if (kotlin.math.abs(a.z) < 0.9f) Vec3(0f, 0f, 1f) else Vec3(1f, 0f, 0f)
        val u = (reference cross a).normalized()
        val v = a cross u
        return (u * local.x + v * local.y + a * local.z).normalized()
    }

    /**
     * 전체 시드 중 넓은 판을 만드는 데 쓰는 비율.
     * 낮출수록 판이 커지고 나머지 구역은 더 잘게 부서진다.
     */
    private const val COARSE_RATIO = 0.2f
}
