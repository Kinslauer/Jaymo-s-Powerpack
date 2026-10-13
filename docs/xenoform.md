# Xenoform

The Xeno-Band: a wrist band of alien metal from a fallen star that turns you into one of the aliens it holds, each with
its own body and four moves. Our own take on the "watch of aliens" idea; no names, art or code from anyone else.
Colour 0x5CF07A, icon the band. All numbers are `static final` constants at the top of the class named in brackets.
The plan with the user's answers: the "Omnitrix Power Plan" doc (Claude Docs); stages 1 and 2 of it are built.

## Getting it [FallingStars, FallenStarBlock, XenoBandItem]

- **Falling stars** are a world event. Every 10 s each player under the open night sky (overworld, not raining) has a
  0.2% chance of one; at most one per world per day. A glowing rock (a scripted `BlockCluster`, like a Gravity meteor)
  comes down at a slant from 110 blocks up, 40–100 blocks from the player, in 4 s, whistling, with "A star is
  falling..." shown to everyone within 160 blocks.
- It lands with a blast (power 2, breaks nothing itself), carves a crater with `WebCraterFeature.carve` (the Web
  symbiote's crater) and leaves a **Fallen Star** block in its heart instead of the goo: light 9, needs an iron pickaxe,
  slow to mine (hardness 5), can't be pushed.
- **Mining it** (not in creative): 15% a **Xeno-Band**; else 7% the symbiote's black goo appears on the rock (a web
  slinger can bond with it; a powerless finder is called to the Web for a day); else 2–5 raw iron, 1–2 amethyst, 12 XP.
- **The band** (Epic, one per stack, fireproof): right-click it on. A powerless survival player is called to Xenoform at
  once (`Callings.call`); a creative player simply gets it; anyone with another power finds it won't close.
- `/powerdebug xenoform band | star | unlock <alien|all> | form <alien> | revert | ready`.

## The band [XenoformPower, XenoformState]

- **Z tap**: turn the dial to the next alien the band holds (overlay with its name). **Z held 0.5 s**: slam the band and
  become the dialled alien; held again while changed, turn back early.
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
| **Pyre** | blaze | 1x, 24 hp, fists 4, fire and lava can't hurt it, water and rain do (1 hp/s) | Fire Bolt: a small fireball (1 s) | Flame Jet (hold): levitation IV while held, costs hunger (needs food > 3), slow falling on release | Fire Nova: 6 to all within 5 and 5 s of fire (10 s) | Magma Trail: fire under your feet for 6 s (15 s) |
| **Prism** | shulker | 1.1x, 30 hp, fists 5, 40% knockback resist | Crystal Shards: 3 instant rays in a 12° fan, 4 each, 16 blocks (1.5 s) | Crystal Wall: 5x3 amethyst 3 blocks ahead for 10 s, only in air (12 s) | Reflect (hold): rooted; arrows that hit you are replaced by one shot back at the shooter, other projectiles vanish | Spike Field: 5 + Slowness II to all within 4.5 (10 s) |
| **Wisp** | phantom | 1x, 16 hp, fists 3, no fall damage, no legs | Phase: through a wall up to 2 thick to the first spot you fit (3 s) | Shroud: Invisibility until you hurt something (`onDealtDamage`); mobs targeting you forget you | Dread Wail: mobs within 10 drop their target and path 10 blocks away for 3 s; players get Darkness (15 s) | Glide (hold): fall at -0.12, drift forward up to 0.7 |
| **Gremlin** | silverfish | 0.3x, 10 hp, fists 2, 20% faster, no fall damage; fits 1x1 gaps | Spring: 1.1 up, 0.6 out (1.5 s) | Sabotage: within 16 blocks, levers and buttons are pulled, doors/trapdoors/gates toggled, wire/repeaters/comparators/torches popped off (5 s) | Bite: 3 damage within 2.5; players lose 4 food, mobs get Hunger II + Weakness; you heal 2 and eat 2 (3 s) | Climb (hold): up any wall at 0.18/tick |
| **Knell** | Warden | 1.3x, 50 hp, fists 8, slower, 90% knockback resist, steps up a block | Sonic Boom: 15-block line through walls, 10 sonic damage + 2.5 knockback (8 s) | Tremor Sense (hold): Glowing on everything within 24 | Shriek: 4 damage, Darkness 6 s, Slowness II to all within 12 (15 s) | Burrow: 1 s digging (immune, Slowness X), teleport to the surface where you look up to 16 blocks, 1 s emerging (12 s) |
| **Wyrm** | Ender Dragon (any part) | 1.2x, 36 hp, fists 5, 40% knockback resist; water and rain 1 hp/s; its own breath can't hurt it | Dragon's Breath: an `AreaEffectCloud` 5 blocks ahead on the ground, radius 3, 5 s, Instant Damage II (10 s) | Wings (hold): fly toward where you look at 0.9/tick, costs hunger (needs food > 3); slow falling on release | Wing Gust: 3 damage, shove 1.6 out to all within 6, you recoil (6 s) | Rift: teleport up to 12 blocks where you look (4 s) |
| **Blur** | horse (any) | 0.9x, 20 hp, fists 4, 60% faster, 4 blocks of extra safe fall | Dash: 1.5 blocks/tick for 8 ticks (2 s) | Wall Run (hold): against a wall, climb at 0.32/tick for up to 2 s | Whirlwind: pulls everything within 5 in, 4 damage (8 s) | Chain Strike: up to 5 foes within 8, every 3 ticks teleport behind one and hit for 5 (12 s) |

## Rendering [client/power/xenoform]

- `XenoformClient.extract` (AvatarRenderer `extractRenderState`, tail) marks the render state with the form and makes
  the player's own body invisible with no armour, cape, held items or head; `XenoformLayer` then draws the alien's
  model (`renderColoredCutoutModel`, which unlike the "copy layer" helper doesn't skip invisible states) and clears
  `isInvisible` again so the shadow still falls.
- `XenoFormModels`: each alien is a `HumanoidModel` built from `HumanoidModel.createMesh` with a different inflation and
  extra parts hung on the head and body (Bulwark's brow, plates and lower arms; Pyre's crest, plumes and core; Blur's
  fin, snout and tail; Prism's crown and back crystals; Wisp's hood and tatters, no legs; Gremlin's big skull and ears;
  Knell's horns, ribs and shoulders; Wyrm's horns, wings and tail), so it takes the player's own pose, swing, crouch and swim. Skins are 64x64 in the vanilla
  layout, extras in the rows below; `tools/gen_xenoform.py` draws them.
- First person: `renderHand` (head, cancellable) draws the alien's arm instead of the skin's.
- HUD right of the hotbar: as a human the dial chip (Z, the dialled alien's icon, shaded through the recharge, name and
  "n/N" above) and the scan chip (X, fills while scanning); as an alien four chips with cooldowns and a timer bar in the
  alien's colour, red through the beeps. A flash of the form's colour round the screen on each change.

## Later (from the plan)

- Stage 3: ultimate forms on C once ascended (Titan, Sunheart, Afterimage, Geode, Revenant, Swarm, and ones for Knell
  and Wyrm), and Kingdoms reactions through a compat class. The power has no ascension trigger yet.
