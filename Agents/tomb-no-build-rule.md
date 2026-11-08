---
status: in-review
task: tomb-no-build-rule
branch: claude/tomb-no-build-rule
with: 
pr: 1138
mods: kingdoms
versions: kingdoms 0.7.650 on master (no version bump yet - taken at merge per workflow)
started: 2026-11-08
owns:
  - kingdoms/src/main/java/com/jaymoskingdoms/dungeon/NoBuildDungeons.java (new)
  - kingdoms/src/main/java/com/jaymoskingdoms/mixin/dungeon/ServerExplosionNoBuildMixin.java (new)
  - kingdoms/src/main/resources/jaymoskingdoms.dungeon.mixins.json (new)
  - kingdoms/src/main/resources/fabric.mod.json (edit: mixins list)
  - kingdoms/src/main/java/com/jaymoskingdoms/JaymosKingdoms.java (edit: import + register())
  - kingdoms/src/main/resources/data/jaymoskingdoms/tags/worldgen/structure/no_build_dungeons.json (new)
  - kingdoms/src/main/resources/data/jaymoskingdoms/tags/blocks/no_build_dungeons_breakable.json (new)
  - kingdoms/src/main/resources/assets/jaymoskingdoms/lang/parts/no_build_dungeons.json (new)
---
# Dungeons you can't dig through: the tomb no-build rule

**Task:** Andy (Stormborn Tomb thread, "we need it"): a DungeonZ-style no-build rule for Kingdoms - inside structures tagged `#jaymoskingdoms:no_build_dungeons`, players can't break or place most blocks; interacting still works (doors, chests, levers, buttons, pressure plates); player explosions don't break the dungeon walls; a short action-bar line (lang key `message.jaymoskingdoms.no_build_dungeons`) says why. Creative players and operators are never blocked; which blocks stay breakable (cobwebs, pots) is the block tag `no_build_dungeons_breakable`.

## Plan
- `com.jaymoskingdoms.dungeon.NoBuildDungeons` (new): `PlayerBlockBreakEvents.BEFORE` cancels breaking inside tagged structures; `UseBlockCallback` cancels placement of block items inside them; both skip creative/ops and breakable-tagged blocks
- `mixin.dungeon.ServerExplosionNoBuildMixin` (new): `ModifyVariable` on `ServerExplosion.interactWithBlocks` removes in-dungeon blocks from player blasts
- Structure tag `no_build_dungeons` (new, empty - stormborn-tomb-structures/PR #1137 fills it when it lands) and block tag `no_build_dungeons_breakable`
- Lang part `no_build_dungeons.json` (new)
- Register in `JaymosKingdoms.onInitialize()`; mixin config added to `fabric.mod.json`

## Notes
- Tag starts empty; PR #1137 adds the Stormborn tomb structure ids when it merges.
- Not tested in game yet.

## Done
What a player will see, and what wasn't tested.
