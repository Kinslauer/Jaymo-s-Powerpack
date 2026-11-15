# GT New Horizons hardcore cross-reference: the plan (2026-11-15)

Andy picked 45 of the 50 rows on the pick page (https://claude.ai/artifact/E1WawaSDWqr27ShXkDemC2, copy in project
files `gtnh-hardcore/`). The source is the GTNH pack's own quest book and configs (GitHub
GTNewHorizons/GT-New-Horizons-Modpack, CC BY-NC-SA) and the 1.7.10 mods behind them (GregTech, Hunger Overhaul, Iguana
Tweaks, Infernal Mobs, Special Mobs). Everything is ideas only, built with our own code, names and art.

Not picked: the GT hand tools set (file, wrench, soft mallet in the grid), plates and rods by hand, tools earning their
mining level, creeper kinds, and the harsher Nether.

Andy's page note: **"We need JEI recipes for everything."** Every new or changed recipe, every multiblock, every new
drop and every machine shows in JEI (Powers `client/jei/`, Kitchen's JEI plugin). A patch isn't done until its JEI pages are.

Standing rules that bind every patch:
- Iron tools are forged (Tinkers route), never a grid recipe.
- No screen text that states lore or explains a mechanic. Signs are looks, sounds and bars.
- Tuning numbers are `static final` constants; everything **starts gentle**.
- PRs never touch `version=`, the MODLOGs or `docs/codemap/`; each mod changed gets `changes/<mod>/<branch>.md`.
- New things get a Lore.md placement as a `(new)` paragraph for Andy to approve.

## Andy's row notes, folded in
- Slow herds: **"bloom affects all of them"**. Chrono's Time Bloom (`power/chrono/TimeBloom`) must speed every slowed
  clock this plan adds (animal growth, breeding waits, egg laying, milking, crop and sapling growth), not only random ticks.
- Small steam boiler: **"there should be a steampunk village, maybe they have airships?"** Becomes patch 9 (the steam
  town). Airship scope is on a decision card; until Andy answers, the plan builds the recommended option (flying
  airships between steam towns), last.
- Marked monsters: **"give these to captains"**. The marked traits also join the Kingdoms captains' trait pool
  (`Captains.Trait`, `CaptainWays`), and roll on captains as their other traits do. Wild marked monsters stay as well.
- Punji sticks: **"diggers can use these too"**. Diggers (`creatures/Digger`) dig punji pits round their burrows and
  in tunnels.
- More spider kinds: **"only mother and flying spider"**. No witch or ghost spider.
- Zombies and skeletons with tricks: **"the MCA can remember their past lives, and get these abilities, not the long
  dead though"**. Only villagers who turned (MCA's zombie villagers, who keep their name and trade) use their old
  trade's tricks: a fisherman's rod hooks and pulls you, a hunter or guard shoots a bow. Plain zombies and skeletons
  get nothing new. Skeletons are left as they are (they are the long dead).

## What already exists that these build on (found while planning)
- Kitchen `mill/power` already has a Better With Mods windmill and waterwheel (Rotor, RotorItem, VillageWindmills),
  gearboxes and shafts. The windmill row is therefore a rework (materials, wear, our wind), not a new machine.
- Powers `layers/SawItem` is a forged saw that cuts wooden blocks into layers in the grid; the saw row extends it.
- Powers `alchemy/brewing/MortarBlock` is a mortar and pestle for brewing ingredients; the mortar row extends it.
- Tinkering smeltery already cooks creatures into blood; forge hearth heat, HotMetal and tongs exist; brick kiln exists.
- Kingdoms `Torches` already burn down.

## Patches
Two tracks at a time, each in a different mod so they never edit the same files; each finding is checked by an
independent verifier. Batches go up as one PR each (patches as separate commits) so Andy can test by merging.

### Patch 1, Stone Age recipes (Powers `ground/`, `tinkering/`, data; rows st-gravel, st-flintrecipes, st-doors, st-paper, st-torchfuel, gt-saw, gt-mortar, ss-brickform, wt-claybucket, ss-glass, gt-nomend, gt-woodbreak)
- **Gravel gives no flint**: gravel's loot never drops flint. A sieve recipe (gravel sifted in the grid with a
  plant-string mesh, or panning) gives flint at a chance. Loose flint (GroundRocks) stays.
