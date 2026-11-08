# Brief: make the four rows of every kanji card one story, and every 외우기 easy to picture

## Who reads it and what they asked
A Korean adult learning **Japanese** reads a kanji card on their phone. They are not a hanja or linguistics
specialist. The card shows, top to bottom:

- **부수** – the dictionary radical and its name (`氵 삼수변(물) · さんずい`). From KANJIDIC2; exact; not yours to edit.
- **구성** – the parts as the Japanese glyph shows them, each with what it is here and its role:
  `*言*(말 · 뜻) + 義(옳을 의 · 뜻·소리 ギ)`. *Stars* = the radical's piece (red on the card), 소리 parts are blue.
- **유래** – how the character was really formed (true; hedged where scholars differ). The card shows the type in
  plain words (뜻+소리, 뜻+뜻, 그림, 기호, 빌린 글자, 일본에서 만든 글자, 줄인 글자, 유래 불확실).
- **외우기** – a one-line memory picture, `장면 → 뜻`. It may be made up, but it is read right after the 유래.

The user said (Korean, translated): "Check whether the four rows — 부수, 구성, 유래, 외우기 — connect organically, and
whether the explanations are easy to understand and easy to associate. Right now the 유래 and the 외우기 often say
different things, and many 외우기 don't evoke anything at all when I read them — they feel forced. Improve them as
much as possible; the goal is better learning."

So the main work is the **외우기**. The 유래 and 구성 were reviewed many times for facts and plain words; you touch
them only to make the four rows agree (below). Many lines are already good: **leave good lines alone.**

