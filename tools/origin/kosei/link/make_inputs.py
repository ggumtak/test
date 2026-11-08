#!/usr/bin/env python3
"""Writes the inputs of the four-rows pass (link/BRIEF.md): list-N.txt (the kanji of batch N) and in-N.txt (each
상용한자 as the card shows it: 훈음, 부수, 구성, 유래, 외우기, and the automatic hints of rules.py).

Usage: make_inputs.py <batches>"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from rules import load_all, card_block  # noqa: E402


def main(batches):
    kanji, parts, origin, rads, joyo = load_all()
    n = int(batches)
    size = -(-len(joyo) // n)
    hinted = 0
    for b in range(n):
        chunk = joyo[b * size:(b + 1) * size]
        with open(os.path.join(HERE, "list-%d.txt" % (b + 1)), "w", encoding="utf-8") as f:
            f.write("\n".join(chunk) + "\n")
        blocks = [card_block(ch, kanji, parts, origin, rads) for ch in chunk]
        hinted += sum("자동 점검" in x for x in blocks)
        with open(os.path.join(HERE, "in-%d.txt" % (b + 1)), "w", encoding="utf-8") as f:
            f.write("\n\n".join(blocks) + "\n")
        print("batch", b + 1, len(chunk), "kanji")
    print(hinted, "kanji with hints")


if __name__ == "__main__":
    main(sys.argv[1])
