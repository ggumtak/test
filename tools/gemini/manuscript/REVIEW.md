# Fact and name review of rewritten kanji rows (batch N)

## Context

The app 한자 바로보기 teaches Japanese kanji to Korean speakers. For each kanji the card shows:

- **구성**: its parts, each with a role (뜻 meaning, 소리 sound, 모양 shape) and a short name in parentheses:
  `氵(물 · 뜻) + 毎(매양 매 · 소리 マイ)`. The shapes, roles and readings are fixed; only the **names** can change.
- **유래**: how the character really came about. **This is fact.** The card labels it (형성 = 뜻+소리, 회의 = 뜻+뜻,
  상형 = 그림, 불명 = 유래 불확실, …).

An outside writer (Gemini) rewrote both for all 2,136 kanji, under a guide that said: 유래 may be shortened and put in
plain words, but must **not add any fact the current 유래 does not state**, and must **not turn something uncertain into
something certain** (drop it instead). Names should be short and picturable, use the usual name of a meaning part
(氵 물, 亻 사람, 扌 손, 貝 돈, 竹 대나무 …), and not mention rare characters or "~라는 의견".

Your job: for each row of your batch, decide whether each rewritten field may replace the current one. You do not
rewrite anything.

## Your files

- `tools/gemini/manuscript/review-N.tsv` (tab separated; first line is a header): 한자, 훈음, 구성(지금, as the card shows
  it), 구성 이름(새: the new names in the same order, separated by " / "; empty = unchanged), 유래(지금), 유래(새; empty =
  unchanged).
- You create `tools/gemini/manuscript/judged-N.tsv`, **one line per kanji of the batch**, no header:
  `한자<TAB>유래<TAB>구성<TAB>이유`
  - 유래: `ok`, `no`, or `-` when there is no new 유래
  - 구성: `ok`, `no`, or `-` when there are no new names
  - 이유: for every `no`, one short Korean sentence saying exactly what is wrong (`'귀한 관'은 지금 유래에 없는 내용`);
    empty when both are ok or `-`.

## 유래: `no` when the new one

1. states a fact the current one does not: a picture, a story, a period, a meaning, an "본래 X" (rewording is fine:
   `발로 뛰어오름` = `발로 뛰어오름을 뜻함`);
2. turns a hedge into a certainty: the current says `~로 보는 의견`, `의견이 갈림`, `확실하지 않음`, and the new one asserts
   it; dropping the uncertain claim entirely is fine;
3. contradicts the current one or is wrong by your own knowledge of the character's history;
4. drops what a learner needs to make sense of the card: the 정자 (traditional form) when the current shape is a Japanese
   abbreviation, "소리만 빌려 씀" for a 가차, or the reason a part looks the way it does;
5. changes the label before the colon (형성:, 회의:, …).

Dropping the first sentence "뜻은 氵(물), 소리는 毎(マイ)." is fine: the 구성 line already says it.

## 구성 names: `no` when a new name

1. calls a part something the 유래 says it is not (유래: 儿 here is 黑 shortened; a name `사람` is wrong, `검은` is fine);
2. differs from the usual name of that meaning part without a reason (氵 `강`, 亻 `남자`), or calls the same part two
   different things in one row;
3. is not something a learner can picture or is long (over 8 characters), or uses a rare character or "의견";
4. calls a sound part by a meaning it does not have (a sound part may be called by its own 훈음, `매양 매`, or by what its
   shape shows).

Be strict on facts, fair on wording. When the new version is merely different in style and not wrong, it is `ok`.

## Procedure

1. Read this brief, then every line of `review-N.tsv`.
2. Write `judged-N.tsv`. Judge each row by reading it; no scripts, no keyword rules.
3. Run `python3 tools/gemini/manuscript/check_judged.py N` from the repository root until it prints `ok`.
4. Reply (in Korean, short): how many `no` for 유래 and for 구성, and the three most common reasons with one example each.
   Do not edit any other file and do not commit.
