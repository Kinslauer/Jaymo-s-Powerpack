# sky villages, ruined villages and the sky lord's castle in the Aether
Andy, on the Sky Villages mod's ideas (ARR, nothing of it used): "should all instead spawn in the aether proper, and be
filled with divine born", "Yes but now aether material", castle "in that scale, out of the relevant material".
- **Sky village** (`aether/structure/SkyVillages.village`, 47 by 47): a round green with paths and a flagged plaza, a well
  of light, four homes (two villagers each, bedrolls, a chest), a kitchen (Kitchen's stove, cabinet, pot and crates when
  Kitchen is installed; smoker, barrel, cauldron and hay if not), a workshop (crafting station, part and pattern chests,
  layer blocks), a windmill with cloth sails, two farm plots, and a mooring mast with a balloon skiff tied to it (a chest
  in its hold). Three palettes by the land: skyroot (highfields), wisproot (magnetic), greatroot and icestone (arctic).
- **Ruined village** (variants 3 to 5): the same village abandoned. Walls and roofs fallen in more the higher up, moss on
  the stone, vases where the furniture was, no skiff, three swets and two cockatrices living in it.
- **Sky castle** (`SkyVillages.castle`, 49 by 49): a two-thick curtain wall with a walk and merlons, four corner towers
  with ladders and cloth caps, an arched gate, a keep with the lord's hall and seat, rooms above, a walled roof and a
  spire. Eight villagers, one tagged as the lord.
- New plans `village` and `castle` on `jaymospowers:aether_structure`; one structure set `sky_villages` (spacing 30,
  separation 14, kept 6 chunks from outposts). Loot: `chests/sky_village/home`, `skiff`, `keep`. Data written by
  `tools/gen_aether_structures.py`.
- Villagers are plain villagers tagged `jaymospowers_sky_villager` (`jaymospowers_sky_lord`); MCA turns them into its own,
  and Kingdoms gives them their blood.
- The skiff hangs a step clear of the landing and, with Jaymo's Kingdoms installed, has a ship's wheel by the gap in
  her rail: use it to cast her off as a balloon ship. Kingdoms also crews her, names the castle's lord and wardens, and
  dresses the sky folk.
- Compiled; never generated in a world, so the layout has not been seen in game.
