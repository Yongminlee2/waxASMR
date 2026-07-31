"""
재질별로 "쥐는 동작 한 번" 크기의 덩어리를 뽑는다.

지금 앱의 파편은 0.17초짜리 파열 낱개다. 그래서 이어지는 느낌이 없다.
다른 앱을 재 보니 소리 한 단위가 0.86초였다 — 쥐어서 쭉 찢어지는 동작 전체다.
그 길이로 자른다.

자르는 기준은 무음이 아니라 **에너지가 낮아지는 골짜기**다. 쥐는 동작 사이에는
완전한 무음이 없고 잔소리가 깔린다. 골짜기에서 끊으면 앞뒤가 뭉개지지 않는다.
"""
import io
import json
import os
import subprocess
import sys

import numpy as np

FFMPEG = r"C:\ffmpeg\bin\ffmpeg.exe"
RATE = 48000
HIGHPASS = 150

TARGET_MIN = 0.40   # 덩어리 최소 길이(초)
TARGET_MAX = 0.95   # 최대
FADE_MS = 8.0       # 이어 붙일 때 딸깍거리지 않게
PER_MATERIAL = 18   # 재질당 이만큼만 남긴다. 용량 때문


def decode(path, start, duration):
    cmd = [FFMPEG, "-v", "error", "-ss", "%.3f" % start, "-i", path, "-t", "%.3f" % duration,
           "-af", "highpass=f=%d:poles=2,highpass=f=%d:poles=2" % (HIGHPASS, HIGHPASS),
           "-f", "s16le", "-acodec", "pcm_s16le", "-ar", str(RATE), "-ac", "1", "-"]
    raw = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=True).stdout
    return np.frombuffer(raw, dtype="<i2").astype(np.float32) / 32768.0


def envelope(x, hop=480):
    n = len(x) // hop
    if n < 2:
        return np.zeros(1), hop
    return np.sqrt((x[: n * hop].reshape(n, hop) ** 2).mean(axis=1)), hop


def cut_points(x):
    """에너지 골짜기에서 자를 자리를 고른다."""
    env, hop = envelope(x)
    if len(env) < 4:
        return []
    smooth = np.convolve(env, np.ones(5) / 5.0, mode="same")
    min_frames = int(TARGET_MIN * RATE / hop)
    max_frames = int(TARGET_MAX * RATE / hop)

    cuts = [0]
    i = 0
    n = len(smooth)
    while i + min_frames < n:
        lo = i + min_frames
        hi = min(n - 1, i + max_frames)
        if hi <= lo:
            break
        # 허용 구간에서 가장 조용한 곳에서 끊는다
        j = lo + int(np.argmin(smooth[lo:hi + 1]))
        cuts.append(j)
        i = j
    return [(a * hop, b * hop) for a, b in zip(cuts[:-1], cuts[1:])]


def score(chunk):
    """덩어리가 쓸 만한지. 조용하거나 몸통이 없으면 낮다."""
    peak = float(np.abs(chunk).max())
    rms = float(np.sqrt((chunk ** 2).mean()))
    if peak < 1e-4:
        return 0.0
    # 피크가 크고, 실효값도 어느 정도 받쳐 주는 것이 좋다
    return rms * min(peak / 0.02, 1.0)


def fade(chunk):
    n = int(FADE_MS * RATE / 1000.0)
    n = min(n, len(chunk) // 3)
    if n < 2:
        return chunk
    ramp = np.linspace(0.0, 1.0, n, dtype=np.float32)
    out = np.copy(chunk)
    out[:n] *= ramp
    out[-n:] *= ramp[::-1]
    return out


def write_wav(path, samples, rate=RATE):
    data = np.clip(samples, -1.0, 1.0)
    pcm = (data * 32767.0).astype("<i2").tobytes()
    with open(path, "wb") as f:
        f.write(b"RIFF")
        f.write((36 + len(pcm)).to_bytes(4, "little"))
        f.write(b"WAVEfmt ")
        f.write((16).to_bytes(4, "little"))
        f.write((1).to_bytes(2, "little"))
        f.write((1).to_bytes(2, "little"))
        f.write(rate.to_bytes(4, "little"))
        f.write((rate * 2).to_bytes(4, "little"))
        f.write((2).to_bytes(2, "little"))
        f.write((16).to_bytes(2, "little"))
        f.write(b"data")
        f.write(len(pcm).to_bytes(4, "little"))
        f.write(pcm)


def main():
    src = sys.argv[1]
    groups = json.load(io.open(sys.argv[2], encoding="utf-8"))
    keep = [int(v) for v in sys.argv[3].split(",")]
    out_dir = sys.argv[4]
    report_path = sys.argv[5]

    os.makedirs(out_dir, exist_ok=True)
    lines = []
    lines.append("재질별 덩어리 뽑기 (동작 한 번 = %.2f~%.2f초)" % (TARGET_MIN, TARGET_MAX))
    lines.append("=" * 80)
    lines.append("%-6s %8s %8s %10s %10s" % ("재질", "덩어리", "평균길이", "재료", "총용량"))
    lines.append("-" * 80)

    manifest = []
    for gi in keep:
        g = groups[gi - 1]
        picked = []
        for a, b in g["spans"]:
            if b - a < TARGET_MIN:
                continue
            audio = decode(src, a, b - a)
            for s, e in cut_points(audio):
                chunk = audio[s:e]
                if len(chunk) < int(TARGET_MIN * RATE):
                    continue
                picked.append((score(chunk), fade(chunk)))
            if len(picked) > PER_MATERIAL * 6:
                break

        picked.sort(key=lambda p: -p[0])
        best = [c for _, c in picked[:PER_MATERIAL]]
        if not best:
            continue

        # 재질 안에서만 정규화한다. 재질끼리는 원래 크기 차이를 남긴다.
        top = max(float(np.abs(c).max()) for c in best)
        gain = 0.92 / max(top, 1e-6)
        best = [c * gain for c in best]

        joined = np.concatenate([np.concatenate([c, np.zeros(int(0.12 * RATE), dtype=np.float32)])
                                 for c in best])
        name = "m%02d_%.0fHz" % (gi, g["centroid"])
        write_wav(os.path.join(out_dir, name + ".wav"), joined)

        total = sum(len(c) for c in best)
        lines.append("%-6s %7d개 %7.2fs %9.0fs %9.1fMB"
                     % (gi, len(best), total / len(best) / RATE, g["seconds"], total * 2 / 1024 / 1024))
        manifest.append(dict(group=gi, centroid=g["centroid"], chunks=len(best),
                             samples=total, name=name))

    lines.append("-" * 80)
    lines.append("재질 %d개 · 덩어리 %d개 · 합계 %.1fMB (48kHz 모노 16bit)"
                 % (len(manifest), sum(m["chunks"] for m in manifest),
                    sum(m["samples"] for m in manifest) * 2 / 1024 / 1024))
    io.open(report_path, "w", encoding="utf-8").write("\n".join(lines) + "\n")
    json.dump(manifest, io.open(os.path.join(out_dir, "manifest.json"), "w", encoding="utf-8"),
              ensure_ascii=False, indent=1)
    print("재질 %d개" % len(manifest))


main()
