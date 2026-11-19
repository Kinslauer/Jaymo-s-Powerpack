---
status: open
for: claude
from: claude-cloud-npc-fights-thread
asked: 2026-11-19
thread: cmsg_01638ouFWZKwEZ1ET74kBk9BMQRVdw2UG8i8DuF3icoij2
claimed_by: 
pr: 
suggested_owns:
  - src/main/java/com/jaymospowers/power/zfighter/brawl/ (string runner, ki moves)
  - src/main/java/com/jaymospowers/power/zfighter/ZFighterGift.java
  - src/main/java/com/jaymospowers/power/zfighter/ZFighterPatrol.java (fight step)
---
# NPC fights patch 2: strings, chases and ki (Powers)

**Andy (2026-11-19):** linked an Anime Clash video of Minecraft anime NPCs fighting ("i like the way these npcs fight, can our powered npcs be similar? id like to see a tyrant warrior born and a galactic patrolman fighting this intensely later game"), ticked all 16 rows of https://claude.ai/artifact/RC1TV2nTdekjxETMhkp8og with the note **"they should be able to employ these techniques against players as well."**, then said "send this to PC claude".

**Full plan:** `docs/NpcFights-Plan.md` on branch `claude/project-thread-7kwvhc` (rows F1-F16, what exists today with file names, and the patch split). The video's mod is someone else's: ideas only, our own code, sounds and art. One PR per patch, change note `changes/<mod>/<branch>.md`, never `version=`/MODLOG/codemap; tuning in `static final` constants at the top of each class; compile-check; say in the PR it has not been played. Every move works against players too.

**What's wanted** (plan "Patch 2", rows F4 F5 F7): the rogue's blow-string runner moved into the shared brawl package so patrolmen and Z Fighter gifted villagers throw 3-6 hit strings too (F4; the patrolman's single 7-damage punch goes); finishers that throw 8-15 blocks by rank, a rush after, then a mid-air follow-up or a slam down (F5); tyrants and patrolmen get a short beam on a 15-25 s cooldown, seeking volleys and a visibly gathered charged blast (F7). All aim at players too.

**Wait for:** npc-fights-1-brawler merged (it makes the brawl package). Can run beside patch 3 after that.

**Where to start:** `ZFighterRogue` (combat, beginBlow, strike, rushing), `ZFighterCombo.STRINGS`, `ZFighterImpact`, `ZFighterKi` (volley, wave), `ZFighterGift` (Wave Beam), client `ZFighterOrbs`.

## Notes
- 
