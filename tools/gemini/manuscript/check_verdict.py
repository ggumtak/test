#!/usr/bin/env python3
"""Usage: check_verdict.py N — checks tools/origin/kosei/polish/verdict-N.tsv against cmp-N.tsv: one line per kanji
of the batch, winner A, B or =, and a reason. Prints `ok` or the problems."""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))


def main(n):
    batch = [line.split("\t")[0] for line in open(os.path.join(HERE, "cmp-%s.tsv" % n), encoding="utf-8")
             if line.strip() and not line.startswith("#")]
    path = os.path.join(HERE, "verdict-%s.tsv" % n)
    if not os.path.exists(path):
        print("missing", path)
        return 1
    problems, seen = [], set()
    for i, line in enumerate(open(path, encoding="utf-8"), 1):
        if not line.strip():
            continue
        c = line.rstrip("\n").split("\t")
        if len(c) < 3:
            problems.append("line %d: fewer than 3 columns" % i)
            continue
        if c[0] not in batch:
            problems.append("line %d: %s is not in cmp-%s.tsv" % (i, c[0], n))
        if c[0] in seen:
            problems.append("line %d: %s twice" % (i, c[0]))
        seen.add(c[0])
        if c[1] not in ("A", "B", "="):
            problems.append("line %d (%s): winner %s" % (i, c[0], c[1]))
        if len(c[2].strip()) < 4:
            problems.append("line %d (%s): no reason" % (i, c[0]))
    missing = [ch for ch in batch if ch not in seen]
    if missing:
        problems.append("no verdict for: " + "".join(missing))
    for p in problems[:40]:
        print(p)
    print("ok (%d verdicts)" % len(seen) if not problems else "%d problems" % len(problems))
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1]))
