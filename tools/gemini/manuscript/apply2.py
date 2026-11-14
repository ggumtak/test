#!/usr/bin/env python3
"""Usage: apply2.py — the follow-up the owner asked for after Gemini's own review of the manuscript (2026-11-14),
written to tools/origin/kosei/link/link-manuscript2.tsv (applied after link-manuscript.tsv):

1. A part whose shape changed (出's 山 is a foot 止, 具's 目 a cauldron 鼎) is called by what it looks like and what it
   was: "산 모양, 본래 발". The 구성 line is always on the card and marks the part's role (뜻), so the look alone
   would state a false meaning; the owner chose both. Only where Gemini named the part by its look, the name is one
   the look really has (the shape's own 훈음, its radical words or its usual name) and the old note gives a plain noun.
2. The 구성 names the fact review passed but held back for the 외우기 shown with them go in with Gemini's 외우기, as a set.
3. Gemini's 외우기 replaces a current one that gives its own meaning away in the scene (… 일곱 토막 → 일곱) when
   Gemini's does not, read one by one (spoil-accepted.txt).
"""
import glob
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "..", "origin", "kosei", "polish"))
from common import ASSETS, UNIT, load, radical_words, units  # noqa: E402

SHAPE = re.compile(r"^([가-힣]+(?: [가-힣]+)?)\s*[^\s가-힣]+\s*(?:이|가|을|를)?\s*(?:변한|줄어든|줄인|바뀐|바꾼|바꿔 쓴|달라진)\s*꼴")
OUT = os.path.join(HERE, "..", "..", "origin", "kosei", "link", "link-manuscript2.tsv")


def gemini():
    gem = {}
    for p in sorted(glob.glob(os.path.join(HERE, "returned-*.txt"))):
        for line in open(p, encoding="utf-8"):
            line = line.strip().lstrip("﻿")
            c = [x.strip() for x in line.split(" | ")]
            if len(c) == 4 and not line.startswith(("[", "\\[", "메모")):
                gem[c[0]] = ([x.strip() for x in c[1].split(" / ")] if c[1] != "-" else [], c[2], c[3])
    return gem


def respec(spec, notes):
    bang = spec.startswith("!")
    out = []
    for raw, note in zip(spec.lstrip("!").split(" + "), notes):
        m = UNIT.match(raw.strip())
        out.append(m.group(2).strip() + ("[%s]" % m.group(3) if m.group(3) else "") + ("(%s)" % note if note else ""))
    return ("!" if bang else "") + " + ".join(out)


def main():
    rows, gem, rad = load(), gemini(), radical_words()
    hun = {}
    with open(os.path.join(ASSETS, "kanji.tsv"), encoding="utf-8") as f:
        for line in f:
            c = line.rstrip("\n").split("\t")
            hun[c[0]] = " ".join(c[i] for i in (1, 10) if len(c) > i)
    usual = dict(l.rstrip("\n").split("\t")[:2] for l in open(os.path.join(HERE, "names.txt"), encoding="utf-8")
                 if not l.startswith("#"))
    out = {}
    look = []
    for ch, r in rows.items():
        us = units(r["spec"])
        if ch not in gem or not us or len(gem[ch][0]) != len(us):
            continue
        notes, changed = [u[3] for u in us], False
        for i, ((s, role, rd, note), g) in enumerate(zip(us, gem[ch][0])):
            m = SHAPE.match(note)
            if not m or not g or g == m.group(1) or m.group(1) in g:
                continue
            origin = m.group(1)
            # Plain nouns only on both sides: "본래 들일", "본래 맞붙은" and "서다 모양" read badly.
            if origin[-1] in "다는은을를할과일들선" or len(origin) > 6 or g.endswith("다"):
                continue
            seen = hun.get(s, "") + " " + " ".join(rad.get(s, ())) + " " + usual.get(s, "")
            if not all(w in seen for w in g.split()):
                continue
            notes[i] = "%s 모양, 본래 %s" % (g, origin)
            changed = True
            look.append("%s %s: %s → %s" % (ch, s, note, notes[i]))
        if changed:
            out.setdefault(ch, ["", "", ""])[0] = respec(r["spec"], notes)
    # 2: the held-back names with Gemini's 외우기.
    held = [l.strip() for l in open(os.path.join(HERE, "held.txt"), encoding="utf-8") if l.strip()]
    for ch in held:
        names, _, memo = gem[ch]
        o = out.setdefault(ch, ["", "", ""])
        base = o[0] or r_spec(rows[ch])
        o[0] = respec(base, [n if n else u[3] for n, u in zip(names, units(base))])
        o[2] = memo
    # 3: 외우기 that no longer give the meaning away.
    for line in open(os.path.join(HERE, "spoil-accepted.txt"), encoding="utf-8"):
        c = line.rstrip("\n").split("\t")
        if len(c) >= 3 and not line.startswith("#"):
            out.setdefault(c[0], ["", "", ""])[2] = c[2]
    # Keep only what leaves the 구성 names and the 외우기 shown with them agreeing: a set where Gemini's own 외우기
    # calls a part otherwise than its own names (上: 卜 '위 표시' and '막대') is left out, the rest of the row kept.
    from check_return import check  # noqa: E402

    def mismatches(ch, spec, memo):
        return sum(1 for w in check(ch, memo, dict(rows[ch], spec=spec), rad)[1] if "부름" in w)

    dropped = []
    for ch in list(out):
        s, o, m = out[ch]
        spec, memo = s or rows[ch]["spec"], m or rows[ch]["memo"]
        if mismatches(ch, spec, memo) > mismatches(ch, rows[ch]["spec"], rows[ch]["memo"]):
            dropped.append(ch)
            del out[ch]
    print("left out for names and 외우기 that disagree:", "".join(dropped))
    with open(OUT, "w", encoding="utf-8") as f:
        f.write("# Gemini 원고 후속(2026-11-14): 한자, 새 구성, 새 유래, 새 외우기, 이유. 빈 칸은 그대로. link-manuscript.tsv 다음에 적용.\n"
                "# 모양이 바뀐 부품은 '산 모양, 본래 발'(사용자 선택), 외우기 때문에 보류했던 구성 이름은 Gemini 외우기와 함께,\n"
                "# 장면에서 뜻을 미리 말하던 외우기는 Gemini 줄로. tools/gemini/manuscript/apply2.py.\n")
        for ch, (s, o, m) in out.items():
            f.write("\t".join([ch, s, o, m, "Gemini 원고 후속"]) + "\n")
    print(len(look), "look-and-origin names;", len(held), "held-back sets;", "rows", len(out))
    print("\n".join(look))


def r_spec(row):
    # The current 구성 line without radical marks, as link layers write it.
    return respec(row["spec"], [u[3] for u in units(row["spec"])])


if __name__ == "__main__":
    main()
