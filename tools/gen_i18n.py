# -*- coding: utf-8 -*-
"""왁뿌볼 ASMR 다국어 리소스 생성. 실제 쓰이는 UI 문자열 26개 + 언어선택 3개 +
볼 카탈로그(이름 36·소리설명 36·재질10·모양4·크기4·두께 3)를 언어별로 만든다."""
import io, os

RES = r"C:\workAndroid\WaxBall\app\src\main\res"

# ---------------- UI 문자열 (실제 쓰이는 것만) ----------------
UI_KEYS = [
    "app_name", "back", "ar_count", "ar_no_camera", "ar_permission_needed",
    "ar_refresh", "ar_show_palm", "ar_squeeze", "cancel", "home_balls",
    "home_settings", "home_start", "home_tagline", "settings_haptics",
    "settings_orbit_lock", "settings_quality", "settings_quality_auto",
    "settings_quality_high", "settings_quality_low", "settings_quality_medium",
    "settings_raw_hint", "settings_raw_playback", "settings_reset",
    "settings_reset_confirm", "settings_reset_done", "settings_title",
    "settings_volume", "language_picker_cd", "language_picker_title",
    "language_follow_system",
]

UI = {}
UI["ko"] = {
    "app_name": "왁뿌볼 ASMR", "back": "나가기", "ar_count": "공 %d개",
    "ar_no_camera": "카메라를 쓸 수 없어 손바닥 모드를 열 수 없습니다",
    "ar_permission_needed": "카메라 권한이 있어야 손바닥에 올릴 수 있습니다",
    "ar_refresh": "새로고침", "ar_show_palm": "손바닥을 펴서 카메라에 보여주세요",
    "ar_squeeze": "손을 쥐어서 부수세요", "cancel": "취소", "home_balls": "볼 고르기",
    "home_settings": "설정", "home_start": "시작하기",
    "home_tagline": "손바닥에 올려놓고 쥐어서 부수세요", "settings_haptics": "진동",
    "settings_orbit_lock": "굴리기 잠금", "settings_quality": "화질",
    "settings_quality_auto": "자동", "settings_quality_high": "높음",
    "settings_quality_low": "낮음", "settings_quality_medium": "보통",
    "settings_raw_hint": "파편으로 쪼개지 않고 녹음을 통째로 틉니다. 소리 비교용입니다.",
    "settings_raw_playback": "녹음 원본 그대로 재생", "settings_reset": "기록 전부 지우기",
    "settings_reset_confirm": "설정과 마지막으로 고른 볼이 초기화됩니다. 되돌릴 수 없습니다.",
    "settings_reset_done": "기록을 지웠습니다.", "settings_title": "설정",
    "settings_volume": "음량", "language_picker_cd": "언어 선택",
    "language_picker_title": "언어 선택", "language_follow_system": "기기 언어 따르기",
}
UI["en"] = {
    "app_name": "WaxBall ASMR", "back": "Back", "ar_count": "%d balls",
    "ar_no_camera": "Camera unavailable, can't open palm mode",
    "ar_permission_needed": "Camera permission is needed to place a ball on your palm",
    "ar_refresh": "New ball", "ar_show_palm": "Open your palm to the camera",
    "ar_squeeze": "Squeeze your hand to crush it", "cancel": "Cancel",
    "home_balls": "Choose a ball", "home_settings": "Settings", "home_start": "Start",
    "home_tagline": "Place it on your palm and squeeze",
    "settings_haptics": "Vibration", "settings_orbit_lock": "Lock rolling",
    "settings_quality": "Quality", "settings_quality_auto": "Auto",
    "settings_quality_high": "High", "settings_quality_low": "Low",
    "settings_quality_medium": "Medium",
    "settings_raw_hint": "Plays the raw recording instead of splitting it into fragments. For comparing sounds.",
    "settings_raw_playback": "Play raw recording", "settings_reset": "Clear all data",
    "settings_reset_confirm": "This resets your settings and last chosen ball. It can't be undone.",
    "settings_reset_done": "Data cleared.", "settings_title": "Settings",
    "settings_volume": "Volume", "language_picker_cd": "Choose language",
    "language_picker_title": "Choose language", "language_follow_system": "Follow device language",
}
UI["zh"] = {
    "app_name": "捏蜡球 ASMR", "back": "返回", "ar_count": "%d 个球",
    "ar_no_camera": "无法使用相机，无法打开掌上模式",
    "ar_permission_needed": "需要相机权限才能把球放在手掌上",
    "ar_refresh": "换一个", "ar_show_palm": "把手掌摊开对准相机",
    "ar_squeeze": "握拳把它捏碎", "cancel": "取消", "home_balls": "选择球",
    "home_settings": "设置", "home_start": "开始",
    "home_tagline": "放在手掌上，握紧捏碎",
    "settings_haptics": "震动", "settings_orbit_lock": "锁定旋转",
    "settings_quality": "画质", "settings_quality_auto": "自动",
    "settings_quality_high": "高", "settings_quality_low": "低",
    "settings_quality_medium": "中",
    "settings_raw_hint": "不切成碎片，直接播放原始录音。用于比较音色。",
    "settings_raw_playback": "播放原始录音", "settings_reset": "清除全部数据",
    "settings_reset_confirm": "将重置设置和上次选择的球，无法恢复。",
    "settings_reset_done": "数据已清除。", "settings_title": "设置",
    "settings_volume": "音量", "language_picker_cd": "选择语言",
    "language_picker_title": "选择语言", "language_follow_system": "跟随设备语言",
}
UI["ja"] = {
    "app_name": "ワックスボール ASMR", "back": "戻る", "ar_count": "ボール %d個",
    "ar_no_camera": "カメラが使えないため手のひらモードを開けません",
    "ar_permission_needed": "手のひらに乗せるにはカメラの権限が必要です",
    "ar_refresh": "新しいボール", "ar_show_palm": "手のひらを開いてカメラに見せてください",
    "ar_squeeze": "手を握って砕いてください", "cancel": "キャンセル",
    "home_balls": "ボールを選ぶ", "home_settings": "設定", "home_start": "はじめる",
    "home_tagline": "手のひらに乗せて握って砕こう",
    "settings_haptics": "振動", "settings_orbit_lock": "回転ロック",
    "settings_quality": "画質", "settings_quality_auto": "自動",
    "settings_quality_high": "高", "settings_quality_low": "低",
    "settings_quality_medium": "中",
    "settings_raw_hint": "破片に分けず録音をそのまま再生します。音の比較用です。",
    "settings_raw_playback": "録音をそのまま再生", "settings_reset": "データを全て消去",
    "settings_reset_confirm": "設定と最後に選んだボールが初期化されます。元に戻せません。",
    "settings_reset_done": "データを消去しました。", "settings_title": "設定",
    "settings_volume": "音量", "language_picker_cd": "言語を選択",
    "language_picker_title": "言語を選択", "language_follow_system": "端末の言語に従う",
}
UI["es"] = {
    "app_name": "WaxBall ASMR", "back": "Volver", "ar_count": "%d bolas",
    "ar_no_camera": "Cámara no disponible, no se puede abrir el modo palma",
    "ar_permission_needed": "Se necesita permiso de cámara para colocar la bola en tu palma",
    "ar_refresh": "Nueva bola", "ar_show_palm": "Muestra tu palma abierta a la cámara",
    "ar_squeeze": "Aprieta la mano para aplastarla", "cancel": "Cancelar",
    "home_balls": "Elegir bola", "home_settings": "Ajustes", "home_start": "Empezar",
    "home_tagline": "Colócala en tu palma y apriétala",
    "settings_haptics": "Vibración", "settings_orbit_lock": "Bloquear giro",
    "settings_quality": "Calidad", "settings_quality_auto": "Automática",
    "settings_quality_high": "Alta", "settings_quality_low": "Baja",
    "settings_quality_medium": "Media",
    "settings_raw_hint": "Reproduce la grabación original entera en lugar de fragmentos. Para comparar sonidos.",
    "settings_raw_playback": "Reproducir grabación original", "settings_reset": "Borrar todos los datos",
    "settings_reset_confirm": "Se restablecerán los ajustes y la última bola elegida. No se puede deshacer.",
    "settings_reset_done": "Datos borrados.", "settings_title": "Ajustes",
    "settings_volume": "Volumen", "language_picker_cd": "Elegir idioma",
    "language_picker_title": "Elegir idioma", "language_follow_system": "Seguir idioma del dispositivo",
}
UI["pt"] = {
    "app_name": "WaxBall ASMR", "back": "Voltar", "ar_count": "%d bolas",
    "ar_no_camera": "Câmera indisponível, não é possível abrir o modo palma",
    "ar_permission_needed": "É preciso permissão da câmera para colocar a bola na palma da mão",
    "ar_refresh": "Nova bola", "ar_show_palm": "Mostre a palma da mão aberta para a câmera",
    "ar_squeeze": "Aperte a mão para esmagá-la", "cancel": "Cancelar",
    "home_balls": "Escolher bola", "home_settings": "Configurações", "home_start": "Começar",
    "home_tagline": "Coloque na palma da mão e aperte",
    "settings_haptics": "Vibração", "settings_orbit_lock": "Bloquear rotação",
    "settings_quality": "Qualidade", "settings_quality_auto": "Automática",
    "settings_quality_high": "Alta", "settings_quality_low": "Baixa",
    "settings_quality_medium": "Média",
    "settings_raw_hint": "Toca a gravação original inteira em vez de fragmentos. Para comparar sons.",
    "settings_raw_playback": "Tocar gravação original", "settings_reset": "Apagar todos os dados",
    "settings_reset_confirm": "As configurações e a última bola escolhida serão reiniciadas. Não pode ser desfeito.",
    "settings_reset_done": "Dados apagados.", "settings_title": "Configurações",
    "settings_volume": "Volume", "language_picker_cd": "Escolher idioma",
    "language_picker_title": "Escolher idioma", "language_follow_system": "Seguir idioma do dispositivo",
}
UI["de"] = {
    "app_name": "WaxBall ASMR", "back": "Zurück", "ar_count": "%d Bälle",
    "ar_no_camera": "Kamera nicht verfügbar, Handflächenmodus kann nicht geöffnet werden",
    "ar_permission_needed": "Kamerazugriff wird benötigt, um den Ball auf die Handfläche zu legen",
    "ar_refresh": "Neuer Ball", "ar_show_palm": "Zeig der Kamera deine offene Handfläche",
    "ar_squeeze": "Ballen Sie die Hand, um ihn zu zerdrücken", "cancel": "Abbrechen",
    "home_balls": "Ball wählen", "home_settings": "Einstellungen", "home_start": "Start",
    "home_tagline": "Auf die Handfläche legen und drücken",
    "settings_haptics": "Vibration", "settings_orbit_lock": "Drehen sperren",
    "settings_quality": "Qualität", "settings_quality_auto": "Automatisch",
    "settings_quality_high": "Hoch", "settings_quality_low": "Niedrig",
    "settings_quality_medium": "Mittel",
    "settings_raw_hint": "Spielt die Originalaufnahme statt Bruchstücken ab. Zum Klangvergleich.",
    "settings_raw_playback": "Originalaufnahme abspielen", "settings_reset": "Alle Daten löschen",
    "settings_reset_confirm": "Einstellungen und der zuletzt gewählte Ball werden zurückgesetzt. Das kann nicht rückgängig gemacht werden.",
    "settings_reset_done": "Daten gelöscht.", "settings_title": "Einstellungen",
    "settings_volume": "Lautstärke", "language_picker_cd": "Sprache wählen",
    "language_picker_title": "Sprache wählen", "language_follow_system": "Gerätesprache verwenden",
}
UI["id"] = {
    "app_name": "WaxBall ASMR", "back": "Kembali", "ar_count": "%d bola",
    "ar_no_camera": "Kamera tidak tersedia, mode telapak tangan tidak bisa dibuka",
    "ar_permission_needed": "Izin kamera diperlukan untuk meletakkan bola di telapak tangan",
    "ar_refresh": "Bola baru", "ar_show_palm": "Buka telapak tangan ke arah kamera",
    "ar_squeeze": "Genggam tangan untuk menghancurkannya", "cancel": "Batal",
    "home_balls": "Pilih bola", "home_settings": "Pengaturan", "home_start": "Mulai",
    "home_tagline": "Letakkan di telapak tangan lalu genggam",
    "settings_haptics": "Getaran", "settings_orbit_lock": "Kunci putaran",
    "settings_quality": "Kualitas", "settings_quality_auto": "Otomatis",
    "settings_quality_high": "Tinggi", "settings_quality_low": "Rendah",
    "settings_quality_medium": "Sedang",
    "settings_raw_hint": "Memutar rekaman asli utuh, bukan potongan-potongan. Untuk membandingkan suara.",
    "settings_raw_playback": "Putar rekaman asli", "settings_reset": "Hapus semua data",
    "settings_reset_confirm": "Pengaturan dan bola terakhir yang dipilih akan direset. Tidak bisa dibatalkan.",
    "settings_reset_done": "Data telah dihapus.", "settings_title": "Pengaturan",
    "settings_volume": "Volume", "language_picker_cd": "Pilih bahasa",
    "language_picker_title": "Pilih bahasa", "language_follow_system": "Ikuti bahasa perangkat",
}
UI["vi"] = {
    "app_name": "WaxBall ASMR", "back": "Quay lại", "ar_count": "%d quả bóng",
    "ar_no_camera": "Không dùng được camera nên không mở được chế độ lòng bàn tay",
    "ar_permission_needed": "Cần quyền camera để đặt bóng lên lòng bàn tay",
    "ar_refresh": "Bóng mới", "ar_show_palm": "Xòe lòng bàn tay hướng vào camera",
    "ar_squeeze": "Nắm tay để bóp vỡ bóng", "cancel": "Hủy",
    "home_balls": "Chọn bóng", "home_settings": "Cài đặt", "home_start": "Bắt đầu",
    "home_tagline": "Đặt lên lòng bàn tay rồi nắm bóp",
    "settings_haptics": "Rung", "settings_orbit_lock": "Khóa xoay",
    "settings_quality": "Chất lượng", "settings_quality_auto": "Tự động",
    "settings_quality_high": "Cao", "settings_quality_low": "Thấp",
    "settings_quality_medium": "Trung bình",
    "settings_raw_hint": "Phát nguyên bản bản ghi âm thay vì cắt thành mảnh. Dùng để so sánh âm thanh.",
    "settings_raw_playback": "Phát bản ghi âm gốc", "settings_reset": "Xóa toàn bộ dữ liệu",
    "settings_reset_confirm": "Cài đặt và quả bóng chọn lần cuối sẽ được đặt lại. Không thể hoàn tác.",
    "settings_reset_done": "Đã xóa dữ liệu.", "settings_title": "Cài đặt",
    "settings_volume": "Âm lượng", "language_picker_cd": "Chọn ngôn ngữ",
    "language_picker_title": "Chọn ngôn ngữ", "language_follow_system": "Theo ngôn ngữ thiết bị",
}
UI["th"] = {
    "app_name": "WaxBall ASMR", "back": "ย้อนกลับ", "ar_count": "%d ลูก",
    "ar_no_camera": "ใช้กล้องไม่ได้ จึงเปิดโหมดฝ่ามือไม่ได้",
    "ar_permission_needed": "ต้องได้รับสิทธิ์กล้องจึงจะวางลูกบอลบนฝ่ามือได้",
    "ar_refresh": "ลูกใหม่", "ar_show_palm": "แบมือให้กล้องเห็น",
    "ar_squeeze": "กำมือเพื่อบี้มันให้แตก", "cancel": "ยกเลิก",
    "home_balls": "เลือกลูกบอล", "home_settings": "ตั้งค่า", "home_start": "เริ่ม",
    "home_tagline": "วางบนฝ่ามือแล้วกำบี้",
    "settings_haptics": "การสั่น", "settings_orbit_lock": "ล็อกการหมุน",
    "settings_quality": "คุณภาพ", "settings_quality_auto": "อัตโนมัติ",
    "settings_quality_high": "สูง", "settings_quality_low": "ต่ำ",
    "settings_quality_medium": "ปานกลาง",
    "settings_raw_hint": "เปิดเสียงบันทึกต้นฉบับทั้งหมดแทนการตัดเป็นชิ้นเล็ก ๆ ใช้เพื่อเทียบเสียง",
    "settings_raw_playback": "เล่นเสียงบันทึกต้นฉบับ", "settings_reset": "ล้างข้อมูลทั้งหมด",
    "settings_reset_confirm": "การตั้งค่าและลูกบอลที่เลือกล่าสุดจะถูกรีเซ็ต ย้อนกลับไม่ได้",
    "settings_reset_done": "ล้างข้อมูลแล้ว", "settings_title": "ตั้งค่า",
    "settings_volume": "ระดับเสียง", "language_picker_cd": "เลือกภาษา",
    "language_picker_title": "เลือกภาษา", "language_follow_system": "ใช้ภาษาของอุปกรณ์",
}

