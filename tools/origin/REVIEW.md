# Review brief: check kanji origin notes before they ship in an app

## Context
An Android kanji card app for a Korean speaker learning Japanese shows, for each 상용한자 (Japanese shinjitai form),
two short Korean notes written by another model:
- **자원**: the real formation (type, what the parts did), e.g. `형성: 氵(물)이 뜻, 毎(マイ)가 소리. 모든 강물이 모여드는 큰 물.`
- **외우기**: a one-line memory story from the parts as they look in the Japanese form, e.g.
  `물(氵)이 매번(毎) 밀려오는 곳 → 바다`.
The writer's brief is BRIEF.md in the same folder: read it first for the rules and limits. The learner will trust
these notes, so a wrong origin is worse than a short one. You are the second, independent check.

## Your files
For each batch N given to you: `in-N.tsv` (inputs: kanji, 정자, 훈음, meanings, on readings, radical, KanjiVG parts
with {부수,소리} marks, a makemeahanzi hint) and `out-N.tsv` (the notes: `kanji<TAB>자원<TAB>외우기`).

## What to check, line by line (read every line)
1. **Facts in 자원**: wrong type (상형/지사/회의/형성/회의 겸 형성/가차/국자/생략형/불명), a part misidentified or given
   the wrong meaning, the wrong sound part, a wrong original meaning, a wrong or invented shinjitai note, folk
   etymology stated as fact, or confident claims about a disputed origin without `(설이 갈림)`. Use mainstream
   scholarship (說文解字 corrected by oracle-bone/bronze evidence). If unsure whether something is right, cut it
   back to what is certain rather than guess a replacement.
2. **외우기**: misnames a part's shape (says 日 where the form has 目), uses a part not in the Japanese form,
   contradicts the meaning, or is so contrived it will not help. Must end with `→ 뜻`. Empty only for the simplest
   pictographs.
3. **Korean**: natural, terse note style. Kanji in their Japanese forms. On readings in katakana. The very common
   opening `A(…)이/가 뜻, B(…)이/가 소리.` will be rewritten automatically to `뜻은 A(…), 소리는 B(…).` after
   your review, so do NOT spend changes on the particles of that exact pattern; fix other particle errors (a
   particle after `X(gloss)` agrees with the gloss's last syllable: `물(氵)이`, `치다(攵)가`).
4. **Format**: three tab-separated fields; 자원 ≤ 80 characters (hard limit 90), 외우기 ≤ 45 (hard limit 50); no Markdown.

## Output
For each batch N, write `fix-N.tsv`: ONLY the lines you change, each the complete corrected line in the same
three-field format (`kanji<TAB>자원<TAB>외우기`), in input order. Do not include unchanged lines. Then run
`python3 -I <folder>/apply.py N` — it applies your fixes to a copy and runs the format check; fix until it prints OK.
Do not edit in-N.tsv or out-N.tsv.

Reply in Korean with, per batch: how many lines you changed, split into 사실 오류 / 외우기 / 문장·조사 / 형식, and
up to 10 of the most serious factual errors you fixed (kanji: before → after, one line each). Do not paste files.
