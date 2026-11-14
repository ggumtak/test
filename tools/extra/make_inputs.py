#!/usr/bin/env python3
"""Usage: make_inputs.py — writes in-N.txt (what the writers of the 부품 글자 lines get) and used.tsv.

chars.txt: the characters outside the 상용한자 that the user's Anki decks teach (radicals such as 厂 and parts
such as 昜): the card had only raw dictionary data for them. For each, in-N.txt gives what the app already knows
(KANJIDIC readings, the 훈음 from the hanja table, the radical's name) and, so that the new lines say what the
상용한자 cards say, the name the 구성 lines give this shape and the 상용한자 that contain it.

used.tsv: char<TAB>the 상용한자 that have it as a part, those met first first (grade, then strokes), up to 6: the
ones whose 구성 line or top-level parts name the shape, then those that hold it deeper.
"""
import collections
import os
import re

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, "..", "..", "app", "src", "main", "assets")
BATCHES = 3
UNIT = re.compile(r"[\^~]?([^\[\]()\s]+?)\[([^\]]+)\](?:\(([^()]*)\))?")
# The same shape under another code point: the name and the kanji found for one count for the other.
SAME = {"艹": "艹䒑", "䒑": "䒑艹", "戶": "戶戸", "兑": "兑兌", "朮": "朮术", "术": "术朮", "夹": "夹夾", "夾": "夾夹",
        "罒": "罒", "攵": "攵", "曰": "曰"}


def rows(name):
    with open(os.path.join(ASSETS, name), encoding="utf-8") as f:
        for line in f:
            c = line.rstrip("\n").split("\t")
            if c[0] and not c[0].startswith("#"):
                yield c


def main():
    chars = [c for c in open(os.path.join(HERE, "chars.txt"), encoding="utf-8").read() if not c.isspace()]
    kanji = {c[0]: c for c in rows("kanji.tsv")}
    origin = {c[0]: c for c in rows("origin.tsv")}
    order = {}
    for ch in origin:
        k = kanji[ch]
        grade = int(k[6]) if k[6].isdigit() and 1 <= int(k[6]) <= 6 else 7
        order[ch] = (grade, int(k[5] or 0), ch)
    radicals = collections.defaultdict(list)
    for c in rows("radicals.tsv"):
        if len(c) >= 6:
            radicals[c[1]].append(c)
    # The shapes each 상용한자 is made of: its 구성 line's units and pieces, and its top-level parts.
    direct, names = collections.defaultdict(set), collections.defaultdict(collections.Counter)
    said = collections.defaultdict(dict)
    for ch, c in origin.items():
        for shape, role, note in UNIT.findall(c[3] if len(c) > 3 else ""):
            for piece in [shape] + shape.split("+"):
                direct[ch].add(piece)
            if note:
                names[shape][note] += 1
                said[ch][shape] = f"{shape}[{role}]({note})"
    elements = {}
    for c in rows("parts.tsv"):
        els = set()
        for p in (c[4] if len(c) > 4 else "").split(" "):
            x = p.split("/")
            if x[0]:
                els.add(x[0])
                els.update(e for e in (x[1].split("+") if len(x) > 1 else []) if e)
        elements[c[0]] = els
        if c[0] in origin:
            direct[c[0]] |= els

    def holds(ch, target, depth=0):
        if depth > 4:
            return False
        return any(e == target or (e != ch and holds(e, target, depth + 1)) for e in elements.get(ch, ()))

    used = {}
    for t in chars:
        same = SAME.get(t, t)
        first = sorted((ch for ch in origin if direct[ch] & set(same)), key=order.get)
        deeper = sorted((ch for ch in origin if ch not in first and any(holds(ch, s) for s in same)), key=order.get)
        used[t] = (first, deeper)
    with open(os.path.join(HERE, "used.tsv"), "w", encoding="utf-8", newline="\n") as f:
        for t in sorted(chars, key=lambda s: s.encode("utf-8")):
            first, deeper = used[t]
            both = (first + deeper)[:6]
            if both:
                f.write(t + "\t" + "".join(both) + "\n")

    size = -(-len(chars) // BATCHES)
    for b in range(BATCHES):
        with open(os.path.join(HERE, f"in-{b + 1}.txt"), "w", encoding="utf-8", newline="\n") as f:
            for t in chars[b * size:(b + 1) * size]:
                k = kanji.get(t)
                head = f"### {t} (U+{ord(t):04X})"
                if k:
                    head += f" · 음 {k[3] or '-'} · 훈 {k[4] or '-'} · {k[5] or '?'}획 · 영어 {k[7] or '-'}"
                else:
                    head += " · KANJIDIC에 없는 글자"
                f.write(head + "\n")
                if k and k[1]:
                    f.write(f"한자 자료의 훈음(참고, 틀리거나 음이 빠진 것이 많음): {k[1]}\n")
                for same in SAME.get(t, t):
                    for r in radicals.get(same, []):
                        f.write(f"부수 {r[0]}번: {r[4]}({r[5]}) · {r[3]}" + (f" · 자리 {r[2]}" if r[2] else "") + "\n")
                    top = names[same].most_common(4)
                    if top:
                        f.write("상용한자 카드가 이 모양을 부르는 이름: " +
                                ", ".join(f"{n} ({cnt}자)" for n, cnt in top) + "\n")
                first, deeper = used[t]
                def show(group):
                    shown = []
                    for ch in group[:10]:
                        k2 = kanji[ch]
                        hun = (k2[10] if len(k2) > 10 and k2[10] else k2[1]).split(", ")[0]
                        how = next((said[ch][s] for s in SAME.get(t, t) if s in said[ch]), "")
                        shown.append(f"{ch}({hun}" + (f": {how}" if how else "") + ")")
                    return ", ".join(shown)
                if first:
                    f.write(f"이 모양이 부품인 상용한자 {len(first)}자: {show(first)}\n")
                if deeper:
                    f.write(f"부품 안쪽에 든 상용한자 {len(deeper)}자(눈에 보이는지 확인할 것): {show(deeper)}\n")
                if not first and not deeper:
                    f.write("이 모양이 든 상용한자: 자료에서 찾지 못함\n")
                f.write("\n")
    print(len(chars), "characters,", sum(1 for t in chars if used[t][0] or used[t][1]), "with 상용한자")


if __name__ == "__main__":
    main()
