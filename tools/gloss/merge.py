#!/usr/bin/env python3
"""Merges the drafted glosses (out-N.tsv) with the reviewers' corrections (fix-N.tsv) into gloss.tsv.

gloss.tsv is what tools/build_kanji_data.py reads: kanji<TAB>gloss<TAB>english_ko for the 2,136 상용한자.
gloss: the kanji's meanings as used in Japanese, in plain modern Korean, most common first; english_ko: the
KANJIDIC English meanings in short Korean. Drafted by Claude from KANJIDIC and the old 훈음, then every line
reviewed by a second pass; fix-N.tsv rows replace the drafted ones.
"""
import os
import sys

here = os.path.dirname(os.path.abspath(__file__))


def rows(path):
    out = []
    if not os.path.exists(path):
        return out
    for line in open(path, encoding="utf-8"):
        f = line.rstrip("\n").split("\t")
        if len(f) == 3 and f[0]:
            out.append(f)
    return out


def main():
    merged, fixed = [], 0
    for i in (1, 2, 3):
        fixes = {r[0]: r for r in rows(os.path.join(here, f"fix-{i}.tsv"))}
        for r in rows(os.path.join(here, f"out-{i}.tsv")):
            if r[0] in fixes:
                fixed += r != fixes[r[0]]
                r = fixes[r[0]]
            merged.append(r)
    with open(os.path.join(here, "gloss.tsv"), "w", encoding="utf-8", newline="\n") as f:
        for r in merged:
            f.write("\t".join(x.strip() for x in r) + "\n")
    print(len(merged), "rows,", fixed, "corrected")


if __name__ == "__main__":
    main(*sys.argv[1:])
