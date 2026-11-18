---
status: stalled
task: fishing-fire-and-crates
branch: claude/fishing-fire-and-crates
with: pc-claude (subagent)
pr: 
mods: Powers
versions: 
started: 2026-11-17
owns:
  - src/main/java/com/jaymospowers/fishing/ (LavaFish, LavaFishing, Crates, Species, FishSpecies, FishBites, CatchRelease, FishingReal, mixin/FishingHookMixin)
  - src/main/java/com/jaymospowers/tinkering/ranged/TinkerRodItem.java (one tooltip line)
  - tools/gen_fishing_species.py and its generated assets
  - src/main/resources/assets/jaymospowers/lang/parts/fishing_fire.json
  - src/main/resources/data/jaymospowers/loot_table/gameplay/crate_*.json
  - changes/powers/fishing-fire-and-crates.md
---
# Fishing: lava, crates, cave fish (notes and tackle bench next)

**Task:** Andy's 5 Tide picks. This PR does lava fishing, crate catches and cave-pool fish; the torn notes (Journal hints) and the tackle bench are left for a second PR.

## Plan
- Lava: heat-proof Tinkering rod (limb and hook of a heat-taking material), hook floats on lava, three hot-spring-style lava fish, spawn at lake edges in the Nether.
- Crates: rare crate with a landed fish, loot table per water (river, sea, swamp, lava).
- Cave fish: cavefish and olm, only in dark pools under Y 33.

## Notes
- No "Ash" region exists in code, so lava fishing works on any Nether lava, and the lava fish spawn in any Nether biome's lava edges.
- Not built or run on this PC (no Gradle here); the build is checked by CI.

## Done
PR 1715, CI green (Powers, Kingdoms, Kitchen builds, change-note checks, pack start). A player sees: a Tinkering rod forged with a heat-proof limb and hook (tooltip says so) floats its hook on Nether lava and draws ash molly, cinder pupfish and ember tilapia that drift along the lava's shore; any other rod's hook burns up in lava. Landed fish sometimes bring up a River, Sea, Swamp or Lava crate that opens for that water's loot. Cavefish and olm live in dark pools 30+ blocks under sea level and bite slowly. Not played or run: the lava fish's hand-made drift, hook floating on lava, and spawn rates are untested. Left for a second PR: torn notes into the Fisher's Journal, and the tackle bench.
- 2026-11-17 21:40 No sign of work for 30 min: marked stalled (whoever ran it stopped).
