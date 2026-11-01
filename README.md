# 한자 바로보기

아무 앱에서 한자를 꾹 눌러 선택하고 텍스트 선택 메뉴의 **한자**를 누르면, 지금 화면 위에 카드가 뜹니다.
카드에는 선택한 단어(가나만 고르면 한글 발음도)와, 한자마다 한국 훈음(気 → 기운 기, 한국 정자 氣), 음독·훈독과
한글 발음(メイ(메이)), 획수, 학년이 나옵니다. 인터넷 없이 바로 보입니다.

- 한자 줄이나 **한자사전**을 누르면 네이버 한자사전, **일본어사전**을 누르면 네이버 일본어사전이 열립니다.
- **Anki 한 번 탭**: `app/src/main/assets/anki-tap.html`(앱의 'Anki용 코드 복사')을 카드 템플릿 맨 아래에 붙이면,
  AnkiDroid에서 한자·가타카나를 탭하는 순간 카드가 뜹니다(`hanja://lookup?q=…&s=…` 링크).
- **이 문장에서의 읽기**: Anki에서 탭했거나 두 글자 이상 선택하면 Claude(Haiku 5.5, 구조화 출력)가 문장을 보고 그 자리의
  발음과 한자별 읽기를 판정합니다. 화면 형식은 그대로이고, 머리의 읽기와 한자 줄의 "단어" 읽기만 그 결과로 바뀝니다
  (今日は雨 → きょう). 답은 기기에 저장해 같은 문장은 다시 묻지 않습니다.
- **Claude 설명**: 앱 아이콘에서 넣은 Claude API 키로 Haiku 5.5에 물어, 답을 카드 안에 바로 보여 줍니다.

## 설치

push할 때마다 GitHub Actions가 서명된 APK를 만들어 이 저장소의 `latest` 릴리스에 올립니다.
휴대폰에서 릴리스 페이지를 열고 `.apk`를 눌러 설치합니다(처음 한 번은 "출처를 알 수 없는 앱" 허용이 필요합니다).

## 구조

- `app/src/main/java/com/ggumtak/hanja/` — `LookupActivity`(선택 메뉴 카드), `MainActivity`(사용법·API 키),
  `KanjiStore`/`Kanji`/`KanjiText`(자료·순수 로직), `ClaudeClient`(스트리밍 요청). 외부 라이브러리는 쓰지 않습니다.
- `app/src/main/assets/words.tsv` — `tools/build_word_data.py`가 JMdict에서 만든 단어 읽기(部屋 → へや). 카드 머리에
  단어 읽기를, 한자 줄에 "이 단어에서 읽는 음"을 보여 주고, 단어 전체로 읽는 말(숙자훈)은 따로 알려 줍니다.
- `app/src/main/assets/kanji.tsv` — `tools/build_kanji_data.py`가 만든 자료(13,436자). 다시 만들 때 쓰는 원본과
  명령은 스크립트 첫머리에 적혀 있습니다.

## 자료 출처

- KANJIDIC2 — © Electronic Dictionary Research and Development Group, CC BY-SA 4.0 (일본어 읽기·획수·영어 뜻)
- JMdict — © Electronic Dictionary Research and Development Group, CC BY-SA 4.0 (단어 읽기)
- libhangul 한자 사전(`data/hanja/hanja.txt`) — © Choe Hwanjin, BSD 3-Clause (한국 훈음)
- Unicode Unihan Database — Unicode License (신자체 → 정자 대응)

`kanji.tsv`와 `words.tsv`는 KANJIDIC2·JMdict에서 파생한 자료이므로 CC BY-SA 4.0을 따릅니다.
