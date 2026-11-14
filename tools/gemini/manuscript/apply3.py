#!/usr/bin/env python3
"""Usage: apply3.py — Gemini's supplement (supplement-01.txt, 101 kanji: the held-back sets, the left-out 외우기 and
lines whose part name gave the meaning away), written to link-manuscript3.tsv after link-manuscript2.tsv.

Taken only where it fixes a defect and adds none, on today's data:
- 외우기: the current line has a defect (a part named otherwise than the 구성 line, the meaning word in the scene, or the
  kanji itself in parentheses for a kanji with parts) and the new one has none of them, and passes check_manuscript.py.
- 구성 names: a part's new name is taken when it is the current one, the one the fact review passed for it
  (judged-N.tsv), or a name its shape really has (its 훈음, radical words or usual name); otherwise the current
  name stays, and the new 외우기 must then agree with it.
- 유래: not taken (it would need the fact review again).
"""
import glob
import os
import re
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "..", "origin", "kosei", "polish"))
from check_return import check  # noqa: E402
from common import ASSETS, is_han, load, radical_words, units  # noqa: E402
from apply2 import respec  # noqa: E402

OUT = os.path.join(HERE, "..", "..", "origin", "kosei", "link", "link-manuscript3.tsv")


def spoils(ch, memo):
    scene, mean = memo.rsplit("→", 1)
    words = [w.strip() for w in re.split("[·,]", mean) if w.strip()]
    stems = [w[:-1] if w.endswith("다") and len(w) > 2 else w for w in words]
    return any(s and s in scene for s in stems)


def defects(ch, spec, memo, rad):
    d = []
    if any("부름" in w for w in check(ch, memo, dict(ROWS[ch], spec=spec), rad)[1]):
        d.append("이름 어긋남")
    if spoils(ch, memo):
        d.append("뜻을 미리 말함")
    inside = re.findall(r"\(([^()]*)\)", memo.split("→")[0])
    if units(spec) and any(ch in x for x in inside):
        d.append("자기 가리킴")
    return d


def main():
    global ROWS
    ROWS = load()
    rad = radical_words()
    out = subprocess.run([sys.executable, os.path.join(HERE, "check_manuscript.py"),
                          os.path.join(HERE, "supplement-01.txt"), "--accept"], capture_output=True, text=True).stdout
    passed = {l.split("\t")[0] for l in out.split("# link 줄(한자, 구성, 유래, 외우기, 이유)\n")[1].strip().split("\n")}
    sup = {}
    for line in open(os.path.join(HERE, "supplement-01.txt"), encoding="utf-8"):
        c = [x.strip() for x in line.strip().lstrip("﻿").split(" | ")]
        if len(c) == 4 and c[0] in ROWS:
            sup[c[0]] = ([x.strip() for x in c[1].split(" / ")] if c[1] != "-" else [], c[3])
    judged_ok = {}
    names_gem = {}
    for p in glob.glob(os.path.join(HERE, "returned-*.txt")):
        for line in open(p, encoding="utf-8"):
            c = [x.strip() for x in line.strip().lstrip("﻿").split(" | ")]
            if len(c) == 4:
                names_gem[c[0]] = [x.strip() for x in c[1].split(" / ")]
    for p in glob.glob(os.path.join(HERE, "judged-*.tsv")):
        for line in open(p, encoding="utf-8"):
            c = line.rstrip("\n").split("\t")
            if len(c) >= 3 and c[2] == "ok":
                judged_ok[c[0]] = names_gem.get(c[0], [])
    hun = {}
    with open(os.path.join(ASSETS, "kanji.tsv"), encoding="utf-8") as f:
        for line in f:
            c = line.rstrip("\n").split("\t")
            hun[c[0]] = " ".join(c[i] for i in (1, 10) if len(c) > i)
    usual = dict(l.rstrip("\n").split("\t")[:2] for l in open(os.path.join(HERE, "names.txt"), encoding="utf-8")
                 if not l.startswith("#"))
    rows, report = [], {"외우기": [], "이름": 0, "안 받음": []}
    for ch, (names, memo) in sup.items():
        r = ROWS[ch]
        us = units(r["spec"])
        cur_names = [u[3] for u in us]
        new_names = list(cur_names)
        if names and len(names) == len(us):
            for i, (n, (s, _, _, note)) in enumerate(zip(names, us)):
                seen = hun.get(s, "") + " " + " ".join(rad.get(s, ())) + " " + usual.get(s, "")
                ok_name = judged_ok.get(ch, [])
                if n == note or (i < len(ok_name) and ok_name[i] == n) or all(w in seen for w in n.split()):
                    if "본래" not in note:  # a look-and-origin name stays as the owner chose
                        new_names[i] = n
        spec = respec(r["spec"], new_names) if us else ""
        cur_spec = respec(r["spec"], cur_names) if us else ""
        now = defects(ch, r["spec"], r["memo"], rad)
        new = defects(ch, spec or r["spec"], memo, rad) if ch in passed else ["검사 오류"]
        # Read one by one: 世 still says its meaning (인간 사회), 器 and 示 read badly (입 아가리, 제물 줄기).
        if ch in "世器示":
            new = new + ["읽어 보고 뺌"]
        if now and not new:
            rows.append((ch, spec if spec != cur_spec else "", "", memo))
            report["외우기"].append("%s(%s)" % (ch, ",".join(now)))
            report["이름"] += spec != cur_spec
        else:
            report["안 받음"].append("%s:%s" % (ch, "지금 줄에 결함 없음" if not now else ",".join(new)))
    with open(OUT, "w", encoding="utf-8") as f:
        f.write("# Gemini 원고 보완(2026-11-14, 검수누락-보완): 한자, 새 구성, 새 유래, 새 외우기, 이유. link-manuscript2.tsv 다음에 적용.\n"
                "# 지금 외우기에 결함(이름 어긋남·뜻을 미리 말함·자기 가리킴)이 있고 새 줄엔 없을 때만. tools/gemini/manuscript/apply3.py.\n")
        for ch, s, o, m in rows:
            f.write("\t".join([ch, s, o, m, "Gemini 원고 보완"]) + "\n")
    print("외우기", len(report["외우기"]), "(그중 이름도 바뀜", report["이름"], ")")
    print(" ".join(report["외우기"]))
    print("안 받음", len(report["안 받음"]))
    print(" ".join(report["안 받음"]))


if __name__ == "__main__":
    main()
