# Blast effects picks (Creeper's Dislikes): plan

Andy (2026-11-28) linked **Creeper's Dislikes** (Eugene2000, CurseForge 1791057) and asked: "can we use this? i
especially want better fx for cannon and catapult impacts". It has a Fabric 26.3 build (0.9.10, needs Cloth Config),
but it is **All Rights Reserved**: the unchanged jar could be listed by its CurseForge id, but nothing of it may be
changed, copied or built on. It also hooks only vanilla `ServerExplosion`, and our cannon shots, ship shots, mortar
shells and mangonel stones never make one (they use `Bombardment.crater` and their own particles), so the jar would
not change a single gun or catapult hit, while doubling our own blast systems on creepers and TNT. So nothing is
shipped or copied: these are our own versions of the ideas, our own code, names and art. Pick list:
https://claude.ai/artifact/E4qx7A7XAAR7YvrkQQoZeL (db doc `picks/andy`). He ticked all 18 rows.

Standing rules that shape all of it: mob griefing always on; no free projectiles; blood toned down, no pixel blood (so
no pixel overlays either); performance matters (2026-11-27 lag work): effects are drawn on each client from one small
packet per hit, never by streaming hundreds of server particles, and no new entities per hit except where a row asks.

## What we already have (don't build again)

- `BlastGore` (Powers): gibs and dismembering by closeness to a blast; Kitchen's meat chunks; ragdoll knockback caps.
- `Bombardment` (Kingdoms sailing): cannon and mangonel craters dug a few columns a tick; `Collapse` brings down what
  a blast left hanging; `TreeBlastMixin` fells trees whole; `BlastMend`, `Craters` and Powers' `TerrainReturn` mend or
  heal holes; `Bombardment.ignite` lights fire balls' fires only where fire may spread.
- `BlastFright`: villagers run and horses shy at blasts within 40 blocks. `Deafened`: ears ring near any explosion
  (`ExplosionRingMixin`) or a close thunderbolt.
- Powers `core.Shakes.around` (reached from Kingdoms by `PowersCompat.shakeAround`): shake with distance falloff.
- Gunpowder already has two crafted routes: Kingdoms `black_powder` (charcoal + 2 bone meal) and Powers
  `gunpowder_from_saltpetre`; the powderhead companion (`Powderheads`) grinds both. `Dynamite` is a lit, thrown stick;
  whaling `Firebomb` is a thrown fire bottle.

## Andy's notes on the picks

- **Dust in your eyes:** "also improve the blood splatter on screen, (unrelated to this)". Sent to its own thread.
- **Renewable gunpowder:** "add both, i think we already have an alternate gunpowder recipe too". We do (two, above),
  but he asked for both, so patch 5 adds the town job that makes powder and a third crafted route as well.

## Where hits happen today

- `Cannonball` (wall cannons, siege field guns, Field Mortar shells, Musketoon): burst at the end of `tick`, particles
  per `ShotKind` (SHELL: explosion emitter, 24 large smoke, 16 flame; GRAPE: crits and smoke; CHAIN: crits; ROUND and
  HEAVY: small explosion and 10 large smoke).
- `sailing.ShipShot` (ship's guns, also ashore): the same per-kind set, plus a water splash on a miss into water.
- `SiegeStone` (mangonel): `burst` sends 40 cobble block particles, 10 poofs, a stone-break sound and a shake.

## Patches (one PR each, Andy merges)

1. **Hit effects** (Kingdoms; client side in `kingdoms/src/client`): one `ImpactFx` clientbound payload per hit
   (position, shot kind or "stone", size, struck block state, in water or not), sent to players within about 96
   blocks, replacing the server particle bursts above in `Cannonball`, `ShipShot` and `SiegeStone`. The client draws:
   a rising smoke plume, mostly white with grey and black streaks, tall for SHELL and mortar shells, a short puff for
   ROUND and HEAVY; a short bright fireball flash for SHELL only; a low dust cloud rolling out along the ground,
   coloured from the struck block (dirt brown, sand pale, stone grey, snow white), smaller for CHAIN and GRAPE;
   look-only debris chunks of the struck block (client particles, no entities) that arc out with a faint trail and
   bounce; for the mangonel stone, rubble that rolls, a stone-dust ring and a deeper thud layered on the stone-break
   sound. Shake tuned per hit through `PowersCompat.shakeAround`: SHELL and mangonel stones felt to about 24 blocks,
   ROUND and HEAVY only close. A dirt or sand hit within 8 blocks gives a soft brown haze over the screen for about a
   second (a smooth fade, not pixels). A client particle budget (cap per hit and in total) keeps a barrage cheap.
   Picks: fx-plume, fx-flash, fx-dust, fx-debris, fx-shake, fx-haze, fx-stone.
2. **Water, shot looks and fire** (Kingdoms): a hit on water draws a tall white splash column and a ring spreading on
   the surface (ship near misses read from the deck; a shell's is biggest). GRAPE kicks a spray of small dirt puffs
   across its spread; CHAIN throws wood splinters when it strikes wood (masts, walls, carts). SHELL sets a capped
   handful of fires on the outer ring of its blast, only where fire may spread (`Bombardment.ignite`'s rule). Water
   soaks up blasts: below water, `Bombardment` craters and vanilla explosions break only about 60% of their reach, with
   a block cap, so a ship holed under the waterline still holes, less. Builds on patch 1's payload. Picks: fx-water,
   fx-grape-chain, fx-embers, w-underwater.
3. **Bodies and ground** (Kingdoms + Powers): the shockwave lifts bodies up and out, more for shells, standing and
   downed alike, inside the ragdoll knockback caps. A player close to a big blast (shell, TNT or more) is concussed for
   a moment (a slow wobble of the view and slower steps), joined to `Deafened` so there is no second ringing sound.
   Thrown blocks, SHELL only: about 6 real blocks of the crater thrown on arcs by material (heavy stone low and short,
   wood and dirt higher), landing as blocks or dropping as items; every one thrown is recorded where it was taken so
   `BlastMend`, `Craters` and `TerrainReturn` still mend the hole, and a landed one is not mended twice. Picks:
   w-lift, w-concussion, w-thrown.
4. **Creatures** (Kingdoms): a vanilla creeper that is wet (rain, water, or a splash of water) fizzles: a weaker
   blast, a soaked darker look and a hiss; lightning still charges a dry one. Animals flee danger: herds and pets
   panic away from lit creepers, lit TNT and dynamite, blast sounds, lightning strikes and falling blocks, extending
   `BlastFright` (villagers and horses already run). Picks: c-wet, c-flee.
5. **Items and powder** (Kingdoms): `Dynamite` already is a thrown TNT-grade charge, so the new item is its sticky
   kind, with our own name and model: a dynamite stick wrapped in slime or honey that clings to the block or creature it hits before it goes off (built on `Dynamite`, made from dynamite and
   slime or honey, so it costs TNT-grade powder and is not a free projectile). A town powder maker: a job at the
   realm's powder garrison (`PowderMagazine`) that grinds black powder from charcoal and bone meal (or saltpetre and
   sulphur) out of the storehouse into the garrison, and sells gunpowder; plus a third crafted route, charcoal, sugar
   and sand into gunpowder (shapeless), beside the two we have.
   Picks: i-charge, i-powder.

Patch 2 builds on patch 1's payload, so 1 goes first. 3, 4 and 5 touch different files and can follow in any order.
