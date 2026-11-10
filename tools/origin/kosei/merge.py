#!/usr/bin/env python3
"""The last step of tools/origin/merge.py: the 구성 line written to match the 유래 (BRIEF.md), as origin.tsv's
fourth column, and the 유래 lines changed to make that link.

Sources, later ones winning: auto.tsv (convert.py: kanji whose 유래 already names every part plainly), out-N.tsv
(written by hand from in-N.tsv following BRIEF.md: 한자, 구성, 새 유래 or empty, 메모), fix-N.tsv (the independent
review, REVIEW.md: the same four columns for the lines it changed) and fix-final.tsv (decided by hand after the
review). Each unit gets the mark the app colours by: ^ for the radical's part, ~ for a part holding the radical,
found by matching the units to parts.tsv (kosei.match); a free line ("!") marks the unit written as the radical's
shape. Then the shapes are written as Japanese print shows them in that kanji (示 → 礻 in 祉, 兌 → 兑) and every
sound part gets an on reading: the one the 유래 gives it, else the kanji's own (他: 也 → タ). A spec "-" means no
구성 line at all (凸, whose KanjiVG parts are not what one sees). memo.tsv replaces 외우기 lines (kanji, new line,
reason).
"""
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import kosei  # noqa: E402

ASSETS = os.path.join(HERE, "..", "..", "..", "app", "src", "main", "assets")


def read(path, width):
    out = []
    if os.path.exists(path):
        for line in open(path, encoding="utf-8"):
            if line.strip() and not line.startswith("#"):
                f = line.rstrip("\n").split("\t")
                out.append([x.strip() for x in f] + [""] * (width - len(f)))
    return out


# A part written by its full form where the kanji shows a variant: the variant, as printed (祉's 礻, 飢's 飠).
VARIANTS = {"示": "礻", "食": "飠", "网": "罒", "羊": "⺷", "茲": "兹", "襄": "㐮"}
# Shapes KanjiVG names by their traditional form where Japanese print has another (税, 説: 兌 → 兑).
JAPANESE = {"兌": "兑"}
KATA = r"[ァ-ヺー]+"


def unit_text(pieces, role, reading, note):
    return "+".join(pieces) + ("[%s]" % " ".join(x for x in (role, reading) if x) if role else "") + \
        ("(%s)" % note if note else "")


def marked(spec, plist, form="", yurae="", on=""):
    """The spec with ^ / ~ in front of the units holding the radical's part, shapes as printed and readings for
    the sound parts, or None if it does not fit the parts. A free line ("!") marks the unit written as the radical's
    shape [form] (祭's 示)."""
    if spec == "-":
        return "-"
    free = spec.startswith("!")
    # A free line may mark a unit itself: "~" for one holding the radical's part (乗's 千+卄, holding 丿), "^" for
    # the radical's part when its shape is not among the unit's pieces.
    given = []
    if free:
        raw = [x.strip() for x in spec[1:].split(" + ")]
        given = [x[0] if x[:1] in ("^", "~") else "" for x in raw]
        spec = "!" + " + ".join(x.lstrip("^~") for x in raw)
    units = kosei.parse(spec[1:] if free else spec)
    cover = [[] for _ in units] if free else kosei.match(units, plist)
    if cover is None:
        return None
    out = []
    for n, ((pieces, role, reading, note), idx) in enumerate(zip(units, cover)):
        if len(pieces) == 1 and len(idx) == 1:
            el, orig = plist[idx[0]][0], plist[idx[0]][1]
            if pieces[0] == orig and VARIANTS.get(orig) == el:
                pieces = [el]
        pieces = [JAPANESE.get(x, x) for x in pieces]
        if "소리" in role and not reading:
            reading = sound_reading(pieces, note, yurae) or on
        if free:
            # The unit with the radical's shape among its pieces (旅's 方+𠂉; 曹's 曰, which KanjiVG files as 日).
            mark = given[n] or ("^" if form and (form in pieces or (form == "日" and "曰" in pieces)) else "")
        else:
            flags = "".join(plist[i][2] for i in idx)
            mark = "^" if "r" in flags else "~" if "R" in flags else ""
        out.append(mark + unit_text(pieces, role, reading, note))
    return ("!" if free else "") + " + ".join(out)


def sound_reading(pieces, note, yurae):
    """The on reading the 유래 gives the sound part: after its shape, or after a kanji its note names (者 for 曰)."""
    for ch in pieces + [c for c in note if "\u3400" <= c <= "\u9fff" or c >= "\U00020000"]:
        m = re.search(re.escape(ch) + r"\((" + KATA + r")", yurae) or \
            re.search(re.escape(ch) + r"\([^()]*?(" + KATA + r")\)", yurae)
        if m:
            return m.group(1)
    return ""


