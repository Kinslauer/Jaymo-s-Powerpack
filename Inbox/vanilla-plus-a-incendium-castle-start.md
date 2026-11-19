---
status: open
for: claude
from: claude-cloud-vanilla-plus-thread
asked: 2026-11-19
thread: cmsg_01638ouFWZKwEZ1ET74kBk9B1dgVWnWmWqNjNa691TsU49
claimed_by: 
pr: 
---
# Vanilla+ patch A: Incendium and the demon king's start (Powers + pack)

**Andy (2026-11-19):** "place our demon king in one of its new structures and make us spawn there with that start", and later "The master that emberborn spawn next to for their nether start". He approved the plan and said "Put this work on the board".

**Full plan:** `docs/VanillaPlus-Plan.md` on branch `claude/project-thread-yw4at0` (PR #1796; on master once it merges). Read its header and the patch section first. One PR per patch, branch `claude/vanilla-plus-a-incendium-castle-start`, change note `changes/<mod>/<branch>.md`, never `version=`/MODLOG/codemap. No new original mobs (Andy, 2026-11-19: "i dont want any new original mobs until we can model and texture them better").

## Picked
- Incendium 5.5.4 (`maven.modrinth:incendium:D4vWUd23`, cf [591388, 9147441]) back into packMods. It was held out of #1796 because its `the_nether.json` lists 13 biomes directly and Fabric's `NetherBiomes` only feeds the preset, so our Gloam/Ash/Abyss (`ZFighterDemonRealm.addBiomes`) would stop generating.
- Fix: re-add every Fabric-API Nether biome to a direct-list Nether `MultiNoiseBiomeSource` (accessor on `parameters`, fresh `possibleBiomes`), timing checked against 26.3 sources. Sample the biome mix.
- Tag `jaymospowers:demon_gloam` into `incendium:structure/has_castle`; `ZFighterStarts.demon` starts you in the Forbidden Castle's throne hall beside the Master of the Gloam (bounded search, falls back to the hall).

## Checks
- Compile check against the 26.3 jars; every Minecraft API checked against 26.3 sources.
- `python3 tools/pr_checks.py` passes, and `checkPackMods`.
- Lore: approved ("The Forbidden Castle", Lore.md 2026-11-19); nothing new expected.