# ---------------- 볼 카탈로그 (36개, id순) ----------------
NAMES = {}
NAMES["ko"] = ["지구","태양","수성","금성","달","화성","세레스","주사위","골프공","목성","테니스공","야구공","농구공","볼링공","토성","축구공","병아리","곰돌이","천왕성","해왕성","돼지","당구 8번공","수박","에리스","마케마케","알록 큐브","판다","개구리","은하수","고양이","딸기우유 반죽","민트초코 반죽","파스텔 반죽","포도 반죽","무지개 반죽","사탕 반죽"]
NAMES["en"] = ["Earth","Sun","Mercury","Venus","Moon","Mars","Ceres","Dice","Golf ball","Jupiter","Tennis ball","Baseball","Basketball","Bowling ball","Saturn","Soccer ball","Chick","Bear","Uranus","Neptune","Pig","Eight ball","Watermelon","Eris","Makemake","Rainbow cube","Panda","Frog","Milky Way","Cat","Strawberry milk dough","Mint choco dough","Pastel dough","Grape dough","Rainbow dough","Candy dough"]
NAMES["zh"] = ["地球","太阳","水星","金星","月球","火星","谷神星","骰子","高尔夫球","木星","网球","棒球","篮球","保龄球","土星","足球","小鸡","小熊","天王星","海王星","小猪","八号球","西瓜","阋神星","鸟神星","彩虹方块","熊猫","青蛙","银河","小猫","草莓牛奶黏土","薄荷巧克力黏土","粉彩黏土","葡萄黏土","彩虹黏土","糖果黏土"]
NAMES["ja"] = ["地球","太陽","水星","金星","月","火星","ケレス","サイコロ","ゴルフボール","木星","テニスボール","野球ボール","バスケットボール","ボウリングボール","土星","サッカーボール","ひよこ","くま","天王星","海王星","ぶた","エイトボール","スイカ","エリス","マケマケ","レインボーキューブ","パンダ","かえる","天の川","ねこ","いちごミルク粘土","ミントチョコ粘土","パステル粘土","ぶどう粘土","レインボー粘土","キャンディー粘土"]
NAMES["es"] = ["Tierra","Sol","Mercurio","Venus","Luna","Marte","Ceres","Dado","Pelota de golf","Júpiter","Pelota de tenis","Béisbol","Baloncesto","Bola de bolos","Saturno","Balón de fútbol","Pollito","Osito","Urano","Neptuno","Cerdito","Bola ocho","Sandía","Eris","Makemake","Cubo arcoíris","Panda","Rana","Vía Láctea","Gatito","Masa de fresa con leche","Masa de menta y chocolate","Masa pastel","Masa de uva","Masa arcoíris","Masa de caramelo"]
NAMES["pt"] = ["Terra","Sol","Mercúrio","Vênus","Lua","Marte","Ceres","Dado","Bola de golfe","Júpiter","Bola de tênis","Beisebol","Basquete","Bola de boliche","Saturno","Bola de futebol","Pintinho","Ursinho","Urano","Netuno","Porquinho","Bola oito","Melancia","Éris","Makemake","Cubo arco-íris","Panda","Sapo","Via Láctea","Gatinho","Massa de morango com leite","Massa de menta com chocolate","Massa pastel","Massa de uva","Massa arco-íris","Massa de doce"]
NAMES["de"] = ["Erde","Sonne","Merkur","Venus","Mond","Mars","Ceres","Würfel","Golfball","Jupiter","Tennisball","Baseball","Basketball","Bowlingkugel","Saturn","Fußball","Küken","Bär","Uranus","Neptun","Schwein","Achterball","Wassermelone","Eris","Makemake","Regenbogenwürfel","Panda","Frosch","Milchstraße","Katze","Erdbeermilch-Knetmasse","Minz-Schoko-Knetmasse","Pastell-Knetmasse","Trauben-Knetmasse","Regenbogen-Knetmasse","Bonbon-Knetmasse"]
NAMES["id"] = ["Bumi","Matahari","Merkurius","Venus","Bulan","Mars","Ceres","Dadu","Bola golf","Jupiter","Bola tenis","Bisbol","Basket","Bola boling","Saturnus","Bola sepak","Anak ayam","Beruang","Uranus","Neptunus","Babi","Bola delapan","Semangka","Eris","Makemake","Kubus pelangi","Panda","Katak","Bima Sakti","Kucing","Adonan susu stroberi","Adonan mint cokelat","Adonan pastel","Adonan anggur","Adonan pelangi","Adonan permen"]
NAMES["vi"] = ["Trái Đất","Mặt Trời","Sao Thủy","Sao Kim","Mặt Trăng","Sao Hỏa","Ceres","Xúc xắc","Bóng golf","Sao Mộc","Bóng tennis","Bóng chày","Bóng rổ","Bóng bowling","Sao Thổ","Bóng đá","Gà con","Gấu bông","Sao Thiên Vương","Sao Hải Vương","Lợn con","Bi số 8","Dưa hấu","Eris","Makemake","Khối lập phương cầu vồng","Gấu trúc","Ếch","Dải Ngân Hà","Mèo con","Đất nặn sữa dâu","Đất nặn bạc hà sô-cô-la","Đất nặn pastel","Đất nặn nho","Đất nặn cầu vồng","Đất nặn kẹo"]
NAMES["th"] = ["โลก","ดวงอาทิตย์","ดาวพุธ","ดาวศุกร์","ดวงจันทร์","ดาวอังคาร","เซเรส","ลูกเต๋า","ลูกกอล์ฟ","ดาวพฤหัสบดี","ลูกเทนนิส","ลูกเบสบอล","ลูกบาสเกตบอล","ลูกโบว์ลิ่ง","ดาวเสาร์","ลูกฟุตบอล","ลูกเจี๊ยบ","หมี","ดาวยูเรนัส","ดาวเนปจูน","หมู","ลูกบิลเลียดเลข 8","แตงโม","อีริส","มาคีมาคี","ลูกบาศก์สายรุ้ง","แพนด้า","กบ","ทางช้างเผือก","แมว","แป้งโดว์นมสตรอว์เบอร์รี","แป้งโดว์มินต์ช็อกโกแลต","แป้งโดว์พาสเทล","แป้งโดว์องุ่น","แป้งโดว์สายรุ้ง","แป้งโดว์ลูกอม"]

