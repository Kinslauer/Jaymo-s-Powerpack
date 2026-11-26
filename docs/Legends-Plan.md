# Legends mods plan (webs, the big symbiote, Chrono's wormhole, fx)

Andy (2026-11-26): "study the animations and mechanics for web and the symbiote suit based off the hulk's animations and
mechanics" from Legends: Superheroes Unlimited, Chrono "running so fast it takes them down a wormhole", "any other
applicable fx and animations" from Legends Anime, Power Rangers, Revenant's Miscellaneous and Legends Core, and (project
chat) "web users should get access to the uptilt attack as they get increased agility, their mid air uptilts should involve
using the webs to swing them up or pendulum kick the enemy higher, like the insomniac games". Pick list (all 61 rows
picked): https://claude.ai/artifact/EHLGagLabgCmMYK9NuHAjV (db `picks/andy`). Andy's one row note: suit-up pose, "rig too"; then "the rig should have a few different suit ups" (several Rig suit-ups, picked by situation: plates slamming on piece by piece while standing, a step-in where the suit folds shut around you, a flight-in that locks on midair, and, Andy: "one where it assembles on you while falling from a great height, then thrusters kick in to stop the momentum").

## Licences

- Legends: Superheroes, Legends Anime, Legends Power Rangers, Legends Revenant's Miscellaneous: All Rights Reserved
  (mods.toml). Ideas only: our own code, poses, names and art. No Marvel/DC/anime names in anything players see.
- Legends Mod Core: LGPL-3.0 (Modrinth; Andy confirmed). Its player animations (`legends_animations/player.animation.json`,
  Bedrock format, 55 moves), katana animations and fx may be ported, keeping the LGPL notice and credit
  (`CREDITS` beside the files, plus a line in the change note). A copy of the files: project files
  `legends-study/core-lgpl/`. Its `.fx` files are LDLib/Photon effects (gzipped NBT); Photon doesn't run on our Fabric
  26.3, so read their numbers (colours, sizes, lifetimes) and rebuild in MagicFx.

## Rules every patch keeps

- No new dimensions or portals (the wormhole leaves no portal block; it is Future's leap's look and a long run in this
  world). No screen text stating lore. Threads don't paint textures: art (the world from orbit in `ch_drag`) comes from
  an art thread or a CC0 pack, and until then the film uses a flat-colour stand-in drawn by code.
- New keys: keep `key.*` names under ~30 characters; check `powers-keys` skill and the radial/bar before taking a key.
- Every patch adds `changes/powers/<slug>.md` (and kingdoms/kitchen notes if touched); never versions or MODLOGs.
- Andy tests by merging. Compile-check (CI builds the PR); played nothing.

## Patch 1: Web swinging and getting about

Power: Web.

- **Loop over the anchor** (`ws_loop`, Superheroes, S): Look up while holding forward on a swing and you climb right over the top of the anchor in a full loop, then come down the far side still swinging. Our side: WebSwing's rope constraint already carries you up; this lets a fast swing go past the top instead of stopping, with a flip pose from WebTricks.
- **Dive rolls** (`ws_roll`, Superheroes, S): Diving after a release, hold use and left or right to barrel-roll that way, steering the dive as you spin. Our side: WebTrickClient picks tricks from the swing; this adds a held roll during the dive, drawn on WebArms.
- **Zip landing marker** (`ws_marker`, Superheroes, S): While a Zip is ready, a small ring shows on the block where you would land, fading in as you aim and turning red past range. Our side: A client marker in WebPowerClient, using the same aim Zip uses. Off in the HUD settings for those who don't want it.
- **Spring off on arrival** (`ws_spring`, Superheroes, S): Hold Jump while a Zip carries you in, or press it as you land, and you kick off the spot at once with the zip's speed. Look up or down at a wall to shoot along it instead. Our side: Zip and WebLedge stop or mantle today. This chains a zip into the next move without a pause.
- **Ride a webline** (`ws_ride`, Superheroes, M): Sneak and use on a webline to hang from it or stand on it. Forward and back slide you along it, turn onto a crossing line where two meet, and sneak again drops you. Our side: WebBridges strings walkable lines; this adds riding along them, hanging under them and switching lines.
- **Springboard line** (`ws_springline`, Superheroes, S): Standing on a webline, crouch to bend it down, then let go to be flung where you look. A twang tells you it's at full bend. Our side: Rides on the webline row above, with the charge bar our slingshot uses.
- **Wind lanes for the web wings** (`ws_wind`, Superheroes, M): Gliding on the web wings, faint streaks of wind show in the air between tall buildings and over cliffs. Fly into one for a burst of speed. Our side: WebSymbioteWingsClient glides like an elytra. Lanes would be found from the terrain near you (gaps between high blocks), drawn as particles, never placed blocks.
- **Ledge leap from a wall run** (`ws_edgehint`, Superheroes, S): Running up a wall, hold use and you leap for the next open ledge above, pulling yourself over it. Our side: WebWallRun and WebLedge both exist; this joins them for the vertical run.
- **More swing poses** (`ws_poses`, Superheroes, M): Their swing has four body poses that change with the swing's phase (reach, pull, swing through, let go) and nine release flips. Ours has fewer. Add poses so a long run of swings never looks the same twice. Our side: Our own hand-made poses on WebArms and WebTricks. Their keyframes are All Rights Reserved, so only the idea and rough timings carry over.

