---
name: monsters-hunt-villagers
description: Andy 2026-10-26: every hostile mob attacks villagers at half damage; golems hunt and one-shot creepers (Kingdoms VillagerHunters, PR #533)
metadata:
  type: project
  modified: 2026-10-26T02:21:04.898Z
---
2026-10-26 Andy: "make them all attack villagers, just doing reduced damage, and the creepers are one shot by golems as they have less health".
- Before: only zombies, illagers and our werewolves/ogres/bleeders/zombified Hornkin/orcs/goblins/barbarians/pirates/tyrant rogues targeted villagers (MCA villagers extend Villager).
- Kingdoms `VillagerHunters` (0.7.217): skeletons, creepers, spiders (dark only), endermen, witches, wraiths, mimics, mud golems, silver skeletons, tarantulas and wild beasts target villagers at priority 3 and do VILLAGER_DAMAGE 0.5 to them. Iron golems target creepers and kill them in one hit.
- PR #533 MERGED 2026-10-26 03:4xZ as pack 0.17.2 / Kingdoms 0.7.217. Not playtested. Creeper blasts in villages still break blocks.
