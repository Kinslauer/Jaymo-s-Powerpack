---
status: claimed
for: hermes
from: coordinator
asked: 2026-11-08
thread: Claude reviewer session (Andy: Stormborn Tomb, "we need it")
claimed_by: hermes-a
pr: 
suggested_owns:
  - kingdoms/src/main/resources/data/jaymoskingdoms/structure/stormborn_tomb/ (new)
  - kingdoms worldgen data for the tomb (structure, structure_set, template_pool, biome tag)
---
# Stormborn Tomb: the tomb structures in Kingdoms

**Andy's words (2026-11-08):** "get them, we need it" (Dungeon Conquest: Stormborn Tomb).

**Why a port:** Stormborn Tomb and all its required mods stop at Minecraft 1.21.1, and the pack is 26.3, so the jars
can't be added. Stormborn Tomb is MIT ("Feel free to use it in any modpacks"), so its tomb structures move into
Kingdoms.

**What's wanted:**
- Take the tomb structure files from Stormborn Tomb's Fabric 1.21.1 build (Modrinth project `iMWTKEcK`, file
  "1.21.1 Nordic Structure 1.1.3", MIT). Keep the MIT notice and credit Tada_no_ahiru.
- Every block, block entity and spawner from Supplementaries, Draugr Invasion, DungeonZ or FrogiLib must be swapped
  for vanilla or Kingdoms blocks (Supplementaries' licence and Draugr Invasion's All Rights Reserved mean none of
  their content comes along). List every swap in the PR.
- For now the tombs' undead are Kingdoms Hollows (spawners or structure entities); our own Draugr-style dead and the
  tomb boss come in later tasks, so leave a clear spot (the boss arena) for them.
- Worldgen: place the tombs in cold or mountain biomes like the original (Nordic, Skyrim-style), not too common.
- The structures must load on 26.3: check each template's DataVersion is upgraded, and that `/place structure`
  builds every tomb without missing blocks.

**PR:** what a player will find, the swap list, and "not tested in game" (or what `/place` showed).

## Notes
- The no-build rule inside tombs is its own task (`tomb-no-build-rule`).
