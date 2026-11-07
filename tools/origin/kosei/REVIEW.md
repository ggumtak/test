# Review brief: check the new 구성 lines against the 유래

## Context
A Korean kanji-learning app shows, for each kanji, a 구성 line (the parts) and then a 유래 line (how the kanji
formed). Learners complained the two lines seemed to talk about different things (書: 구성 said 聿 "마침내" and the
bottom "가로 왈"; 유래 said 聿 is a brush and the bottom a shortened 者). Another writer has just rewritten the 구성
lines of your batch so that 구성 → 유래 reads as one explanation, sometimes changing the 유래 slightly to make the
link. Read /home/user/test/tools/origin/kosei/BRIEF.md first: it is the writer's brief, with the format and
examples. You are the independent check before this reaches the learner. Be strict; quality matters more than
speed.

## Your files (batch N)
- in-N.tsv: the input the writer saw (column 7 is the original 유래).
- out-N.tsv: the writer's lines: 한자, 구성, 새 유래 (empty = unchanged), 메모.
- You write fix-N.tsv: only the kanji you change, any order, the same four columns. An empty column keeps the
  writer's; in the 새 유래 column write the full corrected 유래, or `원래` to go back to the original 유래.

## Check every line, kanji by kanji, as the learner reading 구성 and then 유래
1. **One story**: every part in 구성 is found in the 유래 under the same name or meaning, and every part the 유래
   names is visible in 구성. A note that says something the 유래 does not support is wrong even if it sounds nice.
2. **True to the 유래**: notes and roles follow the 유래 (뜻 / 소리 / 뜻·소리 / 모양 / 기호). A changed or shortened
   form is named correctly (者를 줄인 꼴). Readings after 소리 are the ones the 유래 gives.
3. **유래 changes**: compare 새 유래 with the original word by word. Every fact and hedge (의견이 갈림, 확실하지 않음,
   ~라고 봄, 옛 사전은 …) must survive; nothing uncertain may be added. Only linking changes are allowed (naming
   today's shape of a part, mentioning a left-out part the writer is sure of). If a change goes further, fix it or
   put `원래`.
4. **Readable**: short, plain, natural Korean a non-specialist understands at once; no technical words; no note
   longer than needed. Prefer the 유래's own words.
5. **그림 memos** (the writer thinks the stroke colours disagree with the 유래): say in the 메모 column whether you
   agree, briefly. `!` lines: confirm they are truly needed.
6. Facts you know to be wrong in the 유래 itself are out of scope unless the writer's change touched them; if one
   is serious, mention it in your reply, not in the file.

## Procedure
1. Read BRIEF.md, then in-N.tsv and out-N.tsv in full.
2. Write fix-N.tsv (it may be empty if nothing needs changing: then create it empty).
3. Run `python3 -I /home/user/test/tools/origin/kosei/check_out.py N` (it checks the lines with your fixes applied)
   until it prints OK.
4. Reply in Korean: how many lines you changed, by kind (연결, 사실, 유래 변경 되돌림, 읽기 쉬움, 역할), up to 8 notable
   ones (one line each), and your verdict on each 그림 memo (one line each). Do not paste the files.
