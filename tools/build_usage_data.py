#!/usr/bin/env python3
"""Builds app/src/main/assets/usage.tsv: for each 상용한자, the readings worth learning, the ones met most often
first, each with common words that use it (their reading and a short Korean meaning).

Usage: build_usage_data.py <joyo2010.txt> <jamdict.db> <kanji.tsv> <jlpt-dir> <out.tsv> [words.tsv] [--todo todo.tsv]
  joyo2010.txt - https://raw.githubusercontent.com/cjkvi/cjkvi-tables/master/joyo2010.txt (the 2010 常用漢字表 with
                 its 音訓, from Wikipedia's 常用漢字一覧; a reading in （） is one the table limits to a few words)
  jamdict.db   - the SQLite database of the PyPI package jamdict-data 1.5 (JMdict with its priority tags)
  kanji.tsv    - app/src/main/assets/kanji.tsv (KANJIDIC's readings, to split a word's reading by kanji)
  jlpt-dir     - n5.csv … n1.csv of https://github.com/stephenmk/yomitan-jlpt-vocab (original_data/): Jonathan
                 Waller's JLPT word lists (CC BY) with each word's JMdict entry number
  words.tsv    - tools/usage/words.tsv: form, reading, Korean meaning (written by Claude from JMdict's English
                 glosses and reviewed; see tools/usage/README.md). A word without a line is shown without a meaning.
  --todo       - writes the chosen words that have no meaning yet: form, reading, English glosses, JLPT level,
                 and the kanji and reading each stands for.
  --why 明     - prints each reading of that character with its rank and best words, to see why an order came out.

Why: the card used to list KANJIDIC's readings in its order, so readings outside the table (食: く.らう, は.む)
stood before or beside the ones a learner meets (た.べる), the table's own readings were cut off after five
(下: くだ.る, お.りる) and one word came twice (生: う.まれる, うま.れる). A reading is learned through the
words it appears in, so each one now carries one or two, the easiest (JLPT N5 first) and most common.

One line per character, sorted by UTF-8 bytes (the app binary-searches it), tab separated:
  char  on  kun
on / kun: readings joined by ";", each `READING|form:kana:meaning|form:kana:meaning`. READING is katakana for on,
"stem.okurigana" for kun (as KANJIDIC writes it); in （） when the table limits it to a few words; with a leading
"*" when it is not in the table but a common word is written with it (家: *うち). The order is how soon and how
often a learner meets the reading (the JLPT level of its best-known word; readings of one level stay in the table's
order, which keeps a verb next to its partner: あ.ける, あ.く); the readings outside the table come last.
Meanings never contain ";", "|", ":" or a tab.
"""
import csv
import os
import re
import sqlite3
import sys
from collections import defaultdict

VOICED_FROM = "かきくけこさしすせそたちつてとはひふへほ"
VOICED_TO = "がぎぐげござじずぜぞだぢづでどばびぶべぼ"
HALF_TO = "ぱぴぷぺぽ"
MAX_ON_WORDS = 2
MAX_KUN_WORDS = 1
# A second example must be a fairly common word itself; a word of four or more characters is a weaker example.
SECOND_MIN = 40
LONG_PENALTY = 120
JLPT_SCORE = {5: 500, 4: 400, 3: 300, 2: 200, 1: 120}
# A reading outside the table is shown only when a word this well known is written with it (JLPT N3 or easier).
OUTSIDE_MIN = 300
# Words that are no example of a reading: old ones (not even as a kun reading's own word: 生う) and coarse ones.
OLD_MISC = {"archaism", "obsolete term", "rare", "obscure term", "dated term"}
COARSE_MISC = {"vulgar expression or word", "derogatory", "slang", "manga slang", "Internet slang"}
# KANJIDIC readings outside the table that are only a form of a table reading or a rare spelling.
OUTSIDE_SKIP = {("来", "き"), ("素", "もと")}
KANA_MISC = "word usually written using kana alone"
# Four 상용한자 have two shapes in use: the table's (剝 𠮟 塡 頰) and the everyday one (剥 叱 填 頬). The app's data
# (kanji.tsv grades, origin.tsv) is keyed by the first of each pair below; words are written either way. Both shapes
# get a line, each with the words spelt in its own shape.
VARIANTS = {"剝": "剥", "𠮟": "叱", "填": "塡", "頬": "頰"}
TO_KEY = str.maketrans(VARIANTS)


