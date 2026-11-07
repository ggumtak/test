#!/usr/bin/env python3
"""The 구성 line written to match the 유래 line (2026-11-07), shared by the converter, the checker and merge.

A spec is what origin.tsv's fourth column holds: the top-level parts of parts.tsv in order, " + " between them,
each written as

    SHAPE[ROLE](NOTE)          e.g.  聿[뜻](붓) + 曰[소리 シャ](者를 줄인 꼴)

  SHAPE  the part as drawn: its element or original from parts.tsv (日 or 曰), or the pieces of a part put
         together, or several parts next to each other joined with "+" (牛+巾) when the 유래 treats them as one.
  ROLE   optional: 뜻 | 소리 | 뜻·소리 | 모양 | 기호, a sound part optionally followed by its on reading in
         katakana (소리 シャ, 뜻·소리 ヨウ).
  NOTE   optional: what the part is in this kanji, in the words the 유래 uses (붓; 者를 줄인 꼴; 물), no
         parentheses inside.

The app shows it as  聿(붓 · 뜻) + 曰(者를 줄인 꼴 · 소리 シャ), the radical's part in red and sound parts in blue.

A spec starting with "!" is written freely, for the few kanji whose KanjiVG parts do not follow the 유래 (章: 音 +
十, where KanjiVG has 音 + 早): its shapes are not matched to parts.tsv, and only sound roles are coloured.
"""
import re

ROLES = ("뜻", "소리", "뜻·소리", "모양", "기호")
UNIT = re.compile(r"^(?P<shape>[^\[\]()]+?)(?:\[(?P<role>[^\]]+)\])?(?:\((?P<note>[^()]*)\))?$")
KATA = re.compile(r"^[ァ-ヺー]+$")
# Words the learner does not know (the same list the app's test keeps out of the 유래).
JARGON = ["신자체", "정자", "說文", "설문", "갑골", "금문", "본자", "이체", "속자", "가차", "생략형", "회의", "형성",
          "상형", "지사", "소리 겸 뜻", "설이 갈림", "자원"]
NOTE_MAX = 16
SPEC_MAX = 90


def parse(spec):
    """[(pieces, role, reading, note)] or raises ValueError."""
    out = []
    for raw in spec.split(" + "):
        m = UNIT.match(raw.strip())
        if not m:
            raise ValueError("형식이 틀림: " + raw)
        shape = m.group("shape").strip()
        pieces = [p for p in shape.split("+")]
        if any(not p or " " in p for p in pieces):
            raise ValueError("모양이 틀림: " + raw)
        role, reading = "", ""
        if m.group("role") is not None:
            bits = m.group("role").split()
            role = bits[0]
            if role not in ROLES or len(bits) > 2:
                raise ValueError("역할이 틀림: " + raw)
            if len(bits) == 2:
                if "소리" not in role or not KATA.match(bits[1]):
                    raise ValueError("읽기는 소리 역할 뒤에 가타카나로: " + raw)
                reading = bits[1]
        note = (m.group("note") or "").strip()
        out.append((pieces, role, reading, note))
    return out


def part_tokens(part):
    """The ways one parts.tsv part may be written: its element, its original, or the pieces it is made of."""
    el, orig = part[0], part[1]
    ways = [[el]]
    if orig and "+" not in orig:
        ways.append([orig])
    if "+" in orig:
        ways.append(orig.split("+"))
    return ways


def match(units, plist):
    """For each unit the indexes of the parts it covers, in order, or None when the pieces do not follow the parts."""
    pieces = [(ui, p) for ui, u in enumerate(units) for p in u[0]]

    def go(pi, ki, acc):
        if ki == len(plist):
            return acc if pi == len(pieces) else None
        for way in part_tokens(plist[ki]):
            seg = pieces[pi:pi + len(way)]
            # The pieces of one part may go to different units (学's ⺍ and 冖, which the 유래 tells apart).
            if len(seg) == len(way) and [p for _, p in seg] == way:
                r = go(pi + len(way), ki + 1, acc + [(u, ki) for u in sorted({u for u, _ in seg})])
                if r is not None:
                    return r
        return None

    got = go(0, 0, [])
    if got is None:
        return None
    cover = [[] for _ in units]
    for ui, ki in got:
        if ki not in cover[ui]:
            cover[ui].append(ki)
    return cover if all(cover) else None


def check(spec, plist, memo=""):
    """(errors, warnings) for a spec against the kanji's parts [element, original, flags, inner]."""
    errors, warnings = [], []
    free = spec.startswith("!")
    if free:
        spec = spec[1:]
        if not memo:
            errors.append("'!' 자유 작성에는 이유를 메모에 적어야 함")
    try:
        units = parse(spec)
    except ValueError as e:
        return [str(e)], []
    if len(spec) > SPEC_MAX:
        errors.append("구성 줄이 김(%d자 > %d)" % (len(spec), SPEC_MAX))
    if free:
        for pieces, role, reading, note in units:
            if len(note) > NOTE_MAX:
                errors.append("%s: 설명이 김(%d자 > %d)" % ("+".join(pieces), len(note), NOTE_MAX))
        return errors, warnings + ["자유 작성: 부품과 획 색을 대조하지 않음"]
    cover = match(units, plist)
    if cover is None:
        return errors + ["부품 순서가 parts.tsv와 다름: " + " + ".join(p[0] for p in plist)], warnings
    for (pieces, role, reading, note), idx in zip(units, cover):
        flags = "".join(plist[i][2] for i in idx)
        name = "+".join(pieces)
        if len(note) > NOTE_MAX:
            errors.append("%s: 설명이 김(%d자 > %d)" % (name, len(note), NOTE_MAX))
        for w in JARGON:
            if w in note:
                errors.append("%s: 어려운 말 '%s'" % (name, w))
        blue, partly = "p" in flags, "P" in flags
        # The radical's part is drawn red even when it gives the sound (書's 曰, 毎's 毋): no blue to ask for.
        red = "r" in flags or "R" in flags
        sound = "소리" in role
        problem = None
        if sound and not (blue or partly or red):
            problem = "%s: 소리라고 했지만 자료(획 그림)에서는 소리 부분이 아님" % name
        elif blue and not sound:
            problem = "%s: 자료(획 그림)에서는 소리 부분인데 역할이 소리가 아님" % name
        if problem:
            (warnings if memo else errors).append(problem)
    return errors, warnings
