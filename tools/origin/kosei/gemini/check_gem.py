#!/usr/bin/env python3
"""Checks the answers to the outside review, batch N: python3 -I check_gem.py N   (OK, or every problem).

gem-N.tsv   kanji<TAB>구성 (new spec, "-" for none, or empty = keep)<TAB>새 유래 (or empty)<TAB>메모
memo-N.tsv  kanji<TAB>새 외우기<TAB>이유
Only kanji of g-N.tsv. A non-free spec must follow parts.tsv's parts; any spec's 소리 roles must agree with the
drawing (a sound unit needs blue strokes in the kanji, and a kanji with blue strokes needs a sound unit, unless
the 메모 explains it)."""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, ".."))
import kosei  # noqa: E402
from convert import load  # noqa: E402

ASSETS = os.path.join(HERE, "..", "..", "..", "..", "app", "src", "main", "assets")
TYPES = {"상형", "지사", "회의", "형성", "회의 겸 형성", "가차", "국자", "생략형", "불명"}


def rows(path, width):
    out = []
    if os.path.exists(path):
        for line in open(path, encoding="utf-8"):
            if line.strip() and not line.startswith("#"):
                f = line.rstrip("\n").split("\t")
                out.append([x.strip() for x in f] + [""] * (width - len(f)))
    return out


CMAP = "/tmp/claude-0/-home-user-test/1560e2f5-85e5-5883-ad85-16a26bf44f74/scratchpad/font/noto_cmap.txt"


def missing_glyphs(text):
    """Han characters in [text] that Noto Sans CJK, the phone's font, cannot draw."""
    if not os.path.exists(CMAP):
        return []
    have = getattr(missing_glyphs, "have", None)
    if have is None:
        have = missing_glyphs.have = {int(x, 16) for x in open(CMAP).read().split()}
    return [c for c in text if (0x2E80 <= ord(c) <= 0x9FFF or ord(c) >= 0x20000) and ord(c) not in have]


def main(n):
    parts, origin = load(os.path.join(ASSETS, "parts.tsv")), load(os.path.join(ASSETS, "origin.tsv"))
    asked = {r[0] for r in rows(os.path.join(HERE, "g-%s.tsv" % n), 4)}
    problems, notes = [], []
    for ch, spec, new, memo in rows(os.path.join(HERE, "gem-%s.tsv" % n), 4):
        if ch not in asked:
            problems.append("%s: 이 묶음의 검토 대상이 아님" % ch)
            continue
        p = parts[ch]
        plist = [x.split("/") + [""] * (4 - len(x.split("/"))) for x in p[4].split()]
        tags = [s[0] for s in p[5].split("|")] if p[5] else []
        if spec and spec != "-":
            free = spec.startswith("!")
            if free:
                try:
                    units = kosei.parse(spec[1:])
                except ValueError as e:
                    problems.append("%s: %s" % (ch, e))
                    continue
                if not memo:
                    problems.append("%s: '!' 줄에는 메모에 이유를 적어야 함" % ch)
                for pieces, role, reading, note in units:
                    if len(note) > kosei.NOTE_MAX:
                        problems.append("%s: %s 설명이 김(%d자)" % (ch, "+".join(pieces), len(note)))
                sound = any("소리" in u[1] for u in units)
                if sound and "p" not in tags and not any("r" in x[2] and "p" in x[2] for x in plist):
                    (notes if memo else problems).append("%s: 소리 역할이 있지만 획 그림에 파란 획이 없음" % ch)
                if "p" in tags and not sound:
                    (notes if memo else problems).append("%s: 획 그림에 파란 획이 있지만 소리 역할이 없음" % ch)
                if p[2] and not any(p[2] in u[0] for u in units):
                    notes.append("%s: 부수 모양 %s가 조각으로 없어 빨갛게 표시되지 않음" % (ch, p[2]))
            else:
                errors, warnings = kosei.check(spec, plist, memo)
                problems += ["%s: %s" % (ch, e) for e in errors]
                notes += ["%s: %s" % (ch, w) for w in warnings]
        if new:
            old = origin[ch][1]
            t = new.split(":", 1)[0] if ":" in new[:10] else ""
            if t not in TYPES:
                problems.append("%s: 새 유래의 유형 '%s'가 틀림" % (ch, t))
            elif t != (old.split(":", 1)[0] if ":" in old[:10] else ""):
                notes.append("%s: 유래 유형을 바꿈 (%s → %s)" % (ch, old.split(":", 1)[0], t))
            if len(new) > 120:
                problems.append("%s: 새 유래가 김(%d자)" % (ch, len(new)))
            body = new.split(":", 1)[1] if ":" in new[:10] else new
            for w in ["신자체", "정자", "說文", "설문", "갑골", "금문", "본자", "이체", "속자", "가차", "생략형", "회의", "형성",
                      "상형", "지사", "소리 겸 뜻", "설이 갈림"]:
                if w in body:
                    problems.append("%s: 새 유래에 어려운 말 '%s'" % (ch, w))
    for ch, memo_line, why in rows(os.path.join(HERE, "memo-%s.tsv" % n), 3):
        if ch not in asked:
            problems.append("%s: (외우기) 이 묶음의 검토 대상이 아님" % ch)
        if len(memo_line) > 45 or "→" not in memo_line:
            problems.append("%s: 외우기는 45자 이내, '… → 뜻' 꼴 (%d자)" % (ch, len(memo_line)))
    for path in ("gem-%s.tsv" % n, "memo-%s.tsv" % n):
        for r in rows(os.path.join(HERE, path), 4):
            bad = missing_glyphs(r[1] + r[2])
            if bad:
                problems.append("%s: 휴대폰 글꼴에 없는 글자 %s" % (r[0], "".join(bad)))
    for w in notes:
        print("참고 " + w)
    for e in problems:
        print("고칠 것 " + e)
    print("OK" if not problems else "%d건 고칠 것" % len(problems))
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1]))
