# -*- coding: utf-8 -*-
"""프랑스어·러시아어 리소스 생성. 기존 언어와 같은 키·배열 길이를 강제한다."""
import io, os, xml.etree.ElementTree as ET

RES = r"C:\workAndroid\WaxBall\app\src\main\res"

UI = {}
UI["fr"] = {
    "app_name": "WaxBall ASMR", "back": "Retour", "ar_count": "%d boules",
    "ar_no_camera": "Caméra indisponible, impossible d'ouvrir le mode paume",
    "ar_permission_needed": "L'accès à la caméra est nécessaire pour poser la boule sur ta paume",
    "ar_refresh": "Nouvelle boule", "ar_show_palm": "Montre ta paume ouverte à la caméra",
    "ar_squeeze": "Serre la main pour l'écraser", "cancel": "Annuler",
    "home_balls": "Choisir une boule", "home_settings": "Réglages", "home_start": "Commencer",
    "home_tagline": "Pose-la sur ta paume et serre",
    "settings_haptics": "Vibration", "settings_quality": "Qualité",
    "settings_quality_auto": "Auto", "settings_quality_high": "Élevée",
    "settings_quality_low": "Basse", "settings_quality_medium": "Moyenne",
    "settings_reset": "Effacer toutes les données",
    "settings_reset_confirm": "Cela réinitialise tes réglages et la dernière boule choisie. C'est irréversible.",
    "settings_reset_done": "Données effacées.", "settings_title": "Réglages",
    "settings_volume": "Volume", "language_picker_cd": "Choisir la langue",
    "language_picker_title": "Choisir la langue", "language_follow_system": "Suivre la langue de l'appareil",
}
UI["ru"] = {
    "app_name": "WaxBall ASMR", "back": "Назад", "ar_count": "Шариков: %d",
    "ar_no_camera": "Камера недоступна, режим ладони не открыть",
    "ar_permission_needed": "Нужен доступ к камере, чтобы положить шарик на ладонь",
    "ar_refresh": "Новый шарик", "ar_show_palm": "Покажите раскрытую ладонь камере",
    "ar_squeeze": "Сожмите руку, чтобы раздавить", "cancel": "Отмена",
    "home_balls": "Выбрать шарик", "home_settings": "Настройки", "home_start": "Начать",
    "home_tagline": "Положите на ладонь и сожмите",
    "settings_haptics": "Вибрация", "settings_quality": "Качество",
    "settings_quality_auto": "Авто", "settings_quality_high": "Высокое",
    "settings_quality_low": "Низкое", "settings_quality_medium": "Среднее",
    "settings_reset": "Стереть все данные",
    "settings_reset_confirm": "Настройки и последний выбранный шарик будут сброшены. Отменить нельзя.",
    "settings_reset_done": "Данные стёрты.", "settings_title": "Настройки",
    "settings_volume": "Громкость", "language_picker_cd": "Выбрать язык",
    "language_picker_title": "Выбрать язык", "language_follow_system": "Язык устройства",
}

NAMES = {}
NAMES["fr"] = ["Terre","Soleil","Mercure","Vénus","Lune","Mars","Cérès","Dé","Balle de golf","Jupiter","Balle de tennis","Balle de baseball","Ballon de basket","Boule de bowling","Saturne","Ballon de foot","Poussin","Ourson","Uranus","Neptune","Cochon","Boule numéro 8","Pastèque","Éris","Makémaké","Cube arc-en-ciel","Panda","Grenouille","Voie lactée","Chat","Pâte lait-fraise","Pâte menthe-chocolat","Pâte pastel","Pâte raisin","Pâte arc-en-ciel","Pâte bonbon"]
NAMES["ru"] = ["Земля","Солнце","Меркурий","Венера","Луна","Марс","Церера","Кубик","Мяч для гольфа","Юпитер","Теннисный мяч","Бейсбольный мяч","Баскетбольный мяч","Шар для боулинга","Сатурн","Футбольный мяч","Цыплёнок","Мишка","Уран","Нептун","Поросёнок","Шар номер 8","Арбуз","Эрида","Макемаке","Радужный кубик","Панда","Лягушка","Млечный Путь","Котик","Клубнично-молочная масса","Мятно-шоколадная масса","Пастельная масса","Виноградная масса","Радужная масса","Конфетная масса"]

