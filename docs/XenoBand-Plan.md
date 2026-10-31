# Xeno-Band add-on picks: the plan (2026-10-31)

Andy picked 41 of 44 rows on the pick page (https://claude.ai/artifact/BNdM2M8Y5WHZzd9tgYK1yA, copy in project
files `alien-mods-study/alien-picklist.html`). The rows come from four All Rights Reserved add-ons for a 1.20.1 alien-watch
mod: the Galvan Initiative, Aliens Untapped, Into the Omniverse and Rising Storm. We take ideas only, with our own
names, code and vanilla-style art, and never the show's alien names. Rows he left out: Share an alien, Display case and
Theme disc.

Standing rules that bind every patch:
- No new dimensions or portals. Space is the pod's existing worlds.
- Lore comes only through talk and books, never screen text that states it.
- Iron tools and parts are forged, never made at the crafting grid.
- Every new recipe or route shows in JEI.
- Optional mods only through compat classes.
- **Only two bands exist in the whole world** (Andy, 2026-10-31): the Xeno-Band and the rival band. Nothing copies
  either one. Reforging upgrades the band itself. Down a wearer and tap them to tear their band off yourself (into your
  inventory, or dropped at your feet if it's full), and from then on it's yours to wear, keep, trade or throw away. A
  wrist wears one band at a time.

Andy's notes from the page, folded in below:
- **Nadir:** "needs a flight akin to creative on double tap space. he needs the float that wind has on it as well, but
  he needs another rule to activate it, ME-Can Fly- In air". My reading: whenever Nadir can fly and is off the ground,
  he takes Wind's floating pose, not only once he's flying fast. Patch 0.
- **Ultimates:** "I'd also like to see real ultimate forms, all we have are a few special attacks". Patch 2.
- **Timer:** "the last 5 secs should beep and flash". Patch 0 (and the battery keeps it, patch 1).
- **Reforge the band:** "crafted in space on a moon in a randomly generated structure that houses an alien with the
  forge, you just need the material". Patch 7.

## Patches

The patches run in this order. Each is its own PR. Every finding gets an independent verifier, and the version is set
to master + 1 just before merging. Almost all of this lives in Xenoform's own files (the form registry, the models, lang
`xenoform.json`, `tools/gen_xenoform.py`). So unlike the per-power tracks, these patches stack one after another; only
patches 6 (Kingdoms side) and 9 (art) can run beside another.

### Patch 0, Andy's notes (Powers, small, first)
- **Last 5 seconds:** `BEEP_TICKS` drops from 200 to 100. Each beep also flashes the screen edge red and pulses the
  alien's red glow (`XenoformClient.ENDING`), and the beeps quicken over the last 2 seconds.
- **Nadir's flight:** `mayfly` is set again every tick while you're Nadir, so a respawn, a game-mode change or another
  power can't strip it, and double-tap Jump always works. He gets a new flight kind `FLY_FLOAT`, used whenever he can
  fly and isn't on the ground, so `WindPose` gives him Wind's floating hover pose even when still or slow. The cruise
  lean keeps its 0.25 a tick threshold.

### Patch 1, The band's charge (Powers, medium; picks battery, quick-change, failsafe, randomizer, band-jammer)
- **One band in the world:** the world keeps a record of the Xeno-Band (`XenoBands`, saved data): who wears it, or
  where it lies as an item. A fallen star only holds the band while no one has it and no band item exists. Taking it off
  (dying to another player or a mob that killed you on purpose, see below) ends the power and makes the band an item
  again. A wearer killed by another player loses it to the killer. A band item that's destroyed (lava, the void,
  despawning) goes back to the stars, and the next fallen star carries it. The rival band follows the same rules
  (patch 8).
- **Charge:** a band charge replaces the random 1 to 5 minute timer. Changed, it drains one point a tick, and faster
  when you're hit or when a move goes on cooldown. As a human it refills. Mastery makes it bigger, from 2 minutes up to
  5 at full mastery. The HUD bar becomes the charge, and the last 5 seconds beep and flash (patch 0).
- **Quick change:** while you're an alien, hold Z and scroll to another alien. It costs a quarter of the charge, and
  you're dizzy for a second.
- **The band saves you:** a killing blow while you're human slams the band for you, before Downed. The form fits the
  death: a fall gives Wyrm or Wisp, drowning gives Slipfin once it exists, fire gives Pyre, and anything else gives
  Bulwark. Ten minutes between saves.
- **Wild dial:** a band screen toggle. Every 1 to 60 seconds you become a random alien you know, and you can't turn
  back. Moves count double for mastery.
- **Jammed:** an effect from an alchemy brew, or from a guard's weapon in realms that know about aliens. It turns you
  back to human and blocks the slam for 20 seconds.

### Patch 2, Real Ultimate forms (Powers, large; Andy's note)
Each of the nine Ultimates becomes its own evolved alien, not a buff on the same body:
- **Body:** a new model and skin per Ultimate (`XenoFormModels.Ultimate*`, `gen_xenoform.py`), bigger and armoured,
  with spikes, plates and glowing seams.
- **Kit:** each Ultimate has its own R G V B. Today's Ultimate move becomes one of them, mostly C, and the rest are
  stronger takes on the base alien's moves.
- **Change:** C as an ascended alien changes into it with a short film (`UltimateFilm`). It lasts until the charge runs
  out. C again drops back to the base alien.
