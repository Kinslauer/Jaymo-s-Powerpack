# Plan for 0.7: your edits to Abilities.md

Every item points back to its row in `docs/Abilities.md` (**L21** = line 21). Size: **S** small tweak, **M** a
session's work, **L** a new system. Anything marked **❓** needs your answer before I build it (questions at the end).

---

## Phase 1: the Gravity rework

### Lift (R)
| Ref | Change | Size |
|---|---|---|
| L18 | Passive item pull grows with mastery: 4 blocks to start, up to 12 fully mastered. | S |
| L21, Q1 | **R + left click: Push.** Right click stays choke/slam; left click on a held creature launches it straight away from you. 20 blocks fresh, up to 50 mastered. It goes *through* walls it's inside, and shoves loose blocks out of its way. Every block it hits costs it momentum; it stops when its momentum runs out or it dies. | M |
| L22 | Aerial slam tilts 30° instead of 40°. | S |
| L24, Q2 | **Tearing blocks becomes a held struggle.** Hold R at a block: the screen shakes slightly and the block trembles. Let go after 2 s or more and it comes loose. Hold longer and it grows from 1 block up to a ragged crater (about 3×3, pitted like a small creeper blast, not a cube). Max size grows with mastery. Left click throws it. On impact it shatters, and each block has a 30% chance to drop. | M |
| L34 | **Collapse:** letting go of Lift no longer flings. The held creature is slammed to the ground under heavy gravity, with amplified fall damage. | S |

### The other abilities
| Ref | Change | Size |
|---|---|---|
| L29 | **Rock flight** (ascended or corrupted; *hold* V, a tap still toggles Weightless). You stomp; the solid blocks in a 3×3 under and around you rip up and clump together under your feet. You stand on the slab, steering it like Wind flight (same controls, same air brake), but it hugs the ground: it can't climb much and is always sinking slightly, like falling debris. Particles come only from the slab's underside. Let go to drop off (the slab crumbles). | L |
| L30 | **Crush no longer damages.** Everything caught in it is made so heavy it can't move for the pin time. Only creatures in armor take damage, scaled by how much armor they're wearing. | S |
| L31 | Cave-in skips snow and prefers heavy blocks (stone, deepslate, dirt) when there are any. | S |
| L32 | Event Horizon deflects projectiles from 6 blocks instead of 3. | S |

### C moves
| Ref | Change | Size |
|---|---|---|
| L33, Q3 | **Ascended C, Meteor.** Aim at a spot (48 blocks). A glowing rock is torn out of the sky and falls on it over about 2 s, rendered as a real tumbling chunk of stone and magma trailing fire and smoke, with a shadow growing on the target. Impact: a 25-block blast (up to 20 hearts at the centre, falling off to the edge), a crater, flying debris and a shockwave. 45 s cooldown. ❓ block damage on the server | L |
| L35, Q4 | **Corrupted C:** today's Singularity black hole moves over as is. | S |
| Q5 | **Both, Collapsing Horizon: Skyfall** (my surprise): your ultimate. Gravity inverts in a 20-block radius. Every creature and every loose block lifts off the ground and falls *up* into the sky for 3 s, suspended and struggling. Then you close your fist, gravity comes back at 5×, and the whole lot comes crashing down at once: fall damage from height times weight, blocks shattering on landing, a ground-shake. 90 s cooldown. | L |
| L36 | **Collapsing Horizon passive.** You're so dense that blocks near you sometimes tear loose. They tremble and struggle against your pull, then drift in, speeding up as they get closer. It keeps every ascended and corrupted effect except the corrupted fling. ❓ which blocks, and what happens when they reach you | M |

### Fate
| Ref | Change | Size |
|---|---|---|
| L37 | Ascension: surviving a hard fall (5+ hearts of fall damage) counts toward the 30, alongside kills by weight. Everything still has to be mastered. | S |
| L38 | Corruption: reaching the Nether corrupts outright (the meter goes away). | S |

---

## Phase 2: quick changes across the other powers

| Ref | Power | Change | Size |
|---|---|---|---|
| L60 | Wind | Drifting down leaves animated cloud wisps that fade shortly after you land. | S |
| L63 | Wind | Tailwind and Gust get separate cooldowns. | S |
| L69 | Wind | Stormbound mode: electric particles on you, and a brief blue vignette for you and nearby players when it turns on. | S |
| L70 | Wind | Tempest mode: gusting sounds around you, and a brief white vignette for everyone nearby. | S |
| L74 | Wind | Thunderhead: while it lasts, everyone nearby gets the screen flashing black and white now and then, in time with lightning and thunder. | S |
| L75 | Wind | Thunderhead costs the most hunger, and you can't eat while any mode is on. | S |
| L77 | Wind | Ascension: any breeze kill has a 66% chance to drop the Gale Heart (no flight requirement). Mastery is still required to use it. | S |
| L79 | Wind | Stormbound cures itself after 2 days ❓, or in a snowstorm. | S |
| L91 | Web | Yank: a **tap** pulls; **holding** keeps the lead and hitch as now. | S |
| L92 | Web | Each web line takes 1-4 punches to cut (random per line). | S |
| L114 | Arsenal | Rummage can turn up useful tools: flint and steel, fishing rod, shears, compass and the like. | S |
| L116 | Arsenal | Return refunds in proportion: the more time the weapon had left, the more of Reach's cooldown comes back. | S |
| L128 | Arsenal | The Void Arsenal cures itself after 2 days ❓. | S |
| L134 | Chrono | Nudge: R speeds **you** up; Sneak + R slows the target. | S |
| L136 | Chrono | Stasis grows from 3 s to 6 s with mastery. | S |
| L141 | Chrono | Chrono Punch is twice as fast (10 punches in half a second). | S |
| L144 | Chrono | Corruption: shatter one of your echoes in the Nether and it's destroyed for good, which corrupts you. | M |

