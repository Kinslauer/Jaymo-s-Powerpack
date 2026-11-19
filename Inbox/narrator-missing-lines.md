---
status: open
for: hermes
from: cloud-claude
asked: 2026-11-19
claimed_by: 
pr: 
---

# Narrator: voice the 200 silent intro lines in his own voice (VoxCPM2)

**Andy's words (2026-11-19):** on the Google voices, "none of these are old and grizzled enough for the intro" and "all of these are awful ... not our narrator"; then "il make the voices overnight with hermes so it doesnt use claude usage" and "can you put that task on the board for a bot to pick up?"

**What's wanted:** the 200 life-story lines the narrator doesn't read yet (62 plain tales, the faith page: its question, the line under it and each faith's tale, and 126 retold birthplaces and upbringings across the bloods), made in the **existing narrator's VoxCPM2 voice**, so the whole intro is one voice. No Google voice. No code changes: Kingdoms already plays a story line once its clip is in the jar (`Voices.register`).

**How:** in a worktree off master, on a machine with the narrator's saved voice:
1. Check `tools/voices/refs/narrator.wav` exists (gitignored; on Andy's PC under `E:/Origins Mod/tools/voices/refs/`). Copy it into the worktree's `tools/voices/refs/`. **If it's missing, stop**: without it the script designs a new voice from the description and it won't match. Add a Notes question and set `status: blocked`.
2. `pip install voxcpm soundfile numpy audiotsm`, then `python tools/voices/make_voices.py narrator`. It makes only the missing clips (into `kingdoms/src/main/resources/assets/jaymoskingdoms/sounds/voice/narrator/`) and lists them in Kingdoms' `sounds.json`. VoxCPM2 needs well over 9 GB of memory (the cloud machine ran out); a graphics card makes it much faster. A stopped run picks up where it left off.
3. Listen to a few of the new clips beside an old one (for example `born_coast_tale.ogg`) to check they're the same voice.
4. Commit the clips and `sounds.json`, add `changes/kingdoms/<branch>.md` ("the narrator reads the rest of the life story": which lines, made with make_voices.py, not played), open one PR.

**Watch out:** PR #1782 (Google voices for MCA villagers and the Rig) also adds entries to Kingdoms' `sounds.json`. If it merged first and `sounds.json` conflicts, take master's file and rerun `python tools/voices/make_voices.py --sounds` (rewrites only the voice-line entries, keeps the rest).

**Rules:** CLAUDE.md as always: one PR, change note, no version bumps, never commit `refs/` or any key. No Gradle builds on Andy's PC (CI builds the PR). Say plainly in the PR that it hasn't been heard in game.
