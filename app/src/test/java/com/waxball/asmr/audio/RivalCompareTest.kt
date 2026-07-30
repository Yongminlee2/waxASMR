package com.waxball.asmr.audio

import org.junit.Test
import java.io.File

/**
 * 다른 왁뿌볼 앱의 소리와 내 녹음을 같은 잣대로 재서 맞대 본다.
 *
 * 남의 녹음을 앱에 넣지는 않는다. 재는 것은 길이·스펙트럼 중심·감쇠·대역 비중 같은
 * **측정값**이고, 그건 저작물이 아니라 사실이다. 그 값을 목표로 삼아 내 녹음을
 * 어떻게 자르고 다듬을지 정하는 데만 쓴다.
 *
 * 잴 파일은 저장소에 두지 않는다. 환경변수 `WAXBALL_RIVAL_DIR` 가 가리키는 폴더의
 * WAV를 읽고, 없으면 조용히 건너뛴다.
 */
class RivalCompareTest {

    private val rivalDir = File(System.getenv("WAXBALL_RIVAL_DIR") ?: "reference/rival")
    private val mine = File("reference/src1.wav")

    private class Group(val name: String) {
        val duration = ArrayList<Float>()
        val onsets = ArrayList<Float>()
        val gap = ArrayList<Float>()
        val centroid = ArrayList<Float>()
        val spread = ArrayList<Float>()
        val decay = ArrayList<Float>()
        val low = ArrayList<Float>()
        val mid = ArrayList<Float>()
        val high = ArrayList<Float>()
        val flat = ArrayList<Float>()

        fun add(r: ReferenceAnalyzer.Report) {
            duration.add(r.durationSec)
            onsets.add(r.onsetsPerSecond)
            gap.add(r.meanGapMs)
            centroid.add(r.centroidHz)
            spread.add(r.spreadOctaves)
            if (r.medianDecayMs > 0f) decay.add(r.medianDecayMs)
            low.add(r.lowRatio)
            mid.add(r.midRatio)
            high.add(r.highRatio)
            flat.add(r.flatness)
        }

        val size: Int get() = duration.size
    }

    private fun median(xs: List<Float>): Float {
        if (xs.isEmpty()) return -1f
        val s = xs.sorted()
        return s[s.size / 2]
    }

    private fun line(g: Group): String = "%-26s %3d개 %6.2fs %7.1f회 %8.0fHz %6.2foct %7s %5.0f/%2.0f/%2.0f%% %5.2f".format(
        g.name,
        g.size,
        median(g.duration),
        median(g.onsets),
        median(g.centroid),
        median(g.spread),
        if (g.decay.isEmpty()) "못잼" else "%.0fms".format(median(g.decay)),
        median(g.low) * 100, median(g.mid) * 100, median(g.high) * 100,
        median(g.flat),
    )

    @Test
    fun compareRivalSoundsAgainstMyRecording() {
        val wavs = rivalDir.listFiles { f -> f.extension.equals("wav", true) }?.sortedBy { it.name }
        if (wavs.isNullOrEmpty()) {
            println("[비교] ${rivalDir.path} 에 WAV가 없어 건너뜀")
            return
        }

        val byKind = linkedMapOf("long" to Group("전체 long"), "short" to Group("전체 short"))
        val byBall = LinkedHashMap<String, Group>()

        for (f in wavs) {
            val report = try {
                ReferenceAnalyzer.analyze(WavReader.read(f))
            } catch (e: Exception) {
                println("[비교] ${f.name} 분석 실패: ${e.message}")
                continue
            }
            val lower = f.name.lowercase()
            val kind = when {
                lower.contains("long") -> "long"
                lower.contains("short") -> "short"
                else -> null
            }
            if (kind != null) byKind[kind]!!.add(report)

            // 파일명이 "wak__egg__egg_long_1" 꼴이라 앞 두 토막이 종류다.
            val parts = f.nameWithoutExtension.split("__")
            if (parts.size >= 2 && kind != null) {
                val key = "${parts[0]}/${parts[1]} $kind"
                byBall.getOrPut(key) { Group(key) }.add(report)
            }
        }

        val out = StringBuilder()
        out.appendLine("다른 앱 소리 vs 내 녹음 — 같은 분석기로 잰 중앙값")
        out.appendLine("=".repeat(104))
        out.appendLine(
            "%-26s %4s %7s %8s %10s %8s %7s %12s %6s".format(
                "대상", "개수", "길이", "파열빈도", "중심", "산포", "감쇠", "저/중/고역", "평탄도",
            )
        )
        out.appendLine("-".repeat(104))

        for (g in byKind.values) if (g.size > 0) out.appendLine(line(g))
        out.appendLine("-".repeat(104))
        for (g in byBall.values) if (g.size > 0) out.appendLine(line(g))

        if (mine.exists()) {
            out.appendLine("-".repeat(104))
            val g = Group("내 녹음(통짜 60초)")
            g.add(ReferenceAnalyzer.analyze(WavReader.read(mine)))
            out.appendLine(line(g))
        } else {
            out.appendLine("내 녹음(${mine.path})이 없어 비교 못 함")
        }

        // 내가 지금 쓰는 파편도 같은 잣대로. 뱅크는 이어붙은 덩어리라 하나씩은 못 재고,
        // 길이 분포만 색인에서 읽는다.
        val idx = File("src/main/assets/grains.idx")
        if (idx.exists()) {
            val lengths = idx.readLines().filter { it.isNotBlank() }
                .map { it.split(',')[1].trim().toInt() / Audition.SAMPLE_RATE.toFloat() }
                .sorted()
            out.appendLine("-".repeat(104))
            out.appendLine(
                "내 파편 뱅크 %d개 · 길이 중앙 %.3fs (%.3f~%.3f)".format(
                    lengths.size, lengths[lengths.size / 2], lengths.first(), lengths.last(),
                )
            )
        }

        File("build/rival-report.txt").apply {
            parentFile?.mkdirs()
            writeText(out.toString())
        }
    }
}
