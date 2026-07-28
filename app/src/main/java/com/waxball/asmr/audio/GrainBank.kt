package com.waxball.asmr.audio

import android.content.res.AssetManager
import android.util.Log
import java.io.DataInputStream

/**
 * 실제 왁뿌볼 파열음에서 잘라낸 파편 모음.
 *
 * 노이즈를 필터에 통과시켜 만든 소리로는 왁스 특유의 음색이 안 나왔다.
 * 그래서 진짜 파편을 쓰되, 뿌리는 방식은 그대로 둔다.
 * 파편을 무작위로 골라 피치를 미세하게 흔들고 불규칙한 간격으로 뿌리면
 * 같은 조합이 두 번 나올 일이 없어서 반복이 귀에 안 잡힌다.
 *
 * 밝기 순으로 정렬해 두고 이진탐색으로 고른다. 조각이 300개여도 탐색 비용이 없다.
 */
class GrainBank private constructor(
    val samples: ShortArray,
    private val offsets: IntArray,
    private val lengths: IntArray,
    private val centroids: FloatArray,
    private val sources: IntArray,
    /** 밝기 오름차순으로 정렬된 파편 번호. */
    private val byBrightness: IntArray,
) {
    val size: Int get() = offsets.size

    fun offsetOf(index: Int) = offsets[index]
    fun lengthOf(index: Int) = lengths[index]
    fun centroidOf(index: Int) = centroids[index]
    fun sourceOf(index: Int) = sources[index]

    /**
     * 목표 밝기에 가까운 파편을 고른다. 가장 가까운 하나만 계속 쓰면 반복이 들리므로
     * 근처 후보 중에서 무작위로 집는다.
     *
     * @param spread 후보로 볼 범위. 클수록 음색이 흩어진다
     */
    fun pick(targetHz: Float, spread: Int, random: Int): Int {
        if (size == 0) return 0
        val at = binarySearch(targetHz)
        val half = spread.coerceAtLeast(1)
        val lo = (at - half).coerceAtLeast(0)
        val hi = (at + half).coerceAtMost(size - 1)
        val span = hi - lo + 1
        return byBrightness[lo + (random and 0x7FFFFFFF) % span]
    }

    /**
     * 밝기 순위(백분위)로 파편을 고른다.
     *
     * 절대 주파수를 목표로 던지면 노이즈 합성 시절의 치우침이 그대로 따라온다.
     * 그 치우침은 필터를 통과한 노이즈의 스펙트럼 중심을 녹음에 맞추려고 넣은 것이지,
     * 파편에는 해당 사항이 없다. 파편은 이미 녹음의 스펙트럼이다.
     * 뱅크 안에서의 위치로 고르면 뽑히는 분포가 곧 녹음의 분포가 된다.
     *
     * @param rank01 0이면 가장 어두운 쪽, 1이면 가장 밝은 쪽
     * @param spread01 후보로 볼 범위. 전체 대비 비율. 0.5면 뱅크 전체
     */
    fun pickByRank(rank01: Float, spread01: Float, random: Int): Int {
        if (size == 0) return 0
        val center = rank01.coerceIn(0f, 1f) * (size - 1)
        val half = (spread01.coerceIn(0f, 1f) * size).coerceAtLeast(1f)
        val lo = (center - half).toInt().coerceIn(0, size - 1)
        val hi = (center + half).toInt().coerceIn(lo, size - 1)
        val span = hi - lo + 1
        return byBrightness[lo + (random and 0x7FFFFFFF) % span]
    }

    private fun binarySearch(targetHz: Float): Int {
        var lo = 0
        var hi = size - 1
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (centroids[byBrightness[mid]] < targetHz) lo = mid + 1 else hi = mid
        }
        return lo
    }

    companion object {
        private const val TAG = "WaxBall"

        /** 뱅크가 없으면 null. 호출자는 합성 방식으로 되돌아간다. */
        fun load(assets: AssetManager): GrainBank? = try {
            val index = assets.open("grains.idx").bufferedReader().use { it.readLines() }
                .filter { it.isNotBlank() }

            val n = index.size
            val offsets = IntArray(n)
            val lengths = IntArray(n)
            val centroids = FloatArray(n)
            val sources = IntArray(n)

            for (i in 0 until n) {
                val p = index[i].split(',')
                offsets[i] = p[0].trim().toInt()
                lengths[i] = p[1].trim().toInt()
                centroids[i] = p[2].trim().toFloat()
                sources[i] = p[3].trim().toInt()
            }

            val totalSamples = offsets[n - 1] + lengths[n - 1]
            val samples = ShortArray(totalSamples)
            assets.open("grains.bin").use { input ->
                DataInputStream(input.buffered()).use { data ->
                    val raw = ByteArray(totalSamples * 2)
                    data.readFully(raw)
                    for (i in 0 until totalSamples) {
                        samples[i] = ((raw[i * 2].toInt() and 0xFF) or (raw[i * 2 + 1].toInt() shl 8)).toShort()
                    }
                }
            }

            val byBrightness = (0 until n).sortedBy { centroids[it] }.toIntArray()
            Log.i(TAG, "그레인 뱅크 파편 ${n}개, ${totalSamples * 2 / 1024}KB")
            GrainBank(samples, offsets, lengths, centroids, sources, byBrightness)
        } catch (e: Exception) {
            Log.w(TAG, "그레인 뱅크를 못 읽음: ${e.message}. 합성 방식으로 진행한다")
            null
        }

        /** 테스트에서 뱅크를 직접 만들 때 쓴다. */
        fun of(samples: ShortArray, offsets: IntArray, lengths: IntArray, centroids: FloatArray): GrainBank {
            val byBrightness = centroids.indices.sortedBy { centroids[it] }.toIntArray()
            return GrainBank(samples, offsets, lengths, centroids, IntArray(offsets.size), byBrightness)
        }
    }
}
