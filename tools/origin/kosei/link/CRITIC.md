# Round 2: a fresh reader looks for what still fails

Read BRIEF.md first: the goal, the four links, the picture test and the format are the same. Round 1 rewrote and
reviewed every batch; its rows are now merged into the cards. You see the cards as the learner sees them, without
the history. Your job is **not** to rewrite good lines in your own taste; it is to catch every line that still
fails, and fix it.

## Input: round2/in-N.txt (about 267 kanji, the merged rows; 자동 점검 hints as before)

## A line fails when
- **Link 1–4** of BRIEF.md is broken: a part not in the 구성; a part named otherwise than the 구성 note (outside
  the shape-name exception); a scene that contradicts the 유래; a meaning after `→` the kanji does not have in
  Japanese, or one that leaves out the card's main 훈 (the first word of the 훈음) without a reason.
- **No picture**: you read it and see nothing, or you see something but cannot get from it to the meaning; a list
  of parts with no action; two scenes glued together; a pun that needs hanja knowledge.
- **Too long**: over 40 characters where words can be cut without losing the picture.
- **Hard or odd Korean**: a word a learner in their 20s–30s would not know, or Korean that reads like a
  translation.
- **유래 vs 구성**: they still name one part in two ways (only wording; facts stay — see BRIEF.md).

Grade honestly: a line that is merely "could be a bit better" passes. Look at each kanji once, in order, and
decide pass/fail; spend your effort on the failures.

## Output: round2/fix-N.tsv (only the kanji you fix; same five columns as link-N.tsv)
`한자<TAB>새 구성 or empty<TAB>새 유래 or empty<TAB>새 외우기 or empty<TAB>이유`
and, for a suspected error of fact or a 부수 problem, round2/flags-N.tsv (`한자<TAB>부수|구성|유래<TAB>문제와 근거`).

## Steps
1. Read all of round2/in-N.txt.
2. Write round2/fix-N.tsv (chunks of at most 60 lines per Write/Edit).
3. Run `python3 -I /home/user/test/tools/origin/kosei/link/check.py N round2` until OK; read the 참고 lines.
4. Reply in Korean, briefly: how many kanji you read, how many failed and why (counts per reason), and 3 of your
   fixes you think matter most. Do not paste the file.
