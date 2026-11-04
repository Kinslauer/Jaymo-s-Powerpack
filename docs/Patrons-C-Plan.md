# Patch 11c: Patron gods, part (c) (temples, champions, the patrons' weapons)

Kingdoms **0.7.518** (`kingdoms/gradle.properties`), Powers **0.20.18** (`gradle.properties`: the eight patron weapons
and their heads). Kitchen unchanged (0.5.57). Tag new code comments `(0.7.518)` and `(0.20.18)`.

Part (a) is merged (`docs/Patrons-Plan.md`, Kingdoms 0.7.509). Part (b) is on the branch this worktree is based on
(`docs/Patrons-B-Plan.md`, Kingdoms 0.7.513). This is part (c), the last part.

Andy's plan text: "Champions. Each patron has a champion, a multi-phase boss fought at that patron's temple. Temples are
new rare structures in the Overworld, Nether and Aether, only from world tier Wildlands up. A champion drops that
patron's weapon (forged-style weapons with one ability each), and repeat kills are guaranteed to drop it by the third
try."

Andy's rulings that shape this:
- Champions are group fights. "Only the strongest can fight alone": health, damage and phases scale with the players
  near, and the solo base is very hard (a solo kill needs top gear, Valor and skills, and good play).
- Eight patrons, so eight champions. The Truth's temple is the **great temple** in the capital of a Faith-creed realm (a
  building its builders put up, as Kingdoms builds everything), or a rare **ruined cathedral**. The others' temples go
  where their domain fits (section 4).
- Part (b)'s mark: the old patron's champion hunts you **once**, after the trials. This is a weaker hunting form, not the
  full temple fight (section 6).
- Champions use the existing boss machinery: `BossBars`, `BossPosture`, boss bars, the great-foe credit. Their attacks are
  real and themed to the patron, with telegraphs so good play can dodge them.
- Weapons are forged-style. Kingdoms drops an iron head, and you put it together at the Tool Station like the kings'
  weapons, so smithing grades and tooltips apply. Each has one active or on-hit ability. Anyone can use one, but it is
  stronger for that patron's sworn.
- Our own art: Pillow scripts. The champions are humanoid models (the `HornkinModel` pattern) with our own textures.
- No slash commands for players. Op test commands are fine. No discovery chat: speech bubbles, overlays, sounds and boss
  bars only.

### The pack facts Andy asked about (checked in this worktree)
- **The Aether is in the pack.** It is Powers' own dimension, `jaymospowers:aether`
  (`src/main/resources/data/jaymospowers/dimension/aether.json`), reached through Wind. It is not an Aether mod jar.
  Kingdoms already names it (`GolemLairs.WORLDS`, `GolemLairs.java:72-73`). So the Crowned Sun gets Aether temples,
  placed at runtime by Kingdoms (section 4). Powers' worldgen (`tools/gen_aether_structures.py`) is **not** touched.
  Worldgen JSON cannot read a world tier.
- **World tiers** (`WorldTiers.Tier`: HEARTH 0, BORDERLANDS 1, **WILDLANDS 2**, DREAD 3, LEGEND 4). A player's current tier
  is `WorldTiers.of(player)`, and the tier scaling arrays are `WorldTiers.HEALTH` / `DAMAGE` (`WorldTiers.java:82-83`).
  Every structure in the pack that a tier can gate is placed at runtime near players (`NetherForts.tryFound`,
  `QuestFoes`, `GrimSites`). Temples do the same.
- **Knowledge levels** are the five fixed bosses (`Knowledge.bitFor`, `Knowledge.java:138-152`). A champion is not one
  of them and gets no knowledge bit. That hook does not fit, and none is added.
- **Great foes**: `BossBars.boss` feeds the boss posture (`BossPosture.applies:101`), the world-tier credit
  (`WorldTiers.died:583`: +1 foe for everyone within 64), Valor's great kill (`Valor.java:766`), Will's immunity
  (`Will.java:251/280`), QuietOne, and Powers' `KingdomsCompat.boss` (powers treat it as a boss). A champion goes in
  `BossBars.boss`, so it gets all of these (open question 1).

---

## 1. Files

### Kingdoms, new (main, `kingdoms/src/main/java/com/jaymoskingdoms/`, flat package like the rest)
| File | What |
|---|---|
| `PatronChampion.java` | The champion entity (`extends Monster`, one class, eight entity types). Asleep at its altar as a statue. It wakes for a Wildlands player. Group and tier scaling, phases and phase turns, the move scheduler, the leash, the reset and rest heal, the gap-closing blink, its boss bar, the players who hit it, its save data, entity events for the client. The temple form and the hunting form. |
| `PatronChampions.java` | The eight entity types (attributes, spawn eggs), the hunt (section 6), what a fallen champion leaves (heads with the third-try guarantee, deeds, the temple's slain day), the `TRIES` attachment, the adds' tag and cleanup, and op commands `/kingdom champion ...`. |
| `Temples.java` | The `Temple` record and the `TEMPLES` attachment (on the Overworld). Temples rise near Wildlands players (out of sight, rare). Champions re-form after a kill. A patron is learned when you come near its temple. The great temple's hooks (`greatTempleFor`, `greatBuilt`). Op commands `/kingdom temple ...`. |
| `TempleSites.java` | Finding a site for each patron's temple: surface, deep stone, sea, high ground, crypt, hidden hollow, Nether, Aether. Biome tags, sight, spacing, level ground. |
| `TempleBuildings.java` | Draws the seven wild temples and the ruined cathedral in the world at once (a `Frame` helper like `nether/FortBuildings`). Returns a `Layout(center, altar, championSpot, facing)`. |
| `GreatTemple.java` | The Truth's great temple as a `Blueprint` (`Buildings.Sketch`, the realm's `Buildings.Kit`/`Palette`). Also the nave plan shared with the ruined cathedral. |

### Kingdoms, new (main, `kingdoms/src/main/java/com/jaymoskingdoms/champions/`)
| File | What |
|---|---|
| `Move.java` | `public record Move(String id, int windup, int cooldown, double minRange, double maxRange, Tell tell, Act act)` and its two small interfaces. |
| `Moves.java` | `public interface Moves`: the three moves (A, B, C), the group phase's `hand`, lingering effects (`tick`), damage taken (`hurt`), `clear`. `static Moves create(Patron)` gives a fresh set for each champion, because the moves keep their own lingering state. |
| `MoveKit.java` | Shared helpers: the foe test, telegraph rings and columns in the patron's colour, cone, line and area hits, ground markers, safe blink spots, and safe pushes. |
| `HearthWardenMoves.java`, `ForgeThaneMoves.java`, `TideBrideMoves.java`, `AntleredHunterMoves.java`, `GraveLampMoves.java`, `SunMarshalMoves.java`, `CinderShadeMoves.java`, `InquisitorMoves.java` | One patron each (section 5.4). |

### Kingdoms, new (client, `kingdoms/src/client/java/com/jaymoskingdoms/client/champions/`)
| File | What |
|---|---|
| `ChampionModel.java` | `extends HumanoidModel<ChampionRenderer.State>`, the `HornkinModel` pattern: `HumanoidModel.createMesh` plus each patron's extra parts (section 9.2). 64x64 texture. Asleep, cast and turn poses. |
| `ChampionRenderer.java` | `extends HumanoidMobRenderer<PatronChampion, State, ChampionModel>` (the `HornkinRenderer` pattern), one for each type, with that type's scale and texture. `register()` registers all eight. The held weapon draws through the stock item-in-hand layer. |

### Kingdoms, new (resources)
- `assets/jaymoskingdoms/textures/entity/champion/<id>.png` x8, `textures/item/<id>_spawn_egg.png` x8,
  `models/item/<id>_spawn_egg.json` x8, `items/<id>_spawn_egg.json` x8 (written by `tools/gen_champions.py`).
- `data/jaymoskingdoms/tags/worldgen/biome/temple/<rule>.json` (section 4.2).

### Kingdoms, changed
| File | Change (0.7.518) |
|---|---|
| `JaymosKingdoms.java` | `PatronChampions.register(); Temples.register();` right after `RealmFaiths.register();` (line 504). |
| `BossBars.java` | `boss(...)`: add `\|\| entity instanceof PatronChampion champion && !champion.decoy()`. Class doc: "(0.7.518) a patron's champion". |
| `BossPosture.java` | `wear(ServerLevel, LivingEntity, float)` goes from `private` to package-private (`PatronChampion.wearPosture` calls it). Nothing else. |
| `WorldTiers.java` | `hostile(Mob)`: first line `if (mob instanceof PatronChampion \|\| PatronChampions.isAdd(mob)) { return false; }`. Champions scale themselves, so no double scaling and no `MonsterGear`. |
| `Patrons.java` | `blessing(...)`: the `patron_weapon/<key>` branch (section 7.4). New constant `static final String WEAPON_KEY = "patron_weapon/";`. |
| `Spoils.java` | `powers(String id)` goes from `private` to package-private. |
| `Buildings.java` | `register()`: `realm(FactionType.KINGDOM, Temples.GREAT_TEMPLE, GreatTemple::draw, 1, Temples.GREAT_TEMPLE_FROM);` right after the tavern entry. |
| `Planner.java` | `eligible`: `if (e.id().equals(Temples.GREAT_TEMPLE) && !Temples.greatTempleFor(server, f)) continue;` next to the haven ring's skip. `utilitiesDone`: the same exclusion next to the ring's. Without it, a kingdom of another creed would never count as "utilities done" and would never build its wall. |
| `Gatherers.java` | `built(...)`: `if (entry.equals(Temples.GREAT_TEMPLE)) { Temples.greatBuilt(level, f, min, max); }`. |
| `client/JaymosKingdomsClient.java` | `com.jaymoskingdoms.client.champions.ChampionRenderer.register();` beside `HornkinRenderer.register()`. |
| `client/StoryHoldScreen.java` | `HINTS = 92`. |
| `assets/jaymoskingdoms/lang/en_us.json` | Section 10.1. |
| `kingdoms/MODLOG.md`, `kingdoms/gradle.properties` (`version=0.7.518`) | Section 11. |

Not changed: `Turning.java` (the hunt reads `Patrons.hunters` and `Turning.trial`; the mark is flipped by the existing
`Patrons.hunted`), `Shrine.java` (temples set `Shrine.DEDICATION` on a plain `Healing.SHRINE` block), `QuestFoes`,
`Knowledge`. No mixin, so the mixin JSONs are unchanged.

