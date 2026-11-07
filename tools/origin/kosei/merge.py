#!/usr/bin/env python3
"""The last step of tools/origin/merge.py: the 구성 line written to match the 유래 (BRIEF.md), as origin.tsv's
fourth column, and the 유래 lines changed to make that link.

Sources, later ones winning: auto.tsv (convert.py: kanji whose 유래 already names every part plainly), out-N.tsv
(written by hand from in-N.tsv following BRIEF.md: 한자, 구성, 새 유래 or empty, 메모), fix-N.tsv (the independent
review, REVIEW.md: the same four columns for the lines it changed) and fix-final.tsv (decided by hand after the
review). Each unit gets the mark the app colours by: ^ for the radical's part, ~ for a part holding the radical,
found by matching the units to parts.tsv (kosei.match); free lines ("!") get none.
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import kosei  # noqa: E402

ASSETS = os.path.join(HERE, "..", "..", "..", "app", "src", "main", "assets")


def read(path, width):
    out = []
    if os.path.exists(path):
        for line in open(path, encoding="utf-8"):
            if line.strip() and not line.startswith("#"):
                f = line.rstrip("\n").split("\t")
                out.append([x.strip() for x in f] + [""] * (width - len(f)))
    return out


def marked(spec, plist, form=""):
    """The spec with ^ / ~ in front of the units holding the radical's part, or None if it does not fit the parts.
    A free line ("!") marks the unit written as the radical's shape [form] (祭's 示)."""
    if spec.startswith("!"):
        kosei.parse(spec[1:])
        raw = [u.strip() for u in spec[1:].split(" + ")]
        return "!" + " + ".join(("^" if form and u.split("[")[0].split("(")[0].strip() == form else "") + u
                                for u in raw)
    units = kosei.parse(spec)
    cover = kosei.match(units, plist)
    if cover is None:
        return None
    raw = [u.strip() for u in spec.split(" + ")]
    out = []
    for text, idx in zip(raw, cover):
        flags = "".join(plist[i][2] for i in idx)
        out.append(("^" if "r" in flags else "~" if "R" in flags else "") + text)
    return " + ".join(out)


def apply(merged, fix_particles):
    """Adds the 구성 column to merged {kanji: [kanji, 유래, 외우기]} and puts in the changed 유래 lines."""
    parts, forms = {}, {}
    for line in open(os.path.join(ASSETS, "parts.tsv"), encoding="utf-8"):
        f = line.rstrip("\n").split("\t")
        parts[f[0]] = [x.split("/") + [""] * (4 - len(x.split("/"))) for x in f[4].split()] if len(f) > 4 else []
        forms[f[0]] = f[2] if len(f) > 2 else ""
    spec, origin = {}, {}
    for r in read(os.path.join(HERE, "auto.tsv"), 2):
        spec[r[0]] = r[1]
    # Every batch there is, in order (not stopping at a missing number).
    batches = sorted(int(f[4:-4]) for f in os.listdir(HERE) if f.startswith("out-") and f.endswith(".tsv"))
    for n in batches:
        for name in ("out-%d.tsv" % n, "fix-%d.tsv" % n):
            for r in read(os.path.join(HERE, name), 4):
                if r[1]:
                    spec[r[0]] = r[1]
                if r[2] == "원래":
                    # The review put the original 유래 back.
                    origin.pop(r[0], None)
                elif r[2]:
                    origin[r[0]] = r[2]
    for r in read(os.path.join(HERE, "fix-final.tsv"), 4):
        if r[1]:
            spec[r[0]] = r[1]
        if r[2]:
            origin[r[0]] = r[2]
    bad = []
    for ch, row in merged.items():
        if ch in origin:
            row[1] = fix_particles(origin[ch])
        s = spec.get(ch, "")
        m = marked(s, parts.get(ch, []), forms.get(ch, "")) if s else ""
        if m is None:
            bad.append(ch)
            m = ""
        merged[ch] = row[:3] + [m]
    print(sum(1 for r in merged.values() if r[3]), "구성 lines,", len(origin), "유래 changed to link,",
          len(bad), "not fitting the parts:", "".join(bad))
    return merged
