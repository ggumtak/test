#!/usr/bin/env python3
"""Checks easy-N.tsv (easy/BRIEF.md): python3 -I check.py N   (prints OK, or every problem to fix).

easy-N.tsv: only the kanji whose lines change, no header:  한자<TAB>새 구성 or empty<TAB>새 유래 or empty<TAB>이유
"""
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, ".."))
import kosei  # noqa: E402
from convert import load  # noqa: E402

ASSETS = os.path.join(HERE, "..", "..", "..", "..", "app", "src", "main", "assets")
TYPES = {"상형", "지사", "회의", "형성", "회의 겸 형성", "가차", "국자", "생략형", "불명"}
BODY_JARGON = ["신자체", "정자", "說文", "설문", "갑골", "금문", "본자", "이체", "속자", "가차", "생략형", "회의", "형성",
               "상형", "지사", "소리 겸 뜻", "설이 갈림", "자원", "약자", "고자", "전서", "예서", "소전"]
# Words a learner may not know: flagged so the line is looked at again (easy/BRIEF.md has the list and what to say).
HARD = ["녹봉", "보루", "진지", "시렁", "제후", "천자", "점괘", "희생", "형상", "무릇", "멍에", "장정", "갑을병정",
        "조리", "법도", "관청", "벼슬아치", "의례", "제기", "기물", "성곽", "사당", "종묘", "역참", "주살", "부스럼",
        "삼감", "도리", "연유", "형용", "모양새", "곧,", "이를테면", "및", "등지", "자루(柄)", "본뜸", "본떠", "본뜬"]
HEDGES = ["의견이 갈림", "확실하지 않", "봄", "풂", "라 함", "이라 함", "다고 함", "의견도", "옛 사전", "사전도", "설도",
          "라고도", "듯", "추정"]


def han(s):
    return {c for c in s if 0x2E80 <= ord(c) <= 0x2FDF or 0x3400 <= ord(c) <= 0x9FFF or ord(c) >= 0x20000}


def main(n):
    parts, origin = load(os.path.join(ASSETS, "parts.tsv")), load(os.path.join(ASSETS, "origin.tsv"))
    batch = open(os.path.join(HERE, "list-%s.txt" % n), encoding="utf-8").read().split()
    path = os.path.join(HERE, "easy-%s.tsv" % n)
    if not os.path.exists(path):
        print("easy-%s.tsv 없음" % n)
        return 1
    problems, notes, seen = [], [], set()
    for i, line in enumerate(open(path, encoding="utf-8")):
        if not line.strip() or line.startswith("#"):
            continue
        f = line.rstrip("\n").split("\t")
        if len(f) != 4:
            problems.append("%d번째 줄: 칸이 %d개(4개여야 함)" % (i + 1, len(f)))
            continue
        ch, spec, new, why = (x.strip() for x in f)
        if ch not in batch:
            problems.append("%s: 이 묶음(list-%s.txt)에 없는 글자" % (ch, n))
            continue
        if ch in seen:
            problems.append("%s: 두 번 나옴" % ch)
        seen.add(ch)
        o = origin[ch]
        old, old_spec = o[1], (o[3] if len(o) > 3 else "")
        if not spec and not new:
            problems.append("%s: 구성도 유래도 비어 있음(바꾸지 않으면 줄을 빼기)" % ch)
        if not why:
            problems.append("%s: 이유 칸이 비어 있음" % ch)
        if spec:
            if old_spec in ("", "-"):
                problems.append("%s: 구성 줄이 없는 글자에 구성을 새로 씀(이 작업에서는 하지 않음)" % ch)
            else:
                p = parts[ch]
                plist = [x.split("/") + [""] * (4 - len(x.split("/"))) for x in p[4].split()]
                if spec.startswith("!") != old_spec.startswith("!"):
                    problems.append("%s: 구성 줄의 '!' 여부를 바꿈(원래대로 둘 것)" % ch)
                body = spec.lstrip("!")
                if not old_spec.startswith("!") and re.search(r"(^|\+ )[\^~]", body):
                    problems.append("%s: 구성에 ^/~ 표시를 쓰지 말 것(merge가 붙임)" % ch)
                errors, warnings = kosei.check(spec.replace("!~", "!").replace(" + ~", " + "), plist, why)
                problems += ["%s: 구성 %s" % (ch, e) for e in errors]
                notes += ["%s: 구성 %s" % (ch, w) for w in warnings if "자유 작성" not in w]
                try:
                    a = [(u[0], u[1]) for u in kosei.parse(spec.lstrip("!").replace("~", ""))]
                    b = [(u[0], u[1]) for u in kosei.parse(old_spec.lstrip("!").replace("^", "").replace("~", ""))]
                    if a != b:
                        problems.append("%s: 구성의 모양이나 역할이 바뀜(설명만 바꿀 것): %s → %s" % (ch, b, a))
                except ValueError:
                    pass
        text = new or old
        if new:
            t = new.split(":", 1)[0] if ":" in new[:10] else ""
            ot = old.split(":", 1)[0] if ":" in old[:10] else ""
            if t != ot or t not in TYPES:
                problems.append("%s: 유형 '%s'가 원래 '%s'와 다름" % (ch, t, ot))
            if len(new) > 120:
                problems.append("%s: 새 유래가 김(%d자 > 120)" % (ch, len(new)))
            body = new.split(":", 1)[1] if ":" in new[:10] else new
            for w in BODY_JARGON:
                if w in body:
                    problems.append("%s: 어려운 말 '%s'" % (ch, w))
            if new == old:
                problems.append("%s: 새 유래가 원래와 같음(바꾸지 않으면 비워 둘 것)" % ch)
            for k in re.findall(r"[ァ-ヺー]{1,}", old):
                if k not in new:
                    problems.append("%s: 원래 있던 음독 %s가 사라짐" % (ch, k))
            if any(h in old for h in HEDGES) and not any(h in new for h in HEDGES):
                problems.append("%s: 원래의 유보 표현(의견이 갈림·옛 사전·~라고 봄 등)이 사라짐" % ch)
            lost = han(old) - han(new) - {ch}
            if lost:
                notes.append("%s: 원래 유래의 한자 %s가 빠짐(사실이 빠지지 않았는지 확인)" % (ch, "".join(sorted(lost))))
        for w in HARD:
            if w in text:
                notes.append("%s: 어려울 수 있는 말 '%s'" % (ch, w))
        sp = spec or old_spec
        if sp and sp != "-" and not sp.startswith("!"):
            try:
                units = kosei.parse(sp.replace("^", "").replace("~", ""))
            except ValueError:
                units = []
            for pieces, role, reading, note in units:
                words = [w for w in re.split(r"[\s,·+]+", note) if len(w) >= 2 and not han(w)]
                if not (set("".join(pieces)) & han(text)) and not any(w[:2] in text for w in words):
                    notes.append("%s: 구성의 '%s'가 유래에 보이지 않음(연결 확인)" % (ch, "+".join(pieces)))
    for w in notes:
        print("참고 " + w)
    if problems:
        for e in problems:
            print("고칠 것 " + e)
        return 1
    print("OK (%d줄, 참고 %d건)" % (len(seen), len(notes)))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1]))
