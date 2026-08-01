# -*- coding: utf-8 -*-
"""캐릭터 볼 6종 추가분: 토끼·강아지·펭귄·호랑이·코알라·오리.

gen_ball_textures.py 와 같은 규격 — 등장방형 1024x512, 정면은 u=0.75.
"""
import numpy as np
from PIL import Image, ImageDraw
import os

W, H = 1024, 512
OUT = r"C:\workAndroid\WaxBall\app\src\main\assets\planets"
FRONT_U = 0.75

u = (np.arange(W) + 0.5) / W
v = (np.arange(H) + 0.5) / H
uu, vv = np.meshgrid(u, v)
lon = (uu - 0.5) * 2 * np.pi
lat = (0.5 - vv) * np.pi
Y = np.sin(lat)
X = np.cos(lat) * np.cos(lon)
Z = np.cos(lat) * np.sin(lon)

rng = np.random.default_rng(7)


def col(hexv):
    return np.array([(hexv >> 16) & 255, (hexv >> 8) & 255, hexv & 255], np.float32)


def base(hexv, mottle=True):
    img = np.zeros((H, W, 3), np.float32)
    img[:] = col(hexv)
    if mottle:
        g = rng.random((14, 28))
        m = np.asarray(Image.fromarray((g * 255).astype(np.uint8))
                       .resize((W, H), Image.BILINEAR), np.float32) / 255
        img *= (0.95 + 0.05 * m)[..., None]
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8))