## Patch 2: Web attacks and the web uptilt

Power: Web.

- **Launcher through Agility (your ask)** (`wa_uptilt`, Your ask, S): Web users get the sneaking launcher (the uptilt) as their Agility rises, not only from Might's Air Combo perk in the tree. Our side: AirCombo already counts Web as a power that can follow a foe up; today it needs TreeLocks.AIR_COMBO. This opens it for web slingers at an Agility level (I'd say the level where Agility first speeds up climbing; tell me if you want another), and keeps the perk route for everyone else.
- **Web air launchers (your ask)** (`wa_webtilt`, Your ask, M): In the air, the juggle and launch blows use the webs, the way the Insomniac games do: a web from overhead swings you up past the foe and you pendulum-kick them higher, or a web stuck on the foe yanks them up with you. Each blow throws them higher than a plain juggle, and the lines show the whole time. Our side: On AirCombo's juggle for web slingers only: the blow picks a web move (swing-up kick, pendulum kick, web yank up) from where there's something to stick to, drawn with WebPose and the web lines. Falls back to the plain juggle in open sky.
- **Zip strike** (`wa_strike`, Superheroes, S): Zip at a webbed foe and you fly in feet first, kicking them back as you arrive. A foe with three or more web layers can be zipped from further away. Our side: Zip already pulls you to a creature. This puts a hit and a pose on the arrival and ties the range to WebTraps' layers.
- **Ride a launched foe** (`wa_surf`, Superheroes, M): Kick a foe into the air and zip onto it, and you stand on its back as it flies, steering it into a wall or the ground. Our side: Like WebLatch for the slinger, but brief and only on a foe you just launched.
- **Two-line dive slam** (`wa_diveslam`, Superheroes, M): In the air, look steeply down at the ground ahead and both webs shoot into it. They haul you down into a slam that hurts and webs everything around the landing. Our side: WebSlam's dive landing already breaks the ground. This starts one on purpose, from any height, with the two lines drawn the whole way down.
- **Pin to the wall** (`wa_pin`, Superheroes, S): Web a foe and yank them sideways into the nearest wall, where they stick for a few seconds. With no wall near, they are whipped down and stuck to the ground. Our side: A Yank variant (sneak + Yank). Uses WebTraps' Webbed state to hold them.
- **Rising drop kick** (`wa_rising`, Superheroes, M): Both webs pull a foe in, you flip upside down and drop-kick them up into the air, then burst webbing out around you. Our side: A new move for the bar, or what Yank does when the foe is already webbed.
- **Shock lines** (`wa_shock`, Superheroes, S): Fix both webs on one foe and send a shock down them, five jolts over two seconds that stun it. Breaking line of sight or going too far snaps it. Our side: Uses the two swing webs on one target, like Web Whip does. Copper in the web shooters could gate it.
- **Wrecking ball** (`wa_wreck`, Superheroes, M): Web together loose debris (or a corpse) into a ball, swing it round you on a line, knocking foes and arrows away, then let go to throw it. Our side: Web Whip already swings what both webs hold. This lets it gather a ball first and adds blocking arrows.
- **Web storm** (`wa_cage`, Superheroes, M): Spin a dome of web lines out to the blocks around you, drawing nearby foes in toward you, then burst it, webbing them all. Our side: A pull-in partner to Web Blossom, which throws outward.
- **Five-hit web fists** (`wa_combo`, Superheroes, S): Webbed Fists chain five blows: jab, kick, uppercut, twist and crush. A webbed foe is staggered harder, and the last blow throws them. Our side: WebStrength's fists already hit harder. This gives them a fixed combo with its own poses.
- **Dodge and answer** (`wa_sense`, Superheroes, S): Spider-sense dodges an arrow; click within two seconds and you turn, zip at whoever shot it and hit them. Our side: WebCounter and WebRolls already dodge. This adds the answer back.
- **Web layers cap** (`wa_layers`, Superheroes, S): At five web layers a foe is fully stuck and takes more damage, then can't be webbed again for a few seconds after it breaks free. Our side: A tuning change to WebTraps so webbing a boss doesn't lock it forever.
- **Hunting dive** (`wa_ult`, Superheroes, M): Lock onto one foe, leap back, then dive at them over and over from different sides, five strikes in the air, ending in a stomp that pins them. Our side: An ultimate film on our UltimateFilm, with the lines drawn for each pass.

