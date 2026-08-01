package com.waxball.asmr.core

/**
 * 볼 42종. 실사 태양계 14종 + 장난감·캐릭터 22종 + 파스텔 반죽 6종.
 *
 * 처음에는 30종 전부 태양계였는데, 사진이 없는 천체는 잡음 함수 무늬라 실사
 * 행성 옆에 서면 디테일 차이가 확 났다. 그래서 절차 무늬였던 자리를 전부
 * 직접 그린 텍스처(스포츠공·주사위·과일·자체 캐릭터 얼굴)로 바꿨다.
 * 실존 애니메이션 캐릭터는 저작권 때문에 못 쓴다. 얼굴은 전부 자체 디자인이다.
 *
 * 교체하면서 (크기·두께·모양·재질)은 원래 볼 것을 그대로 물려받았다.
 * 소리 배정과 "30종 전부 다르게 들린다" 검증을 다시 맞추지 않기 위해서다.
 *
 * [kneadColors]는 반죽에 섞인 재료색이다. 주무르면 속살이 이 색들을 차례로
 * 배어들다 끝에는 전부 섞인 색으로 굳는다. 단색 반죽도 몇 개 남겨 뒀다 —
 * 실제 왁뿌볼도 단색과 알록달록이 섞여 있다.
 *
 * 파스텔 반죽 6종은 다이소식 왁뿌볼 그대로다 — 겉면부터 2·3·4색 덩어리가
 * 마블로 뭉쳐 있고, 그 색들이 그대로 반죽색이 된다. 재질 조합은 기존 볼과
 * 겹치지 않는 것을 골라 36종 전부 다르게 들린다 검증을 유지한다.
 */
object BallCatalog {

