"""
사람이 "쓸 만하다"고 고른 구간만 남기고, 그 안을 잘게 다시 가른다.

앞서 굵게 묶었더니 48분이 한 덩어리로 나왔다. 그 안에 서로 다른 공이 여러 개
들어 있을 수밖에 없다. 여기서는 합치지 않고 k-평균으로 잘게 쪼갠다.
같은 공을 나중에 또 만졌더라도 음색이 같으면 같은 묶음으로 모인다.
"""
import io
import json
import os
import subprocess
import sys

import numpy as np

FFMPEG = r"C:\ffmpeg\bin\ffmpeg.exe"
PREVIEW_SEC = 9.0


def load_keep(spec):
    """'3093-3147,3369-5219' 꼴을 구간 목록으로."""
    out = []
    for part in spec.split(","):
        a, b = part.split("-")
        out.append((float(a), float(b)))
    return out


def inside(row, keep):
    mid = (row["start"] + row["end"]) / 2.0
    return any(a <= mid <= b for a, b in keep)


def features(rows):
    m = np.array([
        [np.log2(max(r["centroid"], 50.0)) for r in rows],
        [r["low"] for r in rows],
        [r["mid"] for r in rows],
        [r["high"] for r in rows],
        [r["flatness"] for r in rows],
        [r["spread"] for r in rows],
        [np.log2(max(r["onsets"], 0.5)) for r in rows],
    ], dtype=np.float64).T
    return (m - m.mean(axis=0)) / (m.std(axis=0) + 1e-9)


def kmeans(x, k, seed=0, iters=120):
    rng = np.random.default_rng(seed)
    # k-means++ 로 시작점을 벌려 놓는다. 무작위로 잡으면 한쪽에 몰린다.
    centres = [x[rng.integers(len(x))]]
    for _ in range(k - 1):
        d = np.min(((x[:, None, :] - np.array(centres)[None, :, :]) ** 2).sum(axis=2), axis=1)
        p = d / (d.sum() + 1e-12)
        centres.append(x[rng.choice(len(x), p=p)])
    c = np.array(centres)

    labels = np.zeros(len(x), dtype=int)
    for _ in range(iters):
        d = ((x[:, None, :] - c[None, :, :]) ** 2).sum(axis=2)
        new = d.argmin(axis=1)
        if (new == labels).all():
            break
        labels = new
        for j in range(k):
            sel = labels == j
            if sel.any():
                c[j] = x[sel].mean(axis=0)
    return labels, c


def main():
    src = sys.argv[1]
    sessions = json.load(io.open(sys.argv[2], encoding="utf-8"))["sessions"]
    keep = load_keep(sys.argv[3])
    k = int(sys.argv[4])
    report_path = sys.argv[5]
    preview_dir = sys.argv[6]

    rows = [r for r in sessions if inside(r, keep)]
    if not rows:
        print("남은 세션이 없다")
        return

    x = features(rows)
    labels, _ = kmeans(x, k, seed=7)

    groups = []
    for j in range(k):
        part = [rows[i] for i in range(len(rows)) if labels[i] == j]
        if not part:
            continue
        groups.append(dict(
            sessions=len(part),
            seconds=sum(r["seconds"] for r in part),
            centroid=float(np.median([r["centroid"] for r in part])),
            low=float(np.median([r["low"] for r in part])),
            mid=float(np.median([r["mid"] for r in part])),
            high=float(np.median([r["high"] for r in part])),
            onsets=float(np.median([r["onsets"] for r in part])),
            crest=float(np.median([r["crest"] for r in part])),
            flatness=float(np.median([r["flatness"] for r in part])),
            spans=[(r["start"], r["end"]) for r in sorted(part, key=lambda r: -r["seconds"])],
        ))

    groups.sort(key=lambda g: -g["centroid"])

    lines = []
    lines.append("고른 구간을 잘게 다시 가른 결과")
    lines.append("=" * 96)
    total = sum(g["seconds"] for g in groups)
    lines.append("쓸 만한 구간 %.0f초(%.0f분) · 세션 %d개 · 묶음 %d개" % (total, total / 60, len(rows), len(groups)))
    lines.append("")
    lines.append("%-4s %6s %8s %9s %7s %13s %8s %6s"
                 % ("번호", "세션", "총길이", "중심", "파열", "저/중/고역", "크레스트", "평탄도"))
    lines.append("-" * 96)
    for i, g in enumerate(groups, 1):
        lines.append("%-4d %5d개 %7.0fs %8.0fHz %6.1f회 %4.0f/%2.0f/%2.0f%% %8.1f %6.2f"
                     % (i, g["sessions"], g["seconds"], g["centroid"], g["onsets"],
                        g["low"] * 100, g["mid"] * 100, g["high"] * 100, g["crest"], g["flatness"]))
    lines.append("-" * 96)
    lines.append("")
    lines.append("각 묶음이 녹음의 어디인지 (긴 것부터)")
    for i, g in enumerate(groups, 1):
        spans = ", ".join("%.0f~%.0fs" % (a, b) for a, b in g["spans"][:5])
        more = "" if len(g["spans"]) <= 5 else " …외 %d곳" % (len(g["spans"]) - 5)
        lines.append("%2d번: %s%s" % (i, spans, more))

    io.open(report_path, "w", encoding="utf-8").write("\n".join(lines) + "\n")

    os.makedirs(preview_dir, exist_ok=True)
    for i, g in enumerate(groups, 1):
        a, b = g["spans"][0]
        # 세션 앞머리는 공을 집는 소리가 섞이기 쉬워 조금 뒤에서 뽑는다.
        start = a + min(1.0, (b - a) * 0.1)
        dur = min(PREVIEW_SEC, max(1.0, b - start))
        out = os.path.join(preview_dir, "%02d_%.0fHz_%.0fs.wav" % (i, g["centroid"], g["seconds"]))
        subprocess.run(
            [FFMPEG, "-v", "error", "-y", "-ss", "%.3f" % start, "-i", src, "-t", "%.3f" % dur,
             "-af", "highpass=f=150:poles=2,highpass=f=150:poles=2,dynaudnorm=f=200:g=5",
             "-ac", "1", "-ar", "48000", "-acodec", "pcm_s16le", out],
            check=False,
        )

    json.dump(groups, io.open(sys.argv[7], "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    print("묶음 %d개 · %.0f분" % (len(groups), total / 60))


main()
