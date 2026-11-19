---
status: open
for: claude
from: claude-cloud-npc-fights-thread
asked: 2026-11-19
thread: cmsg_01638ouFWZKwEZ1ET74kBk9BMQRVdw2UG8i8DuF3icoij2
claimed_by: 
pr: 
suggested_owns:
  - src/main/java/com/jaymospowers/core/BeamClash.java
  - src/main/java/com/jaymospowers/power/zfighter/ZFighterImpact.java
  - src/main/resources/data/jaymospowers/tags/block/brawl_breakable.json
  - src/main/java/com/jaymospowers/power/zfighter/brawl/ (clash, power-up)
---
# NPC fights patch 3: clashes and spectacle (Powers)

**Andy (2026-11-19):** linked an Anime Clash video of Minecraft anime NPCs fighting ("i like the way these npcs fight, can our powered npcs be similar? id like to see a tyrant warrior born and a galactic patrolman fighting this intensely later game"), ticked all 16 rows of https://claude.ai/artifact/RC1TV2nTdekjxETMhkp8og with the note **"they should be able to employ these techniques against players as well."**, then said "send this to PC claude".

**Full plan:** `docs/NpcFights-Plan.md` on branch `claude/project-thread-7kwvhc` (rows F1-F16, what exists today with file names, and the patch split). The video's mod is someone else's: ideas only, our own code, sounds and art. One PR per patch, change note `changes/<mod>/<branch>.md`, never `version=`/MODLOG/codemap; tuning in `static final` constants at the top of each class; compile-check; say in the PR it has not been played. Every move works against players too.

**What's wanted** (plan "Patch 3", rows F9 F10 F11 F12 F13): trading blows, where two fighters (NPC or player) landing on each other within ~3 ticks clash fist to fist (sparks, shockwave ring, both pushed back, capped per pair) (F9); `BeamClash` generalised from `ServerPlayer` to `LivingEntity` so NPC beams lock into struggles with NPCs and players, NPC side pushing by rank/power level with surges (F10); dust rings, ground cracks, a dust trail behind thrown bodies, `Shakes.around` near (F11); thrown bodies and slams break soft blocks only (a `brawl_breakable` block tag: leaves, glass, dirt, sand, snow, plants), never in settlements/claims or with `mobGriefing` off (F12); a once-per-fight half-health power-up with a 30-40 tick interruptible aura flare (rocks lift, ground shakes), then a stronger form for tyrants, patrolmen and Z Fighter gifted (F13).

**Wait for:** npc-fights-1-brawler merged; patch 3 registers patch 2's NPC beam for struggles, so if 2 isn't merged yet, register `ZFighterGift`'s Wave Beam and leave a hook.

**Where to start:** `core/BeamClash` (the ServerPlayer API near lines 164, 310, 327), `ZFighterWaveBeam` (how a beam registers), `ZFighterImpact` (slam, ring), `core/Shakes`, `core/Soundscape`, `combat/ClashFx`, how other features check Kingdoms land before breaking blocks.

## Notes
- 
