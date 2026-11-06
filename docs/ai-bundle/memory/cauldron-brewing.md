---
name: cauldron-brewing
description: (merged #626) Andy 2026-10-28: early potions in a heated water cauldron (throw ingredients, stir each, 3 bottles, gunpowder splash) + thrown water bottles douse fire; PR #626
metadata:
  type: project
  modified: 2026-10-28T13:19:36.202Z
---
2026-10-28 Andy asked for "an early route to alchemy using cauldrons". PR #626 MERGED 2026-10-28 13:31Z (pack 0.17.113 / Kingdoms 0.7.300, not client-tested):
- Powers `alchemy/cauldron/CauldronBrewing`: vanilla full WATER_CAULDRON over heat (tinkering.kiln.Heat or lit block in jaymoskitchen:heat_sources); ingredients thrown in use the vanilla 26.3 BREWING recipes (recipe-based now, not PotionBrewing). Stir = 6 use clicks with a stick/shovel; the ingredient floats in the pot (ItemEntity tagged jaymospowers_brewing, no gravity, never pickup) and swirls until stirred through (Andy asked); unstirred or invalid ingredient is spat out. 3 bottles = cauldron levels. Brew stored in a chunk attachment `cauldron_brews`; dropped if the cauldron level changes.
- Sneak-use water bottle throws it as splash water; `SplashWaterDouseMixin` widens fire dousing to radius 2 for every splash of water.
- Kingdoms Firefighting: villagers throw splash water bottles at fires instead of placing water blocks (Andy: "keep and throw water bottles instead of placing buckets"); no real bottle stock.
- Gotcha: the cloud clone is shallow and `origin/master` at session start can be stale (pointed at #556-era); check `git log origin/master` after `git fetch origin master` before branching.
Related: [[kingdoms-sorcery]], [[golems-quiet-firefighters]].
