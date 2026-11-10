#!/usr/bin/env python3
"""Usage: check_grade.py N — checks tools/origin/kosei/polish/grade-N.tsv against in-N.tsv: every line names a
kanji of the batch once, with known codes, severity 1 or 2 and a reason. Prints `ok` or the problems."""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
CODES = {"W1", "W2", "W3", "W4", "W5", "W6", "W7"}


def main(n):
    batch = set()
    with open(os.path.join(HERE, "in-%s.tsv" % n), encoding="utf-8") as f:
        for line in f:
            if not line.startswith("#") and line.strip():
                batch.add(line.split("\t")[0])
    path = os.path.join(HERE, "grade-%s.tsv" % n)
    if not os.path.exists(path):
        print("missing", path)
        return 1
    problems, seen = [], set()
    with open(path, encoding="utf-8") as f:
        for i, line in enumerate(f, 1):
            if not line.strip():
                continue
            c = line.rstrip("\n").split("\t")
            if len(c) != 4:
                problems.append("line %d: %d columns instead of 4" % (i, len(c)))
                continue
            if c[0] not in batch:
                problems.append("line %d: %s is not in in-%s.tsv" % (i, c[0], n))
            if c[0] in seen:
                problems.append("line %d: %s twice" % (i, c[0]))
            seen.add(c[0])
            codes = [x.strip() for x in c[1].split(",")]
            if not codes or any(x not in CODES for x in codes):
                problems.append("line %d (%s): unknown code in %s" % (i, c[0], c[1]))
            if c[2] not in ("1", "2"):
                problems.append("line %d (%s): severity %s" % (i, c[0], c[2]))
            if len(c[3].strip()) < 6:
                problems.append("line %d (%s): the reason is too short" % (i, c[0]))
    for p in problems[:40]:
        print(p)
    print("ok (%d of %d flagged)" % (len(seen), len(batch)) if not problems else "%d problems" % len(problems))
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1]))
