#!/usr/bin/env python3
"""Usage: check_return.py <returned.txt> [--accept] — checks the rewritten 외우기 lines an outside writer sent back
(`한자 | 새 외우기 | 이유` per line, or tab separated) against the card's rules, and prints for each line `ok`, the warnings to look at, or the
errors that reject it. Lines that pass go to stdout's last block as link-layer rows (한자, 구성, 유래, 외우기, 이유) with
`--accept`, ready for tools/origin/kosei/link/link-polish.tsv after a human read.

Errors (the line cannot be used):
  - the kanji was not in the request (requested.txt), or is given twice
  - not `장면 → 뜻`, or the meaning after the arrow differs from the current line's
  - longer than 50 characters (Kotlin's count), a kanji outside parentheses, a line break
  - a shape in parentheses that is neither in the kanji's 구성 line, 유래 or current 외우기 nor the kanji itself
Warnings (a human decides):
  - longer than 45 characters
  - a named part of the 구성 line is missing from the story
  - a part is called by another word than the 구성 line gives it (inflection aside)
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common import HERE, MEMO_MAX, MEMO_SOFT, PAIR, SHAPE_NOTE, is_han, load, radical_words, related, u16, units  # noqa: E402


def check(ch, new, row, rad):
    errors, warnings = [], []
    if "\n" in new or "\t" in new:
        errors.append("줄바꿈·탭이 있음")
    if new.count("→") != 1:
        errors.append("'장면 → 뜻' 꼴이 아님")
    else:
        old_meaning = row["memo"].split("→")[-1].strip()
        if new.split("→")[-1].strip() != old_meaning:
            errors.append("→ 뒤의 뜻이 지금 줄과 다름(지금: %s)" % old_meaning)
    if u16(new) > MEMO_MAX:
        errors.append("%d자: 50자를 넘음" % u16(new))
    elif u16(new) > MEMO_SOFT:
        warnings.append("%d자: 45자를 넘음" % u16(new))
    depth, outside = 0, []
    for c in new:
        if c == "(":
            depth += 1
        elif c == ")":
            depth = max(0, depth - 1)
        elif depth == 0 and is_han(c):
            outside.append(c)
    if outside:
        errors.append("괄호 밖 한자: %s" % "".join(outside))
    us = units(row["spec"])
    known = {c for c in row["spec"] + row["raw"] + row["memo"] + ch if is_han(c)}
    pairs = PAIR.findall(new)
    used = set()
    for name, shape in pairs:
        strange = [c for c in shape if is_han(c) and c not in known]
        if strange:
            errors.append("이 글자에 없는 모양: %s(%s)" % (name, shape))
        used.update(c for c in shape if is_han(c))
        for s, role, _, note in us:
            if shape == s and note and not SHAPE_NOTE.search(note):
                if not related(name, [note] + sorted(rad.get(s, ()))):
                    warnings.append("%s을 '%s'라 부름(구성: %s)" % (s, name, note))
    for s, role, _, note in us:
        pieces = [c for c in s if is_han(c)]
        if pieces and not any(c in used for c in pieces):
            warnings.append("구성의 %s(%s)이 이야기에 없음" % (s, note or role))
    return errors, warnings


def main(argv):
    accept = "--accept" in argv
    path = [a for a in argv if not a.startswith("--")][0]
    rows, rad = load(), radical_words()
    asked_path = os.path.join(HERE, "requested.txt")
    asked = set(open(asked_path, encoding="utf-8").read().split()) if os.path.exists(asked_path) else set(rows)
    seen, good, n_err, n_warn = set(), [], 0, 0
    for i, line in enumerate(open(path, encoding="utf-8"), 1):
        line = line.rstrip("\n")
        if not line.strip() or line.startswith("#") or line.startswith("```"):
            continue
        # Tabs, or " | " as the request asks for (a chat window often loses tabs).
        c = [x.strip() for x in (line.split("\t") if "\t" in line else line.strip().strip("|").split("|"))]
        if c[0] == "한자" or set(c[0]) <= set("-: "):
            continue
        if len(c) < 2:
            print("%d행: 탭으로 나뉜 칸이 2개 미만: %s" % (i, line[:40]))
            n_err += 1
            continue
        ch, new, why = c[0].strip(), c[1].strip(), (c[2].strip() if len(c) > 2 else "")
        errors = []
        if ch not in rows:
            errors.append("상용한자가 아님")
        elif ch not in asked:
            errors.append("요청하지 않은 한자")
        if ch in seen:
            errors.append("두 번 나옴")
        seen.add(ch)
        warnings = []
        if not errors:
            errors, warnings = check(ch, new, rows[ch], rad)
            if new == rows[ch]["memo"]:
                continue  # unchanged: nothing to do
        if errors:
            n_err += 1
            print("✗ %s  %s\n    오류: %s" % (ch, new, "; ".join(errors)))
        else:
            n_warn += bool(warnings)
            print("%s %s  %s%s" % ("△" if warnings else "○", ch, new, ("\n    확인: " + "; ".join(warnings)) if warnings else ""))
            good.append((ch, "", "", new, why))
    print("\n%d줄 통과(그중 확인할 줄 %d), %d줄 오류" % (len(good), n_warn, n_err))
    if accept:
        print("\n# link-polish.tsv 줄(한자, 구성, 유래, 외우기, 이유)")
        for g in good:
            print("\t".join(g))


if __name__ == "__main__":
    main(sys.argv[1:])
