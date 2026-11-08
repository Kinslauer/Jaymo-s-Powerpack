---
status: done
task: stormborn-tomb-structures
branch: claude/stormborn-tomb-structures
with: claude (took over from hermes, 2026-11-08)
pr: 1137
mods: kingdoms
started: 2026-11-08
owns:
  - kingdoms/src/main/resources/data/jaymoskingdoms/structure/stormborn_tomb/
  - kingdoms/src/main/resources/data/jaymoskingdoms/worldgen/structure/stormborn_tomb.json, structure_set/stormborn_tomb.json
  - kingdoms/src/main/resources/data/jaymoskingdoms/tags/worldgen/biome/has_structure/stormborn_tomb.json
  - kingdoms/src/main/resources/data/jaymoskingdoms/loot_table/chests/stormborn_tomb_*
  - kingdoms/src/main/java/com/jaymoskingdoms/dungeon/StormbornTomb.java
  - changes/kingdoms/stormborn-tomb-structures.md
---

# Stormborn Tomb structures

**Task:** port Dungeon Conquest: Stormborn Tomb (Tada_no_ahiru, MIT) into Kingdoms with nothing of Supplementaries,
Draugr Invasion, DungeonZ or FrogiLib left.

## Done (Claude, 2026-11-08)
- Redone from the original jar (Hermes' last commit had cut boss/main to near-empty files; worldgen JSON was wrong).
- The tomb lived in DungeonZ's own dimension: rooms were structure voids (now air) and the layout is ~270 blocks
  across, past a jigsaw's 128 reach, so `dungeon/StormbornTomb` (structure type `jaymoskingdoms:stormborn_tomb`) places
  the pieces at fixed offsets. New `descent.nbt`: shaft + tunnel from the barrow's old portal to the entrance hall.
- Swaps listed in the PR. Mobs: vanilla stand-ins (no Hollow mob exists), kept (PersistenceRequired).
- Built; pr_checks pass; dev-server /place + /locate checked (see PR "Checked"). Not walked through in game.

## For the tomb-undead / boss thread
- Mob ids live in the templates' entity lists (skeleton/stray/zombie/bogged/wither_skeleton): swap ids there.
- Boss spot: `boss.nbt` local (41, 17, 24); boss loot chest (42, 18, 17), table
  `jaymoskingdoms:chests/stormborn_tomb_boss_loot`; exit x 43-46, y 18-21, z 16 (open now).
- The scripts that made the templates (convert, room fill, layout) are in the session's scratchpad, not the repo.

## Notes
- 2026-11-08 15:34 PR #1137 is merged into master; moved to Agents/Done.
