# -*- coding: utf-8 -*-
"""왁뿌볼 장난감·캐릭터 볼 16종의 등장방형(equirect) 텍스처 생성.

셰이더 UV: u = atan2(z,x)/(2pi)+0.5,  v = 0.5 - asin(y)/pi
카메라가 보는 정면은 +Z → u=0.625, v=0.5 가 얼굴 중심.
"""
import numpy as np
from PIL import Image, ImageDraw
import os

W, H = 1024, 512
OUT = r"C:\workAndroid\WaxBall\app\src\main\assets\planets"
FRONT_U = 0.75  # +Z (atan2(1,0)/(2pi)+0.5)

u = (np.arange(W) + 0.5) / W
v = (np.arange(H) + 0.5) / H
uu, vv = np.meshgrid(u, v)
lon = (uu - 0.5) * 2 * np.pi
lat = (0.5 - vv) * np.pi
Y = np.sin(lat)
CL = np.cos(lat)
X = CL * np.cos(lon)
Z = CL * np.sin(lon)

rng = np.random.default_rng(7)

def col(hexv):
    return np.array([(hexv >> 16) & 255, (hexv >> 8) & 255, hexv & 255], dtype=np.float32)

def base(hexv):
    img = np.zeros((H, W, 3), dtype=np.float32)
    img[:] = col(hexv)
    return img

def paint(img, mask, hexv, soft=None):
    c = col(hexv)
    m = mask.astype(np.float32) if soft is None else np.clip(soft, 0, 1)
    img[:] = img * (1 - m[..., None]) + c * m[..., None]

def save(name, img, noise=3.0):
    out = img + rng.normal(0, noise, img.shape)
    Image.fromarray(np.clip(out, 0, 255).astype(np.uint8)).save(os.path.join(OUT, name))
    print(name, os.path.getsize(os.path.join(OUT, name)) // 1024, "KB")

def ang_to(ax):
    ax = np.asarray(ax, dtype=np.float64)
    ax = ax / np.linalg.norm(ax)
    d = X * ax[0] + Y * ax[1] + Z * ax[2]
    return np.arccos(np.clip(d, -1, 1))

def value_noise(scale, octaves=4):
    acc = np.zeros((H, W))
    amp, tot = 1.0, 0.0
    for o in range(octaves):
        gw, gh = scale * (2 ** o), scale * (2 ** o) // 2 + 2
        g = rng.random((gh, gw))
        acc += amp * np.array(Image.fromarray((g * 255).astype(np.uint8)).resize((W, H), Image.BILINEAR)) / 255.0
        tot += amp
        amp *= 0.5
    return acc / tot

# 얼굴 그리기용: 정면 중심 픽셀 좌표. 반지름 단위 R = 90도 경도의 1/4 화면폭.
CXF, CYF = FRONT_U * W, 0.5 * H

def face_canvas(img):
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8))

def E(d, cx, cy, rx, ry, fill, outline=None, width=0):
    d.ellipse([cx - rx, cy - ry, cx + rx, cy + ry], fill=fill,
              outline=outline, width=width)

# ---------- 1. 농구공 ----------
img = base(0xD9772F)
shade = 1 - 0.10 * np.abs(Y)          # 위아래로 살짝 어둡게
img *= shade[..., None]
lines = (np.abs(Y) < 0.017)           # 적도
lines |= (np.abs(X) < 0.017)          # 정면을 지나는 세로 대원
for ax in ((1, 0, 0), (-1, 0, 0)):
    lines |= np.abs(ang_to(ax) - np.deg2rad(55)) < 0.016   # 양옆 곡선 이음매
paint(img, lines, 0x2A2320)
save("ball_basketball.png", img)

# ---------- 2. 축구공 ----------
phi = (1 + 5 ** 0.5) / 2
ico = []
for a in (-1, 1):
    for b in (-phi, phi):
        ico += [(0, a, b), (a, b, 0), (b, 0, a)]
img = base(0xF2F2F0)
dmin = np.full((H, W), 9.0)
d2 = np.full((H, W), 9.0)
for p in ico:
    a = ang_to(p)
    nearer = a < dmin
    d2 = np.where(nearer, dmin, np.minimum(d2, a))
    dmin = np.where(nearer, a, dmin)
paint(img, dmin < 0.30, 0x1E1E22)
paint(img, np.abs(d2 - dmin) < 0.045, 0xB8B8B4)   # 패널 이음매
img *= (1 - 0.06 * np.abs(Y))[..., None]
save("ball_soccer.png", img)

