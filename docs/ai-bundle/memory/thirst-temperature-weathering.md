---
name: thirst-temperature-weathering
description: Immersion pass 2026-10-25: remake Dehydration (thirst), Scorchful+Frostiful (temperature), Immersive Weathering; none have 26.3 builds
metadata:
  type: project
  modified: 2026-10-25T03:25:19.105Z
---
2026-10-25 Andy linked Immersive Weathering (1.20.1, LGPL), Dehydration (1.21.1, GPL), Scorchful + Frostiful (26.1.2 max, LGPL, need Thermoo). None on 26.3, so all are remade as our own code, no copying. Andy chose "all three, in stages": stage 1 = Kitchen thirst + body temperature on top of existing WinterCold/seasons/blizzards/Frostborn (one PR); stage 2 = block weathering as a second PR. Andy 2026-10-25: weathering MAY hit villages; Kingdoms builders' upkeep should repair it.
Stage 1 = PR #505 MERGED 2026-10-25 ~03:55Z: Kitchen 0.5.25 / Kingdoms 0.7.200 / pack 0.16.13. Kitchen package `climate` (Thirst, BodyHeat, WaterskinItem, Climate; client/climate/ClimateHud), gamerules jaymoskitchen:thirst / body_heat; WinterCold's winter hunger folded into BodyHeat (tag + setColdProof kept); Kingdoms KitchenCompat sets Emberborn heat-proof + Calamities.bodyHeat. Built + Kitchen dev server boots; not client-playtested. Follow-ups: cool clothing (tag empty; tailor robes?), Powers Ember heat-proof.
Stage 2 = PR #508 MERGED 2026-10-25 ~04:10Z: Kingdoms 0.7.202 / pack 0.16.15. Kingdoms Weathering.java (moss/cracks/mud/leaf litter/tufts near players, gamerule jaymoskingdoms:weathering) + Upkeep.java (builder-brain wrapper, Overworld attachment `upkeep`, reverts only weathered blocks within 96 of a seat). Kingdoms dev server boots with a temp c:ingots silver+steel datapack (pack.mcmeta needs min_format/max_format).
Thread:. Reference-Notes entries added (2026-10-25 section).
Related: [[natural-disasters-plan]], [[immersion-mods-0152]].
