---
status: done
task: ore-hearts
branch: claude/ore-hearts
with:
pr:
mods: powers
versions:
started: 2026-11-09
owns:
  - src/main/java/com/jaymospowers/core/wounds/
  - src/main/java/com/jaymospowers/core/hearts/
  - src/main/resources/data/jaymospowers/worldgen/
  - src/main/resources/data/jaymospowers/loot_tables/
  - src/main/resources/assets/jaymospowers/lang/
---

# Ore Hearts

**Task:** Add rare heart crystal ore in deep deepslate that drops heart crystals. Using a heart crystal grants a permanent half-heart (up to 10 half-hearts / 5 full hearts above base), shown as a faint extra run on the health bar. Hearts are lost on death but each heart grants one extra sure wake (death burns a heart instead of rolling). Reincarnation clears all hearts and extra max health.

## Plan
- Create HeartCrystalItem (consumable, grants permanent half-heart)
- Create HeartCrystalOre and HeartCrystalOreDeepslate blocks with worldgen
- Add attachment to track permanent half-hearts count
- Integrate with Downed/Mortality systems for extra sure wakes
- Add health bar rendering for the faint extra run
- Add loot tables for ore drops
- Add lang entries

## Notes
- Andy's design answers (2026-11-08):
  1. Found hearts are LOST on death, but each grants an extra life (sure wake)
  2. Cap: 5 full hearts (10 half-hearts) above base, shown as faint extra run on health bar
- Art IDs: jaymospowers:heart_crystal_ore, heart_crystal_ore_deepslate, heart_crystal
- Art is with modeller (ore-hearts-art task)

## Notes

- 2026-11-09 07:55 Apollo's run (`inbox-builder-a`, 05:55-07:20) died on a model-provider stall, not on the code:
  the stream went silent at 06:05:57 and Hermes killed it at 07:18:35. The claim was released at 07:53 and the
  task is back to `open` in the Inbox; this note stays here as the WIP marker.
- Apollo's partial work is committed and pushed on `claude/ore-hearts` as `fa72e7dea` (worktree `E:/wt/ore-hearts`):
  `core/wounds/HeartCrystals.java` plus hooks in `JaymosPowers`, `Downed` and `Mortality`. It does not compile yet
  (one missing `Identifier` import). Full remaining-work checklist is in the task's `## Stall report`.

## Done
- 2026-11-09 Andy: remove the old note. Moved to Done; the task itself is open in the Inbox and the WIP is on `claude/ore-hearts` (fa72e7dea).
