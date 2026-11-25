# Fae Woods plan: Spore & Stem and eleven more mods

Andy's picks, 2026-11-25 (pick list https://claude.ai/artifact/8MNDHjgDZ2Tg93bfsQsbG4, db `picks/andy`): 37 rows from
12 mods, plus four notes that ask for more (cars, Rim, God Wand, Unbound). The licence of each source is in
`outside-art-licences` memory and on the pick list. Lore: "The glow-woods" in Lore.md, approved 2026-11-25.

Rules that bind every patch: the living-things rules (sympathy, feedback loops, stewardship), no new dimensions or portals,
lore only in talk and books, our own names, optional mods only through a compat class, and no new original mob models
(ported mobs from Apache/MIT/CC0 mods are fine, they're the source's art).

Porting from Spore & Stem (Apache-2.0, code and art): copy the 26.3 workspace's textures, models, geo/anim and sounds
(`26.3/common/src/generated`) with `licenses/spore-and-stem/LICENSE` + `NOTICE`, and rewrite the Java in our style. Their
ids are renamed into our namespaces (Kingdoms for the woods and creatures, Kitchen for food). From Seasonal (MIT), Cars
(MIT), God Wand (MIT code only) and Rim (CC0 code/art): same, keeping each licence file.

## Patch 1: Glow-woods round Sylvan havens (Kingdoms)
Rows: ss_glowwood, ss_capwood, ss_rings, ss_sparkle, bb_skeps.
- Giant glowcap, bouncecap and inkcap features, and their three fungal wood sets (planks to doors, glowcap lantern).
- `HavenBuildings` Sylvan style places giants round the tree platforms; `HavenGrowth` adds caps as the haven grows;
  `HavenWares` sells the wood and honey/wax. Bouncecaps under platforms cancel fall damage.
- Fairy rings: worldgen feature (plains/forest, one in 48 chunks) and one on every Sylvan green; Powers' `Fairies`
  weights pink fairy spawns toward a ring.
- Sparkles: client only, `MagicFx` wisp/mote + `SeasonAura` fireflies near Sylvan havens at night. No new art.
- Skeps: straw skeps with vanilla bees in the Sylvan orchard.
- Feedback: caps only spread on shaded ground; a haven that's emptied or burned loses its glow within a season.

## Patch 2: Glow-wood creatures, the rot and the Myceliarch (Kingdoms + Powers medicine)
Rows: ss_critters, ss_mimic, ss_mycosis, ss_myceliarch.
- Sporeling, Truffler, Glowmoth (haven woods), Mimicap, Bloatshroom (old dark woods only), Shambler, Myceliarch: their
  models and animations ported. GeckoLib only if the 26.3 port uses it; else their generated geo/anim classes.
- The rot: a Powers affliction (`medicine`), cured by forager's stew, the antifungal tonic, milk, Restoration magic.
  The dead it takes rise as shamblers, never near a haven. Circle of Spores druids are immune.
- Myceliarch: Fruiting Heart summon, telegraphed moves, second bloom; BossRise bar and a short entry film.
- Stewardship: sporelings stay with a haven that's looked after; tamed ones shed mushrooms.

## Patch 3: Forager's kitchen and bees (Kitchen)
Rows: ss_foraging, ss_dishes, ps_food, bb_hive, bb_jar.
- Nine mushrooms, cultivation logs (four stages, shade fast, open sky slow), mycelial soil, foraging knife.
- Seasons: morels spring, chanterelles late summer, the big flush in autumn (`SeasonalCrops` tags).
- Dishes as `Dish`/`Step` entries; Psithurism's rice/soy/fish in `OptionalItems` when loaded.
- Harvest hive (vanilla bees harvest, replant and carry home; slower without flowers) and the bee jar.

## Patch 4: The wicked harvest (Kitchen + Kingdoms)
Rows: wh_farmhand, wh_crows, wh_cauldron, wh_lich.
- Crows land on open fields by day and peck crops back a stage; a scarecrow guards 16 blocks.
- Rotten pumpkins; a witch's cauldron over soul fire that brews only at night (Alchemy recipe).
- Harvest Lich (ported MIT model) raised at a ruined barn's altar only in late autumn; beating it wakes scarecrows.
- Woken scarecrows harvest, replant and carry to a chosen chest; they need mending with straw to keep working.

## Patch 5: Shipped mods for the Blossom land, balloons and mounts (build + Kingdoms + Powers)
Rows: ps_ship, ps_build, ps_onsen, hab_ship, hab_wind, fm_ship, fm_sylvan, fm_levels.
- packMods: Psithurism + MModding Library; Hot Air Balloons (credit + CurseForge link); Fantasy Mounts (only after
  the author agrees; its update check turned off).
- `PsithurismCompat`: Blossom houses use shoji, tatami and dark cherry when loaded. Onsen pools near Blossom villages;
  snow monkeys bathe in them in winter; a soak lifts winter cold.
- Our `HotAirBalloon`: wind layers by height and gusts, used by airships and ferries too.
- `FantasyMountsCompat`: stag, treant, luna moth and jackalope near Sylvan havens; wardens ride stags.
  Mount levels unlocked by our horse bond.

## Patch 6: Spells that leave the land whole (Powers)
Rows: gw_return, gw_path, gw_hold. Andy's note on gw_ship: "can we make powerful mage wands and spells from it?"
- Terrain-return engine (God Wand MIT code) for Gravity, meteors and big blasts, with a server switch.
- The wood lets a Sylvan through (wood and leaves only), as a Sylvan or Druid move.
- Hold, wait, release for ultimates.
- Mage wands: open question for Andy (see below).

## Patch 7: Carts and quest books (Kitchen + Kingdoms)
Rows: car_physics, rpg_quests.
- Cart physics from the Cars mod's MIT code for `CartEntity`.
- Errand books that point back to the giver; after many, a rare one to a hidden ruin.

## Patch 8: The future's war machines and the deep (Powers future + Kingdoms)
Rows: car_ship (note: "only 4 cars and only in the future"), rim_ship (note below), rim_tide, rim_parts, rim_carcass.
- Four cars from the Cars mod (MIT), found only in the future's ruins, fuelled from the future's stores.
- Rim (note): "larger rigs made by the frostborn and tinkerer, and they only appear in the future to fight the hordes
  and mutants. there can also be sea monsters that come from the abyssal cave and are able to be commanded when dead
  by darktides". Rim's code and rigs ported (CC0), renamed and repainted so nothing carries Pacific Rim's names or
  designs; giant rigs in the future against `FutureFerals` hordes; deep beasts rise from the Abyssal Chasm on a
  rising schedule with `AlarmBells`; Darktide can raise and command a dead one. Per-part hits and carcass butchering
  for every giant.

## Patch 9: Pod travel the Unbound way (Powers)
Pick list: https://claude.ai/artifact/MpUPQ1WdceWLakcUPSZ5fP (25 rows, db `picks/andy`).
Andy's note on ub_ship: "create a new list of how it would integrate our worlds with its travel system. and only use
what we have. it would replace how our space pods work only." Its own pick list; built after he picks.

## Order
Two tracks at a time: 1 + 3, then 2 + 4, then 5 + 7, then 6 + 8, then 9. One PR per patch, a change note per mod.

## Andy's answers (2026-11-25)
1. Mage wands: "They should learn spells and cast from the wand": a caster's learned spells are cast through a wand.
2. Rim's sea beasts keep Rim's body shapes, repainted and renamed.
3. Fantasy Mounts ships only once its author agrees.
Lore "The glow-woods" approved.
