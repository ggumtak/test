#!/usr/bin/env python3
"""Checks plain-N.tsv against in-N.tsv: same kanji and order, the type prefix kept, length, no jargon left.
Usage: check_plain.py N [file]   (file defaults to plain-N.tsv; a fix file can be checked the same way)"""
import os
import sys

here = os.path.dirname(os.path.abspath(__file__))
JARGON = ["신자체", "정자", "說文", "설문", "갑골", "금문", "본자", "고자", "이체", "속자", "약자", "가차", "생략형", "新附",
          "康熙", "集韻", "籀文", "주문", "전서", "소전", "고문", "회의", "형성", "상형", "지사", "소리 겸 뜻", "설이 갈림"]


def main(n, path=None):
    src = [l.rstrip("\n").split("\t") for l in open(os.path.join(here, f"in-{n}.tsv"), encoding="utf-8")
           if l.strip() and not l.startswith("#")]
    path = path or os.path.join(here, f"plain-{n}.tsv")
    if not os.path.exists(path):
        print("missing", path)
        return 1
    got = [l.rstrip("\n").split("\t") for l in open(path, encoding="utf-8") if l.strip()]
    whole = os.path.basename(path).startswith("plain-")
    errs = []
    if whole and len(got) != len(src):
        errs.append("line count %d, expected %d" % (len(got), len(src)))
    want = {r[0]: r for r in src}
    order = [r[0] for r in src]
    for i, g in enumerate(got):
        if len(g) != 2:
            errs.append("line %d: %d fields (need 2: kanji, note)" % (i + 1, len(g)))
            continue
        ch, note = g
        if ch not in want:
            errs.append("line %d: %s is not in in-%s.tsv" % (i + 1, ch, n))
            continue
        if whole and i < len(order) and ch != order[i]:
            errs.append("line %d: %s, expected %s" % (i + 1, ch, order[i]))
        old_type = want[ch][2].split(":", 1)[0]
        if ":" not in note or note.split(":", 1)[0] != old_type:
            errs.append("%s: type prefix must stay '%s:'" % (ch, old_type))
            continue
        body = note.split(":", 1)[1].strip()
        if not body:
            errs.append("%s: empty body" % ch)
        if len(note) > 120:
            errs.append("%s: %d characters (max 100, hard limit 120)" % (ch, len(note)))
        left = [t for t in JARGON if t in body]
        if left:
            errs.append("%s: jargon left in the body: %s" % (ch, ", ".join(left)))
        if any(x in note for x in ("**", "`", "##")):
            errs.append("%s: no Markdown" % ch)
    if errs:
        print("\n".join(errs[:100]))
        print(len(errs), "problems")
        return 1
    print("OK", len(got), "lines")
    return 0


if __name__ == "__main__":
    sys.exit(main(*sys.argv[1:3]))
