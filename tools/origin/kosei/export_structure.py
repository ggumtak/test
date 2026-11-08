#!/usr/bin/env python3
"""Writes current_kanji_structure.md at the repository root: how the card's four rows (부수, 구성, 유래, 외우기) are
shown and made, the formats they are stored in, and every row of data, for an outside review. Code and briefs are
copied verbatim; the data tables show each row as the card does, and the stored lines follow unchanged.

Usage: export_structure.py"""
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, "..", "..", ".."))
sys.path.insert(0, HERE)
from convert import load  # noqa: E402
from export_md import kosei_text, find_radical, old_kosei, cell, LABELS, UNIT  # noqa: E402

ASSETS = os.path.join(ROOT, "app", "src", "main", "assets")
SRC = os.path.join(ROOT, "app", "src", "main", "java", "com", "ggumtak", "hanja")
FENCE = "`````"


def text(rel):
    return open(os.path.join(ROOT, rel), encoding="utf-8").read()


def lines(rel, start, end):
    """Lines start..end (1-based, inclusive) of a file."""
    return "".join(open(os.path.join(ROOT, rel), encoding="utf-8").readlines()[start - 1:end])


def block(title, rel, body, lang=""):
    return "### %s\n\n`%s`\n\n%s%s\n%s%s\n\n" % (title, rel, FENCE, lang, body.rstrip("\n"), FENCE)


def span(rel, first, last):
    """The lines from the one matching [first] through the one matching [last] (regexes)."""
    all_ = open(os.path.join(ROOT, rel), encoding="utf-8").readlines()
    a = next(i for i, l in enumerate(all_) if re.search(first, l))
    b = next(i for i in range(a, len(all_)) if re.search(last, all_[i]))
    return a + 1, b + 1, "".join(all_[a:b + 1])


def radical_card(ch, p, rads, spec):
    """The 부수 row as LookupActivity.radicalText writes it (without the Hangul pronunciation of the kana, which
    the app adds from KanaHangul)."""
    if not p or not p[1]:
        return "", ""
    r = find_radical(rads.get(p[1], []), p[2], p[3] if len(p) > 3 else "")
    if not r:
        return "", ""
    shape = "曰" if p[1] == "73" else (p[2] if p[2] and p[2] == r[1] else r[1])
    meaning = r[5] if len(r) > 5 else ""
    s = "%s %s%s · %s" % (shape, r[4], "(%s)" % meaning if meaning else "", r[3])
    plist = [x.split("/") + [""] * (4 - len(x.split("/"))) for x in p[4].split()] if len(p) > 4 and p[4] else []
    units = []
    if spec and spec != "-":
        for raw in spec.lstrip("!").split(" + "):
            m = UNIT.match(raw.strip())
            if m:
                units.append((m.group(1), m.group(3) or ""))
    full = []
    if spec and spec != "-":
        for raw in spec.lstrip("!").split(" + "):
            m = UNIT.match(raw.strip())
            if m:
                full.append((m.group(1), (m.group(3) or "").split(" ")[0], m.group(4) or ""))
    if units:
        # Kosei.radicalOnlySound: not when the radical's own part gives the meaning as well (酒's 酉 · 뜻·소리).
        in_sound = any(mark in ("^", "~") and "소리" in role and not (mark == "^" and "뜻" in role)
                       for mark, role in units)
    else:
        in_sound = any("p" in x[2] and ("r" in x[2] or "R" in x[2]) for x in plist)
    here = radical_here(full, r[4], meaning)
    if shape == ch:
        s += " · 글자 자체가 부수"
    elif in_sound:
        s += " · 사전 분류용(여기서는 소리 쪽)"
    elif here:
        s += " · 여기서는 '%s'" % here
    return s, shape


CHANGED = re.compile(r"(변한|줄인|바뀐|바꾼|바꿔 쓴) 꼴")


def radical_here(units, korean, meaning):
    """Kosei.radicalHere: what the 구성 line calls the radical's part when the radical's name does not say it."""
    words = lambda t: [w for w in re.split(r"[ ·,()]", t) if w and all("가" <= c <= "힣" for c in w)]  # noqa: E731
    u = next((x for x in units if x[0] == "^"), None)
    if not u or not u[2]:
        return None
    if u[1] not in ("", "모양", "기호") and not CHANGED.search(u[2]):
        return None
    names = words(korean) + words(meaning)
    for w in words(u[2]):
        for n in names:
            if w in n or n in w or (len(w) >= 2 and len(n) >= 2 and w[:2] == n[:2]):
                return None
    return u[2]


