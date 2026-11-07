#!/usr/bin/env python3
"""Builds app/src/main/assets/parts.tsv: each kanji's radical (as Japanese dictionaries file it), its parts and,
from KanjiVG, its strokes tagged by part, so the card can colour the radical and the sound part.

Usage: build_parts_data.py <jamdict.db> <unihan.db> <kanjivg_dir> <radicals.tsv> <kanji.tsv> <out.tsv> [sound.tsv]
                           [graphicsJa.txt] [stroke_tags.tsv]
  jamdict.db  - the SQLite database of the PyPI package jamdict-data 1.5 (KANJIDIC2: the "classical" radical,
                which KANJIDIC takes from Shibano's JIS Kanwa Jiten, so 来 is under 木 and 旧 under 日)
  unihan.db   - data/unihan.db of the npm package cjk-unihan 0.0.3 (kRSKangXi, for the few characters KANJIDIC2
                lacks)
  kanjivg_dir - KanjiVG's kanji/ folder (https://github.com/KanjiVG/kanjivg, CC BY-SA 3.0); only the plain
                <hex>.svg files are read. The script fetched them one by one from
                https://raw.githubusercontent.com/KanjiVG/kanjivg/master/kanji/<hex>.svg (6,680 of the 13,436
                characters exist)
  radicals.tsv - app/src/main/assets/radicals.tsv (the radical forms and names, written by hand)
  kanji.tsv   - app/src/main/assets/kanji.tsv (which characters to write)
  sound.tsv   - tools/origin/sound.tsv: corrections to KanjiVG's sound parts from the origin-note review, one line
                per kanji: kanji<TAB>part<TAB>reason, where part is the top-level part that is the sound part (it
                becomes the blue one) or "-" (no sound part: KanjiVG's mark was wrong)
  graphicsJa.txt - AnimCJK's Japanese stroke outlines (https://github.com/parsimonhi/animCJK, graphicsJa.txt;
                derived from the Arphic PL KaitiM fonts, Arphic Public License: licenses/ARPHICPL.TXT): each stroke
                as a filled brush (楷書) outline in Japanese shapes. Where a character has them with the same number
                of strokes as KanjiVG (all 상용한자), they replace KanjiVG's pen lines and take KanjiVG's tags stroke
                by stroke (both follow Japanese stroke order); characters only AnimCJK has get untagged outlines.
  stroke_tags.tsv - tools/stroke_tags.tsv: stroke colours corrected after looking at every 상용한자 drawn
                (kanji<TAB>tags<TAB>reason; tags = one of r, p, - per stroke in writing order), used when the length
                matches the strokes.

One line per character, sorted by UTF-8 bytes (the app binary-searches it), tab separated:
  char  radical  form  position  parts  strokes  family  outline  starts  medians
radical: the Kangxi radical number. form / position: the radical as it appears in this character and where
(氵, left), from the KanjiVG group that holds it; empty when KanjiVG has no such group. parts: the top-level
parts, space separated, each "element/original/flags" or "element/original/flags/inner" (氵/水/r 毎//p), the flags
agreeing with the stroke colours (part_flags): r = the radical, p = the sound part, R = holds the radical without
being it (昼's 旦 holds 日), P = holds the sound part (塗's 涂 holds 余, then [inner] = 余); a sound part KanjiVG
marks on a group of several parts that names one character is one part, its pieces as original (低: 氐/氏+一/p). strokes: the stroke paths in writing order, "|" separated, each one tag character (r radical, p sound
part, - other) and the SVG path in KanjiVG's 109 x 109 box with numbers rounded to one decimal. family: up to
5 other 상용한자 (school grade, then KANJIDIC frequency order) with the same sound part (the same blue parts, when
the drawing makes several blue) and an on reading in common
with this character, voicing ignored, the part itself first when it is one (晴 セイ: 青清静情精), so the card can show
the sound series; empty when there is none.
outline: "f" when the strokes are AnimCJK filled outlines in a 1024 x 1024 box (y down), empty for KanjiVG pen
lines in its 109 x 109 box. starts: for the outlines, where the brush starts each stroke ("x,y;x,y;…", from
AnimCJK's stroke medians, same box), so tapping the kanji can number its strokes; empty for pen lines, which start
at their path's first point. medians: for the outlines, the line the brush follows through each stroke
("x,y,x,y,…|…", AnimCJK's medians, same box), so the stroke order can be written along it; empty for pen lines.
"""
import json
import os
import re
import sqlite3
import sys
import unicodedata
import xml.etree.ElementTree as ET