def hira(s):
    return "".join(chr(ord(c) - 0x60) if "ァ" <= c <= "ヶ" else c for c in s)


def is_kanji(ch):
    cp = ord(ch)
    return 0x4E00 <= cp <= 0x9FFF or 0x3400 <= cp <= 0x4DBF or 0xF900 <= cp <= 0xFAFF or 0x20000 <= cp <= 0x3134F


def variants(r, word_start):
    """r and its sound-changed forms (Readings.variants in the app): voiced first kana, っ for a final つくちき."""
    out = [r]
    if not word_start and r:
        i = VOICED_FROM.find(r[0])
        if i >= 0:
            out.append(VOICED_TO[i] + r[1:])
            if i >= 15:
                out.append(HALF_TO[i - 15] + r[1:])
    for v in list(out):
        if len(v) >= 2 and v[-1] in "つくちき":
            out.append(v[:-1] + "っ")
    return out


def load_joyo(path):
    """char -> (on, kun): lists of (reading, limited); kun as "stem.okurigana"."""
    out = {}
    with open(path, encoding="utf-8") as f:
        for line in f:
            if line.startswith("#") or not line.strip():
                continue
            cols = line.rstrip("\n").split("\t")
            on, kun = [], []
            for item in cols[-1].split("、"):
                item = item.strip()
                if not item:
                    continue
                limited = "（" in item
                r = item.strip("（）")
                if any("ァ" <= c <= "ヺ" for c in r):
                    on.append((r, limited))
                else:
                    kun.append((r.replace("-", ".", 1).replace("-", ""), limited))
            out[cols[0].translate(TO_KEY)] = (on, kun)
    return out


def load_kanjidic(path):
    """char -> (candidate readings in hiragana as Readings.candidates in the app, on readings, kun readings)."""
    cands, ons, kuns = {}, {}, {}
    with open(path, encoding="utf-8") as f:
        for line in f:
            c = line.rstrip("\n").split("\t")
            cs, on_list, kun_list = [], [], []
            for on in c[3].split("・"):
                on = on.strip("- ")
                if on:
                    cs.append(hira(on))
                    on_list.append(on)
            for kun in c[4].split("・"):
                affix = kun.strip().startswith("-") or kun.strip().endswith("-")
                s = kun.strip("- ")
                if s:
                    cs.append(s.split(".")[0])
                    cs.append(s.replace(".", ""))
                    if not affix:
                        i = s.find(".")
                        kun_list.append(s if i < 0 else s[:i] + "." + s[i + 1:].replace(".", ""))
            cands[c[0]] = list(dict.fromkeys(cs))
            ons[c[0]] = list(dict.fromkeys(on_list))
            kuns[c[0]] = list(dict.fromkeys(kun_list))
    return cands, ons, kuns


def split(word, reading, cands_of):
    """The reading each character of word takes as (surface, base candidate), or None (Readings.split in the app)."""
    chars = list(word)
    if len(chars) > 12:
        return None
    r = hira(reading)
    options = []
    for i, ch in enumerate(chars):
        base = chars[i - 1] if ch == "々" and i > 0 else ch
        opts = []
        for cand in cands_of.get(base, []):
            for v in variants(cand, i == 0):
                opts.append((v, cand))
        opts.sort(key=lambda x: -len(x[0]))
        options.append(opts)
    out = [None] * len(chars)
    failed = set()

    def go(ci, ri):
        if ci == len(chars):
            return ri == len(r)
        if (ci, ri) in failed:
            return False
        ch = chars[ci]
        if not is_kanji(ch) and ch != "々":
            h = hira(ch)
            if not r.startswith(h, ri):
                return False
            out[ci] = (h, None)
            return go(ci + 1, ri + len(h))
        seen = set()
        for v, cand in options[ci]:
            if v and v not in seen and r.startswith(v, ri):
                seen.add(v)
                out[ci] = (v, cand)
                if go(ci + 1, ri + len(v)):
                    return True
        failed.add((ci, ri))
        return False

    return out if go(0, 0) else None


