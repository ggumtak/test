#!/usr/bin/env python3
"""Checks link-N.tsv (link/BRIEF.md): python3 -I check.py N   (prints OK, or every problem to fix).
Round 2 (CRITIC.md): python3 -I check.py N round2   checks round2/fix-N.tsv against round2/list-N.txt.

link-N.tsv: only the kanji whose rows change, no header:
    한자<TAB>새 구성 or empty<TAB>새 유래 or empty<TAB>새 외우기 or empty<TAB>이유
"""
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import rules  # noqa: E402
from rules import kosei  # noqa: E402


def check_row(f, batch, origin, parts, kanji, rads, seen):
    problems, notes = [], []
    ch, spec, new, memo, why = (x.strip() for x in f)
    if ch not in batch:
        return ["%s: 이 묶음에 없는 글자" % ch], []
    if ch in seen:
        problems.append("%s: 두 번 나옴" % ch)
    seen.add(ch)
    o = origin[ch]
    old, old_memo, old_spec = o[1], o[2], (o[3] if len(o) > 3 else "")
    if not spec and not new and not memo:
        problems.append("%s: 구성·유래·외우기가 모두 비어 있음(바꾸지 않으면 줄을 빼기)" % ch)
    if not why:
        problems.append("%s: 이유 칸이 비어 있음" % ch)
    p = parts.get(ch)
    if spec:
        if old_spec in ("", "-"):
            problems.append("%s: 구성 줄이 없는 글자에 구성을 새로 씀(이 작업에서는 하지 않음, flags에 적기)" % ch)
        else:
            plist = rules.plist_of(p)
            if spec.startswith("!") != old_spec.startswith("!"):
                problems.append("%s: 구성 줄의 '!' 여부를 바꿈(원래대로 둘 것)" % ch)
            body = spec.lstrip("!")
            if not old_spec.startswith("!") and re.search(r"(^|\+ )[\^~]", body):
                problems.append("%s: 구성에 ^/~ 표시를 쓰지 말 것(merge가 붙임)" % ch)
            errors, warnings = kosei.check(spec.replace("!~", "!").replace(" + ~", " + "), plist, why)
            problems += ["%s: 구성 %s" % (ch, e) for e in errors]
            notes += ["%s: 구성 %s" % (ch, w) for w in warnings if "자유 작성" not in w]
            try:
                a = [(u[0], u[1]) for u in kosei.parse(body.replace("~", ""))]
                b = [(u[0], u[1]) for u in kosei.parse(old_spec.lstrip("!").replace("^", "").replace("~", ""))]
                if a != b:
                    problems.append("%s: 구성의 모양이나 역할이 바뀜(설명만 바꿀 것, 그 밖은 flags에): %s → %s" % (ch, b, a))
            except ValueError as e:
                problems.append("%s: 구성 %s" % (ch, e))
            # A changed note should be one of that part's 훈 in the data, or the 유래's own words (CRITIC.md).
            try:
                olds = {"+".join(u[0]): u[3] for u in kosei.parse(old_spec.lstrip("!").replace("^", "").replace("~", ""))}
                for pieces, role, reading, note in kosei.parse(body.replace("~", "")):
                    c = "".join(pieces)
                    if note and note != olds.get("+".join(pieces), note) and not rules.SHAPE_NOTE.search(note):
                        hun = kanji.get(c, [""] * 2)[1] if len(pieces) == 1 else ""
                        if note not in hun and note not in (new or old):
                            notes.append("%s: 구성의 %s 설명 '%s'가 자료의 훈음(%s)·유래에 없음(근거 확인)" % (ch, c, note, hun))
            except ValueError:
                pass
            if rules.input_spec(old_spec) == spec:
                problems.append("%s: 새 구성이 원래와 같음(바꾸지 않으면 비워 둘 것)" % ch)
    if new:
        t = new.split(":", 1)[0] if ":" in new[:10] else ""
        ot = old.split(":", 1)[0] if ":" in old[:10] else ""
        if t != ot or t not in rules.TYPES:
            problems.append("%s: 유형 '%s'가 원래 '%s'와 다름(유형은 바꾸지 않음, flags에)" % (ch, t, ot))
        if rules.u16(new) > rules.ORIGIN_MAX:
            problems.append("%s: 새 유래가 김(%d자 > %d)" % (ch, rules.u16(new), rules.ORIGIN_MAX))
        body = new.split(":", 1)[1] if ":" in new[:10] else new
        for w in rules.BODY_JARGON:
            if w in body:
                problems.append("%s: 유래에 전문 용어 '%s'" % (ch, w))
        if new == old:
            problems.append("%s: 새 유래가 원래와 같음(바꾸지 않으면 비워 둘 것)" % ch)
        for k in re.findall(r"[ァ-ヺー]+", old):
            if k not in new:
                problems.append("%s: 원래 있던 음독 %s가 사라짐" % (ch, k))
        if any(h in old for h in rules.HEDGES) and not any(h in new for h in rules.HEDGES):
            problems.append("%s: 원래의 유보 표현(의견이 갈림·옛 사전·~라고 봄 등)이 사라짐" % ch)
        lost = rules.han(old) - rules.han(new) - {ch}
        if lost:
            notes.append("%s: 원래 유래의 한자 %s가 빠짐(사실이 빠지지 않았는지 확인)" % (ch, "".join(sorted(lost))))
    if memo:
        if rules.u16(memo) > rules.MEMO_MAX:
            problems.append("%s: 새 외우기가 김(%d자 > %d)" % (ch, rules.u16(memo), rules.MEMO_MAX))
        if memo.count("→") != 1:
            problems.append("%s: 외우기에는 '→'가 꼭 하나(… → 뜻)" % ch)
        elif not memo.split("→")[1].strip():
            problems.append("%s: '→' 뒤에 뜻이 없음" % ch)
        if memo == old_memo:
            problems.append("%s: 새 외우기가 원래와 같음(바꾸지 않으면 비워 둘 것)" % ch)
        if "\t" in memo or memo.startswith(("외우기", "-")):
            problems.append("%s: 외우기 형식이 틀림" % ch)
        # (A word that is the kanji's own meaning is no term here: 亭 → 정자.)
        own = " ".join(kanji[ch][i] for i in (1, 8, 10) if len(kanji[ch]) > i)
        for w in rules.BODY_JARGON:
            if w in memo and w not in own:
                problems.append("%s: 외우기에 전문 용어 '%s'" % (ch, w))
        if re.search(r"\([^()]*[가-힣][^()]*\)", memo):
            notes.append("%s: 외우기 괄호 안에 한글이 있음(괄호에는 부품 모양만: 물(氵))" % ch)
    _, _, rmeaning = rules.radical_row(p, rads)
    final_spec = spec or old_spec
    if spec and not spec.startswith("!") and old_spec.startswith("^"):
        final_spec = spec
    hints = rules.memo_hints(ch, kanji[ch], p, final_spec, new or old, memo or old_memo, rmeaning)
    notes += ["%s: %s" % (ch, h) for h in hints]
    return problems, notes


