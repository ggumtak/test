#!/usr/bin/env python3
"""Numbers for link/README.md: how the 외우기 lines of an origin.tsv fare on the automatic checks of rules.py.

Usage: stats.py [origin.tsv=the app's]"""
import os
import re
import sys
from collections import Counter

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import rules  # noqa: E402
from convert import load  # noqa: E402


def main(path=None):
    kanji, parts, origin, rads, joyo = rules.load_all()
    if path:
        origin = load(path)
    kinds, hinted, lengths, gives = Counter(), 0, [], 0
    for ch in joyo:
        o = origin[ch]
        _, _, rm = rules.radical_row(parts.get(ch), rads)
        hs = rules.memo_hints(ch, kanji[ch], parts.get(ch), o[3] if len(o) > 3 else "", o[1], o[2], rm)
        hinted += bool(hs)
        for h in hs:
            kinds[re.sub(r"[^\s]*[㐀-鿿\U00020000-\U0002ffff][^\s]*", "X", h.split("(")[0].split("'")[0])[:30]] += 1
        memo = o[2]
        lengths.append(rules.u16(memo))
        if "→" in memo:
            before, after = memo.split("→", 1)
            words = [w for w in re.split(r"[·,\s]+", after.strip()) if len(w) >= 2]
            if any(rules.stem(w) in before for w in words):
                gives += 1
    lengths.sort()
    print("hinted kanji:", hinted)
    for k, v in kinds.most_common(8):
        print("  %4d  %s" % (v, k))
    print("외우기 length: median %d, over 40: %d, over 45: %d" % (lengths[len(lengths) // 2],
          sum(x > 40 for x in lengths), sum(x > 45 for x in lengths)))
    print("meaning said before the arrow:", gives)


if __name__ == "__main__":
    main(*sys.argv[1:])