### Powers, new (`src/main/java/com/jaymospowers/armory/`)
| File | What |
|---|---|
| `PatronWeapons.java` | The eight weapons' `Kind` enum (item id, head id, patron key), the tuning, item properties, `sworn(...)` (through `KingdomsCompat.blessing`), `init()`. |
| `PatronWeaponItem.java` | `extends Item`: `postHurtEnemy` for the on-hit abilities, `use` for the active ones, `appendHoverText`. |
| `PatronWeaponHits.java` | Timed states (Sunder stacks, Marked Prey, Smokestep's window, Sun's Judgement wind-up, Hearthguard's gap), their server tick, and the guarded extra-damage helper. |

### Powers, changed
| File | Change (0.20.18) |
|---|---|
| `armory/Armory.java` | Eight `item(...)` lines after the kings' weapons (line 197). `PatronWeapons.init()` beside `RapierItem.init()`. |
| `tinkering/WeaponParts.java` | `PATRONS` set, eight `Kind`s, `king()` also true for them, `FOUND` includes them (section 7.1). |
| `compat/KingdomsCompat.java` | `blessing(Player, String)` by reflection (section 7.4). |
| `client/java/.../jei/TinkeringJei.java` | The same info loop as the kings' heads, for `WeaponParts.PATRONS` (`jei.jaymospowers.patron_head.<part>`). |
| `assets/jaymospowers/lang/parts/patron_weapons.json` (new) | Section 10.2. The build merges it with the duplicate-key check. |
| `data/jaymospowers/weapon_attributes/<id>.json` x8 (new) | Better Combat parents (section 7.2). |
| `data/minecraft/tags/item/swords.json` | The eight weapon ids. |
| `items/`, `models/item/armory/`, `textures/item/armory/`, head models and textures | Written by `tools/patron_weapons_assets.py` and `tools/weapon_parts_assets.py` (section 9.1). |
| `gradle.properties` (`version=0.20.18`), root `MODLOG.md` | Section 11. |

### Tools (root `tools/`)
| File | What |
|---|---|
| `tools/weapon_parts_assets.py` (changed) | Eight PARTS rows (iron only) and eight `DRAWN[...]` 16x16 heads, the way the kings' heads were added. |
| `tools/patron_weapons_assets.py` (new) | The eight 32x32 weapon sprites, their models and item JSON. Imports `weapon_parts_assets` as `king_spoils_assets.py` does. |
| `tools/gen_champions.py` (new) | The eight 64x64 champion skins and the eight spawn egg icons (Kingdoms). |

---

## 2. Names (no clash with the quest champion)

"Champion" already means the quest champion in code and lang (`QuestFoes.champion`, `Quest.CHAMPION`,
`QuestBoard.champion`, `quest.jaymoskingdoms.champion_slain`). So the classes are `PatronChampion` / `PatronChampions`,
the lang keys use `patron_champion`, and each entity type has its own id:

| Patron | Entity id (`jaymoskingdoms:`) | Name | Size (w x h) | Render scale | Fire immune | Boss bar colour |
|---|---|---|---|---|---|---|
| HEARTHKEEPER | `hearth_warden` | The Hearth-Warden | 0.9 x 2.4 | 1.30 | yes | RED |
| ANVIL_FATHER | `forge_thane` | The Forge-Thane | 1.1 x 2.2 | 1.25 (wide 1.15 x) | yes | WHITE |
| DEEP_MOTHER | `tide_bride` | The Tide-Bride | 0.8 x 2.6 | 1.40 | no | BLUE |
| GREEN_HUNTSMAN | `antlered_hunter` | The Antlered Hunter | 0.8 x 2.8 | 1.45 | no | GREEN |
| LANTERN_WARDEN | `grave_lamp` | The Grave-Lamp | 0.8 x 2.6 | 1.40 | no | PURPLE |
| CROWNED_SUN | `sun_marshal` | The Sun-Marshal | 0.9 x 2.7 | 1.45 | yes | YELLOW |
| ASH_WHISPERER | `cinder_shade` | The Cinder-Shade | 0.7 x 2.2 | 1.20 | yes | PINK |
| TRUTH | `inquisitor` | The Inquisitor | 0.8 x 2.6 | 1.40 | no | WHITE |

(The bar overlay is `BossEvent.BossBarOverlay.PROGRESS`. The colours are checked against `BossEvent.BossBarColor`.)

---

## 3. Temples (`Temples.java`)

### 3.1 Record and attachment
```java
/** (0.7.518) A patron's temple: where, whose, which kind, its altar and champion, and when its champion last fell. */
public record Temple(int id, String patron, String world, BlockPos center, BlockPos altar, Direction facing, int kind, int faction,
	long slainDay, Optional<UUID> champion) { ... CODEC (RecordCodecBuilder, every field; world is the dimension id string) }
public static final int WILD = 0, RUIN = 1, GREAT = 2;
/** Every temple there's been, in every world, on the Overworld (Factions.level(server)). */
static final AttachmentType<List<Temple>> TEMPLES = AttachmentRegistry.create(JaymosKingdoms.id("patron_temples"),
	b -> b.persistent(Temple.CODEC.listOf()));
public static final String GREAT_TEMPLE = "great_temple";
```
`with...` copies for `slainDay` and `champion`. Helpers `all(server)`, `put(server, temple)` (replace by id, `List.copyOf`),
`byId`, `world(server, temple)` (`server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(world)))`),
`nearest(server, level, pos, range)`.

### 3.2 Tuning (`static final` at the top of `Temples`)
```java
/** How often each player's surroundings are looked at for a new temple (ticks), and the chance one rises then. */
static final int RISE_EVERY = 20 * 60 * 3;
static final float RISE_CHANCE = 0.12F;
/** The player's tier (an ordinal) from which temples rise near them: Wildlands. */
static final int RISE_TIER = 2;
/** No new temple while any is this near the player; one patron's temples, in one world, keep this far apart (blocks). */
static final double TEMPLE_AREA = 800.0, SAME_SPACING = 2000.0;
/** A temple rises this far from the player, out of every player's sight (none within HIDE_RANGE). */
static final int SITE_MIN = 72, SITE_MAX = 144, SITE_TRIES = 10;
static final double HIDE_RANGE = 48.0;
/** The Truth's ruined cathedral is rarer than the others: its weight when a patron is drawn for a site. */
static final float RUIN_WEIGHT = 0.35F;
/** Shrine days after a kill before the champion stands at its altar again. */
static final int RETURN_DAYS = 3;
/** How often loaded temples are tended (ticks): re-forming, and players coming near. */
static final int TEND_EVERY = 100;
/** A player this near an altar meets its patron (and is shown the temple's name once until they leave LEAVE_RANGE). */
static final double LEARN_RANGE = 16.0, LEAVE_RANGE = 48.0;
/** The great temple: a Faith-creed kingdom's capital builds it from this many townsfolk. */
static final int GREAT_TEMPLE_FROM = 30;
```

### 3.3 Rising (`tick`, `END_SERVER_TICK`, `server.getTickCount() % RISE_EVERY == 0`)
For each player who is not a spectator or in creative, is in the Overworld, the Nether or the Aether, and has
`WorldTiers.of(p).ordinal() >= RISE_TIER`, with chance `RISE_CHANCE`:
1. Skip if any temple in the player's world is within `TEMPLE_AREA`.
2. Up to `SITE_TRIES` times, take a spot `SITE_MIN..SITE_MAX` blocks away in a random direction. List the patrons whose
   rules (section 4) fit that spot's world, biome and depth, skipping any patron with a temple of its own within
   `SAME_SPACING` in that world. Pick one by weight (1 each, The Truth's ruin `RUIN_WEIGHT`). Then ask
   `TempleSites.find(level, patron, spot, player.blockPosition(), false)` for a site.
3. On a site: `TempleBuildings.build(level, patron, site, random)` gives the `Layout`. Then the `Temple` is stored (id =
   max + 1; kind `WILD`, or `RUIN` for The Truth; faction -1). `PatronChampions.raise(level, temple, layout)` sets its
   champion down asleep at `championSpot`, facing the way out. Log at INFO:
   `"{}'s temple rose at {} in {}"`. No chat.

### 3.4 Tending (`% TEND_EVERY`, for each temple whose world exists and whose altar `isLoaded`)
- **Re-forming**: if `slainDay >= 0 && today >= slainDay + RETURN_DAYS`, and no `PatronChampion` with this temple's id
  is within 32 blocks of the altar, a new one is raised asleep and `slainDay` is set to -1. If the record's champion UUID
  has gone missing some other way (an op `/kill`, a removed entity) and `slainDay < 0`, it is raised again at once.
- **Meeting the patron**: for each player within `LEARN_RANGE` of the altar who is not in the `NEAR` set
  (`Map<Integer, Set<UUID>>`, not saved): `Patrons.met(sp, patron, altar)` (learned, and its book left on the altar the
  first time). Then overlay `message.jaymoskingdoms.temple.near` in the patron's colour, and add them to `NEAR`. They
  leave `NEAR` beyond `LEAVE_RANGE`.

### 3.5 The great temple (The Truth)
- `static boolean greatTempleFor(MinecraftServer server, Faction f)`: `f.type() == FactionType.KINGDOM && f.overlord() < 0
  && !f.fallen() && GreatWars.creed(server, f) == GreatWars.Creed.FAITH`. So only the capital of a Faith-creed realm builds
  one.
- `static void greatBuilt(ServerLevel level, Faction f, BlockPos min, BlockPos max)` (from `Gatherers.built`): find the
  shrine block in the box (`Healing.SHRINE` with `DEDICATION == TRUTH`). That is the altar. Store a `Temple` of kind
  `GREAT` (faction `f.id()`) and raise the Inquisitor asleep two blocks before the altar, facing the doors. If no altar is
  found, log a WARN and store nothing.
- The building is the realm's. If the creed later changes or the realm falls, the temple and its champion stay.
- The temple stands from the start, but its Inquisitor wakes only for a Wildlands player (section 5.2).

### 3.6 Op test commands (registered in `Temples.register()`, `Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)`, under `/kingdom temple`)
- `raise <patron>`: builds that patron's temple ahead of you (`TempleSites.find(..., anywhere = true)`: it ignores
  sight, spacing, biome and tier, but still needs fit ground), with its champion asleep. Prints the id and position.
- `list`: every temple: id, patron, world, centre, kind, champion (asleep, awake, gone), slain day.
- `reform`: the nearest temple's champion stands again now.
`<patron>` is a `StringArgumentType.word()` with suggestions from the `Patron` keys. Feedback is `Component.literal`.

---

## 4. Where each temple rises (`TempleSites.java`)

### 4.1 Rules
`static @Nullable BlockPos find(ServerLevel level, Patron p, BlockPos near, BlockPos playerPos, boolean anywhere)` uses
the patron's rule for `level`'s dimension and returns the temple's floor centre, or null. Every rule checks:
- The square (`TempleBuildings.RADIUS[p]` on each side) is loaded (the four corners `isLoaded`).
- No player is within `HIDE_RANGE` (unless `anywhere`).
- No temple is within `SAME_SPACING / 4` (500 blocks), even of another patron.
- No fluid inside the box, except where the rule wants water (the Deep Mother).
- `Factions.at(server, level, pos) == null`: never on a realm's or camp's land (the great temple is the only one in a town).

A search ring like `NetherForts.nearSite` (rings out to `SEARCH_RADIUS = 32` in steps of `SEARCH_STEP = 4`).
`ground(level, x, z, top, bottom, headroom)` is the highest solid block with `headroom` clear above it (the
`NetherForts.ground` logic, written again here because that one is private). "Level enough" is
`MAX_SPREAD = 4` across nine probe points. These are the same constants as NetherForts.

| Patron | World and place | Biome tag (`#jaymoskingdoms:temple/...`) | Height rule |
|---|---|---|---|
| Hearthkeeper | Overworld surface: a hearth-hall in open, settled-looking country | `hearthkeeper` | `heightmap MOTION_BLOCKING_NO_LEAVES`, level ground |
| Anvil-Father | Overworld **deep**: carved into solid stone, only when the player is below Y 0. Or the **Nether** surface | `anvil_father` (Overworld: any, read at the cave floor), `anvil_father_nether` | Deep: floor Y in `[player.y - 8, player.y + 8]` clamped to `[DEEP_LOW -52, DEEP_HIGH -6]`, at least `SOLID_SHARE 0.85` of 27 samples in the hall's box solid stone or deepslate, no fluid within 2 of it. Nether: ground between 33 and 116 |
| Deep Mother | Overworld **by or under the sea**: a court on pillars standing just out of the water | `deep_mother` | sea floor (`OCEAN_FLOOR` heightmap) between `seaLevel - SEA_DEEPEST 14` and `seaLevel - 2`; the court is built at `seaLevel + 1` |
| Green Huntsman | Overworld **deep forest**: a glade among trees | `green_huntsman` | surface, and at least `FOREST_LOGS 10` log blocks among 48 samples within 24 blocks (a real forest) |
| Lantern Warden | Overworld: a graveyard with a **crypt** under it. Or the **Nether**: the same vault open on the valley floor | `lantern_warden`, `lantern_warden_nether` | surface level ground. The crypt floor is `CRYPT_DEPTH 10` below it, needing solid ground down there |
| Crowned Sun | Overworld **high ground**. Or the **Aether**: a sun-court on an island | `crowned_sun`, `#jaymospowers:is_aether` (Powers' tag) | Overworld: surface Y ≥ `HIGH_GROUND 110`. Aether: surface, with all nine probes on ground (no void under the court) |
| Ash Whisperer | **Nether**: ash-choked deltas. Or Overworld **hidden**: a hollow under dry or dark country, reached by a narrow shaft | `ash_whisperer_nether`, `ash_whisperer` | Nether: ground 33..116. Hidden: floor Y in `[HIDDEN_LOW 4, HIDDEN_HIGH 40]` and at least 12 below the surface, `SOLID_SHARE` solid, no fluid |
| The Truth | Overworld: a rare **ruined cathedral** in open country (the great temple is built, section 3.5) | `truth` | surface level ground |

### 4.2 Biome tags (new, `kingdoms/src/main/resources/data/jaymoskingdoms/tags/worldgen/biome/temple/`)
Data, so the biomes can be tuned without code. Use `"required": false` objects for anything not vanilla.
- `hearthkeeper.json`: `minecraft:plains`, `sunflower_plains`, `meadow`, `snowy_plains`, `taiga`, `snowy_taiga`, `cherry_grove`.
- `anvil_father.json`: `#minecraft:is_overworld` (any; the deep rule decides).
- `anvil_father_nether.json`: `minecraft:nether_wastes`.
- `deep_mother.json`: `#minecraft:is_ocean`, `#minecraft:is_beach`.
- `green_huntsman.json`: `#minecraft:is_forest`, `#minecraft:is_taiga`, `#minecraft:is_jungle`, `minecraft:dark_forest`.
- `lantern_warden.json`: `plains`, `forest`, `birch_forest`, `dark_forest`, `taiga`, `swamp`, `snowy_plains`, `old_growth_birch_forest`.
- `lantern_warden_nether.json`: `minecraft:soul_sand_valley`.
- `crowned_sun.json`: `meadow`, `grove`, `windswept_hills`, `windswept_gravelly_hills`, `stony_peaks`, `snowy_slopes`, `jagged_peaks`.
- `ash_whisperer.json`: `badlands`, `wooded_badlands`, `eroded_badlands`, `savanna`, `savanna_plateau`, `desert`, `dark_forest`.
- `ash_whisperer_nether.json`: `minecraft:basalt_deltas`.
- `truth.json`: `plains`, `meadow`, `forest`, `birch_forest`, `taiga`, `savanna`.
(Check every vanilla tag and biome id against the 26.3 `BiomeTags` and `Biomes` while building.)

---

## 5. Champions (`PatronChampion.java`, `PatronChampions.java`, `champions/`)

### 5.1 Registration (`PatronChampions.register()`)
For each `Patron`, the `SkeletonLord.register()` pattern (`SkeletonLord.java:99-107`):
`Registry.register(BuiltInRegistries.ENTITY_TYPE, key, EntityType.Builder.<PatronChampion>of((t, l) -> new PatronChampion(t, l), MobCategory.MONSTER).sized(w, h).clientTrackingRange(10)` (plus `.fireImmune()` per section 2) `.build(key))`,
`FabricDefaultAttributeRegistry.register(type, PatronChampion.createAttributes())`, a spawn egg
`<id>_spawn_egg` (`SpawnEggItem` with `.spawnEgg(type)`) in `CreativeModeTabs.SPAWN_EGGS`.
`static final Map<Patron, EntityType<PatronChampion>> TYPES` (an `EnumMap`) and `static Patron patronOf(EntityType<?>)`.
The entity reads its patron from its type, so nothing about the patron is saved.

`register()` also registers `AFTER_DEATH` → `died`, `END_SERVER_TICK` → `tick` (hunt), `DISCONNECT` → `hunterGone`,
`SERVER_STOPPED` → clear maps, the op commands, and `LOGGER.debug("champion attachment: {}", TRIES)` so the attachment
class loads at init (the `Healing` pattern).

### 5.2 The entity (`PatronChampion extends Monster`)
**Tuning** (`static final`, top of the class):
```java
/** The solo base at Hearth (the tier and the group raise it): health, blow, armour, toughness, speed, knockback resistance, follow range. */
static final double HEALTH = 600.0, DAMAGE = 14.0, ARMOR = 16.0, TOUGHNESS = 8.0, SPEED = 0.30, KNOCKBACK_RESISTANCE = 0.9, FOLLOW = 40.0;
/** Each counted player past the first adds this share of the base health and of the blow; at most GROUP_MOST count. */
static final double HEALTH_PER_PLAYER = 0.6, DAMAGE_PER_PLAYER = 0.08;
static final int GROUP_MOST = 6;
/** Players this near (alive, not spectating, not creative, not downed) count, rechecked this often; the count only grows in a fight. */
static final double FIGHT_RANGE = 32.0;
static final int RECOUNT_TICKS = 100;
/** From this many players a fourth phase opens: the patron's hand (adds, or the Cinder-Shade's doubles). */
static final int FOURTH_PHASE_FROM = 3;
/** Where the phases turn (shares of max health): three phases alone or in pairs, four in a group; the hunting form has two. */
static final float[] TURNS_3 = {0.66F, 0.33F}, TURNS_4 = {0.75F, 0.5F, 0.25F}, TURNS_HUNT = {0.5F};
/** A phase turn: this long untouchable (roaring), then a shockwave: its reach, harm (a share of the blow) and push. */
static final int TURN_TICKS = 40;
static final double TURN_REACH = 7.0, TURN_PUSH = 1.6;
static final float TURN_SHARE = 0.4F;
/** The last phase: move cooldowns times this, and this much faster. */
static final float LAST_COOLDOWN = 0.7F;
static final double LAST_SPEED = 0.15;
/** The least gap between two moves (ticks). */
static final int MOVE_GAP = 50;
/** Temple form: how far from its altar it will chase (the great temple: GREAT_HOME), and its rest heal (share of max health a second) once nobody is in FIGHT_RANGE. */
static final double HOME_RADIUS = 24.0, GREAT_HOME = 12.0;
static final float REST_HEAL = 0.03F;
/** With no counted player for this long it goes back to its altar, whole, and falls asleep again. */
static final int RESET_TICKS = 20 * 30;
/** No blow landed on its target and no path for this long: it closes the gap (a blink beside the target). */
static final int STUCK_TICKS = 100;
/** A sleeping temple champion wakes for a player of at least this tier (an ordinal: Wildlands) within WAKE_RANGE in sight, or who strikes it; the great temple's only when struck. */
static final int WAKE_TIER = 2;
static final double WAKE_RANGE = 10.0;
/** The hunting form: shares of the solo numbers; it gives up after HUNT_LIFE or once its quarry is HUNT_LOSE away. */
static final double HUNT_HEALTH = 0.4, HUNT_DAMAGE = 0.65, HUNT_LOSE = 96.0;
static final int HUNT_LIFE = 20 * 60 * 6;
/** Experience it gives (the temple form; the hunting form gives a third). */
static final int XP = 250;
public static final byte WAKE = 96, CAST = 97, TURN = 98;
```

**Synced data** (`defineSynchedData`): `DORMANT` (boolean), `CASTING` (byte: -1, or the move slot 0..3), `TURNING`
(boolean), `DECOY` (boolean). (`EntityDataSerializers.BOOLEAN` and `BYTE`; checked in 26.3.)

**Saved** (`addAdditionalSaveData`/`readAdditionalSaveData` with `ValueOutput`/`ValueInput`, as `SkeletonLord`):
`temple` (int, -1 for none), `home` (BlockPos, the altar spot), `kind` (int: `Temples.WILD`/`RUIN`/`GREAT`, -1 loose),
`quarry` (UUID, the hunting form's), `decoy`. Phase, group and the fight are **not** saved: a champion loaded from disk
wakes fresh. When it loads awake with no temple and no quarry (a loose egg champion), it keeps fighting from where it is.

**Goals**: `FloatGoal`, `MeleeAttackGoal(this, 1.15, true)`, `LookAtPlayerGoal`, `RandomLookAroundGoal` (no strolling);
targets `HurtByTargetGoal` and `NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, (t, l) -> foe(t))`.
The hunting form targets only its quarry, and whoever hurts it.

**`foe(Entity e)`** (public, shared with the moves): a living, non-spectator, non-creative `Player` who is not downed
(`Downed.isDowned`), or a mob that has hurt it in the last 200 ticks, or an `OwnableEntity` whose owner is a player.
Never villagers or `Folk` otherwise. So the Inquisitor's blows in the great temple never touch the townsfolk, and the
guards are never drawn in.

**Asleep (the statue)**: `isImmobile()` while `DORMANT`, no goals run, no gravity change. Every 40 ticks a few dust
particles in the patron's colour. It takes no damage at all. A blow from a player wakes it if that player's
`WorldTiers.of(p).ordinal() >= WAKE_TIER`. A lower-tier player gets the overlay
`message.jaymoskingdoms.patron_champion.unworthy` and `BELL_RESONATE` (0.6, 0.5). A temple champion of kind `WILD` or
`RUIN` also wakes for a qualifying player within `WAKE_RANGE` in sight. `GREAT` wakes only when struck.

**Waking** (`wake(level)`): `DORMANT` off, `broadcastEntityEvent(WAKE)`, the patron's sound (5.4), `SOUL_ESCAPE`
burst, speech `wake` (`Speech.say(level, this, Component.translatable(...), patron.colour & 0xFFFFFF, 48.0)`). It counts the
group and scales (below), and the boss bar shows.

**Group and tier scaling** (`rescale()`, at waking and every `RECOUNT_TICKS` while awake):
- `group` = the most players ever counted in this fight, up to `GROUP_MOST` (players within `FIGHT_RANGE`, alive, not
  spectating, not creative, not downed). The hunting form counts players within `FIGHT_RANGE` of its quarry.
- `tier` = the highest `WorldTiers.of(p).ordinal()` among them (the hunting form: its quarry's).
- Health modifier `jaymoskingdoms:champion_health`, `ADD_MULTIPLIED_TOTAL`:
  `(1 + HEALTH_PER_PLAYER * (group - 1)) * (1 + WorldTiers.HEALTH[tier]) * form - 1`, where `form` is `HUNT_HEALTH` for
  the hunting form and 1 otherwise. Damage modifier `jaymoskingdoms:champion_damage`: the same with
  `DAMAGE_PER_PLAYER`, `WorldTiers.DAMAGE` and `HUNT_DAMAGE`. The share of health it had is kept
  (`setHealth(max * share)`, as `WorldTiers.raise` does). `WorldTiers.HEALTH`/`DAMAGE` are package-private in the same
  package.
- Examples: solo at Wildlands is 810 health and a 17.5 blow. Four players at Dread are 600 x 2.8 x 1.6 = 2688 health and
  a 14 x 1.24 x 1.4 = 24.3 blow. The hunting form, solo at Hearth, is 240 health and a 9.1 blow.
- The phase thresholds are picked at waking: `TURNS_HUNT` for the hunting form, `TURNS_4` when `group >= FOURTH_PHASE_FROM`,
  else `TURNS_3`. A group that grows to 3 mid-fight gets the fourth phase, but only if it hasn't passed 25% yet.

**Phases and turns**: `phase` 0.. Crossing a threshold starts a turn. For `TURN_TICKS` it is untouchable (`hurtServer`
returns false), `TURNING` is on, any move under way is cancelled, `broadcastEntityEvent(TURN)`, and it says `phase.<n+1>`.
At the end comes a shockwave: every foe within `TURN_REACH` takes `TURN_SHARE` x its blow and is pushed `TURN_PUSH` away
and up 0.4 (the `SkeletonLord.blast` pattern). Then `phase++`. The last phase (`phase == thresholds.length`) also sets
the move cooldown factor `LAST_COOLDOWN` and adds a speed modifier `LAST_SPEED`. The fourth phase calls
`moves.hand(level, this, group)` once at its start.

**Moves** (`Moves moves = Moves.create(patron)`, `cooldowns int[4]`, `castLeft`, `castSlot`, `castTarget`):
- Phase n has moves `0..min(n, 2)` (A from phase 1, B from phase 2, C from phase 3). The hunting form has A and B.
- Every tick while awake, not turning, and not kneeling (`BossPosture.kneeling(this)`): if a move is under way, call
  `move.tell().tick(level, this, target, castLeft)`; at `castLeft == 0` call `move.act().land(level, this, target)`,
  then set `CASTING` to -1. A kneel (posture broken) cancels a move under way. That is the counterplay for every
  channel.
- If none is under way, and `MOVE_GAP` has passed since the last one, and it has a target: pick at random among the
  open moves that are off cooldown and whose `minRange..maxRange` fits the distance to the target. Then set
  `CASTING = slot`, `broadcastEntityEvent(CAST)`, stop the navigation, and put the move on cooldown
  (`cooldown * factor`).
- While casting it stands still (`isImmobile` adds `castLeft > 0`), as `SkeletonLord` does.
- `moves.tick(level, this)` runs every tick for lingering effects. `moves.hurt(this, source, amount)` scales every
  blow it takes (guards, Iron Skin, the ash cloud). `moves.clear(this)` runs on reset and death.

**Leash and reset** (temple form, `kind >= 0`): a target beyond `HOME_RADIUS` (great: `GREAT_HOME`) from `home` is
dropped, and it walks back. With no counted player in `FIGHT_RANGE` it heals `REST_HEAL` of max health a second. After
`RESET_TICKS` with none: `moves.clear`, adds discarded, modifiers removed, health full, phase 0, group 0, `HITTERS`
cleared. It is moved back to `home` (`teleportTo` if it is beyond 6 blocks, else it walks) and falls asleep again
(`DORMANT` on, facing the way out).

**Stuck**: if it has a target, and has neither landed a blow nor had a path (`getNavigation().isDone()` while the
target is beyond reach) for `STUCK_TICKS`, it blinks: `MoveKit.blinkSpot(level, target, 2.0, 4.0)` (a standable spot
with two blocks of headroom beside the target), then `teleportTo`, `ENDERMAN_TELEPORT`, and a patron-coloured dust
burst at both ends. This stops pillar-and-bow cheese.

**Boss bar**: `ServerBossEvent(getUUID(), getDisplayName(), colour (section 2), PROGRESS)`, visible only while awake.
Every 10 ticks it is shown to every non-spectator player within `BossBars.RANGE` (48) and taken from the rest. A temple
fight is a set piece, so the whole group sees it, not only the hitters. Progress is `health / max`. It is cleared on
`remove` and `stopSeenByPlayer`, and `SERVER_STOPPED` clears it too. `BossPosture`'s thin posture bar comes on its own,
because `applies()` is true through `BossBars.boss`.

**Who fought it**: `HITTERS` (`Set<UUID>`, not saved) gains any `ServerPlayer` behind a blow that did harm (`hurtServer`, `source.getEntity()`).

**Other overrides**: `removeWhenFarAway` false. `setPersistenceRequired()` for the temple form. `checkDespawn`
leaves it be. `canBreatheUnderwater()` true for the Tide-Bride. `setPathfindingMalus(PathType.WATER, 0)` for the
Tide-Bride (check `PathType` in 26.3). `isPushable` false while asleep. The held weapon is
`Spoils.powers(PatronWeapons id)` in `MAINHAND` with `setDropChance(EquipmentSlot.MAINHAND, 0.0F)`. Without Powers it
is empty, and it still fights (its blow is its attribute). `dropCustomDeathLoot`: nothing but the super call; the head
is `PatronChampions.died`'s. `xpReward = XP` (the hunting form `XP / 3`). Decoys: `xpReward = 0`, no drops.

**Public API for the moves** (in `champions/`, so `public`): `patron()`, `phase()`, `group()`, `hunting()`, `blow()`
(the current `ATTACK_DAMAGE` value), `foe(Entity)`, `foes(double range)` (foes within range, sorted nearest first),
`hurtFoe(ServerLevel, LivingEntity, float amount, boolean magic)` (`mobAttack(this)` or `indirectMagic(this, this)`),
`wearPosture(float)` (calls `BossPosture.wear`), `nextBlow(float factor, int ticks)` (the next melee blow times this),
`home()`, `blinkTo(Vec3)`, `speak(String key)`, `spawnAdd(ServerLevel, Mob)` (section 5.5), `decoy()`,
`makeDecoy()`. `doHurtTarget` applies `nextBlow` and clears it.

### 5.3 Move framework (`champions/Move.java`, `Moves.java`, `MoveKit.java`)
```java
public record Move(String id, int windup, int cooldown, double minRange, double maxRange, Tell tell, Act act) {
	@FunctionalInterface public interface Tell { void tick(ServerLevel level, PatronChampion c, @Nullable LivingEntity target, int left); }
	@FunctionalInterface public interface Act { void land(ServerLevel level, PatronChampion c, @Nullable LivingEntity target); }
}
public interface Moves {
	/** [A, B, C]: opened by phases 1, 2 and 3. */
	List<Move> moves();
	/** The fourth phase (a group of FOURTH_PHASE_FROM or more): the patron's hand. */
	void hand(ServerLevel level, PatronChampion c, int group);
	default void tick(ServerLevel level, PatronChampion c) {}
	default float hurt(PatronChampion c, DamageSource source, float amount) { return amount; }
	default void clear(PatronChampion c) {}
	static Moves create(Patron p) { return switch (p) { case HEARTHKEEPER -> new HearthWardenMoves(); ... }; }
}
```
`MoveKit` (public static): `ring(level, center, radius, colour)` and `column(level, pos, height, colour)` as telegraphs
(`DustParticleOptions(colour, 1.2F)`, sent each tell tick), `inCone(c, e, range, degrees)`,
`nearLine(from, to, e, width)`, `push(e, from, strength, lift)` (`push` then `needsSync = true`), `blinkSpot(...)`,
`markSpot(...)`, `adds(group)` = `1 + group / 2`. Every telegraph shows for its whole wind-up, so a player who watches can
move out.

Damage in the move tables below is a share of `c.blow()`, so tier and group scaling carry into every move.

### 5.4 The eight champions (A from phase 1, B from phase 2, C from phase 3; the hand in a group's fourth phase)
Each class keeps its own numbers as `static final` at the top: `<MOVE>_WINDUP`, `_COOLDOWN`, `_RANGE`, `_SHARE`, and the
rest. All sounds below are checked to exist in 26.3 `SoundEvents`; a `Holder` sound needs `.value()`.

**Hearth-Warden (Hearthkeeper)**: an amber-robed matron with an apron and a hearth-iron mace. Wake sound `FIRE_EXTINGUISH`, reversed in feel (pitch 0.5).
| Move | Wind-up / cooldown (ticks) | Range | What |
|---|---|---|---|
| A **Banked Coals** | 20 / 140 | 0-16 | A flame ring marks the spot under each foe within 16 (at most 4) for the wind-up. Each spot then bursts, r 2.0: 0.6x and 60 ticks of fire. `FIRECHARGE_USE`. |
| B **Kept Fire** | 0 / 400 | any | A 60-tick channel: she heals 1.5% of her max health every 10 ticks. It breaks if she takes 6% of her max health during it, or kneels. `FURNACE_FIRE_CRACKLE` loop. |
| C **Hearth's Wrath** | 25 / 180 | 0-10 | A 70° cone, 10 long: 0.9x, 80 ticks of fire, push 1.2. Flame particles fill the cone during the wind-up. `BLAZE_SHOOT`. |
| Hand | | | `adds(group)` Blazes ("Ember spirit", named), health 20. |

**Forge-Thane (Anvil-Father)**: a broad, bearded smith in iron plate with a great hammer. Wake sound `ANVIL_LAND` (1.0, 0.5).
| Move | Wind-up / cooldown | Range | What |
|---|---|---|---|
| A **Hammerfall** | 15 / 160 | 4-14 | He crouches, then leaps at the target's spot as it was at the wind-up's start (a ballistic `setDeltaMovement`). On landing, r 4: 1.0x, Slowness II 40, knock up 0.5. `MACE_SMASH_GROUND`. Step off the marked spot. |
| B **Slag Spray** | 20 / 200 | 0-8 | A 60° cone, 8 long: 0.5x and 80 ticks of fire. Lava drip particles. No blocks placed. |
| C **Iron Skin** | 0 / 420 | any | For 120 ticks he takes 40% of blows (`hurt` x0.4), and each melee blow on him costs the striker's main-hand item 6 durability (`hurtAndBreak`). A kneel ends it. `SMITHING_TABLE_USE` per blow. |
| Hand | | | `adds(group)` Magma Cubes (size 2, "Slag"). |

**Tide-Bride (Deep Mother)**: a tall veiled woman in sea-green, kelp and shell, with a glaive. Wake sound `CONDUIT_ATTACK_TARGET`.
| Move | Wind-up / cooldown | Range | What |
|---|---|---|---|
| A **Undertow** | 20 / 160 | 0-10 | Bubble rings close in during the wind-up. Every foe within 10 is pulled 1.2 toward her, with Slowness I 40. Her next blow within 30 ticks is x1.3 (`nextBlow`). `TRIDENT_RIPTIDE_1`. |
| B **Drowning Grip** | 15 / 260 | 0-14 | One foe in sight: for 100 ticks they drown, 0.12x a second as `damageSources().drown()`, with bubble particles. It ends early if they strike her 3 times or break her sight for 40 ticks. |
| C **Riptide Wave** | 25 / 200 | 0-14 | A wave line 14 long and 3 wide toward the target, marked during the wind-up: 0.9x, knock up 0.8, and it puts out fire. Splash particles. `GENERIC_SPLASH`. |
| Hand | | | `adds(group)` Drowned with tridents. |

**Antlered Hunter (Green Huntsman)**: a lean hunter in hides with a stag's antlers and a spear. Wake sound `POLAR_BEAR_WARNING` (1.0, 0.6).
| Move | Wind-up / cooldown | Range | What |
|---|---|---|---|
| A **Pinning Spear** | 15 / 120 | 6-28 | He aims at the target's spot at the wind-up's start; a green line shows it. On release, every foe within 1.2 of that line (out to 28) takes 0.8x and Slowness IV 30 ("pinned"). Move off the line to dodge. `TRIDENT_THROW`. |
| B **Into the Green** | 0 / 300 | any | Invisibility 80 ticks and a blink behind the farthest foe within 24. His next blow is x1.5. Leaf particles where he stood. |
| C **Stampede** | 20 / 220 | 5-16 | He paws the ground, then charges in a straight line up to 16. Every foe within 1.5 of his path takes 1.1x and is pushed aside 1.4. Charging into a wall dazes him: `wearPosture(25% of max posture)` and 30 ticks still. Bait him into walls. `RAVAGER_ROAR`. |
| Hand | | | `adds(group)` angry Wolves ("his hounds"). |

**Grave-Lamp (Lantern Warden)**: a gaunt warden in grey with a soul lantern on a staff. Wake sound `BELL_BLOCK` (1.0, 0.5).
| Move | Wind-up / cooldown | Range | What |
|---|---|---|---|
| A **Lamp Flare** | 20 / 180 | 0-20 | The lantern is raised and glows (`END_ROD` swirl, `BELL_RESONATE`). On release, every foe in sight within 20 who faces him (look dot > 0.5) gets Blindness 60 and Slowness I 60. Look away to dodge. |
| B **Soul Lanterns** | 15 / 240 | 0-16 | Three soul lights (particles, no blocks) are set at spots near foes. Each pulses r 2.5 every 20 ticks for 160 ticks: 0.25x magic. `SOUL_ESCAPE`. |
| C **Night Falls** | 30 / 400 | any | Darkness 160 to every foe within 24. For 160 ticks his blows are x1.3. `WARDEN_ROAR` (0.6, 1.4). |
| Hand | | | `adds(group)` Vexes ("Lantern wisp"), each living 600 ticks. |

**Sun-Marshal (Crowned Sun)**: a gold-trimmed knight with a rayed crown and a longsword. Wake sound `BEACON_ACTIVATE`.
| Move | Wind-up / cooldown | Range | What |
|---|---|---|---|
| A **Sunbeam** | 25 / 140 | 0-20 | A ring of light follows the target for 15 ticks, then holds still for 10. On release, a column r 1.5 at the held spot: 1.0x and 100 ticks of fire. `LIGHTNING_BOLT_THUNDER` (0.5, 1.6). |
| B **Royal Guard** | 0 / 300 | any | For 80 ticks his front is guarded. A blow from the front (dot > 0.2) does nothing and wears his posture by half its amount. From behind, blows are x1.25. A gold shimmer shows. |
| C **Noon** | 40 / 360 | 0-10 | Rising light and `BEACON_ACTIVATE`, then a burst on every foe within 10 he can see: 1.2x, Weakness 100, 60 ticks of fire. Break his line of sight (pillars) or be beyond 10. |
| Hand | | | `adds(group)` Pillagers in golden helmets ("Sun-guard"). |

**Cinder-Shade (Ash Whisperer)**: a hooded, soot-black thief with ember eyes and a dagger. Wake sound `SCULK_CLICKING`.
| Move | Wind-up / cooldown | Range | What |
|---|---|---|---|
| A **Smokestep** | 10 / 120 | 0-16 | A smoke puff, then a blink 2 behind the target and a strike at once, x1.4. `ENDERMAN_TELEPORT` (0.6, 1.5). |
| B **Ember Knives** | 15 / 160 | 4-20 | Five `SmallFireball`s (`new SmallFireball(level, c, dir)`) in a 40° fan at the target. Vanilla's fireball harm, and they set fires. |
| C **Ash Cloud** | 20 / 360 | any | A cloud r 6 where she stands, for 200 ticks: foes inside get Blindness 40, refreshed each second. She takes 50% while inside it and keeps to it. Pull or knock her out, or fight from outside. `LARGE_SMOKE` + `ASH` particles. |
| Hand | | | `adds(group)` **smoke doubles**: her own type with `makeDecoy()`, health 1, no bar, no drops, no posture, not a boss. Struck, one bursts into smoke (Blindness 60 within 3). |

**Inquisitor (The Truth)**: a stern figure in white and grey with a tall mitre and a heavy plain sword. Wake sound `BELL_BLOCK` (1.0, 0.8).
| Move | Wind-up / cooldown | Range | What |
|---|---|---|---|
| A **Chain of Truth** | 15 / 140 | 4-16 | A chain line (particles) to the target. On release they are dragged to him (push along the line, 1.4), and he strikes, x1.2. `CHAIN_HIT`. |
| B **Strip the Lie** | 25 / 260 | 0-10 | A bell. Every foe within 10 loses every beneficial effect (`MobEffectCategory.BENEFICIAL`) and invisibility, and gets Glowing 120. |
| C **Judgement** | 60 / 400 | 0-24 | Three tolls and a column of light over one marked player. On release, that player takes 30% of **their** max health, magic, ignoring armour (`damageSources().magic()`), split evenly among every player within 3.0 of them, them included. Alone you take it all; a group stands together. |
| Hand | | | `adds(group)` Vindicators ("Zealot"). |

### 5.5 Adds (`PatronChampions`)
- `static final AttachmentType<UUID> ADD` (not persistent; `AttachmentRegistry.create(id("champion_add"))`), and
  `static boolean isAdd(Entity)`.
- `PatronChampion.spawnAdd(level, mob)`: sets `ADD` to the champion's UUID and a name (`Component.translatable`), gives
  it the champion as its leader through the target (`setTarget(nearest foe)`), adds it to the level within 6 blocks
  (`MoveKit.blinkSpot`), and records it in `ADDS` (`Map<UUID, List<Mob>>`). Adds are not persistent. On reset or death
  every add is `discard()`ed, with a smoke puff. Excluded from world tiers (`WorldTiers.hostile`) and from
  `BossBars.boss`. Adds give no XP or drops (`setDropChance` 0 for each slot and `xpReward = 0`, set when spawned).

### 5.6 A champion falls (`PatronChampions.died`, `AFTER_DEATH`)
For a `PatronChampion` that is not a decoy:
1. `moves.clear`, adds discarded, a burst of the patron's dust, a speech bubble `fall`, and to each fighter the overlay
   `message.jaymoskingdoms.patron_champion.slain` (patron).
2. **The fighters**: players in its `HITTERS` who are online, in that world, and within `WorldTiers.CREDIT_RANGE` (64).
3. **Temple form, the head roll** (the third-try guarantee, per player):
   ```java
   /** (0.7.518) Per player: champion kills of each patron (by key) since that patron's weapon last fell for them. Saved, kept through death. */
   static final AttachmentType<Map<String, Integer>> TRIES = AttachmentRegistry.create(JaymosKingdoms.id("champion_tries"),
   	b -> b.persistent(Codec.unboundedMap(Codec.STRING, Codec.INT)).copyOnDeath());
   /** The chance the head falls for a fighter on their 1st, 2nd and 3rd try since their last: the third is sure. */
   static final float[] HEAD_CHANCE = {0.34F, 0.5F, 1.0F};
   /** The grade a head falls at (Powers' smithing grade ids): a solo kill's is a Masterwork. */
   static final String HEAD_GRADE = "perfect", HEAD_GRADE_SOLO = "master";
   /** How long a fighter's head waits for them (ticks: an extended item life) and its pickup delay. */
   static final int HEAD_PICKUP_DELAY = 10;
   ```
   For each fighter: `tries = TRIES[p] + 1`. If `random < HEAD_CHANCE[min(tries, 3) - 1]`: the head
   (`Spoils.powers(PatronWeapons head id)`) with grade `HEAD_GRADE_SOLO` if the champion's group was 1 and it had one
   fighter, else `HEAD_GRADE`. The grade is set through the Powers component found by id
   (`BuiltInRegistries.DATA_COMPONENT_TYPE.getOptional(Identifier.fromNamespaceAndPath("jaymospowers", "forging_grade"))`,
   cast to `DataComponentType<String>`; no grade without Powers). The head drops as an `ItemEntity` at the champion with
   `setTarget(fighter.getUUID())` (only they can pick it up: `ItemEntity.java:338`), `setExtendedLifetime()`, and
   `setPickUpDelay(HEAD_PICKUP_DELAY)`. Their `TRIES[p]` goes back to 0, and they get the overlay
   `...patron_champion.weapon` (the head's name) and `ANVIL_LAND` (0.6, 1.2). Otherwise `TRIES[p] = tries`, with the overlay
   `...patron_champion.no_weapon` (tries, 3). Without Powers (`powers()` empty), the roll is skipped and `TRIES` is left
   alone.
4. **Deeds** for each fighter (`Patrons.deed`): sworn to `p` → `DEED_GREAT`, kind `"champion"` (the patron's own
   champion tests the faithful). Sworn to a rival of `p` (`Patron.rivals`) → their patron `DEED_MID`, kind `"rival_champion"`.
5. **The temple**: `slainDay = Patrons.today(server)`, champion UUID cleared. It re-forms after `RETURN_DAYS` (section 3.4).
6. **The hunting form** gives no head and no tries. Its quarry's present patron gives `DEED_GREAT`, kind
   `"stood_the_hunt"`. Then `Patrons.hunted(quarry, p)` (section 6).
7. Great-foe credit, Valor's great kill, and the rest come through `BossBars.boss` as for every boss (open question 1).

### 5.7 Op test commands (`/kingdom champion`, `LEVEL_GAMEMASTERS`)
- `summon <patron>`: a loose, awake temple-form champion at you (home = where it stands, `kind` = -1, leash
  `HOME_RADIUS`). The spawn eggs do the same.
- `hunt <patron>`: its hunting form, sent at you now (it does not need a mark; it does not flip one unless you have one).
- `tries <patron> <n>`: sets your `TRIES` for that patron (to test the guarantee).

---

## 6. The hunt (part (b)'s `hunted` mark, `PatronChampions`)

### 6.1 Tuning
```java
/** How often hunted players are looked at (ticks), and the chance the champion comes then (about ten minutes on average). */
static final int HUNT_CHECK = 20 * 30;
static final float HUNT_CHANCE = 0.05F;
/** Where it comes: this far from its quarry, on standable ground with HEADROOM, tried this often; out of their view if it can. */
static final int HUNT_NEAR_MIN = 18, HUNT_NEAR_MAX = 28, HUNT_TRIES = 12, HEADROOM = 3;
/** After its quarry got away (logged off, left the world, outran it, or it ran out of time), the wait before it comes again (ticks). */
static final int HUNT_RETRY = 24000;
/** It stands over its downed quarry this long before it goes (ticks). */
static final int HUNT_STAND = 60;
```

### 6.2 When it comes (`huntTick`, every `HUNT_CHECK`)
For each player who is alive, not spectating, not creative, not sleeping, not a passenger, not under water, in the
Overworld, the Nether or the Aether, with no hunter of their own alive (`HUNTERS`: `Map<UUID, PatronChampion>`, not saved),
past their `NEXT_HUNT` time (`Map<UUID, Long>`, not saved), and **not on anyone's land**
(`Factions.at(server, level, pos) == null`, so it never comes into a town):
- `p` = the first of `Patrons.hunters(sp)` (oldest first) with `Turning.trial(sp, p) == 0`. So the hunt waits until that
  patron's trials (or eased trials) are over. Part (b) writes the `hunted` mark only when there are trials
  (`TRIAL_DAYS > 0`), so the `patron_trial_days 0` gamerule also means no hunt.
- With chance `HUNT_CHANCE`, `send(sp, p)`.

### 6.3 `send(ServerPlayer sp, Patron p)`
Find a spot `HUNT_NEAR_MIN..HUNT_NEAR_MAX` away (`HUNT_TRIES`; prefer a spot with look dot < 0.3, behind them). Spawn the
type awake with `quarry = sp`, `kind = -1`, no home, and `rescale()` (the hunting form). Arrival:
`LIGHTNING_BOLT_THUNDER` (0.4, 0.7) at the spot. To the quarry only, `ClientboundSoundPacket` of `ELDER_GUARDIAN_CURSE`
(part (b)'s `signs` pattern) and the overlay `message.jaymoskingdoms.patron_champion.hunt` (patron, coloured). A patron
dust column at the spot, and speech `hunt`. It targets the quarry.

### 6.4 How it ends
| What happens | Result |
|---|---|
| It dies (anyone kills it) | Section 5.6 (6): `stood_the_hunt` deed, then `Patrons.hunted(quarry, p)`, overlay `...hunt.over`. |
| Its quarry dies or goes down (`Downed.isDowned`) | It stands over them for `HUNT_STAND`, says `hunted`, and vanishes in smoke. `Patrons.hunted(quarry, p)`, overlay `...hunt.over`. It has had its hunt. |
| Its quarry logs off, leaves its world, gets beyond `HUNT_LOSE`, or `HUNT_LIFE` runs out | It vanishes in smoke (`discard`). The mark stays. `NEXT_HUNT = now + HUNT_RETRY`, overlay `...hunt.lost`. It comes again another day. |
| The server stops | It is not persistent; the mark stays. |

`Patrons.hunted` (`Patrons.java:316`) already turns the mark into `hunted_done`. So the hunt happens once per leaving,
and `Patrons.hunters` stops naming it. The hunting form never leashes and never resets. It is a great foe like the
temple form (it is a `PatronChampion`).

---

## 7. The patrons' weapons (Powers)

### 7.1 Heads (`tinkering/WeaponParts.java`)
```java
/**
 * (0.20.18) The patrons' weapons (Kingdoms 0.7.518: each champion drops its patron's iron head, armory/PatronWeapons). Like the
 * kings' heads: taken, never shaped, cast or forged anew, only put together at the Tool Station.
 */
public static final String HEARTHGUARD_HEAD = "hearthguard_head", ANVIL_HAMMER_HEAD = "anvil_fathers_hammer_head",
	TIDECALLER_HEAD = "tidecaller_head", HUNTSMANS_SPEAR_TIP = "huntsmans_spear_tip", GRAVELIGHT_BLADE = "gravelight_blade",
	SUNBRAND_BLADE = "sunbrand_blade", ASHFANG_BLADE = "ashfang_blade", PLAIN_WORD_BLADE = "plain_word_blade";
public static final Set<String> PATRONS = Set.of(...the eight...);
```
In the `static` block, after the kings' three:
| Head | Weapon key | Scheme | mB | Assembly |
|---|---|---|---|---|
| `hearthguard_head` | `jaymospowers:hearthguard` | SINGLE | 4 x INGOT | ROD_BINDING |
| `anvil_fathers_hammer_head` | `jaymospowers:anvil_fathers_hammer` | SINGLE | 8 x INGOT | TOUGH_BINDING |
| `tidecaller_head` | `jaymospowers:tidecaller` | SINGLE | 3 x INGOT | ROD_BINDING |
| `huntsmans_spear_tip` | `jaymospowers:huntsmans_spear` | SINGLE | 2 x INGOT | ROD_BINDING |
| `gravelight_blade` | `jaymospowers:gravelight` | SINGLE | 3 x INGOT | ROD_BINDING |
| `sunbrand_blade` | `jaymospowers:sunbrand` | SINGLE | 3 x INGOT | GUARD_ROD |
| `ashfang_blade` | `jaymospowers:ashfang` | SINGLE | 1 x INGOT | ROD_BINDING |
| `plain_word_blade` | `jaymospowers:plain_word` | SINGLE | 5 x INGOT | GUARD_TOUGH |

`king(Kind)` becomes `KINGS.contains(kind.part()) || PATRONS.contains(kind.part())` (doc: "a king's or a patron's head").
`FOUND` gets `PATRONS` too. Through `king()`, every existing rule then holds without more edits: no cast
(`castResult`/`castResults`, `Melting.java:346`), no Smithing Anvil option (`Forging.all:85`), not shaped
(`shaped()` via `FOUND`), iron only (`heads()`), not in the cast list of the creative tab (`Tinkering.java:234`), and a bare
head reads as iron (`WeaponPartItem.metal:39`). The grade a head carries passes to the weapon through `Grades.carry`, so
the usual grade line, colour and numbers (and the Masterwork glint) show on it.

### 7.2 Weapons (`armory/PatronWeapons.java`)
```java
/** (0.20.18) The patrons' weapons: their item, their head, and the patron (a Kingdoms key) whose sworn they serve best. */
enum Kind {
	HEARTHGUARD("hearthguard", WeaponParts.HEARTHGUARD_HEAD, "hearthkeeper"),
	ANVIL_HAMMER("anvil_fathers_hammer", WeaponParts.ANVIL_HAMMER_HEAD, "anvil_father"),
	TIDECALLER("tidecaller", WeaponParts.TIDECALLER_HEAD, "deep_mother"),
	HUNTSMANS_SPEAR("huntsmans_spear", WeaponParts.HUNTSMANS_SPEAR_TIP, "green_huntsman"),
	GRAVELIGHT("gravelight", WeaponParts.GRAVELIGHT_BLADE, "lantern_warden"),
	SUNBRAND("sunbrand", WeaponParts.SUNBRAND_BLADE, "crowned_sun"),
	ASHFANG("ashfang", WeaponParts.ASHFANG_BLADE, "ash_whisperer"),
	PLAIN_WORD("plain_word", WeaponParts.PLAIN_WORD_BLADE, "truth");
}
```
Properties: `p.sword(ToolMaterial.DIAMOND, damage, speed).rarity(Rarity.EPIC).fireResistant()` (the diamond material's
1561 uses and +3; the grade adds on top):
| Weapon | Damage over the hand | Speed | Better Combat parent | Ability (active is right-click, main hand) |
|---|---|---|---|---|
| Hearthguard (mace) | 5.0 | -2.8 | `jaymospowers:combo_mace` | on-hit **Kindled Blow** |
| Anvil-Father's Hammer | 8.0 | -3.3 | `jaymospowers:combo_mace` | on-hit **Sunder** |
| Tidecaller (glaive) | 5.0 | -2.7 | `jaymospowers:combo_glaive` | active **Undertow** |
| Huntsman's Spear | 4.0 | -2.5 | `jaymospowers:combo_spear` | on-hit **Marked Prey** |
| Gravelight (war scythe) | 6.0 | -3.0 | `jaymospowers:combo_war_scythe` | on-hit **Soul Flame** |
| Sunbrand (longsword) | 5.0 | -2.4 | `jaymospowers:combo_longsword` | active **Sun's Judgement** |
| Ashfang (dagger) | 2.5 | -1.6 | `jaymospowers:combo_dagger` | active **Smokestep** |
| The Plain Word (broadsword) | 6.5 | -2.9 | `jaymospowers:combo_broadsword` | on-hit **Unmasking** |

Registered in `Armory.register` after line 197:
`PatronWeapons.HEARTHGUARD = item("hearthguard", p -> new PatronWeaponItem(p, PatronWeapons.Kind.HEARTHGUARD), p -> PatronWeapons.props(PatronWeapons.Kind.HEARTHGUARD, p));`
and the same for each (fields `public static Item ...` on `PatronWeapons`, plus `all()`). They are in `Armory.ITEMS`, so
they go in the Armory tab and `Armory.isWeapon` is true (Tool Station modifiers work).

### 7.3 Abilities (`PatronWeaponItem`, `PatronWeaponHits`; numbers `static final` at the top of `PatronWeapons`)
`float s = PatronWeapons.sworn(wielder, kind)`: 1 when sworn to the weapon's patron (Kingdoms tier ≥ 1), 0 when not, and
-1 while under that patron's trials (you turned from it: the weapon is cold, and its ability does nothing). A mob
wielding one (a champion) is 0. Bonus damage is an extra `hurtServer` after `setInvulnerableTime(0)` (the lance's pattern,
`Armory.charge`). `PatronWeaponHits.extra(...)` guards it with a `ThreadLocal` so it can never chain.

| Weapon | Ability | Numbers |
|---|---|---|
| Hearthguard | **Kindled Blow** (on-hit): at most every `HEARTH_GAP 40` ticks per wielder, a hit heals the wielder and every other player within `HEARTH_RANGE 6.0` (sworn `8.0`) by `HEARTH_HEAL 1.5` (sworn x`HEARTH_SWORN 2.0`). Players heal through `Resting.healWorst(sp, amount)`; anything else through `heal()`. `FLAME` particles, `FIRE_AMBIENT`. | `HEARTH_GAP, HEARTH_RANGE, HEARTH_RANGE_SWORN, HEARTH_HEAL, HEARTH_SWORN` |
| Anvil-Father's Hammer | **Sunder** (on-hit): each hit takes `SUNDER_ARMOR 2.0` armour off the target (a transient modifier `jaymospowers:sunder`, amount -2 x stacks) for `SUNDER_TICKS 100`, refreshed, stacking to `SUNDER_MOST 3` (sworn `5`). `ANVIL_LAND` (0.4, 1.6) on a new stack. | `SUNDER_ARMOR, SUNDER_TICKS, SUNDER_MOST, SUNDER_MOST_SWORN` |
| Tidecaller | **Undertow** (active): every mob within `UNDERTOW_RANGE 8.0` (sworn `10.0`; x`UNDERTOW_WET 1.5` while the wielder is in water or rain) that is an `Enemy` or targets the wielder is pulled `UNDERTOW_PULL 1.1` toward them and gets Slowness II `UNDERTOW_SLOW 60`. Never players. Cooldown `UNDERTOW_COOLDOWN 300` (sworn `200`). `TRIDENT_RIPTIDE_1`, bubble rings. | as named |
| Huntsman's Spear | **Marked Prey** (on-hit): the target glows for `MARK_TICKS 200`. While marked, your hits on it deal `MARK_BONUS 0.15` more (an `Animal`: `MARK_BEAST 0.30`); sworn `0.25` / `0.45`. The bonus is the extra hurt, a share of the hit's damage. | `MARK_TICKS, MARK_BONUS, MARK_BEAST, MARK_BONUS_SWORN, MARK_BEAST_SWORN` |
| Gravelight | **Soul Flame** (on-hit): a hit on the undead (`EntityTypeTags.UNDEAD`) sets them alight `GRAVE_FIRE 100` ticks and adds `GRAVE_BONUS 4.0` (at night `GRAVE_NIGHT 6.0`), with `SOUL_FIRE_FLAME` particles. Sworn: the bonus x`GRAVE_SWORN 1.5`, and each undead struck heals the wielder `GRAVE_HEAL 1.0`. | as named |
| Sunbrand | **Sun's Judgement** (active): the entity you look at within `SUN_RANGE 16` (`ProjectileUtil.getEntityHitResult` along the view). After `SUN_WINDUP 15` ticks (an `END_ROD` column marks it), a beam strikes it: `SUN_DAMAGE 8.0` magic (sworn `11.0`), fire 80, Blindness 40. By day under open sky, x`SUN_DAY 1.5`. Cooldown `SUN_COOLDOWN 400` (sworn `280`). `BEACON_ACTIVATE`. No target, no cooldown. | as named |
| Ashfang | **Smokestep** (active): a leap back (`SMOKE_BACK 1.0` and up `0.25`, the rapier's `setDeltaMovement` pattern, both sides), a `LARGE_SMOKE` cloud where you stood, Invisibility `SMOKE_INVIS 60` (sworn `100`), and mobs within 6 targeting you lose their target. Your next hit within `SMOKE_WINDOW 80` deals x`SMOKE_BACKSTAB 1.75` (the extra 0.75 as the extra hurt). Cooldown `SMOKE_COOLDOWN 240` (sworn `160`). | as named |
| The Plain Word | **Unmasking** (on-hit): strips the target's beneficial effects one at a time, the longest first (`MobEffectCategory.BENEFICIAL`), `UNMASK_EACH 1` (sworn `2`) per hit. An invisible target loses invisibility first and glows `UNMASK_GLOW 100`. `BELL_RESONATE` (0.4, 1.6). | as named |

Actives use `player.getCooldowns().addCooldown(stack, ticks)` / `isOnCooldown(stack)` (as `RapierItem.use`). They return
`InteractionResult.PASS` off the main hand, on cooldown, or while cold. The timed states (Sunder stacks, marks, Smokestep's
window, the Sunbrand wind-up) live in `PatronWeaponHits` as `WeakHashMap`s, ticked by `END_SERVER_TICK`.

**Tooltip** (`appendHoverText`): `item.jaymospowers.patron_weapon.<id>` (the ability, gray), then
`item.jaymospowers.patron_weapon.<id>.sworn` (dark aqua). The grade line comes from Powers' grade tooltip as for every
forged piece.

### 7.4 Sworn reading (`KingdomsCompat.blessing`, `Patrons.blessing`)
Powers, `compat/KingdomsCompat.java`:
```java
/** (0.20.18) Kingdoms' Patrons.blessing(player, key) by reflection: 0 without Kingdoms or on any failure. */
public static float blessing(Player player, String key)
```
(Cache the `Method` the way `boss` does. `MedicineSkill` keeps its own copy and is not touched.)
`PatronWeapons.sworn(e, kind)` = `e instanceof Player p ? Math.signum(KingdomsCompat.blessing(p, "patron_weapon/" + kind.patron)) : 0`.

Kingdoms, `Patrons.blessing` (the key strings are a contract with Powers):
```java
// (0.7.518) Jaymo's Powers' patron weapons: 1 sworn to that patron (tier 1 or more), -1 in its trials (turned from it), else 0
if (key.startsWith(WEAPON_KEY)) {
	Patron p = Patron.byKey(key.substring(WEAPON_KEY.length()));
	if (p == null) {
		return 0.0F;
	}
	return Turning.trial(player, p) > 0.0F ? -1.0F : blessed(player, p, 1) ? 1.0F : 0.0F;
}
```

### 7.5 Data
- `data/jaymospowers/weapon_attributes/<id>.json` x8: `{"parent": "<parent from 7.2>"}`.
- `data/minecraft/tags/item/swords.json`: add the eight ids (the king weapons are there too).
- JEI (`TinkeringJei`, after the kings' loop): the same loop over `WeaponParts.PATRONS` with
  `jei.jaymospowers.patron_head.<part>`.

---

## 8. Blessings and boons that touch this
No blessing changes. These existing hooks reach the new things on their own:
- `Blessings.dealt` (Grave Blows, the Lantern Warden scar): a champion is not undead, so neither applies. Gravelight is
  separate (Powers).
- `Patrons.deed` (section 5.6), `Patrons.met` (section 3.4) and `Patrons.hunted` (section 6) are the existing entry
  points. `Patrons.blessed` is read through `Patrons.blessing` (section 7.4).
- Boons (`Blessings.boon`): Dawnlight burns "the undead and vampires around you", so a champion is not hit. Sun's Muster
  and Wild Hunt help a group as they do anywhere. Nothing to change.

---

## 9. Art (our own, Pillow)

### 9.1 Powers (`tools/patron_weapons_assets.py`, `tools/weapon_parts_assets.py`)
Run `python -I tools/patron_weapons_assets.py` and `python -I tools/weapon_parts_assets.py` from the root.
- **Heads, 16x16**: eight new rows in `weapon_parts_assets.py`'s PARTS list (`("hearthguard_head", "Hearthguard Head",
  "drawn:hearthguard_head", ["iron"])`, and so on), and eight `DRAWN[...]` grids in its `#`/`x`/`o`/`.` code. They write
  `items/<head>.json` (the hot and cold `select` like `gutting_hook_blade.json`), `models/item/tinkering/weapon_part/<head>_iron.json`
  and `_stone.json`, and the textures.
  Shapes: a flanged mace head with an ember-hole; a broad hammer head; a crescent glaive blade; a leaf-shaped spear tip;
  a long curved scythe blade with a lantern hook; a straight longsword blade with a sun-ray fuller; a short curved dagger
  blade; a plain wide broadsword blade.
- **Weapons, 32x32** (`textures/item/armory/<id>.png`, `models/item/armory/<id>.json` parent `minecraft:item/handheld`,
  `items/<id>.json` as `items/gutting_hook.json`). Drawn in code like `king_spoils_assets.py` (`outline`, `shine`), with a
  dark iron ramp and one accent colour each, from the patron's colour:
  | Weapon | Look | Accent |
  |---|---|---|
  | Hearthguard | short oak haft, flanged iron head with a glowing ember core | amber `#E0A050` |
  | Anvil-Father's Hammer | long iron-bound haft, block head with anvil horns | steel `#B0B4BC` |
  | Tidecaller | dark haft, crescent blade with wave fuller, shell at the throat | sea blue `#3A8FB7` |
  | Huntsman's Spear | ash shaft wrapped in hide, leaf tip, antler-tine guard | leaf green `#5E9E3A` |
  | Gravelight | long black haft, curved blade, a small soul lantern hung at the neck | pale blue `#9AA8FF` |
  | Sunbrand | gold crossguard with rays, straight blade, sun disc pommel | gold `#FFC21A` |
  | Ashfang | soot-black grip, short curved ember-edged blade | ash red-brown `#8A5A4A`, ember `#FF7A2A` |
  | The Plain Word | plain grey steel, square crossguard, no ornament, white grip | white `#F2F2F2` |
- No art is copied from any mod.

### 9.2 Kingdoms (`tools/gen_champions.py`)
Run `python -I tools/gen_champions.py` from the root.
- **Skins, 64x64** (`kingdoms/src/main/resources/assets/jaymoskingdoms/textures/entity/champion/<id>.png`): the humanoid
  layout (rows 0-31) and each extra part's box below it (from row 32, as `HornkinModel`). Flat colour ramps with
  noise, drawn in code:
  | Champion | Body | Extra parts in `ChampionModel` (box sizes in pixels) |
  |---|---|---|
  | Hearth-Warden | amber robe, cream apron, ruddy face, grey hair bun | apron front 8x10x1 on the body; bun 4x3x3 on the head |
  | Forge-Thane | iron plate, leather, a braided beard | beard 6x6x2 below the jaw; pauldrons 5x3x5 on each arm |
  | Tide-Bride | sea-green gown, pale skin, shell circlet, kelp | veil 9x9x1 behind the head; kelp sash 2x12x1 |
  | Antlered Hunter | hides and furs, face paint | antlers: two 2x6x2 beams with 2x2x2 tines; a fur collar 10x2x6 |
  | Grave-Lamp | grey shroud, hollow face, iron lantern | hood 9x9x9 (inflated head); lantern 3x4x3 at the belt |
  | Sun-Marshal | gold-trimmed white plate, red cape | crown rays: five 1x3x1 spikes on a 9x1x9 band; cape 10x16x1 |
  | Cinder-Shade | soot-black hood and wraps, ember eyes | hood 9x9x9; a tattered scarf 2x8x1 |
  | Inquisitor | white and grey robes, a tall mitre, iron chain | mitre 6x6x4 on the head; a chain stole 2x14x1 |
- **Spawn eggs, 16x16** (`textures/item/<id>_spawn_egg.png`): an egg in the patron's colour, speckled with a darker
  shade. Models `models/item/<id>_spawn_egg.json` (parent `minecraft:item/generated`, `layer0`) and
  `items/<id>_spawn_egg.json`, as `skeleton_lord_spawn_egg`.

### 9.3 Client (`ChampionRenderer`, `ChampionModel`)
- `State extends HumanoidRenderState`: `patron`, `dormant`, `casting` (slot or -1), `turning`.
- `extractRenderState`: reads `DORMANT`, `CASTING`, `TURNING`.
- Model poses: asleep, the head bowed (`xRot 0.6`) and both arms resting forward on the weapon (`xRot -0.4`). Casting,
  both arms raised (`xRot -2.6`); the Antlered Hunter's and the Inquisitor's A is the right arm thrown forward
  (`xRot -1.6`). Turning, arms out wide (`zRot ±1.2`), the head back.
- `scale()` by the render scale in section 2 (the Forge-Thane x1.15 wide on x and z).
- `register()`: `EntityRenderers.register(PatronChampions.TYPES.get(p), ctx -> new ChampionRenderer(ctx, p))` for each.

---

## 10. Lang

### 10.1 Kingdoms (`kingdoms/src/main/resources/assets/jaymoskingdoms/lang/en_us.json`, tab-indented like the file)
```json
"entity.jaymoskingdoms.hearth_warden": "The Hearth-Warden",
"entity.jaymoskingdoms.forge_thane": "The Forge-Thane",
"entity.jaymoskingdoms.tide_bride": "The Tide-Bride",
"entity.jaymoskingdoms.antlered_hunter": "The Antlered Hunter",
"entity.jaymoskingdoms.grave_lamp": "The Grave-Lamp",
"entity.jaymoskingdoms.sun_marshal": "The Sun-Marshal",
"entity.jaymoskingdoms.cinder_shade": "The Cinder-Shade",
"entity.jaymoskingdoms.inquisitor": "The Inquisitor",
"item.jaymoskingdoms.hearth_warden_spawn_egg": "Hearth-Warden Spawn Egg",
"item.jaymoskingdoms.forge_thane_spawn_egg": "Forge-Thane Spawn Egg",
"item.jaymoskingdoms.tide_bride_spawn_egg": "Tide-Bride Spawn Egg",
"item.jaymoskingdoms.antlered_hunter_spawn_egg": "Antlered Hunter Spawn Egg",
"item.jaymoskingdoms.grave_lamp_spawn_egg": "Grave-Lamp Spawn Egg",
"item.jaymoskingdoms.sun_marshal_spawn_egg": "Sun-Marshal Spawn Egg",
"item.jaymoskingdoms.cinder_shade_spawn_egg": "Cinder-Shade Spawn Egg",
"item.jaymoskingdoms.inquisitor_spawn_egg": "Inquisitor Spawn Egg",

"entity.jaymoskingdoms.patron_champion.add.ember_spirit": "Ember Spirit",
"entity.jaymoskingdoms.patron_champion.add.slag": "Slag",
"entity.jaymoskingdoms.patron_champion.add.drowned": "Drowned Bride's-Man",
"entity.jaymoskingdoms.patron_champion.add.hound": "Hound of the Hunt",
"entity.jaymoskingdoms.patron_champion.add.wisp": "Lantern Wisp",
"entity.jaymoskingdoms.patron_champion.add.sun_guard": "Sun-Guard",
"entity.jaymoskingdoms.patron_champion.add.double": "The Cinder-Shade",
"entity.jaymoskingdoms.patron_champion.add.zealot": "Zealot",

"message.jaymoskingdoms.patron_champion.unworthy": "It does not stir for you. (World tier Wildlands and up)",
"message.jaymoskingdoms.patron_champion.slain": "%s's champion falls.",
"message.jaymoskingdoms.patron_champion.weapon": "You take %s.",
"message.jaymoskingdoms.patron_champion.no_weapon": "Its weapon breaks as it falls. It will hold by your third try. (%1$s of %2$s)",
"message.jaymoskingdoms.patron_champion.hunt": "%s's champion has come for you.",
"message.jaymoskingdoms.patron_champion.hunt.over": "%s's champion has had its hunt.",
"message.jaymoskingdoms.patron_champion.hunt.lost": "%s's champion loses your trail. It will come again.",
"message.jaymoskingdoms.temple.near": "%s's temple",
"building.jaymoskingdoms.great_temple": "great temple",

"speech.jaymoskingdoms.patron_champion.hearthkeeper.wake": "You come armed to her hearth? Then let us see what you're made of.",
"speech.jaymoskingdoms.patron_champion.hearthkeeper.phase.2": "Sit. Rest. I'll mend while you bleed.",
"speech.jaymoskingdoms.patron_champion.hearthkeeper.phase.3": "The fire is banked no longer!",
"speech.jaymoskingdoms.patron_champion.hearthkeeper.phase.4": "Come, little embers. We have guests.",
"speech.jaymoskingdoms.patron_champion.hearthkeeper.fall": "Well struck. Keep the fire for her.",
"speech.jaymoskingdoms.patron_champion.hearthkeeper.hunt": "You let her hearth go cold. I've come to see you out.",
"speech.jaymoskingdoms.patron_champion.hearthkeeper.hunted": "Lie there and think on it. She remembers you.",
"speech.jaymoskingdoms.patron_champion.anvil_father.wake": "Iron rings true or it breaks. Which are you?",
"speech.jaymoskingdoms.patron_champion.anvil_father.phase.2": "Hot slag for soft metal!",
"speech.jaymoskingdoms.patron_champion.anvil_father.phase.3": "Strike, then. My skin is his anvil.",
"speech.jaymoskingdoms.patron_champion.anvil_father.phase.4": "Up from the deep, all of you!",
"speech.jaymoskingdoms.patron_champion.anvil_father.fall": "A true blow. Take his iron, smith's child.",
"speech.jaymoskingdoms.patron_champion.anvil_father.hunt": "You turned from the Father. He sent me to test the break.",
"speech.jaymoskingdoms.patron_champion.anvil_father.hunted": "Brittle. As he said.",
"speech.jaymoskingdoms.patron_champion.deep_mother.wake": "The tide has come in for you.",
"speech.jaymoskingdoms.patron_champion.deep_mother.phase.2": "Breathe while you can.",
"speech.jaymoskingdoms.patron_champion.deep_mother.phase.3": "Every wave comes back!",
"speech.jaymoskingdoms.patron_champion.deep_mother.phase.4": "Rise, drowned ones. Rise!",
"speech.jaymoskingdoms.patron_champion.deep_mother.fall": "The sea gives back what it took.",
"speech.jaymoskingdoms.patron_champion.deep_mother.hunt": "You left her shore. The tide always finds the shore.",
"speech.jaymoskingdoms.patron_champion.deep_mother.hunted": "Sink, and remember the salt.",
"speech.jaymoskingdoms.patron_champion.green_huntsman.wake": "Prey that walks in on its own feet. How rare.",
"speech.jaymoskingdoms.patron_champion.green_huntsman.phase.2": "You won't see me again until it's too late.",
"speech.jaymoskingdoms.patron_champion.green_huntsman.phase.3": "Run! Run, I said!",
"speech.jaymoskingdoms.patron_champion.green_huntsman.phase.4": "Hounds! To me!",
"speech.jaymoskingdoms.patron_champion.green_huntsman.fall": "Good hunting. Dress me well.",
"speech.jaymoskingdoms.patron_champion.green_huntsman.hunt": "You left his wood. Now you're in it.",
"speech.jaymoskingdoms.patron_champion.green_huntsman.hunted": "Brought down. That's the way of the wild.",
"speech.jaymoskingdoms.patron_champion.lantern_warden.wake": "A light for the living, or a light for your grave?",
"speech.jaymoskingdoms.patron_champion.lantern_warden.phase.2": "A lantern for each of you.",
"speech.jaymoskingdoms.patron_champion.lantern_warden.phase.3": "Let the night come down.",
"speech.jaymoskingdoms.patron_champion.lantern_warden.phase.4": "Wisps, guide them out.",
"speech.jaymoskingdoms.patron_champion.lantern_warden.fall": "The lamp goes out. Keep yours lit.",
"speech.jaymoskingdoms.patron_champion.lantern_warden.hunt": "You walked out of his light. The dark followed you.",
"speech.jaymoskingdoms.patron_champion.lantern_warden.hunted": "Rest. The lamp will find you when you wake.",
"speech.jaymoskingdoms.patron_champion.crowned_sun.wake": "Kneel, or be judged in the sun's full light.",
"speech.jaymoskingdoms.patron_champion.crowned_sun.phase.2": "The crown is guarded. Strike me from behind, if you dare.",
"speech.jaymoskingdoms.patron_champion.crowned_sun.phase.3": "Behold noon!",
"speech.jaymoskingdoms.patron_champion.crowned_sun.phase.4": "To arms! The crown is assailed!",
"speech.jaymoskingdoms.patron_champion.crowned_sun.fall": "You have earned the sun's notice.",
"speech.jaymoskingdoms.patron_champion.crowned_sun.hunt": "Oath-breaker. The Sun does not forget a deserter.",
"speech.jaymoskingdoms.patron_champion.crowned_sun.hunted": "Justice is served.",
"speech.jaymoskingdoms.patron_champion.ash_whisperer.wake": "Shh. You weren't supposed to find this place.",
"speech.jaymoskingdoms.patron_champion.ash_whisperer.phase.2": "Catch!",
"speech.jaymoskingdoms.patron_champion.ash_whisperer.phase.3": "Can you see me now? No?",
"speech.jaymoskingdoms.patron_champion.ash_whisperer.phase.4": "Which of us is real, I wonder?",
"speech.jaymoskingdoms.patron_champion.ash_whisperer.fall": "Clever. Keep the secret.",
"speech.jaymoskingdoms.patron_champion.ash_whisperer.hunt": "You told her secrets to the light. I've come about that.",
"speech.jaymoskingdoms.patron_champion.ash_whisperer.hunted": "Nobody saw a thing.",
"speech.jaymoskingdoms.patron_champion.truth.wake": "You would raise a hand in the Truth's own house? Then be weighed.",
"speech.jaymoskingdoms.patron_champion.truth.phase.2": "Your charms are lies. I strip them.",
"speech.jaymoskingdoms.patron_champion.truth.phase.3": "Judgement comes. Stand together, or fall alone.",
"speech.jaymoskingdoms.patron_champion.truth.phase.4": "Faithful! Defend the house!",
"speech.jaymoskingdoms.patron_champion.truth.fall": "The Truth does not need me to stand.",
"speech.jaymoskingdoms.patron_champion.truth.hunt": "Apostate. The Truth sends one witness for you.",
"speech.jaymoskingdoms.patron_champion.truth.hunted": "Confess it, in the dark.",

"gui.jaymoskingdoms.story.hint.91": "Once you reach the Wildlands, rare temples to the patrons rise in the far places. Their champions are fought in groups; only the strongest fight one alone.",
"gui.jaymoskingdoms.story.hint.92": "A champion's weapon head falls for you by your third kill at the latest. Put it together at the Tool Station; it serves its patron's sworn best."
```
Speech keys are `speech.jaymoskingdoms.patron_champion.<patron key>.<line>`; `PatronChampion.speak(line)` builds them.

### 10.2 Powers (`src/main/resources/assets/jaymospowers/lang/parts/patron_weapons.json`, new)
```json
{
  "item.jaymospowers.hearthguard": "Hearthguard",
  "item.jaymospowers.anvil_fathers_hammer": "Anvil-Father's Hammer",
  "item.jaymospowers.tidecaller": "Tidecaller",
  "item.jaymospowers.huntsmans_spear": "Huntsman's Spear",
  "item.jaymospowers.gravelight": "Gravelight",
  "item.jaymospowers.sunbrand": "Sunbrand",
  "item.jaymospowers.ashfang": "Ashfang",
  "item.jaymospowers.plain_word": "The Plain Word",
  "item.jaymospowers.hearthguard_head": "Hearthguard Head",
  "item.jaymospowers.anvil_fathers_hammer_head": "Anvil-Father's Hammer Head",
  "item.jaymospowers.tidecaller_head": "Tidecaller Blade",
  "item.jaymospowers.huntsmans_spear_tip": "Huntsman's Spear Tip",
  "item.jaymospowers.gravelight_blade": "Gravelight Blade",
  "item.jaymospowers.sunbrand_blade": "Sunbrand Blade",
  "item.jaymospowers.ashfang_blade": "Ashfang Blade",
  "item.jaymospowers.plain_word_blade": "Plain Word Blade",
  "item.jaymospowers.patron_weapon.hearthguard": "Now and then a blow heals you and those near you",
  "item.jaymospowers.patron_weapon.hearthguard.sworn": "The Hearthkeeper's sworn: heals twice as much, farther",
  "item.jaymospowers.patron_weapon.anvil_fathers_hammer": "Each blow strips armour from the struck for a while (up to 3)",
  "item.jaymospowers.patron_weapon.anvil_fathers_hammer.sworn": "The Anvil-Father's sworn: up to 5",
  "item.jaymospowers.patron_weapon.tidecaller": "Use: drags foes near you in, slowed; farther in water or rain",
  "item.jaymospowers.patron_weapon.tidecaller.sworn": "The Deep Mother's sworn: farther, and sooner again",
  "item.jaymospowers.patron_weapon.huntsmans_spear": "Marks what it strikes: your blows on it land harder, beasts most",
  "item.jaymospowers.patron_weapon.huntsmans_spear.sworn": "The Green Huntsman's sworn: harder still",
  "item.jaymospowers.patron_weapon.gravelight": "Burns the undead it strikes, harder by night",
  "item.jaymospowers.patron_weapon.gravelight.sworn": "The Lantern Warden's sworn: harder, and each undead struck heals you",
  "item.jaymospowers.patron_weapon.sunbrand": "Use: calls the sun down on the foe you look at",
  "item.jaymospowers.patron_weapon.sunbrand.sworn": "The Crowned Sun's sworn: harder, and sooner again",
  "item.jaymospowers.patron_weapon.ashfang": "Use: a leap back into smoke, unseen; your next blow cuts deep",
  "item.jaymospowers.patron_weapon.ashfang.sworn": "The Ash Whisperer's sworn: unseen longer, and sooner again",
  "item.jaymospowers.patron_weapon.plain_word": "Each blow strips a blessing from the struck, and shows the unseen",
  "item.jaymospowers.patron_weapon.plain_word.sworn": "The Truth's sworn: two at a blow",
  "item.jaymospowers.patron_weapon.cold": "Cold in the hands of one who turned from its patron",
  "jei.jaymospowers.patron_head.hearthguard_head": "Taken from the Hearth-Warden, the Hearthkeeper's champion, in a hearth-hall. Sure to fall by your third kill. It can't be cast or forged: put it together at the Tool Station with a Tool Handle and a Tool Binding.",
  "jei.jaymospowers.patron_head.anvil_fathers_hammer_head": "Taken from the Forge-Thane, the Anvil-Father's champion, deep underground or in the Nether. Sure to fall by your third kill. Put it together at the Tool Station with a Tough Handle and a Tool Binding.",
  "jei.jaymospowers.patron_head.tidecaller_head": "Taken from the Tide-Bride, the Deep Mother's champion, at a temple by the sea. Sure to fall by your third kill. Put it together at the Tool Station with a Tool Handle and a Tool Binding.",
  "jei.jaymospowers.patron_head.huntsmans_spear_tip": "Taken from the Antlered Hunter, the Green Huntsman's champion, in a deep-forest glade. Sure to fall by your third kill. Put it together at the Tool Station with a Tool Handle and a Tool Binding.",
  "jei.jaymospowers.patron_head.gravelight_blade": "Taken from the Grave-Lamp, the Lantern Warden's champion, in a crypt or the Nether's soul valleys. Sure to fall by your third kill. Put it together at the Tool Station with a Tool Handle and a Tool Binding.",
  "jei.jaymospowers.patron_head.sunbrand_blade": "Taken from the Sun-Marshal, the Crowned Sun's champion, on high ground or in the Aether. Sure to fall by your third kill. Put it together at the Tool Station with a Crossguard and a Tool Handle.",
  "jei.jaymospowers.patron_head.ashfang_blade": "Taken from the Cinder-Shade, the Ash Whisperer's champion, in a hidden hollow or the Nether's deltas. Sure to fall by your third kill. Put it together at the Tool Station with a Tool Handle and a Tool Binding.",
  "jei.jaymospowers.patron_head.plain_word_blade": "Taken from the Inquisitor, the Truth's champion, in a Faith realm's great temple or a ruined cathedral. Sure to fall by your third kill. Put it together at the Tool Station with a Crossguard and a Tough Handle."
}
```
(The tooltip shows `.cold` instead of `.sworn` when `sworn(...) < 0` for the viewing player on the client. The client
reads its own `Patrons.STATE`, which is synced. `KingdomsCompat.blessing` works on the client too, because
`Patrons.blessing` and `Turning.trial` are client-safe.)

---

## 11. Versions, MODLOG, docs

- `kingdoms/gradle.properties`: `version=0.7.518`. `gradle.properties`: `version=0.20.18`. Kitchen unchanged.

`kingdoms/MODLOG.md`:
```
## 0.7.518: patron gods, part (c): temples, champions, the patrons' weapons (pack 0.20.18)
`docs/Patrons-C-Plan.md`. Andy: champions are group fights, "only the strongest can fight alone".
- **Temples** (`Temples`, `TempleSites`, `TempleBuildings`): once a player is in the Wildlands (or higher), now and then
  a patron's temple rises out of sight in a place that fits it: the Hearthkeeper's hearth-hall in open country, the
  Anvil-Father's forge-hall carved deep in the stone or in the Nether's wastes, the Deep Mother's tide-court on pillars by
  the sea, the Green Huntsman's glade in deep forest, the Lantern Warden's graveyard and crypt (or a vault in the soul
  valleys), the Crowned Sun's sun-court on high ground or in the Aether, the Ash Whisperer's hollow (hidden under dry
  country, or in the basalt deltas), and the Truth's ruined cathedral. A Faith-creed kingdom's capital builds the Truth's
  **great temple** (`GreatTemple`). Each altar is the patron's shrine; coming near teaches you the patron.
- **Champions** (`PatronChampion`, `PatronChampions`, `champions/`): each temple's champion sleeps at its altar as a
  statue and wakes for a Wildlands player (the great temple's only when struck). Group fights: health and blows grow
  with every player near (up to six) and with the world tier; a group of three or more brings a fourth phase. Three moves
  each, opened by the phases, all telegraphed: the Hearth-Warden, the Forge-Thane, the Tide-Bride, the Antlered Hunter,
  the Grave-Lamp, the Sun-Marshal, the Cinder-Shade and the Inquisitor. A boss bar, the posture bar, great-foe credit. It
  leashes to its temple, resets when everyone leaves, and stands again three days after it falls.
- **The patrons' weapons**: a fallen champion drops its patron's iron head for each fighter, at the latest on their
  third kill (34%, 50%, sure), Perfect (a solo kill's a Masterwork). Put together at the Tool Station (Powers 0.20.18).
- **The hunt** (part (b)'s mark): after the trials, the patron you left sends its champion once, in a weaker hunting
  form, outside any town. Kill it (the new patron is pleased) or fall to it, and the hunt is over; outrun it and it comes
  again.
- Loading hints 91-92.
- Not compile-checked yet at this entry; not played.
```
Root `MODLOG.md`:
```
## 0.20.18
- (Kingdoms 0.7.518) **The patrons' weapons** (`armory/PatronWeapons`): Hearthguard (blows heal you and those near),
  Anvil-Father's Hammer (blows strip armour), Tidecaller (use: drag foes in), Huntsman's Spear (marks prey), Gravelight
  (burns the undead), Sunbrand (use: the sun on a foe), Ashfang (use: smoke and a leap back, then a deep cut), The Plain
  Word (strips blessings, shows the unseen). Heads from Kingdoms' champions only (`WeaponParts.PATRONS`: never cast or
  forged), put together at the Tool Station, graded. Stronger for that patron's sworn (Kingdoms'
  `Patrons.blessing(player, "patron_weapon/<key>")` by reflection, `KingdomsCompat.blessing`), cold while you're in
  its trials.
```
Docs to touch when built:
- `docs/Otectus-Plan.md` row 11: "(c) built in Kingdoms 0.7.518 / Powers 0.20.18 (`docs/Patrons-C-Plan.md`)".
- `docs/Abilities.md`, new row 483: "Everyone (Powers) | right-click with a Tidecaller, Sunbrand or Ashfang | **Patron
  weapons** | (0.20.18) Undertow (drag foes in), Sun's Judgement (a beam on the foe you look at), Smokestep (a leap back
  into smoke, unseen). Stronger for the patron's sworn, cold while in its trials. The other five work on a hit. | |".

---

## 12. Build order and tracks (two at a time, own worktrees, no shared files)

- **Track A (Kingdoms framework, client, data):** `PatronChampion`, `PatronChampions`, `Temples`, `TempleSites`,
  `TempleBuildings`, `GreatTemple`, `champions/Move`, `champions/Moves` (with a stub `create` that track B fills), and
  `champions/MoveKit`. Also `JaymosKingdoms`, `BossBars`, `BossPosture` (one word), `WorldTiers` (one line), `Patrons`
  (`blessing`), `Spoils` (one word), `Buildings`, `Planner`, `Gatherers`, the client (`ChampionModel`,
  `ChampionRenderer`, `JaymosKingdomsClient`, `StoryHoldScreen`), **all Kingdoms lang**, the biome tags,
  `tools/gen_champions.py` and its output, `kingdoms/gradle.properties`, `kingdoms/MODLOG.md`.
- **Track B (the moves and Powers):** the eight `champions/*Moves.java` classes and `Moves.create`, coded against the
  `PatronChampion` public API and `Move`/`MoveKit` exactly as in sections 5.2-5.3. And all of Powers: `PatronWeapons`,
  `PatronWeaponItem`, `PatronWeaponHits`, `Armory`, `WeaponParts`, `KingdomsCompat`, `TinkeringJei`, `patron_weapons.json`,
  weapon attributes, the swords tag, `tools/weapon_parts_assets.py`, `tools/patron_weapons_assets.py` and their output,
  `gradle.properties`, root `MODLOG.md`. Merge B after A (B's `Moves.create` replaces A's stub, one file).
- Build `kingdoms/` and the root (`./gradlew build`). Check while building, against the 26.3 sources:
  `EntityType.Builder.fireImmune`, `PathType.WATER`, `BiomeTags` names (`IS_OCEAN`, `IS_BEACH`, `IS_FOREST`, `IS_TAIGA`,
  `IS_JUNGLE`), `Heightmap.Types.OCEAN_FLOOR`, `ItemStack.hurtAndBreak(int, LivingEntity, EquipmentSlot)`,
  `Codec.unboundedMap`, `StringArgumentType` suggestions, and `DataComponentType` lookup by id. Already looked up for this
  plan in 26.3: `ItemEntity.setTarget/setExtendedLifetime/setPickUpDelay`, `SmallFireball(Level, LivingEntity, Vec3)`,
  `Arrow`/`AbstractArrow.setBaseDamage`, `ProjectileUtil.getEntityHitResult`, `Entity.teleportTo(double, double, double)`,
  `Entity.igniteForTicks`, `LivingEntity.removeEffect`/`getActiveEffects`, `Mob.setDropChance`,
  `EntityDataSerializers.STRING/BOOLEAN/BYTE`, `BossEvent.BossBarColor`, and every sound and effect named above. No long
  dev runs.
- Two risks to watch: the great temple is the biggest town building (13 x 19, 15 high, the tower 20). If `Planner` can't
  find a plot that size it just isn't built, so check the plot search while building, and drop it to 11 x 17 if needed.
  Carving the deep and hidden halls must never open into a fluid (the `SOLID_SHARE` and fluid checks), or it floods.

### 12.1 The temples' plans (`TempleBuildings`, `GreatTemple`)
Every wild temple is built at once on its site with a `Frame(level, center, facing)` (local a/b/y like
`FortBuildings.Frame`). `groundwork(radius, fill, floor)` fills below and clears `CLEAR_HEIGHT` above. Carved halls use
`carve(box)`, which only replaces stone-like blocks. Each places the altar: `Healing.SHRINE.defaultBlockState().setValue(
Shrine.DEDICATION, Shrine.Dedication.of(patron))`. It returns `Layout(center, altar, championSpot = altar.relative(facing, 2), facing)`.
Containers are left empty (no loot tables: the head is the prize).
| Temple | `RADIUS` | Plan (the arena is the clear floor in the middle) |
|---|---|---|
| Hearth-hall | 9 | A 19 x 19 hall of stone bricks with spruce posts every 4, a spruce roof (stairs) at 7 and windows. A brick chimney at the back holds three campfires (`CampfireBlock.LIT` true) behind iron bars. Benches (spruce stairs) line the side walls. The altar stands before the hearth. Clear floor 13 x 13. |
| Forge-hall | 10 | Carved (or built on the Nether surface): 21 x 21, 9 high. Deepslate tile floor, polished deepslate pillars every 5, lava in channels behind iron bars along both side walls (lava only behind solid blocks and bars, never on the floor), anvils and blast furnaces along the back. The altar is set on a 3 x 3 iron block dais. In the Nether, the same in blackstone and nether bricks, roofed. |
| Tide-court | 10 | Prismarine-brick pillars from the sea floor to `seaLevel`, a 21 x 21 court at `seaLevel + 1` of prismarine bricks and dark prismarine, sea lanterns in the pillars' tops, and a 7 x 7 pool one deep in the middle (water source blocks). A dais at the back with the altar. Low walls (prismarine walls) at the edge, with gaps for the stairs. |
| Hunter's glade | 9 | A 19 x 19 clearing (leaves and logs cleared above, moss and coarse dirt floor). A ring of 12 mossy-log posts 4 high topped with bone blocks (antler mounts). At the back, a small log lodge (spruce logs, a hide awning of brown wool) over the altar. Hay bales and dead bushes. |
| Graveyard and crypt | 8 | Surface: a 17 x 17 yard with iron-bar fencing, mossy cobblestone headstones, and a stair down. Below at `CRYPT_DEPTH`: a 17 x 21 vault of stone bricks and polished deepslate, 7 high, soul lanterns on chains, dark oak slab "coffins" along the walls, the altar at the end. In the Nether: the vault only, built on the valley floor with no roof. |
| Sun-court | 10 | A 21 x 21 open terrace of smooth quartz with a border of cut sandstone, and a ring of 8 quartz pillars 5 high topped with glowstone. Gold blocks at the four corners only (four in all, to keep the loot sane). A raised 5 x 5 throne-dais in the east with the altar. No roof. |
| Ash-hollow | 8 | Hidden: a carved 17 x 17 hollow 7 high of blackstone and basalt, magma blocks under coal blocks in the floor's corners (no open fire), black banners, chain curtains before the altar. A 1 x 1 shaft with ladders up to the surface, its top under a dead bush. Nether: the same hollow walled and roofed on the delta floor. |
| Ruined cathedral | 8 | `GreatTemple.nave(setter, stone, true, random)`: the great temple's plan in stone bricks, with `RUIN_HOLES 0.25` of the upper wall blocks gone, `CRACKED 0.3`/`MOSSY 0.3` swaps, a fallen tower, and no roof over the nave's middle. |
| Great temple | (Blueprint) | `GreatTemple.draw(Kit k)`: `Sketch("great_temple", 13, 19, 20)`. A nave 11 inside, 15 high, from the realm's palette (walls, `p.planks()` pews), a bell tower over the doors (a `BELL`), an apse at the far end with the altar, glass windows, and a rose window frame. `GreatTemple.nave(...)` is shared with the ruin: a `Setter` lambda over `Sketch.set` or `Frame.set`. |

`Temples.GREAT_TEMPLE_FROM` (30) is the town size from which it is eligible (with the creed rule).

---

## 13. Open questions for Andy

1. **Champion kills count as great foes.** That is +1 foe toward the next world tier for everyone within 64, and Valor's
   great kill, because champions are in `BossBars.boss` like every other boss. Keep it? A hunting form counts too.
2. **The third-try guarantee is per player.** Each fighter rolls their own head (34%, 50%, sure), so a group of four can
   come away with up to four heads from one kill. Or one head per kill, to whoever has the most tries?
3. **Falling to the hunter ends the hunt** (it "has had its hunt"), and so does killing it. Only outrunning it brings it
   back. Harsh enough?
4. **Head grade:** Perfect, or a Masterwork for a solo kill. Fair reward for "only the strongest"?
5. **A champion stands again three in-game days after a kill.** OK?
6. **A patron weapon goes cold** (no ability) in the hands of someone under that patron's trials. Keep it?
7. **The Truth's great temple** is only in a Faith-creed kingdom's capital (not its fiefdoms), from 30 townsfolk. Its
   Inquisitor only wakes when struck. Should striking it also cost good name with that realm?
8. **Rarity:** a check every 3 minutes for each Wildlands player, a 12% chance, never within 800 blocks of another temple,
   and one patron's temples 2000 apart in a world. Rare enough, or too rare?
