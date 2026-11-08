# Brief: memory stories (외우기) a learner understands at once, on the same story as 구성 and 유래

## Who reads it and what they said
A Korean adult learning **Japanese** reads a kanji card on their phone: 부수, 구성 (the parts, e.g.
`貝(재물 · 뜻) + 占(점칠 점 · 소리 セン)`), 유래 (how the character was really formed), 외우기 (a one-line memory
story). Looking at 貼 they said: "`돈(貝) 위에 점(占)찍듯 딱지를 → 붙이다` — what does this mean? I can't understand
it however long I look. And 유래 and 외우기 are so different that the more I read, the less I understand."

So a 외우기 line must pass two tests:
1. **It makes sense on its own.** Someone who does not know the kanji reads only this line and can say *why* the
   scene means the meaning after the arrow. One scene, each part doing something clear in it, and the meaning
   following from that scene without a jump. `점찍듯 딱지를 → 붙이다` fails: why would dotting lead to sticking?
2. **It tells the same story as 구성 and 유래.** A part that gives the meaning (role 뜻 / 뜻·소리, or 모양) is
   called by the same idea as its 구성 note (貝 is 재물/돈; 艹 in 若 is "머리를 다듬는 두 손", not 풀). When the real
   story in the 유래 can be told as an easy scene, tell that story — that is the best 외우기, because 구성, 유래
   and 외우기 then say one thing.

## How to write one
- Start from the 유래. If its story is concrete (사람이 나무에 기대어 쉼, 물이 매번 밀려오는 곳), say it as a short
  vivid scene with each part in parentheses: `사람(亻)이 나무(木)에 기대어 → 쉬다`.
- A **sound part** (role 소리) only gives the sound, so the scene may use it by its everyday meaning or by another
  meaning the character really has (占 also means 차지하다, as in 占める), or by a pun on its Korean reading, as long
  as the scene still makes sense. If the 유래 says it adds meaning too (뜻도 보탬), use that meaning.
- When the meaning was borrowed (빌려 씀) or the origin is unclear (의견이 갈림 / 확실하지 않음), you may build the
  scene from the visible parts — but it must still pass test 1 and must not give a meaning part a different idea
  from its 구성 note. A short bridge from the 유래's picture to the meaning is good: `두 손(艹)으로 머리를 다듬고
  앉은 사람(右), 꾸미기 좋아하는 → 젊다`.
- Use only shapes the learner sees in the Japanese form (the 구성 pieces, or parts drawn in the kanji). No Chinese
  simplified shapes. Show each shape used in parentheses right after its Korean word.
- Everyday Korean only (no 녹봉, 저당, 시렁, 점괘 … — see ../easy/BRIEF.md for the list and replacements).
- At most 45 characters, one line, ending with ` → 뜻` (spaces around the arrow, once). The meaning after the arrow
  is the card's main meaning (the first 훈음's meaning; add a second with · if the card's 훈음 has two important ones).
- If the current line already passes both tests, leave it alone.

## Examples
```
貼   bad:  돈(貝) 위에 점(占)찍듯 딱지를 → 붙이다           (no link from 점 to 붙이다)
     good: 재물(貝)에 '내가 차지(占)했다'는 표를 → 붙이다      (占 = 차지하다 is a real meaning; one scene)
若   bad:  풀(艹)을 오른손(右)으로 쑥쑥 뽑는 기운 → 젊다         (구성 says 艹 = 두 손, 右 = 꿇어앉은 사람)
     good: 두 손(艹)으로 머리 다듬는 앉은 사람(右), 한창 꾸밀 → 젊은 때
海   good (keep): 물(氵)이 매번(毎) 밀려오는 곳 → 바다
休   good (keep): 사람(亻)이 나무(木)에 기대어 → 쉬다
書   good (keep): 붓(聿)을 들고 말하는(曰) 것을 적어 → 쓰다    (曰 is only the sound part; the scene makes sense)
```

## The 유래 line
While reading, also fix a 유래 line that still has a word the learner would not know (저당 잡히다 → 물건을 맡기고
돈을 빌리다), following ../easy/BRIEF.md: same type prefix, every fact, katakana reading and hedge kept, at most 120
characters, never adding history. Leave the 유래 alone otherwise.

## Input and output
- Input: in-N.txt (훈음, 부수, 구성 as shown and as written, 유래, 외우기 for each kanji of batch N) and, when it
  exists, r-N.tsv (a blind reader's verdicts on the current 외우기 lines: 한자, 판정, 이유).
- Output: story-N.tsv, only the kanji you change, no header:
  `한자<TAB>새 외우기 or empty<TAB>새 유래 or empty<TAB>이유(짧은 한국어)`
- Check: `python3 -I /home/user/test/tools/origin/kosei/story/check.py N` until it prints OK; read its 참고 lines.
