# Cruor and BONK! picks: plan

Andy (2026-11-12) linked **Cruor** (Raiiiden, CurseForge 1729277: blood and wounds) and **BONK! player collision
physics** (kamussy, CurseForge 1745819). Both are All Rights Reserved and have no 26.3 Fabric build, so nothing is
shipped or copied: these are our own versions of the ideas, built on our blood (Powers `core/blood`, `client/blood`),
wounds (`core/wounds`), dismemberment and Kitchen's ragdolls. Pick list:
https://claude.ai/artifact/XyByqVAcRD5MdsU9VKHP7c (db doc `picks/andy`). He ticked all 16 rows.

Standing rules that shape all of it: blood stays toned down, no pixel blood; new features work with the old ones
(freerun, ledge grabs, boxing, Free Rein); no screen text for discoveries (sounds and speech are fine).

## Andy's notes on the picks

- **Sore knuckles:** "same goes for bare hands on armor or stoneborn skin, or mobs with tough enough hide."
- **Head bump:** "hitting hard enough can ragdoll you."
- **Splash-back:** "does not go away until thoroughly washed especially if allowed to dry at all. rotten blood lasts
  the longest, stinks the worst, and is hardest to get rid of. EVERYONE will comment on it."
- **Bleeding stumps:** "the unarmed start starts with the lost limb capped."

## Patches (one PR each, Andy merges)

1. **Collisions** (Powers, `core/collide`): wall slams for anyone knocked or blown into a wall at speed (damage by
   impact speed, thud, stagger, a hard slam knocks a player limp through Kitchen); a small bump for sprinting flat out
   into a wall (skips freerun, vaults, ledge grabs, wall runs, crouching); a head bump jumping into a ceiling, a hard
   one knocks you limp; sore knuckles punching or mining hard blocks bare-handed, and punching armoured bodies,
   Stoneborn or tough-hided mobs (fist gear spares you); a galloping horse that crashes throws its rider (dragons and
   flying mounts left out).
2. **Wound marks** (Powers client): marks drawn on the body where and how a hit landed (slash along the swing,
   puncture, bruise, blast peppering), fading as Wounds mends that part; fresh cuts drip into BloodTrail.
3. **Stumps and impacts** (Powers + Kitchen): stumps and severed parts bleed a while then crust over (Rubber-folk
   skip it); the unarmed start begins with its lost limb already capped; a ragdolling body that hits the ground or a
   wall hard leaves a smear.
4. **Splash-back that lingers** (Powers + Kingdoms): heavy close hits spatter the attacker and bystanders; the blood
   stays until properly washed (a dip only half cleans dried blood), dries, then rots; rotten blood lasts longest,
   stinks (flies, a smell others notice) and takes soap or long scrubbing; every villager remarks on it.
5. **Blood in the world** (Powers client): surface-aware stains (wool soaks, dirt drinks, glass and ice smear), rain
   washes open ground, soaking through thin blocks, clouds in water and a hiss on lava, fresh weapon drips, quiet blood
   sounds, and a Light / Normal / Heavy client setting with an F3 counter.

Patches 2 and 5 both touch `client/blood`, so they run one after the other. 1, 3 and 4 touch different files.
