# Which 외우기 lines could a better writer improve? (batch N)

## Context

The app 한자 바로보기 shows a Korean speaker learning Japanese, for each kanji:

- **구성**: its parts, each with a name and a role (`氵(물 · 뜻) + 毎(매양 매 · 소리 マイ)`)
- **유래**: how the character really came about, in plain words
- **외우기**: a one-line memory story built from those parts, `장면 → 뜻`, each part written `이름(모양)`:
  `물(氵)이 매번(毎) 밀려오는 곳 → 바다`

The 외우기 is the retrieval cue: when the learner sees the kanji, the parts should bring back one scene, and the
scene should bring back the meaning. A scene works when you can **see** it and the meaning **falls out of it**.

The lines have been through several rounds of writing and review, so most are fine. The owner now wants to hand
the weakest ones to another writer for a rewrite. Your job is the triage: read every line of your batch and mark
the ones where a clearly better 외우기 is achievable. You do **not** rewrite anything.

## Your files

- `tools/origin/kosei/polish/in-N.tsv` (tab separated; first line is a header): 한자, 훈음, 구성, 유래, 외우기.
- You create `tools/origin/kosei/polish/grade-N.tsv`: **only the lines you flag**, no header:
  `한자<TAB>codes<TAB>severity<TAB>이유`
  - codes: one or more of the codes below, joined by `,` (`W2,W3`)
  - severity: `2` = clearly weak (a learner would not remember the kanji from this line); `1` = works, but a
    noticeably better line is likely possible
  - 이유: one short Korean sentence saying what exactly is weak, concrete enough to guide a rewrite
    (`'버금'이 왜 나쁜 마음이 되는지 이어지지 않음`), not a generic label.

## Weakness codes

- **W1 그려지지 않음**: no concrete scene; abstract words where an image should be
  (`바른(正) 도리를 지키는 → 정치`).
- **W2 뜻이 나오지 않음**: the scene is clear but the meaning after `→` does not follow from it, or follows only
  by a leap the learner must already know (`토끼(卯)를 돈(貝)과 바꾸니 → 무역` is fine: trading; but
  `높은(卓) 분이 지나가니 마음(忄)이 → 슬퍼하다` would be W2 if nothing says why one is sad).
- **W3 끼워 넣은 부품**: a part's name is in the line but plays no role in the scene; you could delete it and the
  story would be the same, or it is glued on (`… 일곱(七) …` with no reason for seven).
- **W4 뜻을 그대로 말함**: the scene already contains the meaning word as its own punchline, so it explains
  nothing (`미움이 쌓이는 → 미워하다`). Not W4 when the scene shows the meaning happening
  (`새들이 나무 위에 모여드는 → 모이다` is fine: that is the picture).
- **W5 어색한 문장**: unnatural or hard-to-parse Korean, too many clauses, an unclear subject.
- **W6 유래와 따로 놂**: the 유래 gives a real picture that would make a good scene, but the 외우기 tells an
  unrelated invented story, or contradicts the 유래 (calls a part something the 유래 says it is not).
- **W7 빠진 부품**: a part of the 구성 line that has a name does not appear in the 외우기 at all.

## How to judge

- Read as the learner: cover the meaning, read only the scene. Can you picture it in one glance? Would you
  guess the meaning, or at least think "of course" when you see it?
- Be strict but fair. **Do not flag a line that works** just because another wording is imaginable. A made-up
  story is normal and good when the real origin is not picturable; using a sound part by its meaning
  (`절(寺)`) is the method, not a flaw.
- Kanji of one part (`(부품 하나)`) or without a 구성 line are judged only on W1, W2, W4, W5, W6.
- Expect to flag somewhere between one line in ten and one in four. If you are flagging far more, you are
  flagging taste; far fewer, you are not reading as a learner.

## Procedure

1. Read this brief, then all of `in-N.tsv`, every line.
2. Write `grade-N.tsv`.
3. Run `python3 tools/origin/kosei/polish/check_grade.py N` from the repository root and fix what it reports
   until it prints `ok`.
4. Reply (in Korean, short): how many lines you flagged at severity 2 and at severity 1, the three most typical
   weaknesses you saw with one example each, and anything in the brief that was hard to apply.
   Do not edit any other file and do not commit.
