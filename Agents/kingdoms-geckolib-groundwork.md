---
status: running
task: kingdoms-geckolib-groundwork
branch: claude/eager-lehmann-ef2930
with: 
pr: 
mods: Kingdoms
versions: 
started: 2026-11-08
owns:
  - kingdoms/build.gradle (GeckoLib dependency)
  - kingdoms/src/main/java/com/jaymoskingdoms/inhabitants/
  - kingdoms/src/client/java/com/jaymoskingdoms/client/inhabitants/
  - kingdoms/src/main/resources/assets/jaymoskingdoms/geckolib/
  - kingdoms/src/main/resources/assets/jaymoskingdoms/textures/entity/inhabitants/
  - kingdoms/src/main/resources/assets/jaymoskingdoms/lang/parts/inhabitants.json
  - docs/AI-HANDBOOK.md (GeckoLib section)
---
# Kingdoms GeckoLib groundwork + tomb undead

**Task:** GeckoLib 5.5.7 in Kingdoms with one example mob as the pattern to copy, plus our own Nordic tomb undead
(tomb skeleton, draugr, draugr lord boss) for the Stormborn Tomb (PR #1137 owns the structure).

## Plan
- Show Andy a look page for each mob first.
- Dependency, `inhabitants` package, example mob, handbook section, credits.
- Tomb undead with entity ids for the tomb thread's spawners.

## Notes
- GeckoLib 5.5.7 reads only `assets/<ns>/geckolib/models/**.geo.json` and `geckolib/animations/**.animation.json`
  (checked in the jar). PR #1146 (Bogre) puts its files in `geo/` and `animations/`: they need moving.

## Done
