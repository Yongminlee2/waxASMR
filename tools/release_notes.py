# -*- coding: utf-8 -*-
"""첫 출시 노트 12개 언어. 콘솔이 쓰는 태그 형식 그대로 만들고 글자수를 검증한다."""
import io, os

LIMIT = 500

NOTES = {
"ko-KR": """첫 출시입니다.

손바닥을 카메라에 비추면 그 위에 왁스볼이 올라옵니다. 손을 쥐면 쥔 만큼 부서지고, 다 부순 뒤에도 계속 주무르면 조각이 속살과 섞이며 색이 천천히 바뀝니다.

· 직접 녹음한 진짜 부서지는 소리
· 볼 42종 — 행성부터 캐릭터, 파스텔 반죽까지
· 광고 없음, 개인정보 수집 없음""",

"en-US": """First release.

Show your open palm to the camera and a wax ball settles onto it. Squeeze and it crumbles, as hard as you squeeze. Keep kneading afterwards and the pieces blend into the core as the colour slowly shifts.

· Real recorded crushing sounds
· 42 balls — planets, characters and pastel dough
· No ads, no data collection""",

"ja-JP": """初回リリースです。

手のひらをカメラに見せると、その上にワックスボールが乗ります。手を握ると握った分だけ砕け、砕いたあとも こね続けると破片が中身と混ざって色がゆっくり変わります。

・実際に録音した本物の音
・ボール42種 — 惑星からキャラクター、パステル粘土まで
・広告なし、個人情報の収集なし""",

"zh-CN": """首次发布。

把手掌摊开对准相机，蜡球就会落在你的手上。握紧手，它会随着你用力的程度碎开；捏碎之后继续揉捏，碎片会和里面混在一起，颜色慢慢改变。

· 亲手录制的真实声音
· 42种球 — 从行星到角色、粉彩黏土
· 无广告，不收集个人信息""",

"es-ES": """Primera versión.

Muestra tu palma abierta a la cámara y una bola de cera se posa sobre ella. Aprieta y se desmorona, tanto como aprietes. Sigue amasando después y los trozos se mezclan con el interior mientras el color cambia poco a poco.

· Sonidos reales grabados
· 42 bolas — planetas, personajes y masa pastel
· Sin anuncios, sin recopilación de datos""",

"pt-BR": """Primeira versão.

Mostre a palma da mão aberta para a câmera e uma bola de cera pousa sobre ela. Aperte e ela se desfaz, na medida em que você aperta. Continue amassando e os pedaços se misturam ao interior enquanto a cor muda aos poucos.

· Sons reais gravados
· 42 bolas — planetas, personagens e massinha pastel
· Sem anúncios, sem coleta de dados""",

"de-DE": """Erste Veröffentlichung.

Zeig der Kamera deine offene Handfläche und ein Wachsball legt sich darauf. Drück zu und er zerbröselt – so stark, wie du drückst. Knetest du danach weiter, vermischen sich die Stücke mit dem Kern und die Farbe verändert sich langsam.

· Echte Tonaufnahmen
· 42 Bälle — Planeten, Figuren und Pastellknete
· Keine Werbung, keine Datenerhebung""",

"fr-FR": """Première version.

Montre ta paume ouverte à la caméra : une boule de cire vient s'y poser. Serre et elle s'effrite, autant que tu serres. Continue à malaxer ensuite et les morceaux se mêlent au cœur tandis que la couleur change peu à peu.

· Sons réels enregistrés
· 42 boules — planètes, personnages et pâte pastel
· Sans publicité, sans collecte de données""",

"ru-RU": """Первый выпуск.

Покажите камере раскрытую ладонь — на неё ляжет восковой шарик. Сожмите руку, и он начнёт крошиться. Продолжайте мять, и осколки смешаются с сердцевиной, а цвет будет медленно меняться.

· Настоящие записи звуков
· 42 шарика — планеты, персонажи и пастельная масса
· Без рекламы и сбора данных""",

"id": """Rilis pertama.

Tunjukkan telapak tangan terbuka ke kamera, lalu bola lilin akan hinggap di atasnya. Genggam dan bola itu hancur, sekuat Anda menggenggam. Terus remas setelahnya, pecahannya menyatu dengan isinya dan warnanya berubah perlahan.

· Suara rekaman asli
· 42 bola — planet, karakter, dan adonan pastel
· Tanpa iklan, tanpa pengumpulan data""",

"vi": """Bản phát hành đầu tiên.

Xòe lòng bàn tay hướng vào camera, một quả bóng sáp sẽ nằm lên đó. Nắm tay lại và nó vỡ vụn, mạnh đến đâu tùy bạn bóp. Tiếp tục nhào bóp, các mảnh vụn sẽ hòa vào phần ruột và màu sắc từ từ đổi khác.

· Âm thanh thu thật
· 42 quả bóng — hành tinh, nhân vật và đất nặn pastel
· Không quảng cáo, không thu thập dữ liệu""",

"th": """เวอร์ชันแรก

แบมือให้กล้องเห็น แล้วลูกบอลขี้ผึ้งจะมาวางบนฝ่ามือ กำมือแล้วมันจะแตกร่วนตามแรงที่คุณบีบ หลังจากนั้นนวดต่อไป เศษต่าง ๆ จะผสมเข้ากับเนื้อข้างในและสีจะค่อย ๆ เปลี่ยน

· เสียงจริงจากการบันทึก
· ลูกบอล 42 แบบ — ดาวเคราะห์ ตัวละคร และแป้งโดว์พาสเทล
· ไม่มีโฆษณา ไม่เก็บข้อมูล""",
}

ORDER = ["ko-KR", "de-DE", "en-US", "es-ES", "fr-FR", "id",
         "ja-JP", "pt-BR", "ru-RU", "th", "vi", "zh-CN"]

blocks, bad = [], 0
for k in ORDER:
    t = NOTES[k].strip()
    n = len(t)
    if n > LIMIT:
        bad += 1
    print(f"  {'OK ' if n <= LIMIT else 'OVER'} {k:6s} {n:>3d}/{LIMIT}")
    blocks.append(f"<{k}>\n{t}\n</{k}>")

out = "\n".join(blocks) + "\n"
path = r"C:\workAndroid\WaxBall\docs\release-notes-v1.txt"
io.open(path, "w", encoding="utf-8", newline="\n").write(out)
print("초과", bad, "건 ->", path)
