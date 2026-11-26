---
status: in-review
task: structures-4-mohknee
branch: t3/mohknee-rare-structure-starts
with: 
pr: 2250
mods: kingdoms
versions: 
started: 2026-11-26
owns:
  - kingdoms/src/main/java/com/jaymoskingdoms/mixin/sailing/MohkneeStructureMixin.java
  - kingdoms/src/main/java/com/jaymoskingdoms/seas/Mohknee.java
  - changes/kingdoms/mohknee-rare-structure-starts.md
---
# Structures 4 (PR 1): rare mainland structure starts on Mohknee

**Task:** Replace MohkneeStructureMixin's hard ban on mainland villages and other mods' surface structures on Mohknee with rare deterministic valid starts, following Andy's ruling that only distance keeps mainland peoples rare there.

## Plan
- ✅ Replace the hard cancel in `MohkneeStructureMixin` on Mohknee land with tunable `static final` 1-in-N deterministic chunk rolls (`VILLAGE_KEEP_ONE_IN = 10`, `SURFACE_KEEP_ONE_IN = 12`), keeping the crossing-island duplicate-village guard intact.
- ✅ Update `Mohknee.java` javadoc to match.
- ✅ Add change note `changes/kingdoms/mohknee-rare-structure-starts.md`.
- ✅ Build `kingdoms/` and run `tools/pr_checks.py`.
- ✅ Open PR #2250 against `master` with follow-up tracks and `(new)` lore paragraph.

## Notes
- Taken over from Codex (usage limit) on branch `t3/mohknee-rare-structure-starts`, fast-forwarded to fresh `origin/master`.
- Follow-up tracks listed in PR #2250: Ashlands rock-iron barbarian gear, mixed pillager/barbarian bands (shared tactics/language), returning travellers' keepsakes.

## Done
Mainland villages and other mods' surface structures can now rarely generate on Mohknee (1 in 10 valid village starts, 1 in 12 other-mod surface structure starts) instead of being blocked outright, while crossing islands keep only their own Tideborn village. Built `kingdoms/` and passed `tools/pr_checks.py`; not booted or played in game (live Mohknee generation, `/locate`, and multiplayer not tested).
