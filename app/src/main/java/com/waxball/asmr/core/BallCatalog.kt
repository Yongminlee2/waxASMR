package com.waxball.asmr.core

/**
 * 볼 30종. 태양계를 한 바퀴 돈다.
 *
 * 전에는 "노른자·찹쌀떡" 같은 이름에 단색을 입힌 것이었는데, 3D로 굴려 보면
 * 단색 구는 어느 각도에서 봐도 똑같아서 볼을 바꾼 티가 잘 안 났다. 행성은 표면에
 * 무늬가 있어서 돌릴 때마다 다른 면이 나온다.
 *
 * 무늬는 셰이더가 절차적으로 그린다([SurfaceKind]). 텍스처를 쓰면 30장이 붙어
 * 용량을 감당할 수 없다.
 *
 * 재질 10종을 성격에 맞춰 배정했다 — 얼음 위성은 밝고 뾰족한 쪽, 가스 행성은
 * 둔하고 말랑한 쪽이다. 전부 열려 있고 값은 없다.
 */
object BallCatalog {

    val all: List<BallSpec> = listOf(
        // --- 안쪽 행성 ---
        BallSpec(0, "지구", SizeClass.M, Thickness.NORMAL, ShapeKind.SPHERE, Material.HARD_WAX,
            0xFF2E7FD4.toInt(), 0xFF3FA35C.toInt(), 0xFFEAF3FF.toInt(), "🌍", 0,
            "기준이 되는 소리. 마르고 단단한 빠작", SurfaceKind.SWIRL, 0xFF3FA35C.toInt()),
        BallSpec(1, "태양", SizeClass.XL, Thickness.THIN, ShapeKind.SPHERE, Material.GLASS_BEAD,
            0xFFFFC24D.toInt(), 0xFFFF7A18.toInt(), 0xFFFFF3C4.toInt(), "☀", 0,
            "가장 밝고 뾰족하게 터진다", SurfaceKind.FLARE, 0xFFFFF0A0.toInt()),
        BallSpec(2, "수성", SizeClass.S, Thickness.THICK, ShapeKind.LUMPY, Material.THICK_WAX,
            0xFF9A9188.toInt(), 0xFF6B635C.toInt(), 0xFF433D38.toInt(), "☿", 0,
            "작고 두꺼워 묵직한 뽀각", SurfaceKind.CRATER, 0xFF5E564F.toInt()),
        BallSpec(3, "금성", SizeClass.M, Thickness.THICK, ShapeKind.SPHERE, Material.CLAY_WAX,
            0xFFE8C88A.toInt(), 0xFFC98F42.toInt(), 0xFF8A5A22.toInt(), "♀", 0,
            "가장 둔하고 답답하게 뭉개진다", SurfaceKind.SWIRL, 0xFFF6E3B4.toInt()),
        BallSpec(4, "달", SizeClass.S, Thickness.NORMAL, ShapeKind.SPHERE, Material.HARD_WAX,
            0xFFDDD8CE.toInt(), 0xFFA9A399.toInt(), 0xFF6E6962.toInt(), "🌕", 0,
            "작아서 한 음 높다", SurfaceKind.CRATER, 0xFF8E887F.toInt()),
        BallSpec(5, "화성", SizeClass.M, Thickness.NORMAL, ShapeKind.SPHERE, Material.CRUNCH_BEADS,
            0xFFC1573A.toInt(), 0xFF8E3A22.toInt(), 0xFF5A2313.toInt(), "♂", 0,
            "알갱이가 오래 이어지는 빠자자작", SurfaceKind.SPECKLE, 0xFFE08A5E.toInt()),

        // --- 소행성대 ---
        BallSpec(6, "세레스", SizeClass.S, Thickness.NORMAL, ShapeKind.LUMPY, Material.CRUNCH_BEADS,
            0xFFA8A29A.toInt(), 0xFF7A756E.toInt(), 0xFF4C4843.toInt(), "🪨", 0,
            "울퉁불퉁해 음높이가 넓게 흩어진다", SurfaceKind.CRATER, 0xFF6E6963.toInt()),
        BallSpec(7, "베스타", SizeClass.S, Thickness.THICK, ShapeKind.FACETED, Material.THICK_WAX,
            0xFFB6AE9F.toInt(), 0xFF8A8274.toInt(), 0xFF56504A.toInt(), "🪨", 0,
            "모서리부터 우수수 떨어진다", SurfaceKind.SPECKLE, 0xFF7E7669.toInt()),
        BallSpec(8, "이카루스", SizeClass.S, Thickness.THIN, ShapeKind.LUMPY, Material.GLASS_BEAD,
            0xFFE3B15E.toInt(), 0xFFB07C2C.toInt(), 0xFF6E4A12.toInt(), "☄", 0,
            "얇고 밝게 부서진다", SurfaceKind.SPECKLE, 0xFFF6D79A.toInt()),

        // --- 목성과 위성 ---
        BallSpec(9, "목성", SizeClass.XL, Thickness.NORMAL, ShapeKind.SPHERE, Material.SQUISHY_WAX,
            0xFFD8A46A.toInt(), 0xFFB3763C.toInt(), 0xFF7A4A1E.toInt(), "♃", 0,
            "가장 크고 저음이 깊게 깔린다", SurfaceKind.BANDED, 0xFFF2DCC0.toInt()),
        BallSpec(10, "이오", SizeClass.S, Thickness.THIN, ShapeKind.SPHERE, Material.SUGAR_GLASS,
            0xFFE8D45C.toInt(), 0xFFC96B2E.toInt(), 0xFF8A3C12.toInt(), "🌋", 0,
            "높고 뾰족한 챙그랑", SurfaceKind.LAVA, 0xFFB33A1E.toInt()),
        BallSpec(11, "유로파", SizeClass.M, Thickness.THIN, ShapeKind.SPHERE, Material.GLASS_BEAD,
            0xFFDCE9F2.toInt(), 0xFFA8C4DA.toInt(), 0xFF5E86A8.toInt(), "🧊", 0,
            "얼음처럼 밝고 자잘하게", SurfaceKind.ICY, 0xFF8A6A4E.toInt()),
        BallSpec(12, "가니메데", SizeClass.L, Thickness.NORMAL, ShapeKind.SPHERE, Material.HARD_WAX,
            0xFFAFA79F.toInt(), 0xFF8A8078.toInt(), 0xFF5A534D.toInt(), "🌑", 0,
            "커진 만큼 저음이 실린다", SurfaceKind.CRATER, 0xFF7E766E.toInt()),
        BallSpec(13, "칼리스토", SizeClass.L, Thickness.THICK, ShapeKind.LUMPY, Material.THICK_WAX,
            0xFF6E6862.toInt(), 0xFF4E4944.toInt(), 0xFF2E2B28.toInt(), "🌑", 0,
            "두껍고 둔해 오래 버틴다", SurfaceKind.CRATER, 0xFFB4AEA6.toInt()),

        // --- 토성과 위성 ---
        BallSpec(14, "토성", SizeClass.XL, Thickness.THIN, ShapeKind.EGG, Material.SOFT_WAX,
            0xFFE6D2A0.toInt(), 0xFFC4A868.toInt(), 0xFF8E7638.toInt(), "🪐", 0,
            "길쭉해 공명이 더 산다", SurfaceKind.BANDED, 0xFFF7ECCE.toInt()),
        BallSpec(15, "타이탄", SizeClass.L, Thickness.THICK, ShapeKind.SPHERE, Material.CLAY_WAX,
            0xFFD99A3E.toInt(), 0xFFA86C1E.toInt(), 0xFF6E4410.toInt(), "🟠", 0,
            "가장 둔하고 먹먹하다", SurfaceKind.SWIRL, 0xFFF0C878.toInt()),
        BallSpec(16, "엔셀라두스", SizeClass.S, Thickness.NORMAL, ShapeKind.LUMPY, Material.SUGAR_GLASS,
            0xFFF2FAFF.toInt(), 0xFFC8E4F4.toInt(), 0xFF7EB4D4.toInt(), "❄", 0,
            "작고 얇아 가장 높다", SurfaceKind.ICY, 0xFF9EC8E0.toInt()),
        BallSpec(17, "이아페투스", SizeClass.M, Thickness.NORMAL, ShapeKind.EGG, Material.HARD_WAX,
            0xFFC8C0B4.toInt(), 0xFF6A5F54.toInt(), 0xFF3A342E.toInt(), "🌗", 0,
            "한쪽이 밝고 한쪽이 어둡다", SurfaceKind.BANDED, 0xFF4E453C.toInt()),

        // --- 바깥 행성 ---
        BallSpec(18, "천왕성", SizeClass.L, Thickness.NORMAL, ShapeKind.SPHERE, Material.SQUISHY_WAX,
            0xFF8ED6DC.toInt(), 0xFF4E9AA8.toInt(), 0xFF2A6470.toInt(), "🔵", 0,
            "말랑하게 낮은 뿌직", SurfaceKind.BANDED, 0xFFB8E8EC.toInt()),
        BallSpec(19, "해왕성", SizeClass.L, Thickness.NORMAL, ShapeKind.SPHERE, Material.CHEWY_WAX,
            0xFF3B5FC4.toInt(), 0xFF23408E.toInt(), 0xFF14265A.toInt(), "🔷", 0,
            "쫀득하게 촘촘히 이어진다", SurfaceKind.SWIRL, 0xFF89A8F0.toInt()),
        BallSpec(20, "명왕성", SizeClass.S, Thickness.NORMAL, ShapeKind.LUMPY, Material.SOFT_WAX,
            0xFFCBB8A2.toInt(), 0xFF9A8672.toInt(), 0xFF5E5244.toInt(), "🤍", 0,
            "낮고 둔한 뿌직", SurfaceKind.SPECKLE, 0xFFEDE2D2.toInt()),
        BallSpec(21, "카론", SizeClass.S, Thickness.THIN, ShapeKind.SPHERE, Material.GLITTER,
            0xFFB9C2CC.toInt(), 0xFF7E8894.toInt(), 0xFF4A525C.toInt(), "🌘", 0,
            "얇고 밝게 자잘한 사각사각", SurfaceKind.CRATER, 0xFF8A93A0.toInt()),

        // --- 혜성과 얼음천체 ---
        BallSpec(22, "핼리 혜성", SizeClass.M, Thickness.THIN, ShapeKind.LUMPY, Material.GLITTER,
            0xFFCDE8F2.toInt(), 0xFF7EB6D2.toInt(), 0xFF3E6E88.toInt(), "☄", 0,
            "얇고 밝게 흩어진다", SurfaceKind.ICY, 0xFF6E9CBC.toInt()),
        BallSpec(23, "에리스", SizeClass.M, Thickness.NORMAL, ShapeKind.SPHERE, Material.CLAY_WAX,
            0xFFE4E8EC.toInt(), 0xFFAEB6BE.toInt(), 0xFF6E767E.toInt(), "🤍", 0,
            "두껍고 둔하게 눌린다", SurfaceKind.ICY, 0xFFAEB6BE.toInt()),
        BallSpec(24, "마케마케", SizeClass.S, Thickness.NORMAL, ShapeKind.EGG, Material.CHEWY_WAX,
            0xFFD48A6A.toInt(), 0xFFA45E42.toInt(), 0xFF6A3826.toInt(), "🟤", 0,
            "쫀득하게 촘촘히", SurfaceKind.SPECKLE, 0xFFF0B294.toInt()),

        // --- 별과 성운 ---
        BallSpec(25, "시리우스", SizeClass.S, Thickness.THIN, ShapeKind.FACETED, Material.GLASS_BEAD,
            0xFFE8F2FF.toInt(), 0xFFA8C8F0.toInt(), 0xFF5E8AD0.toInt(), "⭐", 0,
            "가장 밝고 날카롭다", SurfaceKind.FLARE, 0xFFFFFFFF.toInt()),
        BallSpec(26, "베텔게우스", SizeClass.XL, Thickness.THICK, ShapeKind.LUMPY, Material.SQUISHY_WAX,
            0xFFD4552E.toInt(), 0xFF9E3418.toInt(), 0xFF5E1C0A.toInt(), "🔴", 0,
            "가장 크고 두꺼워 한참 버틴다", SurfaceKind.FLARE, 0xFFF29A5E.toInt()),
        BallSpec(27, "게 성운", SizeClass.L, Thickness.THIN, ShapeKind.LUMPY, Material.GLITTER,
            0xFF8E5AC8.toInt(), 0xFF4E2A8A.toInt(), 0xFF241246.toInt(), "🌌", 0,
            "자잘하고 밝게 사각사각", SurfaceKind.SWIRL, 0xFFE0A8F0.toInt()),
        BallSpec(28, "은하수", SizeClass.L, Thickness.THIN, ShapeKind.SPHERE, Material.GLITTER,
            0xFF9AA7E8.toInt(), 0xFF3D4B8C.toInt(), 0xFF1E2447.toInt(), "✨", 0,
            "얇고 밝게 자잘한 사각사각", SurfaceKind.SPECKLE, 0xFFF0F2FF.toInt()),
        BallSpec(29, "블랙홀", SizeClass.M, Thickness.THICK, ShapeKind.SPHERE, Material.CHEWY_WAX,
            0xFF17161C.toInt(), 0xFF3A2E4E.toInt(), 0xFFE8C24D.toInt(), "🕳", 0,
            "쫀득하게 끝없이 이어진다", SurfaceKind.SWIRL, 0xFFE8A93D.toInt()),
    )

    /** 전부 열려 있다. 코인으로 여는 것은 없앴다. */
    val free: List<Int> = all.map { it.id }

    fun byId(id: Int): BallSpec = all.getOrElse(id) { all[0] }
}
