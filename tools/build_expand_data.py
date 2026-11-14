#!/usr/bin/env python3
"""Usage: build_expand_data.py — writes app/src/main/assets/expand.tsv, the 확장 row of the card: for each
상용한자, up to three other 상용한자 to learn with it, in this order:

1. the same sound part and an on reading in common (the 소리 계열 that parts.tsv already lists: 晴 → 清 情 精);
2. the 상용한자 in which this kanji is the sound part (真 → 慎 鎮, 青 → 清 晴 情);
3. the same sound part with another reading, when that part is a 상용한자 itself (the shape still helps; an
   abbreviated piece like 龶 in 害 責 契 says nothing);
4. the 상용한자 that have this kanji as another part, for a kanji of four strokes or more (一 in 七 上 三 teaches
   nothing).

Within a group the kanji met first come first (grade 1-6, then the rest, each by stroke count). One line per kanji
that has any, sorted by UTF-8 bytes (the app binary-searches it): char<TAB>kanji."""
import os

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, "..", "app", "src", "main", "assets")
MAX = 3


def main():
    kan = {}
    with open(os.path.join(ASSETS, "kanji.tsv"), encoding="utf-8") as f:
        for line in f:
            c = line.rstrip("\n").split("\t")
            if len(c) > 6 and c[6].isdigit() and (1 <= int(c[6]) <= 6 or int(c[6]) == 8):
                kan[c[0]] = (int(c[6]) if int(c[6]) <= 6 else 7, int(c[5] or 0))
    sound, elements, family = {}, {}, {}
    with open(os.path.join(ASSETS, "parts.tsv"), encoding="utf-8") as f:
        for line in f:
            c = line.rstrip("\n").split("\t")
            if c[0] not in kan:
                continue
            parts = [p.split("/") for p in (c[4] if len(c) > 4 else "").split(" ") if p]
            elements[c[0]] = {p[0] for p in parts if p[0]}
            sound[c[0]] = {p[0] for p in parts if len(p) > 2 and "p" in p[2] and p[0]}
            family[c[0]] = [x for x in (c[6] if len(c) > 6 else "") if x in kan]
    by_sound = {}
    for ch, ss in sound.items():
        for s in ss:
            by_sound.setdefault(s, []).append(ch)
    order = lambda x: kan[x]  # noqa: E731
    lines = []
    for ch in kan:
        out = []

        def add(group):
            for x in sorted(set(group), key=order):
                if x != ch and x not in out and len(out) < MAX:
                    out.append(x)

        add(family.get(ch, []))
        if kan[ch][1] >= 3:
            add([x for x, ss in sound.items() if ch in ss])
        add([x for s in sound.get(ch, ()) if s in kan for x in by_sound.get(s, [])])
        if kan[ch][1] >= 4:
            add([x for x, es in elements.items() if ch in es])
        if out:
            lines.append((ch, "".join(out)))
    lines.sort(key=lambda x: x[0].encode("utf-8"))
    with open(os.path.join(ASSETS, "expand.tsv"), "w", encoding="utf-8") as f:
        for ch, xs in lines:
            f.write(ch + "\t" + xs + "\n")
    print(len(lines), "of", len(kan), "kanji have a 확장 row")


if __name__ == "__main__":
    main()