DESCS = {}
DESCS["ko"] = ["기준이 되는 소리. 마르고 단단한 빠작","가장 밝고 뾰족하게 터진다","작고 두꺼워 묵직한 뽀각","가장 둔하고 답답하게 뭉개진다","작아서 한 음 높다","알갱이가 오래 이어지는 빠자자작","울퉁불퉁해 음높이가 넓게 흩어진다","모서리부터 우수수 떨어진다","얇고 밝게 부서진다","가장 크고 저음이 깊게 깔린다","높고 뾰족한 챙그랑","가죽 아래서 밝게 자잘거린다","커진 만큼 저음이 실린다","두껍고 둔해 오래 버틴다","길쭉해 공명이 더 산다","가장 둔하고 먹먹하다","삐약삐약 작고 높게 부서진다","둥근 머리가 마르게 빠작인다","말랑하게 낮은 뿌직","쫀득하게 촘촘히 이어진다","낮고 둔한 뿌직","얇고 밝게 자잘한 사각사각","얇고 밝게 흩어진다","두껍고 둔하게 눌린다","쫀득하게 촘촘히","가장 밝고 날카롭다","가장 크고 두꺼워 한참 버틴다","자잘하고 밝게 사각사각","얇고 밝게 자잘한 사각사각","쫀득하게 끝없이 이어진다","말랑하게 낮은 뿌직 뿌직","쫀득하고 두꺼워 오래 간다","무르게 뭉개지는 뿌지직","둔하고 먹먹한 뽀갹 뽀갹","밝고 자잘한 사각사각","설탕 유리처럼 쨍하게 부서진다"]
DESCS["en"] = ["The reference sound. Dry, firm, crisp cracks","The brightest, sharpest bursts","Small and thick — a heavy, dull crack","The dullest, most muffled crumble","Small, so it's pitched a bit higher","Grainy crackles that go on and on","Lumpy, so the pitch scatters widely","Crumbles from the corners first","Thin and bright cracks","The biggest, with deep low tones","A high, sharp jingle","Bright, fine crackles under the leather","Deeper tones the bigger it gets","Thick and dull — it lasts a long time","Elongated, so it resonates more","The dullest, most muffled of all","Tiny, high-pitched peeps as it cracks","A round head that cracks dry and firm","A soft, low creak","A chewy, tightly packed crackle","A low, dull creak","Thin, bright, fine crackling","Thin and bright, scattering apart","Thick and dull, presses in","Chewy and tightly packed","The brightest, sharpest of all","The biggest and thickest — lasts forever","Fine, bright crackling","Thin, bright, fine crackling","A chewy crackle that never seems to end","A soft, low creak, creak","Chewy and thick, lasts a long while","A soft, mushy crackle","Dull and muffled crunches","Bright, fine crackling","Sharp, clear cracks like sugar glass"]
DESCS["zh"] = ["基准音色，干燥结实的咔嚓声","最亮最尖锐的爆裂声","又小又厚，沉闷的咔哒声","最迟钝最闷的挤压声","个头小，音调偏高","颗粒感十足，噼里啪啦持续很久","凹凸不平，音高散得很开","从棱角开始簌簌剥落","又薄又脆的碎裂声","个头最大，低音沉厚","高亢清脆的叮当声","皮革下明亮细碎的声音","越大低音越厚","又厚又钝，能撑很久","细长，共鸣更足","最迟钝最闷的一种","叽叽的又小又高的碎裂声","圆脑袋干脆地咔嚓作响","软软的低沉吱吱声","有嚼劲，密集连续","低沉又闷的吱吱声","又薄又亮，细碎沙沙声","又薄又亮，四散开来","又厚又钝，挤压感强","有嚼劲，很密集","最亮最锋利的一种","个头最大最厚，能撑很久","细碎又明亮的沙沙声","又薄又亮，细碎沙沙声","有嚼劲，没完没了","软软的低沉吱吱声","有嚼劲又厚实，能撑很久","软软的挤压碎裂声","迟钝又闷的咔哒声","明亮细碎的沙沙声","像糖玻璃一样清脆碎裂"]
DESCS["ja"] = ["基準となる音。乾いて硬いパキッという音","一番明るく鋭くはじける","小さくて厚みがあり、重いポコッという音","一番鈍くこもった潰れ方","小さいので音が高め","粒感が長く続くパチパチ音","でこぼこで音の高さが幅広く散る","角からポロポロ崩れていく","薄く明るく砕ける","一番大きく低音が深い","高くて鋭いチリンという音","革の下で明るく細かくパチパチ","大きいほど低音が乗る","厚くて鈍く、長持ちする","細長いので響きが増す","一番鈍くこもっている","ピヨピヨと小さく高く砕ける","丸い頭が乾いてパキッと鳴る","柔らかく低いプニッという音","もちもちと密に続く","低く鈍いプニッという音","薄く明るく細かいシャリシャリ","薄く明るく散らばる","厚く鈍く押しつぶされる","もちもちと密","一番明るく鋭い","一番大きく厚く、長く持ちこたえる","細かく明るいシャリシャリ","薄く明るい細かいシャリシャリ","もちもちと終わりなく続く","柔らかく低いプニプニ音","もちもち厚みがあり長持ちする","柔らかくつぶれるプニュッという音","鈍くこもったポコポコ音","明るく細かいシャリシャリ","砂糖ガラスのようにパリッと砕ける"]
DESCS["es"] = ["El sonido de referencia: crujidos secos y firmes","Los estallidos más brillantes y afilados","Pequeña y gruesa, un crujido sordo y pesado","El aplastamiento más apagado y sordo","Pequeña, así que suena algo más aguda","Crujidos granulados que se prolongan","Irregular, así que el tono se dispersa mucho","Se desmorona primero por las esquinas","Crujidos finos y brillantes","La más grande, con graves profundos","Un tintineo agudo y afilado","Crujidos finos y brillantes bajo el cuero","Cuanto más grande, más grave suena","Gruesa y sorda, dura mucho tiempo","Alargada, así que resuena más","La más apagada y sorda de todas","Piídos agudos y pequeños al romperse","Una cabeza redonda que cruje seca y firme","Un crujido suave y grave","Un crujido masticable y muy compacto","Un crujido bajo y sordo","Crujidos finos, brillantes y suaves","Fina y brillante, se dispersa","Gruesa y sorda, aprieta al romperse","Masticable y muy compacto","El más brillante y afilado de todos","La más grande y gruesa, dura para siempre","Crujidos finos y brillantes","Crujidos finos, brillantes y suaves","Un crujido masticable que no termina nunca","Un crujido suave y grave","Masticable y gruesa, dura bastante","Un crujido blando que se aplasta","Crujidos sordos y apagados","Crujidos finos y brillantes","Crujidos nítidos como cristal de azúcar"]
DESCS["pt"] = ["O som de referência: estalos secos e firmes","Os estouros mais brilhantes e afiados","Pequena e grossa, um estalo pesado e surdo","O esmagamento mais surdo e abafado","Pequena, então soa um pouco mais aguda","Estalos granulados que continuam por um bom tempo","Irregular, então o tom se espalha bastante","Esfarela primeiro pelos cantos","Estalos finos e brilhantes","A maior, com graves profundos","Um tilintar agudo e afiado","Estalos finos e brilhantes sob o couro","Quanto maior, mais grave fica o som","Grossa e surda, dura bastante tempo","Alongada, então ressoa mais","A mais surda e abafada de todas","Pios agudos e pequenos ao quebrar","Uma cabeça redonda que estala seca e firme","Um rangido suave e grave","Um estalo mastigável e bem compacto","Um rangido baixo e surdo","Estalos finos, brilhantes e leves","Fina e brilhante, se espalha","Grossa e surda, comprime ao quebrar","Mastigável e bem compacto","O mais brilhante e afiado de todos","A maior e mais grossa, dura para sempre","Estalos finos e brilhantes","Estalos finos, brilhantes e leves","Um estalo mastigável que nunca parece acabar","Um rangido suave e grave","Mastigável e grossa, dura um bom tempo","Um estalo mole e amassado","Estalos surdos e abafados","Estalos finos e brilhantes","Estalos nítidos como vidro de açúcar"]
DESCS["de"] = ["Der Referenzklang: trockene, feste Knackgeräusche","Das hellste, schärfste Zerplatzen","Klein und dick — ein schweres, dumpfes Knacken","Das dumpfeste, gedämpfteste Zerdrücken","Klein, also etwas höher im Ton","Körniges Knistern, das lange anhält","Klumpig, also streut die Tonhöhe stark","Zerbröckelt zuerst an den Ecken","Dünne, helle Risse","Am größten, mit tiefen Bässen","Ein hohes, scharfes Klirren","Helles, feines Knistern unter dem Leder","Je größer, desto tiefer der Ton","Dick und dumpf — hält lange","Länglich, also klingt es mehr nach","Das dumpfeste und gedämpfteste von allen","Kleine, hohe Piep-Knackser","Ein runder Kopf, der trocken und fest knackt","Ein weiches, tiefes Knarren","Ein zähes, dicht gepacktes Knistern","Ein tiefes, dumpfes Knarren","Dünnes, helles, feines Knistern","Dünn und hell, verteilt sich","Dick und dumpf, drückt sich zusammen","Zäh und dicht gepackt","Das hellste und schärfste von allen","Am größten und dicksten — hält ewig","Feines, helles Knistern","Dünnes, helles, feines Knistern","Ein zähes Knistern, das nie aufzuhören scheint","Ein weiches, tiefes Knarren","Zäh und dick, hält lange","Ein weiches, matschiges Knistern","Dumpfes, gedämpftes Knacken","Helles, feines Knistern","Scharfe, klare Risse wie Zuckerglas"]
DESCS["id"] = ["Suara acuan: retakan kering dan tegas","Letupan paling terang dan tajam","Kecil dan tebal — retakan berat dan tumpul","Remukan paling tumpul dan teredam","Kecil, jadi nadanya sedikit lebih tinggi","Retakan berbutir yang berlangsung lama","Bergelombang, jadi nada tersebar luas","Runtuh dari sudut-sudutnya lebih dulu","Retakan tipis dan terang","Paling besar, dengan nada rendah yang dalam","Dentingan tinggi dan tajam","Retakan halus dan terang di bawah kulitnya","Makin besar, nada makin rendah","Tebal dan tumpul — bertahan lama","Memanjang, jadi resonansinya lebih besar","Paling tumpul dan teredam dari semuanya","Bunyi kecil bernada tinggi saat retak","Kepala bundar yang retak kering dan tegas","Derit lembut bernada rendah","Retakan kenyal dan padat","Derit rendah dan tumpul","Retakan tipis, terang, dan halus","Tipis dan terang, tersebar","Tebal dan tumpul, terasa tertekan","Kenyal dan padat","Paling terang dan tajam dari semuanya","Paling besar dan tebal — bertahan lama sekali","Retakan halus dan terang","Retakan tipis, terang, dan halus","Retakan kenyal yang seperti tak berujung","Derit lembut bernada rendah","Kenyal dan tebal, bertahan cukup lama","Retakan lembut dan lembek","Retakan tumpul dan teredam","Retakan halus dan terang","Retakan tajam dan jernih seperti kaca gula"]
DESCS["vi"] = ["Âm thanh chuẩn: tiếng rắc khô và chắc","Tiếng nổ sáng và sắc nhất","Nhỏ và dày — tiếng nứt nặng và trầm đục","Tiếng bóp vỡ trầm đục nhất","Nhỏ nên âm cao hơn một chút","Tiếng lạo xạo hạt kéo dài liên tục","Gồ ghề nên cao độ tỏa rộng","Vỡ vụn từ các góc trước tiên","Tiếng nứt mỏng và sáng","Lớn nhất, với âm trầm sâu","Tiếng leng keng cao và sắc","Tiếng lạo xạo sáng và mịn dưới lớp da","Càng lớn âm trầm càng nặng","Dày và trầm đục — kéo dài lâu","Thuôn dài nên vang hơn","Trầm đục và nghẹt nhất trong tất cả","Tiếng chiêm chiếp nhỏ và cao khi vỡ","Đầu tròn nứt khô và chắc","Tiếng kẽo kẹt mềm và trầm","Tiếng lạo xạo dai và dày đặc","Tiếng kẽo kẹt trầm và đục","Tiếng lạo xạo mỏng, sáng, mịn","Mỏng và sáng, tỏa ra","Dày và trầm đục, bị ép chặt","Dai và dày đặc","Sáng và sắc nhất trong tất cả","Lớn và dày nhất — kéo dài mãi","Tiếng lạo xạo mịn và sáng","Tiếng lạo xạo mỏng, sáng, mịn","Tiếng dai lạo xạo dường như không dứt","Tiếng kẽo kẹt mềm và trầm","Dai và dày, kéo dài khá lâu","Tiếng lạo xạo mềm và nhão","Tiếng lộp bộp trầm và đục","Tiếng lạo xạo sáng và mịn","Tiếng nứt sắc và trong như thủy tinh đường"]
DESCS["th"] = ["เสียงมาตรฐาน แตกกรอบแห้งและหนักแน่น","แตกสว่างและคมที่สุด","เล็กและหนา เสียงป็อกทึบหนัก","บี้ทึบและอู้อี้ที่สุด","เล็กจึงเสียงสูงขึ้นนิดหน่อย","แตกเป็นเม็ดต่อเนื่องยาวนาน","ขรุขระเสียงจึงกระจายกว้าง","ร่วงจากมุมก่อนเป็นเศษ ๆ","แตกบางและสว่าง","ใหญ่ที่สุด เสียงทุ้มลึก","เสียงกรุ๊งกริ๊งสูงและคม","แตกกรอบสว่างละเอียดใต้หนัง","ยิ่งใหญ่ยิ่งมีเสียงทุ้มมาก","หนาและทึบ อยู่ได้นาน","ยาวรีจึงกังวานมากขึ้น","ทึบและอู้อี้ที่สุดในบรรดาทั้งหมด","เสียงจิ๊บ ๆ เล็กและสูงตอนแตก","หัวกลมแตกกรอบแห้งหนักแน่น","เสียงเอี๊ยดอ่อนและทุ้ม","แตกกรอบเหนียวและแน่นถี่","เสียงเอี๊ยดทุ้มและอู้อี้","แตกกรอบบางสว่างละเอียด","บางและสว่าง กระจายออก","หนาและทึบ ถูกบีบอัด","เหนียวและแน่นถี่","สว่างและคมที่สุดในบรรดาทั้งหมด","ใหญ่และหนาที่สุด อยู่ได้นานมาก","แตกกรอบละเอียดและสว่าง","แตกกรอบบางสว่างละเอียด","แตกกรอบเหนียวไม่มีที่สิ้นสุด","เสียงเอี๊ยดอ่อนและทุ้ม","เหนียวและหนา อยู่ได้นานพอควร","แตกกรอบอ่อนนุ่มยวบ","แตกป็อก ๆ ทึบและอู้อี้","แตกกรอบสว่างละเอียด","แตกคมกรอบใสเหมือนแก้วน้ำตาล"]

