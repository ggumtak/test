# Review of the Korean meanings (batch N)

## Context

The app 한자 바로보기 shows a Korean learner of Japanese each reading of a kanji with one or two common words that
use it, and a short Korean meaning after each word:

    음  セイ(세이)   学生(がくせい) 학생 · 先生(せんせい) 선생님
    훈  い(きる)(이키루) 살다 · う(まれる)(우마레루) 태어나다 · なま(나마) 날것

Another writer wrote the meanings of batch N following `tools/usage/BRIEF.md`. You are the independent reviewer:
the learner trusts these as a dictionary, so one wrong meaning teaches a word wrong. Read `BRIEF.md` first (its
rules are the standard), then check **every line** — do not sample.

## Your files

- `tools/usage/in-N.tsv`: word, reading, English glosses (a guide to the sense), JLPT level, where it is used.
- `tools/usage/out-N.tsv`: word, reading, the Korean meaning to check.
- You create `tools/usage/fix-N.tsv`: only the lines you change, no header:
  `word<TAB>reading<TAB>corrected meaning<TAB>why (a few words, Korean or English)`

## What to check, line by line

1. **Right word, right reading.** Is it the meaning of this word with this reading (開く あく 열리다 vs ひらく 열다;
   汚れる よごれる vs けがれる; 上 かみ vs うえ; 分 ふん vs ぶ vs ぶん)? Is it the common sense first, not a rare one?
2. **Transitive / intransitive.** 開ける 열다 · 開く 열리다, 始める 시작하다 · 始まる 시작되다, 壊す 부수다 · 壊れる 부서지다,
   下げる 내리다 · 下がる 내려가다. A swapped or blurred pair is the most harmful error here: fix every one.
3. **False friends.** A Sino-Korean transliteration that is not a Korean word or means something else
   (勉強 면강, 大丈夫 대장부, 愛人 애인, 工夫 공부, 丈夫 장부) must be the real meaning.
4. **Form.** Verbs and adjectives end in 다; nouns are nouns; at most two senses joined by `, `; about 14 characters
   at most; a parenthesised hint only when the bare word misleads, and short. Prefixes, suffixes and counters
   say so. Only Hangul, spaces, `,` `(` `)` `~` `·` and digits.
5. **Natural Korean.** The word a Korean dictionary would use, not a literal rendering of the English gloss.

Change a line only when it is wrong, misleading, or clearly worse than a better short meaning; do not rewrite
correct lines for taste. When you are not sure a line is wrong, leave it and mention it in your reply.

## Procedure

1. Read `BRIEF.md`, then `in-N.tsv` and `out-N.tsv` side by side, every line.
2. Write `fix-N.tsv` (an empty file if nothing needs changing).
3. Run `python3 tools/usage/check.py N` from the repository root and fix what it reports until it prints `ok`.
4. Reply with: how many lines you changed, the three most important corrections, and any lines you doubted but
   left. Do not edit `out-N.tsv` or any other file, and do not commit.
