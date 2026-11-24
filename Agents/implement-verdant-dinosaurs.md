---
status: in-review
task: Verdant dinosaurs (from the Dino-26.3 Amber Age jar), spawned by codex-reference-dinos-venom
branch: t3/implement-verdant-dinosaurs
with: 
pr: 2099
mods: powers
versions: 
started: 2026-11-24
owns:
  - src/main/java/com/jaymospowers/dino/ (new)
  - src/client/java/com/jaymospowers/client/dino/ (new)
  - src/main/resources/assets/jaymospowers/dino/ , textures/entity/dino/ , textures/item/dino/ , sounds/dino/ , items/ and models/item/ dino entries (new)
  - src/main/resources/data/jaymospowers/ dino loot tables, recipes, advancements, tags (new)
  - src/main/resources/jaymospowers.dino.mixins.json (new, only if needed)
  - the dino lines in JaymosPowers.java / JaymosPowersClient.java (registration only, at the end)
  - changes/powers/implement-verdant-dinosaurs.md
  - Another track (venom-head) owns only the symbiote web head and client files; not touched here.
---
# Verdant dinosaurs

**Task:** Port Andy's Amber Age dinosaurs (nine dinos and a dodo) to native Fabric 26.3 and make them live naturally on the Verdant World only.

## Ownership permission

Andy confirmed the jar is his own mod made with Claude and GPT ("yes they were made with claude and gpt", Decisions.md 2026-11-24, "Reference mods are Andy's own creations"). Its ARR metadata is the generated default, so reuse of its code, models, textures and sounds is authorized. Provenance is recorded in `src/main/resources/licenses/PROVENANCE_amber_age_dinosaurs`. Source was recovered by decompiling the jar's classes with Vineflower 1.12.0 (from the Gradle cache); the `.kmdl` models and `.spec` files are used as shipped.

## Plan

- Recover source, port the Forge glue (registries, config, network, spawn, HUD, keys) to Fabric.
- Keep `.kmdl` + `.spec` rigs, procedural animation, all mechanics (taming, torpor, darts, saddles, riding, levels, packs, hunting, fossil skeletons, skulls).
- Spawn only in the eight Verdant biomes (biome keys, no tag leakage).

## Notes

- Forge `PartEntity` multipart hitboxes have no vanilla/Fabric equivalent (vanilla supports only the Ender Dragon's parts); see Done for how it was handled.

## Done

- Source ported: all 9 dinosaurs + dodo, rigs, mechanics (taming, torpor, darts, saddles, riding, levels, packs, hunting, fossil skeletons, skulls)
- Spawn restricted to eight Verdant biomes (biome keys, no tag leakage)
- Client/common source split: client-only classes under `src/client/java/com/jaymospowers/client/dino/`, nothing in `src/main` referencing client classes
- Provenance recorded in `src/main/resources/licenses/PROVENANCE_amber_age_dinosaurs`
- Change note: `changes/powers/implement-verdant-dinosaurs.md`
- PR #2099 updated after merging origin/master; CI checks pass (master has since advanced with unrelated changes)
