#!/usr/bin/env python3
"""Writes in-1.tsv … in-10.tsv: the facts each 상용한자's origin note was written from (see BRIEF.md).

Usage: make_inputs.py <makemeahanzi dictionary.txt> [out_dir]
  dictionary.txt - https://raw.githubusercontent.com/skishore/makemeahanzi/master/dictionary.txt (LGPL 3.0; only its
                   English etymology hint is passed on, as a hint for the writer, not shipped)
Reads app/src/main/assets/kanji.tsv, parts.tsv and radicals.tsv. Columns: 한자, 정자, 훈음, 일본어 뜻, 음독, 부수,
구성 (KanjiVG's top-level parts with their 훈음 and {부수,소리} marks), the makemeahanzi hint. Sorted by school grade
then character, split into ten batches.
"""
import json
import os
import sys

here = os.path.dirname(os.path.abspath(__file__))
assets = os.path.join(here, "..", "..", "app", "src", "main", "assets")


def main(dictionary, out_dir=here):
    kan = {}
    for line in open(os.path.join(assets, "kanji.tsv"), encoding="utf-8"):
        f = line.rstrip("\n").split("\t")
        kan[f[0]] = f
    parts = {}
    for line in open(os.path.join(assets, "parts.tsv"), encoding="utf-8"):
        f = line.rstrip("\n").split("\t")
        parts[f[0]] = f
    rad = {}
    for line in open(os.path.join(assets, "radicals.tsv"), encoding="utf-8"):
        if line.startswith("#") or not line.strip():
            continue
        f = line.rstrip("\n").split("\t")
        rad.setdefault(int(f[0]), f)
    mmh = {}
    for line in open(dictionary, encoding="utf-8"):
        d = json.loads(line)
        mmh[d["character"]] = d

    def hun(ch):
        f = kan.get(ch)
        if not f:
            return ""
        h = f[10] if len(f) > 10 and f[10] else f[1]
        return h.split(" / ")[0].split(", ")[0]

    def ety(ch):
        d = mmh.get(ch)
        if not d or not d.get("etymology"):
            return ""
        e = d["etymology"]
        s = e.get("type") + ": " + (e.get("hint") or "")
        if e.get("semantic"):
            s += " | semantic " + e["semantic"]
        if e.get("phonetic"):
            s += " | phonetic " + e["phonetic"]
        return s

    rows = []
    for ch, f in kan.items():
        if f[6] not in ("1", "2", "3", "4", "5", "6", "8"):
            continue
        var = f[2]
        p = parts.get(ch, [""] * 7)
        n = int(p[1]) if p[1] else 0
        r = rad.get(n)
        radtxt = f"{n} {p[2] or (r[1] if r else '')} ({r[4]})" if r else ""
        comp = []
        for item in p[4].split():
            el, orig, fl = item.split("/")
            tag = (["부수"] if "r" in fl else []) + (["소리"] if "p" in fl else [])
            h = hun(el) or (hun(orig) if orig else "")
            comp.append(el + (f"(원형 {orig})" if orig and orig != el else "") + (f"[{h}]" if h else "")
                        + ("{" + ",".join(tag) + "}" if tag else ""))
        e = ety(ch) or (("정자 " + var + " → " + ety(var)) if var and ety(var) else "")
        rows.append([ch, var, hun(ch), f[8], f[3], radtxt, " + ".join(comp), e])
    rows.sort(key=lambda r: (int(kan[r[0]][6]), r[0]))
    batches = 10
    size = (len(rows) + batches - 1) // batches
    for i in range(batches):
        with open(os.path.join(out_dir, f"in-{i + 1}.tsv"), "w", encoding="utf-8", newline="\n") as fo:
            fo.write("# 한자\t정자\t훈음\t일본어 뜻\t음독\t부수\t구성(KanjiVG)\t참고: makemeahanzi 어원(중국어 자료, 틀릴 수 있음)\n")
            for r in rows[i * size:(i + 1) * size]:
                fo.write("\t".join(x.replace("\t", " ") for x in r) + "\n")
    print(len(rows), "kanji in", batches, "batches of", size)


if __name__ == "__main__":
    main(*sys.argv[1:3])
