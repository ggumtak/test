#!/usr/bin/env python3
"""Builds app/src/main/assets/radkanji.tsv: for each radical shape, the easiest 상용한자 that have it as their
radical with its meaning (氵: 池 海 汽 活 泳 …), for the card's "같은 부수" line.

Usage: build_radical_data.py <parts.tsv> <kanji.tsv> <origin.tsv> <out.tsv>   (all in app/src/main/assets)

Why: the 부수 row names the radical and what it means (氵 삼수변(물)), but a name is learned as a rule only when
several kanji that share it are seen together. A tap on the row lists them.

A kanji is listed under its radical's number and shape in it (parts.tsv: 85 and 氵 for 海, 85 and 水 for 氷) only
when the radical does there what it does in the others: its 구성 line (origin.tsv) gives the radical's part the 뜻
role under the name most kanji of that shape give it (氵 물). That leaves out the kanji a dictionary merely files
there (東 and 来 under 木, 服 under 月 where the 月 was a boat), the ones whose radical gives only the sound, and
the radical itself. Order: school grade, then stroke count (the kanji a learner knows first); at most 12 per shape.

One line per shape, sorted by UTF-8 bytes (the app binary-searches it): NUMBER+SHAPE<TAB>kanji (85氵	池海汽…).
"""
import re
import sys
from collections import defaultdict

LIMIT = 12
UNIT = re.compile(r"^([\^~]?)([^\[\]()]+?)(?:\[([^\]]+)])?(?:\(([^()]*)\))?$")


def radical_name(spec):
    """What the 구성 line calls the radical's part when that part has the 뜻 role (氵 → 물), else None."""
    body = spec.lstrip("!").strip()
    if not body or body == "-":
        return None
    for raw in body.split(" + "):
        m = UNIT.match(raw.strip())
        if not m:
            return None
        if m.group(1) == "^":
            role = (m.group(3) or "").split(" ")[0]
            return (m.group(4) or "").strip() or None if role in ("뜻", "뜻·소리") else None
    return None


def main(parts_path, kanji_path, origin_path, out_path):
    grade, strokes = {}, {}
    with open(kanji_path, encoding="utf-8") as f:
        for line in f:
            c = line.rstrip("\n").split("\t")
            if c[6] in ("1", "2", "3", "4", "5", "6", "8"):
                grade[c[0]] = int(c[6])
                strokes[c[0]] = int(c[5] or 0)
    name = {}
    with open(origin_path, encoding="utf-8") as f:
        for line in f:
            c = line.rstrip("\n").split("\t")
            if len(c) > 3:
                n = radical_name(c[3])
                if n:
                    name[c[0]] = n
    groups = defaultdict(list)
    with open(parts_path, encoding="utf-8") as f:
        for line in f:
            c = line.split("\t", 8)
            ch = c[0]
            if ch not in grade or len(c) < 6 or not c[1] or c[1] == "0":
                continue
            form = c[2]
            if not form or ch == form or ch not in name:
                continue
            groups[c[1] + form].append(ch)
    lines = []
    for key, members in groups.items():
        # The name most of them give the radical; the others use it for something else there.
        count = defaultdict(int)
        for ch in members:
            count[name[ch]] += 1
        usual = max(count, key=lambda n: (count[n], n))
        members = [ch for ch in members if name[ch] == usual]
        members.sort(key=lambda ch: (grade[ch], strokes[ch], ch))
        if len(members) >= 2:
            lines.append((key, "".join(members[:LIMIT])))
    lines.sort(key=lambda x: x[0].encode("utf-8"))
    with open(out_path, "w", encoding="utf-8", newline="\n") as f:
        for key, members in lines:
            f.write(key + "\t" + members + "\n")
    print(len(lines), "radical shapes;", sum(len(m) for _, m in lines), "kanji listed")


if __name__ == "__main__":
    main(*sys.argv[1:5])