# 순서: HARD_WAX, GLITTER, SUGAR_GLASS, CHEWY_WAX, THICK_WAX, GLASS_BEAD, CRUNCH_BEADS, SOFT_WAX, SQUISHY_WAX, CLAY_WAX
MATERIALS = {
    "ko": ["굳은 왁스","반짝이 왁스","설탕 유리","쫀득 왁스","두꺼운 왁스","유리알","알갱이 왁스","무른 왁스","말랑 왁스","찰흙 왁스"],
    "en": ["Hard wax","Glitter wax","Sugar glass","Chewy wax","Thick wax","Glass bead","Crunchy beads","Soft wax","Squishy wax","Clay wax"],
    "zh": ["硬蜡","闪粉蜡","糖玻璃","q弹蜡","厚蜡","玻璃珠","颗粒蜡","软蜡","软糯蜡","黏土蜡"],
    "ja": ["硬いワックス","キラキラワックス","シュガーグラス","もちもちワックス","厚いワックス","ガラスビーズ","粒々ワックス","柔らかいワックス","ぷにぷにワックス","粘土ワックス"],
    "es": ["Cera dura","Cera brillante","Cristal de azúcar","Cera masticable","Cera gruesa","Cuenta de vidrio","Cera granulada","Cera blanda","Cera esponjosa","Cera de arcilla"],
    "pt": ["Cera dura","Cera brilhante","Cristal de açúcar","Cera mastigável","Cera grossa","Conta de vidro","Cera granulada","Cera macia","Cera esponjosa","Cera de argila"],
    "de": ["Harter Wachs","Glitzerwachs","Zuckerglas","Zäher Wachs","Dicker Wachs","Glasperle","Körniger Wachs","Weicher Wachs","Weicher Knetwachs","Ton-Wachs"],
    "id": ["Lilin keras","Lilin glitter","Kaca gula","Lilin kenyal","Lilin tebal","Manik kaca","Lilin berbutir","Lilin lembut","Lilin lembek","Lilin tanah liat"],
    "vi": ["Sáp cứng","Sáp lấp lánh","Kính đường","Sáp dai","Sáp dày","Hạt thủy tinh","Sáp hạt","Sáp mềm","Sáp mềm dẻo","Sáp đất sét"],
    "th": ["ขี้ผึ้งแข็ง","ขี้ผึ้งกากเพชร","แก้วน้ำตาล","ขี้ผึ้งเหนียว","ขี้ผึ้งหนา","ลูกปัดแก้ว","ขี้ผึ้งเม็ด","ขี้ผึ้งนุ่ม","ขี้ผึ้งนิ่ม","ขี้ผึ้งดินเหนียว"],
}
# 순서: SPHERE, EGG, FACETED, LUMPY
SHAPES = {
    "ko": ["동그란","달걀형","각진","울퉁불퉁한"], "en": ["Round","Egg-shaped","Faceted","Lumpy"],
    "zh": ["圆形","蛋形","有棱角","凹凸不平"], "ja": ["丸い","卵形","角ばった","でこぼこ"],
    "es": ["Redonda","Ovalada","Facetada","Irregular"], "pt": ["Redonda","Ovalada","Facetada","Irregular"],
    "de": ["Rund","Eiförmig","Facettiert","Klumpig"], "id": ["Bulat","Berbentuk telur","Bersudut","Bergelombang"],
    "vi": ["Tròn","Hình quả trứng","Nhiều cạnh","Gồ ghề"], "th": ["ทรงกลม","ทรงไข่","เหลี่ยม","ขรุขระ"],
}
# 순서: S, M, L, XL
SIZES = {
    "ko": ["작은","보통","큰","왕"], "en": ["Small","Medium","Large","Giant"],
    "zh": ["小","中","大","超大"], "ja": ["小","中","大","特大"],
    "es": ["Pequeña","Mediana","Grande","Gigante"], "pt": ["Pequena","Média","Grande","Gigante"],
    "de": ["Klein","Mittel","Groß","Riesig"], "id": ["Kecil","Sedang","Besar","Raksasa"],
    "vi": ["Nhỏ","Vừa","Lớn","Khổng lồ"], "th": ["เล็ก","กลาง","ใหญ่","ใหญ่มาก"],
}
# 순서: THIN, NORMAL, THICK — "~ 껍질" 형태로 자연스럽게
THICK_SHELL = {
    "ko": ["얇은 껍질","보통 껍질","두꺼운 껍질"], "en": ["Thin shell","Normal shell","Thick shell"],
    "zh": ["薄壳","普通壳","厚壳"], "ja": ["薄い殻","普通の殻","厚い殻"],
    "es": ["Cáscara fina","Cáscara normal","Cáscara gruesa"], "pt": ["Casca fina","Casca normal","Casca grossa"],
    "de": ["Dünne Schale","Normale Schale","Dicke Schale"], "id": ["Kulit tipis","Kulit normal","Kulit tebal"],
    "vi": ["Vỏ mỏng","Vỏ vừa","Vỏ dày"], "th": ["เปลือกบาง","เปลือกปกติ","เปลือกหนา"],
}