## Patch 3: The big symbiote, after their Hulk

Power: Web (symbiote big suit).

- **Thunderclap** (`hk_clap`, Superheroes, M): Bring both hands back and clap them together. A shockwave rolls out: twice as far and twice as hard in front, a weaker ring all round. More fury, a bigger clap. Our side: A new big-suit move (Sneak + R, say), with a ring of dust and a screen shake. Their clap is 1.4 seconds from wind-up to clap.
- **Aimed leap** (`hk_leap`, Superheroes, M): Crouch to charge the leap, taking less damage the longer you hold. At full charge, aim at a foe up to 40 blocks away and you leap straight at them and land on them in a smash. Our side: WebLeap charges a leap up; this adds aiming it at a foe and the damage cut while you crouch.
- **Rubble wave** (`hk_rubble`, Superheroes, M): Tear up a slab of ground and flip it forward. It breaks into a wave of rubble rolling along the ground at your foe and eats arrows on the way. Our side: WebBrute's rip and throw already tears a chunk out. This is the other choice: flip it as a rolling wave instead of lifting it.
- **Pounding fury** (`hk_tectonic`, Superheroes, M): Pound the ground over and over with both fists. Each blow knocks foes off their feet and drags them in a little closer; the last blow throws them all up. Our side: A full-fury move, on the ground tear's code. Their version lasts five seconds.
- **Ground-shaking steps** (`hk_steps`, Superheroes, S): At full fury every footstep shakes the screens near you and nudges foes standing near. The suit forming and falling away sends out a harmless shockwave. Our side: WebFury already grows the body. This adds the footsteps and the arrival, like our Aether Giants' stomps but softer.
- **Brute wall climbing** (`hk_wall`, Superheroes, M): The big suit catches a wall with both hands and hauls itself up in bursts, can hop sideways, crouch to let go, and at the top heaves itself over the roof edge. Release Jump to push off and leap where you aim. Our side: WebClimbPose already climbs in bounds. This adds the sideways hop, the aimed leap off the wall and climbing over the edge, with their pose order (grab, hold, heave, perch left and right, leap, ledge) as a guide.
- **Heavy hooks** (`hk_punch`, Superheroes, S): The big suit's plain punches swing left and right in turn, wide and slow, and every fourth is a lunging blow that knocks foes flat. Our side: A pose set for the big body's melee, drawn by our own code.
- **Brute walk, run and jump** (`hk_walk`, Superheroes, M): A heavy idle, a stomping walk, a loping run on the knuckles, a crouch before every jump, a fall pose and a heavy landing. Our side: Poses for WebHulk. Today the big body mostly uses the player's own walk scaled up.
- **Rampage** (`hk_rampage`, Superheroes, S): Charge with most damage shrugged off, steering toward foes you aim at, swatting each one aside as you pass without throwing it out of reach. Our side: The shoulder ram hits the first thing and stops. This keeps going and turns.
- **Tracking cracks** (`hk_crack`, Superheroes, S): One stomp sends cracks racing through the ground in a cone, bending toward a moving foe you aim at, then the ground erupts along them. Our side: WebBrute's ground tear goes straight. This one bends toward a target and bursts half a second later.
- **Losing it** (`hk_losing`, Superheroes, M): Badly hurt, the big suit can burst out on its own. While it's loose, a pulse beats on the screen, and pressing sneak in time with it wins control back sooner. Our side: We already have takeovers and the struggle bar. This gives the struggle a rhythm instead of plain mashing.
- **Fury nova** (`hk_nova`, Superheroes, M): With full fury, spend all of it in one huge blast after a two-second build-up. Afterwards the suit is spent and usually falls back to the thin armour. Our side: An ultimate for the big suit, as a short film. Their Hulk's version is a 75-block blast; ours would be much smaller and break blocks only near the centre.
- **Airborne clap** (`ot_clap`, Core, S): A thunderclap in the air: looking down as you clap throws you upward. Our side: Goes with the big suit's Thunderclap row.
- **Seismic boulders** (`ot_quake`, Misc, S): Slam the ground again and again, each slam throwing up boulders that fall on foes around you. Our side: Goes with the Pounding fury row, as its boulders.
- **Ground upheaval** (`ot_flip`, Anime, M): Tear up a long strip of the ground ahead into flying slabs that throw foes into the air. Our side: For Gravity, or the big symbiote. Slabs as falling blocks, like the rubble wave.

