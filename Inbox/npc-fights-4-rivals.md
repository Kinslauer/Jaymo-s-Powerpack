---
status: open
for: claude
from: claude-cloud-npc-fights-thread
asked: 2026-11-19
thread: cmsg_01638ouFWZKwEZ1ET74kBk9BMQRVdw2UG8i8DuF3icoij2
claimed_by: 
pr: 
suggested_owns:
  - src/main/java/com/jaymospowers/power/zfighter/ZFighterRaids.java
  - src/main/java/com/jaymospowers/power/zfighter/ZFighterRaiders.java
  - src/main/java/com/jaymospowers/power/zfighter/ZFighterPatrol.java (foe() and showdown spawn only)
  - src/main/java/com/jaymospowers/power/zfighter/ZFighterRogue.java (registerGoals only)
---
# NPC fights patch 4: tyrants vs the Patrol (Powers, lore)

**Andy (2026-11-19):** linked an Anime Clash video of Minecraft anime NPCs fighting ("i like the way these npcs fight, can our powered npcs be similar? id like to see a tyrant warrior born and a galactic patrolman fighting this intensely later game"), ticked all 16 rows of https://claude.ai/artifact/RC1TV2nTdekjxETMhkp8og with the note **"they should be able to employ these techniques against players as well."**, then said "send this to PC claude".

**Full plan:** `docs/NpcFights-Plan.md` on branch `claude/project-thread-7kwvhc` (rows F1-F16, what exists today with file names, and the patch split). The video's mod is someone else's: ideas only, our own code, sounds and art. One PR per patch, change note `changes/<mod>/<branch>.md`, never `version=`/MODLOG/codemap; tuning in `static final` constants at the top of each class; compile-check; say in the PR it has not been played. Every move works against players too.

**What's wanted** (plan "Patch 4", rows F14 F15): tyrants and raider-band men target patrolmen first (tag `jaymospowers.patrolman`), and patrolmen pick a tyrant/raider over any other `Enemy` within ~48 (F14); from a late world tier (check `WorldTiers` for which one is late-game; constant), when a raider band lands or a tyrant raid starts, a chance (~35%, constant) that a Patrol pod lands 40-80 blocks off with one named patrolman who flies to the tyrant and fights him, leaves once the tyrant falls; a rumour/Tidings line for nearby players, no screen text stating lore (F15). A player can join either side.

**Lore:** the "Old enemies" paragraph is already in Lore.md as `(new)` (Far worlds, after "The Raider's Tally"); Andy approved it on 2026-11-19.

**Runs alongside:** npc-fights-1-brawler (small overlap in `ZFighterRogue.registerGoals` and `ZFighterPatrol#foe`: keep your edits to those spots).

**Where to start:** `ZFighterRogue.registerGoals`, `ZFighterPatrol` (foe, pod landing, `ZFighterNpcs.villager`), `ZFighterRaiders`, `ZFighterRaids`, `ZFighterKingdoms.worldTier`, Kingdoms `WorldTiers`, how Tidings/rumours are sent from Powers.

## Notes
- 
