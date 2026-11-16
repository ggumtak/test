#!/usr/bin/env python3
"""Shared by the scripts here: every part name the 외우기 lines use, per part shape.

A use is `word(shape)` in a 외우기 where the parentheses hold exactly one kanji-like shape (4,100 of them; the 200
with two or more shapes, `풀밭 사내(艹+夫)`, follow the 구성 unit and are left alone)."""
import os
import re
import sys
from collections import Counter, defaultdict

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.append(os.path.join(HERE, "..", "..", "origin", "kosei", "polish"))  # after HERE: both have a make_request.py
from common import ASSETS, SHAPE_NOTE, is_han, load, shown, u16, units  # noqa: E402,F401

USE = re.compile(r"([가-힣]*)\(([^()]*)\)")


def uses_of(memo):
    """[(word, shape)] for each `word(shape)` with one shape inside the parentheses."""
    out = []
    for word, inside in USE.findall(memo.split("→")[0]):
        han = [c for c in inside if is_han(c)]
        if len(han) == 1:
            out.append((word, han[0]))
    return out


def collect(rows):
    """shape -> {word: [kanji]}, and shape -> {kanji: the 구성 note of the unit that is just this shape}."""
    words, notes = defaultdict(lambda: defaultdict(list)), defaultdict(dict)
    for ch, r in rows.items():
        for s, _, _, note in units(r["spec"]):
            if note and len([c for c in s if is_han(c)]) == 1:
                notes[[c for c in s if is_han(c)][0]][ch] = note
        for word, shape in uses_of(r["memo"]):
            words[shape][word].append(ch)
    return words, notes


def grade_order(rows):
    kan = {}
    with open(os.path.join(ASSETS, "kanji.tsv"), encoding="utf-8") as f:
        for line in f:
            c = line.rstrip("\n").split("\t")
            kan[c[0]] = c

    def key(ch):
        g = int(kan[ch][6] or 0)
        return (g if 1 <= g <= 6 else 7, int(kan[ch][5] or 0), ch)
    return sorted(rows, key=key), kan


def load_base():
    """load(), with the 외우기 of the requested kanji as it was when the request was made (base-memo.tsv): what the
    returned lines are checked against, also after some of them have replaced the lines in origin.tsv."""
    rows = load()
    for line in open(os.path.join(HERE, "base-memo.tsv"), encoding="utf-8"):
        if line.strip() and not line.startswith("#"):
            ch, memo = line.rstrip("\n").split("\t")
            rows[ch]["memo"] = memo
    return rows