SVG = "{http://www.w3.org/2000/svg}"
KVG = "{http://kanjivg.tagaini.net}"

# CJK Radicals Supplement code points KanjiVG uses, as the ordinary characters every CJK font has.
DISPLAY = {"⻌": "辶", "⻍": "辶", "⻖": "阝", "⻏": "阝", "⺨": "犭", "⻞": "飠", "⺤": "爫", "⺦": "丬"}
POSITIONS = {"left": "left", "right": "right", "top": "top", "bottom": "bottom", "tare": "tare", "tarec": "tare",
             "nyo": "nyo", "nyoc": "nyo", "kamae": "kamae", "kamaec": "kamae", "kamae1": "kamae", "kamae2": "kamae"}
# Where KANJIDIC files a character under a radical KanjiVG draws as another form: 全 is under 入 (11) but its top
# is drawn as 人, 夏 is under 夊 (35) but drawn with 夂.
ALIASES = {"全": {"人"}, "夏": {"夂"}}


def load_forms(path):
    """form -> the radical numbers it can stand for (月 → 74 and 130)."""
    out = {}
    for line in open(path, encoding="utf-8"):
        if line.startswith("#") or not line.strip():
            continue
        f = line.rstrip("\n").split("\t")
        out.setdefault(f[1], set()).add(int(f[0]))
    for k, v in DISPLAY.items():
        out.setdefault(v, set()).update(out.get(k, set()))
        out.setdefault(k, set()).update(out.get(v, set()))
    return out


def unvoiced(kana):
    """ガク → カク: the sound series ignores voicing (板 バン shares 反 ハン)."""
    return "".join(c for c in unicodedata.normalize("NFD", kana) if c not in "\u3099\u309a")


def load_outlines(path):
    """AnimCJK: character -> (its stroke outlines, each stroke's median: the brush's path), in makemeahanzi's frame
    (1024 wide, y up, baseline at 900)."""
    out = {}
    if not path:
        return out
    for line in open(path, encoding="utf-8"):
        if line.strip():
            d = json.loads(line)
            out[d["character"]] = (d["strokes"], d["medians"])
    return out


OUTLINE = re.compile(r"[MmLlCcQqZz]|-?\d+(?:\.\d+)?")


def compact_outline(d):
    """An AnimCJK stroke ("M246,711C260,695 …Z", y up) flipped to y down (y' = 900 - y) and written with relative
    integer coordinates after the first point ("M246,189c14,16 …z"), about half the size."""
    toks = OUTLINE.findall(d)
    out = []
    x = y = sx = sy = 0
    i = 0
    cmd = None
    first = True
    sizes = {"M": 2, "L": 2, "C": 6, "Q": 4}

    def num(v):
        return str(int(round(v)))

    while i < len(toks):
        t = toks[i]
        if t.isalpha():
            cmd = t
            i += 1
            if cmd in "Zz":
                out.append("z")
                x, y = sx, sy
            continue
        n = sizes[cmd.upper()]
        vals = [float(v) for v in toks[i:i + n]]
        i += n
        pts = []
        for k in range(0, n, 2):
            px, py = vals[k], vals[k + 1]
            if cmd.islower():
                px, py = x + px, y - py
            else:
                py = 900 - py
            pts.append((px, py))
        if cmd.upper() == "M" and first:
            out.append("M" + num(pts[0][0]) + "," + num(pts[0][1]))
            first = False
        else:
            # Every point of a segment is relative to the segment's start (SVG relative commands).
            body = ""
            for v in [num(c) for px, py in pts for c in (px - x, py - y)]:
                body += ("," if body and not v.startswith("-") else "") + v
            out.append(cmd.lower() + body)
        x, y = pts[-1]
        if cmd.upper() == "M":
            sx, sy = x, y
    return "".join(out)


def load_order(jamdict_path):
    """상용한자 -> sort key (school grade, then frequency): the order the sound series is listed in."""
    out = {}
    c = sqlite3.connect(jamdict_path)
    for lit, grade, freq in c.execute("select literal, grade, freq from character"):
        g = int(grade) if grade else 0
        if 1 <= g <= 8:
            out[lit] = (g, int(freq) if freq else 9999)
    return out


def load_radicals(jamdict_path, unihan_path):
    rad = {}
    c = sqlite3.connect(jamdict_path)
    for lit, v in c.execute("select ch.literal, r.value from radical r join character ch on ch.ID = r.cid "
                            "where r.rad_type = 'classical'"):
        rad[lit] = int(v)
    u = sqlite3.connect(unihan_path)
    for ch, rs in u.execute("select character, kRSKangXi from unihan where kRSKangXi is not null"):
        m = re.match(r"(\d+)", rs or "")
        if m and len(ch) == 1:
            rad.setdefault(ch, int(m.group(1)))
    return rad


