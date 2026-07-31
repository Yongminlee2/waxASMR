"""
재질별 덩어리를 앱이 읽는 뱅크 파일로 굽는다.

chunks.bin : 덩어리 샘플을 죽 이어붙인 16bit PCM (48kHz 모노)
chunks.idx : 한 줄에 덩어리 하나 — 시작오프셋,길이,재질번호,중심주파수

재질 번호는 밝은 것부터 0번이다. 앱의 Material enum 순서와 같아야 한다.
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
TARGET_MIN = 0.40
TARGET_MAX = 0.95
FADE_MS = 8.0
PER_MATERIAL = 18


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
    env, hop = envelope(x)
    if len(env) < 4:
        return []
    smooth = np.convolve(env, np.ones(5) / 5.0, mode="same")
    lo_n = int(TARGET_MIN * RATE / hop)
    hi_n = int(TARGET_MAX * RATE / hop)
    cuts = [0]
    i = 0
    n = len(smooth)
    while i + lo_n < n:
        lo = i + lo_n
        hi = min(n - 1, i + hi_n)
        if hi <= lo:
            break
        j = lo + int(np.argmin(smooth[lo:hi + 1]))
        cuts.append(j)
        i = j
    return [(a * hop, b * hop) for a, b in zip(cuts[:-1], cuts[1:])]


def score(chunk):
    peak = float(np.abs(chunk).max())
    if peak < 1e-4:
        return 0.0
    rms = float(np.sqrt((chunk ** 2).mean()))
    return rms * min(peak / 0.02, 1.0)


def fade(chunk):
    n = min(int(FADE_MS * RATE / 1000.0), len(chunk) // 3)
    if n < 2:
        return chunk
    ramp = np.linspace(0.0, 1.0, n, dtype=np.float32)
    out = np.copy(chunk)
    out[:n] *= ramp
    out[-n:] *= ramp[::-1]
    return out


def centroid_of(x):
    win = 2048
    if len(x) < win:
        return 3000.0
    hop = 512
    n = (len(x) - win) // hop + 1
    idx = np.arange(win)[None, :] + hop * np.arange(n)[:, None]
    mag = np.abs(np.fft.rfft(x[idx] * np.hanning(win)[None, :], axis=1))
    freqs = np.fft.rfftfreq(win, 1.0 / RATE)
    p = mag ** 2
    t = p.sum(axis=1) + 1e-12
    strong = t > np.percentile(t, 70)
    if strong.sum() < 2:
        strong = np.ones(n, dtype=bool)
    return float((p[strong] * freqs[None, :]).sum() / p[strong].sum())


def main():
    src = sys.argv[1]
    groups = json.load(io.open(sys.argv[2], encoding="utf-8"))
    keep = [int(v) for v in sys.argv[3].split(",")]
    assets = sys.argv[4]
    report = sys.argv[5]

    os.makedirs(assets, exist_ok=True)

    all_chunks = []   # (material, samples)
    lines = []
    lines.append("재질별 뱅크 (덩어리 %0.2f~%0.2f초)" % (TARGET_MIN, TARGET_MAX))
    lines.append("=" * 78)
    lines.append("%-4s %-8s %7s %8s %9s %9s" % ("번호", "묶음", "덩어리", "평균길이", "중심", "용량"))
    lines.append("-" * 78)

    for material, gi in enumerate(keep):
        g = groups[gi - 1]
        picked = []
        for a, b in g["spans"]:
            if b - a < TARGET_MIN:
                continue
            audio = decode(src, a, b - a)
            for s, e in cut_points(audio):
                c = audio[s:e]
                if len(c) >= int(TARGET_MIN * RATE):
                    picked.append((score(c), fade(c)))
            if len(picked) > PER_MATERIAL * 6:
                break
        picked.sort(key=lambda p: -p[0])
        best = [c for _, c in picked[:PER_MATERIAL]]
        if not best:
            continue

        # 재질 안에서 한 번만 정규화한다. 덩어리마다 따로 맞추면 작게 바스락거리는 것도
        # 큰 파열만큼 커져서 셈여림이 죽는다. 파편 시절에 이미 겪은 실수다.
        top = max(float(np.abs(c).max()) for c in best)
        gain = 0.92 / max(top, 1e-6)
        best = [c * gain for c in best]

        total = sum(len(c) for c in best)
        cents = [centroid_of(c) for c in best]
        for c, cen in zip(best, cents):
            all_chunks.append((material, c, cen))

        lines.append("%-4d %-8s %6d개 %7.2fs %8.0fHz %8.1fMB"
                     % (material, "묶음%d" % gi, len(best), total / len(best) / RATE,
                        float(np.median(cents)), total * 2 / 1024 / 1024))

    # 굽기
    bin_path = os.path.join(assets, "chunks.bin")
    idx_path = os.path.join(assets, "chunks.idx")
    offset = 0
    idx = []
    with open(bin_path, "wb") as f:
        for material, c, cen in all_chunks:
            pcm = (np.clip(c, -1.0, 1.0) * 32767.0).astype("<i2")
            f.write(pcm.tobytes())
            idx.append("%d,%d,%d,%d" % (offset, len(pcm), material, int(cen)))
            offset += len(pcm)
    io.open(idx_path, "w", encoding="utf-8").write("\n".join(idx) + "\n")

    lines.append("-" * 78)
    lines.append("재질 %d개 · 덩어리 %d개 · chunks.bin %.1fMB"
                 % (len(keep), len(all_chunks), offset * 2 / 1024 / 1024))
    io.open(report, "w", encoding="utf-8").write("\n".join(lines) + "\n")
    print("덩어리 %d개 · %.1fMB" % (len(all_chunks), offset * 2 / 1024 / 1024))


main()
