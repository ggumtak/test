#!/usr/bin/env python3
"""Usage: merge.py — writes tools/usage/words.tsv (form, reading, Korean meaning; sorted) from the written batches
(out-N.tsv), with the reviewers' corrections (fix-N.tsv, then fix-final.tsv) on top. build_usage_data.py reads
words.tsv and uses the lines of the words it chose."""
import glob
import os
import re

HERE = os.path.dirname(os.path.abspath(__file__))


def number(path):
    m = re.search(r"-(\d+)\.tsv$", path)
    return int(m.group(1)) if m else 10 ** 6


def main():
    words = {}
    changed = 0
    for kind in ("out", "fix"):
        paths = sorted(glob.glob(os.path.join(HERE, kind + "-*.tsv")), key=number)
        for path in paths:
            with open(path, encoding="utf-8") as f:
                for line in f:
                    c = line.rstrip("\n").split("\t")
                    if len(c) < 3 or not c[2].strip():
                        continue
                    key = (c[0], c[1])
                    if kind == "fix" and words.get(key) != c[2].strip():
                        changed += 1
                    words[key] = c[2].strip()
    with open(os.path.join(HERE, "words.tsv"), "w", encoding="utf-8", newline="\n") as f:
        for (form, kana), meaning in sorted(words.items()):
            f.write("%s\t%s\t%s\n" % (form, kana, meaning))
    print(len(words), "words;", changed, "changed by review")


if __name__ == "__main__":
    main()