NUM = re.compile(r"[MmCcSsLlZzHhVv]|-?(?:\d+\.?\d*|\.\d+)(?:e-?\d+)?")


def compact(d):
    """The path with numbers rounded to one decimal: "M20.38,14.75c3.31,1.47" → "M20.4,14.8c3.3,1.5"."""
    out = []
    prev_num = False
    for tok in NUM.findall(d):
        if tok[0].isalpha():
            out.append(tok)
            prev_num = False
            continue
        v = round(float(tok), 1)
        s = ("%.1f" % v).rstrip("0").rstrip(".")
        if s in ("-0", ""):
            s = "0"
        if prev_num and not s.startswith("-"):
            out.append(",")
        out.append(s)
        prev_num = True
    return "".join(out)


def parse_svg(path):
    root = ET.parse(path).getroot()
    for g in root.iter(SVG + "g"):
        if g.get("id", "").startswith("kvg:StrokePaths"):
            kids = [k for k in g if k.tag == SVG + "g"]
            return kids[0] if kids else None
    return None


def walk(g, parents, out_paths, out_groups):
    """Paths in document order with their ancestor groups; every group with its parent and depth."""
    for k in g:
        if k.tag == SVG + "g":
            out_groups.append((k, parents))
            walk(k, parents + [k], out_paths, out_groups)
        elif k.tag == SVG + "path":
            out_paths.append((k.get("d", ""), parents))


def same_part(a, b):
    """Two groups that are halves of one part (門 drawn around 口: kvg:part 1 and 2)."""
    return a is b or (a.get(KVG + "element") == b.get(KVG + "element") and a.get(KVG + "part") is not None
                      and b.get(KVG + "part") is not None)


def load_sound_fix(path):
    out = {}
    if not path:
        return out
    for line in open(path, encoding="utf-8"):
        f = line.rstrip("\n").split("\t")
        if len(f) >= 2 and f[0] and not f[0].startswith("#"):
            out[f[0]] = f[1].strip()
    return out


def top_level(g):
    """The character's top-level parts: the element groups under its group, looked through unnamed groups."""
    out = []
    for k in g:
        if k.tag != SVG + "g":
            continue
        if k.get(KVG + "element"):
            out.append(k)
        else:
            out.extend(top_level(k))
    return out


