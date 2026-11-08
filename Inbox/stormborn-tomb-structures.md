---
status: in-review
for: hermes
from: coordinator
asked: 2026-11-08
thread: Claude reviewer session (Andy: Stormborn Tomb, "we need it")
claimed_by: hermes-a
pr: 1137
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
- 2026-11-08 11:23 Review (head 482027e, coordinator): changes needed. 1) The tomb won't assemble: the jigsaw blocks inside the .nbt pieces still target pools and pieces in the nordic_structure namespace (e.g. nordic_structure:stormborn_tomb/stormborn_tomb_boss, nordic_structure:stormborn_tomb_main), but this PR put the pools and structures under jaymoskingdoms, so only the entrance generates. Either keep the original nordic_structure namespace for template_pool/ and structure/ files (and the start_pool), or rewrite every jigsaw pool/name/target string in the NBT with a real NBT editor. 2) Mods we don't have: the loot tables use mebahelcreaturesdraugr (ebony/ancient_nord bows, swords, axes, daggers) and supplementaries (bombs, music disc) items; in 26.3 one unknown item makes the whole table fail, so every tomb chest is empty. Swap them for our own or vanilla items (draugr gear -> our weapons or iron/diamond gear). nordic_structure_loot.json points at nordic_structure:chests/nordic_structure_loot, which doesn't exist. 3) The NBT also places mebahelcreaturesdraugr:skeleton_warrior entities (they won't spawn: replace with vanilla skeletons/strays or our own), supplementaries blocks (ash, dragon banner, hat stand: become air) and a dungeonz:spawn block (DungeonZ isn't installed: use a vanilla spawner or our own boss trigger). 4) Add the tomb structure id to kingdoms/.../tags/worldgen/structure/no_build_dungeons.json (from tomb-no-build-rule, merged in Kingdoms 0.7.651) and take Kingdoms 0.7.652 (master is 0.7.651 after #1138). 5) Add a kingdoms/MODLOG.md entry and credit/licence note for the MIT original. Then build, pr_checks, push, set status=in-review.
- 2026-11-08 12:46 Review (head 6aabeb7, coordinator; reviewer away): changes needed, round 2. Fixed: Kingdoms 0.7.652 and a MODLOG entry. Still open from round 1, unchanged: 1) the jigsaw blocks in the .nbt pieces still target nordic_structure:stormborn_tomb/... pools and nordic_structure:stormborn_* names while the pools live under jaymoskingdoms, so only the entrance generates. Easiest fix: move the template_pool/ and structure/ files (and start_pool) to the nordic_structure namespace instead of editing NBT. 2) All 8 loot tables still use mebahelcreaturesdraugr and supplementaries items (17 + 11 references), and nordic_structure_loot.json references a missing table: every chest will be empty. 3) The NBT still places mebahelcreaturesdraugr:skeleton_warrior, supplementaries blocks, dungeonz:spawn and neoforge:attachments data. 4) The tomb id is not in tags/worldgen/structure/no_build_dungeons.json. 5) New: the branch conflicts with master (kingdoms/gradle.properties / MODLOG): merge origin/master first. If round 3 still has 1-3, it goes to Andy as blocked.
- 2026-11-08 08:20 hermes-a's last run stopped before finishing; handed back as changes so it can carry on.