- **Health:** the Ultimate keeps its own health on top of the base alien's.
- **New Ultimates:** Nadir gets none. The aliens added in patches 3 to 5 get theirs later.

Built (0.18.89): the body as planned (armoured skin from the base one, glowing seams, per-alien spikes, health on
top, bigger). The kit so far: R G V are the base moves with halved cooldowns and harder fists, B is the Ultimate's own
move, C again drops back. Brand-new R G V moves per Ultimate are left for a follow-up once Andy has seen these in game.

### Patch 3, Rivet and the block shells (Powers, large; picks rivet, shell-*, machine-merge)
- **Rivet:** the tenth alien, small and made of liquid metal, scanned from a clockwork golem.
- **Shells:** R melds Rivet into the block you're looking at. While melded, R G V B are that block's moves. There are
  seven shells: furnace, piston, anvil, stonecutter, smithing table, dispenser and note block, each as written on the
  page. Sneak + R pours you back out.
- **Ride a machine:** Rivet climbs into an android mech, a golem, a ship's cannon or a minecart and drives it. While
  you're inside it has green lines, more speed and more damage.

### Patch 4, New aliens A (Powers, large; picks gorger, slipfin, polestar, watcher-alien, duplex-senses)
- **Gorger:** scanned from an armadillo. It eats blocks to charge a blast, and has a horn charge that turns into a
  slam off ledges.
- **Slipfin:** scanned from an axolotl. It's the first swimmer, and grabs, nets and bolas slide off it. Kept out of
  water too long, it gasps.
- **Polestar:** scanned from the Magnetic Giant. It pulls and throws metal: iron armour, iron blocks and iron-tipped
  arrows.
- **The stalker:** near a monolith, something watches band wearers at night. Look straight at it three times and it
  leaves a shed skin to scan. It's a hidden alien, like Nadir.
- **Duplex:** sneak + V looks through the aimed double's eyes.

### Patch 5, New aliens B (Powers, large; picks gyre, cortex, locust, croaker, dud)
- **Gyre:** scanned from a turtle, a wind-shell turtle.
- **Cortex:** scanned from a guardian, a lightning-minded crab. It can read a creature's temper.
- **Locust:** scanned from a goat, a crash-landing hopper.
- **Croaker:** scanned from a frog. It has a tongue grab and swallows small mobs.
- **Dud:** never scanned, only learned from a corrupted band's glitch (patch 6). Nothing hurts it, it can't attack,
  and every mob nearby goes for it.

### Patch 6, Earning aliens (Powers + Kingdoms, large; picks code-tablets, alien-crafting, star-wreck, band-hunters, glitch-teaches, star-smith)
- **Star tablets:** found in fallen star loot, fishing treasure and remnant chests. Hold one while slamming and the
  band learns that alien.
- **Alien-only crafting:** Gremlin assembles tablets, Bulwark grinds ore into two dusts, and Pyre smelts what you carry.
  Each has a JEI page.
- **Star wrecks:** about one fallen star in ten is a wreck, with probes that keep coming while a band wearer is near.
  The probes drop circuit parts.
- **Band hunters:** wild android mechs come for band wearers at night and drop tablets.
- **A glitch can teach:** a corrupted band's wrong-alien glitch sometimes lands on an alien you haven't scanned, and the
  band keeps it. Dud is learned only this way.
- **Star-reader:** an MCA villager near old craters (Kingdoms). Trades tablets and module plans for star metal, and
  gives oblique hints.

### Patch 7, Upgrading the band (Powers, large; picks band-reforge, modules, mod-*, skill-tree, band-wipe)
- **Reforge:** done on a moon, in a structure generated on a random moon of the pod's worlds. An alien smith keeps
  the forge there. Bring the material (star metal, a wreck core and Bulwark's dust) and it reforges your own band
  (Andy's note).
- **Modules:** the reforged band has three module slots, opened from the band screen:
  - Cell: double charge and recharge.
  - Breather: breathe water, and thin air on other worlds.
  - Plating: armour, speed and no freezing, but no worn armour.
  - Steady or Wild: block or force the corruption glitches.
- **Skill tree:** mastery gives band points. They buy quick change, the death save, scan reach, a bigger charge and a
  faster recharge.
- **Forget an alien:** take one alien off the dial, or wipe the band clean.

### Patch 8, The rival band (Kingdoms + Powers, large; pick rival-band)
- **The hunter:** a band hunter, an outcast MCA villager wearing a dark-red rival band, hunts band wearers. He changes
  into his own aliens to fight.
- **Taking it:** beat him and you can take his band.
- **Its aliens:** the rival band has its own dial, starting with Gyre, Cortex, Locust and Croaker darkened. Your scans
  go to whichever band you wear.
- **The crime:** in realms that know about aliens, wearing it is a crime.

### Patch 9, Looks (Powers, art; picks alien-looks, band-custom)
- **Alien looks:** two or three looks per alien (plain, armoured, weathered), picked on the band screen.
- **Dress the band:** a glow colour that also tints each alien's green lines and the slam flash, a faceplate shape and
  spikes, with a turning preview.

## Open questions (Claude assumes the first answer and carries on)
1. Patch 2: does each Ultimate keep its current special move as its C, or should the move fold into the new kit with C
   just turning back? (Assumed: it stays as C.)
2. Patch 1: should the charge fully replace the random timer before mastery? (Assumed: yes.)
3. Patch 8: is Dud on the rival band's dial too, or only the four darkened aliens? (Assumed: only the four.)