def build_one(ch, n, svg_path, forms, sound_fix=None):
    """(form, position, parts, strokes) for one character; [sound_fix] overrides KanjiVG's sound part ("-": none)."""
    top = parse_svg(svg_path)
    if top is None:
        return "", "", "", ""
    paths, groups = [], []
    walk(top, [top], paths, groups)

    def matches(g):
        names = {g.get(KVG + "element"), g.get(KVG + "original")} - {None}
        if names & ALIASES.get(ch, set()):
            return True
        return any(n in forms.get(x, set()) for x in names)

    radical = None
    order = {"general": 0, "tradit": 1, "nelson": 2}
    tagged = [(order.get(g.get(KVG + "radical"), 3), i, g) for i, (g, _) in enumerate(groups)
              if g.get(KVG + "radical") and matches(g)]
    if tagged:
        radical = min(tagged, key=lambda t: (t[0], t[1]))[2]
    else:
        # No group marked as the radical: the outermost group of the radical's shape.
        cands = [(len(p), i, g) for i, (g, p) in enumerate(groups) if matches(g)]
        if cands:
            radical = min(cands, key=lambda t: (t[0], t[1]))[2]
    rad_groups = [g for g, _ in groups if radical is not None and same_part(g, radical)]
    if sound_fix is None:
        phon_groups = [g for g, _ in groups if g.get(KVG + "phon") is not None]
    else:
        phon_groups = [g for g in top_level(top) if DISPLAY.get(g.get(KVG + "element"), g.get(KVG + "element"))
                       == sound_fix]
    whole = top.get(KVG + "element") == ch and radical is None and any(
        n in forms.get(x, set()) for x in (ch,))

    strokes = []
    for d, parents in paths:
        tag = "-"
        if any(g in parents for g in rad_groups):
            tag = "r"
        elif any(g in parents for g in phon_groups):
            tag = "p"
        strokes.append(tag + compact(d))

    def strokes_in(g):
        return {i for i, (_, ps) in enumerate(paths) if g in ps}

    def inner_sound(g):
        """The element of a sound group nested in part [g] (涂's 余), or empty."""
        for x in g.iter(SVG + "g"):
            if x is not g and x in phon_groups and x.get(KVG + "element"):
                return DISPLAY.get(x.get(KVG + "element"), x.get(KVG + "element"))
        return ""

    # Top-level parts: the children of the character's group; a group without an element is looked into.
    parts = []
    # Every part made, wherever it was put, so a second half finds its first.
    made = []

    def collect(g, parts):
        for k in g:
            if k.tag != SVG + "g":
                continue
            el = k.get(KVG + "element")
            if not el:
                # An unnamed group that KanjiVG marks as the sound part, spanning two or more parts and naming one
                # character (低: 氏 and 一 under kvg:phon="氐"; V and T mark a variant or a cut-down form): that
                # character is one part, the pieces it is drawn with kept to show when a font lacks it (and to go
                # back to when the reviewed stroke colours say it is no sound part after all).
                ph = k.get(KVG + "phon") if sound_fix is None else None
                m = re.fullmatch(r"(.)[VT]?", ph or "")
                kids = []
                collect(k, kids)
                if m and ord(m.group(1)) >= 0x3400 and len(kids) >= 2:
                    flags = "p" + ("r" if radical is not None and radical in k.iter() else "")
                    pieces = "+".join(DISPLAY.get(x[0], x[0]) for x in kids)
                    parts.append([m.group(1), pieces, flags, strokes_in(k), "", kids])
                else:
                    parts.extend(kids)
                continue
            half = next((p for p in made if p[0] == el and k.get(KVG + "part") is not None), None)
            if half is not None:
                # The second half of a part drawn around another (門 around 口): its strokes join the first's.
                half[3] |= strokes_in(k)
                continue
            flags = ""
            if any(same_part(k, r) for r in rad_groups) or (radical is not None and radical in k.iter()):
                flags += "r"
            if (k.get(KVG + "phon") is not None) if sound_fix is None else (k in phon_groups):
                flags += "p"
            parts.append([el, k.get(KVG + "original") or "", flags, strokes_in(k), inner_sound(k), None])
            made.append(parts[-1])

    collect(top, parts)
    if len(parts) < 2:
        parts = []
    form, pos = "", ""
    if radical is not None:
        form = DISPLAY.get(radical.get(KVG + "element") or "", radical.get(KVG + "element") or "")
        pos = POSITIONS.get(radical.get(KVG + "position") or "", "")
    elif whole:
        form = ch

    def display(ps):
        for p in ps:
            p[0] = DISPLAY.get(p[0], p[0])
            display(p[5] or [])
    display(parts)
    return form, pos, parts, strokes, radical is top


def part_flags(parts, tags, form):
    """The parts as "element/original/flags[/inner]", their flags made to agree with the stroke colours [tags]
    (one of r, p, - per stroke, after stroke_tags.tsv), so the 구성 line says what the drawing shows:
      r  the part is the radical; a part named otherwise that holds it (旦 holding 日, 音 holding 立) gets R
      p  the part is the sound part (also when every one of its strokes is blue though KanjiVG marks a group
         above it: 新's 立 and 木); a part with only some blue strokes gets P, with the sound part's element as
         [inner] when KanjiVG names it (塗's 涂 holding 余)
    A part KanjiVG marks as the sound part but none of whose strokes is blue (after the review) loses p."""
    out = []
    for el, orig, flags, idx, inner, kids in parts:
        mine = [tags[i] for i in sorted(idx) if i < len(tags)]
        if kids and "p" not in mine:
            # A sound part put together from its pieces that the review says is none: the pieces again.
            out.append(part_flags(kids, tags, form))
            continue
        r, p = "r" in flags, "p" in flags
        big_r = big_p = False
        if r and form and form not in (el, orig):
            # Named otherwise than the radical: the part holds it (旦 holds 日; 画's 由 is drawn over 田).
            r, big_r = False, True
        if not r and mine:
            if all(t == "p" for t in mine):
                p = True
            elif p and "p" not in mine:
                p = False
            elif not p and "p" in mine:
                big_p = True
        f = ("r" if r else "") + ("p" if p else "") + ("R" if big_r else "") + ("P" if big_p else "")
        tail = "/" + inner if big_p and inner else ""
        out.append("%s/%s/%s%s" % (el, orig, f, tail))
    return " ".join(out)


