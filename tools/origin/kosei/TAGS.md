# Brief: make the stroke drawing's colours agree with the 구성 line

A Korean kanji-learning app draws each kanji stroke by stroke: the radical's strokes red, the sound part's strokes
blue, the rest black. Under it the 구성 line names the parts with their roles (뜻 / 소리 / 뜻·소리 / 모양 / 기호),
written to agree with the 유래 line (how the kanji formed). After a full review some 구성 lines were rewritten
freely and the colours no longer follow them. Your job: for each kanji of your list, make the blue strokes be
exactly the strokes of the parts the 구성 line gives a 소리 role (소리 or 뜻·소리), and nothing else blue.

- `python3 -I /home/user/test/tools/origin/kosei/gemini/show.py 漢 …` shows the 구성 line as stored and as shown, the
  유래, and every stroke with its current colour (r red, p blue, - black) and the KanjiVG parts it sits in. Use the
  part names and the stroke order (Japanese stroke order) to decide which strokes belong to each 구성 unit.
- Keep red (r) strokes red: the radical stays red even when it also gives the sound.
- A 소리 unit's strokes that are not r become p; a stroke of a unit without a 소리 role that is p becomes -.
- When the 구성 line itself seems wrong (a sound role the 유래 does not support), do not change colours to fit it:
  leave the kanji and say so in your reply.
- Write `tags-N.tsv` in /home/user/test/tools/origin/kosei/: one line per kanji you change,
  `kanji<TAB>tags<TAB>reason` where tags is one letter per stroke in writing order (r, p or -), exactly as many as
  the kanji's strokes in show.py. Kanji already right: no line. The reason is short Korean.
- Check your lines: the number of letters equals the number of strokes; every r stays r.
Reply in Korean: how many kanji you changed, which you left and why (one line each for any doubtful). No git; do not
edit other files.
