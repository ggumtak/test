#!/usr/bin/env python3
"""Usage: make_request.py — writes 외우기_다듬기_요청.md (the request for an outside writer) and requested.txt
(the kanji asked for) in this folder, from the triage (grade-N.tsv) and the card's current rows.

The request is self-contained: who reads the lines, the rules a line must keep, what makes one good, the answer
format check_return.py reads, and one table row per flagged kanji with its 구성, 유래, current 외우기 and why it
was flagged. The clearly weak lines (severity 2) come first."""
import glob
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common import HERE, load, shown  # noqa: E402

CODES = {"W1": "장면이 안 그려짐", "W2": "장면에서 뜻이 안 나옴", "W3": "끼워 넣은 부품", "W4": "뜻을 그대로 말함",
         "W5": "어색한 문장", "W6": "유래와 따로 놂", "W7": "빠진 부품"}

HEAD = """# 한자 '외우기' 한 줄 다듬기 요청

## 부탁하는 일

일본어를 배우는 한국인을 위한 한자 학습 앱의 **'외우기' 한 줄**(한자를 부품으로 쪼개 만든 기억용 이야기)을 더 잘 써 주세요.
상용한자 2,136자의 외우기를 학습자 눈으로 모두 다시 읽어, **더 나은 줄이 가능해 보이는 {n}자**만 골랐습니다(뚜렷이 약한 줄 {n2}자,
쓸 만하지만 더 좋아질 수 있는 줄 {n1}자). 아래 표의 각 줄에 무엇이 약한지 적어 두었습니다.

지금 줄보다 분명히 낫게 쓸 수 있을 때만 새로 써 주세요. 지금 줄이 더 낫다고 판단되면 그 한자는 답에서 빼면 됩니다.

## 카드가 보여 주는 것

카드는 한자마다 다음 줄을 위에서 아래로 보여 줍니다.

| 줄 | 예 (海 바다 해) | 뜻 |
|---|---|---|
| 구성 | `氵(물 · 뜻) + 毎(매양 매 · 소리 マイ)` | 글자를 이루는 부품, 부품마다 이름과 역할(뜻·소리·모양) |
| 유래 | `뜻+소리: 모든 강물이 모여드는 큰 물.` | 글자가 실제로 만들어진 내력(사실) |
| 외우기 | `물(氵)이 매번(毎) 밀려오는 곳 → 바다` | 부품으로 만든 기억용 이야기 한 줄. **이번에 다듬을 줄** |

외우기는 학습자가 한자를 다시 봤을 때 뜻을 떠올리게 하는 단서입니다. 부품을 보면 **장면 하나**가 떠오르고, 그 장면에서
**뜻이 저절로 나와야** 합니다. 진짜 어원일 필요는 없습니다(유래 줄이 따로 있습니다). 소리 부품을 그 글자의 뜻으로 써서
이야기를 지어내는 것(寺 → '절')이 이 방식의 기본입니다.

## 좋은 외우기

1. **한눈에 그려진다.** 누가 무엇을 하는지 보이는 구체적인 장면 하나. 추상어(도리, 이치, 정성)로 풀이하지 않는다.
2. **뜻이 장면에서 나온다.** 뜻을 가리고 장면만 읽어도 뜻이 짐작되거나, 뜻을 보면 "아, 그래서"가 된다.
3. **모든 부품이 장면에서 일을 한다.** 부품 이름을 지워도 이야기가 그대로라면 그 부품은 끼워 넣은 것이다.
4. **뜻을 미리 말하지 않는다.** `미움이 쌓이는 → 미워하다`처럼 뜻 낱말을 장면 안에 그대로 넣으면 설명이 되지 않는다.
   (새가 나무에 모여드는 장면 → '모이다'처럼, 뜻이 일어나는 모습 자체를 그리는 것은 좋다.)
5. **기억에 남는다.** 동작·소리·감정·과장·의외성이 있으면 좋다. 재미있되 억지스럽지 않게.
6. **짧고 자연스러운 한국어.** 한 호흡에 읽히는 일상어.

## 반드시 지킬 규칙 (어기면 그 줄은 쓸 수 없습니다)

1. 형식은 `장면 → 뜻` 한 줄. **`→` 뒤의 뜻은 지금 줄의 것을 한 글자도 바꾸지 않는다.**
2. 부품은 `이름(모양)`으로 쓴다: `물(氵)`, `절(寺)`. **한자는 괄호 안에만** 쓴다.
3. **구성 줄에 있는 부품만, 구성 줄이 부르는 이름으로** 쓴다. 활용은 자유(치다 → 쳐서, 설 립 → 서서, 흰 백 → 흰).
   구성의 이름이 모양의 출처만 말할 때(`亞를 줄인 꼴`, `~의 윗부분`)는 그 모양에서 떠오르는 구체적인 말로 불러도 된다.
   지금 줄이 쓰고 있는 이름은 그대로 써도 된다.
4. **구성 줄의 부품을 모두** 이야기에 넣는다. 구성 줄이 없는 글자(`(부품 하나)`)는 지금 줄처럼 눈에 보이는 조각을 써도 된다.
5. **유래와 어긋나지 않는다.** 유래가 "X와 다른 글자"라고 한 부품을 X의 뜻으로 부르지 않는다. 유래가 그림으로 그려지는
   내력이면(손으로 귀를 잡은 모양 등) 그 그림을 장면으로 살리는 쪽이 좋다.
6. 길이는 띄어쓰기와 괄호를 포함해 **45자 이내**(넘어도 50자까지만).
7. 어려운 한자어·옛말을 쓰지 않는다. 사실처럼 어원을 주장하지 않는다("본래 ~에서 온 글자" 같은 말 금지).
8. 구성·유래 자체가 틀렸다고 생각되면 고치지 말고, 답의 셋째 칸에 `[메모] …`로 적어 주세요.

## 예시 (형식과 방향을 보이기 위한 것이며, 더 좋은 안이 있으면 이 글자들도 다시 써 주세요)

| 한자 | 구성 | 지금 | 약점 | 고친 예 |
|---|---|---|---|---|
| 憎 미워할 증 | 忄(마음 · 뜻) + 曽(시루 · 소리) | 마음(忄)속에 미움이 시루(曽)처럼 켜켜이 쌓이는 → 미워하다 | 뜻('미움')을 장면에서 그대로 말함 | 마음(忄)이 불 위의 시루(曽)처럼 부글부글 끓는 → 미워하다 |
| 涼 서늘할 량 | 氵(물 · 뜻) + 京(서울 경 · 소리) | 서울(京) 강물(氵)에 부는 바람 → 서늘하다 | '서울'을 지워도 장면이 같음 | 찜통 서울(京)을 빠져나와 계곡 물(氵)에 발을 담그니 → 서늘하다 |
| 誌 기록할 지 | 言(말 · 뜻) + 志(뜻 지 · 뜻·소리) | 말(言)과 뜻(志)을 적어 두는 → 기록·잡지 | 추상어 나열, 장면 없음 | 스승의 말(言)을 품은 뜻(志)과 함께 공책에 꾹꾹 눌러 쓰는 → 기록·잡지 |

## 답의 형식

아래처럼 **코드 블록 하나**에, 한 줄에 한 자씩, 칸은 ` | `로 나눠 주세요. 고치지 않을 한자는 넣지 않습니다.

```
한자 | 새 외우기 | 무엇을 어떻게 고쳤는지 한마디
憎 | 마음(忄)이 불 위의 시루(曽)처럼 부글부글 끓는 → 미워하다 | 뜻 낱말을 빼고 끓는 장면으로
```

한 번에 다 쓰기 어려우면 표의 순서대로 50자쯤씩 나눠 답해도 됩니다(답마다 같은 형식). 보내기 전에 줄마다 확인해 주세요:
→ 뒤가 그대로인가, 한자가 괄호 안에만 있는가, 구성의 부품이 모두 들어갔는가, 45자 이내인가, 뜻을 가리고 읽어도 장면이 보이는가.

## 다듬을 줄 ({n}자)

'약점'은 선별한 사람이 적은 진단입니다. 참고만 하고, 다른 약점이 보이면 그것도 고쳐 주세요.

"""


