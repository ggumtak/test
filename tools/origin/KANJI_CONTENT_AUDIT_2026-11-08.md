# 🔎 상용한자 부수·구성·유래·외우기 내용 검토 — Claude 수정 인계

- 검토일: 2026-11-08 (한국시간)
- 저장소: `ggumtak/test`
- 기준 브랜치: `ccr-2183b9b2-mwd49r`
- 기준 커밋: `647ea121fa10269b9050ae854422fcb0276f41b2`
- 작업 범위: 한자 내용의 정확성, 현재 일본 자형, 부수 명칭, 구성과 유래의 연결, 외우기의 모양·뜻 대응. 일반 앱 기능·성능·디자인은 검토 범위 밖.
- 이 문서는 **검토 결과와 수정 지시서**다. 원본 앱 데이터와 기존 지침은 변경하지 않았다.

## 📌 먼저 알아둘 결과

실제 유래 오류와, 현재 자형을 제대로 나타내지 못하는 구성 문제가 함께 남아 있다. 구성은 퍼즐 조각이고 유래는 조립 설명서다. 설명서끼리 말을 맞추기만 하고 조각을 빠뜨리면, 읽기는 매끄러워져도 정확해지지 않는다.

| 확인 대상 | 결과 | 해석 |
|---|---|---|
| 상용한자 카드 | 2,136자 | 모든 유래·외우기를 읽고 구성 표기를 점검했다. |
| 구성 데이터 | 2,011자 있음 / 125자 비어 있음 | 비어 있다는 사실 자체를 오류로 세지 않았다. 단순 글자와 KanjiVG 분할이 없는 글자가 포함된다. |
| 검토용 MD와 실제 카드의 부수 이름 차이 | 579자 / 55개 매핑 | **579자의 부수 분류가 틀렸다는 뜻이 아니다.** 내보내기가 위치별 명칭을 고르지 않아 생기는 표시 차이다. 부록 B에 전부 수록했다. |
| KanjiVG의 이름 붙은 구성 묶음 밖에 남는 획 | 101자 | 이 중 4자는 현재 `!` 예외 구성으로 이미 보완되어 있다. 남은 97자를 부록 A에 전부 적었다. **97개의 자원 오류 확정 판정이 아닌, 완전한 분해처럼 보이는 구성의 누락 후보**다. |
| 반복되는 어색한 유래 문형 | 60자 | `소리는 X도 내고…`, `소리는 X로…` 문형. 사실을 바꾸지 않는 문장 정리가 필요하다. 부록 C에 전부 수록했다. |
| 부수 기초 데이터 | 214개 번호 존재 | 번호 범위·참조 연결을 검사했다. 2,136자의 사전 분류를 별도 최신 사전으로 전부 재판정한 것은 아니다. |

숫자는 서로 겹치므로 합산해 ‘총 오류 수’로 쓰지 말 것. 외부 사전 대조는 의심되는 설명과 자형을 중심으로 했으며, **2,136자 모두의 학설을 외부 문헌으로 완전 검증했다는 뜻은 아니다.** 아래에 없는 설명도 학계의 유일한 정설로 인증한 것이 아니다.

## 📂 검토 대상과 재현 기준

저장소에서 확인한 MD 6개는 실제 2,136자 표가 아니라 다음 작성·검토 지침이다.

1. `tools/origin/BRIEF.md`
2. `tools/origin/REVIEW.md`
3. `tools/origin/review2/REVIEW2.md`
4. `tools/origin/plain/PLAIN.md`
5. `tools/origin/kosei/BRIEF.md`
6. `tools/origin/kosei/REVIEW.md`

별도의 `tools/origin/kosei/export_md.py`가 사용자가 말한 제미나이용 **전체 1개 + 분할 5개**를 만든다. 이 기준 커밋에서 직접 재생성하여 읽었다. 생성 MD는 Git 추적 파일이 아니므로, 행 번호와 원문은 실제 배포 데이터 `app/src/main/assets/origin.tsv`를 기준으로 연결했다. 여섯 MD 중 ‘전체’와 ‘분할’은 중복이므로 12,816자로 중복 계산하지 않았다.

함께 읽은 자료: `kanji.tsv`의 훈음·뜻, `parts.tsv`의 부수·모양·소리 표시, `radicals.tsv`, 카드의 `Parts.kt` 내용 표시 규칙, 병합·내보내기 과정. 자형 검사는 KanjiVG `610091ce618cf757ed1357a8954cd65283e015d6`의 SVG를 대조했다. 부록 A의 101자 모두 SVG 획수와 앱의 그림 획수가 같음을 확인했지만, 모든 획의 좌표·모양까지 픽셀 비교한 것은 아니다.

## 🛠️ Claude에게 요청할 작업

이 문서를 기준으로 현재 데이터를 다시 확인하고 수정해 줘. 기준 커밋 이후 변경된 원문은 이 문서의 문장으로 덮어쓰지 말고 현재 값을 먼저 대조해 줘.

1. 아래 **확정도가 높은 오류**부터 고친다. 원문의 항목 하나만 고치지 말고 유래·구성·외우기에서 같은 잘못을 되풀이하는 부분을 함께 고친다.
2. **학설 차이·검증 보류**는 일괄 적용하지 않는다. 확인한 사전의 풀이를 표시하거나 `의견이 갈림`을 남긴다. ‘쉬워 보인다’는 이유로 새 유래를 만들지 않는다.
3. **현재 모양 / 옛 모양 / 암기용 이야기**를 구분한다. 현재 구성에 옛 원형을 쓸 수는 있지만 `원래 ○○`, `○○를 줄인 부분`이라고 명시한다. 현재 글자에 없는 글자를 그대로 썼다고 오해하게 만들지 않는다.
4. 부록 A의 97자는 누락된 획을 확인한다. 완전한 분해를 제공하거나, `전체 모양의 일부`라고 분명히 적는다. 뜻·소리를 맡은 **전체 묶음**을 일부분 하나에 잘못 배정하지 않는다.
5. 부록 B를 기준으로 검토용 MD의 부수 명칭을 카드와 일치시킨다. 기본 명칭도 유효하므로 579자를 부수 사전의 오류라고 기록하지 않는다.
6. 변경한 내용은 병합 원본·최종 보정층에도 반영한다. `origin.tsv`만 직접 바꾸면 `tools/origin/merge.py` 재실행 때 복구될 수 있다. 기존 보정 적용 순서를 확인하고 마지막에 적용되는 내용이 의도한 값인지 확인한다.
7. 수정한 자형은 구성 글자, 그림의 빨강·파랑 부분, 유래를 함께 본다. ‘부수=뜻 부분’이라고 일괄 처리하지 않는다.
8. 병합 후 형식 검사와 기존 관련 검사를 실행하고, MD 전체 1개+분할 5개를 새로 생성한다. 수정한 글자 수와 근거·보류한 학설을 요약한다.

## 🚨 확정도가 높은 내용 수정

아래 수정 문장은 카드에 사용할 수 있는 제안이다. 저장할 때는 현재 데이터와 병합 우선순위를 확인한다. 이 절의 ‘높음’은 구체적인 지적에 대한 확신이며, 글자의 모든 역사에 대한 확신을 뜻하지 않는다.

