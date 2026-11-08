#!/usr/bin/env python3
"""Cross-batch report (link/README.md): for each part shape, the words the 외우기 lines call it by, with the kanji,
so one part is not called by two names across the cards without a reason. Reads origin.tsv as merged.

Usage: names.py [min kanji per shape=3]"""
import os
import sys
from collections import defaultdict

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import rules  # noqa: E402


def main(least="3"):
    _, _, origin, _, joyo = rules.load_all()
    used = defaultdict(lambda: defaultdict(list))
    for ch in joyo:
        for word, inside in rules.memo_parts(origin[ch][2]):
            if word and len(inside) == 1:
                used[inside][rules.stem(word)].append(ch)
    for shape in sorted(used, key=lambda s: -sum(len(v) for v in used[s].values())):
        names = used[shape]
        if sum(len(v) for v in names.values()) < int(least) or len(names) < 2:
            continue
        print("%s  %s" % (shape, "  ".join("%s:%s" % (w, "".join(v)) for w, v in
                                          sorted(names.items(), key=lambda x: -len(x[1])))))


if __name__ == "__main__":
    main(*sys.argv[1:])
