---
status: open
for: hermes
from: Andy (project chat, 2026-11-10)
asked: 2026-11-10
claimed_by: 
pr: 
suggested_owns: [src/main/java/com/jaymospowers/undead/]
writer: hermes-writer
text_pass: none
---

# Undead seek real shelter from the sun

Andy: "because we made the sun more deadly to the undead, they should now seek shelter underground in caves or otherwise when the day comes, its no longer enough for them to hide under a tree's shade if the rays can reach them."

## What's wanted

1. At dawn (and whenever they find themselves in sunlight by day), sun-burning undead (zombies, skeletons, strays, the pack's own dead: old villagers, draugr, Warrior-born) look for real shelter: a cave, anywhere underground, or under a solid roof.
2. Tree shade no longer counts. If the sun's rays can reach them through or around leaves, they keep burning and keep looking.
3. Shelter checks should match how the sun now burns them, so a mob that thinks it is safe really is safe.

## Where to look

- First find where the sun was made deadlier to the undead. The cloud check couldn't find it in master on 2026-11-10 (searched for sun/burn/daylight code; only `power/chrono/mixin/FutureSunMixin` and `WebHostZombieMixin` touch undead burning). It may be unmerged work or vanilla `Mob.isSunBurnTick` behaviour. If it isn't in master, say so in the PR and ask on the task before building on a guess.
- Vanilla: `FleeSunGoal` (skeletons, strays) picks a random spot where `!level.canSeeSky(pos)`. Leaves block `canSeeSky`, which is why tree shade works today. Check both against the 26.3 sources.
- The new shelter test should treat leaves (and other non-solid blocks like glass, fences, slabs with gaps) as letting rays through. Prefer a short upward scan for a solid, full-face block, or a cave test (dark, under stone).
- Search a wider radius than vanilla's 10 blocks, path toward the nearest cave mouth or building, and dig down/stay put as a last resort only if Andy wants that (ask).
- Keep it cheap: run the search once at dawn per mob and when it starts burning, not every tick.
- Must work with old features: Futures' ashen sun (FutureSunMixin: nothing burns there), symbiote behemoths (WebHostZombieMixin), zombie players in heaven, downed/carried undead, captives.
- Change note in `changes/powers/` (and `changes/kingdoms/` if the pack's dead live there).
