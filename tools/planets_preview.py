"""
앱의 행성 셰이더를 PC에서 그대로 재현해 30종 미리보기를 만든다.

폰에서는 손을 비춰야 볼이 떠서 스크린샷으로 검수할 수가 없다.
GLSL의 hash/noise/fbm/planet()을 numpy로 옮겨 같은 그림을 얻는다.
풍선·깨짐은 빼고 표면만 본다.
"""
import io
import re
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFont

CATALOG = r"C:\workAndroid\WaxBall\app\src\main\java\com\waxball\asmr\core\BallCatalog.kt"
SURFACE_CODE = dict(PLAIN=0, BANDED=1, SWIRL=2, CRATER=3, ICY=4, LAVA=5, FLARE=6, SPECKLE=7, TERRA=8)
TIME = 12.0


def parse_catalog():
    text = io.open(CATALOG, encoding="utf-8").read()
    balls = []
    for chunk in text.split("BallSpec(")[1:]:
        name = re.search(r'"([^"]+)"', chunk).group(1)
        colors = re.findall(r"0x([0-9A-Fa-f]{8})\.toInt\(\)", chunk)
        surface = re.search(r"SurfaceKind\.(\w+)", chunk)
        if len(colors) < 4 or surface is None:
            continue
        shell = tuple(int(colors[0][i:i + 2], 16) / 255.0 for i in (2, 4, 6))
        accent = tuple(int(colors[3][i:i + 2], 16) / 255.0 for i in (2, 4, 6))
        balls.append((name, shell, accent, SURFACE_CODE[surface.group(1)]))
    return balls


# --- GLSL 이식 ---

def hashv(p):
    return np.modf(np.sin(p[0] * 127.1 + p[1] * 311.7 + p[2] * 74.7) * 43758.5453)[0] % 1.0


def noise(p):
    i = np.floor(p)
    f = p - i
    f = f * f * (3.0 - 2.0 * f)

    def corner(dx, dy, dz):
        return hashv((i[0] + dx, i[1] + dy, i[2] + dz))

    n000, n100 = corner(0, 0, 0), corner(1, 0, 0)
    n010, n110 = corner(0, 1, 0), corner(1, 1, 0)
    n001, n101 = corner(0, 0, 1), corner(1, 0, 1)
    n011, n111 = corner(0, 1, 1), corner(1, 1, 1)
    x00 = n000 + (n100 - n000) * f[0]
    x10 = n010 + (n110 - n010) * f[0]
    x01 = n001 + (n101 - n001) * f[0]
    x11 = n011 + (n111 - n011) * f[0]
    y0 = x00 + (x10 - x00) * f[1]
    y1 = x01 + (x11 - x01) * f[1]
    return y0 + (y1 - y0) * f[2]


def fbm(p):
    return (0.5 * noise(p) + 0.25 * noise((p[0] * 2.03, p[1] * 2.03, p[2] * 2.03))
            + 0.125 * noise((p[0] * 4.01, p[1] * 4.01, p[2] * 4.01))
            + 0.0625 * noise((p[0] * 8.07, p[1] * 8.07, p[2] * 8.07)))


def smoothstep(a, b, x):
    t = np.clip((x - a) / (b - a), 0.0, 1.0)
    return t * t * (3.0 - 2.0 * t)


def vec3(x):
    return np.stack([np.full_like(x[0] if isinstance(x, tuple) else x, v) for v in x]) \
        if isinstance(x, tuple) else x


