---
status: running
task: 
branch: claude/project-thread-g31te8
with: cloud thread "Redesign the village golems"
pr: 
mods: kingdoms
versions: 
started: 2026-11-11
owns:
  - kingdoms/src/main/java/com/jaymoskingdoms/TownGolem.java
  - kingdoms/src/client/java/com/jaymoskingdoms/client/TownGolemRenderer.java
  - kingdoms/tools/gen_town_golems.py
  - kingdoms/art/town_golem_*.bbmodel
  - docs/TownGolems-Spec.md
---
# Village golem redesign

**Task:** Andy (2026-11-11): "The village golems all need redesigns, the throne golem NEEDS to turn into a throne among the other previous descriptors". Lamp warden, mender and throne golem each get their own GeckoLib model and keyframed animations; the throne golem folds into a real throne.

## Plan
- Three models from kingdoms/tools/gen_town_golems.py (own cubes and paint), GeckoLib geo + animations.
- TownGolem becomes a GeoEntity; throne fold/unfold are one-time animations.

## Notes
- 

## Done
