#!/usr/bin/env python3
"""Usage: make_request.py — writes what the Gemini app needs to give every part one name across the 외우기 lines
(README.md): names.txt (the fixed name of each part), batch-NN.txt (the kanji whose line calls a part by another
meaning, grouped by that part), requested.txt, and prints the numbers for the README. The batches sent on
2026-11-16 were made from the data of that day: run this again only to count what is left, not to resend.

groups.tsv holds the judgement the batches follow, one part a line: shape, fixed name, then every word the 외우기
lines call it by with a tag: S the same meaning as the fixed name, I:name a real second identity the 구성 line
shows (月 몸 / 달), N an ad-hoc nickname, which is what gets rewritten."""
import os
import sys
from collections import Counter, defaultdict

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from collect import SHAPE_NOTE, collect, grade_order, is_han, load, shown, units, uses_of  # noqa: E402
from common import related  # noqa: E402  (polish/common.py, on the path through collect)

PER = 90  # kanji a batch: about two answers of the Gemini app


def read_groups():
    """shape -> (fixed name, {word: tag})."""
    out = {}
    for line in open(os.path.join(HERE, "groups.tsv"), encoding="utf-8"):
        if not line.strip() or line.startswith("#"):
            continue
        shape, name, words = line.rstrip("\n").split("\t")
        tags = {}
        for item in words.split(" "):
            if item:
                w, _, t = item.rpartition("|")
                tags["" if w == "∅" else w] = t
        out[shape] = (name, tags)
    return out


def table(rows):
    """shape -> dict(name, same=[words], other={identity name: [kanji]}, n=uses), for every part two or more kanji use."""
    words, _ = collect(rows)
    groups = read_groups()
    out = {}
    for shape, ws in words.items():
        n = sum(len(v) for v in ws.values())
        if n < 2:
            continue
        if shape in groups:
            name, tags = groups[shape]
        else:  # one word everywhere: that word is the name
            name, tags = next(iter(ws)), {w: "S" for w in ws}
        # A word groups.tsv has not judged (a line rewritten since): the fixed name's if it reads as that, else open.
        for w in ws:
            if w not in tags:
                tags[w] = "S" if w and related(w, [name] + [x for x, t in tags.items() if t == "S" and x]) else "?"
        other = defaultdict(list)
        for w, ks in ws.items():
            if tags[w].startswith("I:"):
                other[tags[w][2:]] += ks
        out[shape] = dict(name=name, tags=tags, other=dict(other), n=n,
                          same=[w for w, _ in Counter({w: len(k) for w, k in ws.items() if tags[w] == "S"}).most_common()])
    return out


def backing(r, shape, word):
    """The 구성 note that calls the part by this word on this very card (主: 王 '등잔'), or "". A sound part's
    note does not count: it is a 훈음 written to follow the old line (店: 占 '점령할 점') and will be made one."""
    for s, role, _, note in units(r["spec"]):
        if [c for c in s if is_han(c)] == [shape] and note and not role.startswith("소리") \
                and not SHAPE_NOTE.search(note) and word and related(word, [note]):
            return note
    return ""


def wanted(rows, tab):
    """kanji -> [(shape, word, backing note)] its 외우기 calls by an ad-hoc name (tag N). A kanji of one part is
    left out: it is a picture, and its line names the pieces of that picture (魚: 머리, 몸통, 꼬리), not parts
    with a meaning."""
    out = {}
    for ch, r in rows.items():
        if not units(r["spec"]):
            continue
        bad = [(s, w, backing(r, s, w)) for w, s in uses_of(r["memo"])
               if s in tab and s != ch and tab[s]["tags"].get(w) == "N"]
        if bad:
            out[ch] = bad
    return out


def name_for(tab, shape, word):
    """The name this kanji's line is to use for the part: the fixed one, or the second identity it already uses."""
    t = tab[shape]
    tag = t["tags"].get(word, "N")
    return tag[2:] if tag.startswith("I:") else t["name"]


def block(ch, r, tab, bad):
    lines = ["### %s (%s)" % (ch, r["hun"]),
             "구성: " + shown(r["spec"]),
             "유래: " + r["origin"],
             "지금 외우기: " + r["memo"]]
    names = []
    flagged = {(s, w): note for s, w, note in bad}
    for word, shape in uses_of(r["memo"]):
        if shape not in tab:
            names.append("%s = %s(이 글자에만 있는 부품: 그대로)" % (shape, word or "?"))
        elif (shape, word) in flagged:
            note = flagged[(shape, word)]
            names.append("%s = **%s** ← 지금은 '%s'%s" % (shape, tab[shape]["name"], word,
                                                      " [구성도 '%s']" % note if note else ""))
        else:
            names.append("%s = %s" % (shape, name_for(tab, shape, word)))
    lines.append("부품 이름: " + " / ".join(dict.fromkeys(names)))
    return "\n".join(lines)