def planet(nx, ny, nz, shell, accent, surface, l, v):
    """셰이더 planet()과 같은 계산. (H,W,3) 색과 emissive, specMask를 낸다."""
    shellc = np.array(shell)[None, None, :]
    accentc = np.array(accent)[None, None, :]
    dark = shellc * 0.55
    shape = nx.shape
    emissive = np.zeros(shape + (3,))
    spec_mask = np.ones(shape)

    def mix(a, b, t):
        return a + (b - a) * t[..., None]

    n = (nx, ny, nz)

    if surface == 1:
        q = (nx * 3, ny * 3, nz * 3)
        inner = fbm((q[0] * 1.6, q[1] * 1.6, q[2] * 1.6)) * 1.4
        warp = fbm((q[0], q[1] + inner, q[2])) - 0.5
        t = np.sin(ny * 9.5 + warp * 2.8) * 0.5 + 0.5
        col = mix(dark + np.zeros(shape + (3,)), shellc + np.zeros(shape + (3,)), smoothstep(0.12, 0.5, t))
        col = mix(col, accentc + np.zeros(shape + (3,)), smoothstep(0.58, 0.94, t))
        storm = smoothstep(0.34, 0.08, np.hypot(np.arctan2(nz, nx) - 1.2, (ny + 0.3) * 2.6))
        stormc = (accentc + np.array([0.72, 0.28, 0.16])[None, None, :]) * 0.5
        col = mix(col, stormc + np.zeros(shape + (3,)), storm * 0.75)
        spec_mask[:] = 0.25
        return col, emissive, spec_mask
    if surface == 2:
        q = (nx * 2.5, ny * 2.5, nz * 2.5)
        f1 = fbm((q[0] + 1.7, q[1] + 9.2, q[2] + 4.1))
        f2 = fbm((q[0] + 8.3, q[1] + 2.8, q[2] + 5.9))
        m = fbm((q[0] + 2.1 * f1, q[1] + 2.1 * f2, q[2] + 2.1 * f1))
        col = mix(shellc + np.zeros(shape + (3,)), accentc + np.zeros(shape + (3,)), smoothstep(0.3, 0.72, m))
        spec_mask[:] = 0.3
        return col * (0.82 + 0.36 * f2)[..., None], emissive, spec_mask
    if surface == 3:
        # 워리 잡음 분화구 — 셰이더와 같은 계산
        px, py, pz = nx * 3.6, ny * 3.6, nz * 3.6
        ipx, ipy, ipz = np.floor(px), np.floor(py), np.floor(pz)
        fpx, fpy, fpz = px - ipx, py - ipy, pz - ipz
        d = np.full(shape, 8.0)
        tox = np.zeros(shape); toy = np.zeros(shape); toz = np.zeros(shape)
        for xo in (-1, 0, 1):
            for yo in (-1, 0, 1):
                for zo in (-1, 0, 1):
                    gx, gy, gz = ipx + xo, ipy + yo, ipz + zo
                    cx = xo + hashv((gx, gy, gz)) - fpx
                    cy = yo + hashv((gx + 17.1, gy + 17.1, gz + 17.1)) - fpy
                    cz = zo + hashv((gx + 31.7, gy + 31.7, gz + 31.7)) - fpz
                    ln = np.sqrt(cx * cx + cy * cy + cz * cz)
                    closer = ln < d
                    d = np.where(closer, ln, d)
                    tox = np.where(closer, cx, tox)
                    toy = np.where(closer, cy, toy)
                    toz = np.where(closer, cz, toz)
        bowl = 1.0 - smoothstep(0.12, 0.42, d)
        rim_ring = smoothstep(0.56, 0.42, d) - smoothstep(0.42, 0.30, d)
        tlen = np.sqrt(tox**2 + toy**2 + toz**2) + 1e-4
        lit = 0.5 + 0.5 * (tox * l[0] + toy * l[1] + toz * l[2]) / tlen
        col = mix(shellc + np.zeros(shape + (3,)), accentc + np.zeros(shape + (3,)), bowl * 0.6)
        col = col * (1.0 + (mix_s := (0.58 + (1.12 - 0.58) * lit) - 1.0) * bowl)[..., None]
        col = col * (1.0 + rim_ring * 0.22)[..., None]
        col = col * (0.93 + 0.14 * noise((nx * 15, ny * 15, nz * 15)))[..., None]
        spec_mask[:] = 0.12
        return col, emissive, spec_mask
    if surface == 4:
        c = np.abs(fbm((nx * 4.5, ny * 4.5, nz * 4.5)) - 0.5)
        crack = smoothstep(0.06, 0.0, c)
        col = shellc * (0.9 + 0.25 * fbm((nx * 2.2, ny * 2.2, nz * 2.2)))[..., None]
        spec_mask[:] = 1.8
        return mix(col, accentc + np.zeros(shape + (3,)), crack * 0.85), emissive, spec_mask
    if surface == 5:
        crust = fbm((nx * 3.2 + TIME * 0.015, ny * 3.2, nz * 3.2))
        glow = smoothstep(0.56, 0.40, crust)
        emissive = accentc * glow[..., None] * 1.5
        spec_mask[:] = 0.0
        return mix(dark + np.zeros(shape + (3,)), shellc + np.zeros(shape + (3,)), smoothstep(0.35, 0.75, crust)), emissive, spec_mask
    if surface == 6:
        g = fbm((nx * 6, ny * 6 + TIME * 0.05, nz * 6)) + 0.5 * fbm((nx * 13 - TIME * 0.03, ny * 13 - TIME * 0.03, nz * 13 - TIME * 0.03))
        col = mix(shellc + np.zeros(shape + (3,)), accentc + np.zeros(shape + (3,)), smoothstep(0.4, 1.1, g))
        ndv = np.clip(nx * v[0] + ny * v[1] + nz * v[2], 0, None)
        limb = 0.45 + 0.55 * ndv
        emissive = col * ((0.6 + 0.55 * g) * limb)[..., None]
        spec_mask[:] = 0.0
        return col * 0.2, emissive, spec_mask
    if surface == 7:
        g = fbm((nx * 8, ny * 8, nz * 8))
        col = mix(shellc + np.zeros(shape + (3,)), accentc + np.zeros(shape + (3,)), smoothstep(0.52, 0.75, g))
        col = mix(col, accentc + np.zeros(shape + (3,)), smoothstep(0.74, 0.9, noise((nx * 21, ny * 21, nz * 21))) * 0.5)
        return col * (0.88 + 0.24 * g)[..., None], emissive, spec_mask
    # TERRA
    cont = fbm((nx * 2.6 + 4.2, ny * 2.6 + 1.3, nz * 2.6 + 7.8))
    land = smoothstep(0.5, 0.535, cont)
    landc = mix(accentc + np.zeros(shape + (3,)),
                np.array([0.72, 0.62, 0.42])[None, None, :] + np.zeros(shape + (3,)),
                smoothstep(0.6, 0.78, cont))
    col = mix(shellc + np.zeros(shape + (3,)), landc, land)
    cap = smoothstep(0.7, 0.84, np.abs(ny) + (fbm((nx * 5, ny * 5, nz * 5)) - 0.5) * 0.14)
    col = mix(col, np.array([0.92, 0.95, 1.0])[None, None, :] + np.zeros(shape + (3,)), cap)
    clouds = smoothstep(0.55, 0.78, fbm((nx * 3.8 + TIME * 0.008, ny * 3.8, nz * 3.8)))
    spec_mask = (1.0 - land) * (1.0 - clouds) * 1.6
    return mix(col, np.full(shape + (3,), 0.98), clouds * 0.8), emissive, spec_mask


