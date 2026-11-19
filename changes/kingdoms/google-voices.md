# narrator voice lines from Google Cloud Text-to-Speech
- **The narrator's missing lines can now be voiced by a Google voice**: `tools/voices/google_voices.py` reads the same
  lang keys as `make_voices.py` and writes the same `.ogg` clips and sounds.json entries, spoken by a Google US English
  man (Chirp 3 HD "Charon" by default; `--audition` makes samples of nine voices to pick from). It makes only clips that
  don't exist yet, so the lines the narrator already reads keep his voice; `--redo` remakes them all. The key comes from
  the `GOOGLE_TTS_API_KEY` environment variable and is never stored. Clips it makes are listed in `google_clips.txt`.
- **The faith page can be voiced**: its question, its line underneath and each faith's tale are read once their clips
  exist (they were always silent). So are the retold birthplaces (a Frostborn foundling's born-on-Hail tale).
- A story line with no clip is no longer registered or listed in sounds.json (38 tales were listed with no file).
- `make_voices.py --sounds` no longer wipes Kingdoms' other sounds: it only adds, changes or drops voice-line entries
  (it used to write a sounds.json holding nothing but voices).
- Built; no clips made yet (waiting for the Google key); not played.
