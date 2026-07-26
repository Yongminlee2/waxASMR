package com.waxball.asmr.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 녹음에서 파편을 잘라 그레인 뱅크를 만든다.
 * `app/reference/src*.wav` 가 있을 때만 돌고, 없으면 조용히 지나간다.
 *
 * 잘라낸 결과는 사람이 한 번 들어봐야 한다. 영상에 목소리나 배경음이 섞여 있으면
 * 그것까지 파편으로 들어가기 때문이다. 검수용 WAV를 같이 만든다.
 */
class GrainBankBuilderTest {

    private val referenceDir = File("reference")
    private val assetsDir = File("src/main/assets")
    private val auditionDir = File("build/audition")

    @Test
    fun buildGrainBankFromRecordings() {
        val sources = referenceDir.listFiles { f -> f.name.startsWith("src") && f.extension == "wav" }
            ?.sortedBy { it.name }
            ?: emptyList()

        if (sources.isEmpty()) {
            println("[그레인] app/reference/src*.wav 가 없어 건너뜀. 기존 뱅크를 그대로 쓴다.")
            return
        }

        val all = ArrayList<GrainExtractor.Fragment>()
        for ((i, file) in sources.withIndex()) {
            val wav = WavReader.read(file)
            val fragments = GrainExtractor.extract(wav, sourceIndex = i, limit = 220)
            println("[그레인] ${file.name}: 파편 ${fragments.size}개")
            all.addAll(fragments)
        }

        assertTrue("파편을 하나도 못 잘라냄", all.size >= 60)

        GrainExtractor.writeBank(
            all,
            File(assetsDir, "grains.bin"),
            File(assetsDir, "grains.idx"),
        )

        writeAuditionStrip(all)
        printSummary(all)
    }

    /** 파편을 짧은 간격으로 이어 붙여 귀로 확인할 수 있게 한다. */
    private fun writeAuditionStrip(fragments: List<GrainExtractor.Fragment>) {
        val gap = (0.06f * Audition.SAMPLE_RATE).toInt()
        val take = fragments.shuffled(java.util.Random(7).let { r -> kotlin.random.Random(7) }).take(80)

        var total = 0
        for (f in take) total += f.samples.size + gap
        val mono = FloatArray(total)

        var at = 0
        for (f in take) {
            for (s in f.samples) {
                mono[at++] = s / 32768f
            }
            at += gap
        }

        val stereo = FloatArray(mono.size * 2)
        for (i in mono.indices) { stereo[i * 2] = mono[i]; stereo[i * 2 + 1] = mono[i] }
        Audition.writeWav(File(auditionDir, "검수-파편80개.wav"), stereo)
    }

    private fun printSummary(all: List<GrainExtractor.Fragment>) {
        val lengths = all.map { it.lengthMs }.sorted()
        val centroids = all.map { it.centroidHz }.sorted()
        println(
            "[그레인] 총 %d개 · 길이 %.0f~%.0fms(중앙 %.0f) · 밝기 %.0f~%.0fHz(중앙 %.0f) · 용량 %.1fMB".format(
                all.size,
                lengths.first(), lengths.last(), lengths[lengths.size / 2],
                centroids.first(), centroids.last(), centroids[centroids.size / 2],
                all.sumOf { it.samples.size } * 2 / 1024.0 / 1024.0,
            )
        )
    }

    @Test
    fun bankFilesAreUsableWhenPresent() {
        val bin = File(assetsDir, "grains.bin")
        val idx = File(assetsDir, "grains.idx")
        if (!bin.exists() || !idx.exists()) return

        val lines = idx.readLines().filter { it.isNotBlank() }
        assertTrue("색인이 비어 있음", lines.size >= 60)

        var expectedOffset = 0
        for (line in lines) {
            val parts = line.split(',')
            assertEquals("색인 형식이 잘못됨: $line", 4, parts.size)
            val offset = parts[0].toInt()
            val length = parts[1].toInt()
            val centroid = parts[2].toInt()

            assertEquals("파편이 이어져 있지 않음", expectedOffset, offset)
            assertTrue("파편 길이가 비정상: $length", length in 1000..7000)
            assertTrue("밝기가 비정상: $centroid", centroid in 100..20000)
            expectedOffset += length
        }
        assertEquals("bin 크기와 색인이 안 맞음", expectedOffset * 2, bin.length().toInt())
    }
}