def meaning_of(rads, form):
    """Radical.meaningOf: the one plain meaning radicals.tsv gives the shape, or None (none, or lines disagree)."""
    ms = {x[5] for lines in rads.values() for x in lines if x[1] == form and len(x) > 5 and x[5]}
    return ms.pop() if len(ms) == 1 else None


def parts_card(ch, p, kanji, rads):
    """The 구성 row the card builds from parts.tsv when there is no written one (LookupActivity.partsText): each
    part, the radical's starred, with its plain meaning: the radical's from its name line, a radical-shaped part's
    from radicals.tsv (辶 가다·길), else its first 훈; then 부수 / 소리 / 안에 부수 X / 안에 소리 X."""
    from export_md import first_hun
    plist = [x.split("/") + [""] * (4 - len(x.split("/"))) for x in p[4].split()] if len(p) > 4 and p[4] else []
    if len(plist) < 2:
        return "—"
    r = find_radical(rads.get(p[1], []), p[2], p[3] if len(p) > 3 else "") if p[1] else None
    shape = ("曰" if p[1] == "73" else (p[2] if r and p[2] == r[1] else r[1])) if r else p[2]
    out = []
    for el, orig, flags, inner in plist:
        radical, sound = "r" in flags, "p" in flags
        if radical:
            gloss = ((r[5] if len(r) > 5 and r[5] else r[4]) if r else "")
        else:
            gloss = meaning_of(rads, el) or first_hun(kanji.get(el)) or (
                first_hun(kanji.get(orig)) if orig and "+" not in orig else "")
        notes = [x for x in (gloss,) if x]
        if radical and sound:
            notes.append("부수")
        if sound:
            notes.append("소리")
        if "R" in flags and shape:
            notes.append("안에 부수 %s" % shape)
        if "P" in flags:
            notes.append("안에 소리 %s" % inner if inner else "일부가 소리")
        out.append(("*%s*" % el if radical else el) + ("(%s)" % " · ".join(notes) if notes else ""))
    return " + ".join(out)


def unvoiced(r):
    """Parts.unvoiced: a reading without its voicing marks (バン → ハン), so 反 and 板 are one series."""
    import unicodedata
    return unicodedata.normalize("NFC", "".join(c for c in unicodedata.normalize("NFD", r) if c not in "\u3099\u309a"))


def family_card(ch, kanji, parts, spec=""):
    """The 소리 계열 row as LookupActivity.familyText writes it: "青(푸를 청) 계열: 清 静 情 精  セイ" (without the
    Hangul pronunciation and the "비교 ▸" button), or "—"."""
    p = parts.get(ch)
    if not p or len(p) < 7 or not p[6]:
        return "—"
    ons = lambda c: [x.strip("-") for x in (kanji.get(c, [""] * 4)[3] or "").split("・") if x.strip("-")]  # noqa
    mine = {unvoiced(x) for x in ons(ch)}
    items = []
    for m in p[6]:
        r = next((x for x in ons(m) if unvoiced(x) in mine), None)
        if r:
            items.append((m, r))
    if not items:
        return "—"
    plist = [x.split("/") + [""] * (4 - len(x.split("/"))) for x in p[4].split()] if p[4] else []
    sound = [x[0] for x in plist if "p" in x[2]]
    head, shape = "", None
    if sound:
        shape = "+".join(sound)
        hun = ""
        if spec and spec != "-":
            # The name the 구성 line gives the sound part (familyHead), when short and without kanji.
            for raw in spec.lstrip("!").split(" + "):
                m = UNIT.match(raw.strip())
                if m and m.group(2) == shape and "소리" in (m.group(3) or ""):
                    n = m.group(4) or ""
                    hun = n if len(n.encode("utf-16-le")) // 2 <= 8 and not any(
                        "\u2e80" <= c <= "\u9fff" or ord(c) > 0xffff for c in n) else ""
        elif len(sound) == 1 and shape in kanji:
            k = kanji[shape]
            hun = re.split(r", | / ", (k[10] if len(k) > 10 and k[10] else k[1]) or "")[0].strip()
        head = "%s%s 계열: " % (shape, "(%s)" % hun if hun else "")
    shown = [x for x in items if x[0] != shape] or items
    same = len({r for _, r in shown}) == 1
    body = " ".join(m if same else m + r for m, r in shown)
    return head + body + ("  " + shown[0][1] if same else "")


