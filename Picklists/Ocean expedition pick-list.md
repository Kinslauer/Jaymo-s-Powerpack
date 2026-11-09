---
type: picklist
source: Ocean Worlds + Water World (Only Ocean)
url: https://www.curseforge.com/minecraft/mc-mods/ocean-worlds · https://www.curseforge.com/minecraft/data-packs/water-world-only-ocean
licence: Ocean Worlds: Forge 1.18–1.20, ideas only · Water World: datapack, 1.18.2–26.3, usable · verdict: mostly our own
status: waiting-for-picks
created: 2026-11-08
tags: [inbox, picklist, ocean]
---

# Ocean expedition pick-list

Andy: "I would like more water in the world, making the oceans an actual expedition, and making most sea life spawn further out. maybe tideborn settled islands and definitely more and larger ships."

## The two links, honestly

- **Ocean Worlds** (InfernalStudios) raises the sea level, makes the water surface wavy and non-block, and floods caves. Forge 1.18/1.19/1.20 only — no Fabric, no 26.3. Ideas only, and the wavy water is a renderer project, not a config.
- **Water World (Only Ocean)** (Celestial878) is a datapack that sets continentalness to −0.5, which removes all land above sea level. It works up to 26.3. It is the extreme end of the same dial we already own.

**The important find:** Kingdoms already overrides `data/minecraft/worldgen/density_function/overworld/continents.json` — the same file the datapack changes. Ours adds −0.06 to the continental noise. More water is that one number, and we can pick how much instead of taking all-or-nothing.

Tick what you want, add a note under any row, then set `status: picked`.

## The world

- [x] **Turn the water up** `S` ^oc-dial
  Move the continent offset in `kingdoms/.../worldgen/density_function/overworld/continents.json` from −0.06 toward something wetter. The linked datapack is −0.5 (no land at all); a middle value gives seas you cross and coasts you still land on.
  For us: one number, but it changes every new world we generate and moves where structures and biomes fall.
  Notes:

- [ ] **New worlds only, and say so out loud** `S` ^oc-worlds
  Existing chunks keep the land they generated; the seam where new terrain starts is visible and ugly. Decide: do we ship this as the default for new worlds and leave the current server alone?
  Notes:

- [x] **A higher, wavy sea** `L` ^oc-sealevel
  Water above Y63 whose surface is a gradient rather than flat blocks, with caves flooded beneath it.
  For us: this is the expensive one — it fights our water rendering (Streams Reflowing, shaders) and every mod that assumes flat water. Only if you want the ocean to be a place rather than a plane.
  Notes:

## Sea life

- [ ] **Life in the deep, quiet shallows** `M` ^oc-sealife
  Most fish and sea creatures spawn far from shore and in deep water, so a coastal walk is quiet and an expedition is where the life is.
  For us: spawn rules per biome and distance to land — Naturalist, Cube Animals and the pack's own fish are the stock.
  Notes:

- [ ] **Something worth sailing for** `M` ^oc-reward
  A reason to go out: deep-water fish, wrecks, a rare creature, an island that is not on any map.
  For us: the expedition needs a prize; distance alone is just swimming.
  Notes:

## People

- [ ] **Tideborn islands** `M` ^oc-islands
  Tideborn havens on islands — we already build them on stilts with a dock and a net loft, so the people exist; what is missing is islands for them to live on and boats to reach them.
  For us: `HavenBuildings` + `HavenGrowth` (the Tideborn dock) placed on island terrain.
  Notes:

- [ ] **Islands that are worth the crossing** `S` ^oc-island-content
  Hermits, court wizards, a wreck, a shrine — the things we already place inland, placed where only a boat reaches them.
  Notes:

## Ships

- [ ] **Bigger ships** `L` ^oc-bigships
  Shippy Ships gives us small craft; a proper ship — deck, hold, sails, a crew of one — is ours to build. Cannons we already have (`oldcannons`), so an armed hull is a step, not a new system.
  For us: a multi-block ship entity, or a much larger boat with a real deck you can stand on.
  Notes:

- [ ] **More ships, and ships that sail themselves** `M` ^oc-ships
  Variants (fishing boat, trader, galley) and NPC vessels at sea — traders you can hail, a wreck, something hostile.
  For us: same hull, different fittings; the folk AI already sails in the pack's own terms.
  Notes:

## Notes

## Already ours

Streams Reflowing (rivers and lakes), Shippy Ships (small craft), Old Cannons, Tideborn havens with stilt houses and a dock, Powers' real fishing with weighed fish (tuna, shark), Naturalist and Cube Animals for wildlife, and an existing continental offset in Kingdoms' worldgen that already leans slightly to water.