def priority(tags):
    """How common JMdict's priority tags say a word is, 0-85: its rank band among the 24,000 most frequent
    newspaper words (nf01 = the first 500), and the basic-vocabulary list (ichi1) well above the rest."""
    s = 0
    for t in tags:
        if t.startswith("nf"):
            s = max(s, 49 - int(t[2:]))
    if "ichi1" in tags:
        s += 30
    if "news1" in tags:
        s += 6
    if "spec1" in tags:
        s += 6
    if s == 0 and tags:
        s = 2
    return s


def load_jlpt(directory):
    """(JMdict entry, reading) -> (level 5..1, Waller's definition, the written form the list gives), the easiest
    level when the list has the word twice (明い N5, 明るい N1)."""
    out = {}
    for level in (5, 4, 3, 2, 1):
        with open(os.path.join(directory, "n%d.csv" % level), encoding="utf-8", newline="") as f:
            for row in csv.DictReader(f):
                if row["kanji"] and row["jmdict_seq"].isdigit():
                    out.setdefault((int(row["jmdict_seq"]), hira(row["kana"])),
                                   (level, row["waller_definition"], row["kanji"]))
    return out


class Word:
    __slots__ = ("form", "kana", "score", "glosses", "level", "kana_only", "bad", "old", "listed", "alt")

    def __init__(self, form, kana, score, glosses, level, kana_only, bad, old, listed, alt):
        self.form, self.kana, self.score, self.glosses = form, kana, score, glosses
        self.level, self.kana_only, self.bad, self.old, self.listed = level, kana_only, bad, old, listed
        # A second reading of the entry that the JLPT list does not have below N1 (消耗 しょうこう beside しょうもう).
        self.alt = alt


