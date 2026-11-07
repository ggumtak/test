#!/usr/bin/env python3
"""Shows what the card has for kanji: python3 -I show.py 勇 書 …
Its 훈음 and readings, radical, 구성 line as stored (origin.tsv column 4) and as the card shows it, 유래, 외우기, and
each stroke in writing order with its colour (r red = radical, p blue = sound, - black) and the KanjiVG groups it
sits in, so a 구성 line can be checked against what the drawing shows."""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, ".."))
sys.path.insert(0, os.path.join(HERE, "..", "..", ".."))
import build_parts_data as bp  # noqa: E402
from convert import load  # noqa: E402
from export_md import kosei_text  # noqa: E402

ASSETS = os.path.join(HERE, "..", "..", "..", "..", "app", "src", "main", "assets")
KANJIVG = "/tmp/claude-0/-home-user-test/1560e2f5-85e5-5883-ad85-16a26bf44f74/scratchpad/src/kanjivg/kanji"


def main(chars):
    kanji, parts, origin = load(os.path.join(ASSETS, "kanji.tsv")), load(os.path.join(ASSETS, "parts.tsv")), \
        load(os.path.join(ASSETS, "origin.tsv"))
    for ch in "".join(chars):
        k, p, o = kanji[ch], parts[ch], origin.get(ch, [ch, "", "", ""])
        print("=" * 60)
        print(ch, "| 훈음", k[10] or k[1], "| 음", k[3], "| 훈", k[4], "| 획수", k[5])
        print("부수 번호/모양:", p[1], p[2], "| parts.tsv 부품:", p[4], "| 소리 계열:", p[6])
        spec = o[3] if len(o) > 3 else ""
        print("구성(저장):", spec)
        print("구성(화면):", kosei_text(spec, "曰" if p[1] == "73" else p[2]) if spec else "(없음)")
        print("유래:", o[1])
        print("외우기:", o[2] if len(o) > 2 else "")
        tags = [s[0] for s in p[5].split("|")] if p[5] else []
        svg = os.path.join(KANJIVG, "%05x.svg" % ord(ch))
        if os.path.exists(svg):
            top = bp.parse_svg(svg)
            paths, groups = [], []
            bp.walk(top, [top], paths, groups)
            for i, (d, ps) in enumerate(paths):
                names = " > ".join((g.get(bp.KVG + "element") or "·") + ("(소리표시)" if g.get(bp.KVG + "phon") else "")
                                   for g in ps[1:])
                print("  획 %2d  %s  %s" % (i + 1, tags[i] if i < len(tags) else "?", names))


if __name__ == "__main__":
    main(sys.argv[1:])