# ---------- 3. 야구공 ----------
img = base(0xF4EFE6)
f = Y - 1.4 * X * Z                    # 안장 곡선 = 야구공 이음매
for off in (0.10, -0.10):
    paint(img, np.abs(f - off) < 0.035, 0xC23A32)
paint(img, np.abs(f) < 0.012, 0xD8CFC0)  # 두 줄 사이 가죽 골
save("ball_baseball.png", img)

# ---------- 4. 테니스공 ----------
img = base(0xCFE24A)
fuzz = value_noise(64, 3)
img *= (0.93 + 0.07 * fuzz)[..., None]
f = Y - 1.4 * X * Z
paint(img, np.abs(f) < 0.10, 0xF4F6E8)
paint(img, np.abs(np.abs(f) - 0.10) < 0.014, 0xA8BC2E)
save("ball_tennis.png", img)

# ---------- 5. 골프공 ----------
img = base(0xF2F4F0)
n = 380
idx = np.arange(n) + 0.5
ga_lat = np.arcsin(1 - 2 * idx / n)
ga_lon = np.pi * (1 + 5 ** 0.5) * idx
px = np.cos(ga_lat) * np.cos(ga_lon)
py = np.sin(ga_lat)
pz = np.cos(ga_lat) * np.sin(ga_lon)
best = np.full((H, W), 9.0, dtype=np.float32)
for i in range(0, n, 40):     # 40개씩 나눠 메모리를 아낀다
    dots = (X[..., None] * px[i:i+40] + Y[..., None] * py[i:i+40] + Z[..., None] * pz[i:i+40])
    best = np.minimum(best, np.arccos(np.clip(dots.max(axis=-1), -1, 1)))
dimple = np.clip(1 - best / 0.055, 0, 1)
img *= (1 - 0.16 * np.sin(dimple * np.pi))[..., None]
save("ball_golf.png", img)

# ---------- 6. 볼링공 ----------
img = base(0x241A46)
marble = value_noise(12, 5)
swirl = value_noise(6, 3)
m1 = np.clip((marble - 0.45) * 4, 0, 1) * 0.8
m2 = np.clip((swirl - 0.55) * 5, 0, 1) * 0.6
paint(img, None, 0x6A4FC8, soft=m1)
paint(img, None, 0x2E9AA8, soft=m2 * (1 - m1))
for ax in ((0.16, 0.38, 0.91), (-0.06, 0.44, 0.89), (0.05, 0.24, 0.97)):
    hole = np.clip(1 - ang_to(ax) / 0.062, 0, 1)
    paint(img, None, 0x0A0714, soft=np.clip(hole * 2.2, 0, 1))
save("ball_bowling.png", img)

# ---------- 7. 수박 ----------
img = base(0x3E9A4E)
wob = value_noise(24, 3)
stripe = np.sin(lon * 4.5 + (wob - 0.5) * 3.0 + np.sin(lat * 3) * 0.6)
paint(img, None, 0x1E5A28, soft=np.clip((stripe - 0.15) * 3.5, 0, 1))
img *= (0.9 + 0.1 * value_noise(48, 2))[..., None]
save("ball_watermelon.png", img)

# ---------- 8. 주사위 ----------
img = base(0xF5F2EA)
ax_ = np.abs(X); ay_ = np.abs(Y); az_ = np.abs(Z)
dom = np.argmax(np.stack([ax_, ay_, az_]), axis=0)   # 0=X,1=Y,2=Z
signs = np.stack([np.sign(X), np.sign(Y), np.sign(Z)])
# 면 로컬 좌표 (-1..1)
eps = 1e-6
la = np.where(dom == 0, Z / (ax_ + eps), np.where(dom == 1, X / (ay_ + eps), X / (az_ + eps)))
lb = np.where(dom == 0, Y / (ax_ + eps), np.where(dom == 1, Z / (ay_ + eps), Y / (az_ + eps)))
PIPS = {  # (축, 부호) -> 눈 위치 목록
    (2, 1): [(0, 0)],                                                     # 앞 = 1 (빨강)
    (2, -1): [(-.55, -.55), (-.55, 0), (-.55, .55), (.55, -.55), (.55, 0), (.55, .55)],  # 뒤 = 6
    (0, 1): [(-.55, -.55), (0, 0), (.55, .55)],                           # 오른쪽 = 3
    (0, -1): [(-.55, -.55), (-.55, .55), (.55, -.55), (.55, .55)],        # 왼쪽 = 4
    (1, 1): [(-.5, -.5), (.5, .5)],                                       # 위 = 2
    (1, -1): [(-.55, -.55), (.55, .55), (-.55, .55), (.55, -.55), (0, 0)],  # 아래 = 5
}
for (axis, sg), pips in PIPS.items():
    on_face = (dom == axis) & (signs[axis] == sg)
    color = 0xD03A2A if len(pips) == 1 else 0x20242C
    r = 0.30 if len(pips) == 1 else 0.155
    for (pa, pb) in pips:
        m = on_face & ((la - pa) ** 2 + (lb - pb) ** 2 < r ** 2)
        paint(img, m, color)