def load_words(db, jlpt):
    """Every written form of JMdict with each of its readings."""
    c = sqlite3.connect(db)
    kj_tags = defaultdict(set)
    for kid, t in c.execute("select kid, text from KJP"):
        kj_tags[kid].add(t)
    kn_tags = defaultdict(set)
    for kid, t in c.execute("select kid, text from KNP"):
        kn_tags[kid].add(t)
    old_kana = {kid for (kid,) in c.execute("select kid from KNI where text in ('ok', 'sk', 'ik')")}
    odd_kanji = {kid for (kid,) in c.execute("select kid from KJI where text in ('oK', 'iK', 'io', 'ateji', 'rK', 'sK')")}
    restrict = defaultdict(set)
    for kid, t in c.execute("select kid, text from KNR"):
        restrict[kid].add(t)
    kana = defaultdict(list)
    for kid, idseq, text, nokanji in c.execute("select ID, idseq, text, nokanji from Kana order by ID"):
        if not nokanji and kid not in old_kana:
            kana[idseq].append((kid, text))
    first_sense = {}
    for sid, idseq in c.execute("select ID, idseq from Sense order by ID"):
        first_sense.setdefault(idseq, sid)
    entry_of = {sid: idseq for idseq, sid in first_sense.items()}
    glosses = defaultdict(list)
    for sid, text in c.execute("select sid, text from SenseGloss where lang = 'eng'"):
        if sid in entry_of and len(glosses[entry_of[sid]]) < 4:
            glosses[entry_of[sid]].append(text)
    bad, old, kana_only = set(), set(), set()
    for sid, text in c.execute("select sid, text from misc"):
        if sid in entry_of:
            if text in OLD_MISC:
                old.add(entry_of[sid])
                bad.add(entry_of[sid])
            elif text in COARSE_MISC:
                bad.add(entry_of[sid])
            elif text == KANA_MISC:
                kana_only.add(entry_of[sid])
    out = []
    for kid, idseq, form in c.execute("select ID, idseq, text from Kanji order by ID"):
        if kid in odd_kanji:
            continue
        form = form.translate(TO_KEY)
        n = -1
        for nid, text in kana.get(idseq, []):
            if nid in restrict and form not in restrict[nid]:
                continue
            n += 1
            level, definition, listed = jlpt.get((idseq, hira(text)), (0, "", ""))
            listed = listed.translate(TO_KEY)
            # The pair is as common as its rarer half: a common word's rare spelling or reading does not count.
            s = min(priority(kj_tags.get(kid, ())), priority(kn_tags.get(nid, ())))
            gl = list(glosses.get(idseq, []))
            # The JLPT level goes to the spelling the list gives and to the entry's other common spellings.
            # A second reading that only the N1 list has is a variant it lists beside the usual one (消耗 しょうこう,
            # 地形 じぎょう), not a word to learn.
            if form != listed and not kj_tags.get(kid) or n > 0 and level == 1:
                level = 0
            if level:
                s += JLPT_SCORE[level]
                if definition and definition not in gl:
                    gl.insert(0, definition)
            out.append(Word(form, hira(text), s, gl, level, idseq in kana_only, idseq in bad, idseq in old,
                            form == listed, n > 0 and not level))
    return out


def masu(okuri):
    """The forms of this okurigana a compound may use (生き of 生きる, 読み of 読む); the plain form first."""
    forms = [okuri]
    if not okuri:
        return forms
    last = okuri[-1]
    if last == "る" and len(okuri) >= 2:
        forms.append(okuri[:-1])
    i_row = {"う": "い", "く": "き", "ぐ": "ぎ", "す": "し", "つ": "ち", "ぬ": "に", "ぶ": "び", "む": "み", "る": "り"}
    if last in i_row:
        forms.append(okuri[:-1] + i_row[last])
    if last == "い":
        forms.append(okuri[:-1])
    return forms


def derived(r, official):
    """Whether kun reading r is only another form of a table reading (ちが.い of ちが.う, うま.れる of う.まれる)."""
    joined = r.replace(".", "")
    for o in official:
        stem, _, okuri = o.partition(".")
        if joined == stem + okuri or r.split(".")[0] == stem:
            return True
        if any(joined == stem + m for m in masu(okuri)):
            return True
    return False


