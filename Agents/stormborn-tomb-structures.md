---
status: in-review
task: stormborn-tomb-structures
branch: claude/stormborn-tomb-structures
with:
pr: 1137
mods: kingdoms
versions: Kingdoms 0.7.651
started: 2026-11-08
owns:
  - kingdoms/src/main/resources/data/jaymoskingdoms/structure/stormborn_tomb/ (new)
  - kingdoms worldgen data for the tomb (structure, structure_set, template_pool, biome tag)
---

# Stormborn Tomb Structures

**Task:** Port Stormborn Tomb structures from Dungeon Conquest: Stormborn Tomb (MIT) into Kingdoms for Minecraft 26.3, swapping all Supplementaries/Draugr Invasion/DungeonZ/FrogiLib blocks for vanilla or Kingdoms equivalents.

## Plan
- Download and examine Stormborn Tomb structure files from Modrinth (project `iMWTKEcK`, file "1.21.1 Nordic Structure 1.1.3")
- Extract .nbt structure files and identify all non-vanilla blocks/block entities/spawners
- Create swap list mapping each modded block to vanilla/Kingdoms equivalent
- Add structure .nbt files to kingdoms/data/jaymoskingdoms/structure/stormborn_tomb/
- Add worldgen: structure_set, template_pool, biome tag for cold/mountain biomes
- Upgrade DataVersion to 26.3 format
- Test with `/place structure` to verify all tombs load without missing blocks
- Document swap list in PR

## Notes
- MIT license: keep notice and credit Tada_no_ahiru
- Boss arena left clear for future Draugr-style boss task
- No-build rule is separate task (tomb-no-build-rule)

## Done
Structures compile and `/place structure` loads all 11 pieces without missing blocks. Worldgen placement in survival not tested. Loot tables contain modded item entries (Supplementaries bombs, Draugr Invasion weapons) that need vanilla/Kingdoms swaps in follow-up PR. PR #1137 opened for review. Kingdoms version bumped to 0.7.651.