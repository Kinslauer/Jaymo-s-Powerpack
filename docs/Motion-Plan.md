# Actions & Stuff picks: the motion plan (2026-10-31)

Andy picked 40 of 46 rows on the pick page (https://claude.ai/artifact/XiRXRfcBqEFjnpVCynJqwm, copy in project files
`actions-stuff-study/actions-stuff-picklist.html`). The rows come from Actions & Stuff, Oreville Studios' paid Bedrock
Marketplace pack. It is proprietary resource-pack JSON, so nothing is copied: our own code, poses and vanilla-style art.
Rows he left out: weapon swings (but see his flintlock note), splash drops, coloured light, bee animations, item physics
and falling leaves.

Ticked rows we already have, kept as they are unless something looks off in game: livelier walk/run (Fresh Animations:
Player Extension), eating and drinking (Not Enough Animations already raises food to the mouth). Roll on landing and
balancing were ticked with changes, below.

Standing rules that bind every patch:
- New player poses sit beside `BalancePose`/`FreefallPose`, pause Fresh Animations and Touch'n Motion while they play
  (`EmfCompat#hold`), and any power's own pose wins over them.
- Every pose shows the same to everyone in view (worked out from synced state, never client-only guesses).
- Art in our cuboid, vanilla-style look (`docs/Vanilla-Style.md`); models need `#variable` face textures.
- Tuning numbers in `static final` constants at the top of each class.
- No screen text that states lore; villagers hint.

Andy's notes from the page, folded in below:
- **Air flips:** "Make this how web flips in air in addition to his current flip off of a jump. It should also be
  accessible in freerun." Patch 0.
- **Hero landing:** "Adapt this over all web's glide landings too." Patch 0.
- **Roll on landing:** "Give everyone the dodge roll on landing, i.e. remove it from the tree, but keep the skillcheck,
  double tapping shift within a window before landing or just as you land." Patch 0.
- **Balance:** "Yes add those" (chains and end rods, and everyone at a smaller wobble). Patch 0.
- **Sit anywhere:** "Make the look down the meditation trigger as well, leaving I to shadowboxing for those that have
  it. Leave it on the tree for shadow boxing." My reading: sneak and look down for 5 s sits you down anywhere; if you
  have the Meditation perk, that sit is meditating (mastery as now). The I key becomes shadowboxing only, for those who
  shadowbox (Z Fighters), still opened by the same tree perk. Patch 0.
- **Strafing:** "When locked on." Patch 1.
- **Scared idle:** "Starting questionnaire leads to different player temperaments that they cannot change. A paranoid
  temperament would get this small effect for example." Patch 1.
- **Panicked look when chased:** "Your pov follows the shaky cam and turnaround." Patch 1.
- **Swimming:** "Only tideborn or swimming xeno get the boost." Patch 2.
- **Flint and steel:** "Same with the flint and rock" (FirstTools' strike). Patch 4.
- **Weapon swings (not ticked):** "Make flintlock work similarly, with less of a sight picture (smooth barrel) and dual
  wielding." Patch 4.
- **Wet wolves:** "Do them all." Patch 6.
- **Babies:** "Just add baby spiders and the wild and farm animal babies that don't already exist." Patch 7.
- **Biome looks:** "Some villagers will keep rare pets or animals as they found them from a different biome far away
  and will brag about it as some sort of accomplishment." Patch 7.
- **Zombies drawing weapons:** "You can throw daggers at them or sometimes the run through animation will leave your
  sword stuck in them because you are less experienced, axes get stuck with higher frequencies but both low." Patch 8.

## Patches

Each patch is its own PR; versions are set to master + 1 right before merging. Patches 0-4 and 8 are Powers, 1 and 7
touch Kingdoms too, 9 is Kingdoms/Kitchen. Pairs that can run side by side (different files): 0 with 5, 1 with 6,
2 with 7, 3 with 9, 4 with 8.

### Patch 0, Landings, flips and sitting (Powers; picks roll-landing, hero-landing, air-flip, balance, sit-anywhere)
- **Landing roll for everyone:** `LandingRoll` no longer checks `Freerun.Perk.SURE_FOOTING`. The trigger is a
  double tap of sneak (two presses within `DOUBLE_TAP_TICKS`) up to `EARLY_TICKS` before touchdown or within
  `LATE_TICKS` after; the dodge key still works. Sure Footing leaves the Freerun tree; its slot's points are refunded.
- **Hero landing:** a fall that would deal 6+ damage (or a flier diving into the ground faster than `HERO_SPEED`) and
  isn't rolled lands in the three-point crouch, one fist down, with a dust ring (the Giants' stomp ring, smaller) and a
  short camera dip. Held `HERO_TICKS`; moving or jumping breaks it. Every Web glide landing uses it too.
- **Air flips:** tap sneak in the air to flip in the way you're moving (front, back or side, from your input). Freerun
  players with the Freerunner rank and Web (beside his jump flip, same `WebPose.BACKFLIP` look). Costs
  `FLIP_STAMINA`; a flip within `FLIP_LAND_TICKS` of landing still counts for the landing roll window.
- **Balance:** `BalancePose` for everyone on narrow tops, with a smaller wobble for those without Sure Footing ranks,
  and chains and end rods count as narrow tops.
- **Sit anywhere:** sneak still and looking down past `SIT_PITCH` for `SIT_TICKS` (5 s) sits you on the ground (knees
  up, or legs over an edge when the block in front is air). Moving, jumping or being hit stands you up. With the
  Meditation perk, sitting is meditating (`Meditate` counts it). The Meditate key (I) becomes Shadowbox, only bound for
  those who shadowbox; lang `key.jaymospowers.meditate` renamed.

