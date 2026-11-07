# Brief: kanji origin notes for a Korean learner's offline kanji card

## Goal
An Android app shows a card for any kanji a Korean speaker taps while studying Japanese. The card already shows
the Korean 훈음, readings, the radical (coloured in the kanji's strokes) and the parts. The learner's goal is to
MEMORISE kanji. You write two short Korean fields per kanji, stored offline in the app:

1. **자원** — how the character really came to have this shape: its formation type and what its parts did.
2. **외우기** — a vivid one-line memory story built from the parts as they LOOK in the Japanese form.

The forms are Japanese 상용한자 (shinjitai). The Korean learner knows Korean 한자음 well, so the Korean 훈음 of parts
is the best anchor.

## Input (in-N.tsv, tab separated, first line is a header starting with #)
1 한자 (Japanese form) · 2 정자 (traditional form when different, else empty) · 3 훈음 · 4 일본어 뜻 (Korean) ·
5 음독 (on readings) · 6 부수 (Kangxi number, form, Korean name) · 7 구성: the top-level parts from KanjiVG,
`part(원형 original)[훈음]{부수,소리}` joined by " + " ({소리} = KanjiVG marks it as the sound part; KanjiVG is
sometimes wrong or silent) · 8 참고: an English etymology hint from makemeahanzi (Chinese-oriented, often based
on 說文解字, may be wrong or missing). Columns 7 and 8 are hints, not truth.

## Output (out-N.tsv)
Exactly one line per input kanji, in the same order, no header, three tab-separated fields:
`한자<TAB>자원<TAB>외우기`. No tabs or line breaks inside a field. UTF-8.

## 자원 rules
- Start with the type and a colon, one of: `상형:` `지사:` `회의:` `형성:` `회의 겸 형성:` `가차:` `국자:` (made in
  Japan) `생략형:` (only for a Japanese abbreviation whose origin is mainly the abbreviation, e.g. 弁 for 辨·瓣·辯)
  or `불명:` when the origin is truly unknown.
- Then 1–2 short sentences, at most 80 characters in total (Hangul + kanji + punctuation; the app's card is small).
  Name each part with its meaning in parentheses: `氵(물)`. For 형성, say which part gives the meaning and which the
  sound, and give the sound part's on reading with katakana when it helps: `毎(マイ)가 소리`.
- When the 정자 differs, describe the origin through the 정자 when needed and add how Japan simplified it, if you are
  sure: `신자체는 米를 メ로 줄임.` Keep it short.
- Follow mainstream scholarship (說文解字 corrected by oracle-bone and bronze evidence, as in modern references).
  When major views split, give the most accepted one and add `(설이 갈림)`. When the original meaning differs from
  today's, say so briefly (`본래 ~, 뒤에 ~의 뜻으로 씀`).
- NEVER invent. If you are not confident of the real origin, write only what you are sure of (the type and the
  parts), or `불명: 자원이 확실하지 않음.` Folk etymology must never be stated as fact. Quality over completeness.
- Style: terse note style ending in a noun or `-음/-함` (`~을 그림.`, `~을 나타냄.`, `~에서 나옴.`), not 해요체/합니다체.

## 외우기 rules
- One line, at most 45 characters. A concrete, vivid picture that ties the parts (as they look in the Japanese form)
  to the meaning, ending with `→ 뜻`: `물(氵)이 매번(毎) 밀려오는 곳 → 바다`.
- Show each part used in parentheses after its Korean word, so the learner sees which shape is which.
- It is a memory aid, so it may differ from the real origin, but it must not contradict the meaning or misname a
  part's shape. Prefer stories that also fit the real origin when the origin is easy.
- Use the part's most familiar Korean meaning. For a sound part, a pun on its Korean 훈음 is fine.
- May be empty only for the simplest pictographs whose 자원 is already the picture (山, 木, 口).

## Examples
```
海	형성: 氵(물)가 뜻, 毎(マイ)가 소리. 모든 강물이 모여드는 큰 물.	물(氵)이 매번(毎) 밀려오는 곳 → 바다
明	회의: 日(해)+月(달)로 밝음을 나타냄(갑골문은 창 囧+月이라는 설도 있음).	해(日)와 달(月)이 함께 비추니 → 밝다
気	형성: 정자 氣는 米(쌀)가 뜻, 气(キ)가 소리. 본래 '손님에게 보내는 쌀', 뒤에 气(기운)를 대신함. 신자체는 米를 メ로 줄임.	김(气) 속에 X(メ) 표시, 보이지 않는 → 기운
働	국자: 일본에서 만든 글자. 亻(사람)+動(움직이다, ドウ는 소리 겸 뜻).	사람(亻)이 몸을 움직이면(動) → 일하다
山	상형: 봉우리 셋이 솟은 산의 모양.	
上	지사: 기준선 위에 짧은 획을 그어 '위'를 가리킴.	땅(一) 위로 솟아오른 막대 → 위
晴	형성: 日(해)가 뜻, 青(セイ)가 소리. 해가 나와 하늘이 개다.	해(日)가 나와 하늘이 푸르다(青) → 개다
休	회의: 亻(사람)+木(나무). 사람이 나무 그늘에서 쉼.	사람(亻)이 나무(木)에 기대어 → 쉬다
```

## Procedure
1. Read your whole input file first.
2. Write the output in chunks: append at most 60 lines per Write/Edit call (or write parts out-N.part1.tsv,
   part2… and join them with `cat` into out-N.tsv, then delete the parts).
3. Run `python3 -I <scratchpad>/origin/check.py <in-N.tsv> <out-N.tsv>` and fix everything it reports until it
   prints OK.
4. Re-read your output once for factual errors and for 외우기 lines that misname a part; fix them.
5. Reply with: the line count, how many 자원 start with `불명:` and which kanji you were least sure of (up to 15,
   with one-line reasons, in Korean). Do not paste the file.
