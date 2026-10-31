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

### Patch 0, Landings, flips and sitting (Powers 0.18.99 + Kingdoms 0.7.426; picks roll-landing, hero-landing, air-flip, balance, sit-anywhere)
- **Landing roll for everyone:** `LandingRoll` and `DodgeClient` no longer check Sure Footing. The skill check stays:
  Sneak tapped twice (or the dodge key) up to `EARLY_TICKS` before touchdown or within `LATE_TICKS` after. Sure
  Footing leaves the Freerun tree; its points come back (ranks are saved by key) and Bar Swing moves up into its slot.
- **Hero landing:** fall damage of `HERO_DAMAGE` (6) or more that you'd live through lands you in the three-point
  `WebPose.HERO_LAND`, with a thud and a ring of dust (`LandingRoll.dust`). Rolling in the late window takes it away.
  Every Web glide (wings) landing is a hero landing, and Web's hero landings get the dust too.
- **Air flips:** one tap of Sneak in the air, `AirFlip.MIN_HEIGHT` (2) blocks or more off the ground: forward (Forward
  or nothing held), backward (Back, new `WebPose.BACK_FLIP`) or a cartwheel (a strafe key). The client waits out the
  double-tap window so two taps stay the dodge and landing roll. Web slingers and Freerunner 1+; `AirFlip.COST` (2)
  Feathers.
- **Balance:** already done: `BalancePose` is everyone's and `freerun_narrow` already lists chains and end rods.
- **Sit anywhere:** standing still, sneaking and looking down past `SIT_PITCH` (65) for `SIT_TICKS` (5 s) sits you
  down cross-legged (the meditation pose); you stay sat after letting go until you move, jump or are hurt. Swinging
  or using an item resets the count, so bridging doesn't sit you down. With the Meditation perk, sitting is
  meditating. The I key is renamed Shadowbox and only does something for Z Fighters and Gravity users; anyone else
  pressing it gets a hint on how to sit.

### Patch 1, Body motion and temperaments (Powers 0.18.101 + Kingdoms 0.7.428; picks torso-lag, strafe-lean, hurt-flinch, scared-idle, scared-flee)
- **Torso twist** (`client/BodyMotion`): the chest turns `TORSO_SHARE` (a third) of the way after the head, at most
  `TORSO_MAX` (22) degrees, shoulders carried round; all players. Vanilla already lets the body lag the head.
- **Strafe lean:** while locked on (`LockOn.LOCKED`, a new synced flag), side steps lean the body `STRAFE_LEAN` (9)
  degrees into the move.
- **Flinch:** a hit tips the body away from where it came from while the red flash lasts; not while downed.
- **Player temperaments** (Kingdoms `PlayerTemperaments`): the life story's answers pick one of the 12 temperaments for
  good (who raised you and the childhood weigh 2, some roads 1), said once in chat. Old players get theirs on next join.
  There's no paranoid one, so timid and suspicious play that part: scared idle (hunched, glancing about, hands drawn
  in) when it's dark, under 40% health or a monster is within 10 blocks; and the panicked flee (`client/PanicCam`):
  sprinting at half health or less from a monster close behind shakes the first-person view and swings it round over
  the shoulder for about a second. Brave shows a third of the flinch, hotheaded stands with arms tense, gloomy slumps,
  cheerful bounces as they walk.

### Patch 2, Water (Powers 0.18.102 + Kingdoms 0.7.429; picks swim-direction, dives, wakes, underwater-break)
- **Directional swimming** (`client/water/SwimStrokePose`): in water over half your height, off the bottom, not
  sprint-swimming: breaststroke going forward, backstroke going backward, side stroke going sideways, treading water
  when still. Vanilla's sprint-swim crawl is kept. Only Tideborn (Dolphin's Grace while sprint-swimming) and Slipfin
  (already had it) get a faster kick.
- **Dives** (`water/Dives`): falling into water 2+ deep from 4+ blocks: Sneak held 8+ ticks before is a cannonball (big
  splash, fish scatter), Sneak pressed later is a clean dive (deeper, faster), neither is a belly flop (slap sound, 1-2
  damage from 12+ blocks). Our own splash, sized by fall and body size.
- **Boat wakes** (`client/water/BoatWakes`): a V of foam behind moving boats/canoes/rafts, larger behind Kingdoms ships
  and whales.
- **Breaking under water** (`TerrainParticleWaterMixin`): break bits sink slowly with bubbles.

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
