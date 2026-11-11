---
status: in-review
for: hermes
from: Andy (project chat, 2026-11-10)
asked: 2026-11-10
claimed_by: hermes-fixer
pr: 1263
suggested_owns: [src/main/java/com/jaymospowers/water/Dives.java]
writer: hermes-writer
text_pass: done
---

# Belly flops into water hurt

Andy: "https://www.curseforge.com/minecraft/data-packs/realistic-damage-water-fall-damage-vivi2 this should happen if you belly flop or hit the water without a dive"

## What's wanted

1. Falling into water from a height hurts unless you enter it in a dive. A belly flop (flat, face down) or any landing that isn't a dive takes fall damage.
2. A proper dive (head first, body roughly vertical) takes little or no damage, as now.
3. Short drops stay harmless. Scale the damage with fall distance and speed. Pick the numbers yourself (as constants at the top of the class) and say them in the PR.

## Notes

- **Build on what's there: `water/Dives.java` already has the entries.** Falling 4+ blocks into water 2+ deep: Sneak held early = cannonball, Sneak pressed just before = clean dive, neither = belly flop, which today only stings 1-2 damage from 12+ blocks. This task makes the flop (and other non-dive entries) hurt for real: raise FLOP_DAMAGE/FLOP_MAX into real fall damage scaled by height. A clean dive and Leap of Faith's head-first dive (`Freerun.dives`, looking down) stay safe or near-safe. Decide where a cannonball sits (it's feet first, so a middle case) and say it in the PR. Don't add a second system beside Dives.

- The data pack is 1.20.1 and All Rights Reserved, and its page gives no numbers. Remake only the idea in our own code; copy nothing.
- Check how the player is falling. Use the pitch and the freerun pose: `freerun/moves/RunMoves` (synced POSE), `SkyDive` (spread-out sky dive: landing in that pose is the belly flop), and any dive pose there. Look straight down near the water = dive. Feet first (a "pencil" drop) should be a middle case: less than a flop, more than a dive.
- Must fit the existing moves: the sky dive, rolls (Left Alt), ledge grabs and web parkour. Wind and other flyers, and powers that cancel fall damage, keep their protection.
- Vanilla water resets `fallDistance` on entry. Check in the 26.3 sources where (`Entity.updateInWaterStateAndDoFluidPushing` / `checkFallDamage`) and read the speed before it's cleared.
- Mobs can follow the same rule if it's cheap; players first.
- Add a loading-screen hint (StoryHoldScreen HINTS) and a change note in `changes/powers/`.

## Animations (Andy asked 2026-11-10 whether dives are animated)

Today only the falls are posed: FreefallPose (flailing, and the head-first dive for Wind users and Leap of Faith looking down) and RunMovesPose (the sky dive spread flat). The water entries in Dives have no pose: holding Sneak just shows the vanilla crouch. Add poses everyone can see, in the same style as FreefallPose (client, worked out from synced state):
- Clean dive: arms forward over the head, body straight, head first.
- Cannonball: knees hugged to the chest, tucked into a ball.
- Belly flop: flat and spread at the moment of hitting, a short wince after.
Each starts when Sneak marks the entry in the air (or the flop when you hit) and eases out under water.
- 2026-11-11 01:29 hermes-b's claim had no sign of work for 30 min: handed back as open.
- 2026-11-11 01:53 Review (head ff16783), cloud reviewer: checks green, changes needed. 1. Poses never show: Dives.java:127 stores level.getGameTime() but DivePose.java:47 compares it with player.tickCount (the player's age), so age is hugely negative and extract() always clears the pose. Use player.level().getGameTime() + partialTicks on the client. 2. Leap of Faith's head-first dive now hurts: Dives.java:118 makes every no-Sneak entry a FLOP, so Freerun.dives(player) (and Wind users' head-first fall) take up to 8 HP; the task says they stay safe. Treat Freerun.dives / looking down past DIVE_LOOK_DOWN as DIVE. 3. Speed term is ~0: Dives.java:170 reads the server player's getDeltaMovement(), which stays near zero for players (see Standstill.java:340); use getKnownMovement() (as Armory/Leaves do), then re-check the PR's numbers (as is a 12-block flop is 4 HP, not about 6). 4. Dives.java:92 calls setAttached(ENTRY, 0L) every tick for every player out of water; only set it when it isn't already 0, so it doesn't sync each tick. 5. Loading-screen hint Andy asked for is still missing (ok to do as a small Kingdoms change note in this PR). Not tested in game.
- 2026-11-11 03:35 hermes-fixer's claim had no sign of work for 30 min: handed back as changes.
