#!/usr/bin/env python3
"""Merges the drafted origin notes (out-N.tsv) with the reviewers' corrections (fix-N.tsv) into
app/src/main/assets/origin.tsv.

origin.tsv: kanji<TAB>자원<TAB>외우기 for the 2,136 상용한자, sorted by UTF-8 bytes (the app binary-searches it).
자원: the formation type ("형성:", "회의:", …) and how the parts made the character; 외우기: a one-line memory story
from the parts as they look in the Japanese form. Drafted by Claude in ten batches from in-N.tsv (made by
make_inputs.py: KanjiVG parts and sound marks, KANJIDIC2, the 훈음, a makemeahanzi hint) following BRIEF.md, then
every line re-read by a separate review pass following REVIEW.md; fix-N.tsv rows replace the drafted ones.
"""
import os
import re
import sys

here = os.path.dirname(os.path.abspath(__file__))
out_path = os.path.join(here, "..", "..", "app", "src", "main", "assets", "origin.tsv")


# "氵(물)가 뜻, 毎(マイ)가 소리" → "뜻은 氵(물), 소리는 毎(マイ)": the writers' common opening, whose particles
# often disagreed with the gloss in the parentheses.
OPENING = re.compile(r"^(형성|회의 겸 형성): (\S+?\([^)]*\))[이가] 뜻, (\S+?\([^)]*\))[이가] 소리")
# Any other particle right after parentheses agrees with the Hangul word before them (오른쪽(フク)이), as Korean
# writes it; after a kanji, with the Korean gloss inside them (木(나무)가), which is what the learner reads, or,
# when the parentheses hold a Japanese reading, with the kanji's Korean reading (白(ハク)을: 백).
AFTER_PARENS = re.compile(r"(.?)\(([^()]*)\)(이|가|은|는|을|를|과|와)(?=[\s,.·)]|$)")
PAIRS = {"이": ("이", "가"), "가": ("이", "가"), "은": ("은", "는"), "는": ("은", "는"), "을": ("을", "를"),
         "를": ("을", "를"), "과": ("과", "와"), "와": ("과", "와")}


def korean_readings():
    """kanji -> its Korean reading (毎 → 매), from the first 훈음 in kanji.tsv."""
    out = {}
    path = os.path.join(here, "..", "..", "app", "src", "main", "assets", "kanji.tsv")
    for line in open(path, encoding="utf-8"):
        f = line.rstrip("\n").split("\t")
        hun = (f[10] if len(f) > 10 and f[10] else f[1]) if len(f) > 1 else ""
        first = re.split(r", | / ", hun)[0].strip()
        if first and "가" <= first[-1] <= "힣":
            out[f[0]] = first[-1]
    return out


KOREAN = korean_readings()


def batchim(text):
    """Whether [text] ends in a final consonant when read: Hangul by its syllable, katakana ン; None if unknown."""
    for ch in reversed(text.strip()):
        if "가" <= ch <= "힣":
            return (ord(ch) - 0xAC00) % 28 != 0
        if ch in "ーッ":
            continue
        if "ァ" <= ch <= "ヺ" or "ぁ" <= ch <= "ゖ":
            return ch in "ンん"
        return None
    return None


def fix_particles(text):
    text = OPENING.sub(r"\1: 뜻은 \2, 소리는 \3", text)

    def agree(m):
        before, inside = m.group(1), m.group(2).strip()
        if before and "가" <= before <= "힣":
            b = batchim(before)
        elif inside and ("ァ" <= inside[0] <= "ヺ" or "ぁ" <= inside[0] <= "ゖ"):
            b = batchim(KOREAN[before]) if before in KOREAN else None
        else:
            b = batchim(inside)
        if b is None:
            return m.group(0)
        pair = PAIRS[m.group(3)]
        return before + "(" + m.group(2) + ")" + (pair[0] if b else pair[1])

    return AFTER_PARENS.sub(agree, text)


def rows(path):
    out = []
    if not os.path.exists(path):
        return out
    for line in open(path, encoding="utf-8"):
        f = line.rstrip("\n").split("\t")
        if len(f) == 2:
            f.append("")  # an empty 외우기 whose trailing tab an editor dropped
        if len(f) == 3 and f[0]:
            out.append([x.strip() for x in f])
    return out


def main():
    merged, fixed = {}, 0
    n = 1
    while os.path.exists(os.path.join(here, f"out-{n}.tsv")):
        fixes = {r[0]: r for r in rows(os.path.join(here, f"fix-{n}.tsv"))}
        for r in rows(os.path.join(here, f"out-{n}.tsv")):
            if r[0] in fixes:
                fixed += r != fixes[r[0]]
                r = fixes[r[0]]
            merged[r[0]] = [r[0], fix_particles(r[1]), fix_particles(r[2])]
        n += 1
    with open(out_path, "w", encoding="utf-8", newline="\n") as f:
        for k in sorted(merged, key=lambda c: c.encode("utf-8")):
            f.write("\t".join(merged[k]) + "\n")
    print(len(merged), "rows,", fixed, "corrected")


if __name__ == "__main__":
    main(*sys.argv[1:])
