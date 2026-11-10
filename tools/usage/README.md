# 읽기 순서와 읽기별 단어 (`app/src/main/assets/usage.tsv`)

카드의 음·훈 줄이 쓰는 자료입니다. 상용한자마다 상용한자표의 읽기를 자주 만나는 순서로 놓고, 읽기마다 그 읽기를
쓰는 흔한 단어와 한국어 뜻을 붙입니다. 무엇을 왜 바꿨는지는 저장소 `README.md`의 "읽기" 절에 있습니다.

## 파일

| 파일 | 내용 |
|---|---|
| `../build_usage_data.py` | 자료를 만드는 도구. 읽기 순서와 단어 고르는 규칙이 모두 여기 있습니다(첫머리 설명). |
| `in-N.tsv` | 뜻을 써야 했던 단어 묶음(단어, 읽기, JMdict 영어 뜻, JLPT 급수, 어느 한자·읽기의 예인지). 11은 규칙을 다듬은 뒤 추가된 14개. |
| `out-N.tsv` | 작성본(단어, 읽기, 한국어 뜻). 1–10은 Sonnet, 11은 직접 작성. |
| `fix-N.tsv` | 묶음별 독립 검토(Opus)가 고친 줄과 까닭. 모두 89줄. |
| `fix-final.tsv` | 검토자가 의심만 하고 남긴 줄 가운데 따로 판단해 고친 25줄. |
| `words.tsv` | `merge.py`가 위를 합친 결과(4,994개). `build_usage_data.py`가 읽습니다. |
| `BRIEF.md`, `REVIEW.md` | 작성·검토 지침. |
| `check.py` | 묶음의 형식 검사(줄 수·순서, 허용 글자, 길이, 뜻 둘까지). |

## 다시 만들기

```sh
# 원자료 (저장소에 넣지 않음)
curl -LO https://raw.githubusercontent.com/cjkvi/cjkvi-tables/master/joyo2010.txt
pip download jamdict-data==1.5 --no-deps && tar xzf jamdict_data-1.5.tar.gz \
  && xz -dk jamdict_data-1.5/jamdict_data/jamdict.db.xz          # → jamdict.db
mkdir jlpt && for n in 5 4 3 2 1; do curl -L -o jlpt/n$n.csv \
  https://raw.githubusercontent.com/stephenmk/yomitan-jlpt-vocab/main/original_data/n$n.csv; done

python3 tools/usage/merge.py                                       # out/fix → words.tsv
python3 tools/build_usage_data.py joyo2010.txt jamdict_data-1.5/jamdict_data/jamdict.db \
  app/src/main/assets/kanji.tsv jlpt app/src/main/assets/usage.tsv tools/usage/words.tsv --todo todo.tsv
```

`todo.tsv`가 비어 있지 않으면 규칙이나 원자료가 바뀌어 뜻이 없는 단어가 새로 뽑힌 것입니다. 그 줄을 새 `in-N.tsv`로
두고 `BRIEF.md`대로 `out-N.tsv`를 쓴 뒤(다른 사람이 `REVIEW.md`대로 검토) 위 두 명령을 다시 실행합니다.
`--why 明`을 붙이면 그 한자의 읽기마다 순위와 뽑힌 단어를 보여 줍니다.

## 알아 둘 점

- JLPT 급수는 공식 목록이 아니라 Jonathan Waller의 추정 목록입니다(시험 주관처는 단어 목록을 내지 않습니다). 순서를 정하는
  데만 쓰고 카드에는 급수를 적지 않습니다.
- 한 JMdict 항목에 표기가 여럿이면 급수가 흔한 표기 모두에 붙습니다(あける: 開ける·空ける·明ける). 그래서 明ける가 N5로
  잡혀 앞쪽에 옵니다.
- 단어가 없는 읽기 136개는 흔한 단어에 쓰이지 않는 읽기(今: キン, 耗: コウ)라 '읽기 더 보기' 안에 읽기만 나옵니다.
- 검사: `app/src/test/java/com/ggumtak/hanja/UsageTest.kt`가 상용한자 전부에 줄이 있는지, 형식, 대표 사례의 순서를 봅니다.