def main(n, folder=""):
    kanji, parts, origin, rads, joyo = rules.load_all()
    base = os.path.join(HERE, folder) if folder else HERE
    name = ("fix-%s.tsv" if folder else "link-%s.tsv") % n
    batch = open(os.path.join(base, "list-%s.txt" % n), encoding="utf-8").read().split()
    path = os.path.join(base, name)
    if not os.path.exists(path):
        print(name + " 없음")
        return 1
    problems, notes, seen = [], [], set()
    for i, line in enumerate(open(path, encoding="utf-8")):
        if not line.strip() or line.startswith("#"):
            continue
        f = line.rstrip("\n").split("\t")
        if len(f) != 5:
            problems.append("%d번째 줄: 칸이 %d개(5개여야 함: 한자, 구성, 유래, 외우기, 이유)" % (i + 1, len(f)))
            continue
        p, w = check_row(f, batch, origin, parts, kanji, rads, seen)
        problems += p
        notes += w
    flags = os.path.join(base, "flags-%s.tsv" % n)
    if os.path.exists(flags):
        for i, line in enumerate(open(flags, encoding="utf-8")):
            if line.strip() and not line.startswith("#") and len(line.rstrip("\n").split("\t")) != 3:
                problems.append("flags-%s.tsv %d번째 줄: 칸이 3개여야 함(한자, 항목, 문제와 근거)" % (n, i + 1))
    for w in notes:
        print("참고 " + w)
    if problems:
        for e in problems:
            print("고칠 것 " + e)
        return 1
    print("OK (%d줄, 참고 %d건)" % (len(seen), len(notes)))
    return 0


if __name__ == "__main__":
    sys.exit(main(*sys.argv[1:]))
