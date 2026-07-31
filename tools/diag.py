"""
녹음에 파쇄음이 실제로 들어 있는지, 아니면 저주파에 묻혔을 뿐인지 가른다.

대역 비중(%)만 보면 "저역 99%"가 두 가지를 뜻할 수 있다.
  (가) 파쇄음은 멀쩡한데 저주파 럼블이 훨씬 커서 비중이 밀린 것
  (나) 파쇄음이 아예 잡히지 않은 것
둘은 **절대 레벨(dBFS)** 로만 갈린다. 고역이 -60dB면 (가), -90dB면 (나)다.
"""
import io
import subprocess
import sys

import numpy as np

FFMPEG = r"C:\ffmpeg\bin\ffmpeg.exe"
RATE = 48000


def decode(path, start, duration, highpass=None):
    cmd = [FFMPEG, "-v", "error", "-ss", "%.3f" % start, "-i", path, "-t", "%.3f" % duration]
    if highpass:
        cmd += ["-af", "highpass=f=%d:poles=2,highpass=f=%d:poles=2" % (highpass, highpass)]
    cmd += ["-f", "s16le", "-acodec", "pcm_s16le", "-ar", str(RATE), "-ac", "1", "-"]
    raw = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=True).stdout
    return np.frombuffer(raw, dtype="<i2").astype(np.float32) / 32768.0


def db(x):
    return 20.0 * np.log10(max(float(x), 1e-9))


def bands(x):
    win = 4096
    hop = 2048
    if len(x) < win:
        return None
    n = (len(x) - win) // hop + 1
    idx = np.arange(win)[None, :] + hop * np.arange(n)[:, None]
    mag = np.abs(np.fft.rfft(x[idx] * np.hanning(win)[None, :], axis=1)) / (win / 4)
    freqs = np.fft.rfftfreq(win, 1.0 / RATE)
    p = (mag ** 2).mean(axis=0)

    def rms(lo, hi):
        sel = (freqs >= lo) & (freqs < hi)
        return np.sqrt(p[sel].sum())

    peak_bin = int(np.argmax(p))
    return dict(
        sub=rms(0, 60), low=rms(60, 300), lowmid=rms(300, 1000),
        mid=rms(1000, 3000), high=rms(3000, 8000), veryhigh=rms(8000, 20000),
        peakHz=float(freqs[peak_bin]),
        total=np.sqrt(p.sum()),
    )


def centroid(x):
    win = 2048
    hop = 512
    if len(x) < win:
        return 0.0
    n = (len(x) - win) // hop + 1
    idx = np.arange(win)[None, :] + hop * np.arange(n)[:, None]
    mag = np.abs(np.fft.rfft(x[idx] * np.hanning(win)[None, :], axis=1))
    freqs = np.fft.rfftfreq(win, 1.0 / RATE)
    p = mag ** 2
    t = p.sum(axis=1) + 1e-12
    strong = t > np.percentile(t, 70)
    if strong.sum() < 3:
        return 0.0
    return float((p[strong] * freqs[None, :]).sum() / p[strong].sum())


def main():
    path = sys.argv[1]
    out = sys.argv[2]
    spots = [float(v) for v in sys.argv[3].split(",")]

    lines = []
    lines.append("녹음에 파쇄음이 있는가 — 절대 레벨(dBFS)로 확인")
    lines.append("=" * 104)
    lines.append("%-9s %8s %8s %8s %8s %8s %8s %9s %9s"
                 % ("지점", "0-60Hz", "60-300", "300-1k", "1k-3k", "3k-8k", "8k-20k", "최고피크", "중심"))
    lines.append("-" * 104)

    for s in spots:
        x = decode(path, s, 8.0)
        b = bands(x)
        if b is None:
            continue
        lines.append("%7.0fs %8.1f %8.1f %8.1f %8.1f %8.1f %8.1f %8.0fHz %8.0fHz"
                     % (s, db(b["sub"]), db(b["low"]), db(b["lowmid"]), db(b["mid"]),
                        db(b["high"]), db(b["veryhigh"]), b["peakHz"], centroid(x)))

    lines.append("")
    lines.append("같은 지점을 200Hz 아래를 깎고 다시 재면")
    lines.append("-" * 104)
    for s in spots:
        x = decode(path, s, 8.0, highpass=200)
        b = bands(x)
        if b is None:
            continue
        lines.append("%7.0fs %8.1f %8.1f %8.1f %8.1f %8.1f %8.1f %8.0fHz %8.0fHz"
                     % (s, db(b["sub"]), db(b["low"]), db(b["lowmid"]), db(b["mid"]),
                        db(b["high"]), db(b["veryhigh"]), b["peakHz"], centroid(x)))

    io.open(out, "w", encoding="utf-8").write("\n".join(lines) + "\n")
    print("done")


main()
