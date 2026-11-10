# TerraFirmaCraft Hardrock cross-reference: the plan (2026-11-10)

Andy picked 34 of the 39 rows on the pick page (https://claude.ai/artifact/NSAUnMKP6MHGxX1pSuwxqs, copy in project files
`tfc-hardrock-study/`). Sources: the TFC Hardrock modpack (Forge 1.18.2), Cold Sweat TFC, Realistic Water Engine,
Dynamic Waters and Thirsty Bottles. Everything is ideas only and built with our own code, except two things: Thirsty
Bottles ships unchanged, and Dynamic Waters (MPL-2.0) may be ported.

Not picked: Waystones (travel stays on roads, carts, coaches and ships), ItemPhysic (Interactic stays), ship
TerraFirmaCraft, "teleporting is OK" and "Hardrock-hard". So every rule **starts gentle** (slow rot, rare cave-ins, mild
diet effects), and its numbers are `static final` constants we tune up later.

Picked as open questions: **a new world is OK**. Ore veins and the Dynamic Waters rivers may assume fresh land, and the
friends' server can start a new world for them.

Standing rules that bind every patch:
- No new dimensions or portals. Andy's note on the planets row is "The planets are okay to add". We already have six far
  worlds (FarWorld: Moon, Rustfall, Glacia, Cinder, Maw, Verdant), so this plan reads the note as permission to add
  Hardrock-style planet content to those worlds (patch 8), not to add new dimensions. Andy confirms which reading.
- Iron tools are forged (Tinkers route), never a grid recipe.
- No screen text that states lore. Freshness, fuel and the like are shown as bars, never as days left (as with lanterns).
- Dirty water goes through `Thirst.drink`, so Iron Stomach covers it.
- Every new recipe or route shows in JEI.

Andy's notes from the page, folded in below:
- Prospector's pick: "Make veins bigger and rarer. Meaning most villages will not have iron armor or tools and will be
  more encouraged to trade for it. Diamond and netherite become IMPOSSIBLY rare."
- Hand quern: "Replace our hand grinder" (Kitchen's Hand Grindstone, `jaymoskitchen:hand_grindstone`).
- Getting dirty: "if you stink youll be caught, especially among non humans with better noses. Villagers will comment on
  this" (Kingdoms Senses + talk lines).
- Comforts: "The night timelapse does not currently work anyway". That is a bug in Kingdoms `Timelapse`, and it gets its
  own fix in its own thread.

## Patches (one system per track; two tracks at a time, each finding checked by an independent verifier)

### Patch 1, Warmth you build (Kitchen `climate/`; picks cs_hearth, cs_icebox, cs_lining, cs_gauge, cs_skin, cs_climate)
- **Hearth**: a fuelled stone block. It floods warmth through the closed space around it (a flood fill over air, capped
  at a few hundred blocks and stopped by open sky or open doors), then BodyHeat reads "inside a warmed space" as a heat
  source. Fuel is a durability-style bar.
- **Icebox**: the hearth's cold twin, burning ice, snow or packed ice in the same flood.
- **Lining**: sew wool, fur (wolf, bear, Naturalist hides) or leather into any armour or clothes, at a loom or by a
  tailor villager. It is an item component with warmth and cooling, read by BodyHeat's `WARM_PIECE`/`COOL_PIECE`, and
  shown as flame or snowflake pips on the tooltip.
- **Thermometer**: a held item that shows the air's temperature (BodyHeat's `ambient`) as a dial, not your body heat.
- **Hot and cold waterskins**: a waterskin warmed at a fire or chilled in snow drinks as a short warm or cool, like
  `HOT_MEAL`.
- **North is colder**: a latitude term in `ambient`, colder per thousand blocks north of spawn and warmer to the south,
  plus a little damp near oceans.

### Patch 2, Water that isn't free (Kitchen + packMods; picks tb_ship, tb_skin)
- Ship Thirsty Bottles (Darkhax) in packMods. First check the jar's licence and that Kitchen's pond bottles still work.
- Waterskin sips use up the source block as well (`WaterskinItem`), so dry lands' pools really run dry.

### Patch 3, Rivers that flow (Powers or Kitchen; picks dw_current, dw_ice, dw_port, rwe_splash, rwe_finite)
- **3a, port Dynamic Waters** (MPL-2.0, github.com/AdamNewGames/dynamicwaters-public, from its Fabric 1.21.1 branch) as
  `ports/dynamicwaters`, like `ports/trmt`. It replaces Streams Reflowing in packMods, and needs a new world. Check its
  terrain against WWOO and One Dimension first. If the port turns out too big, the currents below run on Streams
  Reflowing's rivers instead.
- **Currents**: flowing river water pushes players, boats, items and mobs downstream, faster in rain and in a storm.
  Shippy Ships boats and our ships drift too.
- **Rivers freeze**: in cold biomes and in winter (Kitchen seasons), calm stretches freeze over and thin ice floes
  drift.
- **Splashes**: something heavy landing in water (a giant, a whale breaching, a felled trunk, a ship) sends out a wave
  ring that rocks boats and pushes swimmers, built like the Chrono water wake.
- **Floods** (large, last): breached water runs downhill and drains away with a real amount, held to small areas so the
  Steam Deck server keeps up. It is built only after the rest of the patch has been played.

