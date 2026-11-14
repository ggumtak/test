#!/usr/bin/env python3
"""Usage: merge.py [--check] — writes app/src/main/assets/extra.tsv from out-N.tsv and the reviewers' fix-N.tsv.

extra.tsv: char<TAB>훈음<TAB>뜻<TAB>유래<TAB>외우기<TAB>쓰임 for the characters of chars.txt (radicals and parts outside
the 상용한자 that the user's Anki decks teach), sorted by UTF-8 bytes (the app binary-searches it). Written from
in-N.txt following BRIEF.md, then every line checked by a separate review pass (REVIEW.md): a fix-N.tsv row
replaces the drafted one, and fix-final.tsv (hand edits) replaces both. "-" is an empty field.
With --check nothing is written; the lines that break a rule are printed either way.
"""
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, "..", "..", "app", "src", "main", "assets")
TYPES = {"상형", "지사", "회의", "형성", "회의 겸 형성", "가차", "국자", "생략형", "불명"}
JARGON = ["신자체", "정자", "說文", "설문", "갑골", "금문", "본자", "이체", "속자"]
KANA = re.compile(r"[ぁ-ゖァ-ヺー]\(([가-힣]+)\)")


def u16(s):
    return len(s.encode("utf-16-le")) // 2


def rows(name):
    path = os.path.join(HERE, name)
    out = []
    if os.path.exists(path):
        for line in open(path, encoding="utf-8"):
            f = [x.strip() for x in line.rstrip("\n").split("\t")]
            if len(f) >= 6 and f[0] and not f[0].startswith("#"):
                out.append(f[:6])
    return out


def problems(r, joyo):
    ch, hun, gloss, origin, memo, used = r
    bad = []
    if hun != "-":
        for item in hun.split(", "):
            if not re.fullmatch(r"[가-힣]+(?: [가-힣]+)* [가-힣]", item):
                bad.append("훈음 꼴")
        if hun.count(", ") > 1:
            bad.append("훈음 셋 이상")
    if not gloss or gloss == "-" or u16(gloss) > 40:
        bad.append("뜻")
    t = origin.split(":", 1)[0] if ":" in origin else ""
    if t not in TYPES:
        bad.append("유래 분류")
    if u16(origin) > 120:
        bad.append("유래 길이 %d" % u16(origin))
    if [w for w in JARGON if w in origin.split(":", 1)[-1]]:
        bad.append("유래 용어")
    if " → " not in memo or u16(memo) > 50:
        bad.append("외우기 꼴/길이 %d" % u16(memo))
    if used != "-" and (len(used) > 3 or [c for c in used if c not in joyo] or ch in used):
        bad.append("쓰임")
    if KANA.search(origin + memo + gloss):
        bad.append("한글 발음")
    return bad


def main(*args):
    chars = [c for c in open(os.path.join(HERE, "chars.txt"), encoding="utf-8").read() if not c.isspace()]
    joyo = {line.split("\t")[0] for line in open(os.path.join(ASSETS, "origin.tsv"), encoding="utf-8") if line.strip()}
    merged, fixed = {}, 0
    for i in (1, 2, 3):
        for r in rows(f"out-{i}.tsv"):
            merged[r[0]] = r
        for r in rows(f"fix-{i}.tsv"):
            fixed += merged.get(r[0]) != r
            merged[r[0]] = r
    for r in rows("fix-final.tsv"):
        fixed += merged.get(r[0]) != r
        merged[r[0]] = r
    bad = []
    bad += ["없음: " + c for c in chars if c not in merged]
    bad += ["목록 밖: " + c for c in merged if c not in chars]
    bad += ["상용한자: " + c for c in merged if c in joyo]
    for c in chars:
        if c in merged:
            for b in problems(merged[c], joyo):
                bad.append(b + ": " + "\t".join(merged[c]))
    for b in bad:
        print(b)
    print(len(merged), "rows,", fixed, "corrected,", len(bad), "problems")
    if "--check" not in args and not bad:
        with open(os.path.join(ASSETS, "extra.tsv"), "w", encoding="utf-8", newline="\n") as f:
            for c in sorted(merged, key=lambda s: s.encode("utf-8")):
                f.write("\t".join(merged[c]) + "\n")
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main(*sys.argv[1:]))
