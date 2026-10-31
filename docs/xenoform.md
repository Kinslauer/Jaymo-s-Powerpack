# Xenoform

The Xeno-Band: a wrist band of alien metal from a fallen star that turns you into one of the aliens it holds, each with
its own body and four moves. Our own take on the "watch of aliens" idea; no names, art or code from anyone else.
Colour 0x5CF07A, icon the band. All numbers are `static final` constants at the top of the class named in brackets.
The plan with the user's answers: the "Omnitrix Power Plan" doc (Claude Docs); stages 1 and 2 of it are built.

## Getting it [FallingStars, FallenStarBlock, XenoBandItem]

- The band is the only way in: `XenoformPower.fated()` is false, so a random awakening never lands on Xenoform and it
  can't be chosen as an affinity (`/power affinity` doesn't offer it either), and creative's H screen doesn't list it
  (unless it's already yours). Only `/power set` gives it by hand.
- **Falling stars** are a world event. Every 10 s each player under the open night sky (overworld, not raining) has a
  0.2% chance of one; at most one per world per day. A glowing rock (a scripted `BlockCluster`, like a Gravity meteor)
  comes down at a slant from 110 blocks up, 40–100 blocks from the player, in 7 s, whistling (no on-screen
  message). The rock is drawn big (2.5x) and glowing (outline through hills), and
  its trail is forced to everyone within 400 blocks; after landing a signal-smoke plume with sparks stands over the
  crater for 3 minutes (`FallingStars` tuning: `SEEN_FROM`, `ROCK_SCALE`, `PLUME_TICKS`).
- It lands with a blast (power 2, breaks nothing itself), carves a crater with `WebCraterFeature.carve` (the Web
  symbiote's crater) and leaves a **Fallen Star** block in its heart instead of the goo: light 9, needs an iron pickaxe,
  slow to mine (hardness 5), can't be pushed.
- **Mining it** (not in creative): 3% a **Xeno-Band**; else 7% the symbiote's black goo appears on the rock (a web
  slinger can bond with it; a powerless finder is called to the Web for a day); else 2–5 raw iron, 1–2 amethyst, 12 XP.
- **The band** (Epic, one per stack, fireproof): right-click it on. A powerless survival player is called to Xenoform at
  once (`Callings.call`); a creative player simply gets it; anyone with another power finds it won't close.
- `/powerdebug xenoform band | star | unlock <alien|all> | form <alien> | revert | ready`.

## The band [XenoformPower, XenoformState]

- **Z held**: the wrist comes up; **scroll** while holding it to turn the dial through the aliens the band holds
  (overlay with its name; the hotbar stays put). **Let go of Z**: slam the band and become the dialled alien. While
  changed, hold Z 0.5 s and let go to turn back; hold Z and **scroll**, then let go, for a **quick change** into the
  dialled alien (a quarter of the full charge, a second of dizziness; refused below 10 s of charge left) (0.18.92).
- A fresh band holds one of the three starters at random (`XenoForms.STARTERS`). **Scanning (X held)** at a creature for
  3 s from up to 12 blocks: if it carries an alien's DNA (`XenoForm.scans`), the band learns that alien (chime, green
  beam, level-up sound). Only as a human.
- **The charge** (0.18.92, replaces the random timer): the band holds 2 minutes of form unmastered, growing with
  each mastered ability to 5 at full mastery (`capacity`). Changed, it spends a point a tick, 10 more per point of
  damage taken and 20 more per move put on cooldown. What's left stays in the band when you turn back, and as yourself it
  refills from empty in a minute; a slam needs 10 s of it. The HUD shows it as a thin bar over the dial (grey while too
  low, purple when jammed, shimmering on the wild dial). The band beeps once a second through the last 5 s (twice a second for the last 2), with red motes, and each beep flashes
  your screen's edge red (0.18.91). Then you turn back where you stand.
- **A second life bar**: your max health becomes the alien's and you start it full. When it hits zero you're knocked
  back into your own body (`ALLOW_DEATH` cancels the death) at the health you had before, dizzy for 5 s, and the band
  is emptied and locked for a minute before it refills (none in creative). `/kill` and the void still kill you.
- **The band saves you** (0.18.92): a killing blow as yourself (not the void or `/kill`, not jammed or locked) slams the
  band for you, before Downed: a fall gives Wyrm or Wisp, drowning Slipfin (once it exists) or Wisp, fire Pyre or Prism,
  anything else Bulwark, Knell or Prism, else the dialled alien. At least 30 s of charge, and you turn back at 1 health.
  Once every 10 minutes.
- **Wild dial** (0.18.92): C while holding Z as yourself turns it on or off (a player tag, kept). The band changes you
  into a random known alien as soon as it can, and again every 1 to 60 s while changed (no cost); Z can't turn you back
  or quick-change. Each move counts double toward mastery.
