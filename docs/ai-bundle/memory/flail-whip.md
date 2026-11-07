---
name: flail-whip
description: Flail rework + leather whip (Web Whip mechanics on weapons), PR #487 MERGED 2026-10-24 22:26Z, pack 0.15.148 (2026-10-24)
metadata:
  type: project
---
Andy 2026-10-24: "Can these mechanics replace the flail? And maybe add a single leather whip?" Read as the Web Whip's swing-overhead-and-slam (Andy not yet confirmed the reading).
PR #487 (branch claude/project-thread-d784j7, pack 0.15.148; master took .147 via #475), not playtested; jar /mnt/project-files/flail-whip/.
- Balkon's flail (weaponmod:flail.*) taken over by mixin/WeaponModFlailMixin (ItemFlail use/hurtEnemy) + WeaponModMeleeUseMixin (ItemMelee use duration/anim/release): hold use = whirl overhead, release = overhead slam where you look. No more ball throwing.
- jaymospowers:leather_whip: crack 7 blocks, hold = wrap, release = haul light catch overhead or yank heavy one.
- armory/Lash: synced Lash.State player attachment; path computed on both sides, client/armory/LashRenderer draws it. Tuning constants at top of Lash.
- Server boot verified the string-target mixins apply (getUseDuration 72000 / TRIDENT on flail.iron).
- Andy 2026-10-24 21:39 "Looks good", added: Finishers.Kind FLAIL (Ball and Chain) + WHIP (Garrotte), everyone's; whip rappel (Lash.HOOK: crack a wall, hooks a top <=7 blocks above feet, climb runs client-side in LashRenderer.climb, sneak lowers).
- Andy 2026-10-24 23:41: executions only with Executioner; untrained whip slam wears it (whip = Finishers.improvised). PR #491, pack 0.16.1.
- Andy 2026-10-25: flail look like the Flails mod (MIT, ingobeans; nothing copied): whirl round the held-out hand at waist height, LashPose poses the arm, chain of links, small ball, client draws the whirl from the first press (LashRenderer.shown). Use anim NONE now. PR #491, pack 0.16.2.
- #491 MERGED 2026-10-25 01:13Z. Andy 2026-10-25 03:17: physics like the Whip mod (MIT, Cukkoo; idea only): Verlet ropes (LashRenderer.Rope) from the drawn handle tip (LashSockets + 4 client mixins), head pinned to Lash.head while acting; bare flail handle in hand (WeaponModFlailHandMixin), whip leather_whip_handle model; material-textured heads, iron_chain strips; whip reach 10 (Andy's pick); rappel hang + walk-to-climb. PR #503 MERGED 2026-10-25 ~03:50Z as pack 0.16.11 (after #504 took 0.16.10). Client mixins untested at runtime.
- Andy 2026-10-25 12:08: First-person Model draws your body+held item in the level, so LashSockets picks the newest frame-stamped tip. Weak charged left-click swipe (Lash.SWEEP; flail 0.22x blow, whip 1.5). Rappel = taut rope, jumps haul 1.4 (walk no longer climbs), Sneak+jump bounds down. Whip crack flings items. PR #518 MERGED 2026-10-25 21:17Z as pack 0.16.24.
Related: [[armory-age-of-weapons]], [[yank-heave]].
