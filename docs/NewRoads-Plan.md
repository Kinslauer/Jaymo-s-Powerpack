# New roads: Kenshi and Streets of Rogue cross-reference, the plan (2026-10-30)

Andy picked 29 of 34 rows on the pick page (https://claude.ai/artifact/F66nVbuNypWGxh7q1zty4s, copy in project files
`kenshi-sor-study/starts-picklist.html`). Left out: Raid Gone Wrong, Mech Pilot, Born into a Feud, Hired Blade,
Barber-Surgeon. His notes: Cast Out is Bio-built only, Lab Escapee is Bio-built, Rubber-folk, Emberborn, Stoneborn or Sylvan; secret roads: "the only available starts
without this should be basic ones and req ones, like verdant and divine born" (read as: every other road is found by a
matching past or a deed; basic = capital, village, wild, sworn, banished, shipwreck, fate). Both games are commercial: ideas only. Our own names, own code,
vanilla-style art, no text or names from either game.

What we borrow: Kenshi's hard starts (the first hour is about getting out alive, and several starts give you a squad or
an animal), and Streets of Rogue's frame (characters unlocked by deeds done in any earlier run, one long personal goal
per character, flaws that pay for strengths, rule switches for a harder run).

Standing rules for every patch: no new dimensions or portals; no screen text that states lore (hints in talk and books
only); iron tools/parts are forged (Tinkers route), never a grid recipe; optional mods only through compat classes;
Kingdoms reaches Powers only through `PowersCompat` reflection; tuning numbers in `static final` constants at the top of
each class; every new road follows the existing pattern in `LifeStory` (a `Start` value, a `ROAD` answer with an icon,
a `begin` case, `kit` lines, lang keys `story.jaymoskingdoms.road.<key>`, `.tale`, `.short`, `.wake`, a backdrop in
`LifeStoryScreen.BACKDROPS`, the figure in `StoryFigure`), and `StoryGround` makes whatever a road needs if the world
has none yet (Andy: "generate whatever is required for the player to choose any of the options").

## Patches

### Patch 1, Hard roads (Kingdoms; picks in-chains, rock-bottom, sentenced, evicted, hideout, cast-out, labels)
- **Still in Chains** (`CHAINED`): wake shackled among a barbarian camp's captives (Captives/Caging). Pick the shackle
  with the Pin lockpicking game, or slip it after dark (sneak still for a few seconds at night). Once free, the
  Escaped captive road's day-long hunt runs (`HUNTS`). The figure is drawn bare and chained.
- **Rock Bottom** (`ROCK_BOTTOM`): the Unarmed road at its worst: a random limb gone, no kit at all (no bread), hunger
  near empty, and a looter band (Outlaws) camped in sight. Hardest mark on the carousel.
- **Sentenced** (`SENTENCED`, names a realm): wake in that capital's cells (Arrests/Busts) with days left to serve.
  Serve them (released at dawn after N days), pick the cell door, or pay the fine to the jailer. The realm starts cold.
- **Evicted** (`EVICTED`, names a realm): the capital road with a fuller purse but no home. Between dusk and dawn the
  watch moves you on (warning, then a night in the cells) while you sleep rough inside the walls, until you hold a
  Careers job or pay an inn for a bed.
- **The Hideout** (`HIDEOUT`): wake inside an Underworld speakeasy (or a barbarian camp when none exists) as one of
  them. Outlaws and camps spare you (the barbarian-outlaws rule), and one realm hates you at banished level.
- **Cast Out by Your Own** (`CAST_OUT`, Bio-built only via `onlyFor`): your own heritage's settlements turn you away (the Bio-built shunning path, for
  your heritage), and your map starts with nothing marked (Xaero waypoints off for the first days). Other heritages may
  take you in.
- **Difficulty and Purse on Every Road**: each carousel card shows a difficulty mark (Easy, Fair, Hard, Very Hard, Cruel)
  and the starting purse; set per `Start` in one table.

### Patch 2, Kit roads (Kingdoms; picks heirloom, pack-trader, pup, nobodies, watch-recruit)
- **The Heirloom Blade** (`HEIRLOOM`): wild landing with a fine Tinkers blade named for your family, no armour, bread
  only. Two to four days in, an Assassins contract or a hired band comes for it (Assassins/Outlaws).
- **Wandering Trader** (`TRADER`): on a King's Road by a milestone (Milestones) with a pack horse (Stables breeds,
  saddlebags filled with local goods) and two Merchant Orders already taken for the next capital.
- **A Pup at Heel** (`PUP`): a tamed wolf pup that grows up over a few days and gets tougher with age (health and
  damage step up per day of age, capped).
