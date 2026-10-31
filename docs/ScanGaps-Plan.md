# Xeno-Band scan gaps: the plan (2026-10-31)

Andy's picks from the scan-gaps list (91 ticks, saved 2026-10-31): 2 fixes, 21 new aliens, and DNA rows for every
mob the band couldn't scan. This plan follows on from `docs/XenoBand-Plan.md` (patches 0-9). Patches here are
lettered (Scan A to Scan G) so they don't clash with those numbers.

## How the picks are read

- **An alien's main source always counts.** Ticking an alien means its main mob gives it (Rime from a stray, Ooze
  from a slime, Howl from a wolf, and so on), even where that mob's own row wasn't ticked.
- **Extra DNA only where ticked.** Unticked "also" mobs (fox, cat, lion, polar bear, dolphin, parrot, ghast...) stay
  unscannable. Andy's notes add some back: Rime also comes from a snow golem, and Ooze takes every source listed.
- **Andy's "No scan" notes win over the suggestion.** That covers hornkin/brute/hurler/warlord, the aerbunny, and
  demon soldiers/captains.
- **Scanning a no-scan mob** does nothing, as today: the band never starts a scan on it.

## Already done

- **Clockwork golem gives Rivet, not Bulwark.** Merged in PR #769 (0.18.98).
- **Wind dragons and the war elephant no longer give Velox.** Merged in PR #769. Dragons get their Wyrm scan in
  Scan A. The elephant gets Towerback in Scan G.

## Order

1. **Scan A** first. It's small and fills the gaps on aliens we already have.
2. **Xeno-Band patch 5** next (Gyre, Cortex, Locust, Croaker, Dud). It also takes the breeze for Gyre and the
   tadpole for Croaker.
3. **Scan B to Scan G**, then Xeno-Band patches 6-9. Patch 6 (earning aliens) is easier once every alien exists.

## Patches

### Scan A, more DNA for the aliens we have (Powers, small)

**Built (0.18.100):** all of the below, in `XenoDna`.

Each line below is a new scan for an existing alien. Mobs from other mods are matched by id, so nothing breaks when
the mod isn't loaded.

- **Wyrm:** the Kingdoms wind dragons (Aeolus, Boreas, Eurus, Notus, Zephyrus), Naturalist's komodo dragon, and the
  Visage of War.
- **Bulwark:** Naturalist's bear and black bear.
- **Velox:** Naturalist's ostrich.
- **Gremlin:** Naturalist's lizard, the Friends & Foes rascal, and the Kingdoms loot goblin.
- **Duplex:** the Friends & Foes copper golem. It's matched by id so both copper golems count.
- **Prism:** the Friends & Foes tuff golem and the Kingdoms gorgon.
- **Pyre:** the Friends & Foes wildfire.
- **Gorger:** Enderscape's rubblemite and Naturalist's giant isopod.
- **Slipfin:** Naturalist's bass, catfish and blobfish.
- **Wisp:** MCA's Grim Reaper and ancient cultist, the Kingdoms wraith and flame wraith, and the haunted armour
  stand.
- **Knell:** the Kingdoms skulk golem.
- **Rivet:** the Kingdoms redstone golem and mimic, plus our own machines (android mech, war mech, Omen mechs,
  android, worky drone, test unit).
- **No scan, written down:** farm animals (cow, pig, sheep, rabbit), the walking dead (zombie, drowned, zombie
  villager, zombified piglin), illagers (pillager, vindicator, evoker, witch, vex), piglins, villagers and wandering
  traders, the old giant zombie, turkey and duck, capybara, every MCA villager and Kingdoms folk, hornkin,
  the Piglin King, demon soldiers and captains, the aerbunny, and every named character. These are listed in one
  `NoDna` table so later scans can't pick them up by accident. For example, the redstone golem is an iron golem
  subclass, the same kind of DNA clash that #769 fixed.
- **`docs/xenoform.md`:** gets a full "what scans to what" table.

### Scan B, cold and bone (Powers, large)