def apply(merged, fix_particles):
    """Adds the 구성 column to merged {kanji: [kanji, 유래, 외우기]} and puts in the changed 유래 lines."""
    on = {}
    for line in open(os.path.join(ASSETS, "kanji.tsv"), encoding="utf-8"):
        f = line.rstrip("\n").split("\t")
        if len(f) > 3 and f[3]:
            on[f[0]] = f[3].split("・")[0].strip("-")
    parts, forms = {}, {}
    for line in open(os.path.join(ASSETS, "parts.tsv"), encoding="utf-8"):
        f = line.rstrip("\n").split("\t")
        parts[f[0]] = [x.split("/") + [""] * (4 - len(x.split("/"))) for x in f[4].split()] if len(f) > 4 else []
        forms[f[0]] = f[2] if len(f) > 2 else ""
    spec, origin = {}, {}
    for r in read(os.path.join(HERE, "auto.tsv"), 2):
        spec[r[0]] = r[1]
    # Every batch there is, in order (not stopping at a missing number).
    batches = sorted(int(f[4:-4]) for f in os.listdir(HERE) if f.startswith("out-") and f.endswith(".tsv"))
    for n in batches:
        for name in ("out-%d.tsv" % n, "fix-%d.tsv" % n):
            for r in read(os.path.join(HERE, name), 4):
                if r[1]:
                    spec[r[0]] = r[1]
                if r[2] == "원래":
                    # The review put the original 유래 back.
                    origin.pop(r[0], None)
                elif r[2]:
                    origin[r[0]] = r[2]
    # Then the outside review (gemini/BRIEF.md): its findings checked kanji by kanji, gem-N.tsv for 구성 and 유래,
    # memo-N.tsv for 외우기; full/: every other 상용한자 read again the same way (full/BRIEF.md), fix-N.tsv and
    # memo-N.tsv; and audit/: the findings of KANJI_CONTENT_AUDIT_2026-11-08.md checked one by one (audit/README.md).
    # Last, fix-final.tsv and memo.tsv: decided by hand after all of them (摂, the lines made to agree across batches).
    memo = {}
    for folder, prefix in (("gemini", "gem-"), ("full", "fix-"), ("audit", "fix-")):
        path = os.path.join(HERE, folder)
        for name in sorted(os.listdir(path)) if os.path.isdir(path) else []:
            if name.startswith(prefix) and name.endswith(".tsv"):
                for r in read(os.path.join(path, name), 4):
                    if r[1]:
                        spec[r[0]] = r[1]
                    if r[2]:
                        origin[r[0]] = r[2]
            elif name.startswith("memo-") and name.endswith(".tsv"):
                memo.update({r[0]: r[1] for r in read(os.path.join(path, name), 3) if r[1]})
    for r in read(os.path.join(HERE, "fix-final.tsv"), 4):
        if r[1]:
            spec[r[0]] = r[1]
        if r[2]:
            origin[r[0]] = r[2]
    memo.update({r[0]: r[1] for r in read(os.path.join(HERE, "memo.tsv"), 3) if r[1]})
    # After all of them, easy/: every 유래 read again for words a learner knows and for its tie to the 구성 line
    # (easy/BRIEF.md). It rewrote the lines as merged above, so it goes last.
    path = os.path.join(HERE, "easy")
    for name in sorted(os.listdir(path)) if os.path.isdir(path) else []:
        if name.startswith("easy-") and name.endswith(".tsv"):
            for r in read(os.path.join(path, name), 4):
                if r[1]:
                    spec[r[0]] = r[1]
                if r[2]:
                    origin[r[0]] = r[2]
        elif name.startswith("memo-") and name.endswith(".tsv"):
            memo.update({r[0]: r[1] for r in read(os.path.join(path, name), 3) if r[1]})
    # Last, link/: the four rows (부수, 구성, 유래, 외우기) read together for one story and a 외우기 the learner can
    # picture (link/BRIEF.md). link-N.tsv (written, then reviewed) and link-final.tsv (the lead's last pass) hold
    # 한자, 구성, 유래, 외우기, 이유; an empty column keeps the line. Numeric order, then link-final.tsv.
    path = os.path.join(HERE, "link")
    names = sorted((f for f in os.listdir(path) if re.match(r"^link-\d+\.tsv$", f)), key=lambda f: int(f[5:-4])) \
        if os.path.isdir(path) else []
    # Then link-lead.tsv: the lead's decisions on the reviewers' flags and across batches, made before round 2 so
    # that round 2 reads them; then round2/fix-N.tsv: the merged rows read again by a fresh reader for what still
    # fails (CRITIC.md).
    if os.path.exists(os.path.join(path, "link-lead.tsv")):
        names.append("link-lead.tsv")
    r2 = os.path.join(path, "round2")
    names += sorted((os.path.join("round2", f) for f in os.listdir(r2) if re.match(r"^fix-\d+\.tsv$", f)),
                    key=lambda f: int(f.split("-")[-1][:-4])) if os.path.isdir(r2) else []
    # Last, link-gemini.tsv: the outside review of current_kanji_structure.md, applied where it holds (link/README.md).
    # Then link-round3.tsv: its second round (empty 구성 notes filled, bridge notes, the last abstract 훈).
    # Then link-names.tsv: one name for a part that means the same in every kanji (貝 돈, 竹 대나무).
    # Then link-polish.tsv: 외우기 lines rewritten by an outside writer and accepted (polish/README.md).
    tail = [n for n in ("link-final.tsv", "link-gemini.tsv", "link-round3.tsv", "link-names.tsv", "link-polish.tsv")
            if os.path.exists(os.path.join(path, n))]
    for name in names + tail:
        for r in read(os.path.join(path, name), 5):
            if r[1]:
                spec[r[0]] = r[1]
            if r[2]:
                origin[r[0]] = r[2]
            if r[3]:
                memo[r[0]] = r[3]
    bad = []
    for ch, row in merged.items():
        if ch in origin:
            row[1] = fix_particles(origin[ch])
        if ch in memo:
            row[2] = fix_particles(memo[ch])
        s = spec.get(ch, "")
        m = marked(s, parts.get(ch, []), forms.get(ch, ""), row[1], on.get(ch, "")) if s else ""
        if m is None:
            bad.append(ch)
            m = ""
        merged[ch] = row[:3] + [m]
    print(sum(1 for r in merged.values() if r[3]), "구성 lines,", len(origin), "유래 changed to link,", len(memo),
          "외우기 replaced,", len(bad), "not fitting the parts:", "".join(bad))
    return merged
