---
status: done
task: Stop spiders attacking in daytime
branch: claude/project-thread-sqg8eq
with: cloud
pr: 2118
mods: powers, kingdoms
versions: 
started: 2026-11-24
owns:
  - src/main/java/com/jaymospowers/core/LeafSpiders.java
  - kingdoms/src/main/java/com/jaymoskingdoms/wild/Tarantula.java
  - kingdoms/src/main/java/com/jaymoskingdoms/wild/SpiderKinds.java
---
# Spiders by day

**Task:** Andy: "spiders should not attack during the day anymore".

## Notes
- Causes found: LeafSpiders ambush (day or night), SpiderKinds flyer perch drop (day or night), Tarantula's always-on player/villager target goals.
- Checked and fine: SpiderMinds (all hunting gated on dark), VillagerHunters (dark), Stormie's Spiders 3.3.1 (only wraps leap goal + target memory, keeps vanilla's light rule).
