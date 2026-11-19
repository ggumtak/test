#!/usr/bin/env python3
"""Builds tools/hun/hun.tsv: the standard Korean 훈음 (대표 훈음) the card shows for a kanji.

Usage: build_hun.py <hanja.csv> <joyo2010.txt> <kanji.tsv> <out hun.tsv> [--report report.tsv]
  hanja.csv    - https://raw.githubusercontent.com/rycont/hanja-grade-dataset/main/hanja.csv
                 (한국어문회's 급수별 배정한자 5,978자 with their 대표 훈음, converted from the xls on hanja.re.kr;
                 the data is © 한국어문회 and is not kept in this repository)
  joyo2010.txt - https://raw.githubusercontent.com/cjkvi/cjkvi-tables/master/joyo2010.txt (the 상용한자 and their
                 old forms: 悪 → 惡)
  kanji.tsv    - app/src/main/assets/kanji.tsv (its third column: the traditional form the 훈음 was taken from)

Why (2026-11-18): the card showed 消 as "사라질 소, 꺼질 소". The old hun.tsv was written by Claude ("the 훈음 most
natural for the Japanese usage first") and 573 of its 3,002 items stand in no dictionary (英 영국 영, 本 책 본, 安 쌀 안);
another 452 are in libhangul's list only (消 꺼질 소) and not what a Korean dictionary heads the entry with. The
owner checks the card against the Naver 한자사전, which heads each entry with the 한국어문회 훈음 (消 사라질 소).
So the 훈음 line now holds only that; what the kanji means in Japanese stays on the 뜻 line (gloss.tsv).

One line per character, tab separated: char, 훈음 (several joined by ", " in the source's order: 樂 → 즐길 락,
노래 악, 좋아할 요), the Korean form it was found under when that is not the character itself (消 → empty, 悪 → 惡).
Nothing is written for a character the list does not have: the app then falls back to libhangul's 훈음 or says
the kanji was made in Japan. No 훈음 is ever composed here.
"""
import ast
import csv
import re
import sys
import unicodedata

# The Korean form to look a character up under when it is neither the character itself nor the old form in
# joyo2010.txt. 弁 stands for three old characters; 唇 is 脣 (입술 순) in Korea, where 唇 itself is 놀랄 진; the table
# prints 𠮟 剝 塡 頰 but text mostly has 叱 剥 填 頬, so both spellings are looked up under the form the list has.
KOREAN_FORM = {
    "弁": "辨辯瓣", "唇": "脣", "毀": "毁", "挿": "揷", "奨": "獎",
    "𠮟": "叱", "剥": "剝", "填": "塡", "頬": "頰",
}


# Brackets that complete the word rather than gloss it: 右 is 오른(쪽) 우, and dropping the brackets left "오른 우"
# on the card (found 2026-11-19 by reading every bracket of the list against a second source).
WORD_BRACKETS = {"오른(쪽)": "오른쪽"}


def clean(hun):
    """한국어문회 adds the hanja of a word or a gloss in brackets (마을[官廳], 성(姓), 화(和)할, 노[새끼줄]): the card
    shows the 훈 alone."""
    for k, v in WORD_BRACKETS.items():
        hun = hun.replace(k, v)
    return re.sub(r"\s+", " ", re.sub(r"\[[^\]]*\]|\([^)]*\)|（[^）]*）", "", hun)).strip()


def load_eomun(path):
    """char -> its 훈음 in the list's order. Nine rows use compatibility code points (樂 U+F95C): normalised."""
    out = {}
    with open(path, encoding="utf-8", newline="") as f:
        for r in csv.DictReader(f):
            ch = unicodedata.normalize("NFKC", r["hanja"].strip())
            items = out.setdefault(ch, [])
            for means, sounds in ast.literal_eval(r["meaning"]):
                for sound in sounds:
                    for mean in means:
                        # Three rows of the csv already carry the 음 in the first 훈 (占: '점령할 점', '점칠').
                        h = clean(mean if mean.endswith(" " + sound) else f"{mean} {sound}")
                        if h and h not in items:
                            items.append(h)
    return out


def load_joyo(path):
    """The 상용한자 in the table's order -> old form ("" when it has none)."""
    out = {}
    for line in open(path, encoding="utf-8"):
        f = line.rstrip("\n").split("\t")
        if line.startswith("#") or len(f) < 2 or not f[0]:
            continue
        out[f[0]] = f[1].strip()
    return out


def load_variants(path):
    out = {}
    for line in open(path, encoding="utf-8"):
        f = line.rstrip("\n").split("\t")
        if len(f) >= 3 and f[0]:
            out[f[0]] = f[2]
    return out


def lookup(ch, joyo, variant, eomun):
    """([ch]'s 훈음, the Korean form they stand under) or None. The forms, most trusted first: the hand-listed
    Korean form(s), the old form (体 is 體, though 体 has an entry of its own for another word), the character
    itself, the form kanji.tsv links."""
    for form in (KOREAN_FORM.get(ch, ""), joyo.get(ch, ""), ch, variant.get(ch, "")):
        chars = [c for c in unicodedata.normalize("NFKC", form) if c in eomun]
        if chars:
            items = []
            for c in chars:
                items.extend(h for h in eomun[c] if h not in items)
            return ", ".join(items), "" if chars == [ch] else "".join(chars)
    return None


def main(eomun_path, joyo_path, kanji_path, out_path, *rest):
    eomun = load_eomun(eomun_path)
    joyo = load_joyo(joyo_path)
    variant = load_variants(kanji_path)
    rows, missing = [], []
    for ch in joyo:
        hit = lookup(ch, joyo, variant, eomun)
        if hit is None:
            missing.append(ch)
        else:
            rows.append([ch, hit[0], hit[1]])
    # Other characters of kanji.tsv: under their own code point or a hand-listed form, no guessing at variants.
    others = 0
    for ch in sorted(c for c in variant if c not in joyo):
        hit = lookup(ch, {}, {}, eomun)
        if hit:
            rows.append([ch, hit[0], hit[1]])
            others += 1
    with open(out_path, "w", encoding="utf-8", newline="\n") as f:
        for r in rows:
            f.write("\t".join(r) + "\n")
    print(len(rows), "lines:", len(joyo) - len(missing), "of", len(joyo), "상용한자,", others, "others")
    print("상용한자 not in the list:", "".join(missing))
    if "--report" in rest:
        with open(rest[rest.index("--report") + 1], "w", encoding="utf-8", newline="\n") as f:
            for ch in missing:
                f.write(ch + "\n")


if __name__ == "__main__":
    main(*sys.argv[1:])
