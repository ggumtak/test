#!/usr/bin/env python3
"""Fills the meaning column of app/src/main/assets/radicals.tsv from tools/origin/radmeaning.tsv (number, form,
position, meaning, reason): the rows the 상용한자 use whose name does not say what the radical is in a kanji
(戸 지게 호 → 외짝 문, 卩 병부 절 → 무릎 꿇은 사람). Usage: apply_radmeaning.py"""
import os

HERE = os.path.dirname(os.path.abspath(__file__))
PATH = os.path.join(HERE, "..", "..", "app", "src", "main", "assets", "radicals.tsv")
fix = {}
for line in open(os.path.join(HERE, "radmeaning.tsv"), encoding="utf-8"):
    if line.strip() and not line.startswith("#"):
        f = line.rstrip("\n").split("\t")
        if f[3] != "뜻 없는 머리 부분":
            fix[(f[0], f[1], f[2])] = f[3]
out, n = [], 0
for line in open(PATH, encoding="utf-8"):
    f = line.rstrip("\n").split("\t")
    if not line.startswith("#") and len(f) >= 5 and (f[0], f[1], f[2]) in fix:
        f = f[:5] + [fix[(f[0], f[1], f[2])]]
        n += 1
        line = "\t".join(f) + "\n"
    out.append(line)
open(PATH, "w", encoding="utf-8", newline="\n").write("".join(out))
print(n, "rows given a meaning of", len(fix))
