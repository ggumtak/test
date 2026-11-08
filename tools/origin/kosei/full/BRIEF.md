# Brief: read every kanji card once more, keep what is right, fix what is not

## Context
A Korean-language Android app teaches Japanese kanji to a Korean adult (not a hanja specialist). For each 상용한자 the
card shows 부수 (dictionary radical), 구성 (the parts, written to read as one story with the 유래), 유래 (how the
kanji formed, plain Korean) and 외우기 (a memory story). First read /home/user/test/tools/origin/kosei/BRIEF.md (how
the 구성 line was made and its format) and /home/user/test/tools/origin/kosei/gemini/BRIEF.md (an outside review's
lessons, what the app already does, the tools, the rules for changes and the file formats). This brief applies
those rules to every remaining kanji.

An outside reviewer (Gemini) recently found real problems of these kinds; look for the same everywhere:
- A 구성 part name that does not cover what one sees: strokes in no part (余's 二), a part named by a shape that
  takes only some of its strokes (党's 兄 covering only 儿, so 口 seems counted twice), a first stroke left out
  (事's 一), the wrong look-alike (亡's bottom is 乚, not 匸; 屯 starts with 一).
- A Chinese simplified form (电, 黾) or an unexplained rare character where a learner needs a plain word or the
  visible pieces; a 외우기 that misnames a part's shape (calling 尸+丨 "戸").
- A 구성 line missing for a kanji whose 유래 explains it by parts, or a role/note that states as fact what the
  유래 only hedges.
It was also wrong at times (it invented etymologies, and wanted 頬 for the official 頰): check, do not trust.

## Your batch
- list-N.txt: the kanji; in-N.txt: for each kanji the card's rows as shown and as stored, every stroke grouped by
  the KanjiVG part it is in with its colour (r red radical, p blue sound, - black; "(부품 밖)" = in no named part),
  and 신호: flags a script raised (strokes in no part, a Chinese-looking character, no 구성 line though the 유래
  names parts, an unusual character in the 외우기). Flags are hints, not verdicts; read every kanji, flagged or not.
- `python3 -I /home/user/test/tools/origin/kosei/gemini/show.py 漢 …` shows one kanji in full when you need more.

## What to write (folder /home/user/test/tools/origin/kosei/full/)
- fix-N.tsv: `kanji<TAB>구성<TAB>새 유래<TAB>메모` for each kanji you change (empty column = keep; 구성 without ^/~).
- memo-N.tsv: `kanji<TAB>새 외우기<TAB>이유` for each memory story you replace.
- v-N.md: one line per kanji you changed or considered and left (`漢: 구성 고침 — 이유`, `漢: 그대로 — 이유` only when
  it was a close call), then the counts.
- Run `python3 -I /home/user/test/tools/origin/kosei/gemini/check_gem.py N full` until it prints OK.

## The bar
Change only what is wrong or clearly confusing for the learner; most kanji are fine and stay as they are. Facts:
change a 유래 only for an established error, keeping every hedge; never add a folk etymology as fact. Shapes:
what Japanese print shows. No character the phone's font lacks (the checker lists them). Quality over speed.
Reply in Korean: how many kanji you changed (구성 / 유래 / 외우기), and the 5 most important changes, one line each.
Do not paste the files; no git; temporary files in /tmp/claude-0/-home-user-test/1560e2f5-85e5-5883-ad85-16a26bf44f74/scratchpad/
with "fullN" in the name.
