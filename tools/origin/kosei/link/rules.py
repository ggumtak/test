#!/usr/bin/env python3
"""Shared by make_inputs.py and check.py (link/BRIEF.md): the card's four rows for one 상용한자 and the automatic
hints about how its 외우기 ties to the 구성 and 유래 rows.

The hints are heuristics, shown as 참고: a part the 외우기 names that the 구성 and 유래 do not show, a 구성 unit
the 외우기 leaves out, a part the 외우기 calls by another word than the 구성 note or the 유래, a meaning after the
arrow that is none of the kanji's meanings. A writer reads them as places to look, not as rules.
"""
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, ".."))
import kosei  # noqa: E402
from convert import load  # noqa: E402
from export_md import kosei_text, find_radical, LABELS  # noqa: E402

ASSETS = os.path.join(HERE, "..", "..", "..", "..", "app", "src", "main", "assets")
MEMO_MAX = 50       # PartsTest.shippedOrigins
MEMO_SOFT = 45      # longer lines are hard to take in at a glance
ORIGIN_MAX = 120
BODY_JARGON = ["신자체", "정자", "說文", "설문", "갑골", "금문", "본자", "이체", "속자", "가차", "생략형", "회의", "형성",
               "상형", "지사", "소리 겸 뜻", "설이 갈림", "자원", "약자", "고자", "전서", "예서", "소전"]
# Words a learner may not know (easy/BRIEF.md): flagged so the line is looked at again.
HARD = ["녹봉", "보루", "진지", "시렁", "제후", "천자", "점괘", "희생", "형상", "무릇", "멍에", "갑을병정",
        "조리", "법도", "관청", "벼슬아치", "의례", "제기", "기물", "성곽", "사당", "종묘", "역참", "주살", "부스럼",
        "삼감", "도리", "연유", "형용", "모양새", "이를테면", "및", "등지", "본뜸", "본떠", "본뜬", "법식", "공훈",
        "천간", "간지", "육갑", "제사장", "면류관", "화톳불", "궁실", "전답"]
HEDGES = ["의견이 갈림", "확실하지 않", "봄", "풂", "라 함", "이라 함", "다고 함", "의견도", "옛 사전", "사전도", "설도",
          "라고도", "듯", "추정"]
TYPES = {"상형", "지사", "회의", "형성", "회의 겸 형성", "가차", "국자", "생략형", "불명"}
# A 구성 note that names a shape, not a thing (黃을 줄인 꼴, 頪의 왼쪽): any word in the 외우기 is fine for it.
SHAPE_NOTE = re.compile(r"꼴|부분|모양|왼쪽|오른쪽|위쪽|아래쪽|윗부분|아랫부분|획|점|조각")


def is_han(c):
    o = ord(c)
    return 0x2E80 <= o <= 0x2FDF or 0x31C0 <= o <= 0x31EF or 0x3400 <= o <= 0x9FFF or 0xF900 <= o <= 0xFAFF \
        or o >= 0x20000


def han(s):
    return {c for c in s if is_han(c)}


def load_all():
    kanji, parts, origin = (load(os.path.join(ASSETS, n)) for n in ("kanji.tsv", "parts.tsv", "origin.tsv"))
    rads = {}
    for line in open(os.path.join(ASSETS, "radicals.tsv"), encoding="utf-8"):
        if line.strip() and not line.startswith("#"):
            f = line.rstrip("\n").split("\t")
            rads.setdefault(f[0], []).append(f)
    joyo = [c for c, f in kanji.items() if len(f) > 6 and f[6].isdigit() and 1 <= int(f[6]) <= 8]
    return kanji, parts, origin, rads, joyo


def plist_of(p):
    return [x.split("/") + [""] * (4 - len(x.split("/"))) for x in p[4].split()] if p and len(p) > 4 and p[4] else []


def radical_row(p, rads):
    """(shape, the 부수 row as the card shows it, the radical's meaning word)."""
    lines = rads.get(p[1], []) if p else []
    form = p[2] if p else ""
    r = find_radical(lines, form, p[3] if p and len(p) > 3 else "")
    if not r:
        return form, form, ""
    shape = "曰" if p[1] == "73" else (form if form == r[1] else r[1])
    meaning = r[5] if len(r) > 5 else ""
    return shape, "%s %s%s · %s" % (shape, r[4], "(%s)" % meaning if meaning else "", r[3]), meaning


def input_spec(spec):
    """The 구성 line as a fix file writes it: no ^ (merge.py adds it), "~" kept only on free lines."""
    if not spec or spec == "-":
        return spec
    free = spec.startswith("!")
    units = [u.strip() for u in spec.lstrip("!").split(" + ")]
    units = [u.lstrip("^") if free else u.lstrip("^~") for u in units]
    return ("!" if free else "") + " + ".join(units)


def units_of(spec):
    if not spec or spec == "-":
        return []
    try:
        return kosei.parse(spec.lstrip("!").replace("^", "").replace("~", ""))
    except ValueError:
        return []