| ID | 한자·위치 | 항목 / 확신 | 현재 내용 | 문제 | 수정 제안 | 근거 |
|---|---|---|---|---|---|---|
| F01 | [墨](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L416) | 유래·외우기 / 높음 | 검은 흙을 굳힌 먹 / 검은(黒) 흙(土)을 굳혀 만든 → 먹 | 글자 구성의 土를 실제 제조 재료로 옮겨 적었다. 먹을 검은 흙 덩어리로 가르치면 안 된다. | 유래: 회의 겸 형성: 옛 사전은 土와 黑을 합치고 黑이 소리도 낸다고 풂. 글씨에 쓰는 검은 먹. / 외우기: 검은(黒) 먹을 흙(土)처럼 단단한 덩이로 → 먹 | [香港中文大學・墨](https://humanum.arts.cuhk.edu.hk/Lexis/lexi-mf/search.php?word=%E5%A2%A8) / [奈良県・奈良墨](https://www.pref.nara.lg.jp/n101/1400.html) |
| F02 | [審](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L530) | 유래·구성·외우기 / 높음 | 회의: 宀(집)+番(짐승 발자국). 집 안 발자국을 자세히 가려 살핌. 본래 宷(宀+釆). | 집 안의 짐승 발자국을 추적한다는 장면은 확인한 사전의 자원 풀이와 다르다. 釆의 ‘가려 구별함’을 番의 발자국 풀이와 섞어 새 이야기를 만들었다. | 유래: 회의: 본래 宀(덮다)+釆(가려 나누다). 덮인 것을 구별해 자세히 살핀다고 풂. 뒤에 釆이 番 꼴이 됨. / 구성: 宀(덮다 · 뜻) + 番(가려 나누는 釆이 변한 꼴 · 뜻) / 외우기: 집(宀)에 차례(番)로 들어온 것을 → 살피다 | [漢字ペディア・審](https://www.kanjipedia.jp/kanji/0003661400) |
| F03 | [砂](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L1347) | 유래·구성 / 높음 | 회의: 石(돌)+少(적다). 잘게 부서진 돌. 沙(모래)와 같은 글자. | 少를 단순히 ‘적다’라는 뜻 부품으로만 설명한다. 확인한 일본 사전은 石+沙의 생략된 소리 부분으로 설명한다. | 유래: 형성: 뜻은 石(돌), 소리는 沙를 줄인 少. 沙와 같은 뜻으로 쓰던 글자로, 잘게 부서진 모래. / 구성: 石(돌 · 뜻) + 少(沙를 줄인 소리 부분 · 소리) | [漢字ペディア・砂](https://www.kanjipedia.jp/kanji/0002462100) |
| F04 | [隣](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L2025) | 유래·구성 / 높음 | 형성: 원래 글자 鄰은 邑(고을)이 뜻, 粦(リン)이 소리. 이웃 마을. 隣은 阝를 왼쪽으로 옮긴, 모양만 다른 같은 글자. | 邑의 阝를 단순히 왼쪽으로 이동시킨 것으로만 쓰면 현대 부수 阜와 옛 뜻 부분 邑의 차이가 사라진다. | 유래: 형성: 원래 鄰은 邑(고을)이 뜻, 粦(リン)이 소리. 뒤에 邑 대신 왼쪽의 阜(阝)를 쓴 隣도 씀. 이웃 마을. / 구성: 阝(본래 고을 邑, 지금은 阜 · 뜻) + 粦(소리 リン) | [漢字ペディア・隣](https://www.kanjipedia.jp/kanji/0007213100) |
| F05 | [貫](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L1801) | 구성 / 높음 | 毋(꿰다 毌, 毋가 아님 · 뜻·소리 カン) + *貝*(조개 돈 · 뜻) | 표시하는 글자는 毋인데 설명은 ‘毋가 아님’이라고 한다. 유래와 외우기는 이미 毌를 쓰므로 구성의 문자부터 맞춘다. | 毌(꿰다 · 뜻·소리 カン) + 貝(조개 돈 · 뜻) | [원문](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L1801)의 세 필드 직접 대조. KanjiVG의 毋/母 변형 태그를 그대로 정답으로 취급하지 말 것. |
| F06 | [渓](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L1142) | 외우기·구성 / 높음 | 세 줄기(⺍) 물(氵)이 크게(大) 모여 흐르는 → 시내 | 현재 오른쪽은 爫 계열의 네 획과 夫 꼴이다. ⺍(세 점)와 大만으로 설명하면 모양을 잘못 배운다. | 외우기: 손(爫) 잡은 사내(夫)가 물(氵)을 건너는 → 시내 / 구성: 氵(물 · 뜻) + 爫+夫(奚를 줄인 부분 · 소리 ケイ) | [漢字ペディア・渓](https://www.kanjipedia.jp/kanji/0001778900) / [현재 SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/06e13.svg) |
| F07 | [選](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L1914) | 외우기 / 높음 | 뱀(巳) 둘이 함께(共) 나가(辶) 겨룰 상대를 → 뽑다 | 현재 일본 자형과 KanjiVG는 己 둘이다. 巳(뱀) 둘이라고 하면 己·已·巳의 구별을 거꾸로 가르친다. | 몸(己) 둘이 함께(共) 길(辶)에 나설 이를 → 뽑다 | [漢字ペディア・選](https://www.kanjipedia.jp/kanji/0004106300) / [현재 SVG의 己 두 묶음](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/09078.svg) |
| F08 | [声](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L425) | 외우기 / 높음 | 선비(士)가 문(戸)에 대고 외치는 → 소리 | 下部를 戸라는 완전한 글자로 표시할 수 없다. 구성은 비어 있으므로 외우기의 잘못된 모양을 바로잡는 것이 우선이다. | 선비(士)가 아래로 울려 퍼지게 외치는 → 소리 | [현재 7획 SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/058f0.svg) |
| F09 | [縄](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L1503) | 외우기 / 높음 | 실(糸)을 꼬아 맹꽁이(黾)처럼 똬리 튼 → 새끼줄 | 외우기는 현재 일본 자형을 써야 하는데 보이지 않는 黾를 넣었다. 맹꽁이가 똬리를 튼다는 장면도 직관적이지 않다. | 실(糸)을 꼬아 오른쪽 고리 모양으로 → 새끼줄 | [漢字ペディア・縄](https://www.kanjipedia.jp/kanji/0003521700) |
| F10 | [勘](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L236) | 유래 / 높음 | 형성: 뜻은 力(힘), 소리는 甚(심). 따져 맞추어 봄, 곧 헤아림. | 소리 부분의 괄호에 다른 행과 달리 한국음 ‘심’만 들어가 음 표기 체계가 섞인다. 甚의 일본 음독은 ジン이다. | 형성: 뜻은 力(힘), 소리는 甚(ジン). 따져 맞추어 봄, 곧 헤아림. | [漢字ペディア・甚](https://www.kanjipedia.jp/kanji/0003700700) |
| F11 | [堪](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L398) | 유래 / 높음 | 형성: 뜻은 土(흙), 소리는 甚(심). 옛 사전에선 본래 '땅이 솟은 곳'이라 함. 뒤에 견디다. | 같은 甚이 ‘심’으로만 적혀 있다. 해당 글자의 음 カン과 음부 자체의 음 ジン은 다를 수 있음을 공통 안내해야 한다. | 형성: 뜻은 土(흙), 소리는 甚(ジン). 옛 사전에선 본래 '땅이 솟은 곳'이라 함. 뒤에 견디다. | [漢字ペディア・甚](https://www.kanjipedia.jp/kanji/0003700700) |
| F12 | [盲](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L1322) | 유래 / 높음 | 형성: 뜻은 目(눈), 소리는 亡(ボウ)으로, '없다'라는 뜻도 보탬. 눈동자가 없어 보지 못함. | ‘눈동자가 없어 보지 못함’을 모든 시각장애의 사실 설명처럼 적었다. 亡의 뜻을 이용한 풀이를 신체 구조의 단정으로 바꾸지 않는다. | 형성: 뜻은 目(눈), 소리는 亡(ボウ). 亡은 '없다'라는 뜻도 보탠다고 풂. 눈으로 보지 못함. | 원문 자체의 目·亡 풀이를 유지하면서 불필요한 신체 단정을 삭제하는 수정. 새 자원설을 추가하지 않음. |

## 🧩 구성과 유래가 어긋나는 주요 항목

여기서는 **전체 묶음을 일부 획 하나로 부른 것**, **겹치는 부분을 독립 부품 두 개처럼 쓴 것**, **옛 원형을 현재 글자처럼 보이게 쓴 것**을 구분한다. 획수를 단순 합산해서 판단한 목록이 아니다. 현재 구성과 SVG의 그룹·누락 획을 함께 확인했다.

| ID | 한자·위치 | 현재 분해의 핵심 | 문제 | 수정 방향 |
|---|---|---|---|---|
| C01 | [僅](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L136) | 亻 + 艹+三 | 소리 부분 堇의 가운데와 세로 획들이 빠진다. 艹+三은 堇의 전체가 아니다. | 亻(사람 · 뜻) + 堇(소리 キン). 부품의 한국 훈은 여기서 뜻을 더한다는 근거로 쓰지 말 것. |
| C02 | [電](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L2042) | 雨 + 日 | 번개를 나타낸 전체를 日 한 글자로 표시해 마지막 굽은 획을 놓친다. | 雨(비 · 뜻) + 电(번개 申이 변한 꼴 · 뜻·소리 シン). 电은 중국 간체 電으로 가르치는 것이 아니라 닮은 모양을 가리키는 용도. |
| C03 | [官](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L502) | 宀 + 口 | 아래의 위아래로 이어진 전체가 口 하나로 축소된다. 현재 아래 전체와 옛 𠂤를 연결해야 한다. | 宀(집 · 뜻) + 㠯 모양(군대·무리 𠂤가 변한 부분 · 뜻). 드문 글자가 안 보이면 아래 전체를 말로 부르기. |
| C04 | [帥](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L597) | 丿 + 巾 | 왼쪽 전체 𠂤를 丿 한 획으로 표시하면서 그 획이 소리 전체라고 설명한다. | 𠂤(왼쪽 전체 · 소리) + 巾(천 · 뜻). 희귀 글자를 못 쓰면 ‘왼쪽 다섯 획’이라고 적기. |
| C05 | [師](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L598) | 丿+丨 + 巾 | 왼쪽 𠂤 대부분과 帀의 윗획이 빠진다. 설명 괄호에 一을 적어 놓고 도식에는 없다. | 𠂤(왼쪽 전체 · 뜻) + 帀(一+巾, 두르다 · 뜻). 현재 선택한 유래 풀이를 유지하되 전체 범위를 표시. |
| C06 | [散](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L874) | 月 + 攵 | 소리 부분은 유래에서 㪔 전체라고 했으나 구성은 그 일부 攵에만 サン을 부여한다. 위의 네 획도 누락된다. | 月(고기 · 뜻) + 나머지 전체(㪔에서 온 부분 · 소리 サン). 攵 한 글자를 サン으로 읽게 만들지 않기. |
| C07 | [華](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L1637) | 艹 + 丨 | 아래의 꽃·초목 전체를 세로획 하나로 적고 그 한 획에 カ라는 소리를 붙였다. | 艹(풀 · 뜻) + 아래 전체(꽃 핀 초목의 옛 모양 · 뜻·소리 カ). 희귀 원형을 추측해 채우지 않기. |
| C08 | [遣](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L1908) | 中 + 辶 | 안쪽 전체를 中으로 축소하여 아래 부분이 빠진다. 中의 음독이 ケン인 것처럼 읽힐 수 있다. | 안쪽 전체(소리 ケン) + 辶(가다 · 뜻). 구성의 범위부터 다시 지정. |
| C09 | [那](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L1918) | 二 + 阝 | 왼쪽 전체를 가로획 둘로 바꿨다. 설명은 冄의 변형이라면서 실제 왼쪽 외곽 획을 담지 않는다. | 왼쪽 전체(冄에서 변한 부분 · 소리) + 阝(고을 邑 · 뜻). 현재 왼쪽을 二라는 완전한 글자로 가르치지 말기. |
| C10 | [隊](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L2017) | 阝 + 豕 | 위의 八/丷 모양이 빠졌다. 유래의 㒸는 그 부분까지 포함한다. | 阝(언덕 · 뜻) + 丷+豕(합쳐서 㒸 · 소리 スイ). 八/丷은 실제 일본 표시 모양에 맞추기. |
| C11 | [遂](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L1895) | 豕 + 辶 | 소리를 맡는 㒸 전체에서 윗부분이 빠진다. | 丷+豕(합쳐서 㒸 · 소리 スイ) + 辶(가다 · 뜻). ‘위에 八을 얹음’이라는 설명도 실제 보이는 점 방향과 대조. |
| C12 | [斗](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L884) | 丶+丶 | 상형 전체를 설명해야 하는데 점 둘만 표시했다. 자루와 아래 두 획이 사라진다. | 丶+丶+十(자루 달린 되의 전체 모양 · 모양). 가장 안전한 대안은 전체 그림 설명 한 단위로 쓰는 것. |
| C13 | [克](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L153) | 古 + 兄 | 古와 兄은 口를 공유한다. 완전한 글자 둘을 별개로 더하면 같은 口를 두 번 세게 된다. | 古+儿(갑옷·짐을 이고 선 사람으로 봄 · 모양), 또는 十+口+儿. 기존 유래의 ‘의견이 갈림’은 유지. |
| C14 | [党](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L156) | 尚 + 兄 | 口를 공유하는데 중복 범위가 보인다. 주석에 ‘口도 尚’이라고 쓰는 것보다 도식 자체를 맞추는 편이 낫다. | 尚(소리 ショウ) + 儿(黑을 줄인 부분 · 뜻). 尚과 儿이 실제로 담당하는 범위를 대조. |
| C15 | [挿](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L817) | 扌 + 千+十+日 | 千에 들어 있는 十 및 日과 공유하는 세로획을 독립 부품처럼 중복한다. 모양 합성식이 불명확하다. | 扌(손 · 뜻) + 오른쪽 전체(臿에서 변한 부분 · 소리 ソウ). 새 분할은 실제 겹친 획을 확인한 뒤 결정. |
| C16 | [重](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L1947) | 千+里 | 천 리라는 외우기는 가능하지만 완전한 자형 분해식으로 쓰면 공유 가로획을 중복한다. | 千와 里가 한 획을 공유한 모양(사람이 짐을 진 옛 꼴). 기존 원형설과 현재 모양을 구분. |
| C17 | [表](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L1683) | 二+丨 + 衣 | 현재 글자를 만들 때 두 부품이 공유하는 부분을 설명하지 않는다. 털과 옷의 옛 풀이와 현재 자형은 구분할 필요가 있다. | 윗부분 龶 + 아랫부분 𧘇(옷 衣의 일부). 원래 毛+衣 풀이와 연결하는 별도 짧은 설명 유지. |
| C18 | [塁](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L402) | 田+冫 + 土 | 田 하나와 冫 하나만 쓰면 좌우의 점 넷을 설명하지 못한다. 유래도 畾를 田 하나로만 줄였다고 하여 점이 남는 이유가 없다. | 田+좌우 네 점(畾를 줄인 부분 · 소리 ルイ) + 土(흙 · 뜻). 유래에도 ‘田과 주위 점으로 줄임’ 정도로 현재 형태 연결. |
| C19 | [塩](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L410) | 土 + 監 | 監은 실제 오른쪽에 그대로 쓰여 있지 않다. 현재 오른쪽의 𠂉·口·皿을 명시하면 훨씬 쉽게 연결된다. | 土(흙) + 𠂉+口+皿(監을 줄인 부분 · 소리 カン). 형태 연결 제안이며 鹽의 실제 의미 부분 鹵를 없던 사실로 바꾸지 말 것. |
| C20 | [壌](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L422) | 土 + 襄 | ‘줄여 쓴 꼴’이라고 설명하지만 화면의 대표 문자는 줄이기 전 襄이다. 嬢·譲도 같다. | 土(흙 · 뜻) + 㐮(襄을 줄인 부분 · 소리 ジョウ). 嬢·譲은 뜻 부분만 女·言으로 바꿔 함께 정리. |
| C21 | [勧](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L242) | 雚 + 力 | 権·歓·観도 줄이기 전 雚를 대표 모양으로 쓰고 있다. 원형 표시를 허용한 기존 규칙상 즉시 오자는 아니지만 현재 자형 설명으로는 불친절하다. | 현재 줄인 묶음(𠂉·一·隹가 들어 있는 전체, 원래 雚 · 소리 カン) + 力. 権·歓·観도 같은 묶음으로 연결. |
| C22 | [外](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L431) | 夕(뜻·소리) + 卜(뜻) | 유래에서는 夕를 소리로 보는 것은 ‘다른 의견’인데 구성에서는 뜻·소리 역할을 확정했다. | 夕(저녁 · 뜻) + 卜(점 · 뜻), 또는 夕(뜻, 소리로 보는 의견도 있음)처럼 조건을 같이 표시. |
| C23 | [容](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L518) | 宀(뜻) + 谷(뜻·소리) | 유래의 ‘소리로 보는 의견도 있음’을 구성에서 단정으로 강화했다. | 宀(집 · 뜻) + 谷(골짜기 · 뜻, 소리로 보는 의견도 있음). 공간이 부족하면 구성 역할은 뜻으로 맞추고 유래에만 다른 의견 유지. |
| C24 | [彙](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L654) | 彑 + 冖 + 果(田은 胃를 줄인 꼴) | 유래에서는 胃의 생략이 소리라고 했으나 구성은 冖를 일반 훈으로만 설명하고 果 일부도 소리 역할을 표시하지 않는다. 누가 어떤 역할을 맡는지 불명확하다. | 彑(옛 짐승 부분 · 뜻) + 나머지 전체(胃에서 변한 부분 · 소리 イ). 冖·果의 역사적 범위는 확인 전 임의 확정하지 말 것. |
| C25 | [敬](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L875) | 苟(삼가는 사람 茍, 苟와 다름) | 현재처럼 쓰면 표시한 글자가 ‘그 글자가 아니다’라는 역설로 읽힌다. 원형과 현재의 닮은 모양을 분리해야 한다. | 苟 모양(본래 삼가다 茍 · 뜻) + 攵(치다 · 뜻). 苟와 茍가 같은 글자라고 고치지 말 것. |
| C26 | [縄](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L1503) | 糸 + 电 | 오른쪽 전체는 9획인데 电은 독립 글자로 5획이다. KanjiVG가 电의 변형 태그를 붙인 것을 보통 电 전체와 동일하게 표시하면 안 된다. | 糸(실 · 뜻) + 오른쪽 전체(黽을 줄인 모양 · 소리). 电은 단순히 닮은 부분의 참고 모양으로만 쓰기. |

C01~C26의 자형 근거는 각 글자의 [KanjiVG 고정 버전](https://github.com/KanjiVG/kanjivg/tree/610091ce618cf757ed1357a8954cd65283e015d6/kanji) SVG와 기준 커밋의 실제 구성 필드다. C06~C11 등에서 제안한 ‘전체’ 표기는 보이지 않는 부품을 새로 지어내지 않기 위한 대안이다. 뜻·소리 역할을 승인하려면 선택한 유래의 문헌 근거도 함께 유지할 것.

## 📚 학설 차이·근거 보강 항목 — 자동 수정 금지

‘다른 풀이가 있다’와 ‘현재 문장이 틀렸다’는 다르다. 같은 산을 다른 길로 올라갈 수 있듯 사전의 설명 체계도 다를 수 있다. 이 절은 **설의 출처·확실성 표시를 보완**하라는 뜻이다.

| ID | 한자·위치 | 현재 설명 | 판단 | 안전한 제안 | 근거 / 한계 |
|---|---|---|---|---|---|
| S01 | [器](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L358) | 개가 여러 그릇을 지킨다는 설명을 단정 | CUHK는 본래 뜻의 구성이 불명확하며 그릇을 지킨다는 설과 개 짖는 소리로 보는 설 등을 병기한다. | 회의: 옛 사전은 口 넷을 그릇, 犬을 지키는 개로 풂(의견이 갈림). 일본에서는 犬의 점을 빼 大로 씀. | [香港中文大學・器](https://humanum.arts.cuhk.edu.hk/Lexis/lexi-mf/search.php?word=%E5%99%A8) |
| S02 | [染](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L983) | 회의: 물+염료 나무+아홉/여러 번 | 현재도 ‘의견이 갈림’을 표시했으므로 단순 민간설 오류로 몰아붙이지 않는다. 다만 CUHK와 일본 사전의 중심 풀이는 水+杂(朵의 변형)라는 소리 구성이다. | 우선안: 형성: 氵(물)이 뜻, 九+木 꼴의 杂가 소리라고 풂. 비단 등에 물감을 들임. / 기존 아홉 번 담그는 이야기는 외우기에만 남길 수 있음. | [漢字ペディア・染](https://www.kanjipedia.jp/kanji/0004056800) / [香港中文大學・染](https://humanum.arts.cuhk.edu.hk/Lexis/lexi-mf/search.php?word=%E6%9F%93) |
| S03 | [沙](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L1088) | 물이 적어지면 모래가 보인다는 설명을 유래로 단정 | 《說文》의 전통 설명에는 근거가 있다. CUHK의 고문자 해석은 물가의 모래 알갱이들로 본다. 砂와 함께 의미부품/음부품을 하나로 일반화하지 말 것. | 회의: 옛 사전은 氵(물)+少(적다), 물이 줄어 모래가 보인다고 풂. 옛 모양은 물가의 작은 알갱이로 보기도 함. | [香港中文大學・沙](https://humanum.arts.cuhk.edu.hk/Lexis/lexi-mf/search.php?word=%E6%B2%99) |
| S04 | [習](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L1532) | 어린 새가 날마다 연습한다는 설명을 유일한 역사처럼 제시 | 일본 사전은 羽+日의 전통적 풀이를 싣는다. CUHK는 彗를 소리, 日을 뜻으로 보아 볕에 말림에서 빌려 썼다는 설을 싣는다. 양쪽을 섞어 새 이야기를 만들지 말 것. | 회의: 일본 사전은 羽(날개)+日(해, 지금은 白 꼴), 새가 햇볕을 향해 날기 연습함이라 풂(의견이 갈림). | [漢字ペディア・習](https://www.kanjipedia.jp/kanji/0003127100) / [香港中文大學・習](https://humanum.arts.cuhk.edu.hk/Lexis/lexi-mf/search.php?word=%E7%BF%92) |
| S05 | [罷](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L1521) | 유능한 죄인을 놓아 보낸다는 옛 사전 풀이 | 실제로 옛 사전에 있는 설명이다. 오류로 보고 能을 임의로 음부품으로 바꾸지 말 것. CUHK는 그 해석이 본뜻인지 의심하며 그물에 든 곰이 지침이라는 설도 소개한다. | 회의: 옛 사전은 网+能을 유능한 죄인을 풀어 줌으로 풂(의견이 갈림). 지치다·그만두다. | [香港中文大學・罷](https://humanum.arts.cuhk.edu.hk/Lexis/lexi-mf/search.php?word=%E7%BD%B7) |
| S06 | [幼](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L614) | 가는 실+힘이 약하므로 어리다는 설명을 단정 | CUHK는 농기구에 줄을 맨 설과 힘이 작다는 설을 병기하고 본뜻이 미정이라고 한다. 기존 설명을 유일한 사실처럼 강화하지 말 것. | 회의 겸 형성: 幺(작다)+力(힘)으로 아직 힘이 약함이라 풂(의견이 갈림). 어리다. | [香港中文大學・幼（力部件）](https://humanum.arts.cuhk.edu.hk/Lexis/lexi-mf/oraclePiece.php?piece=%E5%8A%9B) |
| S07 | [若](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L1622) | ‘젊다’는 일본에서 붙인 뜻 | 일본의 주요 용법을 알려주는 데는 유용하나 ‘일본에서 처음 생긴 뜻’이라는 역사적 기원까지 확인한 문장이 아니다. 발상국의 단정은 별도 근거가 필요하다. | 현 단계 안전한 수정: 해당 마지막 문장을 ‘일본어에서는 젊다의 뜻으로도 씀’으로 낮춤. 나머지 유래를 임의로 바꾸지 않음. | 확신 낮음(기원 주장). 일본어의 현재 용법과 의미가 처음 생긴 장소는 별개. 별도 문헌을 찾기 전 역사적 창작국을 확정하지 말 것. |

## 💡 학습 연결·읽기 쉬움 개선 — 사실 오류와 구분

외우기는 실제 유래와 달라도 된다. 다만 두 설명을 같은 이름으로 부르면 ‘암기용 이야기’가 역사로 둔갑한다. 별도의 외우기 칸을 유지하고, 다음은 오류 확정과 구분해 다듬는다.

| ID | 대상 | 문제 / 관찰 | 개선안 | 근거 / 한계 |
|---|---|---|---|---|
| L01 | 管 | 외우기가 ‘관리하다’로 끝나고 대롱의 뜻과 연결이 약함. 관리하다도 실제 뜻이므로 오답 판정은 아님. | 관리(官)가 대나무(⺮) 대롱을 부는 → 대롱·피리 | [漢字ペディア・管](https://www.kanjipedia.jp/kanji/0001105400) |
| L02 | 漢 | 강(氵)이 풀밭(艹)에 입(口) 큰 사내(夫)들이 사는 → 한나라: 주어·서술 연결이 끊겨 있음. | 풀밭(艹) 사내(夫)가 강(氵)가에서 입(口)을 여는 → 한나라 | 유래의 강 이름 漢水→한나라 설명은 유지. 제안은 현재 모양만 이용한 암기 장면이며 실제 어원이 아님. |
| L03 | 禺 계열 | 偶·遇·隅의 구성에서 禺를 ‘땅 이름 옹’으로 부르지만 소리는 グウ, 외우기는 원숭이다. 서로 다른 훈음의 첫 항목을 고른 결과로 보인다. | 구성의 설명은 ‘원숭이 우’처럼 해당 유래·소리와 연결되는 훈음 우선. ‘땅 이름 옹’이라는 별도 용법 자체를 오자라고 삭제하지 말기. | 해당 세 글자의 현행 구성·소리·외우기 직접 대조. 한국 한자사전의 용례 확인 후 보정. |
| L04 | 宓 계열 | 密·蜜에서 宓(성 복)과 ミツ가 만나면 소리 연결이 끊긴다. 성씨 독음이 존재하는 것과 이 카드에서 유용한 것은 별개다. | ‘편안할 밀’ 등 밀 독음의 사전 표제를 확인해 사용. 성 복을 전역적으로 없애지 말 것. | 훈음 선택 개선. 별도 의미가 틀렸다는 판정은 아님. |
| L05 | 扁 계열 | 偏·遍에는 ‘특별할 편’, 編에는 ‘글 적는 판’으로 나타난다. 희귀 훈을 첫 의미로 노출해 연결이 나빠진다. | 偏·遍은 ‘납작하다/작을 편’, 編은 해당 유래의 ‘글 적는 판’처럼 용도에 맞는 짧은 말로 선택. 전역 치환하지 말기. | 희귀 훈의 존재 여부와 학습 적합성을 구분. 확신 낮음: ‘특별할 편’이 무조건 오자라고 단정하지 않음. |
| L06 | 乇 계열 | 宅·託의 구성은 ‘사나울 학’인데, 각 유래에서는 タク이고 외우기는 풀싹이다. 독음·설명 연결이 크게 어긋난다. | 부탁할 탁·풀잎 탁 등 탁 독음의 정확한 한국 사전 표제를 확인 후 적용. 현재 학 표제의 출처도 별도 확인. | 불일치는 원문에서 확인. ‘사나울 학’ 표제 자체의 진위는 확신 낮음: 이번에 한국 사전 원전 근거를 확보하지 못함. |
| L07 | 聿 계열 | 律 구성의 ‘마침내 율’은 書·建·筆에서 쓰는 ‘붓’과 연결이 안 된다. | 율 독음은 유지하고 구성의 설명을 ‘붓 율’ 또는 ‘붓’로 선택. ‘마침내’ 용법 자체를 오류로 판정하지 않음. | 書·建·筆의 현행 유래와 직접 대조. |
| L08 | 체계 전체 | 代의 弋(ヨク), 転의 云(專의 セン), 仏의 厶(弗의 フツ) 등에서 현재 모양의 일반 음독과 역사적 음부의 음을 혼동하기 쉬움. | 공통 안내: ‘소리 부분은 글자가 만들어질 당시의 중국어 발음과 관련돼. 지금의 일본어 음독은 서로 다를 수 있어.’ 줄인 부분은 ‘원래 專(セン)의 소리 부분’처럼 표기. | 이웃끼리 같은 성을 쓰던 집도 시간이 지나면 발음이 달라질 수 있다는 정도의 짧은 설명이면 충분함. 현대 음독이 다르다는 이유만으로 음부를 바꾸지 말 것. |
| L09 | 체계 전체 | 뜻+소리 유형인데 현재 구성에 소리가 없다는 자동 경고를 모두 오류로 처리할 위험. | 宝·医·体·聴·国처럼 옛 글자에서 소리 부분이 없어졌거나 대체된 경우는 유래와 현재 모양을 잇는 한 문장으로 해결. 없는 소리를 현재 부품에 억지로 부여하지 않기. | 역사적 원형 분석과 현재 모양 분석은 별개의 층위. |
| L10 | 부수 설명 전체 | 부수 이름의 일반 뜻과 글자 안에서 그 부분이 했던 역할을 같은 것으로 읽기 쉬움. | 공통 안내: ‘부수는 사전의 분류표야. 유래에서 뜻을 맡는 부분과 다를 수 있어.’ 全의 入 분류, 書의 曰, 隣의 阜를 이름만 보고 정정하지 말기. | 부수는 책장의 분류표, 유래는 글자의 출생 이야기라는 비유로 구분. |

## 🛡️ 이번 검토에서 오답으로 처리하지 않은 대표 항목

이 표는 재수정 중 정상 설명이 손상되는 일을 막기 위한 것이다. 모든 글자가 유일한 정설이라는 인증은 아니다.

| 한자 | 보존해야 할 내용 | 근거 |
|---|---|---|
| 書 | 聿이 뜻, 者를 줄인 曰이 소리라는 설명은 확인한 CUHK 자료에 근거가 있다. 曰이 보인다는 이유로 ‘말+붓의 회의’로 바꾸지 않는다. | [香港中文大學・書](https://humanum.arts.cuhk.edu.hk/Lexis/lexi-mf/search.php?word=%E6%9B%B8) |
| 聖 | 耳·口·사람과 呈의 소리 설명에는 문헌 근거가 있다. 현재 王 모양과 원형 𡈼를 이미 구분한 점은 유지한다. | [香港中文大學・聖](https://humanum.arts.cuhk.edu.hk/Lexis/lexi-mf/search.php?word=%E8%81%96) |
| 肺 | 일본 교육용 자형의 市와 옛 음부 巿의 차이는 사전에 명시된다. 한국 정자만을 기준으로 일본의 市 모양을 오자라고 고치지 않는다. | [漢字ペディア・肺](https://www.kanjipedia.jp/kanji/0005564100) |
| 新 | 辛과 木이 공유 획을 가지며 斤과 합쳐진다는 분석이 확인된다. 왼쪽에 立이 보인다고 옛 辛 설명을 지우지 않는다. | [香港中文大學・新](https://humanum.arts.cuhk.edu.hk/Lexis/lexi-mf/search.php?word=%E6%96%B0) |
| 便·侵 | 회의 풀이를 제시한 실제 일본 사전이 있다. 다른 자료에 형성 풀이가 있다는 이유만으로 현재 유형을 일괄 변경하지 않는다. | [漢字ペディア・便](https://www.kanjipedia.jp/kanji/0006286100/) / [漢字ペディア・侵](https://www.kanjipedia.jp/kanji/0003603100) |
| 縄 | 糸+蠅을 줄인 黽이라는 일본 사전의 설명에 근거가 있다. 외우기의 현재 자형 문제와 역사적 설명의 진위를 분리한다. | [漢字ペディア・縄](https://www.kanjipedia.jp/kanji/0003521700) |
| 黒 | 현재 문장은 ‘옛 사전은…’, ‘가장 오래된 모양은 의견이 갈림’을 이미 남겼다. 굴뚝 설을 옮겼다는 이유만으로 단정적 오류로 세지 않는다. | 유래의 출처·유보 표현을 직접 확인. |

## 🔬 부록 A — 구성의 빠진 획 후보 전부 (97자)

검사 방식: 기준 KanjiVG SVG에서 현재 생성기가 채택하는 ‘이름 붙은 부품 묶음’의 획 집합을 합치고, 전체 획 집합에서 뺐다. 묶음 안에 이름이 없는 획도 포함했지만 묶음 바깥에 있는 획은 남는다. 단순히 부품 글자의 사전 획수를 더한 검사가 아니다.

**표의 획 번호는 링크한 KanjiVG SVG 기준이다.** 앱 그림의 픽셀 좌표·애니메이션 번호를 전부 동일하다고 보장하지 않는다. 누락이 있어도 설명 괄호가 전체를 대신 부르도록 쓴 경우가 있으므로, 아래는 해당 구성식을 완전한 분해로 보여도 되는지 재검토할 목록이다. 각 행에서 남는 획을 포함하도록 현재 형태의 전체 부품을 다시 잡거나 ‘일부 모양’이라고 분명히 적는다. 소리 역할을 부분 한 획에 몰아주지 말 것.

원자료에서는 빠지지만 현행 `!` 구성에서 이미 보완된 **収·号·従·祭 4자**는 아래 97자에 중복 지적하지 않았다. 해당 4자의 새 수정을 요청하는 표가 아니다.

| 한자·원문 위치 | 현재 구성 | 묶음 밖에 남는 획 | 자형 근거 |
|---|---|---|---|
| [不](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L9) | *一+丿+丨*(꽃받침으로 봄 · 모양) | 4 / 전체 4획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/04e0d.svg) |
| [丹](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L20) | 冂(구덩이 · 모양) + *丶*(붉은 알갱이 · 모양) | 4 / 전체 4획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/04e39.svg) |
| [乗](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L25) | 千(올라선 사람 大가 변한 꼴 · 뜻) + 木(나무 · 뜻) | 3,4,5 / 전체 9획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/04e57.svg) |
| [事](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L36) | 口(장대에 달린 도구 · 뜻) + ⺕(손 · 뜻) + *亅*(장대 · 뜻) | 1 / 전체 8획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/04e8b.svg) |
| [亜](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L41) | *二+口*(무덤 속 방이나 제단으로 봄 · 모양) | 5,6 / 전체 7획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/04e9c.svg) |
| [今](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L49) | *人+一*(뒤집힌 입 등, 의견이 갈림) | 4 / 전체 4획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/04eca.svg) |
| [以](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L58) | 丶(왼쪽, 쟁기 같은 도구로 봄 · 뜻) + *人*(사람 · 뜻) | 1,2 / 전체 5획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/04ee5.svg) |
| [余](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L83) | *人+小*(餘의 소리 부분 余) | 3,4 / 전체 7획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/04f59.svg) |
| [侯](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L94) | *亻*(뒤에 더한 사람 · 뜻) + 矢(과녁 厂에 꽂힌 화살 · 뜻) | 3,4 / 전체 9획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/04faf.svg) |
| [倉](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L109) | *人*(지붕 · 모양) + 口(문과 바닥 · 모양) | 3,4,5,6,7 / 전체 10획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/05009.svg) |
| [僅](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L136) | *亻*(사람 · 뜻) + 艹+三(진흙 근 堇 · 소리 キン) | 6,7,8,9,10 / 전체 13획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/050c5.svg) |
| [兆](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L150) | *儿+冫*(점칠 때 갈라진 금으로 봄 · 모양) | 5,6 / 전체 6획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/05146.svg) |
| [免](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L154) | 𠂊(투구로 봄 · 모양) + *儿*(사람 · 모양) | 3,4,5,6 / 전체 8획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/0514d.svg) |
| [具](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L164) | 目(솥 鼎이 줄어든 꼴 · 뜻) + *八*(두 손 廾이 변한 꼴 · 뜻) | 6 / 전체 8획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/05177.svg) |
| [兼](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L166) | *八*(두 벼 禾의 윗부분 · 뜻) + ⺕(손 又가 변한 꼴 · 뜻) + 丨+丨(두 벼 禾의 줄기 · 뜻) | 3,9,10 / 전체 10획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/0517c.svg) |
| [凸](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L186) | *一+凵*(가운데가 볼록 튀어나온 모양 · 기호) | 2,3 / 전체 5획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/051f8.svg) |
| [前](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L210) | 八+月(앞 歬이 변한 꼴 · 소리) + *刂*(칼 · 뜻) | 3 / 전체 9획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/0524d.svg) |
| [半](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L258) | 丶(나누다 八이 변한 꼴 · 뜻) + *二+十*(소 牛가 변한 꼴 · 뜻) | 2 / 전체 5획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/0534a.svg) |
| [南](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L263) | *十+冂+干*(매달아 치는 악기로 봄 · 모양) | 5,6 / 전체 9획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/05357.svg) |
| [印](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L267) | 丿+丨(손 爪가 변한 꼴 · 뜻) + *卩*(꿇어앉은 사람 · 뜻) | 3,4 / 전체 6획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/05370.svg) |
| [司](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L301) | *一+口*(后를 뒤집은 꼴이라 함) | 1 / 전체 5획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/053f8.svg) |
| [后](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L307) | *厂+口*(司를 뒤집은 꼴이란 의견) | 3 / 전체 6획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/0540e.svg) |
| [呉](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L319) | *口*(입 · 뜻) + 八(머리 기운 夨이 바뀐 꼴 · 뜻) | 4,5 / 전체 7획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/05449.svg) |
| [唐](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L337) | 广+⺕(庚이 변한 꼴 · 소리 コウ) + *口*(입 · 뜻) | 7 / 전체 10획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/05510.svg) |
| [善](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L344) | 羊(양, 좋은 징조 · 뜻) + *口*(말 둘 誩을 줄인 꼴 · 뜻) | 7,8,9 / 전체 12획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/05584.svg) |
| [喜](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L347) | 吉(북 壴의 윗부분 · 뜻) + *口*(입 · 뜻) | 7,8,9 / 전체 12획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/0559c.svg) |
| [在](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L375) | 亻(才가 변한 一+亻 꼴 · 소리 サイ) + *土*(흙 · 뜻) | 1 / 전체 6획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/05728.svg) |
| [垂](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L382) | 千(잎이 늘어진 풀·나무 · 뜻·소리 スイ) + *土*(흙 · 뜻) | 3,4,5 / 전체 8획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/05782.svg) |
| [塞](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L408) | 宀+三+八(틈을 메우는 윗부분 · 소리 サイ) + *土*(흙 · 뜻) | 5,6 / 전체 13획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/0585e.svg) |
| [夜](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L433) | 亠+亻(亦을 줄인 꼴 · 소리 エキ) + *夕*(저녁 · 뜻) | 8 / 전체 8획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/0591c.svg) |
| [央](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L439) | 丨(테두리 冂 또는 칼 · 뜻) + *大*(사람 · 뜻) | 2 / 전체 5획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/0592e.svg) |
| [奉](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L443) | 𡗗+丨(丰·廾·手가 합쳐진 꼴) | 6,7 / 전체 8획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/05949.svg) |
| [妻](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L464) | ⺕(손 · 뜻) + *女*(여자 · 뜻) | 1,5 / 전체 8획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/059bb.svg) |
| [存](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L490) | 亻(才가 변한 一+亻 꼴 · 소리 サイ) + *子*(아이 · 뜻) | 1 / 전체 6획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/05b58.svg) |
| [官](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L502) | *宀*(집 · 뜻) + 口(군대·무리 𠂤가 변한 꼴 · 뜻) | 7,8 / 전체 8획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/05b98.svg) |
| [寒](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L524) | *宀*(집 · 뜻) + 三+八(풀더미에 묻힌 사람 · 뜻) + 冫(얼음 · 뜻) | 5,6 / 전체 12획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/05bd2.svg) |
| [寡](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L528) | *宀*(집 · 뜻) + 自+分(사람 頁이 바뀐 꼴 · 뜻) | 4 / 전체 14획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/05be1.svg) |
| [専](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L537) | 由(실감개 叀이 바뀐 꼴 · 뜻·소리 セン) + *寸*(손 · 뜻) | 1 / 전체 9획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/05c02.svg) |
| [展](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L559) | *尸*(몸 · 뜻) + 廾(㠭을 줄인 꼴 · 소리 テン) | 7,8,9,10 / 전체 10획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/05c55.svg) |
| [岡](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L566) | 冂(그물 网을 줄인 꼴 · 소리) + *山*(산 · 뜻) | 3,4,5 / 전체 8획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/05ca1.svg) |
| [帝](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L596) | *亠+冖+巾*(꽃꼭지나 묶은 제사 장작 · 모양) | 3,4 / 전체 9획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/05e1d.svg) |
| [帥](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L597) | 丿(왼쪽 𠂤 · 소리) + *巾*(천 · 뜻) | 2,3,4,5,6 / 전체 9획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/05e25.svg) |
| [師](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L598) | 丿+丨(언덕·군대가 머무는 곳 · 뜻) + *巾*(위 一과 함께 帀, 두르다 · 뜻) | 3,4,5,6,7 / 전체 10획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/05e2b.svg) |
| [年](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L610) | *丿+干*(벼 禾와 사람 人이 합친 꼴) | 4,5 / 전체 6획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/05e74.svg) |
| [恵](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L707) | 由(실패 叀을 줄인 꼴 · 뜻) + *心*(마음 · 뜻) | 1 / 전체 10획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/06075.svg) |
| [慶](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L741) | 广(사슴 鹿을 줄인 꼴 · 뜻) + *心*(마음 · 뜻) + 夂(가다 · 뜻) | 4,5,6,7,8 / 전체 15획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/06176.svg) |
| [憲](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L748) | 宀(害를 줄인 꼴 · 소리 ガイ) + 罒(눈 目이 변한 꼴 · 뜻) + *心*(마음 · 뜻) | 4,5,6,7 / 전체 16획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/061b2.svg) |
| [我](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L756) | *丿+戈+亅*(날이 톱니 같은 창 · 모양) | 4 / 전체 7획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/06211.svg) |
| [抑](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L779) | *扌*(손 · 뜻) + 卩(印을 뒤집은 꼴이라 함 · 뜻) | 4,5 / 전체 7획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/06291.svg) |
| [拳](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L805) | 二+人(합쳐서 龹 · 소리 ケン) + *手*(손 · 뜻) | 1,2 / 전체 10획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/062f3.svg) |
| [敢](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L873) | 耳(귀 꼴) + *攵*(치다 꼴) | 1,2 / 전체 12획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/06562.svg) |
| [散](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L874) | 月(고기 · 뜻) + *攵*(흩다 㪔의 일부 · 뜻·소리 サン · 부수) | 1,2,3,4 / 전체 12획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/06563.svg) |
| [斉](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L881) | *文*(齊의 위를 줄인 꼴 · 모양) + 二(齊의 아랫부분 · 모양) | 5,8 / 전체 8획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/06589.svg) |
| [斗](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L884) | 丶+丶(국자 모양의 일부 · 모양) | 3,4 / 전체 4획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/06597.svg) |
| [断](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L890) | 米(끊긴 실 㡭을 줄인 꼴 · 뜻) + *斤*(도끼 · 뜻) | 7 / 전체 11획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/065ad.svg) |
| [旅](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L894) | *方+𠂉*(깃발 · 뜻) + 亻(여러 사람 · 뜻) | 9,10 / 전체 10획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/065c5.svg) |
| [昔](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L910) | 廾(물결이 변한 꼴 · 뜻) + *日*(날 · 뜻) | 4 / 전체 8획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/06614.svg) |
| [曹](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L940) | 曲(두 東이 줄어든 꼴 · 뜻) + *曰*(말하다 · 뜻) | 1 / 전체 11획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/066f9.svg) |
| [楽](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L1024) | 白(무엇인지 의견이 갈림 · 모양) + 冫(실 幺를 줄인 점 · 모양) + *木*(나무 · 모양) | 8,9 / 전체 13획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/0697d.svg) |
| [求](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L1076) | *氺+丶*(털이 드러난 가죽옷 · 모양) | 1 / 전체 7획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/06c42.svg) |
| [汚](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L1079) | *氵*(물 · 뜻) + 二(亏가 변한 꼴 · 소리 ウ) | 6 / 전체 6획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/06c5a.svg) |
| [渋](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L1141) | *氵*(물 · 뜻) + 止+冫(엇갈린 발 歰을 줄인 꼴 · 뜻) | 10,11 / 전체 11획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/06e0b.svg) |
| [為](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L1198) | 丶+勹(손과 코끼리 윗부분 · 뜻) + *灬*(코끼리 아랫부분 · 뜻) | 3,4 / 전체 9획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/070ba.svg) |
| [無](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L1200) | *丿+一+灬*(장식을 들고 춤추는 사람 · 모양) | 2,4,5,6,7,8 / 전체 12획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/07121.svg) |
| [牙](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L1221) | 亅+丿(엇갈려 맞물린 어금니 · 모양) | 1,2 / 전체 4획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/07259.svg) |
| [獣](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L1243) | ⺍+田+口(사냥 도구 嘼를 줄인 꼴 · 뜻) + *犬*(개 · 뜻) | 9 / 전체 16획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/07363.svg) |
| [率](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L1246) | *玄*(가운데 밧줄 · 모양) + 冫(양옆 점 · 모양) + 十(밧줄의 아랫부분 · 모양) | 8,9 / 전체 11획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/07387.svg) |
| [畏](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L1277) | *田+一*(몽둥이를 든 귀신 · 뜻) | 7,8,9 / 전체 9획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/0754f.svg) |
| [益](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L1315) | 八(옆으로 누인 물 水 · 뜻) + *皿*(그릇 · 뜻) | 1,2,3 / 전체 10획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/076ca.svg) |
| [監](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L1319) | 臣(내려다보는 눈 · 뜻) + 𠂉(사람 · 뜻) + *皿*(물 담긴 그릇 · 뜻) | 10 / 전체 15획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/076e3.svg) |
| [直](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L1323) | 十(눈 위의 곧은 선 · 뜻) + *目*(눈 · 뜻) | 8 / 전체 8획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/076f4.svg) |
| [眉](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L1327) | 丿(눈썹 · 모양) + *目*(눈 · 모양) | 1,2,3 / 전체 9획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/07709.svg) |
| [県](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L1329) | *目+小*(首를 거꾸로 매단 꼴 · 뜻) | 6 / 전체 9획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/0770c.svg) |
| [粛](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L1447) | *聿*(손에 일을 쥠 · 뜻) + 米(깊은 못이 줄어든 꼴 · 뜻) | 10,11 / 전체 11획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/07c9b.svg) |
| [継](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L1484) | *糸*(실 · 뜻) + 米(끊긴 실 다발을 줄인 꼴 · 뜻) | 13 / 전체 13획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/07d99.svg) |
| [繭](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L1513) | 艹(풀 초 꼴) + 冂(감싼 꼴 · 뜻) + *糸*(실 · 뜻) + 虫(벌레 · 뜻) | 6 / 전체 18획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/07e6d.svg) |
| [膝](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L1585) | *月*(몸 · 뜻) + 木+氺(옻 桼 · 소리 シツ) | 9,10 / 전체 15획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/0819d.svg) |
| [至](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L1594) | 厶(날아온 화살 · 모양) + 土(땅 一에 꽂힌 화살 끝 · 기호) | 1 / 전체 6획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/081f3.svg) |
| [興](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L1597) | *臼*(위의 두 손 · 뜻) + 同(함께 · 뜻) + 八(아래의 두 손 · 뜻) | 14 / 전체 16획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/08208.svg) |
| [舞](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L1601) | 丿+一(춤추는 사람 無의 윗부분 · 뜻·소리 ブ) + *舛*(두 발 · 뜻) | 2,4,5,6,7,8 / 전체 15획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/0821e.svg) |
| [華](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L1637) | *艹*(풀 · 뜻) + 丨(아래, 꽃이 활짝 핀 초목 · 뜻·소리 カ) | 4,5,6,7,8,9 / 전체 10획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/083ef.svg) |
| [薦](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L1651) | *艹*(풀 · 뜻) + 广+灬(해태 廌 · 뜻) | 7,8,9,10,11,12 / 전체 16획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/085a6.svg) |
| [衆](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L1675) | *血*(해 日이 변한 꼴 · 뜻) + 亻(사람 셋 · 뜻) | 9,10,11,12 / 전체 12획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/08846.svg) |
| [衰](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L1684) | *衣*(비옷 · 모양) + 口(엮은 풀 · 모양) | 5 / 전체 10획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/08870.svg) |
| [誉](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L1747) | ⺍+八(與를 줄인 꼴 · 소리 ヨ) + *言*(말 · 뜻) | 4 / 전체 13획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/08a89.svg) |
| [象](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L1789) | 𠂊(코끼리 머리 쪽 · 모양) + *豕*(코끼리 몸과 다리 · 모양) | 3,4,5 / 전체 12획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/08c61.svg) |
| [貴](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L1804) | 中(윗부분, 무엇인지 의견이 갈림 · 소리) + *貝*(재물 · 뜻) | 5 / 전체 12획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/08cb4.svg) |
| [賓](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L1816) | 宀+少(집에 사람이 찾아온 꼴 · 뜻) + *貝*(예물 · 뜻) | 4 / 전체 15획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/08cd3.svg) |
| [遂](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L1895) | 豕(위에 八을 얹은 㒸 · 소리 スイ) + *辶*(가다 · 뜻) | 1,2 / 전체 12획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/09042.svg) |
| [遣](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L1908) | 中(안쪽 부분 · 소리 ケン) + *辶*(가다 · 뜻) | 5,6,7,8,9,10 / 전체 13획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/09063.svg) |
| [那](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L1918) | 二(冄이 변한 왼쪽 부분 · 소리 ゼン) + *阝*(고을 邑이 변한 꼴 · 뜻) | 1,4 / 전체 7획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/090a3.svg) |
| [隊](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L2017) | *阝*(언덕 · 뜻) + 豕(八+豕 꼴인 㒸 · 소리 スイ) | 4,5 / 전체 12획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/0968a.svg) |
| [電](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L2042) | *雨*(비 · 뜻) + 日(번개 申이 바뀐 꼴 · 뜻·소리 シン) | 13 / 전체 13획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/096fb.svg) |
| [革](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L2053) | 廿+口(벗겨 펼친 짐승 가죽 · 모양) | 8,9 / 전체 9획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/09769.svg) |
| [風](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L2081) | 几(凡이 변한 꼴 · 소리 ハン) + 虫(벌레 · 뜻) | 3 / 전체 9획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/098a8.svg) |
| [鹿](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L2125) | 广(뿔·머리 쪽 윗부분 · 모양) + 比(다리 · 모양) | 4,5,6,7 / 전체 11획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/09e7f.svg) |
| [麗](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L2127) | 一+一(윗부분 丽, 한 쌍 · 뜻·소리 レイ) + *鹿*(사슴 · 뜻) | 2,3,4,6,7,8 / 전체 19획 | [SVG](https://github.com/KanjiVG/kanjivg/blob/610091ce618cf757ed1357a8954cd65283e015d6/kanji/09e97.svg) |

## 🏷️ 부록 B — 검토용 MD의 부수 명칭 차이 전부 (579자)

`export_md.py`는 부수 번호와 모양이 맞는 첫 행을 고르지만 `Radical.find()`는 위치까지 고려한다. 그래서 木이 왼쪽에 있는 카드의 `나무목변·きへん`이 검토용 표에서는 `나무 목·き`로 나온다. 둘 다 그 부수의 이름이므로 **기본 이름을 거짓으로 판정하지 말 것**. 문제는 ‘카드 그대로 내보낸다’는 검토 자료가 실제 위치별 이름을 재현하지 못한다는 점이다.

근거: [내보내기 선택](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/tools/origin/kosei/export_md.py#L104) / [카드의 위치별 선택](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/java/com/ggumtak/hanja/Parts.kt#L139) / [부수 이름 데이터](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/radicals.tsv).

아래의 영향 한자는 모두 적었다. 현재 앱이 잘 고르는 명칭을 기본명으로 되돌리지 말고, 검토 표를 앱과 일치시킬 것.

| MD의 기본 이름 | 카드의 위치별 이름 | 글자 수 | 영향 한자 전체 |
|---|---|---|---|
| 人 ひと 사람 인 | 人 ひとやね 사람인 | 10 | 今 介 令 企 会 余 倉 傘 舎 舗 |
| 八 はち 여덟 팔 | 八 はちがしら 여덟 팔 | 2 | 公 兼 |
| 口 くち 입 구 | 口 くちへん 입구변 | 30 | 叫 叱 吐 吟 吸 吹 呪 味 呼 命 咲 咽 哺 唄 唆 唯 唱 唾 喉 喚 喝 喩 喫 嗅 嘆 嘱 嘲 器 噴 嚇 |
| 土 つち 흙 토 | 土 つちへん 흙토변 | 29 | 地 坂 均 坊 坑 坪 垣 埋 城 域 培 埼 堀 堆 堤 堪 場 塀 塊 塔 塚 塡 塩 境 増 墳 壇 壊 壌 |
| 女 おんな 계집 녀 | 女 おんなへん 계집녀변 | 28 | 奴 好 如 妃 妊 妖 妙 妨 妬 妹 姉 始 姓 姫 姻 娘 娠 娯 婚 婦 婿 媒 媛 嫁 嫉 嫌 嫡 嬢 |
| 子 こ 아들 자 | 子 こへん 아들자변 | 3 | 孔 孤 孫 |
| 山 やま 메 산 | 山 やまへん 메산변 | 6 | 岐 岬 峠 峡 峰 崎 |
| 山 やま 메 산 | 山 やまかんむり 메 산 | 6 | 岩 岸 崇 崖 崩 嵐 |
| 工 たくみ 장인 공 | 工 たくみへん 장인 공 | 1 | 巧 |
| 巾 はば 수건 건 | 巾 はばへん 수건건변 | 4 | 帆 帳 帽 幅 |
| 弓 ゆみ 활 궁 | 弓 ゆみへん 활궁변 | 8 | 引 弥 弦 弧 弱 張 強 弾 |
| 戸 と 지게 호 | 戸 とだれ 지게 호 | 3 | 房 扇 扉 |
| 方 ほう 모 방 | 方 ほうへん 모방변 | 5 | 施 旅 旋 族 旗 |
| 日 ひ 날 일 | 日 ひへん 날일변 | 15 | 旺 明 映 昧 昨 昭 時 晩 晴 暁 暇 暖 暗 曖 曜 |
| 月 つき 달 월 | 月 つきへん 달월변 | 2 | 服 朕 |
| 木 き 나무 목 | 木 きへん 나무목변 | 61 | 札 朴 机 朽 杉 材 村 杯 松 板 析 枕 林 枚 枝 枠 枢 枯 柄 柱 柳 柵 柿 栃 栓 校 株 核 根 格 桁 桃 桜 桟 梅 梗 械 棋 棒 棚 棟 棺 椅 植 椎 検 極 楷 楼 概 構 様 槽 標 模 権 横 樹 橋 機 欄 |
| 火 ひ 불 화 | 火 ひへん 불화변 | 9 | 灯 炉 炊 焼 煙 煩 燃 燥 爆 |
| 片 かた 조각 편 | 片 かたへん 조각편변 | 1 | 版 |
| 牛 うし 소 우 | 牛 うしへん 소우변 | 5 | 牧 物 牲 特 犠 |
| 王 たま 구슬 옥 | 王 おうへん 구슬옥변 | 11 | 玩 珍 珠 班 現 球 理 琴 瑠 璃 環 |
| 田 た 밭 전 | 田 たへん 밭전변 | 3 | 町 畔 略 |
| 疋 ひき 필 필 | 疋 ひきへん 필 필 | 1 | 疎 |
| 白 しろ 흰 백 | 白 しろへん 흰백변 | 1 | 的 |
| 目 め 눈 목 | 目 めへん 눈목변 | 8 | 眠 眺 眼 睡 睦 瞬 瞭 瞳 |
| 矢 や 화살 시 | 矢 やへん 화살시변 | 3 | 知 短 矯 |
| 石 いし 돌 석 | 石 いしへん 돌석변 | 13 | 砂 研 砕 砲 破 硝 硫 硬 碑 確 磁 礁 礎 |
| 禾 のぎ 벼 화 | 禾 のぎへん 벼화변 | 21 | 私 秋 科 秒 秘 租 秩 称 移 程 税 稚 種 稲 稼 稽 稿 穂 積 穏 穫 |
| 穴 あな 구멍 혈 | 穴 あなかんむり 구멍혈머리 | 9 | 究 空 突 窃 窒 窓 窟 窮 窯 |
| 立 たつ 설 립 | 立 たつへん 설립변 | 1 | 端 |
| 竹 たけ 대 죽 | 竹 たけかんむり 대죽머리 | 24 | 笑 笛 符 第 筆 等 筋 筒 答 策 箇 箋 算 管 箱 箸 節 範 築 篤 簡 簿 籍 籠 |
| 米 こめ 쌀 미 | 米 こめへん 쌀미변 | 9 | 粉 粋 粒 粗 粘 粧 精 糖 糧 |
| 糸 いと 실 사 | 糸 いとへん 실사변 | 54 | 糾 紀 約 紅 紋 納 純 紙 級 紛 紡 細 紳 紹 紺 終 組 経 結 絞 絡 給 統 絵 絶 絹 継 続 維 綱 網 綻 綿 総 緑 緒 線 締 編 緩 緯 練 緻 縁 縄 縛 縦 縫 縮 績 繊 織 繕 繰 |
| 耳 みみ 귀 이 | 耳 みみへん 귀이변 | 3 | 聖 聴 職 |
| 至 いたる 이를 지 | 至 いたるへん 이를 지 | 1 | 致 |
| 舟 ふね 배 주 | 舟 ふねへん 배주변 | 7 | 航 般 舶 舷 船 艇 艦 |
| 虫 むし 벌레 훼 | 虫 むしへん 벌레훼변 | 4 | 虹 蚊 蛇 蜂 |
| 角 つの 뿔 각 | 角 つのへん 뿔각변 | 2 | 解 触 |
| 言 げん 말씀 언 | 言 ごんべん 말씀언변 | 63 | 訂 訃 計 討 訓 託 記 訟 訪 設 許 訳 訴 診 証 詐 詔 評 詞 詠 詣 試 詩 詮 詰 話 該 詳 誇 誌 認 誕 誘 語 誠 誤 説 読 誰 課 調 談 請 論 諦 諧 諭 諮 諸 諾 謀 謁 謎 謙 講 謝 謡 謹 識 譜 議 譲 護 |
| 貝 かい 조개 패 | 貝 かいへん 조개패변 | 13 | 財 販 貯 貼 賂 賄 賊 賜 賠 賦 賭 購 贈 |
| 走 はしる 달릴 주 | 走 そうにょう 달릴주 | 5 | 赴 起 超 越 趣 |
| 足 あし 발 족 | 足 あしへん 발족변 | 10 | 距 跡 路 跳 践 踊 踏 踪 蹴 躍 |
| 車 くるま 수레 거 | 車 くるまへん 수레거변 | 10 | 軌 軒 軟 転 軸 軽 較 輪 輸 轄 |
| 酉 とり 닭 유 | 酉 とりへん 닭유변 | 13 | 酌 配 酎 酔 酢 酪 酬 酵 酷 酸 醒 醜 醸 |
| 釆 のごめ 분별할 변 | 釆 のごめへん 분별할 변 | 1 | 釈 |
| 里 さと 마을 리 | 里 さとへん 마을 리 | 1 | 野 |
| 金 かね 쇠 금 | 金 かねへん 쇠금변 | 31 | 針 釣 鈍 鈴 鉄 鉛 鉢 鉱 銀 銃 銅 銘 銭 鋭 鋳 鋼 錠 錦 錬 錮 錯 録 鍋 鍛 鍵 鎌 鎖 鎮 鏡 鐘 鑑 |
| 雨 あめ 비 우 | 雨 あめかんむり 비우머리 | 12 | 雪 雰 雲 零 雷 電 需 震 霊 霜 霧 露 |
| 革 かくのかわ 가죽 혁 | 革 かわへん 가죽혁변 | 1 | 靴 |
| 音 おと 소리 음 | 音 おとへん 소리 음 | 1 | 韻 |
| 馬 うま 말 마 | 馬 うまへん 말마변 | 8 | 駄 駅 駆 駐 駒 騎 騒 験 |
| 骨 ほね 뼈 골 | 骨 ほねへん 뼈골변 | 2 | 骸 髄 |
| 鬼 おに 귀신 귀 | 鬼 きにょう 귀신 귀 | 1 | 魅 |
| 魚 うお 물고기 어 | 魚 うおへん 물고기어변 | 2 | 鮮 鯨 |
| 麦 むぎ 보리 맥 | 麦 ばくにょう 보리 맥 | 1 | 麺 |
| 歯 は 이 치 | 歯 はへん 이 치 | 1 | 齢 |

## ✍️ 부록 C — 반복 문형 정리가 필요한 60자

`소리는 X도 내고 뜻도 보탬`은 주어가 어색하고, `소리는 X로, …뜻도 보탬`도 불필요하게 돌아간다. 내용의 뜻+소리 관계를 바꾸지 말고 **‘뜻 부분은 A. B에서 소리를 빌리고 뜻도 더함.’** 등으로 풀어 쓸 것. 예: `低`는 ‘뜻 부분은 亻(사람). 氐(テイ, 밑)에서 소리를 빌리고 뜻도 더함. 낮음을 나타냄.’으로 정리할 수 있다.

이것은 문장 교정 목록이다. 모두를 새로운 학설 오류로 세거나, 60자의 유형을 기계적으로 `회의 겸 형성`으로 바꾸지 않는다. 기존 유형 체계를 유지하되 본문의 소리·뜻 역할을 선명하게 연결하면 된다.

| 한자·원문 위치 | 현재 유래 전체 |
|---|---|
| [低](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L78) | 형성: 뜻은 亻(사람), 소리는 氐(テイ, 밑)도 내고 뜻도 보탬. 낮음을 나타냄. |
| [功](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L222) | 형성: 뜻은 力(힘), 소리는 工(コウ)도 내고 뜻도 보탬. 힘써 일해 이룬 공. |
| [堂](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L393) | 형성: 뜻은 土(흙), 소리는 尚(ショウ, 높이다)도 내고 뜻도 보탬. 흙을 높이 돋운 터의 큰 집. |
| [境](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L412) | 형성: 뜻은 土(땅), 소리는 竟(キョウ, 끝)도 내고 뜻도 보탬. 땅이 끝나는 곳 → 경계. |
| [崖](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L577) | 형성: 뜻은 山(산), 소리는 厓(ガイ, 벼랑)도 내고 뜻도 보탬. 산의 깎아지른 벼랑. |
| [底](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L621) | 형성: 뜻은 广(집), 소리는 氐(テイ, 밑)도 내고 뜻도 보탬. 집의 밑바닥. |
| [弦](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L647) | 형성: 뜻은 弓(활), 소리는 玄(ゲン, 검은 실)도 내고 뜻도 보탬. 활에 맨 줄, 시위. |
| [彩](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L656) | 형성: 뜻은 彡(무늬), 소리는 采(サイ, 캐다)도 내고 뜻도 보탬. 빛깔과 무늬. |
| [影](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L659) | 형성: 뜻은 彡(빛살), 소리는 景(ケイ, 볕)도 내고 뜻도 보탬. 본래 景이 그림자도 뜻해 彡을 더해 나눔. |
| [徳](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L676) | 형성: 뜻은 彳(가다), 소리는 悳(トク, 곧은 마음)도 내고 뜻도 보탬. 곧은 마음으로 행함. 일본에서는 心 위의 一을 빼고 씀. |
| [恭](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L705) | 형성: 뜻은 ⺗(마음), 소리는 共(キョウ)도 내고 뜻도 보탬. 받들어 공손함. |
| [悦](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L712) | 형성: 뜻은 忄(마음), 소리는 兌(기쁘다)도 내고 뜻도 보탬. 일본 글자는 兌 위의 八을 ソ로 씀. |
| [惑](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L718) | 형성: 뜻은 心(마음), 소리는 或(ワク)도 내고 뜻도 보탬. 마음이 어지러움. |
| [慌](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L733) | 형성: 뜻은 忄(마음), 소리는 荒(コウ)도 내고 뜻도 보탬. 마음이 어지러움. |
| [慣](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L737) | 형성: 뜻은 忄(마음), 소리는 貫(カン, 꿰다)도 내고 뜻도 보탬. 마음에 꿰인 듯 몸에 익음. |
| [慰](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L740) | 형성: 뜻은 心(마음), 소리는 尉(イ)도 내고 뜻도 보탬. 尉는 본래 다리미로 폄(熨). 마음을 펴 달램. |
| [憤](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L744) | 형성: 뜻은 忄(마음), 소리는 賁(フン, 부풀다)도 내고 뜻도 보탬. 분이 끓어오름. |
| [憶](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L749) | 형성: 뜻은 忄(마음), 소리는 意(イ)도 내고 뜻도 보탬. 마음에 새겨 둠. |
| [抄](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L777) | 형성: 뜻은 扌(손), 소리는 少(ショウ)도 내고 뜻도 보탬. 조금씩 떠냄, 베껴 뽑음. |
| [披](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L785) | 형성: 뜻은 扌(손), 소리는 皮(ヒ)도 내고 뜻도 보탬. 헤쳐 펼침. |
| [抱](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L786) | 형성: 뜻은 扌(손), 소리는 包(ホウ)도 내고 뜻도 보탬. 감싸 안음. |
| [挿](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L817) | 형성: 뜻은 扌(손), 소리는 臿(ソウ, 절구에 절굿공이를 꽂음)도 내고 뜻도 보탬. 찔러 꽂음. |
| [揚](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L840) | 형성: 뜻은 扌(손), 소리는 昜(ヨウ, 볕이 오름)도 내고 뜻도 보탬. 높이 올림. |
| [搬](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L847) | 형성: 뜻은 扌(손), 소리는 般(ハン)도 내고 뜻도 보탬. 般은 배(舟)를 돌림. 옮겨 나름. |
| [搾](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L850) | 형성: 뜻은 扌(손), 소리는 窄(サク, 좁다)도 내고 뜻도 보탬. 눌러 짜냄. |
| [散](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L874) | 형성: 뜻은 月(고기), 소리는 㪔(サン, 흩다, 月을 뺀 나머지 꼴)도 내고 뜻도 보탬. 본래 잘게 흩은 고기. |
| [敷](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L879) | 형성: 뜻은 攵(치다), 소리는 旉(フ, 펴다)도 내고 뜻도 보탬. 널리 펴서 깖. |
| [旺](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L905) | 형성: 뜻은 日(해), 소리는 王(オウ)도 내고 뜻도 보탬. 해가 성하게 빛남. |
| [昇](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L907) | 형성: 뜻은 日(해), 소리는 升(ショウ)도 내고 뜻도 보탬. 해가 떠오름. |
| [材](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L962) | 형성: 뜻은 木(나무), 소리는 才(サイ, 바탕)도 내고 뜻도 보탬. 쓸모 있는 재목. |
| [枯](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L979) | 형성: 뜻은 木(나무), 소리는 古(コ)로, '오래되다'라는 뜻도 보탬. 나무가 말라 죽음. |
| [架](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L980) | 형성: 뜻은 木(나무), 소리는 加(カ)로, '더하다'라는 뜻도 보탬. 나무를 걸쳐 만든 시렁. |
| [欄](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L1036) | 형성: 뜻은 木(나무), 소리는 闌(ラン)으로, '가로막다'라는 뜻도 보탬. 나무로 된 난간. |
| [清](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L1137) | 형성: 뜻은 氵(물), 소리는 青(セイ)도 내고 뜻도 보탬. 물이 맑음. 일본에서는 靑을 青으로 씀. |
| [滋](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L1161) | 형성: 뜻은 氵(물), 소리는 茲(シ, 불어나다)도 내고 뜻도 보탬. 물이 불어나고 초목이 자람. |
| [潔](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L1176) | 형성: 뜻은 氵(물), 소리는 絜(ケツ)도 내고 뜻도 보탬(絜도 '깨끗하다'로 씀). 물로 씻어 깨끗함. |
| [然](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L1202) | 형성: 뜻은 灬(불), 소리는 肰(ゼン, 개고기)도 내고 뜻도 보탬. 고기를 불에 그슬림(燃의 원래 글자). '그러하다'는 소리만 빌려 쓴 뜻. |
| [照](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L1206) | 형성: 뜻은 灬(불), 소리는 昭(ショウ)도 내고 뜻도 보탬. 불빛이 밝게 비춤. |
| [爆](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L1214) | 형성: 뜻은 火(불), 소리는 暴(バク)로, '사납다'라는 뜻도 보탬. 불이 터져 튐. |
| [畔](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L1279) | 형성: 뜻은 田(밭), 소리는 半(ハン)으로, '나누다'라는 뜻도 보탬. 밭의 경계. |
| [痘](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L1296) | 형성: 뜻은 疒(병), 소리는 豆(トウ)로, 콩알 같은 부스럼이라는 뜻도 보탬. 천연두. |
| [癒](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L1303) | 형성: 뜻은 疒(병), 소리는 愈(ユ)로, '낫다'라는 뜻도 보탬. 병이 나음. |
| [盲](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L1322) | 형성: 뜻은 目(눈), 소리는 亡(ボウ)으로, '없다'라는 뜻도 보탬. 눈동자가 없어 보지 못함. |
| [粉](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L1442) | 형성: 뜻은 米(쌀), 소리는 分(フン)도 내고 뜻도 보탬. 쌀을 잘게 빻은 가루. |
| [芽](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L1619) | 형성: 뜻은 艹(풀), 소리는 牙(ガ)도 내고 뜻도 보탬. 갓 돋은 싹. |
| [訓](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L1722) | 형성: 뜻은 言(말), 소리는 川(セン)도 내고 뜻도 보탬. 물 흐르듯 말로 이끌어 가르침. |
| [試](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L1739) | 형성: 뜻은 言(말), 소리는 式(シキ)도 내고 뜻도 보탬. 정해진 방식대로 해 봄. |
| [議](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L1782) | 형성: 뜻은 言(말), 소리는 義(ギ)도 내고 뜻도 보탬. 옳은 바를 말로 따짐. |
| [貯](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L1803) | 형성: 뜻은 貝(재물), 소리는 宁(チョ, 쌓아 두는 곳)도 내고 뜻도 보탬. 재물을 쌓아 둠. |
| [輪](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L1859) | 형성: 뜻은 車(수레), 소리는 侖(リン, 차례)도 내고 뜻도 보탬. 바퀴살이 가지런한 바퀴. |
| [迅](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L1869) | 형성: 뜻은 辶(가다), 소리는 卂(シン, 빨리 날다)도 내고 뜻도 보탬. 빠르게 감. |
| [逮](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L1891) | 형성: 뜻은 辶(가다), 소리는 隶(タイ, 뒤쫓아 잡다)도 내고 뜻도 보탬. 뒤쫓아 미침, 잡음. |
| [違](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L1904) | 형성: 뜻은 辶(가다), 소리는 韋(イ)도 내고 뜻도 보탬. 韋는 발이 서로 엇갈린 꼴. 어긋나 떠남. |
| [酌](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L1930) | 형성: 뜻은 酉(술), 소리는 勺(シャク)도 내고 뜻도 보탬. 국자로 술을 떠서 따름. |
| [錮](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L1971) | 형성: 뜻은 金(쇠), 소리는 固(コ)도 내고 뜻도 보탬. 쇠를 녹여 틈을 막음, 가둠. |
| [鑑](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L1982) | 형성: 뜻은 金(쇠), 소리는 監(カン)도 내고 뜻도 보탬. 監은 물동이에 얼굴을 비춰 보는 꼴. 본래 큰 쇠 물동이, 뒤에 '거울'. |
| [陶](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L2011) | 형성: 뜻은 阝(언덕), 소리는 匋(トウ)도 내고 뜻도 보탬. 匋는 사람이 질그릇(缶)을 빚는 꼴. |
| [陽](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L2014) | 형성: 뜻은 阝(언덕), 소리는 昜(ヨウ, 볕)도 내고 뜻도 보탬. 볕이 드는 언덕의 남쪽. |
| [頰](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L2070) | 형성: 뜻은 頁(머리), 소리는 夾(キョウ)도 내고 뜻도 보탬. 얼굴 양옆의 뺨. |
| [鶴](https://github.com/ggumtak/test/blob/647ea121fa10269b9050ae854422fcb0276f41b2/app/src/main/assets/origin.tsv#L2124) | 형성: 뜻은 鳥(새), 소리는 隺(カク, 높이 날다)도 내고 뜻도 보탬. 왼쪽은 寉 꼴로 보이나 본래 隺. 학. |

## ✅ 수정 후 내용 확인 기준

- 현재 모양의 모든 주요 부분을 설명하거나, 완전한 분해가 아닌 경우 ‘일부/원형/전체 묶음’을 표시했는가.
- 뜻·소리 역할의 주어가 유래와 구성에서 같은 범위를 가리키는가. 攵 하나를 㪔 전체로, 日 하나를 电 전체로 대신 부르지 않았는가.
- 학설의 ‘옛 사전에서는’, ‘의견이 갈림’, ‘~로 봄’이 구성으로 옮겨지면서 사라지지 않았는가.
- 외우기의 한자 모양이 실제 일본 글자에 보이는가. 다른 글자를 빌리면 ‘모양/일부’라고 구분했는가.
- 부수와 뜻 부분을 구분했는가. MD 이름이 카드 이름과 같은가.
- 유래의 재료·동물·신체 설명을 새 사실로 지어내지 않았는가.
- 재병합한 `origin.tsv`가 2,136개의 고유 키와 4개 필드를 유지하는가. 정렬·문자 인코딩·기존 한국어 설명을 손상하지 않았는가.
- 유래 본문 120자, 외우기 45자, 구성 90자 등의 현행 제한을 확인했는가. 짧게 줄이는 과정에서 핵심 부품이나 유보 표현을 빼지 않았는가.
- 바꾼 글자의 실제 카드와 새 MD를 나란히 읽었는가. 예외 구성도 획 색과 모양을 함께 확인했는가.

## 🔗 출처와 검토 한계

개별 사전 링크는 해당 표의 근거 칸에 넣었다. 일본의 일반 자형·학습용 풀이는 일본한자능력검정협회의 漢字ペディア를, 고문자 해석과 학설 비교는 香港中文大學 漢語多功能字庫를 우선 참고했다. 이 둘이 다르면 어느 한쪽을 무조건 지우지 않았다. 먹 재료는 나라현의 공예 안내로 별도 확인했다.

고문자 전체 학설을 다 조사한 논문 심사 수준의 인증서는 아니다. 의미·구성 전수 읽기, 일관성 검사, 의심 항목의 외부 자료 대조를 결합한 수정용 검토다. 확신이 낮거나 출처를 이번에 확보하지 못한 지적은 표에 명시했다. 인용한 학설은 새로 확신을 높이지 말고 원문 출처·범위를 보존할 것.

참고로 漢字ペディア는 음부의 소리를 가타카나로 표시하고 음의 변화를 설명하는 방침을 밝힌다: [漢字ペディア・字源欄の方針](https://www.kanjipedia.jp/include/popup_kanji.html). 가타카나가 적혀 있다고 현재 보이는 모든 부품을 그 음으로 독립해서 읽는다는 뜻은 아니다.
