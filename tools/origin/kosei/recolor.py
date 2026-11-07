#!/usr/bin/env python3
"""Makes the stroke drawing's sound colour (blue) agree with the 구성 lines written to match the 유래.

For each 상용한자 with a 구성 line in origin.tsv (not a free "!" one): the strokes of a part whose unit has a 소리
role become blue, and those of a part whose unit is plainly 뜻 / 모양 / 기호 stop being blue; the radical's
strokes stay red (a part both radical and sound is drawn red). The result goes into tools/stroke_tags.tsv, which
build_parts_data.py applies, so rebuilding parts.tsv carries it.

Usage: recolor.py <kanjivg kanji dir> <jamdict.db> <unihan.db>   (the same sources as build_parts_data.py)
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
TOOLS = os.path.join(HERE, "..", "..")
sys.path.insert(0, HERE)
sys.path.insert(0, TOOLS)
import kosei  # noqa: E402
import build_parts_data as bp  # noqa: E402

ASSETS = os.path.join(TOOLS, "..", "app", "src", "main", "assets")
TAGS = os.path.join(TOOLS, "stroke_tags.tsv")
REASON = "구성 줄(유래)의 소리 역할과 맞춤 (tools/origin/kosei/recolor.py)"


def main(kanjivg, jamdict, unihan):
    forms = bp.load_forms(os.path.join(ASSETS, "radicals.tsv"))
    sound_fix = bp.load_sound_fix(os.path.join(TOOLS, "origin", "sound.tsv"))
    rad = bp.load_radicals(jamdict, unihan)
    tag_fix = bp.load_tag_fix(TAGS)
    parts = {}
    for line in open(os.path.join(ASSETS, "parts.tsv"), encoding="utf-8"):
        f = line.rstrip("\n").split("\t")
        parts[f[0]] = f
    changed = {}
    for line in open(os.path.join(ASSETS, "origin.tsv"), encoding="utf-8"):
        f = line.rstrip("\n").split("\t")
        ch, spec = f[0], (f[3] if len(f) > 3 else "")
        if not spec or spec.startswith("!"):
            continue
        p = parts[ch]
        plist = [x.split("/") + [""] * (4 - len(x.split("/"))) for x in p[4].split()]
        tags = [s[0] for s in p[5].split("|")]
        clean = spec.replace("^", "").replace("~", "")
        units = kosei.parse(clean)
        cover = kosei.match(units, plist)
        if cover is None:
            print("not fitting the parts:", ch)
            continue
        # Each part's strokes, from KanjiVG as the build script splits it (same order as parts.tsv's parts).
        svg = os.path.join(kanjivg, "%05x.svg" % ord(ch))
        _, _, kvg_parts, _, _ = bp.build_one(ch, rad.get(ch, 0), svg, forms, sound_fix.get(ch))
        expanded = []
        for kp in kvg_parts:
            mine = [tags[i] for i in sorted(kp[3]) if i < len(tags)]
            # A part put together from pieces that the build split again (no blue in it): its pieces.
            if kp[5] and "p" not in mine and len(plist) != len(kvg_parts):
                expanded.extend(kp[5])
            else:
                expanded.append(kp)
        if len(expanded) != len(plist):
            print("parts differ from KanjiVG:", ch)
            continue
        new = list(tags)
        for (pieces, role, reading, note), idx in zip(units, cover):
            for k in idx:
                for s in expanded[k][3]:
                    if s >= len(new) or new[s] == "r":
                        continue
                    if "소리" in role:
                        new[s] = "p"
                    elif role in ("뜻", "모양", "기호") and new[s] == "p":
                        new[s] = "-"
        if new != tags:
            changed[ch] = "".join(new)
    # Rewrite stroke_tags.tsv: its comments and other lines as they are, these kanji replaced or added.
    lines = open(TAGS, encoding="utf-8").read().splitlines()
    out, seen = [], set()
    for line in lines:
        f = line.split("\t")
        if f and f[0] in changed and not line.startswith("#"):
            out.append("%s\t%s\t%s" % (f[0], changed[f[0]], REASON))
            seen.add(f[0])
        else:
            out.append(line)
    for ch in changed:
        if ch not in seen:
            out.append("%s\t%s\t%s" % (ch, changed[ch], REASON))
    with open(TAGS, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(out) + "\n")
    print(len(changed), "kanji recoloured:", "".join(changed))


if __name__ == "__main__":
    main(*sys.argv[1:4])
