# Round 2: a fresh reader looks for what still fails

Read BRIEF.md first: the goal, the four links, the picture test and the format are the same. Round 1 rewrote and
reviewed every batch; its rows are now merged into the cards. You see the cards as the learner sees them, without
the history. Your job is **not** to rewrite good lines in your own taste; it is to catch every line that still
fails, and fix it.

## Input: round2/in-N.txt (about 267 kanji, the merged rows; 자동 점검 hints as before)
New line `부품의 훈음(자료)`: the 훈음 the app's data gives each one-piece 구성 unit (彭 성씨 팽, 땅이름 팽, …,
북소리 팽). Use it for the rule below.

## What round 1 taught (watch for these)
- **Rule 2 applied blindly.** Round 1 sometimes kept a 구성 note that cannot be pictured and bent the 외우기 to it:
  膨's `彭(성씨 팽)` became "팽씨 아저씨". When a sound part's note is a 훈 you cannot picture (성씨, 땅이름, 나라
  이름, 어조사, a hard old word) or that forces the scene, **change the note and the 외우기 together** to another
  훈 of that part listed in `부품의 훈음(자료)` (膨: 북소리 팽 → `북소리(彭)처럼 둥둥 몸(月)이 팽팽 → 부풀다`). Only a
  훈 from that list or the 유래's own words — never a meaning from memory. If none can be pictured, a unit with no
  usable name may be named by its visible pieces (BRIEF rule 1's exception).
- **The meaning said before the arrow.** `꾀어내는 → 꾀다` gives the answer away; the scene should lead to the
  meaning, not state it.
- **The main 훈 missing after `→`** (the first word of the 훈음), or a second meaning that is the same word again.
- **Lines of 40+ characters** and scenes that are two halves glued with a comma.

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
