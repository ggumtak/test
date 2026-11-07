# Brief: a 구성 line that reads as one story with the 유래 line

## Why
A Korean adult learning Japanese (not a hanja specialist) reads a kanji card on their phone. Under the readings the
card shows, in this order:

    부수   曰  가로 왈 · ひらび
    구성   聿(마침내 율) + 日(가로 왈 · 부수 · 소리)                ← generated from KanjiVG: today's shapes
    유래   뜻+소리: 聿(붓)이 뜻, 者(シャ)를 줄인 꼴(曰)이 소리. 붓으로 '쓰다'.   ← how the kanji really formed

They said: the 구성 line and the 유래 line often seem to talk about different things. Above, 구성 says 聿 is
"마침내" and the bottom is "가로 왈", while 유래 says 聿 is a brush and the bottom is a shortened 者. Both lines are
"true" in their own way, but nothing connects them, so the learner is lost.

Your job: write a new **구성** line for each kanji so that reading 구성 and then 유래 feels like one explanation.
The 구성 line names each part *as the 유래 understands it*, in the 유래's own words:

    구성   聿(붓 · 뜻) + 曰(者를 줄인 꼴 · 소리 シャ)
    유래   뜻+소리: 聿(붓)이 뜻, 者(シャ)를 줄인 꼴(曰)이 소리. 붓으로 '쓰다'.

Quality matters more than speed. Think about each kanji as the learner would see it.

## Input: in-N.tsv (tab separated, first line is a header)
1 한자 · 2 훈음 · 3 음독 · 4 부수 (Kangxi number, shape, Korean name) · 5 부품 in writing order, as the stroke
drawing colours them: `shape(원형 original)(조각 pieces)[its 훈음]{colour}` where the colour is
`빨강=부수` (the radical), `파랑=소리` (the sound part), `일부 빨강(안에 부수 X)` (holds the radical X),
`일부 파랑(안에 소리 X)` (holds the sound part X), or `검정`. When a part is both radical and sound it is drawn red.
6 지금 구성 줄 (what the app shows now) · 7 유래 (`TYPE: body`; the app shows TYPE in plain words: 형성 → 뜻+소리,
회의 → 뜻+뜻, 상형 → 그림, 지사 → 기호, 가차 → 빌린 글자 …) · 8 외우기 (memory story; context only, never change it).

## Output: out-N.tsv
One line per input kanji, same order, no header, four tab-separated columns (the last two may be empty):

    한자<TAB>구성<TAB>새 유래 (only when you changed it, else empty)<TAB>메모 (only when needed, else empty)

## The 구성 column
The parts of column 5, in the same order, joined by " + ", each written `SHAPE[ROLE](NOTE)`:

- **SHAPE**: the part's shape, or its 원형 when the 유래 calls it that (日 → 曰 in 書), or its 조각 (pieces).
  Two or more neighbouring parts the 유래 treats as one go together with "+": `牛+巾`. The pieces of a part may also
  be split into separate units when the 유래 tells them apart (学's 𦥯 = ⺍ and 冖). Prefer common characters: write
  the pieces (`艹+口+夫`) rather than a rare character phones lack (𦰩 𤇾 𦥯 𠬝 and other characters outside the
  usual sets).
- **ROLE** (optional, in square brackets): `뜻` (gives meaning), `소리` (gives the sound), `뜻·소리` (the 유래 says it
  gives the sound and also adds meaning: "소리도 내고 뜻도 보탬", "뜻도 보탬"), `모양` (a piece of a picture, for
  그림 kanji), `기호` (a mark, for 기호 kanji). After 소리 / 뜻·소리 you may add the on reading in katakana that
  the 유래 gives for it (`소리 シャ`). Leave ROLE out when the 유래 gives the part no role.
- **NOTE** (optional, in parentheses, at most 16 characters, no parentheses inside): what this part *is in this
  kanji*, with the 유래's own word: 유래 `聿(붓)` → `붓`. When the 유래 says the shape is a changed or shortened
  form, say what of: `者를 줄인 꼴`, `나무 未가 변한 꼴`, `솥 鼎이 줄어든 꼴`. When the 유래 does not mention the
  part at all, give its plain meaning here (its 훈음 word) or, better, make the 유래 mention it (see below).
- The app shows it as `聿(붓 · 뜻) + 曰(者를 줄인 꼴 · 소리 シャ)`, the radical's part in red and sound parts in blue,
  matching the stroke drawing. So roles must agree with the colours of column 5: a 소리 role needs a blue part
  (파랑, or 일부 파랑), and a blue part needs a 소리 role. When the 유래 clearly disagrees with the colours, follow
  the 유래 and explain in the 메모 starting with `그림:` (e.g. `그림: 冖도 파랗게 칠해짐, 유래로는 집(뜻)`). We will
  fix the drawing from your memos.
- At most 90 characters in all. Plain Korean only; no technical words (신자체, 정자, 형성, 회의, 상형, 지사, 가차,
  자원, 갑골, 금문, 說文 …).
- In the rare case the KanjiVG parts cannot be written to follow the 유래 at all, start the column with `!` and write
  the parts freely (they are then not checked against the drawing) and say why in the 메모. Use this only when truly
  needed.

## The 새 유래 column (optional)
Change the 유래 only to make the link, never to change what it says:
- to name the shape today's kanji shows for a part the 유래 describes by an old form ("소리는 歬(앞, 止+舟. 지금은
  八+月 꼴)"), or
