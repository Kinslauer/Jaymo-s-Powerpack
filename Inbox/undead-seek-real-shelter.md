---
status: writing
for: hermes
from: Andy (project chat, 2026-11-10)
asked: 2026-11-10
claimed_by: hermes-fixer
pr: 1262
suggested_owns: [src/main/java/com/jaymospowers/undead/]
writer: hermes-writer
text_pass: 
---

# Undead seek real shelter from the sun

Andy (follow-up): "https://www.curseforge.com/minecraft/mc-mods/realisticsunburn this is the mod, i thought we had added it"

Andy: "because we made the sun more deadly to the undead, they should now seek shelter underground in caves or otherwise when the day comes, its no longer enough for them to hide under a tree's shade if the rays can reach them."

## What's wanted

1. At dawn (and whenever they find themselves in sunlight by day), sun-burning undead (zombies, skeletons, strays, the pack's own dead: old villagers, draugr, Warrior-born) look for real shelter: a cave, anywhere underground, or under a solid roof.
2. Tree shade no longer counts. If the sun's rays can reach them through or around leaves, they keep burning and keep looking.
3. Shelter checks should match how the sun now burns them, so a mob that thinks it is safe really is safe.

## Where to look

- **Build the deadlier sun first; it was never added.** Andy (2026-11-10) thought RealisticSunBurn (https://www.curseforge.com/minecraft/mc-mods/realisticsunburn, project 1485037) was in the pack, but it isn't, and it's All Rights Reserved, so remake the idea in our own code (no copying). What it does: instead of vanilla's straight-up sky check, cast rays toward the Sun's real position from several points on the mob's body. Andy (2026-11-10): "it should be whenever a ray hits the undead": any part of the body in the sun burns, not just the head (e.g. head, chest, legs and the corners of the hitbox; a few rays, not every tick for every point). Low morning/evening sun casts long shadows from walls, deep holes and canyons shade mobs, and glass, flowers and torches let the ray through. Leaves must let it through too (Andy: tree shade no longer counts). Hook `Mob.isSunBurnTick` (check 26.3); Kingdoms' `astronomy/SunMoon.direction/sun` already gives the Sun's direction, so use it when Kingdoms is loaded and fall back to the day-time angle otherwise. Keep FutureSunMixin and WebHostZombieMixin working.
- Then the shelter search below uses the same ray test, so a spot counts as shelter only if the sun ray can't reach it now or for the next while (roof, cave, deep shadow) for the whole body.
- Vanilla: `FleeSunGoal` (skeletons, strays) picks a random spot where `!level.canSeeSky(pos)`. Leaves block `canSeeSky`, which is why tree shade works today. Check both against the 26.3 sources.
- The new shelter test should treat leaves (and other non-solid blocks like glass, fences, slabs with gaps) as letting rays through. Prefer a short upward scan for a solid, full-face block, or a cave test (dark, under stone).
- Search a wider radius than vanilla's 10 blocks, path toward the nearest cave mouth or building, and dig down/stay put as a last resort only if Andy wants that (ask).
- Keep it cheap: run the search once at dawn per mob and when it starts burning, not every tick.
- Must work with old features: Futures' ashen sun (FutureSunMixin: nothing burns there), symbiote behemoths (WebHostZombieMixin), zombie players in heaven, downed/carried undead, captives.
- Change note in `changes/powers/` (and `changes/kingdoms/` if the pack's dead live there).

## Notes
- 2026-11-11 01:19 Review of #1262 (head 025ce16), cloud reviewer: checks green, changes needed.
1. Andy's ruling (in the task since 00:12Z: 'it should be whenever a ray hits the undead'): any part of the body in the sun burns. SunBurnMixin.jaymospowers$sunRay and SeekShelterGoal.canUse test only mob.getEyePosition(), so a zombie with its head under a roof edge and its legs in the sun is safe. Test a few points (feet, chest, head; the hitbox corners at feet and head) and burn if any is exposed. Shelter in findShelter must cover the whole body too (test feet and head at the candidate, not only +1.62).
2. Cost: findShelter scans up to 24 rings x 5 heights (about 12,000 spots) every 40 ticks for each exposed mob, each canSeeSky-false spot casting an 80-step ray. At dawn on open plains with no shelter that repeats for every zombie and skeleton. Search once when the mob starts burning, then back off (e.g. 200+ ticks after an empty search), cap the rays per search, and keep a found spot until the sun ray reaches it.
3. Change note: its first line says 'a ray from their head'; update it to match 1.
OK: vanilla isSunBurnTick is mirrored faithfully (checked against 26.3 bytecode: MONSTERS_BURN, the light/random gate, rain, powder snow), SunMoon.sun(Level, Vec3) exists, FutureSunMixin and WebHostZombieMixin (isSunSensitive) keep working, leaves/glass are not isSolidRender.
Not tested in game.
- 2026-11-10 20:26 hermes-fixer took over the review fixes from hermes-b.
- 2026-11-11 21:32 hermes-fixer's claim had no sign of work for 30 min: handed back as changes.
