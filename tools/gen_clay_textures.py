# -*- coding: utf-8 -*-
"""2·3·4색 파스텔 마블 반죽 텍스처 6장.

다이소 파스텔 왁뿌볼 사진처럼, 큰 색 덩어리들이 부드러운 경계로 맞닿아 있는 모양.
방향 벡터마다 "어느 색 덩어리에 가장 가까운가"를 잡음 섞인 내적으로 정하고
경계를 블러로 눅여서 손으로 뭉친 반죽처럼 만든다.
"""
import numpy as np
from PIL import Image, ImageFilter
import os

W, H = 1024, 512
OUT = r"C:\workAndroid\WaxBall\app\src\main\assets\planets"

u = (np.arange(W) + 0.5) / W
v = (np.arange(H) + 0.5) / H
uu, vv = np.meshgrid(u, v)
lon = (uu - 0.5) * 2 * np.pi
lat = (0.5 - vv) * np.pi
Y = np.sin(lat)
CL = np.cos(lat)
X = CL * np.cos(lon)
Z = CL * np.sin(lon)

def value_noise(rng, scale, octaves=4):
    acc = np.zeros((H, W))
    amp, tot = 1.0, 0.0
    for o in range(octaves):
        gw, gh = scale * (2 ** o), max(2, scale * (2 ** o) // 2)
        g = rng.random((gh, gw))
        acc += amp * np.array(Image.fromarray((g * 255).astype(np.uint8)).resize((W, H), Image.BILINEAR)) / 255.0
        tot += amp
        amp *= 0.5
    return acc / tot

def col(hexv):
    return np.array([(hexv >> 16) & 255, (hexv >> 8) & 255, hexv & 255], dtype=np.float32)

def marble(name, hexes, seed):
    rng = np.random.default_rng(seed)
    n = len(hexes)
    # 색 덩어리 축: 서로 적당히 벌어진 무작위 방향
    axes = rng.normal(size=(n, 3))
    axes /= np.linalg.norm(axes, axis=1, keepdims=True)
    fields = []
    for i in range(n):
        noise = value_noise(rng, 6, 4)
        f = X * axes[i, 0] + Y * axes[i, 1] + Z * axes[i, 2] + (noise - 0.5) * 1.5
        fields.append(f)
    pick = np.argmax(np.stack(fields), axis=0)

    img = np.zeros((H, W, 3), dtype=np.float32)
    for i, hx in enumerate(hexes):
        img[pick == i] = col(hx)

    # 경계를 눅인다. 극 근처는 등장방형에서 가로로 늘어나므로 두 번 살짝.
    pil = Image.fromarray(img.astype(np.uint8))
    pil = pil.filter(ImageFilter.GaussianBlur(7))
    img = np.asarray(pil, dtype=np.float32)

    # 반죽 결: 은은한 얼룩과 아주 약한 결 노이즈
    mottle = value_noise(np.random.default_rng(seed + 99), 32, 3)
    img *= (0.94 + 0.09 * mottle)[..., None]
    img += np.random.default_rng(seed + 7).normal(0, 2.5, img.shape)

    Image.fromarray(np.clip(img, 0, 255).astype(np.uint8)).convert("RGB").save(
        os.path.join(OUT, name), quality=88, subsampling=0)
    print(name, os.path.getsize(os.path.join(OUT, name)) // 1024, "KB")

# 2색
marble("ball_clay_strawberry.jpg", [0xF6B8C8, 0xFDF3EE], 11)          # 딸기우유
marble("ball_clay_mintchoco.jpg", [0xA8E0C8, 0x6B4A36], 22)           # 민트초코
# 3색 (사진의 파스텔 하늘·노랑·분홍)
marble("ball_clay_pastel.jpg", [0xAECBF2, 0xF6EBA8, 0xF2B8C4], 33)
marble("ball_clay_grape.jpg", [0x9C7CC8, 0xC8B4E4, 0xF2EAF6], 44)     # 포도
# 4색
marble("ball_clay_rainbow.jpg", [0xF2B8C4, 0xF6EBA8, 0xA8E0C8, 0xAECBF2], 55)
marble("ball_clay_candy.jpg", [0xE86A6A, 0xFDF3EE, 0xF0A048, 0x78C86A], 66)