**Built (0.18.107):** all four, with these changes: Rime doesn't stand on powder snow yet (that needs a mixin);
Blight's shield comes on by itself at half health, so its B is a Dark Pulse instead, and Rot Touch withers without a
bleed.

- **Rime, the frost alien:** scanned from a stray, a snow golem or the Friends & Foes Iceologer. Per Andy's note, it
  has the whole Cryokinesis kit. It reuses the Cryo classes, so it fights like the Cryo power:
  - R is Frost Breath (held).
  - G is Ice Shards.
  - V is Flash Freeze.
  - B is Ice Wall.
  - Sprinting on the ground is Ice Slide. You skate on a lane of ice.
  - The Ultimate is the Blizzard.
  - Passives: frost armour that chills whoever hits it, it never freezes, and powder snow holds it up.
- **Marrow, the bone alien:** scanned from a skeleton, a silver skeleton or the Skeleton Lord.
  - R is bone spikes from the ground.
  - G throws its own ribs. They come back like a boomerang.
  - V is a bone shield.
  - B is Fall Apart. It drops into a heap and the mobs hunting it lose interest.
  - Once per change, a killing blow drops it into a heap and it stands back up at half health.
- **Swathe, the wrapped one:** scanned from a husk.
  - R is a bandage lash that ties a mob up, much like the bolas trip.
  - G pulls a mob in with a lash.
  - V is a sandstorm. It blinds, and it's stronger in deserts.
  - B is Wrap. It mends its own wounds.
  - It can't drown or starve while changed.
- **Blight, the wither alien (boss tier):** scanned from the Wither only.
  - It has three heads that spit wither skulls: R is one skull and G is a volley of three.
  - V is Rot Touch. It adds Wither and a bleed, and dead grass and leaves turn to dust.
  - B is a dark shield at half health. It turns arrows back, like the Wither's armour.
  - It's big: scale 1.4 and 60 health.

### Scan C, beasts (Powers, large)

**Built (0.18.109):** all four, with these changes: Talon is also scanned from a parrot, and Prowl from a Naturalist
lion; Talon's grab is its own hold (the mob hangs under you), not the player carry code; Rend isn't in Powers, so the
rakes and gores open a dirty cut through the wounds code; Prowl's stalk mark makes you stronger near the prey (Strength
II within 6) rather than adding damage to each blow; Root Out's "truffles" are vanilla roots and mushrooms.

- **Howl, the pack hunter:** scanned from a wolf, a werewolf, a feral wolf or a coyote. It isn't the werewolf curse.
  - R is a pounce.
  - G is a howl that calls nearby wild wolves to fight beside you for 60 s.
  - V is Scent. It shows blood trails and the bleeders who left them. It uses the 0.17.182 blood stains.
  - B is a bite that holds on.
  - It's stronger at night.
- **Prowl, the great cat:** scanned from an ocelot or a Naturalist tiger.
  - It moves silently: no footsteps, and it's hard to see while sneaking.
  - R is a long pounce that pins.
  - G is a claw rake that bleeds, using Rend.
  - V is a stalk mark.
  - B is a roar that makes mobs flee.
  - It always lands on its feet (no fall damage under 12 blocks).
- **Tusk, the war boar:** scanned from a hoglin, a Naturalist boar or rhino, the Kingdoms minotaur, or the Aether
  burrukai, kirrid or taegore.
  - R is a head-down charge that tosses mobs.
  - G is a gore that bleeds.
  - V is Root Out. It digs up truffles and roots: food, plus a random seed or bone meal.
  - B is Thick Hide. It shrugs off knockback for 10 s.
- **Talon, the raptor bird:** scanned from a vulture or a Naturalist bird.
  - It flies.
  - R is a dive that grabs a mob and carries it up, using the carry code. G or a timeout drops it.
  - V is far sight. Foes are marked through fog out to 96 blocks.
  - B is feather darts.

### Scan D, water and light (Powers, large)

**Built (0.18.111):** all four, with these changes: no vanilla shark exists, so Gnasher is scanned from a dolphin
(and the mod mobs by id); Lumen's lights are vanilla light blocks it puts out itself, so a crash while one burns
leaves it standing; Keen's sonar outline is particles only you are sent, not a glow.

