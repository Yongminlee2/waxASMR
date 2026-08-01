# -*- coding: utf-8 -*-
"""이미 만든 스크린샷의 문구만 다른 언어로 바꾼다.

원본 사진이 없어도 된다. 위쪽 문구 자리는 배경 그라데이션뿐이라 그 띠만
같은 색으로 다시 칠하고 글자를 새로 얹으면, 아래 화면 부분은 손대지 않아
화질이 그대로다.

콘솔은 언어마다 스크린샷을 따로 받지만 안 넣으면 기본 언어 것을 쓴다.
12개 언어를 다 만들면 72장을 손으로 올려야 해서 한국어·영어 두 벌만 둔다.
"""
import os
import numpy as np
from PIL import Image, ImageDraw, ImageFont

BASE = r"C:\workAndroid\WaxBall\docs\store-assets\phone"
FONT_BD = r"C:\Windows\Fonts\malgunbd.ttf"
FONT_RG = r"C:\Windows\Fonts\malgun.ttf"

W, H = 1080, 2160
BAND = 326          # 이 아래부터는 화면(그림자 포함)이라 건드리지 않는다
INK = (74, 50, 56)
SUB = (150, 122, 132)

NAMES = ["01-palm", "02-squeeze", "03-dough", "04-home", "05-guide", "06-shape"]

CAPTIONS = {
    "en": [
        ("Rest it on your palm", "and squeeze"),
        ("It breaks as hard as you squeeze", "Real recorded sounds"),
        ("The best part comes after", "Keep kneading, colours blend"),
        ("42 balls", "From planets to characters"),
        ("Match the dotted outline", "and it lands on your hand"),
        ("It follows your hand", "The ball squashes with you"),
    ],
}


def gradient_band():
    """만들 때 쓴 것과 같은 그라데이션의 위쪽 띠."""
    stops = [(0.0, (255, 249, 240)), (0.45, (251, 231, 238)), (1.0, (231, 239, 251))]
    col = np.zeros((H, 3), np.float32)
    for i in range(len(stops) - 1):
        (p0, c0), (p1, c1) = stops[i], stops[i + 1]
        y0, y1 = int(p0 * H), int(p1 * H)
        t = np.linspace(0, 1, y1 - y0)[:, None]
        col[y0:y1] = np.array(c0) * (1 - t) + np.array(c1) * t
    full = np.repeat(col[:, None, :], W, 1).astype(np.uint8)
    return Image.fromarray(full[:BAND])


def fit(draw, text, path, start, max_w):
    size = start
    while size > 30:
        f = ImageFont.truetype(path, size)
        if draw.textlength(text, font=f) <= max_w:
            return f
        size -= 3
    return ImageFont.truetype(path, size)


def main():
    band = gradient_band()
    for lang, lines in CAPTIONS.items():
        out = os.path.join(BASE, lang)
        os.makedirs(out, exist_ok=True)
        for name, (l1, l2) in zip(NAMES, lines):
            im = Image.open(os.path.join(BASE, name + ".png")).convert("RGB")
            im.paste(band, (0, 0))
            d = ImageDraw.Draw(im)
            d.text((W // 2, 150), l1, font=fit(d, l1, FONT_BD, 76, W - 130),
                   fill=INK, anchor="mt")
            d.text((W // 2, 258), l2, font=fit(d, l2, FONT_RG, 52, W - 150),
                   fill=SUB, anchor="mt")
            im.save(os.path.join(out, name + ".png"))
            print(f"  {lang}/{name:12s} {l1} / {l2}")


if __name__ == "__main__":
    main()