LANGS = ["en", "zh", "ja", "es", "pt", "de", "id", "vi", "th"]
FOLDER = {"en": "en", "zh": "zh", "ja": "ja", "es": "es", "pt": "pt", "de": "de",
          "id": "in", "vi": "vi", "th": "th"}  # 인도네시아어는 안드로이드 레거시 폴더명 in

def esc(s: str) -> str:
    return (s.replace("&", "&amp;").replace("'", "\\'").replace('"', '\\"')
             .replace("<", "&lt;").replace(">", "&gt;"))

def write_strings(lang: str, folder: str):
    d = UI[lang]
    lines = ['<?xml version="1.0" encoding="utf-8"?>', "<resources>"]
    for k in UI_KEYS:
        lines.append(f'    <string name="{k}">{esc(d[k])}</string>')
    lines.append("")
    for arr_name, data in (
        ("ball_names", NAMES[lang]), ("ball_sound_descs", DESCS[lang]),
        ("material_labels", MATERIALS[lang]), ("shape_labels", SHAPES[lang]),
        ("size_labels", SIZES[lang]), ("thickness_shell_labels", THICK_SHELL[lang]),
    ):
        lines.append(f'    <string-array name="{arr_name}">')
        for item in data:
            lines.append(f'        <item>{esc(item)}</item>')
        lines.append("    </string-array>")
    lines.append("</resources>")
    out_dir = os.path.join(RES, f"values-{folder}")
    os.makedirs(out_dir, exist_ok=True)
    io.open(os.path.join(out_dir, "strings.xml"), "w", encoding="utf-8", newline="\n").write(
        "\n".join(lines) + "\n")
    print(folder, "ok")

