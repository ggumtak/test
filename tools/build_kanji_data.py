#!/usr/bin/env python3
"""Builds app/src/main/assets/kanji.tsv from KANJIDIC2 and libhangul's hanja.txt.

Usage: build_kanji_data.py <jamdict.db> <hanja.txt> <unihan.db> <out.tsv>
  jamdict.db - the SQLite database of the PyPI package jamdict-data 1.5 (contains KANJIDIC2)
  hanja.txt  - https://raw.githubusercontent.com/libhangul/libhangul/main/data/hanja/hanja.txt
  unihan.db  - data/unihan.db of the npm package cjk-unihan 0.0.3 (Unicode Unihan; the variant fields map
               shinjitai to the traditional forms Korean uses: 広 -> 廣, 読 -> 讀)

One line per character, tab separated:
  char  hun  variant  on  kun  strokes  grade  english
hun: the Korean 훈음 ("기운 기"; several joined by " / "). When the character has none of its own (Japanese
shinjitai such as 気), it is taken from a variant with the same Korean reading and that variant goes in the
third column (氣). on/kun are joined by "・", english by "; ". Empty fields stay empty.
"""
import sqlite3
import sys
from collections import OrderedDict


def load_hun(path):
    hun = {}
    with open(path, encoding="utf-8") as f:
        for line in f:
            if line.startswith("#"):
                continue
            parts = line.rstrip("\n").split(":")
            if len(parts) < 3 or len(parts[1]) != 1 or not parts[2].strip():
                continue
            reading, ch, meaning = parts[0], parts[1], parts[2].strip()
            hun.setdefault(ch, []).append((reading, meaning))
    return hun


def is_cjk(ch):
    cp = ord(ch)
    return 0x3400 <= cp <= 0x4DBF or 0x4E00 <= cp <= 0x9FFF or 0xF900 <= cp <= 0xFAFF or 0x20000 <= cp <= 0x3134F


# Shinjitai that took over an unrelated older character: Korean reads the old one differently (芸 is 향풀 운,
# but Japanese 芸 is 藝 재주 예), so the traditional form's 훈음 comes first.
MERGED = {"芸": "藝", "予": "豫", "台": "臺", "弁": "辨", "欠": "缺", "缶": "罐", "糸": "絲", "余": "餘",
          "豊": "豐", "灯": "燈", "証": "證", "医": "醫", "虫": "蟲", "党": "黨", "宝": "寶", "体": "體"}


def norm(code):
    # KANJIDIC writes the same JIS code as "1-52-04" in one place and "1-52-4" in another.
    return "-".join(str(int(x)) if x.isdigit() else x for x in code.split("-"))


def load_unihan(path):
    """char -> variant chars (Z, traditional, semantic, in that order)."""
    out = {}
    u = sqlite3.connect(path)
    for ch, z, t, sem in u.execute("select character, kZVariant, kTraditionalVariant, kSemanticVariant from unihan"):
        vs = []
        for field in (z, t, sem):
            for tok in (field or "").split():
                if tok.startswith("U+"):
                    v = chr(int(tok[2:].split("<")[0], 16))
                    if v != ch and v not in vs:
                        vs.append(v)
        if vs:
            out[ch] = vs
    return out


def main(db_path, hanja_path, unihan_path, out_path):
    hun = load_hun(hanja_path)
    unihan = load_unihan(unihan_path)
    c = sqlite3.connect(db_path)
    chars = OrderedDict()
    for cid, lit, strokes, grade in c.execute("select ID, literal, stroke_count, grade from character order by ID"):
        chars[lit] = {"cid": cid, "strokes": strokes or "", "grade": grade or ""}
    # JIS / UCS code -> character, to resolve the variant references (気 -> jis208 1-61-70 -> 氣).
    by_code = {}
    for cid, cp_type, value in c.execute("select cid, cp_type, value from codepoint"):
        by_code[(cp_type, norm(value))] = cid
    lit_of = {v["cid"]: k for k, v in chars.items()}
    for lit, e in chars.items():
        cid = e["cid"]
        e["on"], e["kun"], e["en"], e["kor"] = [], [], [], []
        for (gid,) in c.execute("select ID from rm_group where cid=?", (cid,)):
            for r_type, value in c.execute("select r_type, value from reading where gid=?", (gid,)):
                if r_type == "ja_on":
                    e["on"].append(value)
                elif r_type == "ja_kun":
                    e["kun"].append(value)
                elif r_type == "korean_h":
                    e["kor"].append(value)
            for (value,) in c.execute(
                    "select value from meaning where gid=? and (m_lang is null or m_lang='' or m_lang='en')", (gid,)):
                e["en"].append(value)
        variants = []
        for var_type, value in c.execute("select var_type, value from variant where cid=?", (cid,)):
            if var_type == "ucs":
                v = chr(int(value, 16))
            else:
                v = lit_of.get(by_code.get((var_type, norm(value))))
            if v and v != lit and v not in variants:
                variants.append(v)
        for v in unihan.get(lit, []):
            if v not in variants:
                variants.append(v)
        e["variants"] = variants

    def good(reading, meaning):
        # A proper 훈음 names the reading at the end ("쇠 철"); notes such as "鐵의 古字" do not.
        return meaning.endswith(reading) or (reading + ",") in meaning

    def pick(ch, kor):
        """The 훈음 of [ch] that match a Korean reading in [kor] (any when kor is empty), proper ones first."""
        items = [(r, m) for r, m in hun.get(ch, []) if not kor or r in kor]
        items.sort(key=lambda it: 0 if good(*it) else 1)
        return items

    lines = []
    for lit, e in chars.items():
        own = pick(lit, e["kor"])
        h, via = "", ""
        if lit in MERGED and hun.get(MERGED[lit]):
            via = MERGED[lit]
            h = " / ".join(m for _, m in sorted(hun[via], key=lambda it: 0 if good(*it) else 1))
        elif own and good(*own[0]):
            h = " / ".join(m for _, m in own)
        else:
            # Japanese shinjitai (気, 円, 鉄): the 훈음 of a variant with the same reading (氣, 圓, 鐵).
            best = None
            for v in e["variants"]:
                cand = pick(v, e["kor"])
                if cand and good(*cand[0]):
                    best = (v, cand)
                    break
            if best:
                via, h = best[0], " / ".join(m for _, m in best[1])
            elif own:
                h = " / ".join(m for _, m in own)
            elif hun.get(lit):
                h = " / ".join(m for _, m in hun[lit])
        lines.append([lit, h, via, "・".join(e["on"]), "・".join(e["kun"]), str(e["strokes"]), str(e["grade"]),
                      "; ".join(e["en"][:5])])
    # Characters only the Korean list knows (rare in Japanese text, but cheap): 훈음 alone.
    for ch, items in hun.items():
        if ch not in chars and is_cjk(ch):
            lines.append([ch, " / ".join(m for _, m in items), "", "", "", "", "", ""])
    with open(out_path, "w", encoding="utf-8", newline="\n") as f:
        for row in lines:
            f.write("\t".join(x.replace("\t", " ") for x in row) + "\n")
    print(len(lines), "lines")


if __name__ == "__main__":
    main(*sys.argv[1:5])
