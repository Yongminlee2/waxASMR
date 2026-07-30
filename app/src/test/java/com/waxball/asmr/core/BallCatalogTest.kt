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
    fun allMaterialsShapesSizesAndThicknessesAppear() {
        // 재질 하나라도 안 쓰이면 그 뱅크를 만들려고 녹음한 재료가 통째로 논다.
        assertEquals(
            "안 쓰이는 재질이 있음",
            Material.entries.size,
            BallCatalog.all.map { it.material }.toSet().size,
        )
        assertEquals(4, BallCatalog.all.map { it.shape }.toSet().size)
        assertEquals(4, BallCatalog.all.map { it.size }.toSet().size)
        assertEquals(3, BallCatalog.all.map { it.thickness }.toSet().size)
    }

    @Test
    fun everyBallHasASurfacePattern() {
        // 단색 구는 3D에서 어느 각도로 봐도 같아서 볼을 바꾼 티가 안 난다.
        // 무늬가 있어야 굴릴 때마다 다른 면이 나온다.
        val plain = BallCatalog.all.filter { it.surface == SurfaceKind.PLAIN }
        assertTrue("무늬 없는 볼이 있음: ${plain.map { it.nameKo }}", plain.isEmpty())
    }

    @Test
    fun everyTextureAssetActuallyExists() {
        // 이름만 있고 파일이 없으면 그 볼은 소리 없이 절차적 무늬로 떨어진다.
        // 실사를 넣었다고 생각했는데 아닌 상태를 조용히 지나가면 안 된다.
        for (spec in BallCatalog.all) {
            val name = spec.textureAsset ?: continue
            val file = java.io.File("src/main/assets/planets/" + name)
            assertTrue("${spec.nameKo} 의 표면 지도가 없음: $name", file.exists() && file.length() > 10_000L)
        }
        assertTrue(
            "실사 지도가 하나도 배정되지 않음",
            BallCatalog.all.count { it.textureAsset != null } >= 10,
        )
    }

    @Test
    fun everyBallHasAKneadPalette() {
        // 반죽 재료색은 1~4개. 없으면 주물러도 색이 안 변하고, 다섯 개부터는
        // 여정이 너무 길어 한 판 안에 다 못 본다.
        for (spec in BallCatalog.all) {
            assertTrue("${spec.nameKo} 반죽색 개수 이탈", spec.kneadColors.size in 1..4)
        }
        // 알록달록한 볼도 있어야 한다. 전부 단색이면 색 섞임 자체가 안 보인다.
        assertTrue(BallCatalog.all.count { it.kneadColors.size >= 3 } >= 8)
    }

    @Test
    fun theAccentColourContrastsWithTheShell() {
        // 강조색이 껍질색과 같으면 무늬를 그려도 눈에 안 보인다.
        for (spec in BallCatalog.all) {
            assertNotEquals("${spec.nameKo} 의 강조색이 껍질색과 같음", spec.shellColor, spec.accentColor)
        }
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
        // 실제로 나는 소리는 재질 뱅크에서 나온다. 합성 프로파일은 뱅크를 못 읽었을 때의
        // 대비책이고 여러 재질이 같은 것을 공유하므로, 그것만 보면 겹친 것처럼 나온다.
        val signatures = BallCatalog.all.map {
            val p = it.soundProfile()
            listOf(
                it.material.bank.toFloat(),
                p.baseFreq, p.decayMsMax, p.propagation, p.brightness, p.density, p.resonance, p.freqSpread,
            )
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

}
