package com.waxball.asmr.audio

import android.content.res.AssetManager
import android.util.Log
import java.io.DataInputStream

/**
 * 재질별로 "쥐는 동작 한 번"짜리 녹음 덩어리를 모아 둔 것.
 *
 * 앞서 쓰던 [GrainBank] 는 파열 낱개(0.17초)를 모아 두고 한 번에 두세 개를 겹쳤다.
 * 그래서 이어지는 느낌이 없었다. 다른 앱을 재 보니 소리 한 단위가 0.86초 —
 * 쥐어서 쭉 찢어지는 동작 전체였다. 여기 든 덩어리는 평균 0.72초이고, 통째로 튼다.
 *
 * 재질마다 따로 녹음한 것을 따로 담는다. 볼을 바꾸면 진짜 다른 소리가 나야 하는데,
 * 녹음 하나에서 밝은 파편/어두운 파편을 골라 흉내 내는 것으로는 한계가 있었다.
 */
class ChunkBank private constructor(
    val samples: ShortArray,
    private val offsets: IntArray,
    private val lengths: IntArray,
    private val materials: IntArray,
    private val centroids: FloatArray,
    /** 재질별 덩어리 번호 목록. */
    private val byMaterial: Array<IntArray>,
) {
    val size: Int get() = offsets.size
    val materialCount: Int get() = byMaterial.size

    fun offsetOf(index: Int) = offsets[index]
    fun lengthOf(index: Int) = lengths[index]
    fun centroidOf(index: Int) = centroids[index]
    fun materialOf(index: Int) = materials[index]

    fun countOf(material: Int): Int =
        if (material in byMaterial.indices) byMaterial[material].size else 0

    /**
     * 그 재질의 덩어리 하나를 고른다.
     *
     * 재질에 덩어리가 없으면(녹음이 모자란 재질) 가장 가까운 번호에서 가져온다.
     * 소리가 아예 안 나는 것보다는 낫다.
     */
    fun pick(material: Int, random: Int): Int {
        if (size == 0) return 0
        var m = material
        if (m !in byMaterial.indices || byMaterial[m].isEmpty()) {
            m = byMaterial.indices.firstOrNull { byMaterial[it].isNotEmpty() } ?: return 0
        }
        val list = byMaterial[m]
        return list[(random and 0x7FFFFFFF) % list.size]
    }

    companion object {
        private const val TAG = "WaxBall"

        /** 뱅크가 없으면 null. 호출자는 예전 방식으로 되돌아간다. */
        fun load(assets: AssetManager): ChunkBank? = try {
            val index = assets.open("chunks.idx").bufferedReader().use { it.readLines() }
                .filter { it.isNotBlank() }

            val n = index.size
            val offsets = IntArray(n)
            val lengths = IntArray(n)
            val materials = IntArray(n)
            val centroids = FloatArray(n)

            for (i in 0 until n) {
                val p = index[i].split(',')
                offsets[i] = p[0].trim().toInt()
                lengths[i] = p[1].trim().toInt()
                materials[i] = p[2].trim().toInt()
                centroids[i] = p[3].trim().toFloat()
            }

            val total = offsets[n - 1] + lengths[n - 1]
            val samples = ShortArray(total)
            assets.open("chunks.bin").use { input ->
                DataInputStream(input.buffered()).use { data ->
                    val raw = ByteArray(total * 2)
                    data.readFully(raw)
                    for (i in 0 until total) {
                        samples[i] =
                            ((raw[i * 2].toInt() and 0xFF) or (raw[i * 2 + 1].toInt() shl 8)).toShort()
                    }
                }
            }

            val count = (materials.maxOrNull() ?: 0) + 1
            val buckets = Array(count) { m -> (0 until n).filter { materials[it] == m }.toIntArray() }

            Log.i(TAG, "덩어리 뱅크 ${n}개 · 재질 ${count}종 · ${total * 2 / 1024}KB")
            ChunkBank(samples, offsets, lengths, materials, centroids, buckets)
        } catch (e: Exception) {
            Log.w(TAG, "덩어리 뱅크를 못 읽음: ${e.message}. 예전 방식으로 진행한다")
            null
        }

        /** 테스트에서 직접 만들 때. */
        fun of(
            samples: ShortArray,
            offsets: IntArray,
            lengths: IntArray,
            materials: IntArray,
            centroids: FloatArray,
        ): ChunkBank {
            val count = (materials.maxOrNull() ?: 0) + 1
            val buckets = Array(count) { m -> offsets.indices.filter { materials[it] == m }.toIntArray() }
            return ChunkBank(samples, offsets, lengths, materials, centroids, buckets)
        }
    }
}
