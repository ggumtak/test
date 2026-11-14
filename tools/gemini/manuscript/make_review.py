#!/usr/bin/env python3
"""Usage: make_review.py — splits Gemini's accepted rows (check_manuscript.py --accept) into what can go in as it is
and what an independent reviewer must see, and writes the review batches.

- 유래: a change that only drops words of the current one (the 구성 line already says "뜻은 氵(물), 소리는 毎") or rewords
  them goes in as it is; one that brings words the current one does not have, or drops a hedge (의견, 확실, 로 봄), goes to
  review (review-N.tsv, REVIEW.md).
- 구성 names: every changed row goes to review.
- 외우기: every changed row goes to a blind comparison with the current line (cmp-N.tsv, ../../origin/kosei/polish/COMPARE.md);
  the key stays outside the repository.
Writes auto-origin.tsv (한자, 새 유래) for the 유래 that need no review."""
import os
import random
import re
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "..", "origin", "kosei", "polish"))
from common import is_han, load, shown, units  # noqa: E402

HEDGE = re.compile("의견|확실|로 봄|로 보는")


def words(s):
    return {re.sub("(을|를|이|가|은|는|의|에|로|으로|와|과|도|만|임|함|됨)$", "", w) for w in re.findall(r"[가-힣]{2,}", s)} \
        | {c for c in s if is_han(c)} | set(re.findall(r"[ァ-ヺー]+", s))


def main(key_path):
    rows = load()
    out = subprocess.run([sys.executable, os.path.join(HERE, "check_manuscript.py")]
                         + sorted(os.path.join(HERE, f) for f in os.listdir(HERE) if f.startswith("returned-"))
                         + ["--accept"], capture_output=True, text=True).stdout
    acc = [l.split("\t") for l in out.split("# link 줄(한자, 구성, 유래, 외우기, 이유)\n")[1].strip().split("\n")]
    auto, review, memo = [], [], []
    for ch, spec, origin, m, _ in acc:
        r = rows[ch]
        need = False
        if origin:
            new = words(origin) - words(r["raw"])
            if any(len(w) >= 2 or (len(w) == 1 and is_han(w)) for w in new) or (HEDGE.search(r["raw"]) and not HEDGE.search(origin)):
                need = True
            else:
                auto.append((ch, origin))
        if need or spec:
            review.append((ch, spec, origin if need else ""))
        if m:
            memo.append((ch, m))
    with open(os.path.join(HERE, "auto-origin.tsv"), "w", encoding="utf-8") as f:
        for ch, o in auto:
            f.write(ch + "\t" + o + "\n")
    per = (len(review) + 7) // 8
    for b in range(8):
        part = review[b * per:(b + 1) * per]
        with open(os.path.join(HERE, "review-%d.tsv" % (b + 1)), "w", encoding="utf-8") as f:
            f.write("# 한자\t훈음\t구성(지금)\t구성 이름(새, 비었으면 그대로)\t유래(지금)\t유래(새, 비었으면 그대로)\n")
            for ch, spec, origin in part:
                r = rows[ch]
                names = " / ".join(u[3] for u in units(spec)) if spec else ""
                f.write("\t".join([ch, r["hun"], shown(r["spec"]), names, r["raw"], origin]) + "\n")
    rng = random.Random(20261114)
    rng.shuffle(memo)
    per = (len(memo) + 3) // 4
    with open(key_path, "w", encoding="utf-8") as key:
        for b in range(4):
            part = memo[b * per:(b + 1) * per]
            with open(os.path.join(HERE, "cmp-%d.tsv" % (b + 1)), "w", encoding="utf-8") as f:
                f.write("# 한자\t훈음\t구성\t유래\tA\tB\n")
                for ch, m in part:
                    r = rows[ch]
                    new_is = rng.choice("AB")
                    a, bb = (m, r["memo"]) if new_is == "A" else (r["memo"], m)
                    f.write("\t".join([ch, r["hun"], shown(r["spec"]), r["origin"], a, bb]) + "\n")
                    key.write(ch + "\t" + new_is + "\n")
    print("유래 as it is", len(auto), "| to review (유래 or 구성)", len(review), "| 외우기 to compare", len(memo))


if __name__ == "__main__":
    main(sys.argv[1])
