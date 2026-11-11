---
status: in-review
task: 
branch: claude/project-thread-udod01
with: Claude (cloud thread)
pr: 1320
mods: powers, kingdoms
versions: 
started: 2026-11-11
owns:
  - kingdoms/src/main/java/com/jaymoskingdoms/beasts/Labyrinths.java
  - src/main/java/com/jaymospowers/aether/dungeon/RuinBoss.java
  - src/main/java/com/jaymospowers/aether/dungeon/RuinPiece.java
---
# Epic Structures and the minotaur's labyrinths

**Task:** Andy linked Epic Structures: Igloo, Dungeons and Jungle Temples, and said the Epic dungeons should spawn the
minotaur instead of the slider dungeon.

## Plan
- All three are all rights reserved with modpack use invited, data-only jars with 26.3 Fabric builds. Dungeons was already
  in the pack; add Igloo and Jungle Temples to `packMods`, unchanged.
- Kingdoms `beasts/Labyrinths`: one minotaur in the biggest hall of every large Epic dungeon and 35% of medium ones, stood
  there when a player first comes near (structure tags `minotaur_labyrinths`, `minotaur_labyrinths_some`).
- Powers: Sentry Ruins build with the Slider only; `RuinBoss` stays for ruins already built with a minotaur.

## Notes
- Lore.md has a (new) paragraph for Andy to approve.

## Done
Compiled (Kingdoms and Powers); not played.
