#!/usr/bin/env python3
"""Writes the 상용한자 card notes (부수, 구성, 유래, 외우기) as Markdown, the way the card shows them, for an outside
review (e.g. by another AI): one file with everything and the same split into parts, each with the review request
on top so the feedback comes back as a table that can be applied line by line.

Usage: export_md.py <out dir> [parts=5]
"""
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from convert import load, first_hun  # noqa: E402

ASSETS = os.path.join(HERE, "..", "..", "..", "app", "src", "main", "assets")
LABELS = {"상형": "그림", "지사": "기호", "회의": "뜻+뜻", "형성": "뜻+소리", "회의 겸 형성": "뜻+소리", "가차": "빌린 글자",
          "국자": "일본에서 만든 글자", "생략형": "줄인 글자", "불명": "유래 불확실"}
UNIT = re.compile(r"^([\^~]?)([^\[\]()]+?)(?:\[([^\]]+)])?(?:\(([^()]*)\))?$")

REQUEST = """# 상용한자 카드 설명 검토 요청

한국어를 쓰는 일본어 학습자(한자 전문가 아님)를 위한 휴대폰 한자 카드 앱의 설명입니다. 상용한자 2,136자마다 아래 네 줄을
카드에 보여 줍니다. 사실 오류와, 학습자가 헷갈릴 부분을 찾아 주세요.

- **부수**: 일본 사전(KANJIDIC2) 기준 부수와 그 이름. 사전 분류용이라 뜻을 맡은 부분과 다를 수 있습니다.
- **구성**: 지금 일본 글자 모양의 부품을 쓰는 순서대로, `부품(설명 · 역할)`. 역할은 뜻 / 소리(뒤에 음독) / 뜻·소리 /
  모양 / 기호. 설명은 유래 줄과 같은 말을 쓰도록 맞췄습니다(예: 書 `聿(붓 · 뜻) + 曰(者를 줄인 꼴 · 소리 シャ)`).
  `*별표*`는 부수에 해당하는 부품입니다.
- **유래**: 글자가 실제로 만들어진 방식. 앞의 유형은 쉬운 말(그림, 기호, 뜻+뜻, 뜻+소리, 빌린 글자, 일본에서 만든 글자,
  줄인 글자, 유래 불확실). 학자마다 설이 갈리는 글자는 '의견이 갈림', 불확실하면 '확실하지 않음'을 붙였습니다.
- **외우기**: 기억을 돕는 이야기. 실제 어원이 아니어도 되지만, 부품 모양을 잘못 부르거나 뜻과 어긋나면 안 됩니다.

## 봐 주실 점
1. 유래의 사실 오류(정설과 다른 설명, 단정하면 안 되는 설을 단정한 곳).
2. 구성과 유래가 서로 다른 말을 하는 곳(구성의 부품 설명·역할이 유래와 맞지 않음).
3. 부수 이름·모양의 오류.
4. 외우기가 부품 모양을 잘못 부르거나 뜻과 어긋나는 곳.
5. 한국어가 어색하거나 학습자가 모를 전문 용어.

문제가 없는 글자는 적지 마세요. 확실하지 않은 지적은 '확신 낮음'이라고 표시해 주세요.

## 답 형식
아래 표 하나로만 답해 주세요. 한 줄에 지적 하나입니다.

| 한자 | 항목 | 문제 | 고친 문장 | 근거 |
|---|---|---|---|---|
| 書 | 유래 | (무엇이 왜 틀렸는지) | (그 줄 전체를 고쳐 쓴 문장) | (사전·문헌 등) |

- 항목은 부수 / 구성 / 유래 / 외우기 중 하나.
- 고친 문장은 카드 형식 그대로: 구성은 `부품(설명 · 역할) + …`, 유래는 120자 이내의 짧은 메모체(…함, …음), 외우기는 45자 이내로
  `… → 뜻`.
"""


