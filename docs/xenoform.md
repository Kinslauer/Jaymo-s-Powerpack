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
  (overlay with its name; the hotbar stays put). **Let go of Z**: slam the band and become the dialled alien. Held
  0.5 s while changed, turn back early.
- A fresh band holds one of the three starters at random (`XenoForms.STARTERS`). **Scanning (X held)** at a creature for
  3 s from up to 12 blocks: if it carries an alien's DNA (`XenoForm.scans`), the band learns that alien (chime, green
  beam, level-up sound). Only as a human.
- **The timer**: a form lasts a random 1–5 minutes until the power is fully mastered (4 abilities), then always 5. The
  band beeps once a second through the last 10 s, with red motes. Then you turn back where you stand.
- **A second life bar**: your max health becomes the alien's and you start it full. When it hits zero you're knocked
  back into your own body (`ALLOW_DEATH` cancels the death) at the health you had before, dizzy for 5 s, and the band
  recharges for 2 minutes (1 minute after turning back by hand; none in creative). `/kill` and the void still kill you.
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
  speed 0.16), put back on the way out (creative and spectator keep theirs).
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
- **The Ultimate (C, ascended).** As an alien, C makes you its Ultimate form for the rest of the timer
  (`XenoForm.onUltimate`; `tickUltimate` every tick; `onUltimateAgain` for C again; `onUltimateEnd` before `onExit`).
  The Ultimate's own move has a cooldown of its own (`live.ultimateNext`). As yourself C only says to slam first.

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

## Later

- Nothing planned from the Omnitrix Power Plan: all three stages are in. Ideas Andy might want: Kingdoms quests that
  ask for an alien's help, a scan target for the Wither, more Ultimates' second moves.
