# Korean meanings for the example words (batch N)

## Who reads it and why

A Korean speaker learning Japanese looks up a kanji in the app 한자 바로보기. The card now shows each reading of
the kanji with one or two common words that use it, so the reading is learned through real words:

    음  セイ(세이)   学生(がくせい) 학생 · 先生(せんせい) 선생님
    훈  い(きる)(이키루) 살다 · う(まれる)(우마레루) 태어나다 · なま(나마) 날것

You write the short Korean meaning that stands after each word (학생, 선생님, 살다). The learner trusts it as a
dictionary: a wrong or vague meaning teaches the word wrong. Accuracy first, then brevity.

## Your files

- Input: `tools/usage/in-N.tsv` (tab separated; the first line is a header starting with `#`)
  1. the word as written
  2. its reading (hiragana) — **the meaning must be the one for this reading**
  3. English glosses from JMdict; when the JLPT word list has its own definition it comes first. A guide to the
     sense, not a text to translate word for word.
  4. JLPT level (N5 easiest) or empty
  5. which kanji and reading the word is the example for (`生 い.きる`): no need to use it, it only shows why the
     word is here.
- Output: `tools/usage/out-N.tsv` — create it. One line per input line, **same order, every line, no header**:
  `word<TAB>reading<TAB>Korean meaning` (columns 1 and 2 copied exactly).

## Rules for the meaning

1. What a good Japanese–Korean learner's dictionary gives first for this word with this reading. The most common
   sense; a second sense only when the word is really used both ways, joined by `, ` (`電気` → `전기, 전등`). Never
   more than two.
2. Short: aim for 2–8 Hangul characters, at most about 14. No sentences, no explanations.
3. Dictionary form, as Korean dictionaries write it: verbs and adjectives end in `다` (`食べる` 먹다, `高い` 높다, 비싸다,
   `静か` 조용하다, `明らか` 분명하다); nouns as nouns (`勉強` 공부); adverbs as adverbs (`直ちに` 즉시).
4. Keep verb pairs apart — this is what the learner most needs from these lines:
   - transitive / intransitive: `開ける` 열다 · `開く(あく)` 열리다; `始める` 시작하다 · `始まる` 시작되다; `壊す` 부수다 ·
     `壊れる` 부서지다; `下げる` 내리다 · `下がる` 내려가다.
   - one spelling, two readings: `開く(ひらく)` 열다, 펴다 vs `開く(あく)` 열리다; `汚れる(よごれる)` 더러워지다 vs
     `汚れる(けがれる)` 더럽혀지다, 부정 타다; `行く(いく)` and `行く(ゆく)` are both 가다.
5. Sino-Korean word when Korean really uses it with the same meaning (`学生` 학생, `経済` 경제, `説明` 설명). When the
   Korean reading of the characters is not a Korean word, or means something else, write the real meaning
   (`勉強` 공부, `大丈夫` 괜찮다, `丈夫` 튼튼하다, `愛人` 정부(불륜 상대), `工夫` 궁리, `放送` 방송, `汽車` 기차,
   `手紙` 편지, `大家` 집주인). Never just transliterate the kanji into Korean readings.
6. When the bare Korean word would be ambiguous or misleading, add a short hint in parentheses, at most about 6
   characters: `生ける` 꽂다(꽃을), `下(もと)` 아래(영향·지도), `上(かみ)` 위쪽(상류), `代(よ)` 시대, 세대, `分(ふん)` 분(시간),
   `本(もと)` 근본, `金(かな)` 쇠(접두어). For a prefix, suffix or counter say so the same way: `大(おお)` 큰(접두어),
   `小(お)` 작은(접두어), `高(たか)` 금액, 양(접미어) — check the English gloss for what it really is.
7. Formal, old-fashioned or rare words still get their plain meaning; add `(문어)` or `(격식)` only when the word
   would mislead otherwise. Coarse words: `食う` 먹다(거친 말), `食らう` 처먹다, 얻어맞다.
8. Allowed characters: Hangul, spaces, `,` `(` `)` `~` `·` and digits. **Never** `;` `|` `:` or a tab, no kanji,
   kana or Latin letters.
9. Do not guess. If you are not sure of a word's meaning for this reading, think about its kanji and the English
   glosses again; if still unsure, give the safest plain meaning from the glosses.

## Examples

| word | reading | English | meaning |
|---|---|---|---|
| 生きる | いきる | to live; to exist | 살다 |
| 生地 | きじ | cloth; material; texture | 옷감, 반죽 |
| 上着 | うわぎ | coat; tunic; jacket | 겉옷 |
| 下宿 | げしゅく | boarding; lodging | 하숙 |
| 明くる | あくる | next; following | 다음(날·해) |
| 風呂 | ふろ | bath | 목욕, 욕조 |
| 一生懸命 | いっしょうけんめい | very hard; with utmost effort | 열심히 |
| 経つ | たつ | to pass; to lapse | (시간이) 지나다 |
| 手伝う | てつだう | to help; to assist | 돕다 |
| 乞食 | こじき | beggar | 거지 |
| 行脚 | あんぎゃ | pilgrimage; walking tour | 도보 여행, 순례 |
| 直ちに | ただちに | at once; immediately | 즉시 |

## Procedure

1. Read this brief, then all of `in-N.tsv`.
2. Write `out-N.tsv` in order. Work through every line yourself — do not write a script that generates meanings,
   and do not leave any line out or empty.
3. Run `python3 tools/usage/check.py N` from the repository root and fix what it reports until it prints `ok`.
4. Reply with: the number of lines written, and up to 10 words you were least sure about (word, reading, your
   meaning, why unsure). Do not edit any other file and do not commit.
