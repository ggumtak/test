"""Usage: render_one.py <kanji> [suffix] — writes <audit>/one/<codepoint>[-suffix].png: the kanji large, each stroke coloured as the card
colours it (red radical, blue sound part, black other), with its stroke number at the stroke's start, and prints the
path and the tags string (one letter per stroke in writing order: r, p or -)."""
import sys, os, re, subprocess, json
sys.path.insert(0, os.path.dirname(__file__))
from common import *
ch = sys.argv[1]
kan, parts, rads = table("kanji.tsv"), table("parts.tsv"), radicals()
p = parts[ch]
shape, sound, tags = expected(ch, kan, parts, rads)
medians = None
for line in open(os.environ.get("ANIMCJK_GRAPHICS", "graphicsJa.txt"), encoding="utf-8"):
    if line.startswith('{"character":"%s"' % ch):
        medians = json.loads(line)["medians"]
        break
items = []
labels = []
for i, s in enumerate(p[5].split("|")):
    items.append('<path d="%s" fill="%s" fill-opacity="0.85"/>' % (s[1:], COL[s[0]]))
    m = re.match(r"M(-?\d+),(-?\d+)", s[1:])
    # The number at the brush's starting point (AnimCJK's median), else the outline's first point.
    pt = (medians[i][0][0], 900 - medians[i][0][1]) if medians and i < len(medians) else (
        (int(m.group(1)), int(m.group(2))) if m else None)
    if pt:
        x, y = pt
        labels.append('<circle cx="%d" cy="%d" r="30" fill="#FFD54F" stroke="#333" stroke-width="3"/>'
                     '<text x="%d" y="%d" font-size="38" text-anchor="middle" font-family="sans-serif" font-weight="bold">%d</text>'
                     % (x, y, x, y + 13, i + 1))
html = ('<html><head><meta charset="utf-8"></head><body style="margin:0;background:#fff">'
        '<svg viewBox="-40 -40 1104 1104" width="620" height="620">%s</svg>'
        '<div style="font:18px sans-serif;padding:6px">%s · 부수 %s · 소리 %s · tags %s</div></body></html>'
        % ("".join(items + labels), ch, shape, "·".join(sound) or "-", tags))
suffix = ("-" + sys.argv[2]) if len(sys.argv) > 2 else ""
base = os.path.join(os.path.dirname(os.path.dirname(__file__)), "one", "%05x%s" % (ord(ch), suffix))
open(base + ".html", "w", encoding="utf-8").write(html)
subprocess.run(["node", "-e", "const {chromium}=require('playwright');(async()=>{const b=await chromium.launch();"
                "const p=await b.newPage({viewport:{width:620,height:660},deviceScaleFactor:1});"
                "await p.goto('file://%s.html');await p.screenshot({path:'%s.png'});await b.close();})()" % (base, base)],
               check=True)
print(base + ".png")
print("tags", tags, "| radical", shape, "| sound", "·".join(sound) or "-", "| parts", p[4])
