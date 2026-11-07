# Brief: rewrite kanji origin notes in plain Korean

## Why
A Korean adult learning Japanese reads these notes on a kanji card app. They are NOT a hanja or linguistics
specialist. They said the notes are too hard: "회의 겸 형성, 자원 — I can't tell what these words mean, or whether
only you know them". The card's other rows are fine for them. Your job: rewrite each note's body so an ordinary
adult understands every word on first reading, WITHOUT changing any fact.

## Input: in-N.tsv (tab separated; first line is a header starting with #)
한자 · 훈음 · the current note `TYPE: body` · 외우기 (memory story, for context only; do not rewrite it).

## Output: plain-N.tsv
One line per input kanji, same order, no header: `한자<TAB>TYPE: plain body`.
- Keep the TYPE prefix EXACTLY as in the input (상형, 지사, 회의, 형성, 회의 겸 형성, 가차, 국자, 생략형, 불명) and the
  colon. The app replaces it with a plain label itself (그림, 기호, 뜻+뜻, 뜻+소리, 빌린 글자, 일본에서 만든 글자, 줄인
  글자, 유래 불확실), so the body must not repeat or explain the type word.
- Body: at most 100 characters (hard limit 120), terse note style ending in a noun or -음/-함 (no 해요체/합니다체).

## Words that must not appear in the body, and what to say instead
- 신자체 → "일본에서는 …로 줄여 씀", "일본 글자는 …"; 정자 → "원래 글자"(e.g. "원래 글자 氣는 …")
- 說文 / 설문 / 說文新附 / 康熙字典 / 集韻 → "옛 중국 사전"(or just "옛 사전")
- 갑골문 → "가장 오래된 글자(거북 등딱지·뼈에 새긴 것)" or "가장 오래된 모양"; 금문 → "옛 청동기 글자"
- 본자 → "원래 글자"; 고자 / 고문 → "옛 글자"; 이체(자) → "모양만 다른 같은 글자"; 속자 → "흔히 쓰던 약식 글자";
  약자 → "줄인 글자"; 籀文/주문/전서/소전 → "옛 글씨체"
- 가차 → "소리만 빌려 씀"; 생략형 → "줄인 꼴"; 회의/형성/상형/지사 inside the body → say it plainly
  ("뜻을 합침", "소리를 나타냄", "모양을 그림", "기호로 나타냄")
- 소리 겸 뜻 → "소리도 내고 뜻도 보탬"; (설이 갈림) → "(의견이 갈림)"
- Any other technical word an ordinary adult would not know: replace it or explain it in a few plain words.

Plain words that ARE fine (the card already uses them): 뜻, 소리, 부수, 훈음, 모양/꼴, 획, the kanji themselves with a
gloss in parentheses (`氵(물)`), on readings in katakana (`毎(マイ)`), "본래 … 뒤에 …" (originally … later …).

## Facts
Keep every fact and every hedge: which part gives the meaning, which gives the sound and its reading, the
original meaning, how the Japanese form was simplified, "의견이 갈림", "확실하지 않음", "~라고 봄". You may drop a
bare source name (說文) only if the claim keeps its hedge ("옛 사전에서는 ~라고 풂"). Never add a fact, never
remove a hedge, never turn "~라고 봄" into a certainty. When a body is already plain, keep it as is.

## Examples
```
気	형성: 원래 글자 氣는 米(쌀)이 뜻, 气(キ)가 소리. 처음엔 '손님에게 보내는 쌀', 나중에 '기운'을 뜻함. 일본에서는 米를 メ로 줄여 씀.
言	지사: 舌(혀) 위에 짧은 가로획을 그어 혀에서 나오는 '말'을 나타냄(의견이 갈림).
十	지사: 가장 오래된 모양은 세로줄 하나. 뒤에 가운데에 점이 붙었고, 그 점이 가로줄이 되어 '열'.
昆	불명: 日(해)+比(나란하다). 옛 사전에선 '같다'라 하나 확실하지 않음. '벌레'라는 뜻은 소리만 빌려 쓴 것.
海	형성: 뜻은 氵(물), 소리는 毎(マイ). 모든 강물이 모여드는 큰 물.
働	국자: 일본에서 만든 글자. 亻(사람)+動(움직이다, ドウ는 소리도 냄).
```

## Procedure
1. Read your whole input file. 2. Write plain-N.tsv in chunks of at most 60 lines per Write/Edit (or write part
files and join them with cat, then delete the parts). 3. Run
`python3 -I /tmp/claude-0/-home-user-test/ef490da0-1a09-5ad6-aa0e-e8a9ec1018d3/scratchpad/plain/check_plain.py N` and fix
everything it reports until it prints OK. 4. Re-read your output once against the input for changed facts or lost
hedges. 5. Reply in Korean: line count, how many lines you left unchanged, and up to 5 lines where keeping the
meaning in plain words was hardest (one line each). Do not paste the file.
