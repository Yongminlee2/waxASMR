package com.waxball.asmr.core

/**
 * 볼 30종. 크기·두께·모양·재질 네 축의 조합이고, 조합마다 소리가 다르다.
 * 앞의 5종은 처음부터 열려 있고, 나머지는 코인으로 연다.
 */
object BallCatalog {

    val all: List<BallSpec> = listOf(
        // --- 기본 해금 5종: 재질을 하나씩 맛보게 한다 ---
        BallSpec(0, "노른자", SizeClass.M, Thickness.NORMAL, ShapeKind.SPHERE, Material.HARD_WAX,
            0xFFE8D9B8.toInt(), 0xFFF2C14E.toInt(), 0xFFE0A32E.toInt(), "🐣", 0,
            "기준이 되는 소리. 마르고 단단한 빠작"),
        BallSpec(1, "찹쌀떡", SizeClass.M, Thickness.NORMAL, ShapeKind.SPHERE, Material.SOFT_WAX,
            0xFFF3EDE4.toInt(), 0xFFE7D9F0.toInt(), 0xFFCBA6E8.toInt(), "🍡", 0,
            "낮고 둔한 뿌직. 금이 잘 안 번진다"),
        BallSpec(2, "은하수", SizeClass.M, Thickness.THIN, ShapeKind.SPHERE, Material.GLITTER,
            0xFF9AA7E8.toInt(), 0xFF3D4B8C.toInt(), 0xFF1E2447.toInt(), "✨", 0,
            "얇고 밝게 자잘한 사각사각"),
        BallSpec(3, "우박", SizeClass.M, Thickness.NORMAL, ShapeKind.LUMPY, Material.CRUNCH_BEADS,
            0xFFD7DEE4.toInt(), 0xFF7FB2C9.toInt(), 0xFF3E6E88.toInt(), "🧊", 0,
            "알갱이가 오래 이어지는 빠자자작"),
        BallSpec(4, "박하사탕", SizeClass.S, Thickness.THIN, ShapeKind.SPHERE, Material.SUGAR_GLASS,
            0xFFEAF7F2.toInt(), 0xFF7EE0C0.toInt(), 0xFF2FA383.toInt(), "🍬", 0,
            "가장 높고 뾰족한 챙그랑"),

        // --- 굳은 왁스 계열 ---
        BallSpec(5, "밤톨", SizeClass.S, Thickness.THICK, ShapeKind.SPHERE, Material.HARD_WAX,
            0xFF8A5A32.toInt(), 0xFFD9A566.toInt(), 0xFFF2E2C4.toInt(), "🌰", 40,
            "작지만 껍질이 두꺼워 묵직한 뽀각"),
        BallSpec(6, "달걀", SizeClass.M, Thickness.NORMAL, ShapeKind.EGG, Material.HARD_WAX,
            0xFFF6EEE2.toInt(), 0xFFFFD98A.toInt(), 0xFFE8A33D.toInt(), "🥚", 60,
            "위아래로 길어 깨는 순서가 다르다"),
        BallSpec(7, "주사위", SizeClass.M, Thickness.NORMAL, ShapeKind.FACETED, Material.HARD_WAX,
            0xFFF0F0F0.toInt(), 0xFFB9C0CC.toInt(), 0xFF5C6472.toInt(), "🎲", 80,
            "모서리부터 우수수 떨어진다"),
        BallSpec(8, "보름달", SizeClass.L, Thickness.NORMAL, ShapeKind.SPHERE, Material.HARD_WAX,
            0xFFEFE7CE.toInt(), 0xFFD8CBA4.toInt(), 0xFFA89468.toInt(), "🌕", 110,
            "커진 만큼 저음이 실린다"),
        BallSpec(9, "고목", SizeClass.L, Thickness.THICK, ShapeKind.LUMPY, Material.HARD_WAX,
            0xFF6E5236.toInt(), 0xFFB98F5A.toInt(), 0xFFE6D2A8.toInt(), "🪵", 150,
            "두껍고 울퉁불퉁해 한 번에 잘 안 깨진다"),
        BallSpec(10, "거대알", SizeClass.XL, Thickness.THICK, ShapeKind.EGG, Material.HARD_WAX,
            0xFFE3D6BC.toInt(), 0xFFC0A97E.toInt(), 0xFF7A6440.toInt(), "🦖", 260,
            "가장 낮고 여운이 길다"),

        // --- 무른 왁스 계열 ---
        BallSpec(11, "복숭아", SizeClass.M, Thickness.THIN, ShapeKind.SPHERE, Material.SOFT_WAX,
            0xFFF8C9C0.toInt(), 0xFFFFE3D2.toInt(), 0xFFF08A72.toInt(), "🍑", 50,
            "얇고 물러 툭툭 벗겨진다"),
        BallSpec(12, "감자", SizeClass.M, Thickness.THICK, ShapeKind.LUMPY, Material.SOFT_WAX,
            0xFFC9A96E.toInt(), 0xFFEEDFC0.toInt(), 0xFFF5EEDC.toInt(), "🥔", 70,
            "둔탁하고 눅진한 뿌직"),
        BallSpec(13, "말랑젤리", SizeClass.S, Thickness.NORMAL, ShapeKind.SPHERE, Material.SOFT_WAX,
            0xFFE9A7D4.toInt(), 0xFFFFD6F0.toInt(), 0xFFC55BA0.toInt(), "🍮", 45,
            "작고 조용한 소리. 밤에 듣기 좋다"),
        BallSpec(14, "찰흙덩이", SizeClass.L, Thickness.THICK, ShapeKind.SPHERE, Material.SOFT_WAX,
            0xFF9C8B7A.toInt(), 0xFFC7B6A2.toInt(), 0xFFE8DDCE.toInt(), "🧱", 130,
            "가장 낮고 무거운 뭉근한 소리"),
        BallSpec(15, "물풍선", SizeClass.XL, Thickness.THIN, ShapeKind.EGG, Material.SOFT_WAX,
            0xFFA8D8F0.toInt(), 0xFFD6EEFA.toInt(), 0xFF4A9AC8.toInt(), "💧", 200,
            "크고 얇아 넓게 쫙쫙 벗겨진다"),

        // --- 반짝이 계열 ---
        BallSpec(16, "샛별", SizeClass.S, Thickness.THIN, ShapeKind.SPHERE, Material.GLITTER,
            0xFFF7E6A8.toInt(), 0xFFFFF6D6.toInt(), 0xFFE8C24A.toInt(), "⭐", 55,
            "제일 자잘하고 빠른 사각사각"),
        BallSpec(17, "크리스탈", SizeClass.M, Thickness.NORMAL, ShapeKind.FACETED, Material.GLITTER,
            0xFFCFE6F5.toInt(), 0xFF8FC0E0.toInt(), 0xFF3E7AA0.toInt(), "💎", 95,
            "각진 면이 밝게 튀어 오른다"),
        BallSpec(18, "오로라", SizeClass.L, Thickness.THIN, ShapeKind.LUMPY, Material.GLITTER,
            0xFF9FE0C8.toInt(), 0xFFB8A6E8.toInt(), 0xFF5B4B9C.toInt(), "🌌", 140,
            "넓게 퍼지며 반짝이는 잔향"),
        BallSpec(19, "황금알", SizeClass.M, Thickness.THICK, ShapeKind.EGG, Material.GLITTER,
            0xFFE6BE55.toInt(), 0xFFFFE9A8.toInt(), 0xFFB88820.toInt(), "🏆", 170,
            "두꺼워서 반짝임에 무게가 붙는다"),
        BallSpec(20, "별무리", SizeClass.XL, Thickness.NORMAL, ShapeKind.SPHERE, Material.GLITTER,
            0xFF7E8CD6.toInt(), 0xFFB9C4F0.toInt(), 0xFF2C3566.toInt(), "🌠", 240,
            "조각이 가장 많아 오래 반짝인다"),

        // --- 알갱이 계열 ---
        BallSpec(21, "팝콘", SizeClass.S, Thickness.NORMAL, ShapeKind.LUMPY, Material.CRUNCH_BEADS,
            0xFFF7EFD8.toInt(), 0xFFFFF9EC.toInt(), 0xFFE0C87A.toInt(), "🍿", 50,
            "톡톡 튀는 잔소리가 계속 이어진다"),
        BallSpec(22, "자갈밭", SizeClass.M, Thickness.THICK, ShapeKind.SPHERE, Material.CRUNCH_BEADS,
            0xFF9AA0A6.toInt(), 0xFFC6CCD2.toInt(), 0xFF5A6068.toInt(), "🪨", 90,
            "자갈 밟는 소리에 가장 가깝다"),
        BallSpec(23, "각설탕", SizeClass.M, Thickness.THIN, ShapeKind.FACETED, Material.CRUNCH_BEADS,
            0xFFFDFBF6.toInt(), 0xFFEFE6D4.toInt(), 0xFFC9B68C.toInt(), "🧊", 100,
            "얇고 각져서 한 번에 와르르"),
        BallSpec(24, "시리얼", SizeClass.L, Thickness.NORMAL, ShapeKind.LUMPY, Material.CRUNCH_BEADS,
            0xFFD9A15B.toInt(), 0xFFF0CE96.toInt(), 0xFF8C5E28.toInt(), "🥣", 160,
            "가장 촘촘하고 길게 이어지는 빠자자작"),
        BallSpec(25, "운석", SizeClass.XL, Thickness.THICK, ShapeKind.LUMPY, Material.CRUNCH_BEADS,
            0xFF4E4A48.toInt(), 0xFF8A7F78.toInt(), 0xFFD8C9B8.toInt(), "☄️", 280,
            "묵직한 저음 위에 알갱이가 쏟아진다"),

        // --- 설탕유리 계열 ---
        BallSpec(26, "유리구슬", SizeClass.S, Thickness.NORMAL, ShapeKind.SPHERE, Material.SUGAR_GLASS,
            0xFFDFF3FA.toInt(), 0xFF9FD6EA.toInt(), 0xFF3E8CA8.toInt(), "🔮", 75,
            "맑고 짧게 끊어지는 소리"),
        BallSpec(27, "얼음꽃", SizeClass.M, Thickness.THIN, ShapeKind.FACETED, Material.SUGAR_GLASS,
            0xFFEFFAFF.toInt(), 0xFFB8E4F5.toInt(), 0xFF4FA8C8.toInt(), "❄️", 120,
            "가장 밝고 금이 제일 잘 번진다"),
        BallSpec(28, "호박엿", SizeClass.L, Thickness.THICK, ShapeKind.EGG, Material.SUGAR_GLASS,
            0xFFD98E2B.toInt(), 0xFFF5C878.toInt(), 0xFF8C5410.toInt(), "🍯", 190,
            "높은 소리인데 여운이 길다"),
        BallSpec(29, "샹들리에", SizeClass.XL, Thickness.THIN, ShapeKind.FACETED, Material.SUGAR_GLASS,
            0xFFFFF6E0.toInt(), 0xFFEBD9A8.toInt(), 0xFFBFA155.toInt(), "🕯️", 300,
            "얇고 큰 유리가 한꺼번에 쏟아진다"),
    )

    val free: List<Int> = all.filter { it.price == 0 }.map { it.id }

    fun byId(id: Int): BallSpec = all.getOrElse(id) { all[0] }
}
