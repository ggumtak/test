#!/usr/bin/env python3
"""Usage: check.py N — checks tools/usage/out-N.tsv (and fix-N.tsv when it exists) against in-N.tsv: every input
line answered in order, the word and reading copied exactly, and a meaning in the allowed form. Prints `ok` or
the problems."""
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
# Hangul, spaces, digits and , ( ) ~ · only; no kanji, kana or Latin letters.
ALLOWED = re.compile(r"^[가-힣0-9 ,()~·]+$")
MAX_LEN = 20


def rows(path, header=False):
    out = []
    with open(path, encoding="utf-8") as f:
        for line in f:
            if header and line.startswith("#"):
                continue
            if line.strip():
                out.append(line.rstrip("\n").split("\t"))
    return out


def bad_meaning(m):
    if not m.strip():
        return "empty meaning"
    if m != m.strip():
        return "spaces around the meaning"
    if not ALLOWED.match(m):
        return "characters other than Hangul, spaces, digits and , ( ) ~ ·"
    if len(m) > MAX_LEN:
        return "longer than %d characters" % MAX_LEN
    if m.count(", ") > 1:
        return "more than two senses"
    if m.count("(") != m.count(")"):
        return "unbalanced parentheses"
    return None


def main(n):
    problems = []
    src = rows(os.path.join(HERE, "in-%s.tsv" % n), header=True)
    out_path = os.path.join(HERE, "out-%s.tsv" % n)
    if not os.path.exists(out_path):
        print("missing", out_path)
        return 1
    out = rows(out_path)
    if len(out) != len(src):
        problems.append("out-%s.tsv has %d lines, in-%s.tsv has %d" % (n, len(out), n, len(src)))
    for i, (a, b) in enumerate(zip(src, out), 1):
        if len(b) != 3:
            problems.append("line %d: %d columns instead of 3: %s" % (i, len(b), "\t".join(b)[:60]))
            continue
        if b[0] != a[0] or b[1] != a[1]:
            problems.append("line %d: expected %s %s, found %s %s" % (i, a[0], a[1], b[0], b[1]))
            continue
        why = bad_meaning(b[2])
        if why:
            problems.append("line %d (%s %s): %s: %s" % (i, b[0], b[1], why, b[2]))
    fix_path = os.path.join(HERE, "fix-%s.tsv" % n)
    if os.path.exists(fix_path):
        known = {(a[0], a[1]) for a in src}
        for i, b in enumerate(rows(fix_path), 1):
            if len(b) < 3:
                problems.append("fix line %d: fewer than 3 columns" % i)
                continue
            if (b[0], b[1]) not in known:
                problems.append("fix line %d: %s %s is not in in-%s.tsv" % (i, b[0], b[1], n))
            why = bad_meaning(b[2])
            if why:
                problems.append("fix line %d (%s %s): %s: %s" % (i, b[0], b[1], why, b[2]))
    for p in problems[:60]:
        print(p)
    if len(problems) > 60:
        print("… and %d more" % (len(problems) - 60))
    print("ok" if not problems else "%d problems" % len(problems))
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1]))