    val all: List<BallSpec> = listOf(
        // --- 안쪽 행성 ---
        BallSpec(0, "지구", SizeClass.M, Thickness.NORMAL, ShapeKind.SPHERE, Material.HARD_WAX,
            0xFF2E7FD4.toInt(), 0xFF3FA35C.toInt(), 0xFFEAF3FF.toInt(), "🌍", 0,
            "기준이 되는 소리. 마르고 단단한 빠작", SurfaceKind.TERRA, 0xFF3FA35C.toInt(),
            textureAsset = "2k_earth_daymap.jpg",
            kneadColors = listOf(0xFF2E7FD4.toInt(), 0xFF3FA35C.toInt(), 0xFFF2F6FA.toInt())),
        BallSpec(1, "태양", SizeClass.XL, Thickness.THIN, ShapeKind.SPHERE, Material.GLASS_BEAD,
            0xFFFFC24D.toInt(), 0xFFFF7A18.toInt(), 0xFFFFF3C4.toInt(), "☀", 0,
            "가장 밝고 뾰족하게 터진다", SurfaceKind.FLARE, 0xFFFFF0A0.toInt(),
            textureAsset = "2k_sun.jpg",
            kneadColors = listOf(0xFFFFC24D.toInt(), 0xFFFF7A18.toInt(), 0xFFE83A20.toInt())),
        BallSpec(2, "수성", SizeClass.S, Thickness.THICK, ShapeKind.LUMPY, Material.THICK_WAX,
            0xFF9A9188.toInt(), 0xFF6B635C.toInt(), 0xFF433D38.toInt(), "☿", 0,
            "작고 두꺼워 묵직한 뽀각", SurfaceKind.CRATER, 0xFF5E564F.toInt(),
            textureAsset = "2k_mercury.jpg",
            kneadColors = listOf(0xFF9A9188.toInt())),
        BallSpec(3, "금성", SizeClass.M, Thickness.THICK, ShapeKind.SPHERE, Material.CLAY_WAX,
            0xFFE8C88A.toInt(), 0xFFC98F42.toInt(), 0xFF8A5A22.toInt(), "♀", 0,
            "가장 둔하고 답답하게 뭉개진다", SurfaceKind.SWIRL, 0xFFB07B36.toInt(),
            textureAsset = "2k_venus_surface.jpg",
            kneadColors = listOf(0xFFE8C88A.toInt(), 0xFFC98F42.toInt())),
        BallSpec(4, "달", SizeClass.S, Thickness.NORMAL, ShapeKind.SPHERE, Material.HARD_WAX,
            0xFFDDD8CE.toInt(), 0xFFA9A399.toInt(), 0xFF6E6962.toInt(), "🌕", 0,
            "작아서 한 음 높다", SurfaceKind.CRATER, 0xFF8E887F.toInt(),
            textureAsset = "2k_moon.jpg",
            kneadColors = listOf(0xFFDDD8CE.toInt())),
        BallSpec(5, "화성", SizeClass.M, Thickness.NORMAL, ShapeKind.SPHERE, Material.CRUNCH_BEADS,
            0xFFC1573A.toInt(), 0xFF8E3A22.toInt(), 0xFF5A2313.toInt(), "♂", 0,
            "알갱이가 오래 이어지는 빠자자작", SurfaceKind.SPECKLE, 0xFFE08A5E.toInt(),
            textureAsset = "2k_mars.jpg",
            kneadColors = listOf(0xFFC1573A.toInt(), 0xFF8E3A22.toInt())),

        // --- 소행성대 ---
        BallSpec(6, "세레스", SizeClass.S, Thickness.NORMAL, ShapeKind.LUMPY, Material.CRUNCH_BEADS,
            0xFFA8A29A.toInt(), 0xFF7A756E.toInt(), 0xFF4C4843.toInt(), "🪨", 0,
            "울퉁불퉁해 음높이가 넓게 흩어진다", SurfaceKind.CRATER, 0xFF6E6963.toInt(),
            textureAsset = "2k_ceres_fictional.jpg",
            kneadColors = listOf(0xFFA8A29A.toInt())),
        BallSpec(7, "주사위", SizeClass.S, Thickness.THICK, ShapeKind.FACETED, Material.THICK_WAX,
            0xFFF5F2EA.toInt(), 0xFFD8D3C8.toInt(), 0xFFB8B2A6.toInt(), "🎲", 0,
            "모서리부터 우수수 떨어진다", SurfaceKind.SPECKLE, 0xFF20242C.toInt(),
            textureAsset = "ball_dice.jpg",
            kneadColors = listOf(0xFFF5F2EA.toInt(), 0xFF20242C.toInt(), 0xFFD03A2A.toInt())),
        BallSpec(8, "골프공", SizeClass.S, Thickness.THIN, ShapeKind.LUMPY, Material.GLASS_BEAD,
            0xFFF2F4F0.toInt(), 0xFFCFD4CC.toInt(), 0xFF9AA096.toInt(), "⛳", 0,
            "얇고 밝게 부서진다", SurfaceKind.SPECKLE, 0xFF7FBF6A.toInt(),
            textureAsset = "ball_golf.jpg",
            kneadColors = listOf(0xFFF2F4F0.toInt(), 0xFF7FBF6A.toInt())),

        // --- 목성과 장난감 ---
        BallSpec(9, "목성", SizeClass.XL, Thickness.NORMAL, ShapeKind.SPHERE, Material.SQUISHY_WAX,
            0xFFD8A46A.toInt(), 0xFFB3763C.toInt(), 0xFF7A4A1E.toInt(), "♃", 0,
            "가장 크고 저음이 깊게 깔린다", SurfaceKind.BANDED, 0xFFF2DCC0.toInt(),
            textureAsset = "2k_jupiter.jpg",
            kneadColors = listOf(0xFFD8A46A.toInt(), 0xFFB3763C.toInt(), 0xFFF2DCC0.toInt())),
        BallSpec(10, "테니스공", SizeClass.S, Thickness.THIN, ShapeKind.SPHERE, Material.SUGAR_GLASS,
            0xFFCFE24A.toInt(), 0xFFA8BC2E.toInt(), 0xFF6E7D1E.toInt(), "🎾", 0,
            "높고 뾰족한 챙그랑", SurfaceKind.SPECKLE, 0xFFF4F6E8.toInt(),
            textureAsset = "ball_tennis.jpg",
            kneadColors = listOf(0xFFCFE24A.toInt(), 0xFFF4F6E8.toInt())),
        BallSpec(11, "야구공", SizeClass.M, Thickness.THIN, ShapeKind.SPHERE, Material.GLASS_BEAD,
            0xFFF4EFE6.toInt(), 0xFFD8CFC0.toInt(), 0xFFA89C8A.toInt(), "⚾", 0,
            "가죽 아래서 밝게 자잘거린다", SurfaceKind.SPECKLE, 0xFFC23A32.toInt(),
            textureAsset = "ball_baseball.jpg",
            kneadColors = listOf(0xFFF4EFE6.toInt(), 0xFFC23A32.toInt(), 0xFF9A6A42.toInt())),
        BallSpec(12, "농구공", SizeClass.L, Thickness.NORMAL, ShapeKind.SPHERE, Material.HARD_WAX,
            0xFFD9772F.toInt(), 0xFFA8541E.toInt(), 0xFF6E3512.toInt(), "🏀", 0,
            "커진 만큼 저음이 실린다", SurfaceKind.SPECKLE, 0xFF2A2320.toInt(),
            textureAsset = "ball_basketball.jpg",
            kneadColors = listOf(0xFFD9772F.toInt(), 0xFF2A2320.toInt())),
        BallSpec(13, "볼링공", SizeClass.L, Thickness.THICK, ShapeKind.LUMPY, Material.THICK_WAX,
            0xFF3A2C6A.toInt(), 0xFF241A46.toInt(), 0xFF140E2A.toInt(), "🎳", 0,
            "두껍고 둔해 오래 버틴다", SurfaceKind.SWIRL, 0xFF8A76D8.toInt(),
            textureAsset = "ball_bowling.jpg",
            kneadColors = listOf(0xFF6A4FC8.toInt(), 0xFF241A46.toInt(), 0xFF2E9AA8.toInt())),

        // --- 토성과 장난감 ---
        BallSpec(14, "토성", SizeClass.XL, Thickness.THIN, ShapeKind.EGG, Material.SOFT_WAX,
            0xFFE6D2A0.toInt(), 0xFFC4A868.toInt(), 0xFF8E7638.toInt(), "🪐", 0,
            "길쭉해 공명이 더 산다", SurfaceKind.BANDED, 0xFFF7ECCE.toInt(),
            textureAsset = "2k_saturn.jpg",
            kneadColors = listOf(0xFFE6D2A0.toInt(), 0xFFC4A868.toInt())),
        BallSpec(15, "축구공", SizeClass.L, Thickness.THICK, ShapeKind.SPHERE, Material.CLAY_WAX,
            0xFFF2F2F0.toInt(), 0xFFC8C8C4.toInt(), 0xFF8E8E8A.toInt(), "⚽", 0,
            "가장 둔하고 먹먹하다", SurfaceKind.SPECKLE, 0xFF1E1E22.toInt(),
            textureAsset = "ball_soccer.jpg",
            kneadColors = listOf(0xFFF2F2F0.toInt(), 0xFF1E1E22.toInt())),
        BallSpec(16, "병아리", SizeClass.S, Thickness.NORMAL, ShapeKind.LUMPY, Material.SUGAR_GLASS,
            0xFFFFD84A.toInt(), 0xFFF0B62E.toInt(), 0xFFC08A1A.toInt(), "🐤", 0,
            "삐약삐약 작고 높게 부서진다", SurfaceKind.SPECKLE, 0xFFF08A28.toInt(),
            textureAsset = "ball_chick.jpg",
            kneadColors = listOf(0xFFFFD84A.toInt(), 0xFFF08A28.toInt(), 0xFFFFF6D8.toInt())),
        BallSpec(17, "곰돌이", SizeClass.M, Thickness.NORMAL, ShapeKind.EGG, Material.HARD_WAX,
            0xFFA8703E.toInt(), 0xFF7E5028.toInt(), 0xFF523418.toInt(), "🐻", 0,
            "둥근 머리가 마르게 빠작인다", SurfaceKind.SPECKLE, 0xFFE8C89A.toInt(),
            textureAsset = "ball_bear.jpg",
            kneadColors = listOf(0xFFA8703E.toInt(), 0xFFE8C89A.toInt(), 0xFF523418.toInt())),

        // --- 바깥 행성 ---
        BallSpec(18, "천왕성", SizeClass.L, Thickness.NORMAL, ShapeKind.SPHERE, Material.SQUISHY_WAX,
            0xFF8ED6DC.toInt(), 0xFF4E9AA8.toInt(), 0xFF2A6470.toInt(), "🔵", 0,
            "말랑하게 낮은 뿌직", SurfaceKind.BANDED, 0xFFB8E8EC.toInt(),
            textureAsset = "2k_uranus.jpg",
            kneadColors = listOf(0xFF8ED6DC.toInt())),
        BallSpec(19, "해왕성", SizeClass.L, Thickness.NORMAL, ShapeKind.SPHERE, Material.CHEWY_WAX,
            0xFF3B5FC4.toInt(), 0xFF23408E.toInt(), 0xFF14265A.toInt(), "🔷", 0,
            "쫀득하게 촘촘히 이어진다", SurfaceKind.SWIRL, 0xFF89A8F0.toInt(),
            textureAsset = "2k_neptune.jpg",
            kneadColors = listOf(0xFF3B5FC4.toInt(), 0xFF89A8F0.toInt())),
        BallSpec(20, "돼지", SizeClass.S, Thickness.NORMAL, ShapeKind.LUMPY, Material.SOFT_WAX,
            0xFFF2A8B8.toInt(), 0xFFDE7E96.toInt(), 0xFFB05068.toInt(), "🐷", 0,
            "낮고 둔한 뿌직", SurfaceKind.SPECKLE, 0xFFC85A78.toInt(),
            textureAsset = "ball_pig.jpg",
            kneadColors = listOf(0xFFF2A8B8.toInt(), 0xFFC85A78.toInt(), 0xFFFBE8EC.toInt())),
        BallSpec(21, "당구 8번공", SizeClass.S, Thickness.THIN, ShapeKind.SPHERE, Material.GLITTER,
            0xFF1C1C22.toInt(), 0xFF34343C.toInt(), 0xFF0E0E12.toInt(), "🎱", 0,
            "얇고 밝게 자잘한 사각사각", SurfaceKind.SPECKLE, 0xFFF2F2EE.toInt(),
            textureAsset = "ball_eight.jpg",
            kneadColors = listOf(0xFF1C1C22.toInt(), 0xFFF2F2EE.toInt())),

        // --- 과일과 얼음천체 ---
        BallSpec(22, "수박", SizeClass.M, Thickness.THIN, ShapeKind.LUMPY, Material.GLITTER,
            0xFF3E9A4E.toInt(), 0xFFE05A5E.toInt(), 0xFFF08A8A.toInt(), "🍉", 0,
            "얇고 밝게 흩어진다", SurfaceKind.BANDED, 0xFF1E5A28.toInt(),
            textureAsset = "ball_watermelon.jpg",
            kneadColors = listOf(0xFF3E9A4E.toInt(), 0xFFE05A5E.toInt(), 0xFF262A22.toInt())),
        BallSpec(23, "에리스", SizeClass.M, Thickness.NORMAL, ShapeKind.SPHERE, Material.CLAY_WAX,
            0xFFE4E8EC.toInt(), 0xFFAEB6BE.toInt(), 0xFF6E767E.toInt(), "🤍", 0,
            "두껍고 둔하게 눌린다", SurfaceKind.ICY, 0xFFAEB6BE.toInt(),
            textureAsset = "2k_eris_fictional.jpg",
            kneadColors = listOf(0xFFE4E8EC.toInt())),
        BallSpec(24, "마케마케", SizeClass.S, Thickness.NORMAL, ShapeKind.EGG, Material.CHEWY_WAX,
            0xFFD48A6A.toInt(), 0xFFA45E42.toInt(), 0xFF6A3826.toInt(), "🟤", 0,
            "쫀득하게 촘촘히", SurfaceKind.SPECKLE, 0xFFF0B294.toInt(),
            textureAsset = "2k_makemake_fictional.jpg",
            kneadColors = listOf(0xFFD48A6A.toInt(), 0xFFA45E42.toInt())),

        // --- 캐릭터 얼굴 ---
        BallSpec(25, "알록 큐브", SizeClass.S, Thickness.THIN, ShapeKind.FACETED, Material.GLASS_BEAD,
            0xFFF0F0EC.toInt(), 0xFFC8C8C2.toInt(), 0xFF8E8E88.toInt(), "🧩", 0,
            "가장 밝고 날카롭다", SurfaceKind.SPECKLE, 0xFF2A2A30.toInt(),
            textureAsset = "ball_cube.jpg",
            kneadColors = listOf(0xFFD84040.toInt(), 0xFF3A6AD8.toInt(), 0xFFF0C030.toInt(), 0xFF3AA850.toInt())),
        BallSpec(26, "판다", SizeClass.XL, Thickness.THICK, ShapeKind.LUMPY, Material.SQUISHY_WAX,
            0xFFF4F2EE.toInt(), 0xFFD8D4CE.toInt(), 0xFF9E9A94.toInt(), "🐼", 0,
            "가장 크고 두꺼워 한참 버틴다", SurfaceKind.SPECKLE, 0xFF23232A.toInt(),
            textureAsset = "ball_panda.jpg",
            kneadColors = listOf(0xFFF4F2EE.toInt(), 0xFF23232A.toInt())),
        BallSpec(27, "개구리", SizeClass.L, Thickness.THIN, ShapeKind.LUMPY, Material.GLITTER,
            0xFF6FBE4A.toInt(), 0xFF4E9A32.toInt(), 0xFF2E6A1E.toInt(), "🐸", 0,
            "자잘하고 밝게 사각사각", SurfaceKind.SPECKLE, 0xFFF2F8E8.toInt(),
            textureAsset = "ball_frog.jpg",
            kneadColors = listOf(0xFF6FBE4A.toInt(), 0xFFA8E07A.toInt(), 0xFFF2F8E8.toInt())),
        BallSpec(28, "은하수", SizeClass.L, Thickness.THIN, ShapeKind.SPHERE, Material.GLITTER,
            0xFF9AA7E8.toInt(), 0xFF3D4B8C.toInt(), 0xFF1E2447.toInt(), "✨", 0,
            "얇고 밝게 자잘한 사각사각", SurfaceKind.SPECKLE, 0xFFF0F2FF.toInt(),
            textureAsset = "2k_stars_milky_way.jpg",
            kneadColors = listOf(0xFF9AA7E8.toInt(), 0xFF3D4B8C.toInt(), 0xFFF0F2FF.toInt())),
        BallSpec(29, "고양이", SizeClass.M, Thickness.THICK, ShapeKind.SPHERE, Material.CHEWY_WAX,
            0xFFF2E8DC.toInt(), 0xFFD8C8B4.toInt(), 0xFFA89684.toInt(), "🐱", 0,
            "쫀득하게 끝없이 이어진다", SurfaceKind.SPECKLE, 0xFFE88AA0.toInt(),
            textureAsset = "ball_cat.jpg",
            kneadColors = listOf(0xFFF2E8DC.toInt(), 0xFF8A8894.toInt(), 0xFFE88AA0.toInt())),

        // --- 파스텔 반죽 ---
        BallSpec(30, "딸기우유 반죽", SizeClass.M, Thickness.NORMAL, ShapeKind.SPHERE, Material.SQUISHY_WAX,
            0xFFF6B8C8.toInt(), 0xFFFDF3EE.toInt(), 0xFFFBE4E8.toInt(), "🍓", 0,
            "말랑하게 낮은 뿌직 뿌직", SurfaceKind.SPECKLE, 0xFFFDF3EE.toInt(),
            textureAsset = "ball_clay_strawberry.jpg",
            kneadColors = listOf(0xFFF6B8C8.toInt(), 0xFFFDF3EE.toInt())),
        BallSpec(31, "민트초코 반죽", SizeClass.L, Thickness.THICK, ShapeKind.SPHERE, Material.CHEWY_WAX,
            0xFFA8E0C8.toInt(), 0xFF6B4A36.toInt(), 0xFFC8EAD8.toInt(), "🍫", 0,
            "쫀득하고 두꺼워 오래 간다", SurfaceKind.SPECKLE, 0xFF6B4A36.toInt(),
            textureAsset = "ball_clay_mintchoco.jpg",
            kneadColors = listOf(0xFFA8E0C8.toInt(), 0xFF6B4A36.toInt())),
        BallSpec(32, "파스텔 반죽", SizeClass.M, Thickness.NORMAL, ShapeKind.SPHERE, Material.SOFT_WAX,
            0xFFAECBF2.toInt(), 0xFFF6EBA8.toInt(), 0xFFF6F0E0.toInt(), "🎀", 0,
            "무르게 뭉개지는 뿌지직", SurfaceKind.SPECKLE, 0xFFF2B8C4.toInt(),
            textureAsset = "ball_clay_pastel.jpg",
            kneadColors = listOf(0xFFAECBF2.toInt(), 0xFFF6EBA8.toInt(), 0xFFF2B8C4.toInt())),
        BallSpec(33, "포도 반죽", SizeClass.S, Thickness.THICK, ShapeKind.SPHERE, Material.CLAY_WAX,
            0xFF9C7CC8.toInt(), 0xFFC8B4E4.toInt(), 0xFFEDE4F6.toInt(), "🍇", 0,
            "둔하고 먹먹한 뽀갹 뽀갹", SurfaceKind.SPECKLE, 0xFFC8B4E4.toInt(),
            textureAsset = "ball_clay_grape.jpg",
            kneadColors = listOf(0xFF9C7CC8.toInt(), 0xFFC8B4E4.toInt(), 0xFFF2EAF6.toInt())),
        BallSpec(34, "무지개 반죽", SizeClass.M, Thickness.NORMAL, ShapeKind.SPHERE, Material.GLITTER,
            0xFFF2B8C4.toInt(), 0xFFF6EBA8.toInt(), 0xFFF6F0E8.toInt(), "🌈", 0,
            "밝고 자잘한 사각사각", SurfaceKind.SPECKLE, 0xFFAECBF2.toInt(),
            textureAsset = "ball_clay_rainbow.jpg",
            kneadColors = listOf(0xFFF2B8C4.toInt(), 0xFFF6EBA8.toInt(), 0xFFA8E0C8.toInt(), 0xFFAECBF2.toInt())),
        BallSpec(35, "사탕 반죽", SizeClass.M, Thickness.THICK, ShapeKind.SPHERE, Material.SUGAR_GLASS,
            0xFFE86A6A.toInt(), 0xFFFDF3EE.toInt(), 0xFFF6E8E0.toInt(), "🍬", 0,
            "설탕 유리처럼 쨍하게 부서진다", SurfaceKind.SPECKLE, 0xFFF0A048.toInt(),
            textureAsset = "ball_clay_candy.jpg",
            kneadColors = listOf(0xFFE86A6A.toInt(), 0xFFFDF3EE.toInt(), 0xFFF0A048.toInt(), 0xFF78C86A.toInt())),

        // --- 캐릭터 얼굴 2차 ---
        // 재질 조합은 기존 볼과 겹치지 않게 골라 "전부 다르게 들린다" 검증을 유지한다.
        BallSpec(36, "토끼", SizeClass.L, Thickness.NORMAL, ShapeKind.SPHERE, Material.SOFT_WAX,
            0xFFF7F3EE.toInt(), 0xFFE3D8D2.toInt(), 0xFFCBBDB6.toInt(), "🐰", 0,
            "무르고 낮게 뭉개진다", SurfaceKind.SPECKLE, 0xFFF2B2C0.toInt(),
            textureAsset = "ball_rabbit.jpg",
            kneadColors = listOf(0xFFF7F3EE.toInt(), 0xFFF2B2C0.toInt())),
        BallSpec(37, "강아지", SizeClass.M, Thickness.THICK, ShapeKind.SPHERE, Material.HARD_WAX,
            0xFFE8C89A.toInt(), 0xFFC49A66.toInt(), 0xFF8E6A42.toInt(), "🐶", 0,
            "두껍고 마른 빠작", SurfaceKind.SPECKLE, 0xFF7A5230.toInt(),
            textureAsset = "ball_dog.jpg",
            kneadColors = listOf(0xFFE8C89A.toInt(), 0xFF7A5230.toInt(), 0xFFFAF0E1.toInt())),
        BallSpec(38, "펭귄", SizeClass.M, Thickness.NORMAL, ShapeKind.SPHERE, Material.THICK_WAX,
            0xFF2A2E38.toInt(), 0xFF3E4450.toInt(), 0xFF1A1D24.toInt(), "🐧", 0,
            "묵직하게 뽀각뽀각", SurfaceKind.SPECKLE, 0xFFF5F7FA.toInt(),
            textureAsset = "ball_penguin.jpg",
            kneadColors = listOf(0xFF2A2E38.toInt(), 0xFFF5F7FA.toInt(), 0xFFF0963C.toInt())),
        BallSpec(39, "호랑이", SizeClass.L, Thickness.NORMAL, ShapeKind.SPHERE, Material.CRUNCH_BEADS,
            0xFFE8963C.toInt(), 0xFFB86E24.toInt(), 0xFF7A4614.toInt(), "🐯", 0,
            "알갱이가 굵게 이어진다", SurfaceKind.SPECKLE, 0xFF28201A.toInt(),
            textureAsset = "ball_tiger.jpg",
            kneadColors = listOf(0xFFE8963C.toInt(), 0xFF28201A.toInt(), 0xFFFAF4EA.toInt())),
        BallSpec(40, "코알라", SizeClass.S, Thickness.NORMAL, ShapeKind.SPHERE, Material.SQUISHY_WAX,
            0xFFB9B4B8.toInt(), 0xFF968F94.toInt(), 0xFF6E686C.toInt(), "🐨", 0,
            "작고 말랑한 뿌직", SurfaceKind.SPECKLE, 0xFFE8BEC8.toInt(),
            textureAsset = "ball_koala.jpg",
            kneadColors = listOf(0xFFB9B4B8.toInt(), 0xFFE8BEC8.toInt())),
        BallSpec(41, "오리", SizeClass.M, Thickness.NORMAL, ShapeKind.SPHERE, Material.SUGAR_GLASS,
            0xFFFAF6EC.toInt(), 0xFFE8DCC2.toInt(), 0xFFC8B896.toInt(), "🦆", 0,
            "맑고 쨍한 챙그랑", SurfaceKind.SPECKLE, 0xFFF5A03C.toInt(),
            textureAsset = "ball_duck.jpg",
            kneadColors = listOf(0xFFFAF6EC.toInt(), 0xFFF5A03C.toInt())),
    )