def main(argv):
    todo_path = why = None
    if "--todo" in argv:
        i = argv.index("--todo")
        todo_path = argv[i + 1]
        argv = argv[:i] + argv[i + 2:]
    if "--why" in argv:
        i = argv.index("--why")
        why = argv[i + 1]
        argv = argv[:i] + argv[i + 2:]
    joyo_path, db_path, kanji_path, jlpt_dir, out_path = argv[:5]
    meanings = {}
    if len(argv) > 5:
        with open(argv[5], encoding="utf-8") as f:
            for line in f:
                c = line.rstrip("\n").split("\t")
                if len(c) >= 3 and not line.startswith("#") and c[2].strip():
                    meanings[(c[0].translate(TO_KEY), c[1])] = c[2].strip()
    joyo = load_joyo(joyo_path)
    cands_of, dic_on, dic_kun = load_kanjidic(kanji_path)
    words = load_words(db_path, load_jlpt(jlpt_dir))

    # Each character's readings: the table's, then KANJIDIC's others that are not just a form of a table reading.
    readings = {}
    for ch, (on, kun) in joyo.items():
        table_on = [r for r, _ in on]
        table_kun = [r for r, _ in kun]
        more_on = [r for r in dic_on.get(ch, []) if r not in table_on]
        more_kun = [r for r in dic_kun.get(ch, []) if r not in table_kun and not derived(r, table_kun)]
        readings[ch] = (table_on + more_on, table_kun + more_kun)

    used = {ch: defaultdict(list) for ch in joyo}  # reading -> [(score, word)]
    own = {}  # (form, kana) -> word, to find a kun reading's own word (生きる)
    for w in words:
        if not w.old:
            own.setdefault((w.form, w.kana), w)
    for w in words:
        # No example from a word's rarer second reading: it would teach the word with the wrong reading.
        if w.score <= 0 or w.bad or w.kana_only or w.alt or len(w.form) > 5:
            continue
        chars = list(w.form)
        # Words in the table's kanji and kana only: an example must not bring a harder character with it.
        if not any(ch in joyo for ch in chars) or any(is_kanji(ch) and ch not in joyo for ch in chars):
            continue
        parts = split(w.form, w.kana, cands_of)
        if parts is None:
            continue
        for i, ch in enumerate(chars):
            if ch not in joyo or parts[i][1] is None:
                continue
            surface, cand = parts[i]
            tail = ""
            j = i + 1
            while j < len(chars) and "ぁ" <= chars[j] <= "ゖ":
                tail += chars[j]
                j += 1
            on, kun = readings[ch]
            # A kun reading whose okurigana the word shows wins (死ぬ is し.ぬ, not シ), then an on reading, then a
            # kun reading without okurigana.
            same = [r for r in kun if r.split(".")[0] == cand or r.replace(".", "") == cand]
            fits = []
            for r in same:
                okuri = r.split(".")[1] if "." in r else ""
                if r.replace(".", "") == cand and okuri:
                    fits.append((len(okuri), r))  # the word leaves the okurigana out (受付)
                elif okuri and any(tail.startswith(m) for m in masu(okuri)):
                    fits.append((max(len(m) for m in masu(okuri) if tail.startswith(m)), r))
            key = max(fits)[1] if fits else next((r for r in on if hira(r) == cand), None)
            if key is None:
                bare = [r for r in same if "." not in r]
                if bare and not tail or len(same) == 1 and bare:
                    key = bare[0]
            if key is not None:
                # A word that shows the reading as it is (no voicing, no っ) is the clearer example.
                s = w.score - (25 if surface != cand else 0) - (LONG_PENALTY if len(chars) >= 4 else 0)
                if s > 0:
                    used[ch][key].append((s, w))

    lines = []
    todo = {}
    stats = defaultdict(int)
    for ch, (on, kun) in joyo.items():
        limited = {r for r, lim in on + kun if lim}
        table = {r for r, _ in on + kun}

        def own_word(key):
            stem, _, okuri = key.partition(".")
            return own.get((ch + okuri, stem + okuri))

        def best(key, kun_reading):
            """The words of a reading, best first: (score, word)."""
            ranked = sorted(used[ch].get(key, []), key=lambda x: (-x[0], len(x[1].form), x[1].form))
            if kun_reading:
                me = own_word(key)
                if me is not None and all(w is not me for _, w in ranked):
                    ranked.append((me.score, me))
                    ranked.sort(key=lambda x: (-x[0], len(x[1].form), x[1].form))
            return ranked

        def freq(key, kun_reading):
            # The best-known word decides (明るい before 明ける, however many compounds 明け has).
            top = best(key, kun_reading)
            return top[0][0] if top else 0

        def pick(key, limit, kun_reading):
            ranked = best(key, kun_reading)
            chosen = []
            if kun_reading:
                # A kun reading's own word (生きる) says what the reading means: it comes first, unless the kanji
                # alone is only a rare word or a prefix (大 おお) and a compound is far better known.
                me = own_word(key)
                top = ranked[0][0] if ranked else 0
                if me is not None and ("." in key or me.score >= top - 100):
                    chosen.append(me)
            for s, w in ranked:
                if len(chosen) >= limit:
                    break
                # An on reading's example is a compound, unless the kanji alone is a well-known word (本, 楽).
                if not kun_reading and w.form == ch and s < JLPT_SCORE[1]:
                    continue
                if chosen and s < SECOND_MIN:
                    break
                if any(w.form == c.form or (len(w.form) > 1 and len(c.form) > 1 and (w.form in c.form or c.form in w.form))
                       for c in chosen):
                    continue
                chosen.append(w)
            return chosen[:limit]

        def field(all_readings, kun_reading):
            shown = []
            table_on = {v for r, _ in on for v in variants(hira(r), False)}
            for r in all_readings:
                if r in table:
                    shown.append(r)
                    continue
                # Outside the table: only with a well-known word that is really written with the kanji, and not
                # an on reading in disguise (罰: ばっ.する, 気: き).
                if (ch, r) in OUTSIDE_SKIP or kun_reading and r.split(".")[0] in table_on:
                    continue
                top = [(s, w) for s, w in best(r, kun_reading) if w.listed and not w.kana_only and not w.bad]
                if top and top[0][0] >= OUTSIDE_MIN:
                    shown.append(r)
                    stats["readings outside the table"] += 1
            # The best-known reading first: by the JLPT level of its best word. Readings of one level stay in the
            # table's order, which keeps a verb next to its partner (あ.ける, あ.く; い.く before ゆ.く). A limited
            # reading counts one level harder, so it does not pass the main one (風: フウ, then フ of 風呂), but it
            # is not put last either (何: なん is an everyday word); the readings outside the table are.
            def band(r):
                top = freq(r, kun_reading)
                tier = min(5, top // 100) if top >= JLPT_SCORE[1] else 0 if top >= SECOND_MIN else -1
                return (r not in table, -(tier - 1 if r in limited else tier))

            shown.sort(key=band)
            if ch == why:
                for r in shown:
                    print(r, band(r), [(s, w.form, w.kana) for s, w in best(r, kun_reading)[:4]])
            items = []
            for r in shown:
                ws = pick(r, MAX_KUN_WORDS if kun_reading else MAX_ON_WORDS, kun_reading)
                cells = ["*" + r if r not in table else "（" + r + "）" if r in limited else r]
                for w in ws:
                    m = meanings.get((w.form, w.kana), "")
                    if not m:
                        todo.setdefault((w.form, w.kana), (w, []))[1].append(ch + " " + r)
                        stats["without meaning"] += 1
                    assert not re.search(r"[;|:\t]", m), (w.form, m)
                    cells.append(w.form + ":" + w.kana + ":" + m)
                    stats["words"] += 1
                if not ws:
                    stats["readings without a word"] += 1
                stats["readings"] += 1
                items.append("|".join(cells))
            return ";".join(items)

        lines.append((ch, field(readings[ch][0], False), field(readings[ch][1], True)))
    for other, key in VARIANTS.items():
        swap = str.maketrans({key: other})
        lines.extend((other, on.translate(swap), kun.translate(swap)) for ch, on, kun in list(lines) if ch == key)
    lines.sort(key=lambda x: x[0].encode("utf-8"))
    with open(out_path, "w", encoding="utf-8", newline="\n") as f:
        for ch, on, kun in lines:
            f.write(ch + "\t" + on + "\t" + kun + "\n")
    if todo_path:
        with open(todo_path, "w", encoding="utf-8", newline="\n") as f:
            for (form, kana), (w, where) in sorted(todo.items()):
                f.write("\t".join([form, kana, "; ".join(w.glosses), "N%d" % w.level if w.level else "",
                                   ", ".join(where)]) + "\n")
    print(len(lines), "lines;", ", ".join("%s %d" % kv for kv in sorted(stats.items())),
          "; distinct words to gloss", len(todo))


if __name__ == "__main__":
    main(sys.argv[1:])
