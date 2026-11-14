#!/usr/bin/env python3
"""Usage: check_judged.py N — checks judged-N.tsv against review-N.tsv: one line per kanji, ok/no/- per field, a
reason for every no, and - exactly where the field did not change."""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))


def main(n):
    rows = {}
    for line in open(os.path.join(HERE, "review-%s.tsv" % n), encoding="utf-8"):
        if line.startswith("#") or not line.strip():
            continue
        c = line.rstrip("\n").split("\t")
        rows[c[0]] = (bool(c[3].strip()), bool(c[5].strip()) if len(c) > 5 else False)
    path = os.path.join(HERE, "judged-%s.tsv" % n)
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
        ch = c[0]
        if ch not in rows:
            problems.append("line %d: %s is not in review-%s.tsv" % (i, ch, n))
            continue
        if ch in seen:
            problems.append("line %d: %s twice" % (i, ch))
        seen.add(ch)
        names, origin = rows[ch]
        for v, has, what in ((c[1], origin, "유래"), (c[2], names, "구성")):
            if v not in ("ok", "no", "-"):
                problems.append("%s: %s is '%s'" % (ch, what, v))
            elif (v == "-") == has:
                problems.append("%s: %s should be %s" % (ch, what, "ok or no" if has else "-"))
        if "no" in (c[1], c[2]) and (len(c) < 4 or len(c[3].strip()) < 4):
            problems.append("%s: no reason for a no" % ch)
    missing = [ch for ch in rows if ch not in seen]
    if missing:
        problems.append("no verdict for: " + "".join(missing))
    for p in problems[:40]:
        print(p)
    print("ok (%d rows)" % len(seen) if not problems else "%d problems" % len(problems))
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1]))
