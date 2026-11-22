# survival picks: thirst per action, wetness, temperature sources, cold breath, sprint stamina
Andy's picks from Legendary Survival Overhaul (ideas and numbers only) and Walk Jog Run (LGPL, idea only).
- **Thirst per action**: a jump, a block broken, an attack and each second of sprinting cost water, after LSO's
  thirst (jump 0.15, block 0.07, attack 0.3, sprint 0.1 of its exhaustion; 4 make a point of water). This replaces the
  old flat sprint drain. The hotter you are the faster all of it goes, and the cold already burns food (both were
  there before).
- **Wetness meter** (`Wetness`): every half second the rain adds 7, standing in water adds 10 times how deep you're in
  it, and otherwise you dry 3 (100 is wet through). It's the one wet chill on body heat, up to 25 (10 LSO degrees)
  at full; soaked through (80 and up) on cold land is worse, and soaked clothes keep half their warmth (wool all of
  it). Jaymo's Powers' Wet and Soaked keep the meter at least 40 or full, and Kingdoms' downpours fill it. It shows
  as a small blue glass beside the thermometer while you're wet. This replaces the old one-minute wet timer and the
  separate in-water chill.
- **Dousing**: sneak-use a water bottle, a bottle of clean water or a waterskin with water in it (not while aiming at
  water, where it fills): you pour it over yourself, are wet through, and any fire on you goes out. A bottle gives its
  glass back; a waterskin loses a sip. With Powers you're Soaked as well.
- **Temperature**: one LSO degree counts as 2.5 body heat. Height now chills from sea level, 6 degrees every 64
  blocks (it used to start 40 blocks up, so hills are a little colder). The day swings 2 degrees either side, warmest
  at noon and coldest at midnight, on top of the sun and night sky. Fires, campfires, lava, magma and lit furnaces
  warm you from up to 20 blocks away (was 5), less with distance, and half as much out under the sky as inside.
  For 90 seconds after joining or respawning the world can't make your body heat any worse.
- **Cold breath**: a small puff of breath at the mouth, drifting the way you face, every two seconds when the air is
  cold (25 below comfortable) or you're chilly. Everyone sees it.
- **Sprint stamina** (`Stamina`, game rule `jaymoskitchen:stamina`): sprinting spends wind instead of food. A full
  hunger bar holds a full bar of wind (about 13 seconds of sprinting), half a hunger bar half of it. Run out and you're
  winded: slowed for 3 seconds, and no sprinting until a quarter is back; it refills a second after you stop. The bar
  shows in the focus band over the hotbar while it isn't full, the part you can't hold for hunger shaded off. Powers'
  feathers stay what dodges, powers and fighting or running in armour spend.
- Built; not played. Not checked in game: the breath puff's look, the HUD placement of the wetness glass and wind bar,
  the heat-source scan's cost on a busy server, how the sprint bar feels next to feathers.
