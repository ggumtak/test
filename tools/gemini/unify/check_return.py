#!/usr/bin/env python3
"""Usage: check_return.py <returned.txt>... [--accept] — checks the 외우기 lines the Gemini app sent back for the
part-name unification (00_작성지침.md; `한자 | 새 외우기` a line) and prints for each `○`, `△` with what to look at,
or `✗` with what rejects it. With --accept, the lines without errors follow as link-layer rows.

Errors (the line cannot be used):
  - the kanji was not asked for (requested.txt) or comes twice
  - not `장면 → 뜻`, or the meaning after the arrow differs from the current line's
  - longer than 70 characters (Kotlin's count), a kanji outside parentheses
  - a shape in parentheses that is in neither the current line, the 구성 line nor the 유래
  - `유지` (the current line kept) on a kanji with a name its 구성 line does not back
Look again (a reader decides; the word test is a heuristic):
  - longer than 55 characters
  - a part the current line uses is missing
  - a part's word is not the fixed name, one of the words judged the same, or an inflection of them
  - the meaning's word appears in the scene
"""
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from collect import is_han, load_base, u16, uses_of  # noqa: E402
from common import related  # noqa: E402  (polish/common.py, on the path through collect)
from make_request import name_for, table  # noqa: E402

HARD, SOFT = 70, 55
HEAD = re.compile(r"^\[제미나이 답\]")


def check(ch, new, row, tab):
    errors, look = [], []
    if new.count("→") != 1:
        return ["'장면 → 뜻' 꼴이 아님"], look
    scene, meaning = [x.strip() for x in new.split("→")]
    old_meaning = row["memo"].split("→")[-1].strip()
    if meaning != old_meaning:
        errors.append("→ 뒤의 뜻이 지금 줄과 다름(지금: %s)" % old_meaning)
    if u16(new) > HARD:
        errors.append("%d자: %d자를 넘음" % (u16(new), HARD))
    elif u16(new) > SOFT:
        look.append("%d자" % u16(new))
    depth, outside = 0, []
    for c in new:
        depth += (c == "(") - (c == ")" and depth > 0)
        if depth == 0 and is_han(c):
            outside.append(c)
    if outside:
        errors.append("괄호 밖 한자: %s" % "".join(outside))
    old = uses_of(row["memo"])
    old_shapes = {s for _, s in old}
    # Any shape the card already shows may be named: the current line's (also inside 亜's "(一·一)"), the 구성's.
    known = {c for c in row["memo"] + row["spec"] + row["raw"] if is_han(c)}
    old_word = {s: w for w, s in old}
    seen = set()
    for word, shape in uses_of(new):
        seen.add(shape)
        if shape not in known:
            errors.append("이 글자에 없는 부품: %s(%s)" % (word, shape))
            continue
        if shape not in tab:
            continue
        t = tab[shape]
        want = name_for(tab, shape, old_word.get(shape, t["name"]))
        ok = [want] + ([w for w in t["same"] if w] if want == t["name"] else
                       [w for w, tag in t["tags"].items() if tag == "I:" + want])
        if not word or not (word in ok or related(word, ok)):
            look.append("%s을 '%s'라 부름(이름표: %s)" % (shape, word, want))
    for s in old_shapes - seen:
        look.append("지금 줄의 %s이 없음" % s)
    for w in re.split(r"[·,\s]+", meaning):
        stem = re.sub(r"(하다|되다|다)$", "", w)
        if len(stem) >= 2 and stem in scene:
            look.append("장면에 뜻 낱말 '%s'" % stem)
    return errors, look


def main(argv):
    accept = "--accept" in argv
    paths = [a for a in argv if not a.startswith("--")]
    rows = load_base()
    tab = table(rows)
    marks = open(os.path.join(HERE, "requested.txt"), encoding="utf-8").read().split()
    asked = {m[0] for m in marks}
    may_stay = {m[0] for m in marks if m.endswith("=")}  # every flagged name is what the kanji's 구성 line says
    seen, good, kept, n_err, n_look, notes = set(), [], [], 0, 0, []
    for path in paths:
        for line in open(path, encoding="utf-8"):
            line = line.strip().replace("\\", "")
            if not line or HEAD.match(line) or line.startswith(("#", "```")):
                continue
            if line.startswith("메모"):
                notes.append(line)
                continue
            c = [x.strip() for x in line.strip("|").split("|")]
            if len(c) < 2 or len(c[0]) != 1:
                print("? 읽지 못한 줄: %s" % line[:60])
                n_err += 1
                continue
            ch, new = c[0], c[1]
            errors = []
            if ch not in asked:
                errors.append("요청하지 않은 한자")
            if ch in seen:
                errors.append("두 번 나옴")
            seen.add(ch)
            look = []
            if not errors and (new == rows[ch]["memo"] or (len(c) > 2 and c[2].startswith("유지"))):
                if new != rows[ch]["memo"]:
                    errors.append("'유지'인데 지금 줄과 다름")
                elif ch not in may_stay:
                    errors.append("유지할 수 없는 글자(구성 줄이 뒷받침하지 않는 이름이 있음)")
                else:
                    kept.append(ch)
                    continue
            if not errors:
                errors, look = check(ch, new, rows[ch], tab)
            if errors:
                n_err += 1
                print("✗ %s  %s\n    오류: %s" % (ch, new, "; ".join(errors + look)))
            else:
                n_look += bool(look)
                print("%s %s  %s%s" % ("△" if look else "○", ch, new, ("\n    확인: " + "; ".join(look)) if look else ""))
                good.append((ch, new))
    missing = sorted(asked - seen)
    print("\n%d줄 통과(그중 확인할 줄 %d), %d줄 오류, 유지 %d자(%s), 아직 안 온 글자 %d" % (
        len(good), n_look, n_err, len(kept), "".join(kept), len(missing)))
    for n in notes:
        print(n)
    if accept:
        print("\n# link 층 줄(한자, 구성, 유래, 외우기, 이유)")
        for ch, new in good:
            print("\t".join((ch, "", "", new, "부품 이름 통일(Gemini)")))


if __name__ == "__main__":
    main(sys.argv[1:])
