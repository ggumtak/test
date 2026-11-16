#!/usr/bin/env python3
"""Usage: make_request.py — writes what the Gemini app needs to give every part one name across the 외우기 lines
(README.md): names.txt (the fixed name of each part), batch-NN.txt (the kanji whose line calls a part by another
meaning, grouped by that part), requested.txt, and prints the numbers for the README.

groups.tsv holds the judgement the batches follow, one part a line: shape, fixed name, then every word the 외우기
lines call it by with a tag: S the same meaning as the fixed name, I:name a real second identity the 구성 line
shows (月 몸 / 달), N an ad-hoc nickname, which is what gets rewritten."""
import os
import sys
from collections import Counter, defaultdict

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from collect import collect, grade_order, load, shown, uses_of  # noqa: E402

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
        missing = [w for w in ws if w not in tags]
        if missing:
            sys.exit("groups.tsv: %s has no tag for %s" % (shape, missing))
        other = defaultdict(list)
        for w, ks in ws.items():
            if tags[w].startswith("I:"):
                other[tags[w][2:]] += ks
        out[shape] = dict(name=name, tags=tags, other=dict(other), n=n,
                          same=[w for w, _ in Counter({w: len(k) for w, k in ws.items() if tags[w] == "S"}).most_common()])
    return out


def wanted(rows, tab):
    """kanji -> [(shape, word)] its 외우기 calls by an ad-hoc name (tag N)."""
    out = {}
    for ch, r in rows.items():
        bad = [(s, w) for w, s in uses_of(r["memo"]) if s in tab and tab[s]["tags"].get(w) == "N"]
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
    for word, shape in uses_of(r["memo"]):
        if shape not in tab:
            names.append("%s = %s(이 글자에만 있는 부품: 그대로)" % (shape, word or "?"))
        elif (shape, word) in bad:
            names.append("%s = **%s** ← 지금은 '%s'" % (shape, tab[shape]["name"], word))
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

    # A kanji goes under the part it breaks that the most kanji use; parts in the order of how many lines they change.
    by_part = defaultdict(list)
    for ch, bad in want.items():
        by_part[max(bad, key=lambda b: tab[b[0]]["n"])[0]].append(ch)
    sections = sorted(by_part.items(), key=lambda x: (-len(x[1]), -tab[x[0]]["n"]))
    words, _ = collect(rows)
    batches, cur, size = [], [], 0
    for shape, chs in sections:
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
            batches.append(cur)
            cur, size = [], 0
        cur.append(text)
        size += len(chs)
    if cur:
        batches.append(cur)
    for name in os.listdir(HERE):
        if name.startswith("batch-") and name.endswith(".txt"):
            os.remove(os.path.join(HERE, name))
    for i, b in enumerate(batches, 1):
        with open(os.path.join(HERE, "batch-%02d.txt" % i), "w", encoding="utf-8") as f:
            n = sum(x.count("\n### ") for x in b)
            f.write("# 부품 이름 통일 · batch-%02d (%d자)\n\n" % (i, n) + "\n\n\n".join(b) + "\n")
    with open(os.path.join(HERE, "requested.txt"), "w", encoding="utf-8") as f:
        f.write("\n".join(sorted(want, key=rank.get)) + "\n")

    tags = Counter(t[:1] for s in tab.values() for w, t in s["tags"].items())
    multi = [s for s, t in tab.items() if len({x for x in t["tags"].values()}) > 1 or t["other"]]
    print("부품(두 글자 이상이 쓰는 것): %d" % len(tab))
    print("이름이 둘 이상의 뜻으로 갈린 부품: %d" % sum(1 for t in tab.values() if "N" in t["tags"].values()))
    print("실제로 다른 것으로 인정한 이름이 있는 부품: %d" % sum(1 for t in tab.values() if t["other"]))
    print("다시 쓸 외우기: %d자 (%d묶음)" % (len(want), len(batches)))
    print("낱말 꼬리표: %s, 살펴본 부품 %d" % (dict(tags), len(multi)))
    top = Counter({s: len(c) for s, c in by_part.items()}).most_common(15)
    print("많이 바뀌는 부품: " + ", ".join("%s(%s) %d" % (s, tab[s]["name"], n) for s, n in top))


if __name__ == "__main__":
    main()