def main():
    kanji = load(os.path.join(ASSETS, "kanji.tsv"))
    parts = load(os.path.join(ASSETS, "parts.tsv"))
    origin = load(os.path.join(ASSETS, "origin.tsv"))
    rads = {}
    for line in open(os.path.join(ASSETS, "radicals.tsv"), encoding="utf-8"):
        if line.strip() and not line.startswith("#"):
            f = line.rstrip("\n").split("\t")
            rads.setdefault(f[0], []).append(f)
    joyo = [c for c, f in kanji.items() if len(f) > 6 and f[6].isdigit() and 1 <= int(f[6]) <= 8]
    out = []
    w = out.append
    w("# 한자 카드의 네 줄(부수·구성·유래·외우기): 현재 구조와 전체 데이터\n\n")
    w("이 파일은 `tools/origin/kosei/export_structure.py`가 저장소의 현재 상태(브랜치 `ccr-2183b9b2-mwd49r` 기준)에서 "
      "그대로 뽑은 것입니다. 코드·지침·데이터는 고치거나 줄이지 않았습니다. 데이터 표는 카드가 보여 주는 모양으로 "
      "풀어 쓴 것이고, 그 아래에 저장된 원본 줄을 그대로 붙였습니다.\n\n")
    w("## 목차\n\n1. 어디에 무엇이 있나\n2. 화면에 보여 주는 로직(원본 코드)\n3. AI 설명 프롬프트(원본)\n"
      "4. 데이터를 만든 로직과 지침(원본)\n5. 데이터 스키마(필드와 형식)\n6. 상용한자 %d자 전체 데이터\n"
      "7. 상용한자 밖의 글자(부수·자동 구성만 있음)\n\n" % len(joyo))

    # 1
    w("## 1. 어디에 무엇이 있나\n\n")
    w("| 무엇 | 파일 | 내용 |\n|---|---|---|\n")
    w("| 부수 번호·모양·위치, 부품, 획 | `app/src/main/assets/parts.tsv` | `tools/build_parts_data.py`가 KANJIDIC2·Unihan·KanjiVG·AnimCJK로 만든 자료(13,434자) |\n")
    w("| 부수 이름표 | `app/src/main/assets/radicals.tsv` | 214부수와 변형·위치별 일본식/한국식 이름(손으로 작성) |\n")
    w("| 유래·외우기·구성(상용한자) | `app/src/main/assets/origin.tsv` | `tools/origin/merge.py`가 작성본과 모든 검토 수정본을 합쳐 만든 2,136줄 |\n")
    w("| 훈음·음독·뜻 | `app/src/main/assets/kanji.tsv` | `tools/build_kanji_data.py`가 만든 자료(13,436자) |\n")
    w("| 화면 표시 | `LookupActivity.kt`(addPartRows, radicalText, partsText, koseiText), `Parts.kt`(Parts, Radical, Origin, Kosei), `PartsStore.kt` | 아래 2장 |\n")
    w("| 상용한자 밖 글자의 외우기 | `KanjiText.kt`의 `SYSTEM_PROMPT` 【외우기】 | AI가 답할 때만, 아래 3장 |\n\n")
    w("카드 순서(위에서 아래로): 부수 → 구성 → 소리 계열 → 유래 → 외우기. 상용한자는 네 줄이 모두 기기 안의 자료이고, "
      "상용한자 밖의 글자는 부수와 자동 구성(부품과 그 훈음)만 보입니다. 구성의 부수 부품은 붉은색, 소리 부분은 "
      "파란색이고, 이 파일에서는 부수 부품을 `*별표*`로 표시했습니다. 부수 줄의 일본어 이름 뒤에는 앱이 한글 발음을 "
      "붙이는데(さんずい(산즈이)), 이 파일의 표에는 넣지 않았습니다.\n\n")

    # 2
    w("## 2. 화면에 보여 주는 로직(원본 코드)\n\n")
    rel = "app/src/main/java/com/ggumtak/hanja/LookupActivity.kt"
    a, b, body = span(rel, r"private fun readingRow\(", r"^    }$")
    w(block("LookupActivity.readingRow (줄 하나: 이름 + 내용), %d–%d행" % (a, b), rel, body, "kotlin"))
    a, _, _ = span(rel, r"\* What helps the kanji stick", r"x")
    _, b, _ = span(rel, r"private fun firstHun\(", r"^    }$")
    w(block("LookupActivity: addPartRows · radicalShape · radicalText · partsText · koseiText · firstHun, %d–%d행"
            % (a, b), rel, lines(rel, a, b), "kotlin"))
    for name in ("Parts.kt", "PartsStore.kt"):
        rel = "app/src/main/java/com/ggumtak/hanja/" + name
        w(block(name + " (전체)", rel, text(rel), "kotlin"))
    rel = "app/src/main/java/com/ggumtak/hanja/MainActivity.kt"
    a, b, body = span(rel, r"부수를 붉은색, 소리 부분을 파란색", r"\"\s*\)|\"\)|\.trimIndent|\"$")
    w(block("MainActivity: 사용법 화면의 설명 문구, %d–%d행" % (a, b), rel, body, "kotlin"))

    # 3
    w("## 3. AI 설명 프롬프트(원본)\n\n상용한자는 카드에 외우기가 있어 AI가 다시 쓰지 않고, 상용한자 밖의 어려운 "
      "글자만 AI 설명의 【외우기】에서 다룹니다.\n\n")
    rel = "app/src/main/java/com/ggumtak/hanja/KanjiText.kt"
    a, b, body = span(rel, r"private const val FORMAT", r"private const val FORMAT")
    w(block("KanjiText.FORMAT, %d행" % a, rel, body, "kotlin"))
    a, b, body = span(rel, r"const val SYSTEM_PROMPT", r"leave it out instead of guessing")
    w(block("KanjiText.SYSTEM_PROMPT, %d–%d행" % (a, b), rel, body, "kotlin"))

    # 4
    w("## 4. 데이터를 만든 로직과 지침(원본)\n\n")
    w("데이터는 앱이 실행 중에 만들지 않습니다. Claude가 여러 차례 쓰고 검토한 결과 파일을 `tools/origin/merge.py`가 "
      "정해진 순서로 덮어써 `origin.tsv`를 만듭니다. 아래는 처음 작성 지침부터 가장 최근 작업(2026-11-08, `link/`)의 "
      "지침까지와 병합·검사 코드 원본입니다.\n\n")
    for title, rel in (
            ("처음 작성 지침: 유래·외우기 (BRIEF.md)", "tools/origin/BRIEF.md"),
            ("처음 검토 지침 (REVIEW.md)", "tools/origin/REVIEW.md"),
            ("2차 검토 지침 (REVIEW2.md)", "tools/origin/review2/REVIEW2.md"),
            ("쉬운 말 지침 (PLAIN.md)", "tools/origin/plain/PLAIN.md"),
            ("구성 줄 작성 지침 (kosei/BRIEF.md)", "tools/origin/kosei/BRIEF.md"),
            ("구성 줄 검토 지침 (kosei/REVIEW.md)", "tools/origin/kosei/REVIEW.md"),
            ("유래 쉬운 말 지침 (kosei/easy/BRIEF.md)", "tools/origin/kosei/easy/BRIEF.md"),
            ("네 줄 작업: 작성 지침 (kosei/link/BRIEF.md)", "tools/origin/kosei/link/BRIEF.md"),
            ("네 줄 작업: 검토 지침 (kosei/link/REVIEW.md)", "tools/origin/kosei/link/REVIEW.md"),
            ("네 줄 작업: 2차 점검 지침 (kosei/link/CRITIC.md)", "tools/origin/kosei/link/CRITIC.md"),
            ("네 줄 작업: 2차 확인 지침 (kosei/link/VERIFY.md)", "tools/origin/kosei/link/VERIFY.md"),
            ("네 줄 작업: 결과와 판정 (kosei/link/README.md)", "tools/origin/kosei/link/README.md")):
        if os.path.exists(os.path.join(ROOT, rel)):
            w(block(title, rel, text(rel), "markdown"))
    for title, rel in (
            ("병합: origin.tsv를 만드는 코드 (merge.py)", "tools/origin/merge.py"),
            ("병합: 구성 줄과 이후 보정 적용 (kosei/merge.py)", "tools/origin/kosei/merge.py"),
            ("구성 줄 형식과 검사 (kosei/kosei.py)", "tools/origin/kosei/kosei.py"),
            ("네 줄 작업: 자동 점검 규칙 (kosei/link/rules.py)", "tools/origin/kosei/link/rules.py"),
            ("네 줄 작업: 검사기 (kosei/link/check.py)", "tools/origin/kosei/link/check.py"),
            ("부수·부품·획 자료를 만드는 코드 (build_parts_data.py)", "tools/build_parts_data.py")):
        w(block(title, rel, text(rel), "python"))

    # 5
    w("## 5. 데이터 스키마(필드와 형식)\n\n")
    w("모든 파일은 UTF-8, 탭 구분, 한 줄에 한 글자이며 UTF-8 바이트 순으로 정렬되어 있습니다(앱이 이진 탐색).\n\n")
    w("### origin.tsv (상용한자 2,136줄)\n\n| 열 | 이름 | 형식 | 예(海) |\n|---|---|---|---|\n")
    sea = origin["海"]
    w("| 1 | 한자 | 한 글자 | 海 |\n")
    w("| 2 | 유래 | `유형: 본문`. 유형은 상형·지사·회의·형성·회의 겸 형성·가차·국자·생략형·불명 중 하나이고, 앱은 그림·기호·뜻+뜻·뜻+소리·빌린 글자·일본에서 만든 글자·줄인 글자·유래 불확실로 바꿔 보여 줌(`Origin.LABELS`). 120자(UTF-16) 이하 | %s |\n" % cell(sea[1]))
    w("| 3 | 외우기 | `장면 → 뜻`, 50자(UTF-16) 이하, 부품은 `이름(모양)` | %s |\n" % cell(sea[2]))
    w("| 4 | 구성 | 부품을 ` + `로 잇고 각 부품은 `모양[역할 읽기](설명)`. `^` 부수 부품, `~` 부수를 품은 부품, 앞의 `!`는 KanjiVG 부품과 맞추지 않고 자유롭게 쓴 줄, `-`는 구성 줄 없음, 빈칸은 부품이 하나뿐. 역할: 뜻·소리·뜻·소리·모양·기호, 소리 뒤에 음독(가타카나). 설명 16자 이하 | %s |\n\n" % cell(sea[3]))
    w("### parts.tsv (13,434줄)\n\n| 열 | 이름 | 형식 |\n|---|---|---|\n")
    w("| 1 | 한자 | 한 글자 |\n| 2 | 부수 번호 | 강희 부수 번호(KANJIDIC2 classical, 없으면 Unihan) |\n")
    w("| 3 | 부수 모양 | 이 글자에 쓰인 모양(氵) 또는 빈칸 |\n| 4 | 부수 위치 | left·right·top·bottom·kamae·tare·nyo 또는 빈칸 |\n")
    w("| 5 | 부품 | 공백 구분, 각 `모양/원형/표시[/안쪽]`; 표시 r 부수, p 소리, R 부수를 품음, P 소리를 품음 |\n")
    w("| 6 | 획 | `|` 구분, 각 획은 색 표시(r·p·-) + SVG 경로 |\n| 7 | 소리 계열 | 같은 소리 부분의 상용한자 최대 5자 |\n")
    w("| 8 | 획 종류 | f = AnimCJK 붓 외곽선 |\n| 9 | 획 시작점 | `x,y;x,y…` |\n| 10 | 획 중심선 | `|` 구분 |\n\n")
    w("이 파일의 7장에는 1–5열만 넣었습니다(6·8–10열은 획 그림 좌표라 학습 내용이 아님).\n\n")
    w("### radicals.tsv (부수 이름표)\n\n| 열 | 이름 |\n|---|---|\n| 1 | 부수 번호 |\n| 2 | 모양 |\n"
      "| 3 | 위치(빈칸이면 어디서나) |\n| 4 | 일본식 이름 |\n| 5 | 한국식 이름 |\n| 6 | 뜻(카드에 괄호로) |\n\n")
    w("앱은 그 글자의 모양과 위치가 맞는 줄, 없으면 위치 없는 같은 모양 줄, 없으면 같은 모양, 없으면 그 부수의 기본 줄을 고릅니다"
      "(`Radical.find`).\n\n")
    w(block("radicals.tsv (전체)", "app/src/main/assets/radicals.tsv", text("app/src/main/assets/radicals.tsv")))
    w("### kanji.tsv에서 카드가 쓰는 열\n\n| 열 | 이름 |\n|---|---|\n| 1 | 한자 |\n| 2 | 훈음(libhangul) |\n"
      "| 3 | 한국 정자 |\n| 4 | 음독 |\n| 5 | 훈독 |\n| 6 | 획수 |\n| 7 | 학년(1–6, 8 = 중학) |\n| 8 | 영어 뜻 |\n"
      "| 9 | 일본어에서 쓰는 뜻(한국어) |\n| 10 | 영어 뜻의 한국어 |\n| 11 | 카드에 보이는 훈음 |\n\n")
    w("### 앱 안의 데이터 구조(Kotlin)\n\n`Parts`, `Radical`, `Origin`, `Kosei.Unit`의 필드는 2장의 Parts.kt 원본에 있습니다.\n\n")

    # 6
    w("## 6. 상용한자 %d자 전체 데이터\n\n" % len(joyo))
    w("### 6-1. 카드에 보이는 모양\n\n순서는 kanji.tsv(코드값) 순입니다. 구성의 `*별표*`는 부수 부품(카드에서 붉은색)입니다. "
      "구성이 `—`이면 카드에 구성 줄이 없습니다.\n\n")
    w("| 한자 | 훈음 | 학년 | 부수 | 구성 | 소리 계열 | 유래 | 외우기 |\n|---|---|---|---|---|---|---|---|\n")
    for c in joyo:
        k, p, o = kanji[c], parts.get(c), origin.get(c)
        hun = k[10] if len(k) > 10 and k[10] else k[1]
        grade = "중학" if k[6] == "8" else "%s학년" % k[6]
        spec = o[3] if o and len(o) > 3 else ""
        radical, shape = radical_card(c, p, rads, spec)
        plist = [x.split("/") + [""] * (4 - len(x.split("/"))) for x in p[4].split()] if p and len(p) > 4 and p[4] else []
        if spec == "-":
            kosei = "—"
        elif spec and (len(plist) >= 2 or spec.startswith("!")):
            kosei = kosei_text(spec, shape)
        else:
            kosei = parts_card(c, p, kanji, rads)
        t = o[1] if o else ""
        typ, body = (t.split(":", 1) if ":" in t[:10] else ("", t))
        yurae = ("%s: %s" % (LABELS.get(typ, typ), body.strip())) if typ else t
        memo = o[2] if o and len(o) > 2 else ""
        w("| %s | %s | %s | %s | %s | %s | %s | %s |\n" % (c, cell(hun), grade, cell(radical), cell(kosei),
                                                         cell(family_card(c, kanji, parts, spec)), cell(yurae),
                                                         cell(memo) or "—"))
    w("\n### 6-2. 저장된 원본: origin.tsv 전체(%d줄)\n\n열: 한자, 유래, 외우기, 구성(5장 참고).\n\n" % len(origin))
    w(FENCE + "tsv\n" + text("app/src/main/assets/origin.tsv").rstrip("\n") + "\n" + FENCE + "\n\n")
    w("### 6-3. 저장된 원본: 상용한자의 parts.tsv 1–5열(%d줄)\n\n열: 한자, 부수 번호, 부수 모양, 부수 위치, 부품.\n\n"
      % len(joyo))
    w(FENCE + "tsv\n")
    for line in open(os.path.join(ASSETS, "parts.tsv"), encoding="utf-8"):
        f = line.rstrip("\n").split("\t")
        if f[0] in origin:
            w("\t".join(f[:5]) + "\n")
    w(FENCE + "\n\n")

    # 7
    others = [line.rstrip("\n").split("\t") for line in open(os.path.join(ASSETS, "parts.tsv"), encoding="utf-8")]
    others = [f for f in others if f[0] not in origin]
    w("## 7. 상용한자 밖의 글자(%d자): 부수·자동 구성만 있음\n\n" % len(others))
    w("이 글자들에는 유래와 외우기 데이터가 없습니다. 카드는 부수 줄과, 부품이 둘 이상이면 부품과 그 훈음으로 만든 구성 줄"
      "(`partsText`)만 보여 줍니다. 부수 줄이 비어 있으면 부수 번호가 없는 글자입니다.\n\n")
    w("| 한자 | 부수 | 자동 구성 | parts.tsv 1–5열 |\n|---|---|---|---|\n")
    for f in others:
        p = f + [""] * (5 - len(f))
        radical, _ = radical_card(p[0], p, rads, "")
        plist = [x.split("/") + [""] * (4 - len(x.split("/"))) for x in p[4].split()] if p[4] else []
        kosei = parts_card(p[0], p, kanji, rads)
        w("| %s | %s | %s | `%s` |\n" % (p[0], cell(radical) or "—", cell(kosei),
                                       cell("\t".join(p[:5])).replace("\t", " ⇥ ")))
    path = os.path.join(ROOT, "current_kanji_structure.md")
    with open(path, "w", encoding="utf-8", newline="\n") as fh:
        fh.write("".join(out))
    print(path, len(joyo), "상용한자,", len(others), "others")


if __name__ == "__main__":
    main()
