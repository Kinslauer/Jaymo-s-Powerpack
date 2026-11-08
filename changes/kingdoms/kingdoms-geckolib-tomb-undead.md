# GeckoLib mobs: the draugr, the barrow beetle, and the Stormborn Tomb's skeletons and boss
- **GeckoLib** (5.5.7, MIT, Tslat and contributors; the same file the pack already ships) is now a Kingdoms requirement.
  New animated mobs live in `inhabitants/` (`Inhabitants`, `client/inhabitants/InhabitantsClient`); the pattern to copy
  is in `docs/AI-HANDBOOK.md`, "Adding a GeckoLib mob to Kingdoms". `tools/bbmodel_geckolib.py` turns a Blockbench
  project into GeckoLib's files; `kingdoms/tools/gen_tomb_undead.py` makes our models (`kingdoms/art/*.bbmodel`).
- **Draugr** (`jaymoskingdoms:draugr`, our own model, texture and code): a dead Nordic warrior in a horned helm, mail and
  fur, 40 health, armour 8. Each rises with a sword and shield (it takes blows and arrows from in front on its shield
  now and then; an axe knocks its guard down for 5 s) or a two-handed bearded axe (10 damage, knocks a raised shield
  aside). Its blows chill (a short slowness). Only from spawners and its egg: it's for the Stormborn Tomb's halls.
- **Barrow beetle** (`jaymoskingdoms:barrow_beetle`, our own): a harmless little beetle in small groups in dark caves
  under the cold biomes (`has_barrow_beetle`); runs and flutters when hurt. The example GeckoLib mob.
- **Silver skeleton**, now drawn with GeckoLib (polyjo's Dead Traveler, CC BY 4.0): one a spawner raises first climbs up
  out of its coffin (a new 2 s "rise", standing still while it does). It is the Stormborn Tomb's skeleton.
- **Skeleton Lord**, now drawn with GeckoLib (Blockbench Workshop, CC BY 4.0), with three new moves of our own: his staff
  swung at whoever he hits; a slam of the staff's foot (every 8 s at a foe within 8 blocks) that hurts and throws up
  everything on the ground within 4.5 blocks; and his Grave Call at 75% and 40% health, raising 2 then 3 silver
  skeletons that climb out of the floor round him (no more than 6 near him at once). He is the tomb's boss.
- Draugr Invasion (All Rights Reserved) and Supplementaries are not copied in any form.
- Built; not played, and no server boot was run: check the four mobs' facing, the GeckoLib animations (Lord's statue,
  laugh and moves, the skeleton's rise, the draugr's shield) and the beetles' spawning in game.
