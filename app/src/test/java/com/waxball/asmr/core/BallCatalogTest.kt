package com.waxball.asmr.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BallCatalogTest {

    @Test
    fun thirtyBallsWithUniqueIdsAndNames() {
        assertEquals(30, BallCatalog.all.size)
        assertEquals(30, BallCatalog.all.map { it.id }.toSet().size)
        assertEquals(30, BallCatalog.all.map { it.nameKo }.toSet().size)
    }

    @Test
    fun idsMatchListPositions() {
        BallCatalog.all.forEachIndexed { i, spec -> assertEquals(i, spec.id) }
    }

    @Test
    fun fiveAreFreeAndTheRestCostCoins() {
        assertEquals(5, BallCatalog.free.size)
        BallCatalog.free.forEach { assertEquals(0, BallCatalog.byId(it).price) }
        BallCatalog.all.filter { it.id !in BallCatalog.free }.forEach {
            assertTrue("${it.nameKo} 가격이 0", it.price > 0)
        }
    }

    @Test
    fun freeBallsCoverAllFiveMaterials() {
        val materials = BallCatalog.free.map { BallCatalog.byId(it).material }.toSet()
        assertEquals("처음부터 재질 다섯 종류를 다 들어볼 수 있어야 함", 5, materials.size)
    }

    @Test
    fun allMaterialsShapesSizesAndThicknessesAppear() {
        assertEquals(5, BallCatalog.all.map { it.material }.toSet().size)
        assertEquals(4, BallCatalog.all.map { it.shape }.toSet().size)
        assertEquals(4, BallCatalog.all.map { it.size }.toSet().size)
        assertEquals(3, BallCatalog.all.map { it.thickness }.toSet().size)
    }

    @Test
    fun everyBallHasKoreanSoundDescription() {
        BallCatalog.all.forEach {
            assertTrue("${it.nameKo} 설명이 너무 짧음", it.soundDesc.length >= 8)
            assertTrue("${it.nameKo} 캡슐 아이템 없음", it.capsule.isNotBlank())
        }
    }

    @Test
    fun materialsProduceDistinctBaseFrequencies() {
        val freqs = Material.entries.map { it.profile().baseFreq }
        assertEquals(5, freqs.toSet().size)
    }

    @Test
    fun biggerBallsHaveMoreShardsAndLowerPitch() {
        val small = BallCatalog.all.first { it.size == SizeClass.S }
        val huge = BallCatalog.all.first { it.size == SizeClass.XL }
        assertTrue(huge.shardCount(2) > small.shardCount(2))
        assertTrue(
            "왕 크기가 더 낮게 나야 함",
            huge.size.freqScale < small.size.freqScale,
        )
    }

    @Test
    fun thinShellsSoundBrighterThanThickOnes() {
        val thin = BallSpec(
            99, "테스트얇음", SizeClass.M, Thickness.THIN, ShapeKind.SPHERE, Material.HARD_WAX,
            0, 0, 0, "x", 0, "테스트용 설명입니다",
        )
        val thick = thin.copy(thickness = Thickness.THICK)
        assertTrue(thin.soundProfile().brightness > thick.soundProfile().brightness)
        assertTrue(thin.soundProfile().baseFreq > thick.soundProfile().baseFreq)
        assertTrue(thin.soundProfile().decayMsMax < thick.soundProfile().decayMsMax)
    }

    @Test
    fun facetedShapesSpreadCracksMoreThanLumpyOnes() {
        val faceted = BallSpec(
            98, "테스트각짐", SizeClass.M, Thickness.NORMAL, ShapeKind.FACETED, Material.HARD_WAX,
            0, 0, 0, "x", 0, "테스트용 설명입니다",
        )
        val lumpy = faceted.copy(shape = ShapeKind.LUMPY)
        assertTrue(faceted.soundProfile().propagation > lumpy.soundProfile().propagation)
    }

    @Test
    fun shardCountRespectsQualityTier() {
        BallCatalog.all.forEach {
            assertTrue("${it.nameKo} 낮은 화질에서 조각 과다", it.shardCount(0) <= 100)
            assertTrue("${it.nameKo} 보통 화질에서 조각 과다", it.shardCount(1) <= 180)
            assertTrue("${it.nameKo} 높은 화질에서 조각 과다", it.shardCount(2) <= 300)
            assertTrue("${it.nameKo} 조각이 너무 적음", it.shardCount(0) >= 60)
        }
    }

    @Test
    fun profilesStayInAudibleAndStableRanges() {
        BallCatalog.all.forEach {
            val p = it.soundProfile()
            assertTrue("${it.nameKo} 주파수 이탈: ${p.baseFreq}", p.baseFreq in 300f..9000f)
            assertTrue("${it.nameKo} 전파 계수 이탈: ${p.propagation}", p.propagation in 0.05f..0.8f)
            assertTrue("${it.nameKo} 공명 이탈: ${p.resonance}", p.resonance in 0f..0.9f)
            assertTrue("${it.nameKo} 감쇠 역전", p.decayMsMax > p.decayMsMin)
        }
    }

    @Test
    fun everyBallSoundsDifferentFromEveryOther() {
        val signatures = BallCatalog.all.map {
            val p = it.soundProfile()
            listOf(p.baseFreq, p.decayMsMax, p.propagation, p.brightness, p.density, p.resonance, p.freqSpread)
        }
        assertEquals("소리가 겹치는 볼이 있음", 30, signatures.toSet().size)
    }

    @Test
    fun shapesActuallyChangeTheSilhouette() {
        val v = Vec3(0.6f, 0.6f, 0.53f).normalized()
        val radii = ShapeKind.entries.map { it.warp(v) }
        assertEquals("모양별 반지름이 겹침", 4, radii.toSet().size)
        radii.forEach { assertTrue("반지름이 비정상: $it", it in 0.5f..1.6f) }
    }

    @Test
    fun eggIsTallerThanItIsWide() {
        assertTrue(ShapeKind.EGG.warp(Vec3(0f, 1f, 0f)) > ShapeKind.EGG.warp(Vec3(1f, 0f, 0f)))
    }

    @Test
    fun unknownIdFallsBackInsteadOfCrashing() {
        assertEquals(BallCatalog.all[0], BallCatalog.byId(-5))
        assertEquals(BallCatalog.all[0], BallCatalog.byId(999))
    }

    @Test
    fun pricesRiseWithSize() {
        val avgByCategory = BallCatalog.all
            .filter { it.price > 0 }
            .groupBy { it.size }
            .mapValues { e -> e.value.map { it.price }.average() }
        assertNotEquals(null, avgByCategory[SizeClass.S])
        assertTrue(
            "왕 크기가 작은 크기보다 싸다",
            avgByCategory[SizeClass.XL]!! > avgByCategory[SizeClass.S]!!,
        )
    }
}