### Patch 1, Body motion and temperaments (Powers + Kingdoms; picks torso-lag, strafe-lean, hurt-flinch, scared-idle, scared-flee)
- **Torso lag:** the body turns towards the head over `TORSO_EASE` ticks, capped at `TORSO_MAX` degrees, all players.
- **Strafe lean:** while locked on (`core.LockOn`), side steps cross the legs and lean the body `STRAFE_LEAN` into the move.
- **Flinch:** a hit jerks the body away from the attacker's side for `FLINCH_TICKS`, scaled by damage; not while downed.
- **Player temperaments:** the life-story creator's answers pick one of the 12 temperaments (`Temperaments.Temperament`) for the player, fixed
  for good (shown on the body screen). Each temperament gets one small player effect. There's no paranoid one, so timid and suspicious play that part: scared idle (hunched, glancing
  about, hands close) when it's dark around you, at low health or near a monster king, and the panicked flee: sprinting
  from a hostile at low health shakes the first-person camera and turns it to look back over the shoulder for a moment.
  Existing players get a temper rolled from their saved answers on next join. Other examples: brave never flinches at small hits, hotheaded
  clenches fists in the idle, gloomy slumps, cheerful walks with a slight bounce.

### Patch 2, Water (Powers; picks swim-direction, dives, wakes, underwater-break)
- **Directional swimming:** side stroke and backstroke picked from input against look. Only Tideborn and a swimming
  Xeno form get the faster kick (plus Dolphin's Grace).
- **Dives:** falling into water 2+ deep from 4+ blocks: sneak held early is a cannonball (big splash, fish scatter),
  sneak late is a clean dive (deeper, faster), neither is a belly flop (slap sound, 1-2 damage from 12+ blocks). Our own
  splash, sized by speed and body size.
- **Boat wakes:** a V of foam behind moving boats/canoes, larger behind Kingdoms ships and whales.
- **Breaking under water:** break bits sink slowly with bubbles.

### Patch 3, Faces (Powers client; pick face-anim)
- A face layer on players: blink every 3-6 s, pupils shift towards the look target, brows frown when hurt or angry.
  Reads the skin's eye pixels (rows 3-4 of the head front) and only draws when it finds a two-pixel eye pair, so odd
  skins stay as they are. MCA-faced players use MCA's own face. Knocked out and dead keep the closed eyes we have.

### Patch 4, Holding and using items (Powers; picks flint-steel, shears, held-tools, held-lantern, + flintlock note)
- **Flint and steel / flint and rock:** two-handed strike in first and third person, spark, then flame.
- **Shears:** brought to the centre of the screen, snipping on use.
- **Compass and clock:** held up in one hand like NEA's maps.
- **Held lantern:** a hanging 3D lantern in hand that swings as you walk.
- **Flintlock:** the musket's sights treatment with less of a sight picture (smooth barrel, raised to eye line, not
  aimed down), and dual wielding: a flintlock in each hand fires left then right, each reloads on its own.

### Patch 5, 3D models (art; picks tools-3d, items-3d, bottles-3d, emissive, mushroom-dripstone, double-slabs, boats-3d)
- 3D held models for the vanilla tool set and bow (inventory icons stay flat), with a pack toggle.
- 3D held compass, clock, totem, brush, spyglass and saddle; bottles that show their colour and glow, ours included.
- Glowing trims from glowing materials (amethyst, redstone, diamond, emerald) and iron golem eyes.
- 3D mushrooms and pointed dripstone; one-block textures for doubled vanilla slabs; carved details per wood on canoes.

### Patch 6, Mob animation (Powers/Kingdoms client; picks mob-anims, pet-wolves, pet-cats, wet-wolves, golem-idle)
- First check whether base Fresh Animations (FreshLX) allows modpacks and has 26.3; if so ship it unchanged through EMF
  for vanilla mob motion. Otherwise our own walk cycles for farm animals, zombies and spiders.
- Pet wolves: greet the owner coming back, play bow, lying poses, dozing off and waking at sounds (feral wolves too).
- Cats: loaf, knead, stretch, groom. Iron and clockwork golem idle fidgets.
- Wet look for every furry mob (wolves, cats, foxes, horses, sheep, llamas, our own furred mobs), dried by a shake.

### Patch 7, Babies and rare pets (Kingdoms + Powers; picks baby-variants, biome-variants, name-variants)
- Babies for spiders and every wild/farm animal without one, ours included where it fits.
- Rare pets: some villagers keep an animal from a far biome (a snow fox in the desert, a cherry-blossomed one in the
  plains) by their home, and brag about it in talk as an accomplishment.
- A few hidden name-tag looks; no screen text, villagers may hint.

### Patch 8, Stuck weapons (Powers; pick zombie-weapon)
- Some zombies spawn with a weapon stuck in them and pull it out on first sight of you (you can then pick it up when
  they die). Throw a dagger at a zombie and it stays in.
- Run Through can leave your sword stuck in the body: low chance, higher for axes, shrinking with weapon experience.
  Stuck in a living foe, you pull it free with use; in a corpse, it stays until you take it.

### Patch 9, Props (Kingdoms/Kitchen; picks props, plushies, planters)
- Placeable blocks: scarecrow (keeps crows off crops), gravestone, lamp post, candle stand.
- Plushies as gifts for MCA children and spouses; squeak on right click.
- Window box and pot planters that grow small flowers and Kitchen herbs.