def save(name, im, noise=2.5):
    arr = np.asarray(im, np.float32) + rng.normal(0, noise, (H, W, 3))
    Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8)).convert("RGB").save(
        os.path.join(OUT, name), quality=88, subsampling=0)
    print(name, os.path.getsize(os.path.join(OUT, name)) // 1024, "KB")


CXF, CYF = FRONT_U * W, 0.5 * H
R = W / 8            # 앞반구 45도 폭. 얼굴 요소의 척도.


def E(d, cx, cy, rx, ry, fill):
    d.ellipse([cx - rx, cy - ry, cx + rx, cy + ry], fill=fill)


# ---------- 토끼: 흰 몸, 위로 선 긴 귀, 분홍 귀속 ----------
im = base(0xF7F3EE)
d = ImageDraw.Draw(im)
for sx in (-1, 1):
    E(d, CXF + sx * 0.52 * R, CYF - 1.55 * R, 0.30 * R, 0.95 * R, (216, 206, 200))
    E(d, CXF + sx * 0.52 * R, CYF - 1.50 * R, 0.16 * R, 0.72 * R, (242, 178, 192))
E(d, CXF - 0.55 * R, CYF - 0.20 * R, 0.12 * R, 0.16 * R, (56, 46, 48))       # 눈
E(d, CXF + 0.55 * R, CYF - 0.20 * R, 0.12 * R, 0.16 * R, (56, 46, 48))
d.polygon([(CXF - 0.13 * R, CYF + 0.14 * R), (CXF + 0.13 * R, CYF + 0.14 * R),
           (CXF, CYF + 0.30 * R)], fill=(240, 150, 165))                     # 코
d.arc([CXF - 0.28 * R, CYF + 0.22 * R, CXF, CYF + 0.52 * R], 20, 160,
      fill=(120, 105, 100), width=int(0.045 * R))
d.arc([CXF, CYF + 0.22 * R, CXF + 0.28 * R, CYF + 0.52 * R], 20, 160,
      fill=(120, 105, 100), width=int(0.045 * R))
E(d, CXF - 1.0 * R, CYF + 0.25 * R, 0.20 * R, 0.13 * R, (250, 200, 210))     # 볼터치
E(d, CXF + 1.0 * R, CYF + 0.25 * R, 0.20 * R, 0.13 * R, (250, 200, 210))
save("ball_rabbit.jpg", im)

# ---------- 강아지: 갈색 늘어진 귀, 주둥이, 코 ----------
im = base(0xE8C89A)
d = ImageDraw.Draw(im)
for sx in (-1, 1):
    E(d, CXF + sx * 1.15 * R, CYF - 0.45 * R, 0.34 * R, 0.78 * R, (122, 82, 48))  # 늘어진 귀
E(d, CXF, CYF + 0.35 * R, 0.60 * R, 0.45 * R, (250, 240, 225))               # 주둥이
E(d, CXF - 0.52 * R, CYF - 0.32 * R, 0.12 * R, 0.16 * R, (50, 40, 34))       # 눈
E(d, CXF + 0.52 * R, CYF - 0.32 * R, 0.12 * R, 0.16 * R, (50, 40, 34))
E(d, CXF, CYF + 0.12 * R, 0.17 * R, 0.13 * R, (60, 44, 36))                  # 코
d.arc([CXF - 0.28 * R, CYF + 0.24 * R, CXF, CYF + 0.56 * R], 20, 160,
      fill=(90, 66, 50), width=int(0.05 * R))
d.arc([CXF, CYF + 0.24 * R, CXF + 0.28 * R, CYF + 0.56 * R], 20, 160,
      fill=(90, 66, 50), width=int(0.05 * R))
# 이마 얼룩
E(d, CXF + 0.75 * R, CYF - 0.85 * R, 0.34 * R, 0.30 * R, (198, 152, 104))
save("ball_dog.jpg", im)

# ---------- 펭귄: 검은 머리, 흰 얼굴판, 주황 부리 ----------
im = base(0x2A2E38, mottle=False)
d = ImageDraw.Draw(im)
E(d, CXF, CYF + 0.28 * R, 1.05 * R, 1.05 * R, (245, 247, 250))               # 흰 배·얼굴
E(d, CXF - 0.48 * R, CYF - 0.35 * R, 0.30 * R, 0.34 * R, (245, 247, 250))    # 눈 주변 흰판
E(d, CXF + 0.48 * R, CYF - 0.35 * R, 0.30 * R, 0.34 * R, (245, 247, 250))
E(d, CXF - 0.45 * R, CYF - 0.32 * R, 0.11 * R, 0.15 * R, (24, 26, 32))       # 눈
E(d, CXF + 0.45 * R, CYF - 0.32 * R, 0.11 * R, 0.15 * R, (24, 26, 32))
d.polygon([(CXF - 0.22 * R, CYF - 0.02 * R), (CXF + 0.22 * R, CYF - 0.02 * R),
           (CXF, CYF + 0.30 * R)], fill=(240, 150, 60))                      # 부리
save("ball_penguin.jpg", im)

# ---------- 호랑이: 주황, 검은 줄무늬, 흰 주둥이 ----------
im = base(0xE8963C)
d = ImageDraw.Draw(im)
# 줄무늬: 위와 양옆에서 안쪽으로
for k, (x0, y0, x1, y1) in enumerate([
        (-0.15, -1.55, -0.25, -0.95), (0.15, -1.55, 0.25, -0.95), (0.0, -1.62, 0.0, -1.05),
        (-1.45, -0.45, -0.95, -0.30), (-1.50, 0.05, -0.98, 0.10),
        (1.45, -0.45, 0.95, -0.30), (1.50, 0.05, 0.98, 0.10)]):
    d.line([(CXF + x0 * R, CYF + y0 * R), (CXF + x1 * R, CYF + y1 * R)],
           fill=(40, 32, 26), width=int(0.11 * R))
E(d, CXF, CYF + 0.38 * R, 0.55 * R, 0.42 * R, (250, 244, 234))               # 주둥이
E(d, CXF - 0.50 * R, CYF - 0.30 * R, 0.12 * R, 0.16 * R, (36, 30, 26))       # 눈
E(d, CXF + 0.50 * R, CYF - 0.30 * R, 0.12 * R, 0.16 * R, (36, 30, 26))
d.polygon([(CXF - 0.13 * R, CYF + 0.14 * R), (CXF + 0.13 * R, CYF + 0.14 * R),
           (CXF, CYF + 0.30 * R)], fill=(70, 50, 42))                        # 코
d.arc([CXF - 0.26 * R, CYF + 0.26 * R, CXF, CYF + 0.56 * R], 20, 160,
      fill=(70, 50, 42), width=int(0.045 * R))
d.arc([CXF, CYF + 0.26 * R, CXF + 0.26 * R, CYF + 0.56 * R], 20, 160,
      fill=(70, 50, 42), width=int(0.045 * R))
save("ball_tiger.jpg", im)

# ---------- 코알라: 회색, 큰 둥근 귀, 큰 검은 코 ----------
im = base(0xB9B4B8)
d = ImageDraw.Draw(im)
for sx in (-1, 1):
    E(d, CXF + sx * 1.15 * R, CYF - 0.85 * R, 0.52 * R, 0.50 * R, (150, 143, 148))  # 귀
    E(d, CXF + sx * 1.15 * R, CYF - 0.85 * R, 0.30 * R, 0.28 * R, (232, 190, 200))  # 귀속
E(d, CXF - 0.50 * R, CYF - 0.28 * R, 0.11 * R, 0.15 * R, (44, 38, 42))       # 눈
E(d, CXF + 0.50 * R, CYF - 0.28 * R, 0.11 * R, 0.15 * R, (44, 38, 42))
E(d, CXF, CYF + 0.18 * R, 0.26 * R, 0.34 * R, (52, 46, 50))                  # 큰 코
save("ball_koala.jpg", im)

# ---------- 오리: 흰 몸, 넓적한 주황 부리, 머리깃 ----------
im = base(0xFAF6EC)
d = ImageDraw.Draw(im)
E(d, CXF - 0.55 * R, CYF - 0.30 * R, 0.13 * R, 0.17 * R, (46, 40, 40))       # 눈
E(d, CXF + 0.55 * R, CYF - 0.30 * R, 0.13 * R, 0.17 * R, (46, 40, 40))
E(d, CXF, CYF + 0.22 * R, 0.52 * R, 0.26 * R, (245, 160, 60))                # 넓적 부리
E(d, CXF, CYF + 0.12 * R, 0.52 * R, 0.10 * R, (230, 140, 48))                # 부리 윗선
for k in (-1, 0, 1):                                                          # 머리깃
    d.line([(CXF + k * 0.12 * R, CYF - 1.30 * R), (CXF + k * 0.30 * R, CYF - 1.62 * R)],
           fill=(226, 206, 160), width=int(0.06 * R))
E(d, CXF - 1.02 * R, CYF + 0.28 * R, 0.20 * R, 0.13 * R, (252, 208, 190))    # 볼터치
E(d, CXF + 1.02 * R, CYF + 0.28 * R, 0.20 * R, 0.13 * R, (252, 208, 190))
save("ball_duck.jpg", im)