def kosei_text(spec):
    out = []
    for raw in spec.lstrip("!").split(" + "):
        m = UNIT.match(raw.strip())
        if not m:
            return spec
        mark, shape, role, note = m.group(1), m.group(2), (m.group(3) or ""), (m.group(4) or "")
        notes = [x for x in (note, role) if x]
        if mark == "^" and "소리" in role:
            notes.append("부수")
        text = ("*%s*" % shape) if mark == "^" else shape
        out.append(text + ("(%s)" % " · ".join(notes) if notes else ""))
    return " + ".join(out)


def old_kosei(plist, kanji):
    out = []
    for el, orig, flags, inner in plist:
        h = first_hun(kanji.get(el)) or first_hun(kanji.get(orig))
        notes = [x for x in (h, "소리" if "p" in flags else "") if x]
        out.append(("*%s*" % el if "r" in flags else el) + ("(%s)" % " · ".join(notes) if notes else ""))
    return " + ".join(out)


def cell(s):
    return s.replace("|", "\\|").replace("\n", " ")


def main(out_dir, n_parts="5"):
    kanji = load(os.path.join(ASSETS, "kanji.tsv"))
    parts = load(os.path.join(ASSETS, "parts.tsv"))
    origin = load(os.path.join(ASSETS, "origin.tsv"))
    rads = {}
    for line in open(os.path.join(ASSETS, "radicals.tsv"), encoding="utf-8"):
        if line.strip() and not line.startswith("#"):
            f = line.rstrip("\n").split("\t")
            rads.setdefault(f[0], []).append(f)
    joyo = [c for c, f in kanji.items() if len(f) > 6 and f[6].isdigit() and 1 <= int(f[6]) <= 8]
    rows = []
    for c in joyo:
        k, p, o = kanji[c], parts.get(c), origin.get(c)
        hun = k[10] if len(k) > 10 and k[10] else k[1]
        grade = "중학" if k[6] == "8" else "%s학년" % k[6]
        rl = rads.get(p[1], []) if p else []
        form = p[2] if p else ""
        r = next((x for x in rl if x[1] == form), rl[0] if rl else None)
        shape = "曰" if p and p[1] == "73" else (form if r and form == r[1] else (r[1] if r else ""))
        radical = "%s %s%s · %s" % (shape, r[4], "(%s)" % r[5] if len(r) > 5 and r[5] else "", r[3]) if r else ""
        plist = [x.split("/") + [""] * (4 - len(x.split("/"))) for x in p[4].split()] if p and p[4] else []
        spec = o[3] if o and len(o) > 3 else ""
        kosei = kosei_text(spec) if spec else (old_kosei(plist, kanji) if len(plist) >= 2 else "—")
        text = o[1] if o else ""
        typ, body = (text.split(":", 1) if ":" in text[:10] else ("", text))
        yurae = ("%s: %s" % (LABELS.get(typ, typ), body.strip())) if typ else text
        memo = o[2] if o and len(o) > 2 else ""
        rows.append("| %s | %s | %s | %s | %s | %s | %s |" % (c, cell(hun.split(", ")[0]), grade, cell(radical),
                                                             cell(kosei), cell(yurae), cell(memo) or "—"))
    head = "| 한자 | 훈음 | 학년 | 부수 | 구성 | 유래 | 외우기 |\n|---|---|---|---|---|---|---|\n"
    os.makedirs(out_dir, exist_ok=True)
    with open(os.path.join(out_dir, "상용한자_부수_구성_유래_전체.md"), "w", encoding="utf-8") as f:
        f.write(REQUEST + "\n## 상용한자 %d자 (카드 순서)\n\n" % len(rows) + head + "\n".join(rows) + "\n")
    n = int(n_parts)
    size = -(-len(rows) // n)
    for i in range(n):
        chunk = rows[i * size:(i + 1) * size]
        with open(os.path.join(out_dir, "상용한자_부수_구성_유래_%d of %d.md" % (i + 1, n)), "w", encoding="utf-8") as f:
            f.write(REQUEST + "\n## %d/%d묶음: %s ~ %s (%d자)\n\n" % (i + 1, n, chunk[0][2], chunk[-1][2], len(chunk))
                    + head + "\n".join(chunk) + "\n")
    print(len(rows), "kanji;", n, "parts")


if __name__ == "__main__":
    main(*sys.argv[1:3])
