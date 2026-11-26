# Climate plan: body heat, seasons, lands (Andy's 40 picks)

Andy, 2026-11-25: "our temperature / damage system has issues, take everything you can from these to fix it"
(Tough As Nails, Serene Seasons, Biomes O' Plenty, TaN Curios Expansion, TaN Early Game Fix, TaN Compat Fixes).
All six are All Rights Reserved or unlicensed, so **ideas only**: our own code, names and art, nothing copied.
The first six bugs were fixed in PR #2184 (merged). On 2026-11-26 Andy ticked **all 40 rows** of the pick page
(https://claude.ai/artifact/BrY9DT2LgACdkphTLmSTiM, db `picks/andy`), with two notes: hot spring valleys and
volcanic lands belong on **Mohknee**.

The work is split into six tracks, so agents can run two at a time without editing the same files. Each track is
one commit on `claude/project-thread-mhtf6n`, with its own change notes. They all ship in one PR, because cloud
sessions may push only that branch. Every row below is built as written. A cut is named in the track's Done notes
along with the reason.

Rules every track keeps:
- Standing rulings: no slash commands for player features, no chat text for discovery (bubbles, sounds and tip
  cards are fine), mob griefing stays on, and everything works with the old systems and progressive world gen.
- Kingdoms reaches Kitchen only by reflection through `KitchenCompat`, and Powers only through its compat classes.
- Tuning numbers go in `static final` constants at the top of each class. Text goes in lang files, and Kingdoms
  text goes in its lang parts.
- Check every 26.3 API against the sources before using it. Living things follow the three rules (sympathy,
  feedback loops, stewardship).
- Original art only, in vanilla style.

---

## Track A: How body heat feels (Kitchen `climate/`)

**Owns:** `BodyHeat`, `Warmth` (except the drink table), `ClimateHud`, Kitchen block/biome tags it adds, and the
Kingdoms hook for mounts (one new compat call in `KitchenCompat`, plus a small Kingdoms class).

| Row | What it does |
|---|---|
| dm-delay | Reaching frostbite or heatstroke starts a 25-second count before the first hurt. The on-screen line already tells you it's coming. Downed players still take no hurt. |
| dm-heatslow | Heatstroke stops giving Nausea. Instead your steps slow the longer it lasts (up to about 15%), Weakness stays, and the screen edges shimmer with heat haze (ClimateHud's red edges). |
| dm-rebound | Once you're out of the cold or heat that was hurting you, body heat comes back toward comfortable twice as fast. |
| dm-fire | Being on fire pushes body heat up hard. |
| dm-mount | Riding under a rickshaw or carriage canopy keeps the sun off. Riding a warm-blooded beast in the cold warms you a little. |
| dm-clemency | A brand-new character gets a 10-minute grace the first time they wake, shown as a small status icon. This is on top of the temperate start, and it isn't renewed by later deaths or wake-ups. |
| hs-walls | A fire's warmth fills the room it's in and spreads through open air and doorways, never through stone. It uses the same room fill as the hearth (`HearthBlock`/`Warmth`). |
| hs-tags | Block tags list what gives off heat when lit (our bonfire, Kitchen stoves and ovens, Kingdoms forges, braziers, the steam boiler) and what chills the air near it (ice, packed ice, blue ice, snow). `heatOf` reads them. |
| hs-held | Holding a lit torch or lantern takes a little of the cold off. Holding ice or a snowball in the heat cools you a little. |
| hs-deep | Below about y -32 the rock grows warmer the deeper you go. Today caves settle at a flat 5. |
| hs-thinair | Height cold starts at the treeline (about y 120) instead of the sea. |
| bop-damp | Swamps, bogs and marshes count as damp air in `Warmth.damp`, through a biome tag. |
| bop-tags | A biome tag/data list sets how cold or hot a land feels when its own number is wrong (our Aether, Verdant World and Nether lands). |

## Track B: Seasons (Kitchen `season/`)

**Owns:** `Seasons`, `SeasonalWeather`, `SeasonalCrops`, the season mixins, `SeasonTints`, a new season sensor
block, and the seed tooltip.

| Row | What it does |
|---|---|
| ss-weather | Winter rarely thunders, spring and summer bring storms, autumn rains often. The world's weather timer follows the season. |
| ss-tropic | Jungles and savannas get a rainy season and a dry season instead of being always summer. Crops, thirst and fires change with them. |
| ss-melt | Snow and ice melt slowly in early spring and fast by summer, instead of one speed all year. |
| ss-sensor | A block that sends a redstone signal by season, for farms and greenhouses. |
| ss-tips | Hovering a seed or sapling shows which seasons it grows in. |

## Track C: Food, drink and house blocks (Kitchen)

**Owns:** the Warmth drink/food table, Kitchen dishes, the vintner's juices, `WaterskinItem`, butchery drops, and
new blocks (climate stove, weather gauge, rain barrel). It runs after Track A so it builds on A's `Warmth`.

| Row | What it does |
|---|---|
| fd-juice | A cup of the vintner's juice cools you for a minute, like a cold waterskin drink. |
| fd-ice | Ice cream: a Kitchen dish of milk, sugar and snow or ice that cools you for a few minutes in the heat. |
| fd-hot | Roast meat fresh off the spit, hot pies and hot drinks warm you for a while, not only stews and soups. |
| fd-bowl | Scoop still water into a wooden bowl and drink it (unclean unless boiled). The bowls stack a few high. |
| fd-rain | A barrel that fills with clean rainwater in the rain, for bottles and waterskins. Kingdoms builders put them beside houses. |
| fd-sinew | Butchering a carcass gives sinew that works as string for bows and sewing. |
| hs-regulator | A climate stove (our own block) that takes fuel to warm a room and ice to cool it. |
| hs-gauge | A wall gauge that sends a redstone signal as the room gets colder or hotter. |
| cl-skin | The dragon waterskin survives fire and lava when dropped, like netherite. |

## Track D: Clothes and charms (Kingdoms + Kitchen tags)

**Owns:** Kingdoms tailor/farmer/ranger trades and Outfits, the new clothes items, `Lining`/`Tailoring` (the
master lining), Kitchen `warm_clothing`/`cool_clothing` tags, the trims, and the charms.

| Row | What it does |
|---|---|
| cl-desert | Tailors in hot-land villages sell light robes and headscarves of linen or rabbit hide that keep the sun off. They give almost no armour. |
| cl-hats | Farmers and tailors sell bamboo, reed and palm-leaf hats as well as straw ones, each keeping the sun off. |
| cl-leaf | Forest rangers sell a cloak of woven leaves. It's cool in the heat and makes you harder to spot in woods (Senses). |
| cl-trims | A copper trim holds warmth and a quartz trim stays cool. Smithing trims are already gated behind Knowledge level 2. |
| cl-tempered | A master tailor can sew a dragon-hide lining that keeps you from going past chilly or warm. |
| cl-charm | A hot-stone pouch (warms) or a cold-clay flask (cools), worn in the belt slot and made by a herbalist. A rare charm does both. |

## Track E: Lands (Kingdoms world gen)

**Owns:** new biomes and features, the climate map placement for them, and Mohknee's land gen.

| Row | What it does |
|---|---|
| bop-springs | **On Mohknee** (Andy's note): a rare cold-mountain valley full of steaming pools on calcite terraces, warm enough to live by in winter. |
| bop-volcanic | **On Mohknee** (Andy's note): black-sand lands with lava vents and ash. They're very hot, with harsh-land foes and rich ore. Mohknee's iron sand already ties in. |
| bop-salt | White salt pans in the home continent's dry hot belt. The salt cures meat so it keeps, and salty food makes you thirsty. |
| bop-steppe | Dry, cold grasslands where the days are mild and the nights are bitter. |

## Track F: Villages and people (Kingdoms)

**Owns:** villager jobs and approaches, guard and villager climate behaviour.

| Row | What it does |
|---|---|
| vl-weather | Some villagers know the weather. They sell thermometers, waterskins and climate gear, and tell you what season is coming. |
| vl-notice | A villager who sees you shivering offers a seat by their fire or a blanket. A guard tells you to get out of the noon sun. Bubbles only, no chat. |
| vl-npcs | Villagers and guards gather round fires on winter nights, seek shade at desert noon and wear more in the cold. |

---

## Order

1. A and E together (Kitchen climate and Kingdoms world gen don't share files).
2. B and D.
3. C and F.

Each track gets an independent verifier agent, which checks the rows line by line against the code and the build.
The fix loop stops after two rounds with no progress.

## Not checked

No dev client runs (CLAUDE.md: build to check). Everything is built but not played until Andy tests the merge.
