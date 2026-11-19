#!/usr/bin/env python3
"""The 구성 line's part names: a name written as a 훈음 is the standard one (tools/hun/hun.tsv), nothing composed.

Why (2026-11-19): after the 훈음 line was put right (build_hun.py), the 구성 line still named parts with 훈음 no
dictionary heads the entry with: 反(반대할 반), 令(명령할 령), 方(방향 방). Of the 845 part names written as a 훈음, 232
(120 kinds, in 232 kanji) were not the standard one though the part has one: 98 stood in no dictionary, 134 in
libhangul's list only (昔 옛 석 for 예 석). The owner: "구성 줄 부품 이름도 표준 훈음으로 바꿔". Another 34 (21 kinds) are
parts the standard list does not have, named as libhangul does: they stay.

tools/origin/kosei/merge.py runs every 구성 line through [standard_spec] as its last step, so a later layer cannot
bring such a name back. A name is touched only when it is written as a 훈음 of that part: Hangul words ending in one
of the part's Korean sounds (반대할 반 for 反). Plain names (물, 사람, 者를 줄인 꼴) are not 훈음 and stay.

  the part has a standard 훈음   -> the one with the same sound (若 젊을 약 -> 같을 약, not 반야 야), the first of them
                                    (反 -> 돌이킬 반); with no such sound, the first (串 꿸 천 -> 꿸 관)
  it has none (隹, 尞, 复)       -> the name stays when libhangul has it (隹 새 추); else libhangul's first 훈음
                                    with that sound (尞 모닥불 료 -> 화톳불 료)

Usage: part_names.py            prints the names that are still not standard (none after a merge), exit 1 if any
       part_names.py --memo <origin.tsv from before>
                                prints the 외우기 lines that call a part by the name its 구성 line had there and no
                                longer has (memo-old-names.tsv: 159 lines, left as they are)
"""
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, "..", "..", "app", "src", "main", "assets")
UNIT = re.compile(r"^([\^~]?)([^\[\]()]+?)(\[[^\]]+\])?(?:\(([^()]*)\))?$")
HUN = re.compile(r"[가-힣]+( [가-힣]+)* [가-힣]")
SEP = re.compile(r"( · |, )")

_tables = None


def tables():
    """(part -> standard 훈음, part -> libhangul's 훈음), read once."""
    global _tables
    if _tables is None:
        std, lib = {}, {}
        for line in open(os.path.join(HERE, "hun.tsv"), encoding="utf-8"):
            f = line.rstrip("\n").split("\t")
            if len(f) >= 2 and f[1]:
                std[f[0]] = f[1].split(", ")
        for line in open(os.path.join(ASSETS, "kanji.tsv"), encoding="utf-8"):
            f = line.rstrip("\n").split("\t")
            if len(f) >= 2 and f[1]:
                lib[f[0]] = [x.strip() for x in re.split(r", | / ", f[1]) if HUN.fullmatch(x.strip())]
        _tables = (std, lib)
    return _tables


def squeeze(s):
    return s.replace(" ", "")


def standard_name(part, name):
    """[name] of [part] as the 구성 line may show it: unchanged unless it is a 훈음 of the part that is not standard."""
    std, lib = tables()
    s, l = std.get(part, []), lib.get(part, [])
    if not HUN.fullmatch(name) or name[-1] not in {x[-1] for x in s + l}:
        return name
    for x in s:
        if squeeze(x) == squeeze(name):
            return x
    same = [x for x in s if x[-1] == name[-1]]
    if same:
        return same[0]
    if s:
        return s[0]
    if any(squeeze(x) == squeeze(name) for x in l):
        return name
    same = [x for x in l if x[-1] == name[-1]]
    return same[0] if same else name


def standard_note(part, note):
    """A note may hold several names (" · ", ", "): each on its own."""
    return "".join(x if SEP.fullmatch(x) else standard_name(part, x.strip()) for x in SEP.split(note))


def standard_spec(spec):
    """The 구성 line [spec] (origin.tsv's fourth column) with the notes of its one-shape units made standard."""
    if not spec or spec == "-":
        return spec
    free = spec.startswith("!")
    out = []
    for raw in spec.lstrip("!").split(" + "):
        m = UNIT.match(raw.strip())
        if not m or "+" in m.group(2) or len(m.group(2).strip()) != 1 or not m.group(4):
            out.append(raw.strip())
            continue
        out.append("%s%s%s(%s)" % (m.group(1), m.group(2), m.group(3) or "", standard_note(m.group(2).strip(), m.group(4))))
    return ("!" if free else "") + " + ".join(out)


def rows():
    for line in open(os.path.join(ASSETS, "origin.tsv"), encoding="utf-8"):
        f = line.rstrip("\n").split("\t")
        if len(f) >= 4 and f[3] and f[3] != "-":
            yield f


def units(spec):
    """[(part, note)] of the one-shape units with a note."""
    for raw in spec.lstrip("!").split(" + "):
        m = UNIT.match(raw.strip())
        if m and len(m.group(2).strip()) == 1 and m.group(4):
            yield m.group(2).strip(), m.group(4)


def left():
    """[(kanji, part, name, standard name)] still not standard in origin.tsv."""
    out = []
    for f in rows():
        for part, note in units(f[3]):
            for name in SEP.split(note):
                if not SEP.fullmatch(name) and standard_name(part, name.strip()) != name.strip():
                    out.append((f[0], part, name.strip(), standard_name(part, name.strip())))
    return out


def main(*args):
    if "--memo" in args:
        return memo_report(args[args.index("--memo") + 1] if len(args) > args.index("--memo") + 1 else "")
    bad = left()
    for b in bad:
        print("\t".join(b))
    print("OK" if not bad else "%d names not standard" % len(bad))
    return 1 if bad else 0


def stem(hun):
    """The 훈 without its ending, for telling whether a 외우기 word is that 훈 (반대할 -> 반대, 돌이킬 -> 돌이)."""
    w = squeeze(hun[:-2])
    return w[:2] if len(w) > 2 else re.sub(r"[할을를은는한힐일릴질칠울길킬]$", "", w) or w


def memo_report(before_path):
    """The 외우기 lines that call a part by the name the 구성 line had in [before_path] (an origin.tsv from before the
    change) and no longer has: kanji, part, old name, new name, 외우기."""
    before = {}
    for line in open(before_path, encoding="utf-8"):
        f = line.rstrip("\n").split("\t")
        if len(f) >= 4:
            before[f[0]] = dict(units(f[3]))
    n = 0
    for f in rows():
        for part, note in units(f[3]):
            old = before.get(f[0], {}).get(part, note)
            if old == note:
                continue
            for word, inside in re.findall(r"([가-힣]*)\(([^()]*)\)", f[2].split("→")[0]):
                if inside != part or not word:
                    continue
                olds = [stem(x) for x in SEP.split(old) if HUN.fullmatch(x)]
                news = [stem(x) for x in SEP.split(note) if HUN.fullmatch(x)]
                if any(s and s in word for s in olds) and not any(s and s in word for s in news):
                    n += 1
                    print("\t".join((f[0], part, old, note, f[2])))
    print(n, "외우기 lines")
    return 0


if __name__ == "__main__":
    sys.exit(main(*sys.argv[1:]))