## Patch 4: Symbiote suit moves and the suit-up pose

Power: Web (symbiote) + costume + Rig.

- **Grind across the ground** (`sy_grind`, Superheroes, M): Tendrils pin a foe and you rush along the ground dragging them through it, steering as you go, then lift them overhead and slam them down. Our side: A new tendril move, on WebHeldGrab's hold. The drag tears grass and leaves a scar.
- **Tendril stilts** (`sy_stilts`, Superheroes, M): Double-tap Jump to rise up on two tendrils from your back and stride on them, a third bracing you on walls. Hold Jump to go higher, sneak to come down. Our side: WebTendrilWalk walks you up walls on four tendrils. This walks you on the ground, high up.
- **Feeding lunge** (`sy_lunge`, Superheroes, S): In a surge, click a foe you haven't hit yet and the symbiote throws you at them. Each new foe struck heals you a little. Our side: Fits our satiety: each new foe is a bite.
- **Tendril tempest** (`sy_tempest`, Superheroes, S): Tendrils snatch every foe near you, pull them in and fling them all up into the air at once. Our side: Like our two-foe slam reflex, but for a crowd, on demand.
- **Puts out the ground fire** (`sy_snuff`, Superheroes, S): When fire is burning on the ground where you stand, a tendril smothers it. Our side: Our symbiote already fears fire and shoves you clear; this stamps it out too.
- **Suit-up pose** (`ot_suitup`, Power Rangers, S): A short pose while a suit forms over you: arms crossed, then thrown out as it finishes. Our side: For the symbiote putting on its suit, and costumes changing.

## Patch 5: Chrono: the wormhole

Power: Chrono.

- **Run into the wormhole** (`ch_wormhole`, Superheroes, M): In top gear, run flat out long enough and the air ahead tears open into a swirling hole of blue and gold light. Run into it and you race down a tunnel of streaking light toward a glowing sphere, lightning trailing off you, then burst out in the future (or back home). Anyone running close behind you comes too. Our side: A new way into Future's leap, with the same rules as Sneak + B (your echo slain). Legends Core (LGPL) carries the Speed Force portal effect, so its colours, sizes and timings can be read and rebuilt in our MagicFx (their effect format, Photon, doesn't run on our Fabric 26.3). The tunnel is drawn by code (streak bands and a lit sphere), no painted textures. No portal block is left, so it doesn't break the no-portals rule: it's the leap's look.
- **Wormhole to a marked place** (`ch_marked`, Superheroes, S): Run into the wormhole with a place marked and you come out there instead, in this time. Our side: ChronoMarkRun runs you to a mark along the ground today. This is the long-distance version, over any distance in this world.
- **Drag a foe through time** (`ch_drag`, Superheroes, L): Catch a foe at full speed and race them down the wormhole, out over the sea and round the world, then back to where you started with an uppercut and a lightning slam. Our side: An ultimate film (UltimateFilm) for Overclock's top gear. The world fly-past needs a picture of the world from high up: that comes from an art thread or a CC0 pack, not from this one.
- **Time skip** (`ch_skip`, Superheroes, M): Shatter the moment: foes near you fall out of step (slowed, their aim wobbling) while you're pushed forward at speed and the screen warps. Our side: A screen warp on our post shaders while it lasts, and a slow on ChronoSlowLook's look for foes.
- **Swap with an afterimage** (`ch_mirage`, Superheroes, S): Vibrate and split off an afterimage beside you that fights; sneak to swap places with it. Our side: ChronoDecoys already leaves afterimages. This makes one of them fight and lets you swap.
- **Blitz poses** (`ch_blitz`, Superheroes, S): When time slows and you hit everyone nearby, each foe gets a different strike pose (six of them), and one in five drops what it was holding. Our side: For ChronoPunch and ChronoFlurry, which hit with the same blow today.
- **Blur steps** (`ot_flicker`, Anime, S): Short bursts of speed in any direction, side, back and up, that carry you through the path in a blur rather than teleporting. Our side: For Chrono at low gears, or Z Fighters, on our dodge keys.

