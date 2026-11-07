import sys, os, json
sys.path.insert(0, os.path.dirname(__file__))
from common import *
out = sys.argv[1]
kan, parts, rads = table("kanji.tsv"), table("parts.tsv"), radicals()
jo = sorted([c for c, f in kan.items() if len(f) > 6 and f[6] in ("1", "2", "3", "4", "5", "6", "8")],
            key=lambda c: (int(kan[c][6]), c))
N = 48
index = []
for s in range(0, len(jo), N):
    cells = []
    for ch in jo[s:s + N]:
        p = parts[ch]
        shape, sound, tags = expected(ch, kan, parts, rads)
        paths = "".join('<path d="%s" fill="%s"/>' % (x[1:], COL[x[0]]) for x in p[5].split("|"))
        red = "부수 " + shape if "r" in tags else "부수 칠 없음(" + shape + ")"
        blue = ("소리 " + "·".join(sound)) if "p" in tags else "소리 칠 없음"
        cells.append('<div class="c"><svg viewBox="0 0 1024 1024" width="150" height="150">%s</svg>'
                     '<div class="l"><b>%s</b> <span style="color:#C0392B">%s</span><br><span style="color:#1F63B0">%s</span></div></div>'
                     % (paths, ch, red, blue))
    k = s // N + 1
    html = ('<html lang="ja"><head><meta charset="utf-8"><style>body{margin:8px;background:#fff;font-family:sans-serif}'
            '.g{display:grid;grid-template-columns:repeat(8,170px);gap:6px}.c{border:1px solid #ddd;text-align:center;padding:4px}'
            '.l{font-size:13px;line-height:1.3}</style></head><body><div class="g">%s</div></body></html>' % "".join(cells))
    open(os.path.join(out, "sheet-%02d.html" % k), "w", encoding="utf-8").write(html)
    index.append({"sheet": k, "chars": "".join(jo[s:s + N])})
json.dump(index, open(os.path.join(out, "index.json"), "w", encoding="utf-8"), ensure_ascii=False)
print(len(index), "sheets")
