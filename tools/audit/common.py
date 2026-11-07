"""Shared loading for the brush-colour audit: what the card draws and what it should colour."""
import os
REPO = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "app", "src", "main", "assets") + "/"
COL = {"r": "#C0392B", "p": "#1F63B0", "-": "#161616"}


def table(name):
    out = {}
    for line in open(REPO + name, encoding="utf-8"):
        f = line.rstrip("\n").split("\t")
        out[f[0]] = f
    return out


def radicals():
    rows = []
    for line in open(REPO + "radicals.tsv", encoding="utf-8"):
        if line.startswith("#") or not line.strip():
            continue
        f = line.rstrip("\n").split("\t")
        rows.append((int(f[0]), f[1], f[2], f[3], f[4], f[5] if len(f) > 5 else ""))
    return rows


def find(rads, n, form, pos):
    mine = [r for r in rads if r[0] == n]
    if not mine:
        return None
    if form:
        for r in mine:
            if r[1] == form and r[2] == pos and pos:
                return r
        for r in mine:
            if r[1] == form and not r[2]:
                return r
        for r in mine:
            if r[1] == form:
                return r
    return next((r for r in mine if not r[2]), mine[0])


def expected(ch, kan, parts, rads):
    """(radical shape the card names, sound parts the card marks, tags string)."""
    p = parts[ch]
    r = find(rads, int(p[1]), p[2], p[3]) if p[1] else None
    shape = (p[2] if p[2] and r and p[2] == r[1] else (r[1] if r else "?"))
    sound = [x.split("/")[0] for x in p[4].split() if "p" in x.split("/")[2] and "r" not in x.split("/")[2]]
    tags = "".join(s[0] for s in p[5].split("|") if s)
    return shape, sound, tags