## Your input: in-N.txt (batch N, about 134 kanji)
For each 상용한자: 훈음, 음, 일본어 뜻 (the meanings Japanese uses, most common first), 부수 (as the card shows it),
구성(카드) and 구성(쓰는 꼴) (the same line as a fix file writes it), 유래(카드) and 유래(쓰는 꼴), 외우기, and
`자동 점검(참고)`: heuristic hints from a script (a part the 외우기 names that the 구성/유래 do not show, a 구성 unit
the 외우기 leaves out, a part called by another word than the 구성 note, an arrow meaning that is not one of the
kanji's meanings, a long line). Hints are places to look, not verdicts; lines without hints can still be bad.

## The four links — check every kanji against all four
1. **Same parts.** The 외우기 is built from the 구성 units — all of them where it fits in the length, at least the
   radical's part and every part that carries meaning — and from nothing the 구성 does not show. Do not bring in
   a piece that is not a 구성 unit (属: the 구성 is 尸 + 禹, so no 虫 or 冂 in the 외우기). Exception: a unit that
   has no picturable name (a bare shape such as 𠔉 with no note, or a stroke group) may be named by its visible
   pieces, if those pieces really are visible in the Japanese print shape. A kanji with no 구성 line (a single
   picture: 木, 巾) is pictured from the 유래's picture and its visible strokes.
2. **Same names.** A part has one name across the card. Call it by the word of its 구성 note (or the 유래, or the
   부수 row's meaning in parentheses): if the 구성 says `敬(조심하다 · 뜻·소리)`, the 외우기 says 조심(敬), not 공경(敬);
   if the 구성 says `月(몸 · 뜻)`, not 달(月). A sound part whose note is its 훈음 (`爰(당길 원 · 소리)`) is called by
   that 훈 (당기다). Exception: when the 구성 note only says where a shape came from (`厶(黃을 줄인 꼴)`,
   `卯(丣가 변한 꼴)`, `血(해 日이 변한 꼴)`), the 외우기 may call the visible shape by its everyday meaning (나(厶),
   토끼(卯), 피(血)) — that is what the learner sees — as long as it does not contradict the 유래 (rule 3).
   The same holds when the 유래 says a part was originally another character but is now written exactly as a
   known one (話·活's 舌, 読·続's 売, 姉·肺's 市): call it by that character (혀(舌), 팔다(売), 시장(市)) and let the
   구성 note say so (`지금은 혀 舌 꼴`); do not split a known character into its strokes (added after the outside
   review, link/README.md).
   A sound part whose 훈 cannot be pictured (그 其, 일찍 曽, 또 且, 없을 莫, 놈 者, 말미암을 由, 어조사 也) is called,
   in the 구성 note and the 외우기 alike, by what it first drew when another card's 유래 or a dictionary says so
   (其 → 곡식 키, 曽 → 시루, 且 → 도마·위패, 莫 → 해가 풀숲에 짐), else by another 훈 the data gives (者 사람 자, 由
   지날 유), else by its visible shape (也 구불구불한 꼴) — never by a meaning made up for the picture.
3. **Same story.** When the 유래 is something you can picture with the visible parts (most 그림, 기호, 뜻+뜻, and
   뜻+소리 lines whose sound part "also gives meaning"), the 외우기 **retells that true story as a vivid scene** in
   fresh words (not a copy of the 유래 sentence): 取 유래 "싸움에서 벤 적의 귀를 손에 쥠" → 외우기 "벤 적의 귀(耳)를
   손(又)에 꽉 쥐어 → 가지다". Then the two rows reinforce each other. Only when the true story cannot be pictured
   from the visible parts (sound loans, shapes changed beyond recognition, unknown origins, a sound part that gives
   no meaning) do you invent a scene — and even then it must **not contradict** the 유래: if the 유래 says 埶 is "나무를
   심는 모양", the 외우기 must not call 埶 재주. If the 유래 says a part gives the meaning X, the 외우기 does not
   give it a different meaning Y.
4. **Same meaning.** After `→` comes the meaning the learner must remember: the kanji's main meaning as the 훈음
   and 일본어 뜻 give it (one, or two joined by ·), in Japanese use. 尚 → 아직·숭상하다 is fine; a meaning the kanji
   does not have in Japanese is not.

## What makes a 외우기 easy to picture (연상)
- **One concrete scene**: someone does something to something you can see, hear or touch. Prefer action verbs and
  physical detail (꽉 쥐다, 쏟아지다, 기대다) over abstract nouns. One scene, not two glued together.
- **The meaning follows from the scene.** Covering the part after `→`, a reader should be able to guess it, or at
  least say "아, 그래서 그 뜻이구나" at once. No leaps, no chains (A라서 B이고 C이니 → D), no wordplay that only a
  hanja expert gets, no scene that needs history or culture the learner lacks.
- **Every part has a job in the scene** (who, what, where, with what) — not a list of parts (`A와 B와 C → 뜻`).
- **Looking at the glyph, the parts of the scene are where they are drawn**: 위/아래/안/옆 in the scene should match
  the layout when the scene mentions position (지붕(宀) 아래, 물(氵) 옆).
- **Easy everyday Korean.** No hard Sino-Korean or rare words (녹봉·보루·시렁·점괘·제후·천간·법식·공훈 …; see
  easy/BRIEF.md for the list and what to say instead). If the meaning itself is a hard word, put the plain word
  first (공(功)을 세움 → 공·공적 rather than 공훈).
- A sound hint is welcome but optional: when the sound part's Korean reading is the kanji's own (義 의 → 議 의), a
  natural echo helps (의(義)로운 말(言) …). Never force it.

## Format of a 외우기
- `장면 → 뜻`, exactly one `→`. **Shorter is better**: the shortest line that keeps the picture is the one the
  learner remembers. Most good lines are 20–35 characters; above 40, cut every word that adds no picture
  (adjectives, a second clause, a repeated meaning). 50 is the hard limit (the app's test).
- Each part as `한국어 이름(모양)`: 물(氵), 손(扌), 꼬리(尸). The parentheses hold only the shape as the 구성 writes it
  (氵 not 水; a unit of several pieces as (木木) or (亠·二)). No Hangul inside the parentheses.
- Terse note style like the other lines: endings such as -는, -니, -어, -ㄴ, or a noun before `→`; no 해요체/합니다체.
- Particles after the parentheses agree with the Korean word before them (물(氵)이, 손(扌)으로); merge fixes
  most of them, but write them right.

## Examples from this data
- 警 bad: `공경(敬)하는 말(言)로 주의를 주는 → 경계하다` — the 구성 calls 敬 조심하다 (rule 2).
  good: `조심하라(敬)고 말(言)로 일깨우는 → 경계하다`.
- 勢 bad: `재주(埶)에 힘(力)까지 더하니 → 기세·세력` — the 유래 says 埶 is a person planting a tree (rule 3).
  good: `나무를 심는(埶) 손에 힘(力)이 실려 쑥쑥 → 기세`.
- 属 bad: `몸(尸)에 벌레(虫)가 울타리(冂)째 딸려 붙은 → 속하다` — 虫, 冂 are not 구성 units; 구성 calls 尸 꼬리 (rules 1, 2).
  good: `꼬리(尸)에 벌레(禹)가 줄줄이 달라붙은 → 붙다·속하다`.
- 芸 bad: `풀(艹)을 이르는(云) 대로 잘 가꾸는 → 재주` — '이르다' has no part in the 유래 (藝 = planting) and no picture.
  good: `풀(艹)을 구름(云)처럼 탐스럽게 키우는 솜씨 → 재주`.
- 衆 acceptable shape name: `해 아래 피땀(血) 흘려 일하는 사람들(乑) → 무리` — 血 is the visible shape, and the
  scene is the 유래's (people working under the sun).
- Keep: 柳 `토끼(卯) 귀처럼 늘어진 가지의 나무(木) → 버들` (vivid; 卯 is named by its visible shape, rule 2's exception),
  判 `칼(刂)로 반(半)을 갈라 시비를 가리니 → 판단`, 城 `흙(土)을 쌓아 이룬(成) → 성`.

## 유래 and 구성: only to make the rows agree
- **유래**: change only to name a part the way the 구성 does, or to split a sentence a learner cannot follow.
  Keep the type prefix exactly (상형, 지사, 회의, 형성, 회의 겸 형성, 가차, 국자, 생략형, 불명), every fact, every
  katakana reading, every hedge (의견이 갈림, 확실하지 않음, ~라고 봄, 옛 사전은 ~라 풂, ~라는 의견도 있음). No new
  facts from memory. At most 120 characters. No technical words (신자체, 정자, 說文, 갑골, 금문, 회의, 형성 …).
- **구성**: change only the notes (at most 16 characters), to use the same word as the 유래 and 외우기. Shapes, roles,
  readings, the leading `!` and any `~` stay exactly as given (write it like 구성(쓰는 꼴), no `^`).
- **부수**, and anything else you believe is wrong in fact (a radical name or position, a 유래 claim, a role in the
  구성): do not change it; write it in **flags-N.tsv** with your evidence. The lead decides.

## Output
**link-N.tsv** (create it; only the kanji you change; no header; five tab-separated columns):
`한자<TAB>새 구성 or empty<TAB>새 유래 or empty<TAB>새 외우기 or empty<TAB>이유`
- 이유: short Korean, which link failed or what is better (예: `구성 이름과 맞춤(조심)`, `유래 장면으로`, `구성에
  없는 虫 뺌`, `장면이 안 그려짐`).
- Empty column = keep the current line.

**flags-N.tsv** (only if you have something; three columns): `한자<TAB>부수|구성|유래<TAB>문제와 근거(한국어)`.

## Procedure
1. Read all of in-N.txt first, so names you choose for a part in one kanji can match the same part elsewhere.
2. For each kanji, test the current 외우기 against the four links and the picture test. If it passes, leave it.
   If not, write a better line. Then check the 유래 and 구성 against each other once more.
3. Write link-N.tsv in chunks of at most 60 lines per Write/Edit (or part files joined with cat, then deleted).
4. Run `python3 -I /home/user/test/tools/origin/kosei/link/check.py N` and fix every "고칠 것" until it prints OK.
   Read every "참고" line for the kanji you changed: each is usually worth one more look.
5. Re-read your file once as the learner: glyph → 구성 → 유래 → 외우기. Does the 외우기 read as the natural last
   step? Would you remember it tomorrow?
6. Reply in Korean, briefly: how many 외우기 / 유래 / 구성 lines you changed, the most common problems you fixed,
   how many flags, and up to 5 kanji where a good picture was hardest. Do not paste the file.
