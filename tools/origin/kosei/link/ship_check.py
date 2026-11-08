#!/usr/bin/env python3
"""The checks of PartsTest.shippedOrigins on app/src/main/assets/origin.tsv, for a machine without the Android SDK
(lengths in UTF-16 units, as Kotlin counts them). Prints OK or the failing lines. The full test still runs in CI."""
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import rules  # noqa: E402

TYPES = {"상형", "지사", "회의", "형성", "회의 겸 형성", "가차", "국자", "생략형", "불명"}
JARGON = ["신자체", "정자", "說文", "설문", "갑골", "금문", "본자", "이체", "속자", "가차", "생략형", "회의", "형성", "상형",
          "지사", "소리 겸 뜻", "설이 갈림"]
UNIT = re.compile(r"^([\^~]?)([^\[\]()]+?)(?:\[([^\]]+)])?(?:\(([^()]*)\))?$")


def main():
    path = os.path.join(rules.ASSETS, "origin.tsv")
    lines = open(path, encoding="utf-8").read().splitlines()
    bad = []
    keys = [l.split("\t")[0].encode("utf-8") for l in lines]
    bad += [lines[i] for i in range(1, len(keys)) if not keys[i - 1] < keys[i]]
    for line in lines:
        f = line.split("\t")
        origin, memo, kosei = f[1].strip(), (f[2].strip() if len(f) > 2 else ""), (f[3].strip() if len(f) > 3 else "")
        t = origin.split(":", 1)[0] if ":" in origin and len(origin.split(":", 1)[0]) <= 8 else ""
        body = origin.split(":", 1)[1].strip() if t else origin
        if t not in TYPES:
            bad.append("유형: " + line)
        if rules.u16(origin) > 120 or rules.u16(memo) > 50:
            bad.append("길이(%d, %d): %s" % (rules.u16(origin), rules.u16(memo), line))
        if [w for w in JARGON if w in body]:
            bad.append("용어: " + line)
        if kosei and kosei != "-":
            for raw in kosei.lstrip("!").strip().split(" + "):
                m = UNIT.match(raw.strip())
                if not m:
                    bad.append("구성 형식: " + line)
                    break
                role = (m.group(3) or "").strip().split(" ")[0]
                # (The test checks roles and note lengths only on lines that are not written freely.)
                if not kosei.startswith("!") and (role not in ("", "뜻", "소리", "뜻·소리", "모양", "기호") or
                                                  rules.u16((m.group(4) or "").strip()) > 16):
                    bad.append("구성 역할/설명: " + line)
    kanji, _, _, _, joyo = rules.load_all()
    have = {l.split("\t")[0] for l in lines}
    if len(joyo) != 2136 or [c for c in joyo if c not in have]:
        bad.append("상용한자 2,136자가 다 있지 않음")
    for b in bad:
        print(b)
    print("OK" if not bad else "%d건" % len(bad))
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