- **The Nobodies** (`NOBODIES`): three poor MCA villagers hired to you from the first minute (follow, carry, fight),
  allowed by the MCA strangers rule because they are hired.
- **Watch Recruit**: the Sworn road gets manacles. A downed outlaw can be manacled and walked to the cells (Arrests)
  for a bounty instead of killed.

### Patch 3, Powered roads (Powers + Kingdoms; picks lab-escapee, fresh-android, moon-cursed)
- **Lab Escapee** (`LAB`, Andy: Bio-built, Rubber-folk, Emberborn, Stoneborn or Sylvan only): wake in a hermit alchemist's lab (Alchemy) as a test subject with one random infusion that
  has a good side and a bad side; the alchemist's golems follow you out.
- **Fresh Off the Bench** (`ANDROID`): wake on the Mad Tinkerer's bench as a new android (Powers androids: endless ki,
  no food, metal repairs), with a note in your pocket.
- **Moon-Cursed** (`MOON_CURSED`): under a blood moon (Kingdoms `Moons`) you turn feral (fast, strong, attacks whatever
  is near, screen tinted, no control of targets). A temple or healer cure ends it.
The lab and android starts need Jaymo's Powers (`offered()`), reached by `PowersCompat`.

### Patch 4, Bands and companions (Powers + Kingdoms; picks start-together, companions-learn, drifters, prosthetics)
- **Wooden and Iron Limbs** (Powers `core/Dismember`): a smith fits a wooden or forged iron arm or leg for a severed or
  never-had limb. Iron is forged (a Tinkers part), wood is a carpenter's job. A fitted limb counts as present for
  LimbUse (weaker grip: slower swing, lower climb grip). Comes off when the real limb regrows.
- **Companions Who Learn** (Kingdoms): hired MCA villagers gain toughness and damage with each fight they survive (capped).
- **Drifters for Hire** (Kingdoms): down-and-out drifters in taverns and on roads, hired cheaply.
- **Start Together** (Kingdoms `Parties` + `LifeStory`): party members who pick the same road land at the same spot,
  sharing its kit and trouble.

### Patch 5, Roads earned by deeds (Kingdoms; picks deed-unlocks, book-of-lives, writ-full-story, secret-roads)
- **Deeds**: a server-wide record (saved on the Overworld) of deeds that unlock roads for everyone: free a chained
  captive -> Still in Chains; serve a sentence or break out -> Sentenced; sign on with whalers -> Whalers; survive the
  Pale Moon -> Moon; etc. Table in one place. Andy: only the basic roads (capital, village, wild, sworn, banished,
  shipwreck, fate) and the heritage-locked ones (Verdant World for Verdant-born, afterlife) are open from the start;
  every other road, old or new, is found by a deed or a secret past.
- **Locked cards**: locked roads show dim on the carousel with one oblique hint line (never lore).
- **A Writ opens the whole story**: a Writ of Rebirth reopens the full life story, unlocked roads included.
- **Secret roads**: some roads only show for a matching past (raised by outlaws + cutpurse youth -> The Hideout; healers
  + caves -> Rock Bottom). Each locked road has at least one of the two ways in.

### Patch 6, A life's aim (Kingdoms; picks lifes-aim, rise-up, broken-aim)
- **A Life's Aim**: each road gives one aim, spoken at the start or written on a journal page; finishing it gives a
  title and a small perk. Examples: Still in Chains, free five captives; Evicted, own a bed in that capital; Heirloom,
  kill the one who sent for the blade.
- **Rise Up**: finishing a hard road's aim unlocks a richer road (a deed for patch 5).
- **Turning Your Back on It**: betraying the aim (selling the heirloom, abandoning your band) leaves a mark: folk who
  knew your story trust you less (Temperaments).

### Patch 7, Choosing a harder life (Kingdoms; picks flaws, hard-roads, humble-start)
- **Flaws for Points**: on the story page, take up to two flaws (light sleeper, glass jaw, can't swim well, afraid of
  the dark) for one extra attribute point each.
- **Hard Road Options**: switches on the last page: everyone is wary (cooler with every realm), hunted (a bounty from day
  one), nothing but rags (no kit). Shown on the story page.
- **Start from Nothing**: give up your story's attribute points; finishing your aim pays them back double.

## Order

Each patch is its own PR, stacked where they share `LifeStory`. Two tracks at a time:
1. Patch 1 (Kingdoms) with the Powers-only part of patch 4 (limbs).
2. Patch 2 with the Powers-side part of patch 3.
3. Patch 3's Kingdoms roads and the rest of patch 4.
4. Patch 5, then 6, then 7.
Versions are set to master + 1 right before merging (see the merging rule).