DESCS = {}
DESCS["fr"] = ["Le son de référence. Des craquements secs et fermes","Les éclats les plus vifs et les plus nets","Petite et épaisse, un craquement lourd et sourd","L'écrasement le plus sourd et le plus étouffé","Petite, donc le son est un peu plus aigu","Des craquements granuleux qui n'en finissent pas","Irrégulière, la hauteur du son se disperse largement","S'effrite d'abord par les coins","Des craquements fins et clairs","La plus grande, avec des graves profonds","Un tintement aigu et net","Des craquements fins et clairs sous le cuir","Plus elle est grande, plus le son est grave","Épaisse et sourde, elle tient longtemps","Allongée, elle résonne davantage","La plus sourde et la plus étouffée de toutes","De petits couinements aigus quand elle craque","Une tête ronde qui craque sec et ferme","Un grincement doux et grave","Un craquement élastique et bien serré","Un grincement grave et sourd","Un craquement fin, clair et léger","Fine et claire, elle se disperse","Épaisse et sourde, elle se comprime","Élastique et bien serrée","La plus vive et la plus nette de toutes","La plus grande et la plus épaisse, elle tient une éternité","Un craquement fin et clair","Un craquement fin, clair et léger","Un craquement élastique qui ne s'arrête jamais","Un grincement doux et grave","Élastique et épaisse, elle tient un bon moment","Un craquement mou et pâteux","Des craquements sourds et étouffés","Un craquement fin et clair","Des craquements nets comme du sucre vitrifié"]
DESCS["ru"] = ["Эталонный звук. Сухой, твёрдый, чёткий треск","Самые яркие и резкие хлопки","Маленький и толстый — тяжёлый глухой треск","Самое глухое и приглушённое сминание","Маленький, поэтому звук чуть выше","Зернистый треск, который всё длится и длится","Бугристый, поэтому高 звук разлетается широко","Осыпается сначала с углов","Тонкий и звонкий треск","Самый большой, с глубокими низами","Высокий и резкий звон","Яркий мелкий треск под кожей","Чем больше, тем ниже звук","Толстый и глухой — держится долго","Вытянутый, поэтому звучит гулче","Самый глухой и приглушённый из всех","Тонкий высокий писк при трещинах","Круглая голова трещит сухо и твёрдо","Мягкий низкий скрип","Тягучий, плотно набитый треск","Низкий глухой скрип","Тонкий, звонкий, мелкий треск","Тонкий и звонкий, разлетается в стороны","Толстый и глухой, сминается","Тягучий и плотно набитый","Самый яркий и резкий из всех","Самый большой и толстый — держится вечно","Мелкий яркий треск","Тонкий, звонкий, мелкий треск","Тягучий треск, которому нет конца","Мягкий низкий скрип","Тягучая и толстая, держится долго","Мягкий вязкий треск","Глухие приглушённые хрусты","Яркий мелкий треск","Резкий чистый треск, как сахарное стекло"]

MATERIALS = {
    "fr": ["Cire dure","Cire pailletée","Sucre vitrifié","Cire élastique","Cire épaisse","Perle de verre","Cire granuleuse","Cire molle","Cire moelleuse","Cire argileuse"],
    "ru": ["Твёрдый воск","Блёстки в воске","Сахарное стекло","Тягучий воск","Толстый воск","Стеклянная бусина","Зернистый воск","Мягкий воск","Пластичный воск","Глиняный воск"],
}
SHAPES = {
    "fr": ["Ronde","Ovale","Facettée","Bosselée"],
    "ru": ["Круглый","Яйцевидный","Гранёный","Бугристый"],
}
SIZES = {
    "fr": ["Petite","Moyenne","Grande","Géante"],
    "ru": ["Маленький","Средний","Большой","Огромный"],
}
THICK_SHELL = {
    "fr": ["Coque fine","Coque normale","Coque épaisse"],
    "ru": ["Тонкая оболочка","Обычная оболочка","Толстая оболочка"],
}

def esc(s):
    return (s.replace("&", "&amp;").replace("'", "\\'").replace('"', '\\"')
             .replace("<", "&lt;").replace(">", "&gt;"))

# 기존 언어에서 키 목록과 배열 길이를 읽어 그대로 강제한다.
ref = ET.parse(os.path.join(RES, "values", "strings.xml")).getroot()
REF_KEYS = [s.get("name") for s in ref.findall("string")]
REF_ARRAYS = {a.get("name"): len(a.findall("item")) for a in ref.findall("string-array")}

for lang in ("fr", "ru"):
    assert set(UI[lang]) == set(REF_KEYS), (lang, set(REF_KEYS) ^ set(UI[lang]))
    data = {
        "ball_names": NAMES[lang], "ball_sound_descs": DESCS[lang],
        "material_labels": MATERIALS[lang], "shape_labels": SHAPES[lang],
        "size_labels": SIZES[lang], "thickness_shell_labels": THICK_SHELL[lang],
    }
    for k, n in REF_ARRAYS.items():
        assert len(data[k]) == n, (lang, k, len(data[k]), n)

    lines = ['<?xml version="1.0" encoding="utf-8"?>', "<resources>"]
    for k in REF_KEYS:
        lines.append('    <string name="%s">%s</string>' % (k, esc(UI[lang][k])))
    lines.append("")
    for k in REF_ARRAYS:
        lines.append('    <string-array name="%s">' % k)
        for item in data[k]:
            lines.append("        <item>%s</item>" % esc(item))
        lines.append("    </string-array>")
    lines.append("</resources>")

    out_dir = os.path.join(RES, "values-" + lang)
    os.makedirs(out_dir, exist_ok=True)
    io.open(os.path.join(out_dir, "strings.xml"), "w", encoding="utf-8", newline="\n").write(
        "\n".join(lines) + "\n")
    print(lang, "written:", len(REF_KEYS), "strings,", REF_ARRAYS)
