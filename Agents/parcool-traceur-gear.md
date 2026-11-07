---
status: in-review
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
- Boots: attribute modifiers via `Equippable` FEET, no custom `EquipmentAsset` (like zfighter training weights)

## Done
What a player will see, and what wasn't tested.
