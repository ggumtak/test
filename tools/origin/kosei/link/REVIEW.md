# Review: an independent second reader for link-N.tsv

Read BRIEF.md first: the goal, the four links, the picture test and the format are the same for you. A writer
went through batch N (in-N.txt) and wrote link-N.tsv (and maybe flags-N.tsv). You did not write it; your job is
to make the batch as good as it can be, not to defend it. The user will judge the card by one question: "Do the
four rows tell one story, and does the 외우기 make me see something that leads to the meaning?"

## Steps
1. `cp link-N.tsv draft-N.tsv` (and `cp flags-N.tsv draft-flags-N.tsv` if it exists) before you change anything.
   You edit link-N.tsv in place.
2. **Every row the writer changed** — compare old (in-N.txt) and new:
   - 유래 / 구성: is every fact, hedge, reading and type the same? Is it really closer to the other rows? If a fact
     changed, put the old line back (empty that column) or fix it. A 유래 changed only for taste: revert.
   - 외우기: apply the four links and the picture test like a learner who has never seen the kanji: look at the glyph,
     read the scene — can you point at each part in the glyph? Cover the meaning — does the scene lead to it? Is
     every word plain? Is it better than the old line, clearly? If not: revert (delete the row or empty the column)
     or write a better one. Watch for: parts not in the 구성; a part named differently from the 구성 note; a scene
     that contradicts the 유래; a meaning after `→` that the kanji does not have in Japanese; a list of parts with no
     action; two scenes glued together; Korean that reads like a translation.
3. **Every kanji the writer left alone** — read them all (not only those with 자동 점검 hints). Add a row for each
   current 외우기 that still fails a link or the picture test, and for each 유래/구성 pair that still names a part
   in two ways.
4. **Flags**: keep only those with real evidence, and add your own. Three columns: 한자, 부수|구성|유래, 문제와 근거.
5. Run `python3 -I /home/user/test/tools/origin/kosei/link/check.py N` until it prints OK; read the 참고 lines.
6. Reply in Korean, briefly: of the writer's rows how many you kept, improved, reverted; how many rows you added;
   the writer's most common mistake; flags kept/added; up to 5 kanji you are least sure about. Do not paste files.