def main():
    rows = load()
    flagged = []
    for path in sorted(glob.glob(os.path.join(HERE, "grade-*.tsv")), key=lambda p: int(p.split("-")[-1][:-4])):
        with open(path, encoding="utf-8") as f:
            for line in f:
                c = line.rstrip("\n").split("\t")
                if len(c) == 4 and c[0] in rows:
                    flagged.append(c)
    order = {ch: i for i, ch in enumerate(rows)}
    flagged.sort(key=lambda c: (-int(c[2]), order[c[0]]))
    n2 = sum(1 for c in flagged if c[2] == "2")
    out = [HEAD.format(n=len(flagged), n2=n2, n1=len(flagged) - n2)]
    for title, sev in (("뚜렷이 약한 줄 (%d자)" % n2, "2"), ("더 좋아질 수 있는 줄 (%d자)" % (len(flagged) - n2), "1")):
        out.append("### %s\n\n| 한자 | 훈음 | 구성 | 유래 | 지금 외우기 | 약점 |\n|---|---|---|---|---|---|\n" % title)
        for ch, codes, s, why in flagged:
            if s != sev:
                continue
            r = rows[ch]
            labels = "·".join(CODES[x.strip()] for x in codes.split(","))
            cell = lambda t: t.replace("|", "/")
            out.append("| %s | %s | %s | %s | %s | %s: %s |\n" % (ch, r["hun"], cell(shown(r["spec"])), cell(r["origin"]),
                                                             cell(r["memo"]), labels, cell(why)))
        out.append("\n")
    with open(os.path.join(HERE, "외우기_다듬기_요청.md"), "w", encoding="utf-8", newline="\n") as f:
        f.write("".join(out))
    with open(os.path.join(HERE, "requested.txt"), "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(c[0] for c in flagged) + "\n")
    print(len(flagged), "kanji requested (%d clearly weak)" % n2)


if __name__ == "__main__":
    main()
