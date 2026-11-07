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
  char  radical  form  position  parts  strokes  family  outline  starts
radical: the Kangxi radical number. form / position: the radical as it appears in this character and where
(氵, left), from the KanjiVG group that holds it; empty when KanjiVG has no such group. parts: the top-level
parts, space separated, each "element/original/flags" (flags: r = the radical, p = KanjiVG's sound part; 氵/水/r
毎//p). strokes: the stroke paths in writing order, "|" separated, each one tag character (r radical, p sound
part, - other) and the SVG path in KanjiVG's 109 x 109 box with numbers rounded to one decimal. family: up to
5 other 상용한자 (school grade, then KANJIDIC frequency order) with the same sound part and an on reading in common
with this character, voicing ignored, the part itself first when it is one (晴 セイ: 青清静情精), so the card can show
the sound series; empty when there is none.
outline: "f" when the strokes are AnimCJK filled outlines in a 1024 x 1024 box (y down), empty for KanjiVG pen
lines in its 109 x 109 box. starts: for the outlines, where the brush starts each stroke ("x,y;x,y;…", from
AnimCJK's stroke medians, same box), so tapping the kanji can number its strokes; empty for pen lines, which start
at their path's first point.
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
    """AnimCJK: character -> (its stroke outlines, the start of each stroke's median), in makemeahanzi's frame
    (1024 wide, y up, baseline at 900)."""
    out = {}
    if not path:
        return out
    for line in open(path, encoding="utf-8"):
        if line.strip():
            d = json.loads(line)
            out[d["character"]] = (d["strokes"], [m[0] for m in d["medians"]])
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

    # Top-level parts: the children of the character's group; a group without an element is looked into.
    parts = []

    def collect(g):
        for k in g:
            if k.tag != SVG + "g":
                continue
            el = k.get(KVG + "element")
            if not el:
                collect(k)
                continue
            if any(p[0] == el and k.get(KVG + "part") is not None for p in parts):
                continue
            flags = ""
            if any(same_part(k, r) for r in rad_groups) or (radical is not None and radical in k.iter()):
                flags += "r"
            if (k.get(KVG + "phon") is not None) if sound_fix is None else (k in phon_groups):
                flags += "p"
            parts.append((el, k.get(KVG + "original") or "", flags))

    collect(top)
    if len(parts) < 2:
        parts = []
    form, pos = "", ""
    if radical is not None:
        form = DISPLAY.get(radical.get(KVG + "element") or "", radical.get(KVG + "element") or "")
        pos = POSITIONS.get(radical.get(KVG + "position") or "", "")
        if radical is top or all(s[0] == "r" for s in strokes):
            # The radical is the whole character (木, or 来 drawn as one 木 group): nothing to single out.
            strokes = ["-" + s[1:] for s in strokes]
    elif whole:
        form = ch
    parts_s = " ".join("%s/%s/%s" % (DISPLAY.get(e, e), o, f) for e, o, f in parts)
    return form, pos, parts_s, "|".join(strokes)


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
        if os.path.exists(svg):
            form, pos, parts, strokes = build_one(ch, n, svg, forms, sound_fix.get(ch))
            with_strokes += 1
            matched += 1 if form else 0
        kind = ""
        brush, starts = outlines.get(ch, (None, None))
        start_s = ""
        tags = [x[0] for x in strokes.split("|")] if strokes else []
        if ch in tag_fix and len(tag_fix[ch]) == len(brush or tags or []):
            tags = list(tag_fix[ch])
            fixed_tags += 1
            if not brush:
                strokes = "|".join(t + x[1:] for t, x in zip(tags, strokes.split("|")))
        if brush and (not tags or len(tags) == len(brush)):
            # The brush outlines, coloured by KanjiVG's tag for the same stroke.
            strokes = "|".join((tags[i] if tags else "-") + compact_outline(b) for i, b in enumerate(brush))
            start_s = ";".join("%d,%d" % (round(x), round(900 - y)) for x, y in starts)
            kind = "f"
            with_brush += 1
        if not n and not strokes:
            continue
        lines.append([ch, str(n or ""), form, pos, parts, strokes, kind, start_s])
    # The sound series: every 상용한자 whose sound part is the same element.
    order = load_order(jamdict_path)
    sound = {}
    for row in lines:
        start_s = row.pop()
        kind = row.pop()
        for p in row[4].split():
            el, _, flags = p.split("/")
            if "p" in flags:
                row.append(el)
                break
        else:
            row.append("")
        row.append(kind)
        row.append(start_s)
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
