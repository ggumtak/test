#!/usr/bin/env python3
"""Usage: check_manuscript.py <answer.txt>... [--accept] — checks the Gemini app's answers (`한자 | 구성 이름 | 유래 |
외우기`, one line per kanji, see 00_작성지침.md) against the card's rules and prints, per line, ok, the warnings to
look at, or the errors that reject it. With --accept the lines that pass are printed last as link-layer rows
(한자, 구성, 유래, 외우기, 이유) for tools/origin/kosei/link/: the 구성 line keeps its shapes, roles and readings and
takes the new names; an unchanged field is left empty.

Errors: not 4 fields; a kanji that is not a 상용한자 or comes twice; as many names as parts not given; a name with a
kanji outside the parts or a rare character; 유래 that does not start with the current label or is over 120
characters; 외우기 not `장면 → 뜻`, its meaning not starting with the current one, over 50 characters, a kanji
outside parentheses, a shape that is not in the kanji; Hangul in parentheses right after kana (a pronunciation).
Warnings: 외우기 over 45 characters; a part of the 구성 missing from the story; 유래 that adds a
`의견`/`확실` hedge."""
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "..", "origin", "kosei", "polish"))
from common import UNIT, is_han, load, u16, units  # noqa: E402

LABELS = ["회의 겸 형성", "형성", "회의", "상형", "지사", "가차", "국자", "생략형", "불명"]
PRONOUNCED = re.compile(r"[ぁ-ゖァ-ヺー]\([가-힣 \-]+\)")


def respec(spec, names):
    bang = spec.startswith("!")
    out = []
    for raw, name in zip(spec.lstrip("!").split(" + "), names):
        m = UNIT.match(raw.strip())
        mark, shape, role = m.group(1), m.group(2).strip(), m.group(3)
        out.append(mark + shape + ("[%s]" % role if role else "") + ("(%s)" % name if name else ""))
    return ("!" if bang else "") + " + ".join(out)


def label(raw):
    for lb in LABELS:
        if raw.startswith(lb + ":"):
            return lb
    return ""


def check(ch, names, origin, memo, row):
    errors, warnings = [], []
    us = units(row["spec"])
    if us:
        if len(names) != len(us):
            errors.append("구성 이름 %d개(부품은 %d개)" % (len(names), len(us)))
    elif names not in ([], ["-"]):
        errors.append("구성이 없는 한자인데 이름을 씀")
    shapes = {c for c in row["spec"] + ch if is_han(c)}
    for n in names:
        strange = [c for c in n if is_han(c)]
        if strange:
            errors.append("이름에 한자: %s" % n)
        if len(n) > 8:
            warnings.append("이름이 김: %s" % n)
    old = label(row["raw"])
    if not origin.startswith(old + ":"):
        errors.append("유래가 '%s:'로 시작하지 않음" % old)
    if len(origin) > 120:
        errors.append("유래 %d자: 120자를 넘음" % len(origin))
    if re.search("의견|확실", origin) and not re.search("의견|확실", row["raw"]):
        warnings.append("유래에 새 유보 표현")
    if memo.count("→") != 1:
        errors.append("외우기가 '장면 → 뜻' 꼴이 아님")
    else:
        was = row["memo"].split("→")[-1].strip()
        now = memo.split("→")[-1].strip()
        if not now.startswith(was.split("·")[0].strip()):
            errors.append("→ 뒤의 뜻이 지금 줄(%s)로 시작하지 않음" % was)
    n = u16(memo)
    if n > 50:
        errors.append("외우기 %d자: 50자를 넘음" % n)
    elif n > 45:
        warnings.append("외우기 %d자" % n)
    depth, outside, inside = 0, [], set()
    for c in memo:
        if c == "(":
            depth += 1
        elif c == ")":
            depth = max(0, depth - 1)
        elif is_han(c):
            (inside.add(c) if depth else outside.append(c))
    if outside:
        errors.append("괄호 밖 한자: %s" % "".join(outside))
    known = shapes | {c for c in row["memo"] + row["raw"] if is_han(c)}
    if inside - known:
        errors.append("이 글자에 없는 모양: %s" % "".join(sorted(inside - known)))
    for s, _, _, _ in us:
        if not any(c in inside for c in s if is_han(c)):
            warnings.append("외우기에 %s이 없음" % s)
    for field in (" ".join(names), origin, memo):
        if PRONOUNCED.search(field):
            errors.append("가나 뒤 한글 발음")
            break
    return errors, warnings


def main(argv):
    accept = "--accept" in argv
    rows = load()
    seen, good, n_err, n_warn = set(), [], 0, 0
    for path in [a for a in argv if not a.startswith("--")]:
        for i, line in enumerate(open(path, encoding="utf-8"), 1):
            line = line.strip()
            if not line or line.startswith(("#", "```", "메모", "한자 |")) or set(line) <= set("-|: "):
                continue
            c = [x.strip() for x in line.strip("|").split(" | ")]
            if len(c) != 4:
                print("✗ %s:%d 칸이 %d개: %s" % (os.path.basename(path), i, len(c), line[:50]))
                n_err += 1
                continue
            ch, names, origin, memo = c[0], [x.strip() for x in c[1].split(" / ")] if c[1] != "-" else [], c[2], c[3]
            if ch not in rows:
                print("✗ %s: 상용한자가 아님" % ch)
                n_err += 1
                continue
            if ch in seen:
                print("✗ %s: 두 번 나옴" % ch)
                n_err += 1
                continue
            seen.add(ch)
            errors, warnings = check(ch, names, origin, memo, rows[ch])
            if errors:
                n_err += 1
                print("✗ %s  %s\n    오류: %s" % (ch, memo, "; ".join(errors)))
                continue
            n_warn += bool(warnings)
            if warnings:
                print("△ %s  %s\n    확인: %s" % (ch, memo, "; ".join(warnings)))
            r = rows[ch]
            spec = respec(r["spec"], names) if units(r["spec"]) else ""
            good.append((ch, spec if spec and spec != r["spec"] else "", origin if origin != r["raw"] else "",
                         memo if memo != r["memo"] else "", "Gemini 원고"))
    missing = len(rows) - len(seen)
    print("\n%d줄 통과(확인할 줄 %d), %d줄 오류, 아직 안 온 한자 %d" % (len(good), n_warn, n_err, missing))
    if accept:
        print("\n# link 줄(한자, 구성, 유래, 외우기, 이유)")
        for g in good:
            print("\t".join(g))


if __name__ == "__main__":
    main(sys.argv[1:])
