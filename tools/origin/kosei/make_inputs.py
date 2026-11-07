#!/usr/bin/env python3
"""Writes in-N.tsv for the kanji whose 구성 line is written by hand (convert.py's leftovers and the scope list).

Usage: make_inputs.py <assets dir> <kanji list file> <out dir> <batches>
Columns: 한자 · 훈음 · 음독 · 부수 · 부품(그림 색) · 지금 구성 줄 · 유래 · 외우기
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from convert import load, first_hun  # noqa: E402

COLORS = {"r": "빨강=부수", "p": "파랑=소리", "R": "일부 빨강(안에 부수 %s)", "P": "일부 파랑(안에 소리 %s)"}


def describe(part, form, kanji):
    el, orig, flags, inner = part
    name = el + ("(원형 %s)" % orig if orig and "+" not in orig else "") + \
        ("(조각 %s)" % orig if "+" in orig else "")
    h = first_hun(kanji.get(el)) or first_hun(kanji.get(orig))
    names = {"R": form or "?", "P": inner or "?"}
    color = ", ".join(COLORS[f] % names[f] if f in names else COLORS[f] for f in flags if f in COLORS) or "검정"
    if "r" in flags and "p" in flags:
        color += " (그림에는 빨강으로 칠함)"
    return "%s[%s]{%s}" % (name, h or "-", color)


def main(assets, list_path, out_dir, batches):
    kanji = load(os.path.join(assets, "kanji.tsv"))
    parts = load(os.path.join(assets, "parts.tsv"))
    origin = load(os.path.join(assets, "origin.tsv"))
    rads = {}
    for line in open(os.path.join(assets, "radicals.tsv"), encoding="utf-8"):
        if line.strip() and not line.startswith("#"):
            f = line.rstrip("\n").split("\t")
            rads.setdefault(f[0], []).append(f)
    wanted = [l.strip() for l in open(list_path, encoding="utf-8") if l.strip()]
    order = [c for c in kanji if c in set(wanted)]
    n = int(batches)
    size = -(-len(order) // n)
    for b in range(n):
        rows = order[b * size:(b + 1) * size]
        with open(os.path.join(out_dir, "in-%d.tsv" % (b + 1)), "w", encoding="utf-8") as f:
            f.write("#한자\t훈음\t음독\t부수\t부품(그림 색)\t지금 구성 줄\t유래\t외우기\n")
            for c in rows:
                k, p, o = kanji[c], parts[c], origin[c]
                h = k[10] if len(k) > 10 and k[10] else k[1]
                form = p[2]
                rl = rads.get(p[1], [])
                rname = next((r[4] for r in rl if r[1] == form), rl[0][4] if rl else "")
                plist = [x.split("/") + [""] * (4 - len(x.split("/"))) for x in p[4].split()]
                f.write("\t".join([c, h, k[3], "%s %s %s" % (p[1], form, rname),
                                   " + ".join(describe(x, form, kanji) for x in plist), old_line(plist, form, kanji, rl),
                                   o[1], o[2] if len(o) > 2 else ""]) + "\n")
        print("in-%d.tsv" % (b + 1), len(rows))


def old_line(plist, form, kanji, rl):
    """The 구성 line as the app showed it before: part(its 훈음, or the radical's meaning · 소리)."""
    out = []
    for el, orig, flags, inner in plist:
        if "r" in flags:
            g = next((r[5] or r[4] for r in rl if r[1] == form and len(r) > 5), "") or (rl[0][4] if rl else "")
        else:
            g = first_hun(kanji.get(el)) or first_hun(kanji.get(orig))
        notes = [x for x in [g, "부수" if "r" in flags and "p" in flags else "", "소리" if "p" in flags else ""] if x]
        out.append(el + ("(%s)" % " · ".join(notes) if notes else ""))
    return " + ".join(out)


if __name__ == "__main__":
    main(*sys.argv[1:5])
