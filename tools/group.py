"""
세션들을 종류별로 묶는다.

공을 하나씩 순서대로 뿌셨을 것이므로, 같은 공의 세션은 시간상 붙어 있다.
그래서 눈먼 군집화보다 **연속된 세션의 음색이 크게 바뀌는 지점**을 찾는 편이 정확하다.
바뀐 지점으로 끊어 묶고, 묶음마다 음색을 요약해 사람이 들어보고 이름 붙일 수 있게 한다.
"""
import io
import json
import subprocess
import sys

import numpy as np

FFMPEG = r"C:\ffmpeg\bin\ffmpeg.exe"

# 음색이 이만큼(정규화 거리) 튀면 다른 공으로 본다
CHANGE_THRESHOLD = 1.35
# 한 묶음이 되려면 최소 이만큼은 있어야 한다
MIN_SESSIONS = 2
MIN_SECONDS = 6.0
# 이보다 가까운 묶음은 같은 종류로 합친다
MERGE_THRESHOLD = 0.85


def features(rows):
    """중심은 옥타브로, 나머지는 그대로. 각 축을 표준화한다."""
    m = np.array([
        [np.log2(max(r["centroid"], 50.0)) for r in rows],
        [r["low"] for r in rows],
        [r["high"] for r in rows],
        [r["flatness"] for r in rows],
        [np.log2(max(r["onsets"], 0.5)) for r in rows],
    ], dtype=np.float64).T
    mean = m.mean(axis=0)
    std = m.std(axis=0) + 1e-9
    return (m - mean) / std


def smooth(f, k=3):
    """세션 하나가 튀는 것으로 경계를 만들지 않도록 이동평균을 건다."""
    out = np.copy(f)
    n = len(f)
    for i in range(n):
        lo = max(0, i - k // 2)
        hi = min(n, i + k // 2 + 1)
        out[i] = f[lo:hi].mean(axis=0)
    return out


def split_runs(f):
    d = np.linalg.norm(f[1:] - f[:-1], axis=1)
    cuts = [0] + [i + 1 for i, v in enumerate(d) if v > CHANGE_THRESHOLD] + [len(f)]
    return [(cuts[i], cuts[i + 1]) for i in range(len(cuts) - 1)]


def summarize(rows, f, lo, hi):
    part = rows[lo:hi]
    return dict(
        sessions=len(part),
        seconds=sum(r["seconds"] for r in part),
        start=part[0]["start"],
        end=part[-1]["end"],
        centroid=float(np.median([r["centroid"] for r in part])),
        low=float(np.median([r["low"] for r in part])),
        mid=float(np.median([r["mid"] for r in part])),
        high=float(np.median([r["high"] for r in part])),
        onsets=float(np.median([r["onsets"] for r in part])),
        crest=float(np.median([r["crest"] for r in part])),
        flatness=float(np.median([r["flatness"] for r in part])),
        spread=float(np.median([r["spread"] for r in part])),
        sessionSeconds=float(np.median([r["seconds"] for r in part])),
        centre=f[lo:hi].mean(axis=0).tolist(),
        range=(lo, hi),
    )


def main():
    src = sys.argv[1]
    data = json.load(io.open(sys.argv[2], encoding="utf-8"))
    report_path = sys.argv[3]
    preview_dir = sys.argv[4]

    rows = data["sessions"]
    f = smooth(features(rows))
    runs = split_runs(f)

    groups = [summarize(rows, f, lo, hi) for lo, hi in runs]
    groups = [g for g in groups if g["sessions"] >= MIN_SESSIONS and g["seconds"] >= MIN_SECONDS]

    # 떨어져 있어도 음색이 거의 같으면 같은 공을 두 번 만진 것으로 보고 합친다.
    merged = []
    for g in groups:
        hit = None
        for m in merged:
            if np.linalg.norm(np.array(g["centre"]) - np.array(m["centre"])) < MERGE_THRESHOLD:
                hit = m
                break
        if hit is None:
            g["parts"] = [(g["start"], g["end"])]
            merged.append(g)
        else:
            hit["parts"].append((g["start"], g["end"]))
            hit["sessions"] += g["sessions"]
            hit["seconds"] += g["seconds"]
            for key in ("centroid", "low", "mid", "high", "onsets", "crest", "flatness", "spread", "sessionSeconds"):
                hit[key] = (hit[key] + g[key]) / 2.0

    merged.sort(key=lambda g: -g["centroid"])

    lines = []
    lines.append("왁뿌볼녹음2.wav 자동 분류")
    lines.append("=" * 100)
    lines.append("전체 %.0f초(%.0f분) · 세션 %d개 · 묶음 %d개"
                 % (data["totalSec"], data["totalSec"] / 60, len(rows), len(merged)))
    lines.append("")
    lines.append("%-4s %6s %8s %9s %9s %7s %13s %7s %6s"
                 % ("번호", "세션", "총길이", "세션길이", "중심", "파열", "저/중/고역", "크레스트", "평탄도"))
    lines.append("-" * 100)
    for i, g in enumerate(merged, 1):
        lines.append("%-4d %5d개 %7.0fs %8.2fs %8.0fHz %6.1f회 %4.0f/%2.0f/%2.0f%% %7.1f %6.2f"
                     % (i, g["sessions"], g["seconds"], g["sessionSeconds"], g["centroid"],
                        g["onsets"], g["low"] * 100, g["mid"] * 100, g["high"] * 100,
                        g["crest"], g["flatness"]))
    lines.append("-" * 100)
    lines.append("")
    lines.append("각 묶음이 녹음의 어디인지 (들어보고 이름 붙이시면 됩니다)")
    for i, g in enumerate(merged, 1):
        spans = ", ".join("%.0f~%.0fs" % (a, b) for a, b in g["parts"][:6])
        more = "" if len(g["parts"]) <= 6 else " …외 %d곳" % (len(g["parts"]) - 6)
        lines.append("%2d번: %s%s" % (i, spans, more))

    io.open(report_path, "w", encoding="utf-8").write("\n".join(lines) + "\n")

    # 묶음마다 미리듣기를 뽑는다. 가장 긴 세션의 앞 6초.
    import os
    os.makedirs(preview_dir, exist_ok=True)
    for i, g in enumerate(merged, 1):
        best = max(g["parts"], key=lambda p: p[1] - p[0])
        start = best[0]
        dur = min(6.0, best[1] - best[0])
        out = os.path.join(preview_dir, "%02d_%.0fHz.wav" % (i, g["centroid"]))
        subprocess.run(
            [FFMPEG, "-v", "error", "-y", "-ss", "%.3f" % start, "-i", src,
             "-t", "%.3f" % dur, "-ac", "1", "-ar", "48000", "-acodec", "pcm_s16le", out],
            check=False,
        )

    json.dump(merged, io.open(sys.argv[5], "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    print("묶음 %d개" % len(merged))


main()
