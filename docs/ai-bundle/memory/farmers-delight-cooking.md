---
name: farmers-delight-cooking
description: Farmer's Delight (MIT incl. art) remade in Kitchen's delight/ package: placed pot/pan over fire, cutting board, feasts, Comfort/Nourishment, cabbage/rice; PR #524
metadata:
  type: project
---
2026-10-25 Andy asked to overhaul Kitchen cooking with Farmer's Delight ideas/textures, chose "all six parts", and said "Replace any matching items with the mod's textures". FD is MIT for the whole repo (vectorwing), so models/art are reused with LICENSE_farmersdelight + fabric.mod.json credit.
PR #524 MERGED 2026-10-25 21:16Z (Kitchen 0.5.28, Powers 0.16.26, Kingdoms 0.7.207 as opened; not playtested), code in kitchen/.../delight/:
- Cooking Pot / Frying Pan are BlockItems; placed over a hot block (tag jaymoskitchen:heat_sources, LIT checked) = Station HEARTH_POT / HEARTH_PAN.
- Own cutting board (BE + renderer, counts as Station.BOARD), knives tag jaymoskitchen:knives (primitive, bone, new iron knife), Chopping.java recipes; knife on grown wheat gives straw.
- Feasts = Dish entries with feast(): ModItems registers FeastItem/FeastBlock; Quality stored as block property, servings get it.
- Comfort (bowl food) caps cold in BodyHeat; Nourishment (nutrition >= 11) pauses Stomach meal decay. Hunger/power fuel untouched on purpose.
- Rice in 1-deep water (RiceBlock + RicePaniclesBlock), cabbages; wild cabbages (beaches), wild rice (swamps) via worldgen/feature + placed_feature (26.3 dir is worldgen/feature, loot condition is match_block).
- FD textures also on Powers primitive_knife and Kingdoms rope.
Not client-tested: hob transforms for the FD pot/pan models were computed. Dev runServer boot + command checks passed.
Related: [[sevtech-starter-routes]], [[hearty-meals]], [[thirst-temperature-weathering]].
