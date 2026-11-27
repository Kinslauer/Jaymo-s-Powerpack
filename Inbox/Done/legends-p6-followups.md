---
status: done
for: claude
from: Claude thread Rework webs and symbiote from Legends Superheroes (review of #2258)
asked: 2026-11-27
claimed_by: claude-thread
pr: 2282
---

# Legends patch 6 follow-up fixes (PR #2258, merged)

Andy: "have codex and gemini take a patch each then check it afterward". The Claude thread "Rework webs and symbiote from Legends Superheroes" checked merged PR #2258 (merge d9fa9abd30). It compiles, CREDITS is fine. Fix these on a new branch from origin/master, one PR, change note changes/powers/<branch>.md, never merge.

1. Skill-tree locks are skipped for anyone with a power: Freerun.java:609-616 (canSlide, canVault), Freerun.java:727, AirFlip.java:63, Acrobatics.java:124,187 now give Slide (TreeLocks.SLIDE), the Backflip perk, Freerunner rank-1 vaults and Sneak-tap air flips without the tree. TreeLocks are Andy's ruling: put the locks back (power users still need the perk), unless Andy rules otherwise in that thread.
2. Sneak-tap air flips (AirFlipClient) fire for every power and can clash with Sneak air moves: Talon takedown (sneak+R in air, Talon.java:144), TailSweep (sneak+Z in air), Gravity sneak+R rocks. Don't flip when the Sneak press is part of another power's combo (wait a few ticks for R/Z, or step aside when the power has a Sneak air move), and only spend Feathers when the flip happens.
3. key.jaymospowers.air_step (CoreMotionClient.java:26) is unbound by default, so nobody gets the double jump / cat leap. Give it a free default (check every bind in Powers, Kingdoms, Kitchen; see .claude/skills/powers-keys) or say plainly in the change note that it must be bound.
4. Landings (CoreMotion.landed at the top of checkFallDamage, ImpactShakeMixin.java:21-23): don't fight LandingRoll.heroLanding or RunMoves.SAFETY_TAP (skip the clip when either plays), don't throw the 14+ block dust ring twice (CoreMotion.java:106 + heroLanding) or double Gravity's own cracks (GravityPicks.java:584), and no cracks/dust when a landing roll cancelled the fall or the fall killed you.
5. KatanaForms (KatanaForms.java:69) only advances the combo when a hit lands, and the clip starts after the hit: advance on the swing (attack start) so misses chain and the clip leads the hit; make the charged draw (4 Feathers, :136-141) actually do something (damage or reach bonus) or drop its cost. Fix the change note wording.
Nits: air step's resetFallDistance (CoreMotion.java:83) wipes falls for 4 Feathers: keep fall distance unless the step really breaks the fall (reduce, don't reset). ArmedPose.java:124 / BodyWeight drop out during the 12-tick light landing on small drops: let short landing clips leave them on.

## Notes
- 2026-11-27 03:27 Andy ruled 2026-11-27 (decision card): power users get slide, backflip, vaults and air flips FREE without the skill tree. Skip item 1 (keep it as merged); say so in the change note. Do items 2-5 and the nits.
- 2026-11-27 11:03 PR #2282 is merged into master; moved to Inbox/Done.
