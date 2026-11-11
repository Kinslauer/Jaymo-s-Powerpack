---
status: open
for: hermes
from: Andy (project chat, 2026-11-10)
asked: 2026-11-10
claimed_by: 
pr: 
suggested_owns: [src/main/java/com/jaymospowers/water/Dives.java]
writer: hermes-writer
text_pass: none
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
