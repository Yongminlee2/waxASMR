# -*- coding: utf-8 -*-
"""폰 스크린샷을 스토어용으로 다듬는다.

실제 앱 화면을 그대로 쓰되(없는 장면을 지어내면 심사에서 걸린다), 파스텔 배경과
문구가 있는 틀 안에 앉힌다. 화면이 작아지면서 배경에 찍힌 방바닥·이불 같은 것이
덜 도드라지고, 무엇을 하는 앱인지가 글로 먼저 읽힌다.

원본 1080x2400은 화면비 2.22:1 이라 구글 상한 2:1 을 넘는다. 상태바(위 78px)와
내비바(아래 130px)를 걷어내면 통신사·시계·배터리도 함께 사라지고 규격도 맞는다.
"""
import os, glob
import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

SRC = r"C:\workAndroid\WaxBall\docs\store-assets\phone\drive-download-20260801T122430Z-1-001"
OUT = r"C:\workAndroid\WaxBall\docs\store-assets\phone"
FONT_BD = r"C:\Windows\Fonts\malgunbd.ttf"
FONT_RG = r"C:\Windows\Fonts\malgun.ttf"

W, H = 1080, 2160                 # 정확히 2:1
CROP_TOP, CROP_BOTTOM = 110, 2270  # 상태바·내비바를 걷어낸 자리

INK = (74, 50, 56)
SUB = (150, 122, 132)

# (원본 번호, 파일 이름, 첫 줄, 둘째 줄)
PICK = [
    (0, "01-palm",    "손바닥에 올려놓고", "쥐어서 부수세요"),
    (1, "02-squeeze", "쥔 만큼 부서집니다", "직접 녹음한 진짜 소리"),
    (3, "03-dough",   "다 부순 뒤가 진짜", "주무를수록 색이 섞여요"),
    (6, "04-home",    "왁뿌볼 42종", "행성부터 캐릭터까지"),
    (7, "05-guide",   "점선에 손을 맞추면", "바로 올라옵니다"),
    (5, "06-shape",   "손 모양 그대로", "볼도 함께 눌립니다"),
]


def gradient():
    """앱 첫 화면과 같은 크림-분홍-하늘 파스텔."""
    stops = [(0.0, (255, 249, 240)), (0.45, (251, 231, 238)), (1.0, (231, 239, 251))]
    col = np.zeros((H, 3), np.float32)
    for i in range(len(stops) - 1):
        (p0, c0), (p1, c1) = stops[i], stops[i + 1]
        y0, y1 = int(p0 * H), int(p1 * H)
        t = np.linspace(0, 1, y1 - y0)[:, None]
        col[y0:y1] = np.array(c0) * (1 - t) + np.array(c1) * t
    return Image.fromarray(np.repeat(col[:, None, :], W, 1).astype(np.uint8))


def rounded_shadow(img, radius, blur, offset, alpha):
    """둥근 모서리로 자르고 그림자를 깐 이미지를 (그림, 그림자) 로 돌려준다."""
    m = Image.new("L", img.size, 0)
    ImageDraw.Draw(m).rounded_rectangle([0, 0, img.size[0] - 1, img.size[1] - 1],
                                        radius=radius, fill=255)
    sh = Image.new("L", (img.size[0] + blur * 4, img.size[1] + blur * 4), 0)
    sh.paste(m, (blur * 2, blur * 2 + offset))
    sh = sh.filter(ImageFilter.GaussianBlur(blur)).point(lambda v: int(v * alpha))
    return img, m, sh


def main():
    files = sorted(f for f in glob.glob(os.path.join(SRC, "*"))
                   if f.lower().endswith((".jpg", ".jpeg", ".png")))
    big = ImageFont.truetype(FONT_BD, 76)
    small = ImageFont.truetype(FONT_RG, 52)
    bg0 = gradient()

    for idx, name, line1, line2 in PICK:
        shot = Image.open(files[idx]).convert("RGB").crop(
            (0, CROP_TOP, 1080, CROP_BOTTOM))

        canvas = bg0.copy()
        d = ImageDraw.Draw(canvas)

        # 문구
        d.text((W // 2, 150), line1, font=big, fill=INK, anchor="mt")
        d.text((W // 2, 258), line2, font=small, fill=SUB, anchor="mt")

        # 화면: 폭 78%로 줄여 앉힌다
        sw = int(W * 0.78)
        sh_h = int(shot.height * sw / shot.width)
        small_shot = shot.resize((sw, sh_h), Image.LANCZOS)
        img, mask, shadow = rounded_shadow(small_shot, 44, 26, 16, 0.30)

        x = (W - sw) // 2
        y = 380
        canvas.paste((120, 92, 104), (x - 52, y - 52), shadow)
        canvas.paste(img, (x, y), mask)

        canvas.save(os.path.join(OUT, name + ".png"))
        print(f"  {name:12s} {canvas.size}  {line1} / {line2}")


if __name__ == "__main__":
    main()
