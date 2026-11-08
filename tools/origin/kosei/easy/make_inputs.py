#!/usr/bin/env python3
"""Writes the plain-words pass inputs (easy/BRIEF.md): list-N.txt (the kanji of batch N) and in-N.txt (for each
상용한자 what the card shows: 훈음, 부수, 구성 as shown and as written, 유래, 외우기).

Usage: make_inputs.py <batches>"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, ".."))
from convert import load  # noqa: E402
from export_md import kosei_text, find_radical  # noqa: E402

ASSETS = os.path.join(HERE, "..", "..", "..", "..", "app", "src", "main", "assets")


def input_spec(spec):
    """The 구성 line as a fix file writes it: no ^ (merge.py adds it), "~" kept only on free lines."""
    if not spec or spec == "-":
        return spec
    free = spec.startswith("!")
    units = [u.strip() for u in spec.lstrip("!").split(" + ")]
    units = [u.lstrip("^") if free else u.lstrip("^~") for u in units]
    return ("!" if free else "") + " + ".join(units)


def main(batches):
    kanji, parts, origin = (load(os.path.join(ASSETS, n)) for n in ("kanji.tsv", "parts.tsv", "origin.tsv"))
    rads = {}
    for line in open(os.path.join(ASSETS, "radicals.tsv"), encoding="utf-8"):
        if line.strip() and not line.startswith("#"):
            f = line.rstrip("\n").split("\t")
            rads.setdefault(f[0], []).append(f)
    joyo = [c for c, f in kanji.items() if len(f) > 6 and f[6].isdigit() and 1 <= int(f[6]) <= 8]
    n = int(batches)
    size = -(-len(joyo) // n)
    for b in range(n):
        chunk = joyo[b * size:(b + 1) * size]
        with open(os.path.join(HERE, "list-%d.txt" % (b + 1)), "w", encoding="utf-8") as f:
            f.write("\n".join(chunk) + "\n")
        out = []
        for ch in chunk:
            k, p, o = kanji[ch], parts[ch], origin[ch]
            radical = "曰" if p[1] == "73" else p[2]
            r = find_radical(rads.get(p[1], []), p[2], p[3] if len(p) > 3 else "")
            rname = "%s %s(%s)" % (radical, r[4], r[3]) if r else radical
            spec = o[3] if len(o) > 3 else ""
            out.append("\n".join([
                "■ %s  훈음 %s | 음 %s | 부수 %s" % (ch, k[10] or k[1], k[3], rname),
                "  구성(카드): %s" % (kosei_text(spec, radical) if spec and spec != "-" else "(없음)"),
                "  구성(쓰는 꼴): %s" % (input_spec(spec) or "(없음)"),
                "  유래: %s" % o[1],
                "  외우기: %s" % o[2],
            ]))
        with open(os.path.join(HERE, "in-%d.txt" % (b + 1)), "w", encoding="utf-8") as f:
            f.write("\n\n".join(out) + "\n")
        print("batch", b + 1, len(chunk), "kanji")


if __name__ == "__main__":
    main(sys.argv[1])