edge = np.clip((np.maximum(np.abs(la), np.abs(lb)) - 0.82) / 0.18, 0, 1)
img *= (1 - 0.22 * edge)[..., None]
save("ball_dice.png", img)

# ---------- 9. 알록 큐브 ----------
img = base(0xF0F0EC)
FACES = {(2, 1): 0xD84040, (2, -1): 0xE8842A, (0, 1): 0x3A6AD8,
         (0, -1): 0x3AA850, (1, 1): 0xF0C030, (1, -1): 0xF0F0EC}
for (axis, sg), c in FACES.items():
    paint(img, (dom == axis) & (signs[axis] == sg), c)
grid = (np.abs(np.abs(la) - 1 / 3) < 0.035) | (np.abs(np.abs(lb) - 1 / 3) < 0.035)
border = np.maximum(np.abs(la), np.abs(lb)) > 0.94
paint(img, grid | border, 0x2A2A30)
save("ball_cube.png", img)

# ---------- 10. 당구 8번공 ----------
img = base(0x1C1C22)
gloss = np.clip((Y * 0.5 + Z * 0.6), 0, 1) ** 2
img += (gloss * 38)[..., None]
circle = ang_to((0, 0, 1)) < 0.52
paint(img, circle, 0xF2F2EE)
# 숫자 8: 위아래 고리 두 개
r_small, r_big = 0.155, 0.185
tha = ang_to((0, 0, 1))
loc_a = (uu - FRONT_U) * 4 * np.pi / np.pi   # 경도 방향 로컬(라디안 비율)
loc_b = (0.5 - vv) * 2                        # 위가 +
la8 = (uu - FRONT_U) * (2 * np.pi)
lb8 = (0.5 - vv) * np.pi
for (cy, r) in ((0.145, r_small), (-0.175, r_big)):
    ring = np.abs(np.sqrt(la8 ** 2 + (lb8 - cy) ** 2) - r) < 0.052
    paint(img, ring & circle, 0x1C1C22)
save("ball_eight.png", img)

# ---------- 얼굴 6종: PIL 드로잉 ----------
def start_face(hexv, mottle=True):
    img = base(hexv)
    if mottle:
        img *= (0.95 + 0.05 * value_noise(32, 2))[..., None]
    return face_canvas(img)

def finish(name, pil_img, noise=2.5):
    save(name, np.asarray(pil_img, dtype=np.float32), noise)

R = W / 4 / 2  # 앞 반구(90도)의 절반 = 45도 ≈ 128px. 얼굴 요소 척도.

# 병아리
im = start_face(0xFFD84A)
d = ImageDraw.Draw(im)
E(d, CXF - 0.62 * R, CYF - 0.30 * R, 0.16 * R, 0.20 * R, (35, 30, 28))       # 눈
E(d, CXF + 0.62 * R, CYF - 0.30 * R, 0.16 * R, 0.20 * R, (35, 30, 28))
E(d, CXF - 0.67 * R, CYF - 0.37 * R, 0.05 * R, 0.06 * R, (255, 255, 255))    # 눈빛
E(d, CXF + 0.57 * R, CYF - 0.37 * R, 0.05 * R, 0.06 * R, (255, 255, 255))
d.polygon([(CXF - 0.28 * R, CYF + 0.10 * R), (CXF + 0.28 * R, CYF + 0.10 * R),
           (CXF, CYF + 0.46 * R)], fill=(240, 138, 40))                       # 부리
E(d, CXF - 1.05 * R, CYF + 0.28 * R, 0.22 * R, 0.15 * R, (250, 168, 120))    # 볼터치
E(d, CXF + 1.05 * R, CYF + 0.28 * R, 0.22 * R, 0.15 * R, (250, 168, 120))
for k in (-1, 0, 1):                                                          # 머리 깃
    d.line([(CXF + k * 0.12 * R, CYF - 1.28 * R), (CXF + k * 0.3 * R, CYF - 1.62 * R)],
           fill=(200, 138, 26), width=int(0.06 * R))
