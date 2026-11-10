#!/usr/bin/env python3
"""Builds app/src/main/assets/similar.tsv: for each 상용한자, the other 상용한자 it is easily mistaken for by shape
(持: 待 特 侍; 未: 末 米), for the card's "닮은 한자" row.

Usage: build_similar_data.py <parts.tsv> <kanji.tsv> <groups.txt> <out.tsv> [--why 持]
  parts.tsv, kanji.tsv - app/src/main/assets (the brush outlines the card draws, and the grades)
  groups.txt           - tools/similar/groups.txt: hand-picked groups of look-alikes
  --why 持             - prints that character's candidates with their scores
Needs numpy and Pillow (pip install numpy pillow).

How: each kanji is drawn from its own stroke outlines (the ones the card shows), blurred a little, and laid over
every other one. Two kanji look alike when
  - the whole drawings overlap closely and the stroke counts differ by at most one (未 末, 大 犬, 貴 責), or
  - the drawings without the radical's strokes overlap closely: the same part in the same place with another
    radical beside it (持 待 特, 腹 復, 険 験), or
  - groups.txt says so (土 士, 輸 輪): the classic pairs that differ by one short stroke or by a part that only
    resembles the other's. A hand-picked pair the drawings do not support at all is left out and printed,
    unless its line starts with "!" (忙 忘: the same parts arranged another way).
At most four per kanji: the hand-picked ones first, then the closest drawings.

One line per character that has any, sorted by UTF-8 bytes (the app binary-searches it): char<TAB>look-alikes.
"""
import re
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

BIG = 192          # each kanji is drawn this large, then blurred and reduced
SMALL = 48
BLUR = 3.75
WHOLE_MIN = 0.78   # overlap of the whole drawings (1 = identical)
REST_MIN = 0.87    # overlap of the drawings without the radical's strokes
REST_WHOLE_MIN = 0.45
PICKED_WHOLE_MIN = 0.40   # a hand-picked pair needs at least this, or the next
PICKED_REST_MIN = 0.60
LIMIT = 4
TOKEN = re.compile(r"[Mcqz]|-?\d+(?:\.\d+)?")


def polygons(path, steps=6):
    """The outline (M, c, q, z as AnimCJK writes it) as closed polygons."""
    t = TOKEN.findall(path)
    out, cur = [], []
    x = y = 0.0
    cmd = ""
    i = 0

    def num():
        nonlocal i
        v = float(t[i])
        i += 1
        return v

    while i < len(t):
        if t[i] in "Mcqz":
            cmd = t[i]
            i += 1
        if cmd == "M":
            x, y = num(), num()
            cur = [(x, y)]
        elif cmd == "c":
            x1, y1, x2, y2, x3, y3 = x + num(), y + num(), x + num(), y + num(), x + num(), y + num()
            for k in range(1, steps + 1):
                u = k / steps
                a, b, c, d = (1 - u) ** 3, 3 * (1 - u) ** 2 * u, 3 * (1 - u) * u * u, u ** 3
                cur.append((a * x + b * x1 + c * x2 + d * x3, a * y + b * y1 + c * y2 + d * y3))
            x, y = x3, y3
        elif cmd == "q":
            x1, y1, x2, y2 = x + num(), y + num(), x + num(), y + num()
            for k in range(1, steps + 1):
                u = k / steps
                a, b, c = (1 - u) ** 2, 2 * (1 - u) * u, u * u
                cur.append((a * x + b * x1 + c * x2, a * y + b * y1 + c * y2))
            x, y = x2, y2
        elif cmd == "z":
            if len(cur) > 2:
                out.append(cur)
            cur, cmd = [], ""
        else:
            i += 1
    return out


def picture(strokes):
    """The strokes drawn, blurred and reduced, as a unit vector (so a dot product is the overlap)."""
    im = Image.new("L", (BIG, BIG), 0)
    draw = ImageDraw.Draw(im)
    s = BIG / 1024
    for st in strokes:
        for p in polygons(st[1:]):
            draw.polygon([(px * s, py * s) for px, py in p], fill=255)
    v = np.asarray(im.filter(ImageFilter.GaussianBlur(BLUR)).resize((SMALL, SMALL), Image.BILINEAR),
                   dtype=np.float32).ravel()
    v = v - v.mean()
    n = np.linalg.norm(v)
    return v / n if n > 1e-6 else v * 0