### Patch 4, Rock and ore (Kingdoms `worldtier/` + Powers tinkering; picks tfc_cavein, tfc_prospect, tfc_veins, tfc_pan, tfc_quern)
- **Bigger, rarer veins**: vanilla iron, copper and gold ore is replaced by fewer, much larger veins, tied in with
  GroundChanges. Diamond and netherite become impossibly rare: a handful per world region, mostly in great foes' hoards
  and deep vaults.
- **Iron is scarce**: Kingdoms villages without a vein nearby lose iron gear: guards wear leather, bronze or chain, and
  smiths ask a lot for iron. Caravans and TradeRoutes carry iron as a prized good. This is Andy's "encouraged to trade".
- **Prospector's pick**: a forged pick head (Tinkers). Tapping rock reports "nothing", "traces" or "a lot" of the
  nearest vein's metal within a radius. Prospector career villagers sell vein rumours.
- **Cave-ins**: mining out raw stone under an unsupported roof may drop a patch of the roof, made of Gravity Rocks'
  falling stone. Support beams (axe-made from logs: a post and a cross beam) hold a radius. Rare to start.
- **Panning and washing**: a pan used on river gravel gives small nuggets. A sluice block in flowing water washes
  gravel for more. It is an early copper and gold route for the smeltery.
- **Hand quern**: replaces Kitchen's Hand Grindstone (same recipes and grinding JEI page, a new model, and an old block
  converts on load).

### Patch 5, Food that doesn't keep (Kitchen; picks tfc_rot, tfc_preserve, tfc_cellar, tfc_groups, tfc_cheese, tfc_soil)
- **Freshness**: every fresh food carries a "made at" time component and shows a freshness bar. When it runs out it turns
  into the rotten form (rotten flesh, Farmer's Delight rotten tomato, spoiled food). It builds on `butchery/Spoilage`.
  Stacks merge with the older time. It starts slow: about a season for bread, a week for raw meat.
- **Preserving**: salt (from the salt crystals), brine and vinegar barrels, a drying rack, and the smoker minigame mark
  food "preserved", which rots far slower.
- **Cold cellar**: a container in a cool, dark underground room, or next to an icebox or packed ice, slows rot.
- **Food groups**: grain, fruit, vegetables, protein and dairy, shown on the stomach screen. A balanced diet gives up to
  2 extra hearts, and one food alone lowers you a little. Checked against Survivalist hearts first.
- **Cheese, ovens and greenhouses**: cheese wheels that age in a cellar; a clay oven for bread, pies and pizza; a glass
  greenhouse where Seasons' winter frost doesn't reach the crops.
- **Tired fields**: farmland carries three nutrients, and each crop draws on one. Compost (Farmer's Delight), manure and
  bone meal restore them, and crop rotation pays.

### Patch 6, Animals (Kingdoms or Kitchen; picks tfc_familiar, tfc_traps, tfc_dogs)
- **Familiarity**: feed or brush a farm animal once a day and it grows familiar (a small heart that fills in). Only
  familiar animals breed, and familiar ones give more milk, wool and eggs. It goes with Respawning Animals.
- **Snares and cage traps**: baited snares and cages catch small game (rabbits, chickens, foxes, Naturalist critters) as
  corpses, for primitive small game.
- **Dogs that learn**: tamed wolves earn points and learn a few skills (Hunter, Guard, Pack Dog, Herder, Tracker). Tracker
  uses the blood trail.

### Patch 7, Living rough (Powers + Kingdoms; picks ql_comforts, ql_glider, ql_balloon, ql_hygiene, ql_pollution)
- **Comforts**: ship it if a 26.3 Fabric build exists, otherwise build our own sleeping bag and hammock (BedRest works
  with them, and they don't move your spawn). Fix the timelapse first (separate thread).
- **Paraglider**: a cloth glider item on the wing-glide code. It uses stamina, and updrafts rise over campfires and
  chimneys (smoke system).
- **Hot air balloon**: a rideable wicker basket and envelope with a burner (fuel bar), carried by our wind.
- **Dirt and smell**: grime (mud, blood, sweat, armour grime) builds a smell value. Senses gives mobs a smell sense:
  beasts, orcs, goblins, vampires and werewolves smell you from further away, humans less. Villagers comment in talk
  lines and greetings. You wash in rain, a river, a wash tub or a bath house.
- **Pollution**: lit furnaces, smelteries and fires add soot to a chunk value that spreads slowly. High soot wilts crops
  and leaves, dirties water (`Thirst` dirty water) and makes people cough. Advanced Chimneys spread it further.

### Patch 8, The planets (Powers `farworld/`; pick rule_worlds, see the open question)
- Hardrock's planet metals, as new materials on our existing far worlds: a Moon metal, a Mars-like one on Rustfall, a
  Venus-like one on Cinder, each with a Tinkers material and a use (pod parts, armour). There are no new dimensions.

## Order
Patch 2 (small) and patch 1 first, then 4 and 5 (the big ones), then 6 and 7, then 3, with 8 last. Two tracks at a time.

## Open questions
1. The planets: is it new materials on our six far worlds (this plan), or new planet dimensions as an exception to the
   no-new-dimensions rule?
2. Diamond and netherite "impossibly rare": is it still possible to find a few in the deepest vaults and great foes'
   hoards, or truly none from mining?
3. Food groups: OK to raise max hearts with a balanced diet, beside the Survivalist hearts?