- **Murk, the ink squid:** scanned from a squid, a Naturalist anglerfish or a Friends & Foes barnacle. It's the
  second swimmer.
  - R is an ink cloud that blinds.
  - G is four tentacles that grab up to four mobs.
  - V is a jet dash.
  - B is Lure. A glow in deep water draws fish and mobs in.
- **Gnasher, the shark:** scanned from a great white shark, an alligator, a piranha or a Friends & Foes mauler.
  - It's a fast swimmer that smells blood in the water. Bleeders in water within 48 blocks glow for it.
  - R is a clamp bite that holds on.
  - G is a lunge out of water onto the shore.
  - V is a frenzy. It gets faster with each bleeder nearby.
  - B is a death roll.
- **Lumen, the light:** scanned from an allay, a Naturalist firefly or a Friends & Foes glare. It's the support alien.
  - R is a healing glow on friends.
  - G is a flare that blinds.
  - V is Light. It leaves short-lived light where you look, like the glare.
  - B is Fetch. Like an allay, it brings dropped items of the kind you hold.
- **Keen, the sonar bat:** scanned from a bat, a Kingdoms vampire bat or a blightwing. The Night Bleeder is an undead
  illager, so it gives no scan.
  - It flies, and hangs from ceilings when you sneak under one.
  - R is a sonar ping that outlines everything within 24 blocks for 6 s. Only you see it.
  - G is a screech that knocks flyers down.
  - V is a bite that drains health.
  - B is Echo, a dash in the dark.

### Scan E, swarm, web and tricks (Powers, large)

**Built (0.18.113):** all four, with these changes: Skitter clings to a ceiling while jump is held and crawls along,
drawn upside down for everyone and with its own camera rolled over (Andy's ask; mouse and strafe are flipped to match
the rolled view); Shade's blink-away when hit is B arming it for 10 s (the next blow only), so B has a
key; Mirage's copies follow you like Duplex's doubles rather than wander, and last 15 s.

- **Hive, the swarm:** scanned from a bee, a Naturalist ant, or a butterfly, caterpillar or dragonfly.
  - R is Scatter. You become a cloud of insects that dodges and makes short flights.
  - G is a poison sting.
  - V is Honey Glue. It roots a mob.
  - B sends a swarm at a target.
- **Skitter, the eight-legged:** scanned from a spider or a Kingdoms tarantula. Unlike the Web power, it's a body and
  not a suit.
  - It runs on walls and ceilings. That's stronger than everyone's wall climb: it holds on upside down.
  - R is a web line to swing on.
  - G ties a mob with web.
  - V is a poison bite.
  - B is Egg Sac. It drops 3 cave-spider minions for 30 s.
- **Shade, the stealer:** scanned from an enderman or Enderscape's drifter.
  - R lifts a block out of the wall, the way Polestar lifts iron blocks. R again throws it.
  - G is a stare that freezes the mob you lock eyes with.
  - V is a blink.
  - B is a blink-away when hit, on a cooldown.
  - Water hurts it.
- **Mirage, the illusionist:** scanned from an illusioner (vanilla or Friends & Foes).
  - R throws three false copies. They vanish when hit and can't fight. They are XenoDouble entities with a "fake"
    flag.
  - G blinds a target.
  - V goes unseen for 5 s.
  - B swaps places with a copy.

### Scan F, odd bodies (Powers, large)

**Built (0.18.117):** all four, with these changes: the magma cube scan is its own alien, Magma Ooze (same kit,
burning look, fire immunity, magma-cube blobs), since a scan gives an alien rather than a variant; Ooze's blobs are
small vanilla slimes (magma cubes) that fight for it, not copies of the alien; V Squeeze shrinks Ooze 60% rather than
forcing the crawl (everyone can crawl already); Fuse's Self-Destruct is one blast and then four small ones a tick apart
(not the meteor code itself); Billow's riders sit on its back as passengers of the player, set side by side by a mixin.