def main(argv):
    why = None
    if "--why" in argv:
        i = argv.index("--why")
        why = argv[i + 1]
        argv = argv[:i] + argv[i + 2:]
    parts_path, kanji_path, groups_path, out_path = argv[:4]
    joyo = set()
    with open(kanji_path, encoding="utf-8") as f:
        for line in f:
            c = line.rstrip("\n").split("\t")
            if c[6] in ("1", "2", "3", "4", "5", "6", "8"):
                joyo.add(c[0])
    chars, whole, rest, strokes, rest_strokes = [], [], [], [], []
    with open(parts_path, encoding="utf-8") as f:
        for line in f:
            c = line.rstrip("\n").split("\t")
            if c[0] not in joyo or len(c) < 8 or c[7] != "f":
                continue
            st = [x for x in c[5].split("|") if len(x) > 1]
            other = [x for x in st if x[0] != "r"]
            chars.append(c[0])
            strokes.append(len(st))
            rest_strokes.append(len(other))
            whole.append(picture(st))
            # Without the radical: only when the kanji has both a radical and something else.
            rest.append(picture(other) if other and len(other) < len(st) else np.zeros(SMALL * SMALL, np.float32))
    index = {c: i for i, c in enumerate(chars)}
    whole_sim = np.stack(whole) @ np.stack(whole).T
    rest_sim = np.stack(rest) @ np.stack(rest).T

    picked = {c: [] for c in chars}
    dropped = []
    with open(groups_path, encoding="utf-8") as f:
        for line in f:
            group = line.strip()
            if not group or group.startswith("#"):
                continue
            # "!": kept whatever the drawings say.
            forced = group.startswith("!")
            group = group.lstrip("!")
            unknown = [c for c in group if c not in index]
            assert not unknown, "not a 상용한자 with a drawing: %s in %s" % ("".join(unknown), group)
            for a in group:
                for b in group:
                    if a == b or b in picked[a]:
                        continue
                    i, j = index[a], index[b]
                    if forced or whole_sim[i, j] >= PICKED_WHOLE_MIN or rest_sim[i, j] >= PICKED_REST_MIN:
                        picked[a].append(b)
                    elif a < b:
                        dropped.append("%s%s %.2f/%.2f" % (a, b, whole_sim[i, j], rest_sim[i, j]))

    lines = []
    counts = [0] * (LIMIT + 1)
    for c in chars:
        i = index[c]
        found = []
        for j in range(len(chars)):
            if j == i:
                continue
            w, r = float(whole_sim[i, j]), float(rest_sim[i, j])
            by_whole = w >= WHOLE_MIN and abs(strokes[i] - strokes[j]) <= 1
            by_rest = (r >= REST_MIN and rest_strokes[i] >= 3 and rest_strokes[j] >= 3
                       and abs(rest_strokes[i] - rest_strokes[j]) <= 1 and w >= REST_WHOLE_MIN)
            by_hand = chars[j] in picked[c]
            if by_whole or by_rest or by_hand:
                score = max(w, r - 0.05 if by_rest else 0.0) + (1.0 if by_hand else 0.0)
                found.append((score, chars[j], w, r, "hand" if by_hand else "whole" if by_whole else "rest"))
        found.sort(key=lambda x: (-x[0], x[1]))
        if c == why:
            for score, b, w, r, how in found[:12]:
                print("%s %s whole %.2f rest %.2f %s" % (c, b, w, r, how))
        found = found[:LIMIT]
        counts[len(found)] += 1
        if found:
            lines.append((c, "".join(b for _, b, _, _, _ in found)))
    lines.sort(key=lambda x: x[0].encode("utf-8"))
    with open(out_path, "w", encoding="utf-8", newline="\n") as f:
        for c, members in lines:
            f.write(c + "\t" + members + "\n")
    print(len(lines), "of", len(chars), "kanji have a look-alike; by number of look-alikes", counts)
    if dropped:
        print("hand-picked pairs the drawings do not support (left out):", " ".join(dropped))


if __name__ == "__main__":
    main(sys.argv[1:])