for lang in LANGS:
    write_strings(lang, FOLDER[lang])

# 기본(한국어) 카탈로그 배열도 별도 파일로 만들어 로컬라이제이션 헬퍼가 항상 배열을 찾게 한다.
lines = ['<?xml version="1.0" encoding="utf-8"?>', "<resources>"]
for arr_name, data in (
    ("ball_names", NAMES["ko"]), ("ball_sound_descs", DESCS["ko"]),
    ("material_labels", MATERIALS["ko"]), ("shape_labels", SHAPES["ko"]),
    ("size_labels", SIZES["ko"]), ("thickness_shell_labels", THICK_SHELL["ko"]),
):
    lines.append(f'    <string-array name="{arr_name}">')
    for item in data:
        lines.append(f'        <item>{esc(item)}</item>')
    lines.append("    </string-array>")
lines.append("</resources>")
io.open(os.path.join(RES, "values", "ball_arrays.xml"), "w", encoding="utf-8", newline="\n").write(
    "\n".join(lines) + "\n")
print("ko arrays ok")

# 검증: 배열 길이 확인
assert all(len(NAMES[l]) == 36 for l in ["ko"] + LANGS), "ball_names 개수 불일치"
assert all(len(DESCS[l]) == 36 for l in ["ko"] + LANGS), "ball_sound_descs 개수 불일치"
assert all(len(MATERIALS[l]) == 10 for l in ["ko"] + LANGS), "material_labels 개수 불일치"
assert all(len(SHAPES[l]) == 4 for l in ["ko"] + LANGS), "shape_labels 개수 불일치"
assert all(len(SIZES[l]) == 4 for l in ["ko"] + LANGS), "size_labels 개수 불일치"
assert all(len(THICK_SHELL[l]) == 3 for l in ["ko"] + LANGS), "thickness_shell_labels 개수 불일치"
assert all(set(UI[l].keys()) == set(UI_KEYS) for l in ["ko"] + LANGS), "UI 키 불일치"
print("validation ok — all counts match")
