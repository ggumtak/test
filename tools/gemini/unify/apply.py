#!/usr/bin/env python3
"""Usage: apply.py — writes tools/origin/kosei/link/link-unify.tsv from the returned answers (returned-NN.txt): the
lines check_return.py finds no error in, less the kanji held back in the reading (held.tsv) and the lines kept as
they are (`유지`). Then run tools/origin/merge.py."""
import glob
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from check_return import check  # noqa: E402
from collect import load_base  # noqa: E402
from make_request import table  # noqa: E402


def main():
    rows = load_base()
    tab = table(rows)
    asked = {m[0] for m in open(os.path.join(HERE, "requested.txt"), encoding="utf-8").read().split()}
    held = {line.split("\t")[0] for line in open(os.path.join(HERE, "held.tsv"), encoding="utf-8")
            if line.strip() and not line.startswith("#")}
    out, seen = [], set()
    for path in sorted(glob.glob(os.path.join(HERE, "returned-*.txt"))):
        for line in open(path, encoding="utf-8"):
            c = [x.strip() for x in line.strip().replace("\\", "").strip("|").split("|")]
            if len(c) < 2 or len(c[0]) != 1 or c[0] not in asked or c[0] in held or c[0] in seen:
                continue
            seen.add(c[0])
            if c[1] == rows[c[0]]["memo"] or (len(c) > 2 and c[2].startswith("유지")):
                continue
            if not check(c[0], c[1], rows[c[0]], tab)[0]:
                out.append((c[0], c[1]))
    path = os.path.join(HERE, "..", "..", "origin", "kosei", "link", "link-unify.tsv")
    with open(path, "w", encoding="utf-8") as f:
        f.write("# 부품 이름 통일(2026-11-16, tools/gemini/unify): 한자, 새 구성, 새 유래, 새 외우기, 이유. 빈 칸은 그대로.\n"
                "# Gemini가 고정 이름으로 다시 쓴 외우기 가운데 검수를 통과한 줄. apply.py가 만든다.\n")
        for ch, memo in out:
            f.write("\t".join((ch, "", "", memo, "부품 이름 통일(Gemini)")) + "\n")
    print(len(out), "줄 →", os.path.relpath(path))


if __name__ == "__main__":
    main()
