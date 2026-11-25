---
status: running
task: mordhau-4-legs
branch: claude/mordhau-4-legs
with: claude (T3 thread, Sonnet 5.5)
pr: 
mods: kingdoms, powers
versions: 
started: 2026-11-25
owns:
  - kingdoms/tools/gen_heraldry_legs.py (new: every new or re-cut piece of this track, previews)
  - kingdoms/src/main/resources/assets/jaymoskingdoms/bbmodels/heraldry_<this track's new pieces>.json, their textures, icons, item and item-model files, recipes
  - kingdoms/src/main/resources/assets/jaymoskingdoms/lang/parts/heraldry-legs.json (new), lang keys of the new HOSE place
  - kingdoms/src/main/java/com/jaymoskingdoms/Harness.java (the new HOSE place on the end of Slot, partsOf, layers)
  - kingdoms/src/main/java/com/jaymoskingdoms/HeraldryArmour.java (one separate block at the end of register())
  - kingdoms/src/main/java/com/jaymoskingdoms/HarnessMenu.java, kingdoms/src/client/java/com/jaymoskingdoms/client/HarnessScreen.java (the new place's slot)
  - kingdoms/src/main/java/com/jaymoskingdoms/GuardKit.java, McaBridge.java, HeraldryWear.java (only where the new place must be handled)
  - kingdoms/src/client/java/com/jaymoskingdoms/client/HeraldryLayer.java, UnderArmourLayer.java (hose on the legs, new leg/foot models)
  - src/main/java/com/jaymospowers/tinkering/smithing/HeraldryForging.java (new makes)
  - kingdoms/src/main/resources/data/jaymospowers/tags/item/{cloth,mail,plate}_armor.json
  - changes/kingdoms/claude-mordhau-4-legs.md, changes/powers/claude-mordhau-4-legs.md
  - E:/Jaymo Vault Live/Assets/mordhau-4-legs/ (previews, row table)
---
# Mordhau harness 4: waist, legs and feet (and a new HOSE place)

**Task:** Inbox `mordhau-4-legs`: Andy's 15 picked rows (faulds and skirts, thighs, shins, feet, and a new HOSE place for hose and breeches).

## Plan
- Starts after track 3's PR is up; this branch stacks on claude/mordhau-3-body (shared harness files, the padded skirt that track 3's jupon row names).
- Same method as track 3: gen_heraldry_legs.py, a row table and worn previews in Assets/mordhau-4-legs/.

## Notes
- Queued behind mordhau-3-body in the same T3 thread.

## Done
