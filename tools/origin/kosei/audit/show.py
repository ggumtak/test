#!/usr/bin/env python3
"""Prints what the card shows now for the given kanji (구성, 유래, 외우기) and each stroke's KanjiVG part and colour,
for checking KANJI_CONTENT_AUDIT_2026-11-08.md against the current data.

Usage: show.py <kanjivg kanji dir> <kanji...>"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, ".."))
sys.path.insert(0, os.path.join(HERE, "..", "full"))
sys.path.insert(0, os.path.join(HERE, "..", "..", ".."))
import make_inputs as mi  # noqa: E402
from convert import load  # noqa: E402
from export_md import kosei_text  # noqa: E402

ASSETS = os.path.join(HERE, "..", "..", "..", "..", "app", "src", "main", "assets")


def main(kanjivg, chars):
    mi.KANJIVG = kanjivg
    kanji, parts, origin = (load(os.path.join(ASSETS, n)) for n in ("kanji.tsv", "parts.tsv", "origin.tsv"))
    for ch in chars:
        k, p, o = kanji[ch], parts[ch], origin.get(ch, [ch, "", "", ""])
        tags = [s[0] for s in p[5].split("|")] if p[5] else []
        spec = o[3] if len(o) > 3 else ""
        radical = "曰" if p[1] == "73" else p[2]
        line, orphans = mi.strokes_line(ch, tags)
        print("■ %s  %s | 음 %s | 부수 %s %s | 부품 %s" % (ch, k[10] or k[1], k[3], p[1], radical, p[4]))
        print("  구성: %s" % (kosei_text(spec, radical) if spec else "(없음)"))
        print("  저장: %s" % spec)
        print("  유래: %s" % o[1])
        print("  외우기: %s" % (o[2] if len(o) > 2 else ""))
        print("  획: %s%s" % (line, "  ※부품 밖 %s" % orphans if orphans else ""))
        print()


if __name__ == "__main__":
    main(sys.argv[1], "".join(sys.argv[2:]))
