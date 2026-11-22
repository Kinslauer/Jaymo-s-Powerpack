---
status: done
task: medieval-patch10-temple-patrons
branch: claude/medieval-patch10-temple-patrons
with: 
pr: 1964
mods: kingdoms
versions: kingdoms 0.11.50
started: 2026-11-19
owns:
  - kingdoms/src/main/java/com/jaymoskingdoms/oldage/People.java
  - kingdoms/src/main/java/com/jaymoskingdoms/oldage/OldAge.java
  - kingdoms/src/main/java/com/jaymoskingdoms/oldage/OldRuins.java
  - kingdoms/src/main/java/com/jaymoskingdoms/oldage/OldRuler.java
  - kingdoms/src/main/java/com/jaymoskingdoms/Temples.java
  - kingdoms/src/main/resources/data/jaymoskingdoms/worldgen/structure_set/old_ruins.json
  - kingdoms/src/main/resources/data/jaymoskingdoms/worldgen/structure/anvil_ruins.json
  - kingdoms/src/main/resources/data/jaymoskingdoms/worldgen/structure/deep_ruins.json
  - kingdoms/src/main/resources/data/jaymoskingdoms/worldgen/structure/grove_ruins.json
  - kingdoms/src/main/resources/data/jaymoskingdoms/worldgen/structure/truth_ruins.json
  - kingdoms/src/main/resources/data/jaymoskingdoms/loot_table/chests/old_age/anvil_house.json
  - kingdoms/src/main/resources/data/jaymoskingdoms/loot_table/chests/old_age/anvil_tomb.json
  - kingdoms/src/main/resources/data/jaymoskingdoms/loot_table/chests/old_age/deep_house.json
  - kingdoms/src/main/resources/data/jaymoskingdoms/loot_table/chests/old_age/deep_tomb.json
  - kingdoms/src/main/resources/data/jaymoskingdoms/loot_table/chests/old_age/grove_house.json
  - kingdoms/src/main/resources/data/jaymoskingdoms/loot_table/chests/old_age/grove_tomb.json
  - kingdoms/src/main/resources/data/jaymoskingdoms/loot_table/chests/old_age/truth_house.json
  - kingdoms/src/main/resources/data/jaymoskingdoms/loot_table/chests/old_age/truth_tomb.json
  - kingdoms/src/main/resources/data/jaymoskingdoms/tags/worldgen/biome/has_structure/anvil_ruins.json
  - kingdoms/src/main/resources/data/jaymoskingdoms/tags/worldgen/biome/has_structure/deep_ruins.json
  - kingdoms/src/main/resources/data/jaymoskingdoms/tags/worldgen/biome/has_structure/grove_ruins.json
  - kingdoms/src/main/resources/data/jaymoskingdoms/tags/worldgen/biome/has_structure/truth_ruins.json
  - changes/kingdoms/claude-medieval-patch10-temple-patrons.md
---
# Patch 10: which patron each old-age temple calls

**Task:** Pair each old-age temple site with Andy's assigned patron (Hearthkeeper→Hearth-Warden etc.) instead of drawing the patron at random, in cv-avatars / cv-ruins (Kingdoms).

## Plan
- [x] Fix patron assignments in oldage/People.java (SUNSTEP->LANTERN_WARDEN, MOUND->CROWNED_SUN, COURT->ASH_WHISPERER)
- [x] Add 4 new peoples to oldage/People.java (ANVIL, DEEP_FOLK, GROVE, TRUTH_FOLK)
- [x] Add Build methods in OldRuins.Build for new monument types
- [x] Add relic registrations in OldAge.java
- [x] Add structure JSONs, biome tags, structure set entries, loot tables
- [x] Build check passes
- [ ] Commit, push, create PR
- [ ] Set task status in-review

## Notes
- Temples.draw() on the branch already changed from weighted random to deterministic first-fit (pre-existing work)
- OldRuler.move() switch statement doesn't cover new enum values (falls through - basic melee only)
- Builds successfully with `sh ./gradlew build` in kingdoms/
- Loot tables created for all 8 ruin variants (house + tomb for each people)

## Done
What a player will see, and what wasn't tested.
- 2026-11-20 02:36 No sign of work for 30 min: marked stalled (whoever ran it stopped).
- 2026-11-22 14:29 PR #1964 is merged into master; moved to Agents/Done.
