# Second review: kanji card notes against what the card shows

## Context
An Android kanji card app for a Korean speaker learning Japanese. For each 상용한자 (Japanese form) the card shows,
top to bottom: the kanji drawn from its strokes (the radical's strokes in red, the "sound part" strokes in blue),
the 훈음, readings, then these rows:
- 부수: the radical as Japanese dictionaries file it, with its Korean and Japanese names
- 구성: the top-level parts from KanjiVG, each with its 훈음; the radical part red, the sound part blue
- 자원: how the character really came to its shape (written by one model, already reviewed once)
- 외우기: a one-line memory story from the parts as they look in the Japanese form (not real etymology)
The learner's goal is memorising, and they will trust the 자원 row. A first review already fixed 179 of 2,136
lines. You are a SECOND, independent pass with a different focus: consistency between rows, and what the first
pass may have missed. Read the writer's rules in ../origin/BRIEF.md first
(/tmp/claude-0/-home-user-test/ef490da0-1a09-5ad6-aa0e-e8a9ec1018d3/scratchpad/origin/BRIEF.md).

## Input: rev2/in-N.tsv (tab separated, header line starts with #)
한자 · 정자 · 훈음 · 음독 · 카드의 부수 줄 · 카드의 구성 줄 (`part(훈음){부수,소리}`) · whether the stroke drawing colours
a sound part blue (있음: which part / 없음) · 자원 · 외우기.

## Check every line for
1. **Facts still wrong** in 자원: wrong formation type, wrong sound part or reading, wrong original meaning, a
   wrong or invented shinjitai note, a disputed origin stated as certain. Mainstream scholarship (說文解字 corrected
   by oracle-bone and bronze evidence). If unsure, cut back to what is certain; never guess a replacement.
2. **Rows that contradict each other**: 자원 names a sound part but the 구성 row marks a different part {소리}, or
   marks one when 자원 says 회의/상형/지사; 자원 calls a part by a shape the 구성 row shows differently; the type
   says 형성 but no sound part is named.
3. **외우기**: a part named that the learner cannot see in the Japanese form, a meaning that does not match the
   part's shape in the 구성 row (when they differ, use the 구성 row's shape and you may give both names), a story
   that contradicts the meaning or is too contrived to help, or one that merely repeats the 자원. Must end in
   `→ 뜻`.
4. **Korean**: natural, terse note style; particles agree (the app also fixes particles right after parentheses
   automatically, so do not spend changes only on those).
5. **Format**: 자원 ≤ 80 characters (hard 90), 외우기 ≤ 45 (hard 50), one of the type prefixes in BRIEF.md, no
   Markdown, kanji in their Japanese forms, on readings in katakana.

## Output (two files, only lines you change; none if nothing to change)
- `rev2/fix2-N.tsv`: complete corrected lines `kanji<TAB>자원<TAB>외우기`, in input order.
- `rev2/sound2-N.tsv`: lines `kanji<TAB>part<TAB>reason (Korean, short)` where the blue sound colouring should
  change: `part` is the top-level part exactly as written in the 구성 row that IS the sound part (to colour it), or
  `-` to remove a wrong blue colouring. Only when mainstream analysis clearly supports it and the sound part is one
  of the top-level parts shown; otherwise leave it.
Then run `python3 -I /tmp/claude-0/-home-user-test/ef490da0-1a09-5ad6-aa0e-e8a9ec1018d3/scratchpad/rev2/apply2.py N`
and fix what it reports until it prints OK. Do not edit any other file.

Reply in Korean: number of changed lines split into 사실 오류 / 행끼리 어긋남 / 외우기 / 문장, number of sound
colouring changes, and the 10 most serious fixes (kanji: before → after, one line each). Do not paste files.