finish("ball_chick.png", im)

# 곰돌이
im = start_face(0xA8703E)
d = ImageDraw.Draw(im)
E(d, CXF - 1.15 * R, CYF - 1.15 * R, 0.42 * R, 0.42 * R, (82, 52, 24))       # 귀
E(d, CXF + 1.15 * R, CYF - 1.15 * R, 0.42 * R, 0.42 * R, (82, 52, 24))
E(d, CXF - 1.15 * R, CYF - 1.15 * R, 0.22 * R, 0.22 * R, (232, 200, 154))
E(d, CXF + 1.15 * R, CYF - 1.15 * R, 0.22 * R, 0.22 * R, (232, 200, 154))
E(d, CXF, CYF + 0.42 * R, 0.62 * R, 0.48 * R, (232, 200, 154))               # 주둥이
E(d, CXF, CYF + 0.18 * R, 0.20 * R, 0.15 * R, (60, 38, 20))                  # 코
d.arc([CXF - 0.3 * R, CYF + 0.34 * R, CXF, CYF + 0.66 * R], 20, 160,
      fill=(60, 38, 20), width=int(0.05 * R))                                 # 입
d.arc([CXF, CYF + 0.34 * R, CXF + 0.3 * R, CYF + 0.66 * R], 20, 160,
      fill=(60, 38, 20), width=int(0.05 * R))
E(d, CXF - 0.55 * R, CYF - 0.25 * R, 0.13 * R, 0.16 * R, (40, 28, 18))       # 눈
E(d, CXF + 0.55 * R, CYF - 0.25 * R, 0.13 * R, 0.16 * R, (40, 28, 18))
finish("ball_bear.png", im)

# 돼지
im = start_face(0xF2A8B8)
d = ImageDraw.Draw(im)
d.polygon([(CXF - 1.35 * R, CYF - 1.0 * R), (CXF - 0.75 * R, CYF - 1.15 * R),
           (CXF - 1.2 * R, CYF - 1.6 * R)], fill=(200, 90, 120))              # 귀
d.polygon([(CXF + 1.35 * R, CYF - 1.0 * R), (CXF + 0.75 * R, CYF - 1.15 * R),
           (CXF + 1.2 * R, CYF - 1.6 * R)], fill=(200, 90, 120))
E(d, CXF, CYF + 0.25 * R, 0.52 * R, 0.38 * R, (200, 90, 120))                # 코
E(d, CXF - 0.2 * R, CYF + 0.25 * R, 0.09 * R, 0.15 * R, (120, 44, 66))
E(d, CXF + 0.2 * R, CYF + 0.25 * R, 0.09 * R, 0.15 * R, (120, 44, 66))
E(d, CXF - 0.62 * R, CYF - 0.35 * R, 0.12 * R, 0.15 * R, (45, 32, 34))       # 눈
E(d, CXF + 0.62 * R, CYF - 0.35 * R, 0.12 * R, 0.15 * R, (45, 32, 34))
finish("ball_pig.png", im)

# 판다
im = start_face(0xF4F2EE)
d = ImageDraw.Draw(im)
E(d, CXF - 1.2 * R, CYF - 1.2 * R, 0.45 * R, 0.42 * R, (35, 35, 42))         # 귀
E(d, CXF + 1.2 * R, CYF - 1.2 * R, 0.45 * R, 0.42 * R, (35, 35, 42))
eye = Image.new("RGBA", (int(0.9 * R), int(1.1 * R)), (0, 0, 0, 0))
de = ImageDraw.Draw(eye)
de.ellipse([0, 0, eye.width - 1, eye.height - 1], fill=(35, 35, 42))
for sx, rot in ((-1, 25), (1, -25)):
    patch = eye.rotate(rot, expand=True)
    im.paste(patch, (int(CXF + sx * 0.62 * R - patch.width / 2),
                     int(CYF - 0.35 * R - patch.height / 2)), patch)
