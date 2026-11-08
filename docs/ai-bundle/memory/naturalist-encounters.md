---
name: naturalist-encounters
description: PR #92 (pack 0.8.110 / Kingdoms 0.5.56 / Kitchen 0.4.34) - Naturalist + BAP beasts as wild encounters, Fangs 'n Claws-style legends, beast gossip/quests, bat horse + nightmare
metadata:
  type: project
---
2026-10-13: Andy asked for Naturalist's hostile mobs as rare random encounters near players in the wild.
Naturalist 2.0.6 (Starfish Studios, own 26.3 Fabric build; code MIT, art ARR) is a packMods entry, shipped unchanged.
Kingdoms `Encounters` removes its predators/elephant/mammoth from natural spawning; encounters fire 256+ blocks from
settlements. Andy chose to keep Naturalist's peaceful animals (`WILDLIFE`).
Better Animals Plus (1.19.2 only, CC BY-NC-SA 4.0): Kingdoms `wild/` rebuilds feral wolf, coyote, tarantula, moose.
Fangs 'n Claws (Andy picked "core plus extras"): Kingdoms `monsters/` werewolf (gold/silver to hurt), ogres, wraiths,
silver skeleton, mud golem, chest mimic (6% of wild loot chests), silver sword; models/art from Mo' Creatures (GPL-3).
`Sightings` drives villager gossip and HUNT/SURVIVE beast quests. `equestrian/MythicMounts`: bat horse (flies, jump to
climb) and nightmare breeds, appended to `Breed` (ordinal-synced).
PR #92 merged 2026-10-13 as pack 0.8.110 (after #93/#94). Not playtested.
Related: [[alexs-mobs]], [[equestrian-plan]], [[rarer-quests]].
Follow-up PR #95 merged 2026-10-13 (pack 0.8.111 / Kingdoms 0.5.57): silver ore + ingots in Powers tinkering (Tinkering.registerSilver, tools/gen_silver.py), silver Tinkering metal (Smite), silver sword crafted from #c:ingots/silver, tinker silver hurts werewolves; Andy asked mythic mounts be "exceedingly rare" (CHANCE 1/6000, one wild at a time).
Worldgen JSON note (26.3): ore feature lives in worldgen/feature with "state" as a plain block id string, not {"Name":...}.
