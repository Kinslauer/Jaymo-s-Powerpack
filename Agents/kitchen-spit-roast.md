---
status: blocked
task: kitchen-spit-roast
branch: claude/kitchen-spit-roast
with:
pr: 1123
mods: kitchen
versions: 0.5.87
started: 2026-11-07
claimed_by: hermes-fixer
owns:
  - kitchen/src/main/java/com/jaymoskitchen/cooking/spit/
  - kitchen/src/client/java/com/jaymoskitchen/client/spit/
  - kitchen/src/main/resources/assets/jaymoskitchen/lang/parts/spit.json
---

# Spit roast over an open fire

**Task:** Add a stick-crafted spit block that goes over lit Kitchen campfires/heat sources, holds a fish or animal carcass turning over the flames, cooks it over time with visible colour change (raw → cooked → burnt), lets players rip cooked chunks off by hand (animated tearing) or take the whole cooked product when done. Tuning as static final constants.

## Plan
- Read kitchen/ cooking/ (Station, Grill), client/ (GrillView, LarderGames), corpse/ (Corpses, CorpseKind) and the butcher table.
- Add spit block + block entity over a lit heat source (jaymoskitchen:heat_sources tag, lit).
- Spit-roast fish (from #1114 fishing) or Kitchen corpses with raw/cooked/burnt stages and colour change.
- Rip chunks off by hand (animated tearing, one cooked piece each) or take the whole cooked product when done.
- static final tuning constants for cook time, chunks per animal.
- Lang part spit.json; recipes; item/block models and textures; client spit view animation.
- Build: sh ./gradlew build in kitchen/; tools/pr_checks.py; version bump kitchen to next free number right before the PR.

## Notes
- Coordinate with fishing-real-tug (#1114) only through Kitchen's fish items; don't edit its files.
- Not started: nothing built or playtested yet.

## Done
What a player will see, and what wasn't tested.
