---
name: sevtech-starter-routes
description: Andy 2026-10-25: SevTech-style leather tanning chain, salt crystals, 8 fibre string, flint+rock fire; PR #520 merged (Powers 0.16.24 / Kitchen 0.5.27 / Kingdoms 0.7.205)
metadata:
  type: project
  modified: 2026-10-25T14:41:53.815Z
---
2026-10-25 Andy asked for SevTech Ages starter routes using our items (work blade = Primitive Knife).
PR #520 MERGED 2026-10-25 21:10Z (master then pack 0.16.24 / Kitchen 0.5.27 / Kingdoms 0.7.205).
- Kitchen `tanning/Hides`: animals drop jaymoskitchen:hide (not leather); hide+blade=4 raw hide; 2 raw+salt+water=salted; Drying Rack (block, 4 min, rain pauses) -> dried; 2 dried+resin+water=2 wet tanned; rack -> leather. `BladeRecipe` type jaymoskitchen:blade_crafting keeps blade (-1 durability) and waterskin (-1 sip). Resin = bark on hand grindstone; knife on log side peels bark. Wool+blade=2 string. Raw-hide waterskin recipe so first leather is possible.
- Powers: 8 fibre ring = string; rocks in water = salt_crystals (drop Kitchen salt); rock piles have `natural` state, natural waterlogged ones random-tick into salt (covers Chunky-pregenerated world). Flint + rock in hands strikes fire (late UseBlockCallback phase so Kingdoms arson watch sees it). `Uncraftable.TANNED` drops minecraft:leather (Naturalist re-adds it).
- Gotcha found: 26.3 loot tables use `modifier`/`type`/`match_block`; old `functions`/`conditions` keys are silently ignored (rock piles always dropped 1, fixed in #520). Check other old-format loot JSON if counts look wrong.
See [[ground-rocks-rlcraft-start]], [[no-punching-trees]].
2026-11-05 (PR #995, Powers 0.20.81): Andy ruled rocks throw ONLY from the off hand after a short hold (ThrownRocks.CHARGE 10 ticks); main hand rock only places, off hand never places. Sneak+use with flint+rock splinters flint into 2 shards (air or block), never fire.
