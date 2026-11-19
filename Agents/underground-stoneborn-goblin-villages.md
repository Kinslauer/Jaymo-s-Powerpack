---
status: stalled
task: underground-stoneborn-goblin-villages
branch: claude/underground-stoneborn-goblin-villages
with: 
pr: 
mods: Kingdoms
versions: 
started: 2026-11-19
owns:
  - kingdoms/src/main/java/com/jaymoskingdoms/Havens.java
  - kingdoms/src/main/java/com/jaymoskingdoms/Settlers.java
  - kingdoms/src/main/java/com/jaymoskingdoms/Camps.java
  - kingdoms/src/main/java/com/jaymoskingdoms/GoblinVillages.java
---
# Underground Stoneborn holds and goblin villages (plan with Andy first)

**Task:** Plan underground Stoneborn holds cut into rock and goblin villages near them in deep caves/deepslate layer, with occasional spider-kin beastfolk settlers. Need to settle with Andy where "down there" is first.

## Plan
- [ ] Ask Andy where "down there" is (Overworld deep caves Y -64 to 0, or under mountains)
- [ ] Review Lore.md for Stoneborn lore ("raised under the mountains", "The silk deeps", "The Beneath")
- [ ] Review codemap entries for Havens, HavenBuildings, HavenGrowth, GoblinVillages, GoblinBuildings, Camps.spawnCamps, Settlers.kindFor, HeritageVariants.ARACHNID
- [ ] Create plan split into PR-sized tracks as in docs/Plan-0.8.md
- [ ] Write (new) Lore.md paragraph for each placement for Andy to approve
- [ ] Implement cave-floor finder for ShutIn.openGround and GoblinVillages.spot (currently use heightmap)

## Notes
- Background: Lost Cities layer removed from stack (now: high sky, Aether, Cloud Sea, Overworld, Nether)
- Stoneborn holds should be raised by code with new building designs (not converting ordinary villages)
- Underground goblin villages may rise near any Stoneborn settlement down there
- Spider beastfolk settle only into these underground settlements; names need Andy's approval
- HeritageVariants.ARACHNID currently only Land.DARK; Land.DEEP exists

## Done
- 2026-11-19 15:36 No sign of work for 30 min: marked stalled (whoever ran it stopped).
