# Brief: make every 유래 line easy to read and tied to the 구성 line

## Who reads it and what they asked
A Korean adult learning **Japanese** reads a kanji card on their phone. They are not a hanja or linguistics
specialist. The card shows, top to bottom: 부수 (radical), 구성 (the parts, e.g. `氵(물 · 뜻) + 毎(소리 マイ)`),
유래 (how the character was actually formed), 외우기 (a memory story that may be made up).
They said: "If the 유래 has a hard Sino-Korean word or a hard word, I can't understand it, so I can't remember it.
The 부수 must be exact, but the 유래 should connect organically with the 부수 and 구성 and be easy to understand."
They chose: **easy words + tied to the 구성, but the facts stay true** (made-up stories belong only in 외우기).

## Your input: in-N.txt (batch N)
For each 상용한자: 훈음, 음, 부수, `구성(카드)` (as the card shows it; *stars* = the radical's piece, red on the card),
`구성(쓰는 꼴)` (the same line as a fix file writes it), `유래`, `외우기` (context only — do not rewrite it).

## What to change (only where needed — many lines are already fine; leave those out)
1. **Hard words → everyday words.** Hard = a word an ordinary Korean adult in their 20s–30s would not know or
   would have to stop and think about: old or literary Sino-Korean words, specialist terms, rare native words.
   Examples and what to say instead (adapt to the sentence):
   녹봉 → 나라에서 주는 봉급 · 보루 → 적을 막으려고 쌓은 흙벽 · 진지 → 군대가 자리 잡은 곳 · 시렁 → 물건을 얹는
   선반 · 제후 → 왕 아래서 땅을 다스리던 사람 · 천자 → 황제 · 점괘 → 점친 결과 · 희생(제물) → 제사에 바치는 짐승 ·
   형상 → 모양 · 무릇 → 대체로 · 멍에 → 수레를 끌 때 소·말 목에 얹는 나무 · 장정 → 젊은 일꾼 · 갑을병정의 넷째 →
   순서를 매길 때 넷째 · 조리 → 이치 · 법도 → 법과 규칙 · 관청 → 나랏일 보는 곳 · 벼슬아치 → 관리(나랏일 하는 사람) ·
   의례 → 예식 · 제기 → 제사 그릇 · 성곽 → 성벽 · 사당 → 조상을 모신 집 · 주살 → 줄 달린 화살 · 부스럼 → 종기 ·
   삼감 → 조심함 · 도리 → 사람이 지켜야 할 길 · 해서 → 반듯한 글씨체 · 본뜬 → 그린/닮게 만든.
   If the hard word IS the meaning being taught (e.g. 侯 really means 제후), keep it and add a short plain gloss in
   parentheses: `제후(왕 아래서 땅을 다스리던 사람)`. Glosses after kanji must be plain too: `侖(차례)` rather than
   `侖(차례·조리)`.
   The card's own words are fine: 뜻, 소리, 부수, 모양/꼴, 획, 본래, 뒤에, 원래 글자, 옛 사전, 가장 오래된 모양,
   옛 청동기 글자, 의견이 갈림, a kanji with a gloss `氵(물)`, on readings in katakana `毎(マイ)`.
2. **Tie it to the 구성 line.** Reading 구성 then 유래 should feel like one explanation: name each part with the
   same shape and the same word the 구성 line uses for it, in the same order where natural. If you change a word the
   유래 uses for a part and the 구성 note uses that word, change the note too (write the 구성 column: the same
   units, shapes, roles and readings — only the notes may change; a note is at most 16 characters).
3. **Where scholars differ** (the line says 의견이 갈림 / ~라는 의견도 있음 / 사전도 있음), you may put first the
   explanation that best fits the 구성 line. Keep "(의견이 갈림)" or the "other view" sentence. Never invent a new
   explanation, never drop a hedge, never turn "~라고 봄 / 옛 사전은 ~라 풂" into a plain fact.
4. **Make the sentence itself easy.** Short clauses, the subject clear, no stacked noun chains. Terse note style
   ending in a noun or -음/-함/-씀 (no 해요체/합니다체). "본래 A, 뒤에 B" and "→" are fine.

## What must not change
- The type prefix before the colon, exactly (상형, 지사, 회의, 형성, 회의 겸 형성, 가차, 국자, 생략형, 불명).
- Every fact: which part gives the meaning, which gives the sound and its katakana reading, the original meaning,
  the later meaning, how the Japanese form was simplified, which shape replaced which. Never add a fact you do
  not find in the line (do not "explain" with history you remember — the user cannot check it).
- Every hedge (의견이 갈림, 확실하지 않음, ~라고 봄, 옛 사전은 ~라 풂, ~라는 의견도 있음).
- Japanese standards: Japanese print shapes, Japanese on readings. No Chinese simplified shapes.
- Length: the whole 유래 line at most 120 characters.
- The 부수 row is not yours to change. The 외우기 is not yours to change.

## Output: easy-N.tsv (create it; only the kanji you change; no header)
`한자<TAB>새 구성 or empty<TAB>새 유래 or empty<TAB>이유(짧은 한국어: 무엇을 왜)`
- 새 구성: write it like `구성(쓰는 꼴)`: no `^` marks; keep a leading `!` and any `~` exactly as given. Empty =
  keep. Only notes may differ from the input.
- 새 유래: the whole line with its type prefix. Empty = keep.

## Procedure
1. Read all of in-N.txt.
2. Write easy-N.tsv in chunks of at most 60 lines per Write/Edit (or part files joined with cat, then deleted).
3. Run `python3 -I /home/user/test/tools/origin/kosei/easy/check.py N` and fix every "고칠 것" until it prints OK.
   Look at every "참고" line too: a flagged hard word or a missing link is usually worth fixing.
4. Re-read your file once against the input: facts and hedges the same, every word easy, 구성 and 유래 say the
   same thing about each part.
5. Reply in Korean: how many lines you changed, the hard words you replaced most often, and up to 5 lines where
   keeping the facts in easy words was hardest. Do not paste the file.
