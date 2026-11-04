#!/usr/bin/env python3
"""Checks an origin output file against its input: same kanji in the same order, three fields, the type prefix,
the length limits. Usage: check.py in-N.tsv out-N.tsv"""
import sys

TYPES = ("상형:", "지사:", "회의:", "형성:", "회의 겸 형성:", "가차:", "국자:", "생략형:", "불명:")


def main(inp, out):
    want = [l.split("\t", 1)[0] for l in open(inp, encoding="utf-8") if l.strip() and not l.startswith("#")]
    try:
        got = [l.rstrip("\n") for l in open(out, encoding="utf-8") if l.strip()]
    except FileNotFoundError:
        print("missing", out)
        return 1
    errs = []
    if len(got) != len(want):
        errs.append("line count %d, expected %d" % (len(got), len(want)))
    for i, line in enumerate(got):
        f = line.split("\t")
        if len(f) != 3:
            errs.append("line %d: %d fields (need 3): %s" % (i + 1, len(f), line[:40]))
            continue
        ch, origin, memo = f
        if i < len(want) and ch != want[i]:
            errs.append("line %d: kanji %s, expected %s" % (i + 1, ch, want[i]))
        if not origin.startswith(TYPES):
            errs.append("line %d %s: 자원 must start with a type: %s" % (i + 1, ch, origin[:20]))
        if len(origin) > 90:
            errs.append("line %d %s: 자원 %d chars (max 80, hard limit 90)" % (i + 1, ch, len(origin)))
        if len(memo) > 50:
            errs.append("line %d %s: 외우기 %d chars (max 45, hard limit 50)" % (i + 1, ch, len(memo)))
        if memo and "→" not in memo:
            errs.append("line %d %s: 외우기 should end with '→ 뜻'" % (i + 1, ch))
        if any(x in line for x in ("**", "##", "`")):
            errs.append("line %d %s: no Markdown" % (i + 1, ch))
    if errs:
        print("\n".join(errs[:80]))
        print(len(errs), "problems")
        return 1
    print("OK", len(got), "lines")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1], sys.argv[2]))
