#!/usr/bin/env python3
"""Builds app/src/main/assets/words.tsv: JMdict's written forms and their readings, for "this word is read …".

Usage: build_word_data.py <jamdict.db> <out.tsv>
  jamdict.db - the SQLite database of the PyPI package jamdict-data 1.5 (contains JMdict)

One line per written form, sorted by code point, which is also UTF-8 byte order (the app binary-searches the bytes):
  form  reading・reading…
Readings JMdict marks common (ichi1, news1, …) when there are any, else all; entry order, at most 2.
"""
import sqlite3
import sys


def main(db_path, out_path):
    c = sqlite3.connect(db_path)
    prio_kana = {kid for (kid,) in c.execute("select distinct kid from KNP")}
    obsolete = {kid for (kid,) in c.execute("select kid from KNI where text in ('ok', 'sk')")}
    restrict = {}
    for kid, text in c.execute("select kid, text from KNR"):
        restrict.setdefault(kid, set()).add(text)
    kana_by_entry = {}
    for kid, idseq, text, nokanji in c.execute("select ID, idseq, text, nokanji from Kana order by ID"):
        if nokanji or kid in obsolete:
            continue
        kana_by_entry.setdefault(idseq, []).append((kid, text))
    readings = {}
    for idseq, form in c.execute("select idseq, text from Kanji order by ID"):
        for kid, text in kana_by_entry.get(idseq, []):
            if kid in restrict and form not in restrict[kid]:
                continue
            readings.setdefault(form, []).append((0 if kid in prio_kana else 1, text))
    lines = []
    for form, rs in readings.items():
        best = min(r[0] for r in rs)
        seen, out = set(), []
        for p, text in rs:
            if p == best and text not in seen:
                seen.add(text)
                out.append(text)
        lines.append((form, "・".join(out[:2])))
    lines.sort(key=lambda x: x[0].encode("utf-8"))
    with open(out_path, "w", encoding="utf-8", newline="\n") as f:
        for form, rs in lines:
            f.write(form + "\t" + rs + "\n")
    print(len(lines), "lines")


if __name__ == "__main__":
    main(*sys.argv[1:3])