- **Flint in the first table and chest**: crafting table = 4 logs + 1 flint (pattern), chest = 4 logs + 4 planks + 1 flint.
- **Doors and fences cost more**: wooden doors take copper or iron nuggets as fittings plus 6 planks for 1 door; iron
  doors take a steel ingot; fences and gates take sticks cut by the saw. Cobblestone walls stay cheap.
- **Paper from pulp**: sugar cane alone no longer makes paper. Wood pulp (log ground at the quern, or sawdust) + water
  pressed between two stone slabs in the grid; or cane ground in the mortar to chad, pressed the same way.
- **Torches by fuel**: torch yield by fuel: coal 2, charcoal 2, coal coke 5, creosote-soaked rag 3 (recipes after patch 4).
- **Saw for sticks and slabs**: the saw in the grid cuts 1 plank into 4 sticks (2 by hand), planks into slabs, and makes
  fence rails. It stays in the grid worn, as for layers.
- **Mortar and pestle**: the brewing mortar also grinds flint (to flint dust: 1 flint, used for glass), clay (to clay
  dust), ingots (to metal dust that the smeltery melts faster), and mixes copper and tin dust 3:1 into bronze dust.
- **Bricks need a form**: clay balls no longer smelt into bricks. A wooden brick form (planks carved with the knife)
  in the grid with clay gives 4 unfired bricks, which the kiln fires. Furnace brick recipe removed.
- **Clay bucket first**: clay dust or clay shaped into an unfired bucket, fired in the kiln: holds water and milk,
  cracks after a number of uses (a durability-style bar), shatters on lava.
- **Glass needs casting**: sand no longer smelts to glass. Sand + flint dust (mortar) = glass dust, melted in the
  smeltery into molten glass, cast into blocks (basin) and panes (table, a pane cast). Villager glass trades kept.
- **No self-mending tools early**: Tinkering's overslime and Mending do nothing until the holder has Knowledge level 3.
- **Wood and flint tools can't be mended**: Tinkering repair, the anvil and the grid refuse wood and flint parts.
- JEI: every recipe above, the sieve chances, the kiln loads, the casting.

