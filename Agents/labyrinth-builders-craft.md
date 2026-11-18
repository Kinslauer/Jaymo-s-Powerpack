---
status: done
task: labyrinth-builders-craft
branch: claude/labyrinth-builders-craft
with: 
pr: 1714
mods: kingdoms
versions: 
started: 2026-11-17
owns:
  - kingdoms/src/main/java/com/jaymoskingdoms/beasts/
  - kingdoms/src/main/resources (labyrinth-craft assets, lang part, recipes)
  - changes/kingdoms/labyrinth-builders-craft.md
---
# Labyrinth builders' craft

**Task:** Inbox `labyrinth-builders-craft`: compass, rune doors and tablets, bombardier beetles, colony and queen,
living braziers, the Afterblow. Kingdoms only (the beetle-gland flamethrower is a Kingdoms item, not Armory).

## Plan
- All six picks in one PR; reuses the crystal beetle model (recoloured, crystals removed) for the bombardier beetle.

## Notes
- Cannot run Gradle on this PC: build is checked by CI on the PR.

## Done
PR #1714, CI green (Kingdoms, Kitchen, Powers, change notes). All six picks, Kingdoms only.
- **Player sees:** labyrinth halls get braziers (about a third living, spitting fire); the minotaur's hall is shut behind rune gates; three tablets (Anthu, Kesh, Orrim) in chests in the other halls; the Afterblow on a pedestal behind. Labyrinth Compass (3 geared plates + compass + bronze ingot); plates from drowned-colonnade chests. Bombardier beetles in warm lands (crystal beetle model, crystals off, repainted), glands by sneaking with a glass bottle, Gland Flamer; colonies with a queen in great trees.
- **Not tested (not played):** doorway detection and gate placement in a real Epic labyrinth, colonnade plate drops (Moog ids unchecked; matches "colonnade" in structure or table name), compass needle, colony loop, flamer feel. The beetle-gland flamethrower is a Kingdoms item, not Armory.
