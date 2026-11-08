---
status: done
task: kingdoms-geckolib-groundwork
branch: claude/kingdoms-geckolib-tomb-undead
with: 
pr: 1149
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
PR #1149. GeckoLib 5.5.7 in Kingdoms, inhabitants/ with the barrow beetle (example) and the draugr; the silver skeleton
(coffin rise) and Skeleton Lord (staff swing, slam, Grave Call) moved to GeckoLib; handbook section; tools/bbmodel_geckolib.py.
Tomb ids: silver_skeleton, draugr, skeleton_lord {dormant:1b}. Built and pr_checks pass; not played, no server boot
(the dev server's EULA file was refused).
- 2026-11-08 15:34 PR #1149 is merged into master; moved to Agents/Done.
