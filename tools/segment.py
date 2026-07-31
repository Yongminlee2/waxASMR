"""
긴 왁뿌볼 녹음 하나를 세션으로 끊고, 세션마다 음색을 재서 종류별로 묶는다.

여러 종류를 한 파일에 몰아 녹음했으므로, 공을 바꿀 때 생긴 조용한 구간을 경계로 본다.
경계를 찾은 뒤 세션마다 스펙트럼 중심·대역 비중·파열 빈도를 재고, 그 값으로 묶는다.
어느 묶음이 어느 공인지는 사람이 알려 줘야 한다. 여기서는 "몇 종류이고 각각 어떤
소리인지"까지만 낸다.

ffmpeg으로 8kHz 모노를 뽑아 경계를 찾고(가볍다), 스펙트럼은 48kHz 원본에서 잰다.
"""
import io
import json
import os
import subprocess
import sys

import numpy as np

FFMPEG = r"C:\ffmpeg\bin\ffmpeg.exe"
RATE_ENV = 8000        # 경계 찾기용
RATE_FULL = 48000      # 스펙트럼 측정용

# 세션을 가르는 기준
SILENCE_SEC = 1.2      # 이만큼 조용하면 공을 바꾼 것으로 본다
MIN_SESSION_SEC = 2.0  # 이보다 짧은 덩어리는 버린다
FLOOR_PERCENTILE = 35  # 이 백분위를 잡음 바닥으로 본다
FLOOR_MULT = 3.0       # 바닥의 이 배를 넘으면 소리가 있는 것


# 이 녹음에는 12Hz짜리 초저역 럼블이 파쇄음보다 크게 들어 있다(0~60Hz가 -20~-37dBFS).
# 걷어내지 않으면 무엇을 재도 "저역 99%"로 뭉개져서 종류를 가릴 수 없다.
HIGHPASS_HZ = 150


def decode(path, rate, mono=True, start=None, duration=None, highpass=HIGHPASS_HZ):
    cmd = [FFMPEG, "-v", "error"]
    if start is not None:
        cmd += ["-ss", "%.3f" % start]
    cmd += ["-i", path]
    if duration is not None:
        cmd += ["-t", "%.3f" % duration]
    if highpass:
        cmd += ["-af", "highpass=f=%d:poles=2,highpass=f=%d:poles=2" % (highpass, highpass)]
    cmd += ["-f", "s16le", "-acodec", "pcm_s16le", "-ar", str(rate)]
    if mono:
        cmd += ["-ac", "1"]
    cmd += ["-"]
    raw = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=True).stdout
    return np.frombuffer(raw, dtype="<i2").astype(np.float32) / 32768.0


def envelope(x, rate, hop_ms=10.0):
    hop = max(1, int(rate * hop_ms / 1000.0))
    n = len(x) // hop
    return np.sqrt((x[: n * hop].reshape(n, hop) ** 2).mean(axis=1)), hop / rate


def find_sessions(env, step):
    floor = np.percentile(env, FLOOR_PERCENTILE)
    live = env > max(floor * FLOOR_MULT, 1e-5)

    gap_frames = int(SILENCE_SEC / step)
    sessions = []
    i = 0
    n = len(live)
    while i < n:
        if not live[i]:
            i += 1
            continue
        start = i
        quiet = 0
        j = i
        while j < n:
            if live[j]:
                quiet = 0
            else:
                quiet += 1
                if quiet >= gap_frames:
                    break
            j += 1
        end = j - quiet
        if (end - start) * step >= MIN_SESSION_SEC:
            sessions.append((start * step, end * step))
        i = j + 1
    return sessions, float(floor)


def spectrum_features(x, rate):
    """스펙트럼 중심, 산포(옥타브), 저/중/고역 비중, 평탄도."""
    win = 2048
    hop = 512
    if len(x) < win:
        return None
    n = (len(x) - win) // hop + 1
    idx = np.arange(win)[None, :] + hop * np.arange(n)[:, None]
    frames = x[idx] * np.hanning(win)[None, :]
    mag = np.abs(np.fft.rfft(frames, axis=1))
    freqs = np.fft.rfftfreq(win, 1.0 / rate)

    power = mag ** 2
    total = power.sum(axis=1) + 1e-12
    # 조용한 프레임은 중심 계산을 흐린다. 센 프레임만 본다.
    strong = total > np.percentile(total, 60)
    if strong.sum() < 3:
        strong = np.ones(n, dtype=bool)
    p = power[strong]
    t = total[strong]

    centroid = float((p * freqs[None, :]).sum(axis=1).mean() / t.mean())

    band = lambda lo, hi: float(p[:, (freqs >= lo) & (freqs < hi)].sum() / p.sum())
    low = band(0, 500)
    mid = band(500, 3000)
    high = band(3000, rate / 2)

    # 옥타브 산포: 중심 주변 에너지 가중 표준편차를 log2로
    safe = np.maximum(freqs, 20.0)
    logf = np.log2(safe)
    mean_log = float((p * logf[None, :]).sum() / p.sum())
    var_log = float((p * (logf[None, :] - mean_log) ** 2).sum() / p.sum())
    spread = float(np.sqrt(max(var_log, 0.0)))

    gmean = np.exp(np.log(p.mean(axis=0) + 1e-12).mean())
    amean = p.mean() + 1e-12
    flatness = float(gmean / amean)

    return dict(centroid=centroid, low=low, mid=mid, high=high,
                spread=spread, flatness=flatness)


def onset_rate(x, rate):
    """스펙트럼 변화량으로 파열을 센다. 초당 몇 회인지."""
    win = 1024
    hop = 256
    if len(x) < win * 2:
        return 0.0
    n = (len(x) - win) // hop + 1
    idx = np.arange(win)[None, :] + hop * np.arange(n)[:, None]
    mag = np.abs(np.fft.rfft(x[idx] * np.hanning(win)[None, :], axis=1))
    flux = np.maximum(mag[1:] - mag[:-1], 0).sum(axis=1)
    if len(flux) < 5:
        return 0.0
    thresh = np.median(flux) + 1.6 * (np.percentile(flux, 75) - np.percentile(flux, 25) + 1e-9)
    armed = True
    count = 0
    for v in flux:
        if armed and v > thresh:
            count += 1
            armed = False
        elif v < thresh * 0.5:
            armed = True
    return count / (len(x) / rate)


def main():
    path = sys.argv[1]
    out_path = sys.argv[2]

    env_signal = decode(path, RATE_ENV)
    env, step = envelope(env_signal, RATE_ENV)
    total_sec = len(env_signal) / RATE_ENV
    del env_signal

    sessions, floor = find_sessions(env, step)

    rows = []
    for k, (s, e) in enumerate(sessions):
        chunk = decode(path, RATE_FULL, start=s, duration=e - s)
        if len(chunk) < RATE_FULL // 4:
            continue
        f = spectrum_features(chunk, RATE_FULL)
        if f is None:
            continue
        peak = float(np.abs(chunk).max())
        rms = float(np.sqrt((chunk ** 2).mean()) + 1e-12)
        rows.append(dict(
            index=k, start=s, end=e, seconds=e - s,
            onsets=onset_rate(chunk, RATE_FULL),
            crest=peak / rms, peak=peak,
            **f,
        ))
        del chunk

    with io.open(out_path, "w", encoding="utf-8") as fp:
        json.dump(dict(
            file=os.path.basename(path),
            totalSec=total_sec,
            noiseFloor=floor,
            sessions=rows,
        ), fp, ensure_ascii=False, indent=1)

    print("총 %.0f초 · 세션 %d개" % (total_sec, len(rows)))


main()
