#!/usr/bin/env python3
"""Writes the 구성 spec (kosei.py) for kanji whose 유래 already names every part plainly, e.g.

    海  형성: 뜻은 氵(물), 소리는 毎(マイ). …   →   氵[뜻](물) + 毎[소리 マイ](매양 매)
    休  회의: 亻(사람)+木(나무). …              →   亻[뜻](사람) + 木[뜻](나무)

so the 구성 line uses the 유래's own words. Kanji it cannot do with certainty are listed for a person to write.

Usage: convert.py <assets dir> <scope.txt: kanji written by hand, one per line> <out.tsv> <todo.txt>
"""
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import kosei  # noqa: E402

KATA = re.compile(r"^[ァ-ヺー]+$")


def load(path):
    out = {}
    for line in open(path, encoding="utf-8"):
        if line.strip() and not line.startswith("#"):
            f = line.rstrip("\n").split("\t")
            out[f[0]] = f
    return out


def first_hun(k):
    if not k:
        return ""
    h = k[10] if len(k) > 10 and k[10] else k[1]
    return h.replace(" / ", ", ").split(", ")[0].strip()


def convert(ch, body, typ, plist, kanji):
    """The spec, or None when a part's role or gloss cannot be read off the 유래 with certainty."""
    units = []
    for el, orig, flags, inner in plist:
        found = None
        for name in (el, orig):
            if not name or "+" in name:
                continue
            m = re.search(re.escape(name) + r"\(([^()]*)\)", body)
            if m:
                found = (name, m)
                break
        if not found:
            return None
        name, m = found
        gloss = m.group(1).strip()
        # Every gloss the 유래 gives this part: two different words for it need a person to choose.
        words = {g.strip() for g in re.findall(re.escape(name) + r"\(([^()]*)\)", body) if not KATA.match(g.strip())}
        if len(words) > 1:
            return None
        after = body[m.end():m.end() + 4]
        before = body[max(0, m.start() - 4):m.start()]
        # The rest of its sentence: a sound part that "also adds meaning" is both (소리도 내고 뜻도 보탬, 뜻도 보탬).
        sentence = re.split(r"[.]", body[m.end():], maxsplit=1)[0]
        both = "뜻도" in sentence
        if before.endswith("뜻은 ") or re.match(r"^[이가] 뜻", after):
            role = "뜻"
        elif before.endswith("소리는 ") or re.match(r"^[이가] 소리", after) or both:
            role = "뜻·소리" if both else "소리"
        elif typ == "회의" and ("+" in body[max(0, m.start() - 1):m.start()] or body[m.end():m.end() + 1] == "+"):
            role = "뜻"
        else:
            return None
        if "소리" in role:
            if not KATA.match(gloss):
                return None
            # What the 유래 calls it elsewhere (禺(원숭이)), else its 훈음 on the card.
            note = next(iter(words), "") or first_hun(kanji.get(name))
            units.append("%s[%s %s](%s)" % (name, role, gloss, note) if note else "%s[%s %s]" % (name, role, gloss))
        else:
            if KATA.match(gloss) or not gloss or "," in gloss:
                return None
            units.append("%s[%s](%s)" % (name, role, gloss))
    return " + ".join(units)


def main(assets, scope_path, out_path, todo_path):
    kanji = load(os.path.join(assets, "kanji.tsv"))
    parts = load(os.path.join(assets, "parts.tsv"))
    origin = load(os.path.join(assets, "origin.tsv"))
    scope = {l.strip() for l in open(scope_path, encoding="utf-8") if l.strip()}
    done, todo = [], []
    for ch, o in origin.items():
        p = parts.get(ch)
        plist = [x.split("/") + [""] * (4 - len(x.split("/"))) for x in p[4].split()] if p and p[4] else []
        if len(plist) < 2 or ch in scope:
            continue
        text = o[1]
        typ, body = (text.split(":", 1) + [""])[:2] if ":" in text[:10] else ("", text)
        spec = convert(ch, body.strip(), typ, plist, kanji)
        if spec is not None:
            errors, _ = kosei.check(spec, plist)
            if not errors:
                done.append((ch, spec))
                continue
        todo.append(ch)
    with open(out_path, "w", encoding="utf-8") as f:
        for ch, spec in done:
            f.write(ch + "\t" + spec + "\n")
    with open(todo_path, "w", encoding="utf-8") as f:
        f.write("".join(c + "\n" for c in todo))
    print(len(done), "converted,", len(todo), "left to write")


if __name__ == "__main__":
    main(*sys.argv[1:5])