    /** 전부 열려 있다. 코인으로 여는 것은 없앴다. */
    val free: List<Int> = all.map { it.id }

    fun byId(id: Int): BallSpec = all.getOrElse(id) { all[0] }

    /**
     * 볼 고르기 줄에 보이는 순서.
     *
     * [all]의 순서(=id)는 손대지 않는다. id가 곧 언어별 이름·설명 배열의 인덱스이고
     * 저장된 "마지막에 고른 볼"도 그 값이라, 순서를 바꾸면 이름이 통째로 어긋난다.
     * 앞자리는 성격이 뚜렷해서 처음 만져 보기 좋은 것들로 골랐다.
     */
    private val FIRST = intArrayOf(32, 20, 0, 7, 21)  // 파스텔 반죽·돼지·지구·주사위·8번공

    /**
     * 하늘에 있는 것들. 앞쪽은 손에 잡히는 것(장난감·캐릭터·반죽)으로 채우고
     * 이들은 뒤로 몰아 둔다. 지구만 [FIRST]에 있어 맨 앞자리를 지킨다.
     */
    private val CELESTIAL = setOf(0, 1, 2, 3, 4, 5, 6, 9, 14, 18, 19, 23, 24, 28)

    val displayOrder: List<BallSpec> = buildList {
        FIRST.forEach { add(byId(it)) }
        val shown = FIRST.toSet()
        all.filterTo(this) { it.id !in shown && it.id !in CELESTIAL }
        all.filterTo(this) { it.id !in shown && it.id in CELESTIAL }
    }
}
