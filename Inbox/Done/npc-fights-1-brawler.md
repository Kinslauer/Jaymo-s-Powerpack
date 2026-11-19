---
status: done
for: claude
from: claude-cloud-npc-fights-thread
asked: 2026-11-19
thread: cmsg_01638ouFWZKwEZ1ET74kBk9BMQRVdw2UG8i8DuF3icoij2
claimed_by: pc-claude (npc-fights-1 track)
pr: 1825
suggested_owns:
  - src/main/java/com/jaymospowers/power/zfighter/brawl/
  - src/main/java/com/jaymospowers/core/gifted/GiftedAI.java
  - src/main/java/com/jaymospowers/power/zfighter/ZFighterRogue.java (movement/defence only)
---
# NPC fights patch 1: the fighter brain (Powers)

**Andy (2026-11-19):** linked an Anime Clash video of Minecraft anime NPCs fighting ("i like the way these npcs fight, can our powered npcs be similar? id like to see a tyrant warrior born and a galactic patrolman fighting this intensely later game"), ticked all 16 rows of https://claude.ai/artifact/RC1TV2nTdekjxETMhkp8og with the note **"they should be able to employ these techniques against players as well."**, then said "send this to PC claude".

**Full plan:** `docs/NpcFights-Plan.md` on branch `claude/project-thread-7kwvhc` (rows F1-F16, what exists today with file names, and the patch split). The video's mod is someone else's: ideas only, our own code, sounds and art. One PR per patch, change note `changes/<mod>/<branch>.md`, never `version=`/MODLOG/codemap; tuning in `static final` constants at the top of each class; compile-check; say in the PR it has not been played. Every move works against players too.

**What's wanted** (plan "Patch 1", rows F1 F2 F3 F6 F8 F16): one shared brain every powered NPC runs (tyrants/raiders/champions, Galactic Patrolmen, gifted villagers): free 3D flight when the foe or the fighter is airborne (F1), dashes, sidesteps and circling (F2), vanish dodge behind the attacker with a fading afterimage (F3), faster thinking and smaller gaps with rank (F6), NPC guard and timed parry with `ClashFx` sparks and a counter (F8), all scaled by one rank number from the fighter's kind/rank plus the nearest player's world tier (F16). Rank 0 behaves about as today. Movement/defence rows apply to every power's gifted folk in that power's flavour.

**Where to start:** `power/zfighter/ZFighterRogue` (customServerAiStep, step, rushing), `ZFighterPatrol#fight`/`#foe`, `core/gifted/GiftedAI` + `GiftKit`, `ZFighterHighSky.steer`, `ZFighterImpact.rush`, `ZFighterGuard` + client `ZFighterGuardPose`, `combat/ClashFx`, `ZFighterKingdoms.worldTier`, `client/TerrainAhead`.

**Runs alongside:** npc-fights-4-rivals (only targeting/spawning; small overlap in `ZFighterRogue.registerGoals` and `ZFighterPatrol#foe`). Patches 2 and 3 wait for this one to merge.

## Notes
- PR #1825, CI green, not played. Travel flight is `Brawl.flyTo(Mob, Entity)` (true on arrival); package is core/brawl. See PR for gaps.
- 2026-11-19 09:09 PR #1825 is merged into master; moved to Inbox/Done.
- 1b: PR #1832 (feints and patrolman ranks), CI green, not played.
