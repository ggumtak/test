"""Shared by make_request.py and check_return.py: the card's rows for each 상용한자 as an outside writer sees them."""
import os
import re

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, "..", "..", "..", "..", "app", "src", "main", "assets")
LABELS = {"상형": "그림", "지사": "기호", "회의": "뜻+뜻", "형성": "뜻+소리", "회의 겸 형성": "뜻+소리", "가차": "빌린 글자",
          "국자": "일본에서 만든 글자", "생략형": "줄인 글자", "불명": "유래 불확실"}
UNIT = re.compile(r"^([\^~]?)([^\[\]()]+?)(?:\[([^\]]+)])?(?:\(([^()]*)\))?$")
# A 구성 note that names a shape, not a thing (黃을 줄인 꼴): any picturable word may stand for that part.
SHAPE_NOTE = re.compile(r"꼴|부분|모양|왼쪽|오른쪽|위쪽|아래쪽|윗부분|아랫부분|획|점|조각|글자|대신|풀이|의견|본래|안쪽")
PAIR = re.compile(r"([가-힣·]+)\(([^()가-힣]{1,8})\)")
MEMO_MAX = 50    # PartsTest.shippedOrigins (UTF-16 units)
MEMO_SOFT = 45


def u16(s):
    return len(s.encode("utf-16-le")) // 2


def is_han(c):
    o = ord(c)
    return 0x2E80 <= o <= 0x2FDF or 0x31C0 <= o <= 0x31EF or 0x3400 <= o <= 0x9FFF or 0xF900 <= o <= 0xFAFF \
        or o >= 0x20000


def units(spec):
    """The 구성 line's parts as (shape, role, reading, note), or [] for a kanji of one part or without the line."""
    body = spec.lstrip("!").strip()
    if not body or body == "-":
        return []
    out = []
    for raw in body.split(" + "):
        m = UNIT.match(raw.strip())
        if not m:
            return []
        role = (m.group(3) or "").strip().split(" ")
        out.append((m.group(2).strip(), role[0], role[1] if len(role) > 1 else "", (m.group(4) or "").strip()))
    return out


def shown(spec):
    """The 구성 line roughly as the card shows it."""
    us = units(spec)
    if not us:
        return "(부품 하나)" if spec.strip() != "-" else "(구성 줄 없음)"
    return " + ".join(s + ("(" + " · ".join(x for x in (n, " ".join(y for y in (r, rd) if y)) if x) + ")" if n or r else "")
                      for s, r, rd, n in us)


def load():
    """kanji -> dict(hun, spec, origin, memo); the 2,136 상용한자 in origin.tsv's order."""
    kan = {}
    with open(os.path.join(ASSETS, "kanji.tsv"), encoding="utf-8") as f:
        for line in f:
            c = line.rstrip("\n").split("\t")
            kan[c[0]] = c
    rows = {}
    with open(os.path.join(ASSETS, "origin.tsv"), encoding="utf-8") as f:
        for line in f:
            c = line.rstrip("\n").split("\t")
            k = kan[c[0]]
            t, _, body = c[1].partition(":")
            rows[c[0]] = dict(hun=(k[10] or k[1]).split(",")[0].split(" / ")[0].strip(),
                              spec=c[3] if len(c) > 3 else "", raw=c[1],
                              origin=(LABELS.get(t, t) + ": " + body.strip()) if body else c[1], memo=c[2])
    return rows


def radical_words():
    """shape -> the words the radical table gives it (its Korean name and meaning), which a 외우기 may also use."""
    out = {}
    with open(os.path.join(ASSETS, "radicals.tsv"), encoding="utf-8") as f:
        for line in f:
            if line.startswith("#") or not line.strip():
                continue
            c = line.rstrip("\n").split("\t")
            out.setdefault(c[1], set()).update([c[4]] + ([c[5]] if len(c) > 5 else []))
    return out


def _cv(ch):
    n = ord(ch) - 0xAC00
    return n // 588, (n % 588) // 28


_CONTR = {(20, 6), (18, 4), (13, 14), (8, 9), (0, 1), (18, 6), (20, 7)}


def _same_stem(a, b):
    x, y = _cv(a[0]), _cv(b[0])
    return x == y or (x[0] == y[0] and ((y[1], x[1]) in _CONTR or (x[1], y[1]) in _CONTR))


def hangul_words(s):
    return [w for w in re.split(r"[ ·,()]+", s) if w and all("가" <= ch <= "힣" for ch in w)]


def related(name, texts):
    """Whether the 외우기's word for a part is the 구성 note's word, allowing for inflection (치다 → 쳐서)."""
    notes = [w for t in texts for w in hangul_words(t)]
    return any(w in n or n in w or _same_stem(w, n) for w in hangul_words(name) for n in notes)