- to mention a part the 유래 leaves out, when you are certain what it is.
Keep every fact and every hedge (의견이 갈림, 확실하지 않음, ~라고 봄, 옛 사전은 …). Never add an uncertain claim. Keep
the `TYPE:` prefix exactly. At most 120 characters, the same plain terse style (ending in a noun or -음/-함), none
of the technical words above in the body. Leave the column empty when the 유래 already links (most kanji).

## Examples (column 5 shortened)
```
海	氵[뜻](물) + 毎[소리 マイ](매양 매)		
書	聿[뜻](붓) + 曰[소리 シャ](者를 줄인 꼴)		
制	牛+巾[뜻](나무 未가 변한 꼴) + 刂[뜻](칼)		
具	目[뜻](솥 鼎이 줄어든 꼴) + 八[뜻](두 손 廾이 변한 꼴)		
前	八+月[소리](앞 歬이 변한 꼴) + 刂[뜻](칼)	형성: 뜻은 刂(칼), 소리는 歬(앞, 止+舟. 지금은 八+月 꼴). 본래 '자르다'(剪의 원래 글자), 뒤에 歬 대신 '앞'으로 씀.	
勇	マ[소리 ヨウ](甬의 윗부분) + 男(田은 甬의 아랫부분, 力은 힘)		
学	⺍[뜻·소리 コウ](두 손과 爻를 줄인 꼴) + 冖[뜻](집) + 子[뜻](아이)		그림: 冖도 소리처럼 파랗게 칠해짐, 유래로는 집(뜻)
主	亠[모양](불꽃 丶이 든 윗부분) + 王[모양](등잔)	상형: 등잔(王 꼴) 위에 불꽃(丶)이 타는 모양. 뒤에 '주인'의 뜻으로 쓰여 불꽃은 炷로 씀.	
章	!音[뜻](음악) + 十[뜻](마침)		KanjiVG가 音+早로 나눠 유래의 音+十과 맞지 않음
```
In each, read the 구성 and then the 유래: every part of the 구성 is found in the 유래 under the same name or meaning,
and every part the 유래 names is visible in the 구성.

## Procedure
1. Read your whole in-N.tsv first.
2. Write out-N.tsv in chunks of at most 50 lines per Write/Edit (or part files joined with `cat`, then delete them).
3. Run `python3 -I /home/user/test/tools/origin/kosei/check_out.py N` and fix every `고칠 것` until it prints OK.
   Then look at each `참고` line (a part the 유래 does not seem to name) and fix the ones that are real gaps.
4. Re-read your output against the input once more, kanji by kanji, as the learner: does 구성 → 유래 read as one
   explanation? Is every note true to the 유래? Fix what is not.
5. Reply in Korean: line count, how many 유래 you changed, how many 그림 memos and `!` lines, and up to 8 kanji you
   found hardest (one line each). Do not paste the file.