def hun_words(k):
    """The kanji's meaning words: from its 훈음 (사랑 애 → 사랑) and its Japanese-usage glosses."""
    words = []
    for field in ((k[10] if len(k) > 10 and k[10] else k[1]), k[8] if len(k) > 8 else ""):
        for bit in re.split(r"[,/;]", field or ""):
            bit = bit.strip()
            if not bit:
                continue
            ws = bit.split()
            if field is not (k[8] if len(k) > 8 else "") and len(ws) >= 2:
                ws = ws[:-1]  # 사랑 애 → 사랑
            words += [w for w in ws if len(w) >= 1]
    return words


def stem(w):
    w = re.sub(r"(하다|되다|하는|한|함|다|는|은|을|를|이|가|의|에|로|으로|와|과)$", "", w)
    return w[:2] if len(w) >= 2 else w


def memo_parts(memo):
    """[(word before the parentheses, the kanji inside)] for every 'word(漢)' in the 외우기."""
    out = []
    for m in re.finditer(r"([가-힣A-Za-z]*)\(([^()]*)\)", memo):
        out.append((m.group(1), "".join(c for c in m.group(2) if is_han(c))))
    return out


def gloss_in(text, ch):
    """The words the text puts in parentheses after ch: 'X(물)' → ['물'] (readings in katakana dropped)."""
    out = []
    for m in re.finditer(re.escape(ch) + r"\(([^()]*)\)", text):
        out += [w for w in re.split(r"[\s,·+]+", m.group(1)) if w and not re.match(r"^[ァ-ヺー]+$", w)]
    return out


def memo_hints(ch, k, p, spec, yurae, memo, radical_meaning=""):
    """Heuristic hints about the 외우기 against the 구성 and 유래 rows."""
    out = []
    if not memo:
        return ["외우기가 비어 있음"]
    units = units_of(spec)
    seen_parts = set()
    for pieces, role, reading, note in units:
        seen_parts |= set("".join(pieces)) | han(note)
    known = seen_parts | han(yurae) | {ch}
    for x in plist_of(p):
        known |= han(x[0]) | han(x[1])
    named = memo_parts(memo)
    in_memo = set().union(*[set(h) for _, h in named]) if named else set()
    for word, inside in named:
        for c in inside:
            if c not in known:
                out.append("외우기의 %s는 구성·유래에 없는 부품(보이는 모양이 맞는지, 구성의 이름으로 부를 수 있는지)" % c)
    for pieces, role, reading, note in units:
        if not (set("".join(pieces)) & in_memo) and not (han(note) & in_memo):
            out.append("구성의 %s(%s)가 외우기에 나오지 않음" % ("+".join(pieces), note or role))
    for word, inside in named:
        if not word or len(inside) != 1:
            continue
        c = inside
        for pieces, role, reading, note in units:
            if pieces != [c] or not note or SHAPE_NOTE.search(note):
                continue
            names = [w for w in re.split(r"[\s,·]+", note) if w and not han(w)] + gloss_in(yurae, c)
            if c == (p[2] if p else "") and radical_meaning:
                names += re.split(r"[\s,·]+", radical_meaning)
            names = [n for n in names if n and not han(n)]
            if names and not any(stem(word) == stem(n) or stem(n) in word or stem(word) in n for n in names):
                out.append("%s를 구성은 '%s', 외우기는 '%s'로 부름" % (c, note, word))
    if "→" not in memo:
        out.append("외우기에 '→ 뜻'이 없음")
    else:
        after = memo.split("→")[-1]
        words = hun_words(k)
        if words and not any(stem(w) in after or w in after for w in words if len(stem(w)) >= 1):
            out.append("→ 뒤 '%s'가 훈음·뜻(%s)과 다름" % (after.strip(), ", ".join(dict.fromkeys(words))))
    if len(memo) > MEMO_SOFT:
        out.append("외우기가 김(%d자)" % len(memo))
    for w in HARD:
        if w in memo:
            out.append("외우기에 어려울 수 있는 말 '%s'" % w)
    return out


def card_block(ch, kanji, parts, origin, rads):
    """The kanji as the card shows it, plus the 구성 line as a fix file writes it and the automatic hints."""
    k, p, o = kanji[ch], parts.get(ch), origin[ch]
    shape, rrow, rmeaning = radical_row(p, rads)
    spec = o[3] if len(o) > 3 else ""
    text = o[1]
    typ, body = (text.split(":", 1) if ":" in text[:10] else ("", text))
    shown = ("%s: %s" % (LABELS.get(typ, typ), body.strip())) if typ else text
    hun = k[10] if len(k) > 10 and k[10] else k[1]
    hints = memo_hints(ch, k, p, spec, text, o[2], rmeaning)
    lines = [
        "■ %s  훈음 %s | 음 %s | 일본어 뜻 %s" % (ch, hun, k[3], k[8] if len(k) > 8 else ""),
        "  부수: %s" % rrow,
        "  구성(카드): %s" % (kosei_text(spec, shape) if spec and spec != "-" else "(없음)"),
        "  구성(쓰는 꼴): %s" % (input_spec(spec) or "(없음)"),
        "  유래(카드): %s" % shown,
        "  유래(쓰는 꼴): %s" % text,
        "  외우기: %s" % o[2],
    ]
    if hints:
        lines.append("  자동 점검(참고): " + " / ".join(hints))
    return "\n".join(lines)
