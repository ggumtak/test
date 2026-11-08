#!/usr/bin/env python3
"""Checks story-N.tsv (story/BRIEF.md): python3 -I check.py N   (prints OK, or every problem to fix).

story-N.tsv: only the kanji whose lines change, no header:  한자<TAB>새 외우기 or empty<TAB>새 유래 or empty<TAB>이유
"""
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, ".."))
sys.path.insert(0, os.path.join(HERE, "..", "full"))
from convert import load  # noqa: E402

ASSETS = os.path.join(HERE, "..", "..", "..", "..", "app", "src", "main", "assets")
TYPES = {"상형", "지사", "회의", "형성", "회의 겸 형성", "가차", "국자", "생략형", "불명"}
BODY_JARGON = ["신자체", "정자", "說文", "설문", "갑골", "금문", "본자", "이체", "속자", "가차", "생략형", "회의", "형성",
               "상형", "지사", "소리 겸 뜻", "설이 갈림", "자원", "약자", "고자", "전서", "예서", "소전"]
HEDGES = ["의견이 갈림", "확실하지 않", "봄", "풂", "라 함", "이라 함", "다고 함", "의견도", "옛 사전", "사전도", "설도",
          "라고도", "듯", "추정"]


def han(s):
    return [c for c in s if 0x2E80 <= ord(c) <= 0x2FDF or 0x3400 <= ord(c) <= 0x9FFF or ord(c) >= 0x20000]


def main(n):
    parts, origin = load(os.path.join(ASSETS, "parts.tsv")), load(os.path.join(ASSETS, "origin.tsv"))
    try:
        from make_inputs import CHINESE  # full/make_inputs.py: simplified shapes Japanese does not use
    except ImportError:
        CHINESE = set()
    joyo = set(origin)
    batch = open(os.path.join(HERE, "list-%s.txt" % n), encoding="utf-8").read().split()
    path = os.path.join(HERE, "story-%s.tsv" % n)
    if not os.path.exists(path):
        print("story-%s.tsv 없음" % n)
        return 1
    problems, notes, seen = [], [], set()
    for i, line in enumerate(open(path, encoding="utf-8")):
        if not line.strip() or line.startswith("#"):
            continue
        f = line.rstrip("\n").split("\t")
        if len(f) != 4:
            problems.append("%d번째 줄: 칸이 %d개(4개여야 함)" % (i + 1, len(f)))
            continue
        ch, memo, new, why = (x.strip() for x in f)
        if ch not in batch:
            problems.append("%s: 이 묶음(list-%s.txt)에 없는 글자" % (ch, n))
            continue
        if ch in seen:
            problems.append("%s: 두 번 나옴" % ch)
        seen.add(ch)
        o = origin[ch]
        old, old_memo, spec = o[1], o[2], (o[3] if len(o) > 3 else "")
        if not memo and not new:
            problems.append("%s: 외우기도 유래도 비어 있음(바꾸지 않으면 줄을 빼기)" % ch)
        if not why:
            problems.append("%s: 이유 칸이 비어 있음" % ch)
        if memo:
            if memo == old_memo:
                problems.append("%s: 새 외우기가 원래와 같음(바꾸지 않으면 비워 둘 것)" % ch)
            if len(memo) > 45:
                problems.append("%s: 외우기가 김(%d자 > 45)" % (ch, len(memo)))
            if memo.count(" → ") != 1 or not memo.split(" → ")[1].strip():
                problems.append("%s: 외우기는 '… → 뜻' 꼴로 (화살표 앞뒤 띄어 한 번)" % ch)
            shown = set(re.findall(r"\(([^()]+)\)", memo))
            if not shown:
                notes.append("%s: 외우기에 괄호로 보인 부품이 없음" % ch)
            # The shapes shown should be ones the card names: its parts, the 구성 pieces, the 유래's characters.
            names = set(han(spec)) | set(han(old)) | set(han(new)) | {ch}
            for x in parts.get(ch, [""] * 5)[4].split() if len(parts.get(ch, [])) > 4 else []:
                names.update(han(x))
            for s in shown:
                for c in han(s):
                    if c in CHINESE and c not in joyo:
                        problems.append("%s: 외우기에 중국 간체 모양 %s" % (ch, c))
                    elif c not in names:
                        notes.append("%s: 외우기의 %s는 구성·유래에 없는 모양(보이는 부품인지 확인)" % (ch, c))
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
                    problems.append("%s: 유래에 어려운 말 '%s'" % (ch, w))
            if new == old:
                problems.append("%s: 새 유래가 원래와 같음(바꾸지 않으면 비워 둘 것)" % ch)
            for k in re.findall(r"[ァ-ヺー]+", old):
                if k not in new:
                    problems.append("%s: 원래 있던 음독 %s가 사라짐" % (ch, k))
            if any(h in old for h in HEDGES) and not any(h in new for h in HEDGES):
                problems.append("%s: 원래의 유보 표현(의견이 갈림·옛 사전·~라고 봄 등)이 사라짐" % ch)
            lost = set(han(old)) - set(han(new)) - {ch}
            if lost:
                notes.append("%s: 원래 유래의 한자 %s가 빠짐(사실이 빠지지 않았는지 확인)" % (ch, "".join(sorted(lost))))
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