d = ImageDraw.Draw(im)
E(d, CXF - 0.58 * R, CYF - 0.38 * R, 0.11 * R, 0.14 * R, (250, 250, 250))    # 눈동자
E(d, CXF + 0.58 * R, CYF - 0.38 * R, 0.11 * R, 0.14 * R, (250, 250, 250))
E(d, CXF - 0.58 * R, CYF - 0.36 * R, 0.055 * R, 0.07 * R, (20, 20, 24))
E(d, CXF + 0.58 * R, CYF - 0.36 * R, 0.055 * R, 0.07 * R, (20, 20, 24))
E(d, CXF, CYF + 0.28 * R, 0.16 * R, 0.12 * R, (35, 35, 42))                  # 코
d.arc([CXF - 0.26 * R, CYF + 0.36 * R, CXF + 0.26 * R, CYF + 0.72 * R], 30, 150,
      fill=(35, 35, 42), width=int(0.05 * R))
finish("ball_panda.png", im)

# 개구리
im = start_face(0x6FBE4A)
d = ImageDraw.Draw(im)
E(d, CXF - 0.62 * R, CYF - 1.05 * R, 0.42 * R, 0.42 * R, (242, 248, 232))    # 눈 흰자
E(d, CXF + 0.62 * R, CYF - 1.05 * R, 0.42 * R, 0.42 * R, (242, 248, 232))
E(d, CXF - 0.62 * R, CYF - 1.0 * R, 0.18 * R, 0.22 * R, (30, 40, 26))
E(d, CXF + 0.62 * R, CYF - 1.0 * R, 0.18 * R, 0.22 * R, (30, 40, 26))
d.arc([CXF - 0.85 * R, CYF - 0.15 * R, CXF + 0.85 * R, CYF + 0.75 * R], 15, 165,
      fill=(30, 60, 26), width=int(0.07 * R))                                 # 입
E(d, CXF - 1.1 * R, CYF + 0.45 * R, 0.2 * R, 0.13 * R, (168, 224, 122))      # 볼
E(d, CXF + 1.1 * R, CYF + 0.45 * R, 0.2 * R, 0.13 * R, (168, 224, 122))
finish("ball_frog.png", im)

# 고양이
im = start_face(0xF2E8DC)
d = ImageDraw.Draw(im)
d.polygon([(CXF - 1.4 * R, CYF - 0.85 * R), (CXF - 0.7 * R, CYF - 1.1 * R),
           (CXF - 1.25 * R, CYF - 1.7 * R)], fill=(138, 136, 148))            # 귀
d.polygon([(CXF + 1.4 * R, CYF - 0.85 * R), (CXF + 0.7 * R, CYF - 1.1 * R),
           (CXF + 1.25 * R, CYF - 1.7 * R)], fill=(138, 136, 148))
d.polygon([(CXF - 1.25 * R, CYF - 0.95 * R), (CXF - 0.85 * R, CYF - 1.08 * R),
           (CXF - 1.15 * R, CYF - 1.45 * R)], fill=(232, 138, 160))
d.polygon([(CXF + 1.25 * R, CYF - 0.95 * R), (CXF + 0.85 * R, CYF - 1.08 * R),
           (CXF + 1.15 * R, CYF - 1.45 * R)], fill=(232, 138, 160))
E(d, CXF - 0.55 * R, CYF - 0.3 * R, 0.13 * R, 0.17 * R, (52, 46, 46))        # 눈
E(d, CXF + 0.55 * R, CYF - 0.3 * R, 0.13 * R, 0.17 * R, (52, 46, 46))
d.polygon([(CXF - 0.14 * R, CYF + 0.12 * R), (CXF + 0.14 * R, CYF + 0.12 * R),
           (CXF, CYF + 0.3 * R)], fill=(232, 138, 160))                       # 코
d.arc([CXF - 0.3 * R, CYF + 0.2 * R, CXF, CYF + 0.5 * R], 20, 160,
      fill=(90, 82, 80), width=int(0.045 * R))
d.arc([CXF, CYF + 0.2 * R, CXF + 0.3 * R, CYF + 0.5 * R], 20, 160,
      fill=(90, 82, 80), width=int(0.045 * R))
for sy in (-0.05, 0.15, 0.35):                                                # 수염
    d.line([(CXF - 1.55 * R, CYF + sy * R), (CXF - 0.95 * R, CYF + (sy + 0.05) * R)],
           fill=(120, 112, 108), width=int(0.035 * R))
    d.line([(CXF + 0.95 * R, CYF + (sy + 0.05) * R), (CXF + 1.55 * R, CYF + sy * R)],
           fill=(120, 112, 108), width=int(0.035 * R))
finish("ball_cat.png", im)

print("done")
