---
type: picklist
source: Two links Andy sent (Peterwolf's Forest Fire & Firefighting; Fall Hall by PaleoTech, as a kingdom's castle)
url: https://www.curseforge.com/minecraft/mc-mods/peterwolfs-forest-fire-firefighting · https://www.curseforge.com/minecraft/worlds/fall-hall-paleotech
licence: MIT (the mod) · none stated, so All Rights Reserved (the world)
status: open
created: 2026-11-15
tags: [inbox, picklist, mods, kingdoms, castles]
---

# Forest Fire & Firefighting and Fall Hall: fit check

**Pick on the page, not here:** https://claude.ai/artifact/AFN7f4YxdZh6aJSotkMrrT (ticks and notes save there and
Claude reads them back; "Copy picks" gives the rows in the docs/*-Rows.md format). This file is the first draft.

Neither goes in today. The fire mod is MIT and nearly runs on 26.3, but its jar is locked to 26.2. Fall Hall is a
26.3 world made only of vanilla blocks, but it has no licence and it's a world save, not a structure our castle code
can place. Facts were read on 2026-11-15 from the mod's jar, the world's region files and our code (Qwen's research
in DeepAI, checked against the 26.3 game sources). Nothing was tested in game.

## 1. Peterwolf's Forest Fire & Firefighting: one lock away

`ship once it loads` · MIT · Fabric, client and server · 1.1.11, built for 26.2 · 146 downloads, one file (2026-08-19)

- [ ] **Ask Peterwolf for a 26.3 build** `S` ^ff-ask
  His Planes and Realistic Tree Felling mods already have 26.3 builds, so he's likely to make one.
  For us: the clean way in. Nothing to keep up on our side.
  Notes:

- [ ] **Or lift the version lock ourselves** `S` ^ff-lock
  Only its manifest says 26.2. Every Minecraft class it uses (153) still exists in 26.3, and its three hooks (fire
  ticking, the title screen, the camera) have the same signatures. MIT allows it, and Fabric Loader can override a
  mod's version requirement from a config file, so the jar ships untouched.
  For us: one test launch decides. Set its limits for the Deck at the same time (burning blocks, spread checks,
  chunk tickets).
  Notes:

- [ ] **Our wildfires stay the only wild ones** `S` ^ff-ignite
  It can start fires on its own at random. Calamities already starts ours: dry lightning over savanna and badlands.
  For us: turn its natural ignition off.
  Notes:

- [ ] **Its fire runs our wildfires** `M` ^ff-sim
  Its fire is a simulation: embers jumping ahead, crown fires, wind, faster uphill, slower downhill, peat that
  smoulders, trees that burn through and fall. It can take over any fire near an incident.
  For us: a Calamities wildfire becomes a real fire line that crews fight, through its ForestFireApi.
  Notes:

- [ ] **Villagers keep their own answer** `S` ^ff-villagers
  It has its own villager panic. Ours (Firefighting) already sends villagers to beat the fire out or throw water,
  others to lead children away, and golems with their fists.
  For us: turn its panic off. When our villagers put a fire out, its fire must go out too, or it relights.
  Notes:

- [ ] **Fire pits and hearths stay lit** `S` ^ff-pits
  Fire that burns forever on purpose (the fire pits where corpses go, village Hearths) must never become an
  incident its crews come to put out.
  Notes:

- [ ] **Heat and smoke** `S` ^ff-heat
  Standing near a big fire burns you, and smoke hurts, blinds and weakens.
  For us: smoke is new and good. The heat doubles up with Kitchen's body heat: count its heat into body heat, or
  switch its own off.
  Notes:

- [ ] **The firefighter's kit** `M` ^ff-kit
  Portable pump with fuel, hoses (16/32/64), splitter, nozzle, strainer, water tanks, sprinkler, backpack sprayer,
  thermal scanner, incident command post, fire axe, Pulaski, fire shovel and rake, a full turnout set with
  breathing mask and air tank. Rescue missions, a score for each fire (speed, lives and buildings saved, teamwork,
  water used) and an incident HUD.
  For us: the HUD has to fit HUD-Layout. Lore: pumps and air tanks are Frostborn tech (they brought the Overworld
  its tech, and heat wears them thin); the Verdant, whose oldest enemy is fire, crew them.
  Notes:

- [ ] **The water bomber** `M` ^ff-plane
  A plane that scoops water and drops it on the fire. It needs Peterwolf's Planes (26.3, CC0, needs his Wind 'n
  Sails), which we don't run.
  For us: leave it out, or add Planes as Frostborn flyers. Planes over a medieval world is your call.
  Notes:

- [ ] **The dedication on the title screen** `S` ^ff-title
  It writes "Dedicated to all firefighters..." on the title screen (a config switch).
  For us: keep it, or turn it off and put the dedication with his credit instead.
  Notes:

## 2. Fall Hall: the right castle, but not ours to take yet

`ask first` · no licence stated (All Rights Reserved) · a 26.3 world save (version 5023, ours) · made by PaleoTech

- [ ] **Ask PaleoTech first** `S` ^fh-ask
  The page states no licence, so it's All Rights Reserved. Same road as Mosslorn, which the future now uses with
  its author's OK.
  For us: nothing ships until they say yes.
  Notes:

- [ ] **Cut the manor into a castle** `L` ^fh-cut
  Grand Seats raises a kingdom's castle from other mods' structures by name and copies nothing. Fall Hall is a world,
  so the manor has to be cut out of it into a structure of our own. Either we ship that (needs their OK to share
  it), or the server downloads the map the first time, the way the future fetches Mosslorn, and cuts it there.
  For us: 426 kinds of block, every one vanilla, including the 26.3 autumn blocks, so no other mods come with it.
  Notes:

- [ ] **It's at the size limit** `S` ^fh-size
  A rough scan puts the manor at about 110 by 80 blocks, walls to about 37 above the ground and towers to 57.
  Grand Seats takes castles up to 112 a side.
  For us: measure it properly; let this one go bigger if it needs to.
  Notes:

- [ ] **Which kingdoms get it** `S` ^fh-land
  Castles are picked by the land round the village, and there's no forest land yet (plains, taiga, snowy, savanna,
  desert, badlands, jungle, swamp, cherry).
  For us: a new autumn-forest choice, or the plains castles' list.
  Notes:

- [ ] **One of a kind, or one of many** `S` ^fh-unique
  A seat any forest kingdom can roll, or one Fall Hall in the world, like the keep above the ford.
  Notes:

- [ ] **Empty it like a seat** `M` ^fh-empty
  Grand Seats already takes out spawners, monsters, loose items and chest loot. The map also has vaults, trial
  spawners, a big sculk farm, structure blocks and brushable blocks (130 spawners and 237 chests across the whole
  map). Those aren't handled yet, and nor are chests stocked by hand rather than by a loot table.
  For us: add them, and check its great hall is what becomes the throne room (the largest room does).
  Notes:

- [ ] **Its autumn wood** `M` ^fh-wood
  The manor stands in a sculpted poplar forest and village; the whole map runs about 3000 by 900 blocks.
  For us: cut only the manor and grow our own red, orange and yellow poplars and leaf litter round it (26.3 has
  them), rather than copying their forest.
  Notes:

- [ ] **Whose seat it is** `S` ^fh-lore
  Deepslate tile and oak, soul lanterns, in the autumn woods.
  For us: the seat of a Sylvan or Heartlander realm in the poplar woods. Pick it and the paragraph goes to Lore.md
  for you to approve.
  Notes:

## Notes



## Couldn't check

Whether the fire mod's methods all still match 26.3: only its classes and its three hooks were checked, so it could
still fail somewhere at runtime. The manor's exact size: the block scan was rough. PaleoTech's wishes: the page says
nothing either way.

## Already ours (not re-suggested)

Wildfires, hail, floods and the rest of the land's calamities; villagers, folk and golems fighting fires; village
hearths and fire pits; a town's fire as work for its folk; Kitchen body heat and thirst; grand seats from the
structure mods' castles; downloading a CurseForge map at runtime (the future's Mosslorn); poplar leaves in the
season motes.
