# Which of two 외우기 lines is the better memory cue? (batch N)

## Context

The app 한자 바로보기 shows a Korean speaker learning Japanese, for each kanji:

- **구성**: its parts, each with a name and a role (`氵(물 · 뜻) + 毎(매양 매 · 소리 マイ)`)
- **유래**: how the character really came about
- **외우기**: a one-line memory story built from those parts, `장면 → 뜻`, each part written `이름(모양)`

The 외우기 is the retrieval cue: seeing the kanji, the parts should bring back one scene, and the scene should
bring back the meaning. For each kanji of your batch there are two candidate lines, **A** and **B**, in random
order; one is the line the app has now and the other is a rewrite. You do not know which is which, and it does
not matter: judge only which line a learner would remember the kanji by.

## What makes a line better

1. **You can see it**: one concrete scene, not an explanation in abstract words.
2. **The meaning falls out of it**: cover the meaning after `→` and read the scene; would you guess it, or think
   "of course" when you see it?
3. **Every part does something**: a part whose name could be deleted without changing the story is padding.
4. **It does not just say the meaning**: `미움이 쌓이는 → 미워하다` explains nothing (showing the meaning happen,
   `새들이 나무에 모여드는 → 모이다`, is fine).
5. **It sticks**: action, sound, feeling, a touch of surprise; funny is good, forced is not.
6. **Short, natural Korean**: read in one breath. A line stuffed with clauses to fit every part in is worse than
   a simpler one, even if it is more "complete".
7. **It does not fight the other rows**: it calls each part what the 구성 line calls it (inflection is free; a
   part whose 구성 note only describes a shape, like `亞를 줄인 꼴`, may be called by what the shape suggests),
   and it does not contradict the 유래.

Longer is not better. A vivid short line beats a thorough long one. When the two are about equally good, say so.

## Your files

- `tools/origin/kosei/polish/cmp-N.tsv` (tab separated; first line is a header): 한자, 훈음, 구성, 유래, A, B.
- You create `tools/origin/kosei/polish/verdict-N.tsv`, **one line for every kanji of the batch**, no header:
  `한자<TAB>winner<TAB>이유<TAB>남은 문제`
  - winner: `A`, `B`, or `=` (about equal, or both weak)
  - 이유: one short Korean sentence on what decides it
  - 남은 문제: what is still wrong with the **winner** (or with both when `=`), in a few Korean words, or empty.
    Be concrete (`'구역(区)'이 여전히 덧붙음`, `너무 길어 한 번에 안 읽힘`, `유래는 X라는데 Y라 부름`).

## Procedure

1. Read this brief, then all of `cmp-N.tsv`, every line.
2. Write `verdict-N.tsv`. Decide each line by reading it as the learner; no scripts, no keyword rules.
3. Run `python3 tools/origin/kosei/polish/check_verdict.py N` from the repository root until it prints `ok`.
4. Reply (in Korean, short): how many A, B and `=`, and the three most common remaining problems among the
   winners. Do not edit any other file and do not commit.
