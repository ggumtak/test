# Brief: check an outside AI's review of the kanji card notes, and apply only what is right

## Context
A Korean-language Android app teaches Japanese kanji. For each 상용한자 the card shows four rows (read
/home/user/test/tools/origin/kosei/BRIEF.md first for the 구성 format and how 구성 and 유래 were made to read as one
story):
- 부수: the dictionary radical. 구성: the parts in writing order, `SHAPE[ROLE](NOTE)` joined by " + ", stored in
  /home/user/test/app/src/main/assets/origin.tsv column 4 (a leading "^" marks the radical's part, "~" a part that
  holds the radical; a leading "!" marks a line written freely, not tied to the KanjiVG parts). 유래: how the kanji
  formed (column 2). 외우기: a memory story (column 3, at most 45 characters, ending "→ 뜻").
- The stroke drawing colours the radical's strokes red and the sound part's strokes blue (parts.tsv tags).

The user asked Gemini to review an export of these rows. Its findings for your batch are in g-N.tsv (kanji, row,
problem, proposal). Gemini is often right and sometimes wrong: it has invented etymologies before, and it did not
know some things the app does. Your job: check every finding against the real data and established scholarship,
apply what is right (as Gemini proposed or better), and reject what is wrong, with a reason.

## Already done by the app (do not redo; a finding about only these is "이미 처리됨")
- Every sound part shows an on reading (the one the 유래 gives, else the kanji's own); you may still write one.
- A unit written by a full form shows the variant the kanji prints (示 → 礻, 食 → 飠, 网 → 罒, 羊 → ⺷, 兌 → 兑).
- In a unit of several pieces only the radical's piece is red (丸's 九+丶: only 丶).
- A unit marked "~" (holds the radical) gets "안에 부수 X" added to its note on the card unless the note names X.
  So "the radical is not shown separately" is not an error in itself. Split the radical out as its own unit only
  when the kanji really shows it as a separate piece (勇's 力, 整's 攵) and the line stays clear; then mark nothing,
  the app finds it.

## Tools
- `python3 -I /home/user/test/tools/origin/kosei/gemini/show.py 余 勇 …`: the stored 구성 and how the card shows
  it, 유래, 외우기, and every stroke with its colour (r red, p blue, - black) and the KanjiVG groups it is in.
  This is how you see whether a part name really leaves strokes out (余: strokes 3–4, the 二, are in no part).
- `python3 -I /home/user/test/tools/origin/kosei/gemini/check_gem.py N`: checks your files; run until OK.

## What to write
- gem-N.tsv: `kanji<TAB>구성<TAB>새 유래<TAB>메모` for each kanji you change; empty column = keep. 구성 is the full new
  line without ^/~ marks. Prefer a line that follows parts.tsv's parts (the checker matches it); when the KanjiVG
  parts cannot show the visible shapes (余: 人 + 二+小 with 二 in no part), write a free line starting with "!"
  whose pieces are exactly what Japanese print shows, in order, the radical as its own piece where it is one, and
  say why in the 메모. "-" removes the 구성 line (凸, whose parts do not match what one sees, like 凹 which has none).
  For a kanji that has no 구성 line now (先, 共, 比 …), add one only if its parts are clear and the 유래 names them.
- memo-N.tsv: `kanji<TAB>새 외우기<TAB>이유` for a memory story you replace.
- v-N.md: one line per finding of g-N.tsv: `漢 항목: 채택 | 수정 채택 | 거절 | 이미 처리됨 — 이유` (short).

## Rules
- 구성: SHAPE is what Japanese print shows (check the stroke list). NOTE: what the part is in this kanji in the
  유래's words, at most 16 characters, plain Korean; ROLE 뜻 / 소리 / 뜻·소리 / 모양 / 기호 as the 유래 says. A 소리
  role must match blue strokes and blue strokes need a 소리 unit (the checker tells you); if the 유래 and the
  drawing disagree, follow the 유래 and write `그림: …` in the 메모 (we will recolour).
- 유래: change only for an established factual error, keeping every hedge (의견이 갈림, 확실하지 않음, ~라고 봄, 옛 사전은
  …); never state a folk explanation as fact (体 as '사람의 근본 → 몸' is a popular story, not the origin: at most a
  외우기). Keep the `TYPE:` prefix unless the type itself is wrong (say why in v-N.md). At most 120 characters, no
  technical words.
- 외우기: replace when the old one misnames a shape (a part that looks different), leans on a part the learner
  cannot picture without explanation, or leaves out a big visible part — and only if the new one is clearly
  better: every part named as it looks, in parentheses after its Korean word, at most 45 characters, ending
  "→ 뜻", not contradicting the meaning. A story is a memory aid and may differ from the 유래.
- No character the phone's font lacks (the checker lists them).
- Quality over speed. Reply in Korean: counts of 채택 / 수정 채택 / 거절 / 이미 처리됨, and the 5 most important
  decisions (one line each). Do not paste the files. Do not edit other files; no git. Temporary files: put them in
  /tmp/claude-0/-home-user-test/1560e2f5-85e5-5883-ad85-16a26bf44f74/scratchpad/ with "gemN" in the name.
