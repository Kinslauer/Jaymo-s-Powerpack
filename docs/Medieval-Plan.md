# Medieval mods cross-reference: the plan (2026-11-15)

Andy picked 87 of the 90 rows on the pick page (https://claude.ai/artifact/S51onucWyjSXtHmYRZiEyk). The sources are
Derelict (mim1q, code MIT, art ARR), Mob Senses (Shamanalle, MIT), Valkyrien Pirates (JSJBDEV, MIT), A Game of Blocks
(ARR), Armed Mobs Warfare: Across the Ages (ARR), Guarding (Team-Abode, CC0), Middle-earth (ARR), Natural Disasters:
Doomsday (ARR), Civilizations (ARR) and Moog's Mineshafts Reimagined (already in the pack). Everything is built with
our own code, names, models and textures. The MIT and CC0 code may be borrowed with credit in a comment; the ARR mods
are ideas only.

**Not picked:** silk gear from the nests (de-silkgear), shipping Mob Senses unchanged (ms-ship), the seismograph
(nd-seismograph). Nothing else is left out.

Standing rules that bind every patch:
- No slash commands for player features (UIs, items and blocks instead). An op-only test switch is allowed where Andy
  asked to test (the world's ending).
- No discovery chat text and no screen text that states lore or explains a mechanic. Signs are looks, sounds, bars,
  speech bubbles and wordless films.
- No new dimensions. Our own names (no Tolkien, no real rulers or gods).
- No free projectiles: thrown decoys, cannonballs, kegs and arrows are real items spent from somewhere.
- Gold and diamond gear stay off. Netherite is allowed.
- Tuning numbers are `static final` constants at the top of each class; everything starts gentle.
- Magic effects go through `MagicFx` (Powers) or `Fx` (Kingdoms).
- New creatures follow the three rules: sympathy, feedback loops, stewardship.
- PRs never touch `version=`, the MODLOGs or `docs/codemap/`; each mod changed gets `changes/<mod>/<branch>.md`.
- Kingdoms lang keys go in `lang/parts/<feature>.json`.

## Andy's row notes, folded in
- **de-struggle**: "emberborn burn any webs they contact." An Emberborn player or mob who is webbed burns the web off at
  once (all layers), and any cobweb or web block an Emberborn touches burns away.
- **de-spiders**: "you can awaken from a death roll, webbed up inside a lair instead of poisoned and out in the world."
  A new black-screen wake-up scenario for the first two deaths: you come to webbed up (all three layers) in a spider
  lair (a nest cave if one is near, else a small lair hollowed out underground), with your gear in an egg-hung cache.
  It replaces the "poisoned, out in the wild" wake when a spider killed you or you died underground near spiders.
- **de-flicker**: "court wizards have developed a spell that lets a light mote inside a lantern burn indefinitely."
  Court wizards sell an **Everburning Mote**: hand them a lantern and coin and you get back a mote lantern whose flame
  is a little drawn mote (Fx mote) that never runs out under Lamplight's ten-day fill and never flickers. Lamps in
  abandoned places flicker and stutter as the row says.
- **vp-patrols**: "little to no chat indicators; fireworks and horns should be heard, maybe drums from approaching orc
  and barbarian warbands." A patrol airship that spots a war party fires signal fireworks over it and sounds a horn the
  realm hears; orc and barbarian war bands beat war drums as they come (a sound that grows as they close). No chat.
- **vp-redstonecannon**: "the cannons should all look real, no dispenser type blocks." A real cast cannon on a timber
  carriage, its own model, fired by redstone; it loads cannonballs and powder from a chest or hopper behind it. No
  dispenser block anywhere.
- **nd-doom**: "I want to test this." The world's ending is built in full, natural rolls are very rare and late, and
  an op-only switch starts and stops it on a test world.
- **nd-harbinger**: "doomsday should be tied to the arsenal. maybe he gets an artifact that warns him how powerful and
  destructive it can be. his activation will bring upon the end of the world." The relic is something the Pocket
  Arsenal pulls through late in its mastery (see the lore below). Each lesser calling (pick a spot on a map, a disaster
  falls there) warns its holder with worse visions (red sky flashes, the ground shaking under them, a low toll). Holding
  it to the end, past the warnings, brings the world's ending (nd-doom).
- **cv-coins**: "old age money was emeralds." The old peoples traded in emeralds. Their ruins hold emeralds, and money
  changers and collectors pay well for them in culture coins. No separate old-coin item.
- **cv-avatars**: "don't the patrons have champions?" Yes: `PatronChampion` already gives each patron a champion boss.
  An offering at an old-age temple calls down that temple's patron's champion; beating it gives a boon.

## Patches
Two tracks at a time, each in its own worktree; each patch gets an independent verifier that checks it row by row.
All patches go up as one PR (patches as separate commits).

### Patch 1, Webbed up (Powers `power/web`, Kingdoms `wild/Spider*`, Aether effects; rows de-wrap, de-layers, de-struggle, de-firstperson, de-spiders, de-flicker)
- A **Webbed** effect with three layers (dense, thick, thin). Each layer wears off on its own clock; while any holds you
  can't walk (only hop), milk doesn't clear it. Every web hold (Web's shots, web bomb, webbed fists' third hit,
  cocoons, hanging targets) adds a layer, up to three, instead of slowness and a cobweb at the feet.
- Drawn: a web skin over the whole body (our own sparse, medium, dense textures) and cobweb tufts round the victim.
- Struggle (break-free mashing) tears a layer sooner; a blade hit or a friend's use with a blade cuts one; fire burns
  all. Emberborn burn webs they touch (note above).
- First person: strands of web at the screen edges, thinning by layer.
- Spiders: cavern spit, flyer dragline, a mother's bite web you up; the Aether webbed affliction uses the same look.
- Lair wake-up after a death roll (note above).
- Flickering lamps in abandoned places; the court wizards' Everburning Mote (note above).

### Patch 2, Spider nests (Kingdoms `wild/`, worldgen; rows de-netcaster, de-spiny, de-nests, de-eggs, de-broodqueen)
- Netcaster: a long-legged deep spider that throws a net from range, then closes in; sometimes drops a silk gland.
- Spiny burst spider in crimson and warped forests: runs in and bursts into web.
- Spider nests: an Overworld cave region of ours (silk walls, hanging webs, shed skins, egg clusters, dark spider
  stone); a web-covered surface hut with a map to the deepest nest.
- Egg sacs that split into spiderlings when someone comes near; the brood goes for whoever woke them.
- The brood queen: boss bar, intro film, wakes when her egg is struck three times, landing blast, leg slams, web spit,
  fang grab (struggle or be poisoned), spiders off her back, bigger and faster as she's hurt; egg regrows in an hour;
  a knowledge level gates waking her. Feedback loop: a nest's brood thins the cave's bats and critters and spreads
  when fed; a burned-out nest stays dead.

### Patch 3, Hearing (Kingdoms `Senses`, `Awareness`, `VoiceRange`, HUD; rows ms-everyone, ms-walls, ms-surfaces, ms-weather, ms-decoys, ms-suspicion, ms-ring, ms-guns, ms-voice, ms-blood, ms-meter)
All eleven as written. Sound occlusion ray borrowed from Mob Senses (MIT) with credit.

### Patch 4, The sky (Kingdoms `sailing/`, SteamAirships, wars; rows vp-skypirates, vp-helm, vp-patrols, vp-bombs, vp-boarding, vp-wrecks, vp-redstonecannon)
All seven, with Andy's notes on patrols and real-looking cannons.

### Patch 5, Realms and the intro (Kingdoms LifeStory, Outfits, laws, boards; Powers ClothCape; rows gb-faith, gb-realmcards, gb-customs, gb-tent, gb-capes, gb-sails, gb-haggle)

### Patch 6, Troops, formations and castles (Kingdoms Followers, guards, war parties; rows gb-troops, gb-formations, gb-castles, me-ranks, me-shieldwall)

### Patch 7, Sieges and war (Kingdoms wars; rows am-sieges, am-sappers, am-warchest, am-upgrades, am-feuds, am-hunts, am-homeraid, am-whiteflag, am-colours, am-fixer, am-mages)

### Patch 8, Shields and smithing (Powers `combat/Guard`, enchanting, smithing; Kingdoms goblins; rows gu-reflect, gu-pummel, gu-barbed, gu-netherite, gu-instant, me-sidesmith, me-alloys, me-mail, me-reheat, me-inscribe, me-goats, me-goblinraids)
Guarding is CC0, so its reflect and instant-block code may come in directly.

### Patch 9, Disasters (Kingdoms `disasters/`, Powers FallingStars and Arsenal; rows nd-volcano, nd-tsunami, nd-hurricane, nd-meteor, nd-slides, nd-lightning, nd-hailhelm, nd-tiers, nd-chains, nd-signs, nd-doom, nd-harbinger)

### Patch 10, Lost peoples and the mineshafts (Kingdoms structures, money, bosses; rows cv-ruins, cv-coins, cv-weapons, cv-rulers, cv-avatars, cv-music, cv-trade, mm-miners, mm-loot, mm-collapse, mm-creatures)

## Lore placements (Andy approved 2026-11-15; now in Lore.md)

(new) **The silk deeps** (medieval picks, 2026-11-15, Claude's placement). Under the old hills some caves have gone to
the spiders: silk over the walls, shed skins underfoot, egg sacs hung like fruit, the stone itself dark with them. At
the deepest lies a **brood queen**, a spider grown huge on the cave's life, sleeping on her egg; strike the egg and she
wakes. Above, a web-hung hut where someone once watched the nest keeps a map down. Spiders that take a living thing
alive carry it home wrapped, which is how the dead sometimes wake there. Deep below the nests the **netcasters** hunt
with thrown silk, and in the crimson and warped woods of the Nether a small spined spider bursts into web.

(new) **The everburning mote** (Andy, 2026-11-15: "court wizards have developed a spell that lets a light mote inside a
lantern burn indefinitely"). It is a court wizard's working, sold to those who can pay: a mote of their light shut in a
lantern, which never needs oil and never gutters. Abandoned places have no one to tend their lamps, which is why the
lamps of ruins flicker.

(new) **Sky pirates** (medieval picks, 2026-11-15). When the steam towns first put balloon ships in the air, some were
taken, and their takers stayed up there. Sky pirates keep a cove on a high peak, circle what they've marked, and come
down on caravans, ships and towns with broadsides and powder kegs. A ship whose helmsman falls drifts on the wind and
sinks. Rich realms keep a patrol airship of their own, lent by the steam towns, that fires a signal over a war band and
blows a horn; the orc and barbarian bands beat war drums as they come.

(new) **The faith you were raised in** (medieval picks, 2026-11-15). Everyone grew up praying to someone. A life story
now names the patron of your childhood, which a person may later keep or leave.

(new) **The realms' ways** (medieval picks, 2026-11-15). Each land's realms keep their own manners and laws: the north
allows the duel, the sands charge for water, Blossom bows on greeting, the plains keep market days, and what is a crime
in one is nothing in the next. Their soldiers come in five ranks (militia, soldier, knight, veteran, leader), and the
militia lock shields in a wall with the archers behind. Keeps change hands in war and fly the winner's colours. A war on
a seat comes in waves, with a lull between, and the realm's war chest pays for its defence; its court casters march
with its war bands.

(new) **The realm steels** (medieval picks, 2026-11-15). Each people keeps its own steel: the Stoneborn a deep steel
worked with lead, the forest folk a bright steel with silver in it, the orcs a crude bronze with ash. A realm's armour
and weapon patterns are its own, forged only by its sworn smiths or from a captured pattern. The Stoneborn ride the big
mountain goats no one else can tame, and the mountain goblins come down from the north in hordes.

(new) **The old age's peoples** (medieval picks, 2026-11-15; Andy: "old age money was emeralds"). Before the long
defence, every land had a people of its own who are gone now: stepped tombs in the sands, columned temples on the
plains, barrows and long halls in the north, a walled palace in Blossom. They traded in **emeralds**, which is why
villagers still prize them and money changers buy them. Their rulers lie in their tombs with their guard and wake when
the tomb is broken. Their temples were the patrons' before the realms', and an offering there still calls the patron's
champion down to test the one who made it. A few of their descendants live on hidden in the ruins, speaking the old
tongue and trading old goods and maps.

(new) **The great disasters** (medieval picks, 2026-11-15). The world is not quiet under the realms: mountains in the
hot lands wake as volcanoes, quakes under the sea send waves inland, hurricanes come off the sea, a star sometimes
falls far bigger than the rest and leaves a crater with a heart of meteoric iron, and slopes let go their snow and
earth. Each comes in its strength, from minor to catastrophic, and one sets off the next. Animals know first.

(new) **The Last Bell** (Andy, 2026-11-15: "doomsday should be tied to the arsenal ... his activation will bring upon
the end of the world"; Claude's name and shape). Late in its mastery the Arsenal's reach pulls through a thing it should
not: a cracked bronze bell from a world that ended. Rung over a map, it brings a disaster down where it is pointed; each
ringing shows its holder more of what it did to its own world. Rung to the end, it does it again: the sky goes red,
stars fall to the bedrock, the sea comes in, the mountains open. Very rarely the world comes to that end without anyone
ringing anything.