- **Jammed** (0.18.92, `XenoJam`): an effect. An alien who has it turns back, and the band won't slam or save you until
  it ends. Potion of Jamming: awkward + echo shard (20 s; redstone 40 s; gunpowder splash, dragon's breath lingering),
  tipped arrows from the game's own recipe.
- **One band in the world** (0.18.92, `XenoBands`, Andy): the overworld keeps who wears it and how many band items are
  loose. A fallen star holds the band only while nobody wears it and none is loose. A downed wearer can have it torn
  off: another player taps them (use let go at once; holding is a revive, `Downed.TAPPED_LISTENERS`), the power ends and
  the taker gets the band as an item that keeps every alien it learned. Killed outright, the wearer keeps it. A loose band
  destroyed (lava, void, despawn) or a power removed by hand returns it to the stars. `/powerdebug xenoform bands reset`
  forgets the count.
- **Fists only**: while changed both hands read as empty to everything that asks the entity (`XenoformHandsMixin` on
  `LivingEntity.getItemBySlot`), so no item can be used, mined with or shown, and punches hit for the alien's attack
  damage. The inventory itself is untouched.
- The body is attribute modifiers: `SCALE`, `MAX_HEALTH`, `ATTACK_DAMAGE`, `MOVEMENT_SPEED`, `KNOCKBACK_RESISTANCE`,
  `STEP_HEIGHT`, all removed on turning back, a relog (`DISCONNECT` reverts first) or losing the power.
- Attachments: `xenoform_band` (unlocked list + dial; saved, kept on death, synced to the owner), `xenoform_form` (the
  current alien's id, synced to everyone; the renderer reads it), `xenoform_hud` (timers and cooldowns for the HUD).
- Mastery: every move counts a use; the key guide shows the dialled alien's moves (`statusSuffix` = `.<alien>`).
- **Corruption**: every knockout adds `KNOCKOUT_CORRUPTION` (25) to the Fate meter, so four knockouts corrupt the band.
  Corrupted, it glitches: a slam gives a random other unlocked alien `GLITCH_WRONG_CHANCE` (25%) of the time, and about
  every `GLITCH_SELF_TICKS` (6 min) as a recharged human it changes you on its own. Every glitch shows: nausea, a sour
  note, ink, and a line in chat and on the overlay (`glitch`, `glitchFx`).

## The aliens [forms/*, XenoForm]

Each is a class under `forms/` with its body (scale, health, fist damage, speed, knockback resistance, step height) and
four moves on R G V B with their own cooldowns (`Live.cooldownUntil`). HUD icons are in `XenoformHud.ICONS`.

| Alien | DNA | Body | R | G | V | B |
|---|---|---|---|---|---|---|
| **Bulwark** | iron golem | 1.5x, 40 hp, fists 7, slower, 60% knockback resist, steps up a block | Fist Combo: 3 blows at 4 ticks, 5 each, the last shoves (2 s) | Ground Clap: 6 to all within 5, debris ring (8 s) | Grab: lift the creature you look at overhead (it rides you); V again hurls it. Looking at the ground rips a 4-block chunk out and throws it (1 s) | Leap Slam: a leap; the landing hits 8 within 4 blocks, no fall damage (6 s) |
| **Pyre** | blaze | 1x, 24 hp, fists 4, fire and lava can't hurt it, water and rain do (1 hp/s) | Fire Bolt: a small fireball (1 s) | Flame Jet (G toggles; flown on the client like a Z Fighter, `PyreFlightClient`): hover with WASD/Jump/Sneak at 0.5/tick, Sprint streaks along your look up to 1.4/tick, turning 8°-1.5° a tick; arms swept back as jets; a comet's tail of flame behind it (`PyreComet`); G again or Sneak on the ground ends it, slow falling in the air | Fire Nova: 6 to all within 5 and 5 s of fire (10 s) | Magma Trail: fire under your feet for 6 s (15 s) |
| **Prism** | shulker | 1.1x, 30 hp, fists 5, 40% knockback resist | Crystal Shards: 3 instant rays in a 12° fan, 4 each, 16 blocks (1.5 s) | Crystal Wall: 5x3 amethyst 3 blocks ahead for 10 s, only in air (12 s) | Reflect (hold): rooted; arrows that hit you are replaced by one shot back at the shooter, other projectiles vanish | Spike Field: 5 + Slowness II to all within 4.5 (10 s) |
| **Wisp** | phantom | 1x, 16 hp, fists 3, no fall damage, no legs | Phase: through a wall up to 2 thick to the first spot you fit (3 s) | Shroud: Invisibility until you hurt something (`onDealtDamage`); mobs targeting you forget you | Dread Wail: mobs within 10 drop their target and path 10 blocks away for 3 s; players get Darkness (15 s) | Glide (hold): fall at -0.12, drift forward up to 0.7 |
| **Gremlin** | silverfish | 0.3x, 10 hp, fists 2, 20% faster, no fall damage; fits 1x1 gaps | Spring: 1.1 up, 0.6 out (1.5 s) | Sabotage: within 16 blocks, levers and buttons are pulled, doors/trapdoors/gates toggled, wire/repeaters/comparators/torches popped off (5 s) | Bite: 3 damage within 2.5; players lose 4 food, mobs get Hunger II + Weakness; you heal 2 and eat 2 (3 s) | Climb (hold): up any wall at 0.18/tick |
| **Knell** | Warden | 1.3x, 50 hp, fists 8, slower, 90% knockback resist, steps up a block | Sonic Boom: 15-block line through walls, 10 sonic damage + 2.5 knockback (8 s) | Tremor Sense (hold): Glowing on everything within 24 | Shriek: 4 damage, Darkness 6 s, Slowness II to all within 12 (15 s) | Burrow: 1 s digging (immune, Slowness X), teleport to the surface where you look up to 16 blocks, 1 s emerging (12 s) |
| **Wyrm** | Ender Dragon (any part) | 1.2x, 36 hp, fists 5, 40% knockback resist; water and rain 1 hp/s; its own breath can't hurt it | Dragon's Breath: an `AreaEffectCloud` 5 blocks ahead on the ground, radius 3, 5 s, Instant Damage II (10 s) | Wings (hold): fly toward where you look at 0.9/tick, free; slow falling on release | Wing Gust: 3 damage, shove 1.6 out to all within 6, you recoil (6 s) | Rift: teleport up to 12 blocks where you look (4 s) |
| **Velox** | horse (any) | 0.9x, 20 hp, fists 4, 80% faster, 4 blocks of extra safe fall; runs on water while sprinting | Velocity (toggle, after Chrono's Overclock): +100% speed and Overclock's sprint boost, steps up whole blocks, runs up walls holding forward, runs on water and lava, no fall damage, Shift walks, a light blue speed-force streak; R again to stop (2 s) | Rapid Punches: 6 double hits, one every 2 ticks, on the creature you aim at within 3.5; the last shoves (5 s) | Cyclone: 1.5 s, sweeps everything within 6 round you and lifts it up to 3.5 blocks, 3 damage once (10 s) | Chain Strike: up to 5 foes within 8, every 3 ticks teleport behind one and hit for 5 (12 s) |
| **Duplex** | copper golem | 0.55x (a copper golem's size and build, its doubles too), 18 hp, fists 3, 10% faster, 20% knockback resist; the name counts you all (`Duplex.title`, also on the HUD): Duplex, Triplex, Quadruplex, up to Septuplex | Split: a double steps out beside you (`XenoDouble`: 6 hp, fists 3, never saved), up to 3; doubles go for what you hit, what hits or hunts you or them, and visible hostiles within 12, and swarm it (`SwarmGoal`: a turning ring, blows in turn every 3-6 ticks, +35% damage per extra double); a loyal double's death costs you 2 (2 s) | Rally: doubles target what you aim at within 16; at nothing, they come back (1 s). Sneak: Fetch, the nearest free double picks up the aimed solid block (`CarryBlock.liftableAny`, where you may break it) and carries it on its head; sneak + G again sets it down where you aim; dropped as a falling block if the double goes | Switch: trade places with the aimed double, or the farthest within 32 (2 s) | Merge: doubles within 24 pop back into you, healing 3 each (8 s) |
| **Rivet** | clockwork golem (Kingdoms) | 0.6x, 20 hp, fists 4, 15% faster, 20% knockback resist; liquid metal, so falls never hurt it | Meld: flow into the furnace (or blast furnace, smoker), piston, anvil, stonecutter, smithing table, dispenser (or dropper) or note block you look at within 5 and take its shape (the block stays); R G V B become the shell's moves, sneak + R pours you out (1 s) | Lash: 5 damage and a drag to you, 7 blocks (2 s) | Pour: a 1.4 dash where you look, half a second where nothing can land on you (2 s) | Ride: climb into the aimed minecart, iron golem, clockwork golem or android mech (4 blocks) and drive it where you look; it's sped up, strengthened and glows green, and goes for what you hit; B again climbs out (1 s) |
| **Gorger** | armadillo | 1.1x, 30 hp, fists 5, a little slower, 50% knockback resist | Gulp: eats the aimed block within 4.5 (breakable by you, hardness 0-50, no block entities, where you may build; no drop), 1 bite + 1 per 3 hardness (max 3), gut holds 12 (0.4 s) | Gut Blast: the gut as a 9-block cone of rubble, 2 a bite and a shove; empties it (2 s) | Horn Charge: 22 ticks running at 0.75 where you look, the first creature met takes 7 and a toss; run off a ledge and it becomes a slam: landing hits all within 4 for 5 + 1 a block fallen (max 18), no fall damage (5 s) | Roll Up (hold): Slowness IV, Resistance II, no sliding, shots glance off |
| **Slipfin** | axolotl | 0.8x, 22 hp, fists 4, 10% faster; in water Dolphin's Grace II, water breathing, night vision; never drowns; out of water and rain 30 s it dries (1 hp and Slowness every 2 s); can't be carried or taken by tendrils, slips out of carries and trips on its own | Bubble Jet: a 12-block jet, 3 (6 under water), pushes, puts out fire (1 s) | Slip: out of any carry, ride on a monster, trip; Slowness gone; Speed II 3 s (5 s) | Dart: a lunge (2.0 in water, 0.9 on land) that bites the first creature for 6 (1.5 s) | Regrow: one lost limb back, every break knit, Regeneration II 5 s (45 s) |
| **Polestar** | Magnetic Giant (Kingdoms) | 1.2x, 36 hp, fists 6, a little slower, 70% knockback resist | Pull: things wearing iron or chainmail (an iron golem counts 4) in a cone ahead within 16 are dragged to you, 0.4 + 0.25 a piece; iron items on the ground fly to you (2 s) | Repel: all iron-wearers within 8 take 1.5 a piece and a throw; arrows in the air turn back (4 s) | Lift: tear an iron, raw iron, bars, trapdoor, anvil, cauldron, chain or heavy pressure plate block out (6 blocks) and hold it; V again throws it as a falling block (2 a block fallen, max 30) (0.5 s / 3 s) | Field (hold): arrows heading at you within 6 turn back at their shooter |
| **Gyre** | turtle; breeze | 1x, 28 hp, fists 4, a little slower, 40% knockback resist, steps up a block; Dolphin's Grace and water breathing in water | Shell Spin: 30 ticks bowling forward at 0.6; each creature met takes 5 and a toss once; shots glance off (4 s) | Gust: an 8-block cone shoves 1.6, puts out burning creatures and fire blocks (2 s) | Whirlwind: at the aimed creature or block (16), 40 ticks dragging everything within 4 round and up, 3 a second (8 s) | Shell (hold): Slowness IV, shots glance off |
| **Cortex** | guardian | 1x, 24 hp, fists 4, 30% knockback resist; looking at a creature (16) shows its health, and a Kingdoms temperament if it has one (`Temperaments.of` by reflection) | Arc: 5 to the aimed creature (14), jumping to 2 more within 5 for 3 (1.5 s) | Bubble: 6 s where shots can't hit you (15 s) | Mind Lift: Levitation V 1.5 s on the aimed creature (16), then it drops (7 s) | Claw Snap: 7 within 3.5 and Slowness III 3 s (2 s) |
| **Locust** | goat | 0.95x, 22 hp, fists 4, 12% faster; no fall damage | Crash Leap (hold, let go): up to 30 ticks of charge, 0.9-2.4 out; landing hits everything on the ground within 3-6 for 4-9 (3 s) | Kick: 6 and a 1.8 shove within 3.5, you spring back (1.5 s) | Wall Bounce: off a wall you touch, 0.9 out and 0.85 up; chains (0.4 s) | Hops: three quick hops (3 s) |
| **Croaker** | frog; tadpole | 1x, 26 hp, fists 5, 30% knockback resist, steps up a block; no fall damage; Dolphin's Grace and water breathing in water | Tongue: hauls the aimed creature (10) to you for 2, or snatches the dropped item on the line (1.5 s) | Gulp: swallows a mob no wider than 1 and no taller than 1.5 within 3 (it rides you hidden, AI off, 1 every 2 s); G again spits it 1.6 for 4 (2 s) | Bound: 1.3 out, 0.8 up (2 s) | Croak: 2, Slowness IV 2.5 s to all within 6; players sick, mobs forget you (10 s) |
| **Dud** | never scanned (patch 6's glitch; `/powerdebug xenoform unlock dud`); hidden | 0.7x, 20 hp, fists 0: its blows are stopped (`XenoformPower`, ALLOW_DAMAGE); nothing hurts it but the void, every blow throws it; monsters within 12 that see it target it every second | Taunt: monsters within 24 come for you (5 s) | Boing: a hop of 1.0 (1 s) | Play Dead: 3 s where nothing targets you (10 s) | Roll: a 1.1 dash (2 s) |
| **Rime** | stray, snow golem; Friends & Foes Iceologer | 1.05x, 30 hp, fists 5; Cryokinesis's moves lent through `cryo/CryoLent` with Rime's own cooldowns; sprinting on the ground skates (Ice Slide, off 15 ticks after you stop); frost armour: whatever hits it within 4 is frozen and slowed (3 s); cold never hurts it. Ultimate (Hoarfrost) B: the Blizzard | Frost Breath (hold), as Cryo's; the wait (3 s) starts when it ends | Ice Shards (3 s) | Flash Freeze (10 s) | Ice Wall (12 s) |
| **Marrow** | skeleton; Kingdoms silver skeleton, Skeleton Lord | 1x, 24 hp, fists 4, a little quick; once each change (and again after each Ultimate) a killing blow drops it in a heap, untouchable for 3 s, then up at half health | Bone Spikes: a 9-block line, 6 and a toss (3 s) | Rib Throw: a boomerang out 10 ticks and back, 4 each way (2 s) | Bone Shield: 5 s, shots glance off, Resistance II (15 s) | Fall Apart: 3 s in a heap, hunters lose you, can't move (12 s) |
| **Swathe** | husk | 1x, 26 hp, fists 4; can't drown or starve | Lash Bind: the aimed creature (9) is tripped like a bolas (`Tripped.bind`), or held (Slowness X, Weakness) 4 s (6 s) | Lash Pull: drags the aimed creature (12) to you (2 s) | Sandstorm: 6 s, radius 6 (9 in desert or badlands), 1 (2) a second, Blindness (20 s) | Wrap: bleeding stops, Regeneration II 5 s (30 s) |
| **Blight** | the Wither; hidden | 1.4x, 60 hp, fists 7, slower, heavy; the Wither effect and its own skulls can't touch it; at half health or less the dark shield rises and shots can't touch it | Skull: one wither skull (1 s) | Volley: three fanned, the middle one blue (4 s) | Rot Touch: 6 and Wither II 6 s on the creature in reach (4); grass, leaves, flowers and crops round it rot (mob griefing on) (3 s) | Dark Pulse: 4, Wither 4 s and a throw to all within 6 (10 s) |
| **Howl** | wolf; Kingdoms werewolf, feral wolf, coyote | 1x, 26 hp, fists 5, quick; Strength and Speed at night | Pounce: a 1.2 leap; the first creature landed on takes 6 and is tripped 2 s (3 s) | Howl: untamed wolves, feral wolves and coyotes within 32 hunt what you hit (or what hit you) for 60 s, never you (90 s) | Scent: 10 s, blood stains within 48 shown only to you, bleeders glow (20 s) | Lockjaw: 4 and a dirty cut, then held 4 s: dragged, Slowness IV, 2 a second; B again lets go (8 s) |
| **Prowl** | ocelot; Naturalist tiger, lion | 1x, 24 hp, fists 5, quickest; silent; Invisibility while sneaking; falls under 12 blocks don't hurt | Long Pounce: a 1.7 low leap; first creature landed on takes 6 and is pinned 3 s (5 s) | Rake: 5 and a dirty cut (2 s) | Stalk: the aimed creature (32) glows 20 s; within 6 of it you get Strength II (15 s) | Roar: mobs within 12 drop their target and run (20 s) |
| **Tusk** | hoglin; Naturalist boar, rhino; Kingdoms wild boar, minotaur; Aether burrukai, kirrid, taegore | 1.15x, 34 hp, fists 6, a little slow, 60% knockback resist | Charge: 25 ticks at 0.7 where you face; first creature met takes 7 and is tossed (5 s) | Gore: 6, a dirty cut, a shove (2.5 s) | Root Out: grass, dirt, podzol, mycelium, rooted dirt or mud at your feet or aimed (4) turns to coarse dirt and gives 1-2 potatoes, carrots, beetroots, brown mushrooms or sweet berries, 40% also a seed or bone meal (10 s) | Thick Hide: 10 s of full knockback resistance and Resistance I (25 s) |
| **Talon** | parrot; Naturalist vulture, bluejay, canary, cardinal, robin, finch, sparrow | 0.9x, 20 hp, fists 4; flies (jump held in the air beats up and where you look, let go glides, sneak drops); no fall damage | Stoop: a 1.4 dive; the first creature met takes 4, and a mob no wider than 1.4 and no taller than 2.2 is carried under you up to 8 s (6 s) | Drop what you carry; with nothing held, a talon strike of 5 (1.5 s) | Far Sight: monsters within 96 glow 10 s, Night Vision (30 s) | Feather Darts: five fanned quills, 2.5 each (4 s) |
| **Murk** | squid, glow squid; Naturalist anglerfish, Friends & Foes barnacle | 0.95x, 22 hp, fists 4; in water Dolphin's Grace II, water breathing, Night Vision | Ink: Blindness 5 s to all within 5, mobs lose you (10 s) | Tentacles: the aimed creature and the nearest, up to 4 within 6, held round you 4 s (Slowness IV, 1 a second); G again lets go (12 s) | Jet: 1.6 dash in water, 0.8 on land (2.5 s) | Lure: 10 s, mobs in water within 24 swim to you (20 s) |
| **Gnasher** | dolphin; sharks, Naturalist alligator, piranha, Friends & Foes mauler | 1.1x, 30 hp, fists 6, 40% knockback resist; in water Dolphin's Grace II, water breathing; bleeders in water within 48 glow | Clamp: 6 and a dirty cut, held 4 s (dragged, Slowness IV); R again lets go (5 s) | Lunge: a 1.5 leap, 0.6 up; first creature met takes 5 (4 s) | Frenzy: 10 s, Speed and Strength one level per bleeder within 16 (max 3) (20 s) | Death Roll: only while clamped: 2 s, 3 and a cut every half second, then tripped 3 s and let go (10 s) |
| **Lumen** | allay; Naturalist firefly, Friends & Foes glare | 0.7x, 16 hp, fists 2; falls no faster than 0.15 unless sneaking; no fall damage | Mend: you, players and pets within 8 heal 4 (10 s) | Flare: Blindness 4 s to all within 8, mobs lose you (8 s) | Light: a level-15 light block where you look (24) for 30 s, six at most, oldest out first; needs build rights (2 s) | Fetch: 10 s, dropped items within 24 matching your held item (any, empty-handed) fly to you (15 s) |
| **Keen** | bat; Kingdoms vampire bat, blightwing | 0.6x, 14 hp, fists 3; Night Vision; no fall damage; flies like Talon; sneak under a ceiling to hang (jump lets go) | Sonar: 6 s, every creature within 24 outlined in points only you see (10 s) | Screech: anything airborne within 16 takes 3 and is thrown down; elytra stop (10 s) | Drain: 4, you heal 3 (3 s) | Echo: a 1.8 dash; Invisibility 1.5 s if light is 7 or less (3 s) |
| **Hive** | bee; Naturalist ant, butterfly, caterpillar, dragonfly | 0.8x, 16 hp, fists 3; no fall damage | Scatter: 4 s a cloud of insects: Invisibility, blows pass through, mobs lose you, jump held lifts you (15 s) | Sting: 3 and Poison II 5 s (2.5 s) | Honey Glue: the aimed creature (12) gets Slowness X and can't jump 4 s (8 s) | Swarm: the aimed creature (24) is stung 1.5 and poisoned every half second for 6 s (15 s) |
| **Skitter** | spider, cave spider; Kingdoms tarantula | 0.9x, 22 hp, fists 4; climbs any wall it walks into (sneak to stop); jump held under a ceiling clings there upside down (body and your camera turn over) and crawls | Web Line: a line to the block you look at (32), held to its length so falls swing; sneak reels in; R again lets go (1 s) | Web Tie: the aimed creature (12) is tripped 4 s (or Slowness VI) and weakened (10 s) | Bite: 4 and Poison II 5 s (3 s) | Egg Sac: 3 cave spiders for 30 s, at what you fight or what hits you, never players (60 s) |
| **Shade** | enderman; Enderscape drifter | 1.3x, 30 hp, fists 6; water and rain burn (1 a second) | Steal: the aimed block (6) up to hardness 10, no block entities, needs build rights, held over your head; R again throws it (2 damage per block fallen, max 20) (1.5 s) | Stare: the aimed creature (24) held still while you keep it under your crosshair, up to 5 s; G again lets go (10 s) | Blink: to where you look (24) (2 s) | Wary: 10 s; the next blow that would land blinks you up to 10 away instead (15 s) |
| **Mirage** | illusioner; Friends & Foes illusioner | 1x, 20 hp, fists 3 | Mirror Images: 3 false copies for 15 s; mobs within 16 after you go after one; a copy vanishes at any blow and never fights (20 s) | Blind: Blindness and Darkness 5 s on the aimed creature (16), it loses you (8 s) | Unseen: Invisibility 5 s, mobs within 24 lose you (20 s) | Swap: trade places with the copy farthest off (nearest, sneaking); what was after you goes after it (2 s) |
| **Ooze** | slime; sulfur cube, Aether blue and golden swet, Kingdoms mud golem | 1x, 24 hp, fists 4; bounces off falls (sneak to land), no fall damage; a small slime splits off at each quarter of health lost (up to 4), fights beside you and rejoins (+3 hp) 5 s after the fight | Engulf: a creature no wider than 1 and no taller than 1.6 (3) is held inside you up to 15 s, 1 a second (3 s) | Spit: what you hold, thrown 1.6; with nothing held, a glob of 3 and Slowness II 3 s (12) (1.5 s) | Squeeze: 60% smaller (fits a one-block gap); again with room to fill out (1 s) | Split: two blobs at once (20 s) |
| **Magma Ooze** | magma cube | as Ooze, plus fire immunity; its blobs are magma cubes and its glob sets fire | as Ooze | as Ooze | as Ooze | as Ooze |
| **Fuse** | creeper | 1x, 20 hp, fists 4; every hit pops (a shove and a puff); its own blasts never hurt it | Blast Pod: on the aimed creature or block (8), power 2 after 2 s (5 s) | Fuse Dash: 1.3 dash; first creature met takes 5 and pops (3 s) | Self-Destruct: hold 1.5 s, let go to blow (power 3.5) then four power-1.5 blasts one a tick round you; unseen 1 s; costs 300 ticks of band charge (20 s) | Defuse: primed TNT within 10 is put out (dropped as TNT), swelling creepers stop, your pod is cancelled; +2 hp each; blasts can't hurt you 5 s (15 s) |
| **Thornwild** | creaking; Enderscape rustle | 1.1x, 28 hp, fists 5, 40% knockback resist; heals 1 every 2 s in sunlight | Roots: the aimed creature (16) held 4 s, 1 a second (10 s) | Thorn Volley: five thorns, 2.5 each (3 s) | Hedge: a 5x2 wall of leaves two blocks ahead; withers after 30 s; needs build rights (15 s) | Bloom: 10 s, three bone-meal tries every half second round your feet (30 s) |
| **Billow** | happy ghast; Aether aerwhale | 1.6x, 40 hp, fists 2, slow, 50% knockback resist; floats (jump rises, sneak sinks); no fall damage; up to 3 players ride on its back (right-click it, sneak off) | Downpour: 6 s, fire blocks within 4 (and 8 below) and burning creatures put out (15 s) | Thunderhead: a bolt (no fire) and 8 on the aimed creature (32) (8 s) | Gust: everything within 8 in front blown 1.6 back (3 s) | Fog: 10 s, creatures within 8 (not players or riders) blinded and lose you and your riders (25 s) |
| **Towerback** | Kingdoms Highfield, Arctic and Irradiated Giants, war elephant | 5x (about 9 blocks), 120 hp, fists 12, slow, full knockback resist; Z tap: monolith size 16x (open sky only; +80 hp, +8 fists, 60% faster, step 4; walks through what it meets, never block entities or unbreakables); every step stomps (cracks, a shockwave 1.6 per scale for 5, jump it); falls land as stomps, no fall damage | Grab: the aimed creature no taller than half you, the Giants' grab (1.5 s wind-up, squeeze 4 every 0.5 s, slam 30; x1.6 at monolith size), with the film (15 s) | Stomp: a shockwave twice a step's, 10, thrown up (6 s) | Swat: 12 to everything in front, flung aside 2.4 (4 s) | Roar: monsters (and mobs after you) within 3 per scale get Weakness II, Slowness III 8 s and lose you (20 s) |

### More DNA [XenoDna] (0.18.100)

`XenoDna` is checked before every form's own `scans`, by entity id. Mod mobs that aren't loaded never match.

| Alien | Also scanned from |
|---|---|
| Wyrm | Kingdoms wind dragons (Aeolus, Boreas, Eurus, Notus, Zephyrus), Naturalist komodo dragon, Visage of War |
| Bulwark | Naturalist bear, black bear |
| Velox | Naturalist ostrich |
| Gremlin | Naturalist lizard, Friends & Foes rascal, Kingdoms loot goblin |
| Duplex | Friends & Foes copper golem |
| Prism | Friends & Foes tuff golem, Kingdoms gorgon |
| Pyre | Friends & Foes wildfire |
| Gorger | Enderscape rubblemite, Naturalist giant isopod |
| Slipfin | Naturalist bass, catfish, blobfish |
| Wisp | MCA Grim Reaper and ancient cultist, Kingdoms wraith, flame wraith, haunted stand |
| Knell | Kingdoms skulk golem |
| Gyre | breeze |
| Croaker | tadpole |
| Rime | Friends & Foes Iceologer |
| Marrow | Kingdoms silver skeleton, Skeleton Lord |
| Howl | Kingdoms werewolf, feral wolf, coyote |
| Prowl | Naturalist tiger, lion |
| Tusk | Naturalist boar, rhino; Kingdoms wild boar, minotaur; Aether burrukai, kirrid, taegore |
| Talon | Naturalist vulture and songbirds (bluejay, canary, cardinal, robin, finch, sparrow) |
| Murk | Naturalist anglerfish, Friends & Foes barnacle |
| Gnasher | sharks, Naturalist alligator, piranha, Friends & Foes mauler |
| Lumen | Naturalist firefly, Friends & Foes glare |
| Keen | Kingdoms vampire bat, blightwing |
| Hive | Naturalist ant, butterfly, caterpillar, dragonfly |
| Skitter | Kingdoms tarantula |
| Shade | Enderscape drifter |
| Mirage | Friends & Foes illusioner |
| Ooze | sulfur cube, Aether blue and golden swet, Kingdoms mud golem |
| Thornwild | Enderscape rustle |
| Billow | Aether aerwhale |
| Towerback | Kingdoms Highfield, Arctic and Irradiated Giants, war elephant (only by these) |
| Rivet | Kingdoms redstone golem and mimic, android mech, war mech, Omen mechs, android, worky drone, test unit |

**No DNA:** cow, pig, sheep, rabbit, turkey, duck, capybara; zombies, drowned, zombie villagers, zombified piglins and
hornkin, the old giant; pillagers, vindicators, evokers, witches, vexes; piglins, piglin brutes, the Piglin King;
villagers, wandering traders, every MCA villager and Kingdoms folk (ids ending `_of`/`_titled`, caravan folk), hornkin
and their brutes, hurlers and warlords, the Night Bleeder; our people (Judge, Keeper, Hermit, raiders and so on), demon
soldiers and captains, the aerbunny.

## Earning aliens [XenoFinds, StarWreckBlock] (0.18.122, Xeno-Band patch 6)

Scanning stays the main way in. Beside it:
- **Star tablets:** 25% of mined fallen stars, 15% of fishing treasure catches and 20% of Ancient Remnants chests hold one;
  band hunters drop one; Gremlin makes them. Hold one in either hand as you slam the band: it reads a random alien the
  band doesn't hold yet (never a hidden one) and the tablet crumbles. With nothing left to learn it stays whole.
- **Alien-only crafting** (sneak + use, as the alien; JEI pages "Gremlin's Tablets", "Bulwark's Grinding", "Pyre's
  Smelting"):
  - Gremlin at a crafting table: 4 probe circuits and an amethyst shard make a tablet.
  - Bulwark at a grindstone, ore in hand: one raw iron, copper or gold (or its ore block; not nether gold ore) grinds into
    two dusts, which smelt or blast into ingots.
  - Pyre, holding anything a furnace smelts: up to 8 smelted in its hands, once a second.
- **Star wrecks:** one fallen star in 10 lands with a Star Wreck block beside it and bits of hull (waxed cut copper, iron
  bars, tinted glass) strewn round the crater. While a band wearer is within 32, the wreck sends out a probe every 5 s, up to 3
  at once. Probes are vex-built flying machines (8 hp, ram for 3, pass through walls like a vex) and drop 1-2 probe
  circuits. Mined with an iron pickaxe, the wreck gives a wreck core (for the band's reforging, patch 7) and 2-4 circuits.
- **Band hunters:** every minute at night in the overworld each band wearer has a 12% chance, at most once every half day,
  that an android mech comes for them from 28-44 blocks off. It drops a tablet and 2-4 circuits.
- **A glitch can teach:** when a corrupted band's slam glitches to the wrong alien, 20% of the time it lands on an alien
  the band never held (Dud included) and keeps it. Dud comes only this way (`XenoformPower.GLITCH_TEACH_CHANCE`).
- **Star-readers** (`StarReaders`, with Kingdoms and MCA): one fallen star in 3 is remembered on its level, and a day to
  a day and a half later, once a player is within 96, a star-reader (an MCA villager, tag `jaymospowers_star_reader`)
  comes to stand 7-10 blocks out on the crater's rim, facing in. None comes within 96 of another. "Talk about work" opens
  their screen: a star tablet for 3 star metal ingots, a reading of your band (how many aliens it has yet to hold), and a
  hint about the sky (wrecks and probes, hunters, the smith on a moon, glitches, fishing, remnant hollows, tablets).
  Module plans join the trade with patch 7.
- Tuning: constants at the top of `XenoFinds`, `StarWreckBlock` and `StarReaders`. Art: `tools/gen_xeno_finds.py`.

## Rendering [client/power/xenoform]

- `XenoformClient.extract` (AvatarRenderer `extractRenderState`, tail) marks the render state with the form and makes
  the player's own body invisible with no armour, cape, held items or head; `XenoformLayer` then draws the alien's
  model (`renderColoredCutoutModel`, which unlike the "copy layer" helper doesn't skip invisible states) and clears
  `isInvisible` again so the shadow still falls.
- `XenoFormModels`: each alien is a `HumanoidModel` built from `HumanoidModel.createMesh` with a different inflation and
  extra parts hung on the head and body (Bulwark's brow, plates and lower arms; Pyre's crown and hair of fire,
  shoulder plumes, forearm flames and core; Velox's
  crest, visor, snout, ball feet and tail; Prism's crown and back crystals; Wisp's hood and tatters, no legs; Gremlin's big skull and ears;
  Knell's horns, ribs, shoulders and jaw tendrils; Wyrm's horns, wings and finned tail; Bulwark's pauldrons, Prism's shoulder shards, Gremlin's tail; Duplex's swaying lightning rod and belt, which its doubles wear too, drawn by `XenoDoubleRenderer`), so it takes the player's own pose, swing, crouch and swim. Skins are
  64x128: the vanilla layout in the top 64 rows, every extra in the rows below (nothing shares texels with a limb);
  `tools/gen_xenoform.py` draws them face by face (lit from above, dark underneath) with each alien's own motifs, and
  every alien wears the xeno insignia (the band's green hourglass): on the chest, or on Pyre's core and Knell's sternum.
  `python tools/gen_xenoform.py preview.png` also writes a front-view contact sheet of all nine.
- Pyre is living flame (since 0.11.99, an original look in the spirit of a flame-bodied hero): vanilla's fire colours,
  hair of fire streaming back with speed, and a second skin (`xenoform_pyre_glow`) drawn again additive and fullbright
  (`RenderTypes.eyes`) in third and first person, so it shines in the dark. Small flames rise off its body
  (`XenoformFlightPose.bodyFlame`, not in water or rain, not round your own first-person camera).
- First person: `renderHand` (head, cancellable) draws the alien's arm instead of the skin's; `XenoformFirstPersonMixin`
  lifts the render state's invisible flag for the hands (the body was hidden for the alien, and the renderer skips an
  invisible body's bare arm).
- The band itself (`XenoBand`): a 5x3x5 box on the left wrist of anyone whose power is Xenoform while they're
  themselves, its dial emissive, grown to sit over any costume, suit or chestplate sleeve (`GROW`, `ARMOUR_GROW`,
  since 0.15.107); a layer in third person (not on an arm First-person Model hides), and on your
  first-person left arm whenever it's drawn (AvatarRenderer.renderHand).
- The slam (`XenoformSlap`, client only): holding Z brings the left wrist up across the chest and the right fist up
  behind it; the fist drops onto the band as the hold reaches `HOLD_TICKS` (when the server changes you), rests, and
  both arms fall back. A tap just lifts the wrist for a look. Everyone else sees the slap when a player changes shape
  (the alien appears mid-slap). The pose is read onto the render state in `extract`, laid over any humanoid model
  after it animates (`XenoformModelFeatureRendererMixin`), and mirrored in first person by replacing the usual hands.
- The first-person slam (`XenoformSlamView`, since 0.15.51; its numbers were tuned against offline pictures, see its
  javadoc): the left forearm swings up into a bar across the lower middle of the view with the band's face turned up
  at you (a slight overshoot); while held it breathes, the dial pops up as a small glowing cap (`XenoBand`) with the
  dialled alien standing over it as a see-through green hologram (`XenoHologram`, since 0.15.103: upright on screen,
  facing you, red while the band would refuse, light fanning up behind it from the band; scaled with its distance from
  your eye so it looks the same size on First-person Model's own arm), and the right hand waits up and off to the right, clear of it; the hand comes straight down over 2 ticks and covers the dial; on contact
  the forearm dips and springs back, the view kicks once (`ScreenShake`) and a green burst blooms (`XenoformHud`);
  then both arms drop away.
- The band's flash (`BandFlash`, since 0.15.103): changing shape either way blinds everyone within `RADIUS` (10) who can
  see the band for `TICKS` (10): players get a green whiteout (`XenoNet.Flash`, client `BandFlashHud`), creatures
  Blindness, no target and Darktide's stun. Not the wearer.
- Flight (`XenoformFlightPose`, client): the server syncs how an alien is flying (`XenoformState.FLIGHT`, from
  `XenoForm.flight`). Pyre's jet, Wyrm's wings and Nadir's flight make `WindPose` treat the alien as cruising once it
  moves at 0.25/tick, so it takes Wind's lean, banking and stretched-out limbs (Pyre keeps its arms swept back as jets). Wyrm's wings beat deep while flying and
  fold back along the body from 0.45 to 0.8/tick (`WyrmWings`: since 0.10.86 they're the ender dragon's own wings, its
  boxes at a fifth of the size, drawn from the game's own dragon texture, never copied); Wisp's glide tips the body 38° forward with a
  slow roll and bob, arms reaching out limp, the tatters streaming; Pyre pours client-side flames from both hands and feet,
  out behind along the flight. All flight is free (no hunger). Nadir flies like creative (`Abilities.mayfly`, flying
  speed 0.16, kept every tick; off the ground he floats in Wind's hover pose, 0.18.91), put back on the way out (creative and spectator keep theirs).
- **Aliens tap the insignia (0.18.90):** an alien wears no band, so changing back (hold Z) no longer raises a wrist:
  the right hand comes up and taps the insignia on its chest, which lights under it (the band's face glow laid on the
  chest, `XenoBand.submitInsignia`, placed per form by `FormModel.insigniaY/Z/Size`); in first person only the right hand
  comes up and presses in under the view (`XenoformSlamView.chest`). Others see the same tap when a player becomes an
  alien. `XenoformSlap.Pose.chest` marks it; your own tap keeps its kind through the change so the arm doesn't jump.
- **Wisp is ki (0.18.90):** it floats 0.3 blocks up and bobs instead of walking (no step swing, no footsteps,
  `XenoformWispStepMixin`); its skin is drawn see-through and fullbright over two shells of pale ki (the ki glow
  texture, scrolling) that are its own body puffed out (`XenoFormModels.Wisp.shells`, `WispKi`), first-person arm too.
- The ending glow: the server sets the synced `XenoformState.ENDING` through a form's last `BEEP_TICKS`; everyone's
  client then draws the alien a second time with the emissive `eyes` render type in pulsing red (`XenoformLayer.glow`),
  the first-person arm too.
- HUD right of the hotbar: as a human the dial chip (Z, the dialled alien's icon, shaded through the recharge, name and
  "n/N" above) and the scan chip (X, fills while scanning); as an alien four chips with cooldowns and a timer bar in the
  alien's colour, red through the beeps. A flash of the form's colour round the screen on each change.

## Stage 3: ascension, the Ultimates, the twists, the kingdoms

- **Ascension.** The power ascends on its own once the band is complete (all nine aliens scanned; Duplex, added in 0.15.51, made it nine, and anyone already ascended stays ascended) and the power fully
  mastered (`XenoformPower.tickAscension`, checked every 2 s as a human; `/power ascend` for testing). Nothing else
  changes until you slam.
- **The Ultimate (C, ascended).** As an alien, C makes you its Ultimate form until the charge runs out
  (`XenoForm.onUltimate`; `tickUltimate` every tick; `onUltimateEnd` before `onExit`). (0.18.93, patch 2, Andy: "real
  ultimate forms, all we have are a few special attacks") It is its own body:
  - **Look:** an armoured skin made from the alien's own (darker plates, seams glowing in its colour, eyes and
    insignia burning through; `xenoform_<id>_ultimate.png` + `_ultimate_glow.png` drawn fullbright, pulsing), and its
    own spikes, horns, crests and forearm blades (`UltimateSpikes.Style`, one bone strip tinted per alien). Synced as
    `XenoformState.ULTIMATE`.
  - **Body:** half again the alien's health on top of its own (healed in full as it comes on, gone when it ends), fists
    +50%, 15% bigger (`ULT_HEALTH`, `ULT_DAMAGE`, `ULT_SCALE`).
  - **Kit:** R G V are the base moves with every cooldown halved (`ULT_COOLDOWN`, in `XenoForm.cooldown`). The
    Ultimate's own move (`onUltimateAgain`, where `ultimateHasMove`) moves to B; its cooldown is its own
    (`live.ultimateNext`). C again drops back to the base alien (`dropUltimate`), the charge running on.
  - As yourself C only says to slam first (or, with Z held, flips the wild dial).

| Alien | Ultimate | What it does |
|---|---|---|
| Bulwark | Titan | 2x size, +12 armour, +20 health; the clap smashes walls 3 deep, 3 wide, 3 tall (hardness ≤ 6, no block entities). |
| Pyre | Sunheart | The jet streaks up to 2.0/tick with a longer, wider comet (flight `FLY_SUN`); R is a held beam (12 blocks, 2 fire damage every 4 ticks, lights what it ends on). |
| Velox | Standstill | Everything living within 24 blocks gets Slowness VII and its speed cut for 6 s; projectiles hang at 5% speed, then fly on; you get Speed II. C again every 30 s. |
| Prism | Geode | All within 6 blocks: slowness/weakness/mining fatigue for 4 s, held still, amethyst grown round them (removed after); projectiles shatter; you get Resistance III. Every 20 s. |
| Wisp | Revenant | Ride inside the mob you look at (8 blocks) for 10 s with its AI off: it walks where you look and jumps at walls; you're invisible and mobs drop you. It wakes dazed, glowing and weak. |
| Gremlin | Swarm | 3 glowing silverfish follow you, bite what you bite and never attack players. C again swaps places with the farthest (nearest, sneaking), every second. |
| Knell | The Tolling | Rooted, 5 tolls 2 s apart: 6 sonic damage, a shove and darkness (players) to all within 8; Resistance II meanwhile. Again after 30 s. |
| Wyrm | Elder Wyrm | 2x size, +8 armour, wings cost nothing, breath every 2 s, gust 1.5x wide. |
| Duplex | Copper Storm | Fills up to 3 doubles, then visual-only lightning on each: 6 lightning damage to everything within 3 of a double (not you or yours), and the doubles get Speed II, Strength and Glowing for 20 s; up to 6 doubles while it lasts (the extras pop when it ends). C again every 20 s. |

- **The corruption** is the band's alone: corrupted, a slam may give the wrong alien and the band changes you on its
  own (above). No alien has a twist of its own (0.15.51 removed them at Andy's word); `XenoForm.corrupt` is the empty
  hook, called every tick while the band is corrupted, for any that's added later.
- **Jaymo's Kingdoms** (`kingdoms/.../Aliens`, by entity tag `jaymospowers_xenoform` and `jaymospowers_xenoform:<id>`,
  set in `transform` and cleared in `revert`): to a kingdom's people an alien is a monster. Villagers run from one
  (`Frights`), guards hunt it (`Folk`'s player target goal, `MobCombat.foe`), unless that realm's folk or villagers saw
  you change (16 blocks, line of sight): then they know, for good (`Aliens.KNOWN`, copied on death), say so once, and
  treat the alien as they treat you. Minions carry `jaymospowers_xenoform_minion`.

## Nadir, the void alien, and the two voices

The ninth alien is one no creature carries, and it is lent, never owned. Once the power has ascended *and* the band
is corrupted, two voices speak from it (`XenoVoices`, every 2 s as a human): **Hush**, the still one (aqua, italic),
and **Clamour**, the loud one (dark red, bold). They never agree, and the trial never ends: it runs in rounds
(`XenoformState.TRIAL`: stage, round, this round's answers and deed counts; kept across deaths; once begun it goes on
whatever Fate does after).

- **Questions.** A round opens with a pair of yes-or-no questions, one from each voice, clickable in chat (or
  `/xenovoice hush yes`). The pair conflicts (Hush: "when you could strike, will you stay your hand?"; Clamour:
  "when you could stay your hand, will you strike?") and each voice wants a yes, so pleasing both means telling each
  what it wants to hear, which the other remarks on. An answer that displeases a voice is thrown back and asked again a
  minute later. Then a second pair. There are `PAIRS` (6) pairs in lang (`voice.jaymospowers.xenoform.<voice>.q<n>`,
  with `.yes<n>`/`.no<n>` replies); round *r* asks pairs `(r-1)*2` and `(r-1)*2+1` mod 6, so the questions change
  from round to round and come back around after three rounds.
- **Deeds**, also by round: odd rounds, Hush wants `PEACE_FORMS` (3) changes to run their whole course without a blow
  (`live.hurt`) and Clamour `KILLS` (12) lives ended as an alien (after-death hook, minions excepted); even rounds,
  Hush wants `RESTRAINT` (3) changes ended by your own hand before the timer, Clamour `KNOCKOUTS` (2) knockouts taken.
  `XenoVoices.formEnded` sorts each ending change into one of those.
- **Content.** Both deeds done, the voices agree for once: Nadir goes on the dial (the first time) and the band will
  turn to it **once** (`transform` refuses a hidden form while `!XenoVoices.content`, with the voices saying what's
  missing). The moment that Nadir change ends, it is spent: the next round begins with the next questions, and Nadir
  stays on the dial but won't turn until the voices are content again.

Debug: `/powerdebug xenoform trial show | reset | deeds | content`.

**Nadir** (`forms/Nadir`, id `nadir`, `hidden()` so it doesn't count toward a complete band): 1.15x, 60 health, fists
of 6, a little faster, immovable; no fall, fire or drowning damage. Its look is a tall near-black silhouette with faint
stars in it, heavy shoulders, a drifting mantle of void, a face of nothing with one pale point, and the green xeno
insignia on its chest (`XenoFormModels.Nadir`, `gen_xenoform.nadir`). Moves:

- R, **Beacon**: tap to pick a player anywhere on the server (next player on each tap; they get Glowing for 3 s, so
  they show through the world to everyone); hold R for 1.5 s to phase to just behind them, across dimensions
  (`teleportTo(ServerLevel...)`). 5 s cooldown. Overlay shows who and a progress bar.
- G, **Rift** (`nadir/NadirRift`). Tap at a creature (22 blocks): **Recursive Drop**, a maw in the ground under it
  and one in the sky (7 blocks up, less under a ceiling, never under 2.5); it's pinned in the column and falls
  through, 2 damage each pass, 7 passes, then let go (12 s cooldown). Tap at nothing: **Rift Blink** to where you
  look (16 blocks; on a floor you land on it, at a wall or roof just short of it; 1.5 s). Hold 1 s: **Surface**,
  straight up to the highest dry, fire-free ground with headroom in your column (under the Nether's bedrock roof;
  10 s). Sneak + G: the **Void Pocket** (`nadir/NadirStash`, 27 slots, attachment `nadir_stash`, kept through death).
- V, **Graviton** (`nadir/NadirGraviton`). At a creature (24 blocks; the grip breaks past 30): **Heavy Hand**. Tap:
  yanked to arm's length, 4 damage; sneak + tap: shoved away, 2. Hold 8 ticks: slammed down, 9 damage, pinned 1.5 s;
  sneak + hold: thrown about 12 blocks up (it takes the fall). At nothing, tap: **Weightless**, a 5-block zone where
  you look for 3 s; whatever enters floats up helpless (Levitation III, Weakness V; mobs 3 s, players 1.5 s) and is
  then driven down; projectiles not yours drift and slow in it (8 s). At nothing, hold 1 s: **Event Horizon**, a black
  hole up to 32 blocks off (4 up from the ground it meets), opening over 1 s and holding 6 s. It drags every creature
  in 16 blocks (players weakly), items and XP into a spiral; 1.5 damage every 0.5 s inside 4; anything not a player
  with 30 health or less that reaches the core is erased (its drops come loose); then it collapses: 12 damage within
  8, everything in 16 thrown out. Once every 45 s. Ending the form closes it quietly.
- B, **Command** (`nadir/NadirRules`, screen `NadirRulesScreen`, packets `nadir/NadirNet`). Three rules, each WHO +
  WHAT + WHEN, two on at once, kept on the player (attachment `nadir_rules`) and worked only while Nadir is out. WHO:
  me, what I hit, what hits me, things near me (8), what I look at (24). WHAT: sees in the dark, moves faster, jumps
  higher, lands softly, burns, glows, is weakened, cannot move, is pushed, is pulled, weighs nothing, weighs a ton,
  heals, is unseen, bursts (3-block void blast, 5 damage, never you or yours), feels my pain (4 damage). WHEN: always,
  sneaking, sprinting, in the air, at night, below half health, under rock, standing still. Effects refresh each
  second; instant words fire every 2 s on me/near/look, and at most once a second per struck creature. A rule's own
  damage doesn't set off more rules.
- G V B are from-scratch takes on the Rift, Graviton and Command powers of The Awakening (CurseForge, all rights
  reserved: ideas only, nothing of its code or art). Left out: its Domain Expansion, Mass Control weights, Dense, and
  swallowing mobs into a pocket dimension.
- No Ultimate; corruption doesn't trouble it.

## Rivet's shells [forms/Rivet]

Rivet (0.18.95, Xeno-Band patch 3) melds into one of seven shells. The shell is synced (`XenoformState.SHELL`) and
drawn for everyone as the block itself, 1.6x the body's own space, bobbing as it walks (`XenoformLayer.shell`, a
`MovingBlockRenderState`); the HUD swaps to the shell's icons (`XenoformHud.ICONS`, `rivet_<shell>`), and the key
guide reads `key.<slot>.rivet_<shell>` (`XenoformPower.statusSuffix`). Hands are empty while changed, so ammunition and
fuel come out of the inventory. Sneak + R pours out of any shell. All numbers are constants at the top of `Rivet`.

| Shell | R | G | V | B |
|---|---|---|---|---|
| Furnace | Fire Breath, 3 and 4 s of fire to all in a 5-block cone (2 s) | Fire Shot, a small fireball (1.5 s) | Smelt: the first smeltable stack carried, up to 16, burning carried coal/charcoal (8 each, coal blocks 72) (5 s) | Smoke: a 3.5 cloud of Blindness for 7 s (10 s) |
| Piston | Leap, 1.4 out and 0.7 up (2 s) | Brace (hold): Resistance III, Slowness III | Shove: 4 and a throw to all in front of you within 4 (2 s) | Rebound (toggle): landings faster than 0.5 spring you back at 85% (sneak to land still) |
| Anvil | High Leap, 1.5 up (3 s) | Drop: plunge at 2.5; landing hits all within 3 for 4 + 1 a block fallen, up to 20 (2 s) | Eye Beam, 6 at 12 blocks (3 s) | Iron Skin, Resistance II 5 s (20 s) |
| Stonecutter | Saw, a 16-block hitscan for 6 and Slowness (1.5 s) | Burrow: up again 6 ahead where you fit (4 s) | Climb (hold): run up walls | Spin: 5 to all within 2.5 (3 s) |
| Smithing table | Brace (hold), as the piston's | Temper: fully mends damaged items lying within 2 (30 s) | Hammer, 8 at 3.5 (2 s) | Plate, Absorption II 30 s (30 s) |
| Dispenser (or dropper) | Volley: 3 carried arrows (picked up again) (1.5 s) | Potion: throws the first carried splash or lingering potion (2 s) | Fire Charges: up to 3 carried ones (2 s) | Water Spray (hold): a 4-block jet that pushes creatures, puts out fires and burning creatures |
| Note block | Stun Pulse: 2, Slowness IV and Nausea 3 s to all within 6 (6 s) | Lure: mobs within 16 walk to the aimed spot (24 blocks) (10 s) | Chord: plays the band's theme; you and friends within 12 get Regeneration 5 s (10 s) | Bass Drop: 4 and a throw within 5 (4 s) |

Not done yet: riding a ship's cannon (Kingdoms' cannons aren't entities to climb into; left for later).

## The stalker and Lurk [XenoStalker, forms/Lurk]

(0.18.96, Xeno-Band patch 4.) At night in the Overworld, within 160 blocks of a monolith Kingdoms knows of
(`Monoliths.nearestFoot`, by reflection), a band wearer who hasn't found Lurk may be visited: every 10 s, a 1 in 5
chance, one stalker per wearer. It stands 22-34 blocks off (behind you more often than not), on the surface, always
facing you, and is there only for you. Nothing hurts it and it never fights. Catch it with your eyes (look straight at
it, nothing between, within 56) and it's gone in a puff of smoke; the count is kept on you (`XenoformState.SIGHTINGS`).
The third time it slips out of its skin instead: a pale, see-through husk kneels where it stood for 5 minutes, and the
band can scan it. It leaves on its own at day, after 2 minutes, or if you go 64 blocks off; it's never saved.

**Lurk** (`hidden()`, so not counted toward a complete band, but unlike Nadir not `lent()`: it's yours to slam like any
other): 1.05x, 26 hp, fists 5, 15% faster; tall, thin, long-fingered, six eyes (`XenoFormModels.Lurk`).
R Watch (64 blocks: Glowing 20 s, and it loses you; 3 s), G Behind (to the watched creature or the aimed one within
24, behind it, 7, doubled if it wasn't facing you; 5 s), V Dread (everything within 16 looking at you: Darkness,
Slowness II, Weakness 6 s and a moment's Blindness; 15 s), B Fade (invisible, hunters forget you, until you strike or
B again; 10 s after).

## Duplex's eyes

(0.18.96) Sneak + V as Duplex: your camera moves into the aimed double (or the farthest within 48) and you see what it
sees, your own body held still where it stands (`XenoformState.EYES`, synced to you only; `client/XenoEyes`). V again,
the double dying or straying past 48 blocks, or the change ending brings you back.

## Not yet

Slipfin's slickness covers Powers' carries, Web's tendrils and bolas trips; a Kingdoms giant's grab is Kingdoms' own
and still holds it (G, Slip, gets it out of a ride on a monster).

## Later

- Nothing planned from the Omnitrix Power Plan: all three stages are in. Ideas Andy might want: Kingdoms quests that
  ask for an alien's help, a scan target for the Wither, more Ultimates' second moves.