- **Ooze, the living slime:** scanned from a slime, a magma cube, a sulfur cube, the blue or golden swet, or the
  Kingdoms mud golem. Andy's note: all of these work.
  - It splits into smaller copies of itself as it takes damage. They join back up when you stop.
  - It bounces off every fall.
  - R swallows a small mob and G spits it out.
  - V squeezes through 1-block gaps (the crawl pose).
  - B is Split, which you can trigger yourself.
  - A magma cube scan gives the burning look and fire immunity.
- **Fuse, the blast alien:** scanned from a creeper.
  - Its punches pop.
  - R sticks a blast pod on something.
  - G is a fuse dash.
  - V is a held charge that blows up the alien and re-forms it a moment later. It costs band charge, not health.
  - B is Defuse. It soaks up a nearby explosion.
  - It uses the 0.17.200 spread-over-ticks blasts so it doesn't lag.
- **Thornwild, the plant:** scanned from a creaking or Enderscape's rustle.
  - R is roots that hold a mob fast.
  - G is a thorn volley.
  - V grows a hedge for cover. It decays after 30 s.
  - B is Bloom. Crops grow where it stands.
  - It heals in sunlight.
- **Billow, the cloud:** scanned from a happy ghast or an aerwhale.
  - It's a floating cloud up to three friends can ride. They sit on its back like the happy ghast.
  - R is a downpour that puts out fire.
  - G is a thunderhead burst.
  - V is a gust.
  - B is Fog.

### Scan G, Towerback (Powers + Kingdoms, large; one alien on its own)

- **Towerback, the true giant:** scanned from a Highfield, Arctic or Irradiated Giant, or the Kingdoms war elephant.
  - **Size choice, per Andy's note:** Z picks between two sizes:
    - 5x your size (about 9 blocks tall).
    - Monolith-giant size. The Giants are 30.6 blocks tall, but the scale attribute stops at 16x, so this is 16x
      (about 29 blocks).
  - Every step stomps with the Giants' shockwave. Jumping avoids it, like the Giants.
  - R grabs, squeezes and slams like a Giant, with the letterbox film.
  - G is a stomp.
  - V is a swat.
  - B is a roar.
  - It's slow and huge. The giant camera (0.15.x) and reach scaling come for free.
  - The Giants' stomp, grab and slam live in Kingdoms. Powers gets its own copies, tuned the same, so it doesn't need
    Kingdoms loaded. Kingdoms only adds the scan targets by id.
  - It's L tier: no fitting through doors, and blocks it walks into at the big size are trampled. That would use the
    Giants' trample rule, at chest height and above.

## Not picked (left as they are)

Fox, cat, lion, chicken, parrot, dolphin, ghast, magma-only Pyre, polar bear, penguin, mammoth, panda, ravager,
sniffer, mooshroom, strider, nautilus, glow squid, endermite, evoker, wither skeleton, bogged, parched, cave spider,
whale and great whale, ray, crab, clam/starfish, jellyfish, hedgehog, mole, deer, elephant (Naturalist), giraffe,
hippo, snake, scorpions, rat, snail, tortoise, ogres, moose, messenger pigeon, sheepuff, phyg, moa, cockatrice,
prismallard, skyroot lizard, glitterwing/shroudwing, tempest/zephyr, Aether plants, bladeshroom hunter, skephid,
talutons, sentries, flux wisp, Frostbite, clockwork hornet, symbiote blob. Each can be added later with one line in
its alien's `scans`.

## Open questions (Claude assumes the first answer and carries on)

1. **The dial gets crowded.** There will be 42 aliens after Scan G. Should the dial page in rings of 12, or switch to
   a scrolling list? (Assumed: rings of 12, with scroll to change ring.)
2. **Towerback's monolith size is 16x (about 29 blocks), not the Giants' full 30.6.** Is that fine? (Assumed: yes.)
3. **Blight is boss tier.** Should it be hidden like Nadir and Lurk (not needed for a complete band)? (Assumed: yes,
   hidden.)
4. **Does Rime's Blizzard replace its Ultimate,** or is the Ultimate the big form with the Blizzard as its C?
   (Assumed: the Ultimate form, with the Blizzard as C, matching patch 2's rule.)