### Patch 2, Farms, herds and the body (Kitchen `farm/`, `stomach/`, `season/`; rows fm-farmwater, fm-dryseeds, fm-slowgrow, fm-herds, fm-bonemeal, fm-hoewear, fm-crowd, fm-knifekill, st-jump, st-respawn, fb-hearts, fb-lunchbox)
- **Farmland only near water**: a hoe makes farmland only within 4 blocks of water (vanilla's hydration reach).
- **Seeds from dry grass**: breaking grass drops no seeds (hemp included). A hoe used on grass more than 4 blocks
  from water forages seeds one time in four (wheat, hemp, and the Kitchen crops' seeds by biome), costing durability.
- **Farms grow at half speed**: crops, saplings, cane, cactus, cocoa, nether wart, berries and fruit trees regrow at
  half speed on top of the season rule.
- **Slow herds**: young animals take twice as long to grow, breeding waits twice as long, hens lay half as often, a
  cow gives milk once every 20 minutes (a bucket on a milked cow gives nothing).
- **Weaker bone meal**: bone meal pushes a crop one stage; on a sapling it adds a growth step instead of growing it
  at once (Kingdoms SaplingBlockMixin's instant bone meal goes).
- **Hoes wear out fast**: tilling costs 5 durability.
- **Crowded pens**: more than 12 adult animals of a kind within 8 blocks start hurting each other now and then, and
  don't breed.
- **Kill clean or lose the meat**: a farm animal killed by fist, blunt weapon or blast leaves a bruised corpse: half
  the meat and a torn hide (Kitchen butchery reads a "bruised" mark on the corpse). A knife, cleaver or blade kill gives
  the full yield. No bursting.
- **Jumping makes you hungry**: each jump adds extra exhaustion (starts gentle); parkour moves that already cost
  stamina don't pay it twice.
- **Wake up hungry**: after a death wake-up the player starts at half food.
- **Eat widely, grow stronger**: each food kind eaten the first time adds points by its hunger value; every 50
  points a permanent extra heart, up to 10. A Kitchen food journal (book item) lists foods eaten and not yet tried
  (no explanation text, just the list).
- **Lunch box**: a pouch of 9 food slots that feeds you from its best-fitting food when you get hungry enough.
- **Time Bloom** (Andy's note): Chrono's Time Bloom also advances the slowed clocks above for animals and plants in it.
- JEI: food journal, lunch box, the hoe forage drops.

### Patch 3, Water (Kitchen `climate/`, Dynamic Waters; rows wt-finite, wt-cactus)
- **Water you can't copy**: two sources no longer make a third except in rivers, oceans and swamps, or under open
  sky in rain. Check against Dynamic Waters' own rules and the Hardrock waterskin rule first, and don't double them.
- **Cactus water**: 8 cacti in the grid give a bucket of bitter water (Thirst counts it as unclean unless boiled).

### Patch 4, Coke and steel (Powers `tinkering/`, `ground/`; rows ss-cokeoven, ss-creosote, ss-charpile, ss-bbf, ss-tin)
- **Coke oven**: coke-oven bricks (clay + sand, form + kiln) built as a hollow 3x3x3 with a controller face. Logs bake
  to charcoal, coal to coal coke, no fuel; each bake leaves creosote in the oven's tank, and a full tank stops it.
  Drain it into buckets or clay buckets. Counts as a soot source.
- **Creosote**: burns in furnaces; dipped rags make creosote torches; planks soaked in it become treated planks that
  never weather or rot (Kingdoms Weathering skips them).
- **Charcoal pit**: logs stacked in a heap, covered in dirt, lit through a gap; after a few in-game days it turns to
  charcoal blocks. Heavy soot. Uncovered logs just burn.
- **Brick blast furnace**: firebricks (clay + flint dust) in a 3x4x3 tower; iron ingot + coal coke turns to a steel
  ingot slowly (minutes each), with ash as a byproduct and heavy soot. A second steel route beside the smeltery alloy.
- **Tin and a real bronze age**: tin ore (stone and deepslate) in high mountain veins, tin sand in river beds near the
  surface (panning gives tin nuggets); smeltery alloy bronze 3 copper : 1 tin; bronze joins Tinkering as a tool and
  armour material between flint and iron, and kettles and bells take it. Kingdoms smiths and caravans trade tin.
- JEI: oven, pit, furnace pages; tin ore drops; alloys; bronze parts.

### Patch 5, The forge's dangers (Powers `tinkering/`; rows hz-hotblocks, hz-rain, hz-upkeep, hz-hotcarry, hz-mobfluids)
- **Hot walls burn**: standing against or touching a lit forge hearth, a kiln wall over fire, a working smeltery
  wall, a coke oven or a blast furnace burns you a little; a cover (planks or a trapdoor) stops it.
- **Rain on the fire**: lit hearths, kilns, smelteries and the new furnaces open to the sky lose heat in rain and
  go out in a downpour; a hot smeltery doused cracks one seared brick.
- **Multiblocks need upkeep**: seared bricks and kiln bricks crack with use (a cracked texture state); a cracked
  smeltery block leaks molten metal now and then until mended with grout (and a hammer).
- **Don't burn your fingers**: lava buckets, pots straight off the fire and blaze rods burn the hands like hot metal;
  smithing gloves (leather, ring slot) or tongs stop it.
- **Creatures melt to what they're made of**: endermen give molten ender (cast to pearls), iron golems molten iron,
  slimes molten slime, blazes molten blaze, on top of blood.
- JEI: gloves, the new melting entries.

### Patch 6, Monsters (Kingdoms `wild/`, `creatures/`, `Captains`; rows mb-affix, mb-spiders, mb-zombies, mb-punji)
- **Marked monsters**: a monster may spawn marked with 1 to 4 traits: Vengeance (returns part of the damage), Regen,
  Sapper (drains your food on hit), Webber (webs you), Ghastly (fireballs), Blastoff (throws you up), Ninja (appears
  behind you), Sticky (pulls your held weapon away, dropped near it), Rust (wears your armour faster), Storm (calls
  lightning). Chance by world tier: rare at Hearth, common at Dread and Legend. Shown by a faint aura and a sound per
  trait; three or more traits get a boss bar (BossBars). Drops: a little extra loot by traits.
- **Captains** (Andy's note): the same traits join `Captains.Trait`, rolled with the others.
- **Mother spider**: a big spider that bursts into a brood of small spiders when killed.
- **Flying spider**: a spider that leaps from trees and ledges and carries you a few blocks up before dropping you.
- **Turned villagers remember**: a zombie villager who was an MCA villager uses their old trade: fishermen hook and
  pull you with a rod, hunters and guards shoot a bow (finite arrows, the free-projectiles rule), farmers throw
  their hoe once. Plain zombies, and every skeleton, don't.
- **Punji sticks**: a placeable block of sharpened sticks (or bamboo) that hurts and slows anything that steps in;
  mobs path round it if they can. Kingdoms outposts' builders and **Diggers** (Andy's note) set them in pits and tunnels.
- JEI: punji recipe.

### Patch 7, Ores (Powers `ground/`, new world; rows or-mixed, or-small, or-dowse)
- **Mixed veins**: each big vein (BigVeins) gets partner ores scattered through it (iron with copper streaks, copper
  with tin, gold with iron); washing gives the partner's tiny dust as a byproduct.
- **Small ores**: lone ore stones in near-surface stone, giving a crushed piece; their mix hints at veins near.
- **Sample dowsing rod**: a forked rod holding an ore sample; it ticks faster the nearer that ore is (sound only).

### Patch 8, Steam and wind (Kitchen `mill/power`; rows hz-windmill, hz-boiler)
- **Windmill rework**: sails of cloth, wool, leather or paper (each with its own strength and wear); turning speed
  from our wind (Kingdoms `sailing/Winds` through a compat bridge), the height of the hub and open air; sails wear and
  storms tear them; a windsock block shows the wind's strength and direction.
- **Small steam boiler**: a copper (bronze once patch 4 is in) boiler over a firebox, fed water and fuel, turns a shaft
  through a steam engine block for the mill machines. Let it run dry while hot and refill it, and it bursts.

### Patch 9, The steam town (Kingdoms; Andy's note, lore placement first)
- A new settlement kind: a smoky town of brick, iron and timber built round boilers, windmills and a coke oven, its
  people tinkers and engineers. They sell steam parts, coke, creosote and tin. Lore placement proposed: the town grew
  from the craftsmen who worked for the Mad Tinkerer before he vanished (Lore.md `(new)`, for Andy to approve).
- Airships per Andy's answer on the card. Recommended: airships moored at the town that fly a route between steam
  towns, built on our ships (sailing) and the wind; passage bought from the captain.

## Order
1 and 2 (Powers + Kitchen), then 4 and 6 (Powers + Kingdoms), then 5 and 3 (Powers + Kitchen), then 7 and 8
(Powers + Kitchen), then 9. Each finished pair goes up as a PR.

## Open questions
- Airship scope (decision card in the thread).
- The exact numbers (torch yields, herd waits, crowding count) are starting values, to be tuned after a playtest.

## Lore placements, for Andy to approve
These are the `(new)` paragraphs proposed for `Lore.md` (the vault is edited only through the live folder, so they wait
here until Andy approves them).

- **(new) Hard ground** (patches 1 and 2). The land gives nothing for free since the sorcerer's fall: flint must be
  sifted from gravel, a table and a chest are bound with flint, and the first bricks are fired in a campfire pit before
  anyone has a kiln. Fields die away from water, crops and herds grow at half the old pace, and a beast clubbed to
  death is bruised meat. Folk eat widely or sicken of sameness, which is why a field hand carries a lunch box.
- **(new) Water that comes back** (patch 3). Rivers, the sea and the marshes are the Deep Mother's, and whatever is
  taken from them she fills again; rain is her gift and fills what lies open under it. A pond pooled by hand in dry
  country holds what it was given and no more. Where there is no water at all, desert travellers press cactus round a
  bucket for a bitter drink that turns the stomach unless it is boiled first. The Tideborn call it "the Mother's thin
  milk" and won't touch it unboiled.
- **(new) Tin and bronze, coke and steel** (patch 4). Tin is the Stoneborn's metal: its dark tinstone runs in great
  veins under the mountains, and the rivers coming down from them carry it out as tin sand for lowland panners.
  Bronze, three of copper to one of tin, is told as the Anvil-Father's first gift to smiths, and the metal bells are
  cast in. Coke ovens and brick blast furnaces are the smoky craft of the Mad Tinkerer's old workmen; charcoal pits,
  logs buried under earth to smoulder for days, are the older woodland way.
- **(new) The forge's dangers** (patch 5). Smiths of the Anvil-Father say his fire and the sky's water never marry: a
  forge is roofed or it is doused, and a thunderstorm over an open smeltery cracks its brick. Cracked seared brick
  weeps its melt until a mason presses grout in with a hammer. Tanners cut thick smithing gloves for anyone who
  handles hot iron, lava or blaze rods. What a thing was made of (the ender's dark, the golem's iron, the slime's
  jelly, the blaze's ember) the smeltery gives back to the smith.
- **(new) The marked, the mothers and the turned** (patch 6). The sorcerer's mana that went into the ground lies in
  seams; a thing of the dark that rises over one drinks some of it and carries it as a faint aura: the marked. The
  older the world grows, the more rise marked, and orc and goblin captains sleep on the seams to take the marks too.
  Mother spiders carry their brood on their backs; in the woods, flyers drop from the canopy on a line. The recently
  dead remember their trade (the fisher casts, the hunter shoots, the farmer throws his hoe); the old dead and the
  skeletons have forgotten. Punji stakes are the oldest trap of anyone holding a line: diggers line their pits with
  them and barbarians ring their palisades.
- **(new) Mixed seams, float stones and the dowser's fork** (patch 7). The Anvil-Father's miners say no metal lies
  alone: iron keeps a thread of copper, copper keeps tin, gold keeps iron, and a patient washer finds the companion's
  grains. Over a great seam the ground is shot with lone stones of its ore, which miners call float and follow down.
  The dowser's fork, a forked rod with a pinch of the wanted ore bound in its crotch, knocks in the hand quicker the
  nearer its kind lies; the Anvil-Father's priests call it his pulse.
- **(new) Sails, windsocks and the little boilers** (patch 8). Millers sew their own sails (hemp, wool, leather, and
  paper only as a stopgap) and read the wind, the same wind Aeolus keeps, off a striped windsock by the mill. The small
  steam boiler came out of the Mad Tinkerer's workshops; the craftsmen who worked there kept the trick when he
  vanished, and all of them warn: never fill a boiler that has boiled dry over the fire.
- **(new) The steam towns** (patch 9). When the Mad Tinkerer vanished, the craftsmen who had built for him kept
  together and went on building steam from memory. Their towns are smoky places of brick, iron and creosoted timber,
  the only ones where a tinker or an engineer can still be found at the bench. They never had his drawings, so they
  could not build his pods; instead they hung railed decks under bags of wool with a boiler aboard, and each town
  keeps one such airship on its mast, flying travellers between the towns on the wind.