def main():
    rows = load()
    order, _ = grade_order(rows)
    rank = {ch: i for i, ch in enumerate(order)}
    tab = table(rows)
    want = wanted(rows, tab)

    with open(os.path.join(HERE, "names.txt"), "w", encoding="utf-8") as f:
        f.write("# 부품 이름표: 부품 | 고정 이름 | 같은 뜻으로 써도 되는 말 | 실제로 다른 것일 때의 이름(그 글자들에서만)\n")
        for shape, t in sorted(tab.items(), key=lambda x: -x[1]["n"]):
            same = [w for w in t["same"] if w and w != t["name"]][:6]
            other = ["%s: %s" % (n, "".join(sorted(k, key=rank.get))) for n, k in t["other"].items()]
            f.write("%s | %s | %s | %s\n" % (shape, t["name"], ", ".join(same) or "-", " / ".join(other) or "-"))

    # First the kanji with a name nothing on the card backs (they must change), then those whose 구성 line uses
    # the same name (the writer may keep the line). In each, a kanji goes under the part it breaks that the most
    # kanji use; parts in the order of how many lines they change.
    words, _ = collect(rows)
    batches = []
    for must in (True, False):
        by_part = defaultdict(list)
        for ch, bad in want.items():
            if any(not x[2] for x in bad) == must:
                by_part[max(bad, key=lambda x: tab[x[0]]["n"])[0]].append(ch)
        cur, size = [], 0
        for shape, chs in sorted(by_part.items(), key=lambda x: (-len(x[1]), -tab[x[0]]["n"])):
            chs.sort(key=rank.get)
            t = tab[shape]
            good = sorted((k for w, ks in words[shape].items() if t["tags"][w] == "S" for k in ks), key=rank.get)
            head = ["## %s = %s" % (shape, t["name"])]
            if good:
                head.append("이미 이 이름으로 쓰는 글자 %d자. 예: %s" % (
                    len(good), " ／ ".join("%s %s" % (k, rows[k]["memo"]) for k in good[:2])))
            if t["other"]:
                head.append("다른 것으로 인정한 글자(그대로 둠): " + " / ".join(
                    "%s %s" % (n, "".join(sorted(k, key=rank.get))) for n, k in t["other"].items()))
            text = "\n".join(head) + "\n\n" + "\n\n".join(block(ch, rows[ch], tab, want[ch]) for ch in chs)
            if size and size + len(chs) > PER:
                batches.append((must, cur))
                cur, size = [], 0
            cur.append(text)
            size += len(chs)
        if cur:
            batches.append((must, cur))
    for name in os.listdir(HERE):
        if name.startswith("batch-") and name.endswith(".txt"):
            os.remove(os.path.join(HERE, name))
    for i, (must, b) in enumerate(batches, 1):
        with open(os.path.join(HERE, "batch-%02d.txt" % i), "w", encoding="utf-8") as f:
            n = sum(x.count("\n### ") for x in b)
            kind = "반드시 바꿀 글자" if must else "구성 줄도 그 이름을 쓰는 글자: 고정 이름으로 자연스럽게 안 되면 '유지'"
            f.write("# 부품 이름 통일 · batch-%02d (%d자, %s)\n\n" % (i, n, kind) + "\n\n\n".join(b) + "\n")
            print("batch-%02d: %d자, %s" % (i, n, "필수" if must else "유지 가능"))
    with open(os.path.join(HERE, "requested.txt"), "w", encoding="utf-8") as f:
        # "=" marks a kanji whose line may stay: every flagged name is what its own 구성 line says.
        f.write("\n".join(ch + ("=" if all(x[2] for x in want[ch]) else "") for ch in sorted(want, key=rank.get)) + "\n")

    tags = Counter(t[:1] for s in tab.values() for w, t in s["tags"].items())
    multi = [s for s, t in tab.items() if len({x for x in t["tags"].values()}) > 1 or t["other"]]
    print("부품(두 글자 이상이 쓰는 것): %d" % len(tab))
    print("이름이 둘 이상의 뜻으로 갈린 부품: %d" % sum(1 for t in tab.values() if "N" in t["tags"].values()))
    print("실제로 다른 것으로 인정한 이름이 있는 부품: %d" % sum(1 for t in tab.values() if t["other"]))
    print("다시 쓸 외우기: %d자 (%d묶음)" % (len(want), len(batches)))
    pictures = [ch for ch, r in rows.items() if not units(r["spec"]) and any(
        s in tab and s != ch and tab[s]["tags"].get(w) == "N" for w, s in uses_of(r["memo"]))]
    print("뺀 그림 글자(부품 하나): %d자 %s" % (len(pictures), "".join(sorted(pictures, key=rank.get))))
    strokes = set("一丨丶丿乙亅")
    print("그중 획(一丨丶丿乙亅) 이름만 바뀌는 글자: %d" % sum(1 for b in want.values() if all(x[0] in strokes for x in b)))
    print("반드시 바꿀 곳이 있는 글자: %d, 구성 줄도 그 이름을 쓰는 곳뿐인 글자(유지 가능): %d" % (
        sum(1 for b in want.values() if any(not x[2] for x in b)), sum(1 for b in want.values() if all(x[2] for x in b))))
    print("한 글자에서 바뀌는 부품 수: %s" % dict(sorted(Counter(len(b) for b in want.values()).items())))
    print("낱말 꼬리표: %s, 살펴본 부품 %d" % (dict(tags), len(multi)))
    top = Counter(max(b, key=lambda x: tab[x[0]]["n"])[0] for b in want.values()).most_common(15)
    top = [(s, n) for s, n in top]
    print("많이 바뀌는 부품: " + ", ".join("%s(%s) %d" % (s, tab[s]["name"], n) for s, n in top))
    must_parts = Counter(x[0] for b in want.values() for x in b if not x[2])
    print("반드시 바꿀 곳이 많은 부품: " + ", ".join("%s(%s) %d" % (s, tab[s]["name"], n) for s, n in must_parts.most_common(15)))


if __name__ == "__main__":
    main()