---

## Phase 3: the bigger features

### Web and the symbiote
| Ref | Change | Size |
|---|---|---|
| L86 | **Pendulum swinging:** real arc physics, speeding up through the bottom and stalling at the top. | M |
| L87 | **Slingshot:** the floor under you always cracks as you draw. If it breaks and you're standing on air, the lines snap and you ragdoll for 2 s. | S |
| L88 | **Web wings fly like an elytra:** pitch for speed, dive to gain, pull up to climb. | M |
| L93 | **Spider-Sense:** up to 5 dodge charges, each on its own cooldown. Every dodge plays an acrobatic animation: a spinning sidestep, a backflip into a crouch, a duck and roll, or a weave. | M |
| L96 | **Tendril Grab at a block:** tendrils from both sides reach out and haul you in, you turn your back to the wall, and several tendrils fan out and slam into it, with crack marks where each one hits. | M |
| L98 | **Living Armor rework.** X now brings out only the **tendrils**, over any suit, and they carry everything the armor's tendrils do now (strikes, grabs, chunks). Veins course and writhe over the body. The **big body** appears only near death (it takes over to defend itself, gets stressed, and the bond suffers) or in a random takeover when the bond is weak. ❓ | L |
| L103 | **Fire terror:** the symbiote always shoves you away from fire, or runs you off if there's no room. Using flint and steel makes it recoil and scream, then do something unpredictable (drop the item, lash out, bolt). | M |
| L104 | **Eating animation:** a tendril picks up the corpse and brings it to a huge mouth: one bite for small things; cow-sized ones are bitten in half (blood), then one more chomp. A few seconds of chewing, then the head shrinks back (or vanishes if you're not in the suit). | M |
| L105 | **Moods:** messages get darker as stress rises. With a good bond, takeovers are curious instead: it grabs a mob or player, swells its head and stares for 2-8 s, then either puts them down ("just looking") or attacks and hands control back. | M |
| L106 | **Weakness vignettes:** fiery orange-red for fire, bright bluish-white for loud sounds. | S |

### Arsenal
| Ref | Change | Size |
|---|---|---|
| L119 | **Relics become generated weapons with abilities**, themed by where they're found. Starting set: *Soul Saber* (Nether; life steal and a ranged slash) and *Grand Shield* (reflects projectiles), plus a few more per dimension that I'll propose. | L |
| L120 | **More swords in stones:** Durandal variants with their own design and stone, in their own places: a blackstone one in bastions, an end-stone one in End cities or islands, a sculk-and-deepslate one in the Deep Dark, and so on. | M |
| L125 | **Void Armory (both): flintlocks.** You gain a pair of flintlock pistols. Pinned, they're the only thing you draw; fire one, drop it, and a freshly loaded one is already in your hand. | M |

---

## Answers (these override anything above)

1. **Collapsing Horizon block pull (L36)** is a blessing and a curse: it tears loose *any* block near you, bases
   included, wherever or however it was placed. Heavy blocks (stone, obsidian and the like) only rarely (10% of the
   usual chance); light and natural blocks far more often. Blocks that reach you **orbit you as entities** until:
   - grabbed with **R** (Lift picks an orbiting block like a chunk); **right click while holding R** puts it in your
     inventory;
   - or **Crush (B)**: charging Crush draws the orbiting blocks to the target site, adding to the impact's damage; on
     release they dissipate in a radial damaging shockwave.
2. **Meteor and Skyfall break blocks everywhere**, bases included. They're meant to be devastating, used rarely with
   heavy consequences. No griefing switch.
3. **Cures after time:** 3 Minecraft days (not 2) for Stormbound (L79) and the Void Arsenal (L128).
4. **Living Armor:** the big body keeps the extra absorption hearts, the gallop and the 3× hunger; the tendrils mode
   does not get them.
5. **Skyfall** is approved as the Collapsing Horizon ultimate.

## Questions before I build (answered above)

1. **Collapsing Horizon block pull (L36):** this pulls blocks out of the ground, *including your friends' builds* on the server. Should it take only natural terrain (stone, dirt, sand, gravel, logs, leaves)? And when a block reaches you, does it shatter (with a 30% drop like chunks), orbit you, or go into your inventory?
2. **The Meteor and Skyfall on the server:** should the meteor's 25-block crater really break blocks anywhere, bases included? I can add a `/power griefing off` switch for the server.
3. **"2 days" for cures (L79, L128):** two Minecraft days (40 minutes of play) or two real days?
4. **Living Armor (L98):** with the big body only appearing near death or in a takeover, it stops being something you choose to wear. Do the gallop, the extra absorption hearts and the 3× hunger move to the tendrils mode? Or do they stay with the big body only?
5. **Skyfall:** is it the ultimate you want for Collapsing Horizon?

**Suggested order:** Phase 1 first (one version, Gravity only, since everything there depends on each other), then
Phase 2 in one go, then Phase 3 a feature or two per version so we can playtest each.
