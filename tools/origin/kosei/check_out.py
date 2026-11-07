#!/usr/bin/env python3
"""Checks out-N.tsv against in-N.tsv: python3 -I check_out.py N   (prints OK, or every problem to fix).

out-N.tsv: one line per input kanji, same order, no header:  한자<TAB>구성<TAB>새 유래 or empty<TAB>메모 or empty
When the review's fix-N.tsv exists (only the changed kanji, any order, the same four columns; an empty column keeps
out-N's, 새 유래 = 원래 goes back to the original 유래), the lines are checked as fixed.
"""
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import kosei  # noqa: E402
from convert import load  # noqa: E402

ASSETS = os.path.join(HERE, "..", "..", "..", "app", "src", "main", "assets")
TYPES = {"상형", "지사", "회의", "형성", "회의 겸 형성", "가차", "국자", "생략형", "불명"}
BODY_JARGON = ["신자체", "정자", "說文", "설문", "갑골", "금문", "본자", "이체", "속자", "가차", "생략형", "회의", "형성",
               "상형", "지사", "소리 겸 뜻", "설이 갈림"]


def han(s):
    return {c for c in s if 0x2E80 <= ord(c) <= 0x2FDF or 0x3400 <= ord(c) <= 0x9FFF or ord(c) >= 0x20000}


def main(n):
    parts = load(os.path.join(ASSETS, "parts.tsv"))
    rows = [l.rstrip("\n").split("\t") for l in open(os.path.join(HERE, "in-%s.tsv" % n), encoding="utf-8")
            if l.strip() and not l.startswith("#")]
    path = os.path.join(HERE, "out-%s.tsv" % n)
    if not os.path.exists(path):
        print("out-%s.tsv 없음" % n)
        return 1
    out = [l.rstrip("\n").split("\t") for l in open(path, encoding="utf-8") if l.strip()]
    problems, notes = [], []
    fix_path = os.path.join(HERE, "fix-%s.tsv" % n)
    if os.path.exists(fix_path):
        at = {o[0]: i for i, o in enumerate(out)}
        for line in open(fix_path, encoding="utf-8"):
            if not line.strip():
                continue
            f = line.rstrip("\n").split("\t") + ["", "", ""]
            if f[0] not in at:
                problems.append("fix: %s는 이 묶음에 없음" % f[0])
                continue
            o = out[at[f[0]]] + [""] * 3
            o = o[:4]
            for c in (1, 2, 3):
                if f[c].strip():
                    o[c] = "" if (c == 2 and f[c].strip() == "원래") else f[c]
            out[at[f[0]]] = o
    if len(out) != len(rows):
        problems.append("줄 수 %d, 입력은 %d" % (len(out), len(rows)))
    for i, row in enumerate(rows):
        ch, old = row[0], row[6]
        if i >= len(out):
            break
        o = out[i] + [""] * (4 - len(out[i]))
        if o[0] != ch:
            problems.append("%d번째 줄: %s 자리에 %s" % (i + 1, ch, o[0]))
            continue
        if len(out[i]) > 4:
            problems.append("%s: 칸이 4개보다 많음" % ch)
        spec, new, memo = o[1].strip(), o[2].strip(), o[3].strip()
        p = parts[ch]
        plist = [x.split("/") + [""] * (4 - len(x.split("/"))) for x in p[4].split()]
        errors, warnings = kosei.check(spec, plist, memo)
        problems += ["%s: %s" % (ch, e) for e in errors]
        notes += ["%s: %s" % (ch, w) for w in warnings]
        text = new or old
        if new:
            t = new.split(":", 1)[0] if ":" in new[:10] else ""
            ot = old.split(":", 1)[0] if ":" in old[:10] else ""
            if t != ot or t not in TYPES:
                problems.append("%s: 새 유래의 유형 '%s'가 원래 '%s'와 다름" % (ch, t, ot))
            if len(new) > 120:
                problems.append("%s: 새 유래가 김(%d자 > 120)" % (ch, len(new)))
            body = new.split(":", 1)[1] if ":" in new[:10] else new
            for w in BODY_JARGON:
                if w in body:
                    problems.append("%s: 새 유래에 어려운 말 '%s'" % (ch, w))
            if new == old:
                problems.append("%s: 새 유래가 원래와 같음(바꾸지 않으면 비워 둘 것)" % ch)
        # The link: every unit is named in the 유래 by its shape, or by a word of its note.
        if not errors and not spec.startswith("!"):
            for pieces, role, reading, note in kosei.parse(spec):
                words = [w for w in re.split(r"[\s,·+]+", note) if len(w) >= 2 and not han(w)]
                if not (set("".join(pieces)) & han(text)) and not any(w[:2] in text for w in words):
                    notes.append("%s: '%s'가 유래에 보이지 않음(연결 확인)" % (ch, "+".join(pieces)))
    for w in notes:
        print("참고 " + w)
    if problems:
        for e in problems:
            print("고칠 것 " + e)
        return 1
    print("OK (%d줄, 참고 %d건)" % (len(out), len(notes)))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1]))
