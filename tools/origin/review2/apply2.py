#!/usr/bin/env python3
"""Second review: applies rev2/../fix2-N.tsv to the 자원/외우기 in rev2/in-N.tsv, checks the format with
origin/check.py, and checks rev2/../sound2-N.tsv names a top-level part of the 구성 row. Usage: apply2.py N"""
import os
import subprocess
import sys

here = os.path.dirname(os.path.abspath(__file__))
origin = os.path.dirname(here)  # tools/origin: check.py


def lines(path):
    if not os.path.exists(path):
        return []
    out = []
    for l in open(path, encoding="utf-8"):
        if l.strip() and not l.startswith("#"):
            out.append(l.rstrip("\n").split("\t"))
    return out


def main(n):
    rows = lines(os.path.join(here, f"in-{n}.tsv"))
    base = {r[0]: [r[0], r[7], r[8] if len(r) > 8 else ""] for r in rows}
    comp = {r[0]: r[5] for r in rows}
    bad = []
    fixes = {}
    for r in lines(os.path.join(here, f"fix2-{n}.tsv")):
        if len(r) == 2:
            r.append("")
        if len(r) != 3:
            bad.append("fix2 line with %d fields: %s" % (len(r), "\t".join(r)[:40]))
        elif r[0] not in base:
            bad.append("fix2 for a kanji not in this batch: " + r[0])
        else:
            fixes[r[0]] = r
    for r in lines(os.path.join(here, f"sound2-{n}.tsv")):
        if len(r) < 2 or r[0] not in base:
            bad.append("sound2 line not for a kanji of this batch: " + "\t".join(r)[:40])
            continue
        parts = [p.split("(")[0].split("{")[0] for p in comp[r[0]].split(" + ")] if comp[r[0]] else []
        if r[1] != "-" and r[1] not in parts:
            bad.append("sound2 %s: part %s is not one of the 구성 row's parts %s" % (r[0], r[1], parts))
    if bad:
        print("\n".join(bad))
        return 1
    path = os.path.join(here, f"reviewed2-{n}.tsv")
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        for r in rows:
            f.write("\t".join(x.strip() for x in fixes.get(r[0], base[r[0]])) + "\n")
    changed = sum(1 for k, v in fixes.items() if v != base[k])
    print(changed, "lines changed", flush=True)
    return subprocess.call([sys.executable, "-I", os.path.join(origin, "check.py"),
                            os.path.join(here, f"in-{n}.tsv"), path])


if __name__ == "__main__":
    sys.exit(main(sys.argv[1]))
