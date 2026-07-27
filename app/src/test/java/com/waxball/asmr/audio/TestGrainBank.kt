package com.waxball.asmr.audio

import java.io.File

/**
 * assets에 있는 파편 뱅크를 PC에서 읽어 온다.
 *
 * 앱에서는 AssetManager로 읽지만 JVM 테스트에는 그게 없다. 그래서 여태 소리 측정이
 * 전부 노이즈 합성 경로만 재고 있었다. 실제로 출시되는 것은 파편 재생 경로인데
 * 거기에 측정이 하나도 없었다는 뜻이다.
 *
 * 뱅크 파일은 그냥 파일이므로 직접 읽으면 된다.
 */
object TestGrainBank {

    private val binFile = File("src/main/assets/grains.bin")
    private val idxFile = File("src/main/assets/grains.idx")

    val available: Boolean get() = binFile.exists() && idxFile.exists()

    /** 뱅크가 없으면 null. 호출자가 건너뛴다. */
    fun load(): GrainBank? {
        if (!available) return null

        val lines = idxFile.readLines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return null

        val n = lines.size
        val offsets = IntArray(n)
        val lengths = IntArray(n)
        val centroids = FloatArray(n)

        for (i in 0 until n) {
            val p = lines[i].split(',')
            offsets[i] = p[0].trim().toInt()
            lengths[i] = p[1].trim().toInt()
            centroids[i] = p[2].trim().toFloat()
        }

        val total = offsets[n - 1] + lengths[n - 1]
        val raw = binFile.readBytes()
        val samples = ShortArray(total)
        for (i in 0 until total) {
            samples[i] = ((raw[i * 2].toInt() and 0xFF) or (raw[i * 2 + 1].toInt() shl 8)).toShort()
        }

        return GrainBank.of(samples, offsets, lengths, centroids)
    }
}
