#!/usr/bin/env python3
"""Bundles the repository into text files for the Drive folder's `0_저장소 사본` (제미나이_협업.md).

The Gemini app reads Google Docs in Drive, not GitHub and not a tree of .kt files, so the tracked files are
joined into a few dozen text bundles by area (each becomes one Google Doc), every file under a
"===== FILE: path =====" line. Plus 00 안내 (what is where) and a zip of each data file too large to read as a document, when it fits one upload.

    python3 tools/drive_snapshot.py <out dir>

Upload every file of <out dir> but manifest.json (create_file, text/plain; a zip unconverted) and trash the previous copies.
"""
import hashlib
import json
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
UPLOAD_MAX = 3_000_000  # bytes one Drive connector call can carry
LIMIT = 400_000  # characters per bundle: well under the 1.02M a Google Doc holds
# Too large to read as documents (stroke paths, the word dictionary): in the zip only.
ZIP_ONLY = {"app/src/main/assets/parts.tsv", "app/src/main/assets/words.tsv"}
# (bundle name, path prefixes); the first match wins, so the specific ones come first.
GROUPS = [
    ("01 문서와 설정", ["README.md", "CLAUDE.md", "제미나이_협업.md", ".claude/", ".github/", ".gitignore", "licenses/",
                   "build.gradle.kts", "settings.gradle.kts", "gradle.properties", "gradle/", "gradlew",
                   "app/build.gradle.kts", "app/proguard-rules.pro", "app/src/main/AndroidManifest.xml",
                   "app/src/main/res/"]),
    ("02 앱 코드", ["app/src/main/java/"]),
    ("03 앱 테스트", ["app/src/test/"]),
    ("04 앱 자료 kanji.tsv", ["app/src/main/assets/kanji.tsv"]),
    ("05 앱 자료 origin.tsv", ["app/src/main/assets/origin.tsv"]),
    ("06 앱 자료 그 밖", ["app/src/main/assets/"]),
    ("07 한자 구조 정리", ["current_kanji_structure.md"]),
    ("08 tools gemini", ["tools/gemini/"]),
    ("09 tools origin kosei", ["tools/origin/kosei/"]),
    ("10 tools origin", ["tools/origin/"]),
    ("11 tools 그 밖", ["tools/"]),
    ("12 그 밖", [""]),
]


def git(*args: str) -> str:
    return subprocess.run(["git", *args], cwd=ROOT, capture_output=True, check=True).stdout.decode()


def pieces(path: str, text: str) -> list[str]:
    """The file under its header, cut at line ends into parts of at most LIMIT characters."""
    lines = text.splitlines(keepends=True)
    parts, cur, size = [], [], 0
    for line in lines:
        if size + len(line) > LIMIT - 200 and cur:
            parts.append("".join(cur))
            cur, size = [], 0
        cur.append(line)
        size += len(line)
    parts.append("".join(cur))
    n = len(parts)
    return [f"===== FILE: {path}" + (f" ({i + 1}/{n})" if n > 1 else "") + " =====\n" + p +
            ("" if p.endswith("\n") else "\n") + "\n" for i, p in enumerate(parts)]