def load_tag_fix(path):
    out = {}
    if not path:
        return out
    for line in open(path, encoding="utf-8"):
        f = line.rstrip("\n").split("\t")
        if len(f) >= 2 and f[0] and not f[0].startswith("#") and set(f[1]) <= set("rp-"):
            out[f[0]] = f[1]
    return out


def main(jamdict_path, unihan_path, kanjivg_dir, radicals_path, kanji_path, out_path, sound_path=None,
         outlines_path=None, tags_path=None):
    forms = load_forms(radicals_path)
    sound_fix = load_sound_fix(sound_path)
    outlines = load_outlines(outlines_path)
    tag_fix = load_tag_fix(tags_path)
    rad = load_radicals(jamdict_path, unihan_path)
    chars = [line.split("\t", 1)[0] for line in open(kanji_path, encoding="utf-8")]
    lines = []
    with_strokes = matched = with_brush = fixed_tags = 0
    for ch in chars:
        if len(ch) != 1:
            continue
        n = rad.get(ch, 0)
        svg = os.path.join(kanjivg_dir, "%05x.svg" % ord(ch))
        form = pos = parts = strokes = ""
        kvg_parts, kvg_strokes, radical_whole = [], [], False
        if os.path.exists(svg):
            form, pos, kvg_parts, kvg_strokes, radical_whole = build_one(ch, n, svg, forms, sound_fix.get(ch))
            with_strokes += 1
            matched += 1 if form else 0
        kind = ""
        brush, medians = outlines.get(ch, (None, None))
        start_s = median_s = ""
        tags = [x[0] for x in kvg_strokes]
        if ch in tag_fix and len(tag_fix[ch]) == len(brush or tags or []):
            tags = list(tag_fix[ch])
            fixed_tags += 1
        parts = part_flags(kvg_parts, tags, form)
        if form and (radical_whole or (tags and all(t == "r" for t in tags))):
            # The radical is the whole character (木, or 来 drawn as one 木 group): nothing to single out.
            tags = ["-"] * len(tags)
        strokes = "|".join(t + x[1:] for t, x in zip(tags, kvg_strokes))
        if brush and (not tags or len(tags) == len(brush)):
            # The brush outlines, coloured by KanjiVG's tag for the same stroke.
            strokes = "|".join((tags[i] if tags else "-") + compact_outline(b) for i, b in enumerate(brush))
            start_s = ";".join("%d,%d" % (round(m[0][0]), round(900 - m[0][1])) for m in medians)
            median_s = "|".join(",".join("%d,%d" % (round(x), round(900 - y)) for x, y in m) for m in medians)
            kind = "f"
            with_brush += 1
        if not n and not strokes:
            continue
        lines.append([ch, str(n or ""), form, pos, parts, strokes, kind, start_s, median_s])
    # The sound series: every 상용한자 whose sound part is the same element.
    order = load_order(jamdict_path)
    sound = {}
    for row in lines:
        median_s = row.pop()
        start_s = row.pop()
        kind = row.pop()
        # The sound part's element; when the drawing makes several parts blue (診: 人 and 彡), all of them, so
        # only characters with the same blue parts are one series.
        sound_parts = [p.split("/")[0] for p in row[4].split() if "p" in p.split("/")[2]]
        row.append("+".join(sound_parts))
        row.append(kind)
        row.append(start_s)
        row.append(median_s)
        if row[6] and row[0] in order:
            sound.setdefault(row[6], []).append(row[0])
    on = {}
    for line in open(kanji_path, encoding="utf-8"):
        f = line.rstrip("\n").split("\t")
        if len(f) > 3:
            on[f[0]] = {unvoiced(r.strip("-")) for r in f[3].split("・") if r.strip("-")}
    for row in lines:
        el = row[6]
        if not el:
            continue
        fam = sorted(sound.get(el, []), key=lambda c: order[c])
        if el in order:
            fam = [el] + [c for c in fam if c != el]
        mine = on.get(row[0], set())
        row[6] = "".join([c for c in fam if c != row[0] and on.get(c, set()) & mine][:5])
    lines.sort(key=lambda r: r[0].encode("utf-8"))
    with open(out_path, "w", encoding="utf-8", newline="\n") as f:
        for row in lines:
            f.write("\t".join(row) + "\n")
    print(len(lines), "lines,", with_strokes, "with KanjiVG strokes,", matched, "with the radical found in them,",
          with_brush, "drawn with AnimCJK brush outlines,", fixed_tags, "with corrected stroke colours")


if __name__ == "__main__":
    main(*sys.argv[1:10])
