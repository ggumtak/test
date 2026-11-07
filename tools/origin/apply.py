#!/usr/bin/env python3
"""Applies fix-N.tsv to out-N.tsv into reviewed-N.tsv and runs check.py on it. Usage: apply.py N"""
import os
import subprocess
import sys

here = os.path.dirname(os.path.abspath(__file__))


def rows(path):
    if not os.path.exists(path):
        return []
    out = []
    for l in open(path, encoding="utf-8"):
        if not l.strip():
            continue
        f = l.rstrip("\n").split("\t")
        # An empty 외우기 loses its trailing tab in some editors.
        out.append(f + [""] if len(f) == 2 else f)
    return out


def main(n):
    out = rows(os.path.join(here, f"out-{n}.tsv"))
    fixes = {}
    bad = []
    for r in rows(os.path.join(here, f"fix-{n}.tsv")):
        if len(r) != 3:
            bad.append("fix line with %d fields: %s" % (len(r), "\t".join(r)[:40]))
            continue
        fixes[r[0]] = r
    known = {r[0] for r in out}
    bad += ["fix for a kanji not in out-%s.tsv: %s" % (n, k) for k in fixes if k not in known]
    if bad:
        print("\n".join(bad))
        return 1
    merged = [fixes.get(r[0], r) for r in out]
    path = os.path.join(here, f"reviewed-{n}.tsv")
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        for r in merged:
            f.write("\t".join(x.strip() for x in r) + "\n")
    changed = sum(1 for r in out if r[0] in fixes and fixes[r[0]] != r)
    print(changed, "lines changed")
    return subprocess.call([sys.executable, "-I", os.path.join(here, "check.py"),
                            os.path.join(here, f"in-{n}.tsv"), path])


if __name__ == "__main__":
    sys.exit(main(sys.argv[1]))
