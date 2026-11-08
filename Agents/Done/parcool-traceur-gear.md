---
status: done
task: parcool-traceur-gear
branch: claude/parcool-traceur-gear
with:
pr: 1122
mods: Powers
versions: 0.21.54
started: 2026-11-07
owns:
  - src/main/java/com/jaymospowers/freerun/gear/
  - src/main/resources/assets/jaymospowers/lang/parts/freerun-gear.json
  - src/main/resources/assets/jaymospowers/textures/entity/equipment/humanoid/traceur_boots.png
  - src/main/resources/assets/jaymospowers/textures/item/traceur_boots.png
---
# ParCool track B part 2: traceur gloves and boots

**Task:** Andy's picks from the ParCool checklist (track B part 2; the moves landed in #1118). Traceur gloves stop
the wall-slide hand burn via `WallSlide.addHandGuard` (the hook landed in #1116); traceur boots soften landings and
help the parkour moves, tuning as `static final` constants. Craftable, own textures, items registered like the rest
of Powers.

## Plan
- `freerun/gear/TraceurGloves`: wrist bauble registering `WallSlide.addHandGuard` at init
- `freerun/gear/TraceurBoots`: FEET-slot item, `SAFE_FALL_DISTANCE` + `JUMP_STRENGTH` tuning constants
- `lang/parts/freerun-gear.json`, baubles wrist tag, crafting recipes, item models + 16x16 textures
- `JaymosPowers.onInitialize` calls both inits; version bump to 0.21.54 (#1114 took 0.21.53); MODLOG entry

## Notes
- Gloves on `Baubles.Kind.WRIST` ("gloves and claws on the wrist" per ArtifactBaubles); the wall-slide burn is checked
  server-side, where the synced worn list is present
- Boots: attribute modifiers via `Equippable` FEET; own `EquipmentAsset` (`equipment/traceur_boots.json`) so worn boots draw their texture on the feet; `TraceurBoots.wears(player)` checks the feet slot

## Done
Worn traceur boots now draw a complete boot: side faces painted edge to edge over the foot only (no see-through slits down the sides, no thigh-high wrap), with a solid sole and top face, in our own dark brown with a black sole edge — no ParCool art. Build `sh ./gradlew build` passes and `tools/pr_checks.py` is green. Not tested: playtested in game (Andy merges only on his word; merged = compiled, not played).
- 2026-11-08 12:44 PR #1122 is merged into master; moved to Agents/Done.