def render_ball(name, shell, accent, surface, size=240):
    y, x = np.mgrid[0:size, 0:size]
    nx = (x + 0.5) / size * 2 - 1
    ny = -((y + 0.5) / size * 2 - 1)   # 화면은 아래가 +, 공간은 위가 +
    r2 = nx * nx + ny * ny
    inside = r2 <= 1.0
    nz = np.sqrt(np.clip(1.0 - r2, 0, 1))

    l = np.array([0.45, 0.8, 0.6])
    l = l / np.linalg.norm(l)
    v = np.array([0.0, 0.0, 1.0])

    base, emissive, spec_mask = planet(nx, ny, nz, shell, accent, surface, l, v)

    ndl = nx * l[0] + ny * l[1] + nz * l[2]
    wrap = ndl * 0.5 + 0.5
    ndv = np.clip(nz, 0, 1)
    rim = (1.0 - ndv) ** 3
    h = l + v
    h = h / np.linalg.norm(h)
    spec = np.clip(nx * h[0] + ny * h[1] + nz * h[2], 0, 1) ** 48

    atmo = np.array(shell) * 0.65 + 0.35
    col = (base * (0.24 + 0.88 * wrap * wrap)[..., None]
           + atmo[None, None, :] * (rim * 0.3)[..., None]
           + (spec * 0.3 * spec_mask)[..., None]
           + emissive)
    col = np.clip(col, 0, 1)

    img = np.zeros((size, size, 4))
    img[..., :3] = col
    img[..., 3] = inside.astype(float)
    # 가장자리 계단 줄이기
    edge = np.clip((1.0 - np.sqrt(r2)) * size * 0.5, 0, 1)
    img[..., 3] *= edge
    return (img * 255).astype(np.uint8)


def main():
    out_path = sys.argv[1]
    balls = parse_catalog()
    cell = 240
    pad = 14
    label_h = 44
    cols = 6
    rows = (len(balls) + cols - 1) // cols
    W = cols * (cell + pad) + pad
    H = rows * (cell + label_h + pad) + pad

    sheet = Image.new("RGB", (W, H), (8, 9, 14))
    draw = ImageDraw.Draw(sheet)
    try:
        font = ImageFont.truetype(r"C:\Windows\Fonts\malgun.ttf", 21)
        small = ImageFont.truetype(r"C:\Windows\Fonts\malgun.ttf", 15)
    except OSError:
        font = small = ImageFont.load_default()

    surface_names = {v: k for k, v in SURFACE_CODE.items()}
    for idx, (name, shell, accent, surface) in enumerate(balls):
        cx = pad + (idx % cols) * (cell + pad)
        cy = pad + (idx // cols) * (cell + label_h + pad)
        tile = Image.fromarray(render_ball(name, shell, accent, surface), "RGBA")
        sheet.paste(tile, (cx, cy), tile)
        draw.text((cx + 4, cy + cell + 2), name, font=font, fill=(235, 235, 240))
        draw.text((cx + 4, cy + cell + 26), surface_names[surface], font=small, fill=(140, 145, 160))

    sheet.save(out_path)
    print("balls", len(balls))


main()
