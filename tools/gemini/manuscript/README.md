# 상용한자 원고 전체 다시 쓰기 (Gemini 앱)

사용자 요청(2026-11-14): 구성 이름·유래·외우기 원고를 처음부터 끝까지 Gemini가 쓰게 할 것. Gemini 앱은 저장소를 못 읽으므로
여기 파일을 올려 주고 답을 받아 옵니다.

| 파일 | 내용 |
|---|---|
| `00_작성지침.md` | Gemini에게 주는 지침(무엇을, 답 형식, 칸별 규칙, 예시) |
| `names.txt` | 뜻 부품의 지금 이름표(같은 부품은 같은 이름으로) |
| `batch-01.txt` ~ `batch-11.txt` | 2,136자를 학년·획수 순으로 200자씩. 한자마다 훈음·읽기·구성 부품·지금 유래·지금 외우기 |
| `make_batches.py` | 위 묶음을 만드는 스크립트(`python3 tools/gemini/manuscript/make_batches.py`) |
| `check_manuscript.py` | 돌아온 답 검사, `--accept`면 link 층 줄 출력 |

## 돌아온 답 넣기

1. 답을 `returned-NN.txt`로 저장, `python3 tools/gemini/manuscript/check_manuscript.py returned-*.txt --accept`.
2. 오류 줄은 Gemini에게 고쳐 달라고 하거나 지금 줄을 유지. 유래는 사실이므로 새로 들어온 주장이 없는지 검토(독립 검토자).
3. 통과한 줄을 `tools/origin/kosei/link/link-manuscript.tsv`로 넣고 `merge.py`의 tail에 추가, `python3 tools/origin/merge.py`,
   `ship_check.py`, 테스트.
