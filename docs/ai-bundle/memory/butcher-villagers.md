---
name: butcher-villagers
description: Butcher villagers butcher passive-animal corpses for emeralds; hidden 1x-3x skill learned via gossip or the Survival butchering perk. Merged PR #79 (Kitchen 0.4.32 / Kingdoms 0.5.51 / pack 0.8.99)
metadata:
  type: project
  modified: 2026-10-13T01:48:48.935Z
---
2026-10-13 Andy asked for butcher villagers who butcher your animals for emeralds. Their skill varies, up to 3x the drops from one corpse. They take the corpse, walk out back, and come back with the drops. Passive animals only.
PR #79 MERGED 2026-10-13 03:17Z as Kitchen 0.4.32 / Kingdoms 0.5.51 / pack 0.8.99. The code is `corpse/Butchers` (logic, tuning constants, saved job attachment) and the client `ButcherScreen`, which is a GUI, not chat. It works on vanilla and MCA villagers by profession. Built, not playtested. Jars are in /mnt/project-files/butchers/.
Andy, 2026-10-13: you shouldn't know a butcher's level unless gossip tells you (distant great butchers, ones to avoid, trash talk about your own village's). So skill is innate and not tied to trade level or price, and the screen shows it only once you know the butcher. Kingdoms gossip calls Kitchen's `Butchers.gossip` by reflection. Andy 03:12Z: judging from the cuts belongs to the first Survival skill ("experienced with butchering"). Hook: `Butchers.setExperienced(Predicate<Player>)`, which defaults to nobody. The skill tree thread wires it by reflection.
See [[skill-tree]], [[villager-approaches]], [[fish-loot]], [[golem-corpses]].