## Patch 6: Legends Core animation library (LGPL port)

Power: core/combat, shared.

- **Port Core's animation library** (`ot_corelib`, Core, M): Bring over Core's whole set of player animations as files: landings, flips, rolls, dodges, slides, wall climb and perch, gliding, kicks, uppercut, parry, throw, thunderclap, sonic punches, and the hyperflight and hyperdash loops. Our side: We already play player animation files (assets/jaymospowers/player_animations). Theirs are in the Bedrock format; if our animation library on 26.3 can't read that, a small script converts them. Kept with Core's LGPL notice in a CREDITS file. The Core rows below would then use these keyframes.
- **Landings by height** (`ot_landing`, Core, S): Four landing poses picked by how far you fell, from a light crouch to a full one-knee superhero landing that cracks the ground. Our side: WebSlam has one superhero landing for the slinger. This gives every power user the set.
- **Movement set for everyone** (`ot_moves`, Core, M): Backflip, front flip, double jump, cat leap over a block, sprint slide, long roll and three dodges. Our side: Some exist (WebParkour vaults, rolls, our slide). This makes one shared set any power can use.
- **Katana stance set** (`ot_katana`, Core, M): Swap stance, draw-charge, a four-hit chain ending in a launcher and a finisher, a dash slash, two air slashes and a downward spike. Our side: For our katanas in Weapon Forms. Ours have fewer moves.

## Patch 7: Z Fighter and weapon moves

Power: Z Fighter, weapons.

- **Great Ape moves** (`ot_ape`, Anime, M): A full set for the giant ape: swipes left and right, a roar, a ground slam, a leap smash and a boulder throw, each with its own pose. Our side: ZFighterApe already grabs, carries and slams. This fills in swipes, the boulder throw and the leap smash.
- **Staged beam firing** (`ot_beam`, Anime, S): Beams go in stages: gather in the hands, hold, fire, keep firing, end. Foes inside a beam you're holding are outlined red so you can see you're on them. Our side: ZFighterBeams and the beam clash exist. This adds the red outline and smoother stages.
- **Instant step** (`ot_transmit`, Anime, S): Two fingers to the brow and you're a few blocks ahead in a flash, or behind the foe in your sights if you sneak. Our side: A Z Fighter move once ki sense is learned. A short shimmer where you left and where you land.
- **Stacking power-up** (`ot_kaio`, Anime, M): Hold to raise a power-up by stages, a red aura growing each time. Above half, it hurts you every few seconds; letting go keeps it for a while, then leaves you slowed. Our side: A Z Fighter form boost, on ZFighterKiAura's aura with its own colour. Our own name for it.
- **Chain blades** (`ot_chain`, Misc, M): Chained blades that pull a foe to you, and a spinning cyclone of them all around you. Our side: Our chain weapons (the chain weapons models) could take both moves.
- **Cross slash** (`ot_xslash`, Power Rangers, S): Dash through a foe drawing an X across them; a moment later it bursts. Our side: A Weapon Forms move for swords.

## Order

Tracks run two at a time on separate files, then land on one branch (`claude/project-thread-aaw9tm`), one commit per
patch, one PR: 1 + 5, then 2 + 7, then 3 + 6, then 4. Patches 1 to 4 all touch the web package, so each starts from the
branch with the earlier web patch in it.

## Open questions

- `wa_uptilt`: which Agility level opens the launcher for web slingers (default: the first level where Agility speeds
  up climbing, per `core.Agility`).
- `ch_drag`: the world-from-orbit picture needs an art thread or a CC0 image.
