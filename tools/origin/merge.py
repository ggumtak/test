#!/usr/bin/env python3
"""Merges the drafted origin notes (out-N.tsv) with the reviewers' corrections (fix-N.tsv) into
app/src/main/assets/origin.tsv.

origin.tsv: kanji<TAB>자원<TAB>외우기 for the 2,136 상용한자, sorted by UTF-8 bytes (the app binary-searches it).
자원: the formation type ("형성:", "회의:", …) and how the parts made the character; 외우기: a one-line memory story
from the parts as they look in the Japanese form. Drafted by Claude in ten batches from in-N.tsv (made by
make_inputs.py: KanjiVG parts and sound marks, KANJIDIC2, the 훈음, a makemeahanzi hint) following BRIEF.md, then
every line re-read by a separate review pass following REVIEW.md; fix-N.tsv rows replace the drafted ones.
"""
import os
import sys

here = os.path.dirname(os.path.abspath(__file__))
out_path = os.path.join(here, "..", "..", "app", "src", "main", "assets", "origin.tsv")


def rows(path):
    out = []
    if not os.path.exists(path):
        return out
    for line in open(path, encoding="utf-8"):
        f = line.rstrip("\n").split("\t")
        if len(f) == 3 and f[0]:
            out.append([x.strip() for x in f])
    return out


def main():
    merged, fixed = {}, 0
    n = 1
    while os.path.exists(os.path.join(here, f"out-{n}.tsv")):
        fixes = {r[0]: r for r in rows(os.path.join(here, f"fix-{n}.tsv"))}
        for r in rows(os.path.join(here, f"out-{n}.tsv")):
            if r[0] in fixes:
                fixed += r != fixes[r[0]]
                r = fixes[r[0]]
            merged[r[0]] = r
        n += 1
    with open(out_path, "w", encoding="utf-8", newline="\n") as f:
        for k in sorted(merged, key=lambda c: c.encode("utf-8")):
            f.write("\t".join(merged[k]) + "\n")
    print(len(merged), "rows,", fixed, "corrected")


if __name__ == "__main__":
    main(*sys.argv[1:])
