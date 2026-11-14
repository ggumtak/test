#!/usr/bin/env python3
"""Usage: make_batches.py — writes the files to hand to the Gemini app for a full rewrite of the 구성 names,
유래 and 외우기 of the 2,136 상용한자 (00_작성지침.md is written by hand; this writes batch-NN.txt and
names.txt, the part names most kanji share, for consistency).

Kanji go in learning order (grade 1-6, then the secondary-school ones, each by stroke count), 200 per batch, so a
partial return still covers the kanji met first. Each kanji is one block: its 훈음, grade, first readings, the parts
of its 구성 line (shape, role, current name), its current 유래 and 외우기."""
import os
import sys
from collections import Counter, defaultdict

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "..", "origin", "kosei", "polish"))
from common import ASSETS, load, units  # noqa: E402

PER = 200


def readings():
    out = {}
    with open(os.path.join(ASSETS, "usage.tsv"), encoding="utf-8") as f:
        for line in f:
            c = line.rstrip("\n").split("\t")
            if len(c) < 3:
                continue
            on = [r.split("|")[0] for r in c[1].split(";") if r][:2]
            kun = [r.split("|")[0].replace(".", "").lstrip("*") for r in c[2].split(";") if r][:2]
            out[c[0]] = (" · ".join(on), " · ".join(kun))
    return out


def main():
    rows = load()
    kan = {}
    with open(os.path.join(ASSETS, "kanji.tsv"), encoding="utf-8") as f:
        for line in f:
            c = line.rstrip("\n").split("\t")
            kan[c[0]] = c
    rd = readings()

    def grade(ch):
        g = int(kan[ch][6] or 0)
        return (g if 1 <= g <= 6 else 7, int(kan[ch][5] or 0))

    order = sorted(rows, key=grade)
    # The usual name of each meaning part across all kanji, so one part keeps one name.
    names = defaultdict(Counter)
    for ch in rows:
        for s, role, _, note in units(rows[ch]["spec"]):
            if note and role.startswith("뜻"):
                names[s][note] += 1
    with open(os.path.join(HERE, "names.txt"), "w", encoding="utf-8") as f:
        f.write("# 뜻 부품의 지금 이름(여러 한자에서 가장 많이 쓰는 이름, 쓰인 한자 수). 같은 부품은 모든 한자에서 같은 이름으로 불러 주세요.\n")
        for s, c in sorted(names.items(), key=lambda x: -sum(x[1].values())):
            n = sum(c.values())
            if n < 4:
                continue
            f.write("%s\t%s\t%d\n" % (s, c.most_common(1)[0][0], n))
    label_of = {1: "1학년", 2: "2학년", 3: "3학년", 4: "4학년", 5: "5학년", 6: "6학년", 7: "중학 이상"}
    for b in range(0, len(order), PER):
        part = order[b:b + PER]
        n = b // PER + 1
        with open(os.path.join(HERE, "batch-%02d.txt" % n), "w", encoding="utf-8") as f:
            f.write("# 묶음 %02d: %d자 (%s ~ %s). 작성 규칙은 00_작성지침.md. 답은 한자마다 한 줄: 한자 | 구성 이름 | 유래 | 외우기\n\n"
                    % (n, len(part), part[0], part[-1]))
            for ch in part:
                r = rows[ch]
                g, _ = grade(ch)
                on, kun = rd.get(ch, ("", ""))
                f.write("### %s (%s) · %s · 음 %s · 훈 %s\n" % (ch, r["hun"], label_of[g], on or "-", kun or "-"))
                us = units(r["spec"])
                if us:
                    parts = []
                    for i, (s, role, reading, note) in enumerate(us, 1):
                        parts.append("%d) %s [%s%s] 지금 이름: %s" % (i, s, role, " " + reading if reading else "",
                                                                note or "(없음)"))
                    f.write("구성 부품: " + " | ".join(parts) + "\n")
                else:
                    f.write("구성 부품: (없음 → 답의 구성 칸은 -)\n")
                f.write("유래(지금): %s\n" % r["raw"])
                f.write("외우기(지금): %s\n\n" % r["memo"])
    print(len(order), "kanji in", (len(order) + PER - 1) // PER, "batches")


if __name__ == "__main__":
    main()
