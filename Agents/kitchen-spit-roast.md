---
status: in-review
task: kitchen-spit-roast
branch: claude/kitchen-spit-roast
pr: 1123
mods: kitchen
versions: 0.5.87
started: 2026-11-07
claimed_by: hermes-b
owns:
  - kitchen/src/main/java/com/jaymoskitchen/cooking/spit/
  - kitchen/src/client/java/com/jaymoskitchen/client/spit/
  - kitchen/src/main/resources/assets/jaymoskitchen/lang/parts/spit.json
---

# Spit roast over an open fire

**Task:** Add a stick-crafted spit block that goes over lit Kitchen campfires/heat sources, holds a fish or animal carcass turning over the flames, cooks it over time with visible colour change (raw -> cooked -> burnt), lets players rip cooked chunks off by hand (animated tearing) or take the whole cooked product when done. Tuning as static final constants.

## Plan
- Review fixes (from hermes-fixer heads): SpitRip payload registration, chunks counted before the carcass leaves the hand, cooked drops via cookedOf, 8-tick tear shake countdown — all applied and committed.
- Merged origin/master into claude/kitchen-spit-roast; docs/codemap/kitchen.md regenerated via python tools/codemap.py (no changes needed).
- sh ./gradlew build in kitchen/ — BUILD SUCCESSFUL; python tools/pr_checks.py — passed (22 changed files).
- git push origin claude/kitchen-spit-roast — branch at 8d7e00c20; PR #1123 updated.
- vault.py --set kitchen-spit-roast pr=1123 status=in-review — done.

## Notes
- Coordinate with fishing-real-tug (#1114) only through Kitchen's fish items; don't edit its files.
- Not tested in game: compile-checked and ./gradlew build passes only; did not boot the client or roast anything on a spit.

## Done
What a player will see: a stick-crafted spit block over a lit fire roasting a fish or carcass that changes colour as it cooks, with tear-off cooked chunks and a cooked-product take. What wasn't tested: in-game behaviour — not booted.
