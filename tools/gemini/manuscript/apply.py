#!/usr/bin/env python3
"""Usage: apply.py <cmp key> — writes tools/origin/kosei/link/link-manuscript.tsv from Gemini's checked rows:

- 유래: the ones that only shorten the current one (auto-origin.tsv) and those the fact review passed (judged-N.tsv `ok`);
- 구성 names: those the review passed;
- 외우기: those that beat the current line in the blind comparison (verdict-N.tsv against the key kept outside the
  repository); a tie or a loss keeps the current line.
Prints what went in and what stayed out."""
import glob
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
LINK = os.path.join(HERE, "..", "..", "origin", "kosei", "link", "link-manuscript.tsv")


def main(key_path):
    out = subprocess.run([sys.executable, os.path.join(HERE, "check_manuscript.py")]
                         + sorted(glob.glob(os.path.join(HERE, "returned-*.txt"))) + ["--accept"],
                         capture_output=True, text=True).stdout
    acc = {c[0]: c for c in (l.split("\t") for l in
                             out.split("# link 줄(한자, 구성, 유래, 외우기, 이유)\n")[1].strip().split("\n"))}
    auto = dict(l.rstrip("\n").split("\t") for l in open(os.path.join(HERE, "auto-origin.tsv"), encoding="utf-8"))
    judged = {}
    for p in glob.glob(os.path.join(HERE, "judged-*.tsv")):
        for l in open(p, encoding="utf-8"):
            c = l.rstrip("\n").split("\t")
            if len(c) >= 3:
                judged[c[0]] = (c[1], c[2])
    key = dict(l.rstrip("\n").split("\t") for l in open(key_path, encoding="utf-8"))
    won = {}
    for p in glob.glob(os.path.join(HERE, "verdict-*.tsv")):
        for l in open(p, encoding="utf-8"):
            c = l.rstrip("\n").split("\t")
            if len(c) >= 2 and c[0] in key:
                won[c[0]] = "new" if c[1] == key[c[0]] else ("tie" if c[1] == "=" else "old")
    rows, n = [], {"유래": 0, "유래 탈락": 0, "구성": 0, "구성 탈락": 0, "외우기": 0, "외우기 탈락": 0}
    for ch, (_, spec, origin, memo, _) in acc.items():
        o = s = m = ""
        if origin:
            if ch in auto or judged.get(ch, ("", ""))[0] == "ok":
                o = origin
                n["유래"] += 1
            else:
                n["유래 탈락"] += 1
        if spec:
            if judged.get(ch, ("", ""))[1] == "ok":
                s = spec
                n["구성"] += 1
            else:
                n["구성 탈락"] += 1
        if memo:
            if won.get(ch) == "new":
                m = memo
                n["외우기"] += 1
            else:
                n["외우기 탈락"] += 1
        if o or s or m:
            rows.append((ch, s, o, m, "Gemini 원고 2026-11-14"))
    with open(LINK, "w", encoding="utf-8") as f:
        f.write("# Gemini 원고(2026-11-14): 한자, 새 구성, 새 유래, 새 외우기, 이유. 빈 칸은 그대로. link-polish.tsv 다음에 적용.\n"
                "# 유래는 지금 문장을 줄이기만 한 것과 독립 사실 검토를 통과한 것, 구성 이름은 검토를 통과한 것, 외우기는 지금 줄과의\n"
                "# 눈가림 비교에서 이긴 것만. 과정은 tools/gemini/manuscript/README.md.\n")
        for r in rows:
            f.write("\t".join(r) + "\n")
    print(n, len(rows), "rows")


if __name__ == "__main__":
    main(sys.argv[1])