def main() -> None:
    out = Path(sys.argv[1])
    out.mkdir(parents=True, exist_ok=True)
    commit = git("rev-parse", "--short", "HEAD").strip()
    date = git("log", "-1", "--format=%cs").strip()
    files = git("ls-files", "-z").split("\0")[:-1]
    grouped: dict[str, list[tuple[str, str]]] = {name: [] for name, _ in GROUPS}
    binary, zipped, left_out = [], [], []
    for f in files:
        if f in ZIP_ONLY:
            continue
        try:
            text = (ROOT / f).read_text(encoding="utf-8")
        except UnicodeDecodeError:
            binary.append(f)
            continue
        name = next(n for n, prefixes in GROUPS if any(f == p or (p.endswith("/") and f.startswith(p)) or p == ""
                                                        for p in prefixes))
        grouped[name].append((f, text))

    index = []  # (document title, [paths])
    written = []
    for name, items in grouped.items():
        docs: list[tuple[list[str], list[str]]] = [([], [])]
        size = 0
        for path, text in items:
            for piece in pieces(path, text):
                if size + len(piece) > LIMIT and docs[-1][0]:
                    docs.append(([], []))
                    size = 0
                docs[-1][0].append(piece)
                if path not in docs[-1][1]:
                    docs[-1][1].append(path)
                size += len(piece)
        docs = [d for d in docs if d[0]]
        for i, (body, paths) in enumerate(docs):
            title = name + (f" ({i + 1}-{len(docs)})" if len(docs) > 1 else "")
            head = f"[저장소 사본] ggumtak/test · {title} · 커밋 {commit} ({date})\n파일 {len(paths)}개. 각 파일은 '===== FILE: 경로 =====' 줄로 시작합니다.\n\n"
            (out / f"{title}.txt").write_text(head + "".join(body), encoding="utf-8")
            written.append(f"{title}.txt")
            index.append((title, paths))

    # One connector call carries about 3 MB at most: a ZIP_ONLY file goes up zipped when it fits, else not at all.
    for f in sorted(ZIP_ONLY):
        name = f"큰 자료 {Path(f).name}.zip"
        (out / name).unlink(missing_ok=True)
        subprocess.run(["zip", "-q", "-9", str(out / name), f], cwd=ROOT, check=True)
        if (out / name).stat().st_size <= UPLOAD_MAX:
            written.append(name)
            zipped.append(f)
        else:
            (out / name).unlink()
            left_out.append(f)
    guide = [
        f"[저장소 사본] ggumtak/test · 00 안내 · 커밋 {commit} ({date})",
        "",
        "이 폴더는 GitHub 저장소 ggumtak/test(일본어 한자 학습 안드로이드 앱 'Hanja')의 읽기용 사본입니다.",
        "Gemini 앱이 GitHub을 못 읽어서, Claude가 저장소 파일을 영역별 문서로 묶어 올렸습니다.",
        "",
        "쓰는 법",
        "- 아래 목록에서 필요한 파일이 든 문서를 찾아 엽니다. 문서 안에서 각 파일은 '===== FILE: 경로 =====' 줄로 시작합니다.",
        "- 이 사본은 위 커밋 시점의 것입니다. 원본은 GitHub이고, 여기 문서를 고쳐도 앱에는 반영되지 않습니다.",
        "- 바꿀 내용은 '2_제미나이→클로드'에 답 문서로 넣습니다(00_약속의 규칙). 파일 경로와 바꿀 부분을 적어 주면 Claude가 검토해 저장소에 반영합니다.",
        "- 확장 영역 한자(𠂉, 𢦏 등)는 문서 변환에서 깨져 보일 수 있습니다.",
        "- 문서로 읽기에 너무 큰 자료: " + (", ".join(f"{f}는 '큰 자료 {Path(f).name}.zip'" for f in zipped) or "없음") + ".",
        "- 이 폴더에 없는 파일(GitHub에만 있음): " + (", ".join(left_out + binary) or "없음") +
        ". 올리기 한도(한 번에 약 3MB)를 넘거나 글이 아닌 파일입니다.",
        "",
        "문서별 파일 목록",
    ]
    for title, paths in index:
        guide.append("")
        guide.append(f"■ {title}")
        guide.extend("  " + p for p in paths)
    (out / "00 안내.txt").write_text("\n".join(guide) + "\n", encoding="utf-8")
    written.insert(0, "00 안내.txt")

    manifest = [{"file": w, "sha256": hashlib.sha256((out / w).read_bytes()).hexdigest(),
                 "bytes": (out / w).stat().st_size} for w in written]
    (out / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=1), encoding="utf-8")
    for m in manifest:
        print(f"{m['bytes']:>10}  {m['file']}")


if __name__ == "__main__":
    main()
