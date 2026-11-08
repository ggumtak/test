# Round 2 verify: a second reader for the critic's fixes

Read BRIEF.md first. A critic read the merged cards of batch N (round2/in-N.txt) and wrote round2/fix-N.tsv for
the lines it judged failing. Check **only those rows**:

1. `cp round2/fix-N.tsv round2/draft-N.tsv` (and the flags file, if any, to round2/draft-flags-N.tsv).
2. For each row: did the old line really fail (CRITIC.md's list)? If not, delete the row — a good line must not be
   churned. In particular, a row whose only reason is that the meaning word appears before the arrow is churn
   when the old scene pictured that meaning with its parts (CRITIC.md was made clearer on this after the critic
   started): delete it. If it did: is the new line clearly better and does it pass all four links, the picture test, the
   format and the plain-word rule? 유래/구성 columns: facts, hedges, readings, type unchanged? A changed 구성 note
   must be a 훈 listed in `부품의 훈음(자료)` or the 유래's own words (the checker prints a 참고 line otherwise).
   Improve or revert.
3. Flags: keep only those with real evidence.
4. Run `python3 -I /home/user/test/tools/origin/kosei/link/check.py N round2` until OK.
5. Reply in Korean, briefly: rows kept / improved / deleted, flags kept, up to 3 kanji you are least sure about.
