# Quests to 270: Kingdoms 0.7.530 (pack 0.20.33)

Patch 9. Andy picked the idea of the MCA: Quests add-on and asked for "260+ ... get close". This is our own version
and uses none of its code or text.

Approved plan text: "We have about 188 quest templates now. Adds 75 or more across the trades, plus quests from what's
happening in town (food shortage, sickness, after a raid) and shared village projects that several players chip into
(a well, a wall, a bridge)."

Worktree `/home/user/quests` (HEAD 9bdf1d02b, Kingdoms 0.7.523, Powers 0.20.25). All code is in `kingdoms/` (package
`com.jaymoskingdoms`). Paths are relative to `kingdoms/src/main/java/com/jaymoskingdoms/` unless marked. Tag every
new code comment `(0.7.530)`. Tuning numbers are `static final` constants at the top of each class. Check every
Minecraft API, item id, entity id and tag against the 26.3 sources (`./gradlew genSources`) before using it.

## 0. The count

Counted in `lang/parts/quest*.json` at HEAD:

| | Count |
|---|---|
| Unique `quest.jaymoskingdoms.<template>.title` keys | **189** (quest.json 106, quest-town.json 48, quest-king.json 19, quest-beast.json 16) |
| Of those, board notices that are not quests (`town.help`, `town.tidings`) | 2 |
| Quest templates | **187** |

This patch adds **83 quest templates**: 55 trade quests and 28 town-trouble quests. That makes **272 titles and 270
quest templates**. The plan asks for at least 75. That leaves 10 spare above 260 if review cuts some. Shared village
projects (section 4) are not quest templates and are not in that count. There are 6 of them.

| Family | New | Of which |
|---|---|---|
| Trade quests (`QuestBoard.TRADES`) | 55 | 49 across the 20 existing trade keys, 6 in 3 new rows (`cook` 3, `puller` 2, `changer` 1) |
| Town troubles (`TownTroubles`) | 28 | famine 4, plague 3, wounds 2, raid 5, fire 3, war 3, drought 3, missing 2, feud 3 |
| **Total** | **83** | 189 + 83 = **272 titles** |

**No new objective kinds.** Every new template uses an existing kind: GATHER, SLAY, FETCH, LEAD, RELIEF, CHIEF,
RESCUE or DELIVER. `Quests.title/text/objective` need no new branches. The one helper that changes is `QuestBoard.make`,
which gets a pay case for LEAD.

## 1. What the player sees

- **More work from every trade.** Every trade has 2 or 3 more quests: farmers, butchers, shepherds (tailors),
  leatherworkers, fishermen (with whale oil from the whalers), fletchers, the three smiths, librarians, clerics
  (healers), cartographers, masons, builders, miners, woodcutters, guards (and archers, mercenaries and adventurers),
  stablehands, farriers and the jobless. Kingdoms' cooks, pullers and money-changers now give quests of their own. Until
  now they asked only the jobless quests. Some quests ask you to lead animals home on a lead (cows, pigs, sheep).
- **Trouble in town.** While a town is hungry, sick, burned, raided, sacked, in drought, missing one of its own, or
  feuding with a neighbour, 2 to 4 of its villagers wear the gold **!** and ask for help with that trouble. Players near
  the town hear when trouble starts and when it ends. When it ends, any unaccepted asks are taken back. Quests already
  taken stay in your log and pay as normal. Food, wheat, timber and iron you bring for a trouble go into the town's
  stores, so a famine can really end.
- **Town works.** Each town board gets a **Town works** sheet that stays up, like the merchant orders sheet. Pull it
  (use the pinned sheet) to see the town's shared projects: a well, a granary, a wall, a watchtower, a bridge and an
  infirmary. Each lists the goods and coin it still needs. **Give goods** hands over everything you carry that it
  needs. **Give coin** and **Give 5 coins** pay in the town's coin. Anyone can give. Progress is shared and saved per
  town. When a project is finished, the town really gets something (section 4.4), the news goes round, and everyone
  who gave is paid coin and reputation by their share. A contributor who is offline is paid when they next join.
- No new player commands. The op test commands go under the existing op-only `/kingdom quests` (section 6).

## 2. Files

New:
| File | What it does |
|---|---|
| `TownTroubles.java` | Town situations. Reads the existing state, starts and ends troubles, offers 2 to 4 of each trouble's quests through `Quests.offerNow`, takes them back on clearing, and puts the goods handed in into the town's stores. Has the `TROUBLES` and `DRY` attachments. |
| `TownProjects.java` | Shared village projects. Has the project table, the `PROJECTS` attachment, the Town works sheet key, the screen views and actions, contributions, completion (structure, bonus, payout) and owed pay on join. It also has the bonus reads that the other classes call. |
| `assets/jaymoskingdoms/lang/parts/quests-more.json` (under `kingdoms/src/main/resources/`) | Every new lang key in this patch (section 7). |

Changed:
| File | Change |
|---|---|
| `QuestBoard.java` | 49 new `T` entries in the existing trades, 3 new `TRADES` rows (`cook`, `puller`, `changer`), a `T.lead` helper, and a LEAD pay case in `make`. |
| `Quests.java` | `withdraw(giver, prefix)` helper. `act()` branch for `QuestNet.PROJECTS`. `turnIn()` GATHER branch calls `TownTroubles.handedIn`. |
| `QuestNet.java` | `PROJECTS = -9`. Actions `GIVE_GOODS = 4`, `GIVE_COIN = 5`, `GIVE_COINS = 6`. No packet shape change. |
| `client/QuestScreen.java` (under `src/client/java/com/jaymoskingdoms/`) | Projects mode: `projects()`, its buttons and row labels. |
| `client/StoryHoldScreen.java` | `HINTS = 95` becomes `HINTS = 97`. |
| `Bounties.java` | Posts the Town works sheet. `pull()` opens it. Treats the sheet's key like the orders sheet in `setNotice`, the stale-notice pass, the post loop's town-notice count, and `errands`. |
| `Commissions.java` | `give(server, f, entryId)`. `next()` falls back to the town's catalogue entry for an id that isn't a design. |
| `Raids.java` | `end()` calls `TownTroubles.event`. The raid chance is multiplied by `TownProjects.raidFactor`. `start()` calls `TownProjects.watch`. |
| `nether/PortalRaids.java` | A successful `start()` calls `TownTroubles.event` (portal raid, no camp). |
| `WarParties.java` | A sack calls `TownTroubles.event` (war). |
| `Firefighting.java` | `lit()` keeps the town it found and calls `TownTroubles.fire`. |
| `Captives.java` | `take()` calls `TownTroubles.missing`. `arrive()` calls `TownTroubles.found`. |
| `Economy.java` | `hasFood` reads the granary bonus. |
| `Roads.java` | `wanted()` adds `TownProjects.extraLinks(server, f)` to `LINKS`. |
| `WalledTowns.java` | `fund(server, f, add)`: adds to a village's purse. |
| `QuestCommands.java` | Op-only `trouble <kind>`, `trouble clear`, `works fill <project>`. |
| `JaymosKingdoms.java` | Registers `TownTroubles` and `TownProjects` next to `Quests.register()` (line 471), and logs their attachments in the attachment debug block so they class-load on both sides. |
| `kingdoms/gradle.properties`, `gradle.properties` | 0.7.523 becomes 0.7.530. 0.20.25 becomes 0.20.33. |
| `kingdoms/MODLOG.md`, `MODLOG.md` | Section 9. |
| `docs/codemap/kingdoms.md` | Regenerate with `tools/codemap.py`. |

Not touched: `Promises.java` and the talks. Situation and project quests use no `Promises.Kind` and call nothing in
`Promises`.

No new items, blocks, recipes, data components or textures. The sheet is the existing `TownNotices.ITEM` paper with
`Bounties.NOTICE_KEY` custom data, as `MerchantOrders.sheet` makes it. On the board it shows the existing `TOWN` notice
face (`BountyBoard.TOWN`).

## 3. Trade quests (`QuestBoard.java`)

### 3.1 Code

- Add next to `T.slay/gather/fetch` (line 92):
  ```java
  /** (0.7.530) Bring so many of an animal to the giver on a lead (a town notice's errand, now a trade's too). */
  static T lead(String key, String type, int min, int max) {
  	return new T(key, Quest.LEAD, type, min, max, List.of(), "");
  }
  ```
- `make()` (line 377) already builds a non-FETCH objective as `(target, count)`, and LEAD already works end to end:
  `Quests.ready` (`led`), `turnIn` (leash off, persistent), `text` (count, `beastName`), `objective` (`objective.lead`),
  and `finishAll` skips it. Add a pay case to the emerald switch at line 396:
  `case Quest.LEAD -> LEAD_EMERALDS + random.nextInt(LEAD_SPREAD + 1);`. Add a hearts bonus the way FETCH and SLAY
  have one: `t.kind() == Quest.LEAD ? LEAD_HEARTS : ...`.
- New constants, next to the others at the top:
  `static final int LEAD_EMERALDS = 6, LEAD_SPREAD = 4, LEAD_HEARTS = 4; // (0.7.530) a trade's lead quest pays`
- GATHER targets that name another mod's item (`jaymospowers:*`) or one of our own optional ones are already skipped
  when missing (`make` line 389), so they degrade safely. The new `pays` lists are filtered the same way (`reward`
  line 445).
- The 3 new rows are job paths that `Professions` already registers (`cook` line 56, `puller` line 45, `changer` line
  58). `job()` maps a path to its trade only when `TRADES.containsKey(path)`, so adding the rows is all it takes.
  Today these three fall to `none`.
- Kingdoms' healers are vanilla clerics and its tailors are shepherds (`Directions.java:75-78`), so their quests go in
  the `cleric` and `shepherd` rows. Whalers are ship crews, not standing givers, so the whaling quests go to the harbour
  trades (fisherman, leatherworker).

### 3.2 The 55 new entries

Kinds: G = `T.gather`, S = `T.slay`, F = `T.fetch`, L = `T.lead`. Counts are min-max.

| Trade | Key | Kind | Target | Count / where |
|---|---|---|---|---|
| farmer | `farmer.pumpkins` | G | `minecraft:pumpkin` | 6-12 |
| farmer | `farmer.bonemeal` | G | `minecraft:bone_meal` | 16-32 |
| farmer | `farmer.cows` | L | `minecraft:cow` | 2-3 |
| butcher | `butcher.pigs` | L | `minecraft:pig` | 2-3 |
| butcher | `butcher.foxes` | S | `minecraft:fox` | 3-5 |
| shepherd | `shepherd.sheep` | L | `minecraft:sheep` | 2-4 |
| shepherd | `shepherd.dye` | G | `minecraft:red_dye` | 8-16 |
| shepherd | `shepherd.cloak` | F | `minecraft:white_wool\|weavers_shuttle` | FIELDS |
| leatherworker | `leatherworker.scutes` | G | `minecraft:armadillo_scute` | 3-6 |
| leatherworker | `leatherworker.blubber` | G | `jaymoskingdoms:whale_blubber` | 4-8 |
| fisherman | `fisherman.whale_oil` | G | `jaymoskingdoms:whale_oil` | 2-4 |
| fisherman | `fisherman.guardians` | S | `minecraft:guardian` | 3-5 |
| fisherman | `fisherman.puffers` | G | `minecraft:pufferfish` | 3-6 |
| fletcher | `fletcher.string` | G | `minecraft:string` | 12-24 |
| fletcher | `fletcher.quiver` | F | `minecraft:arrow\|fletchers_quiver` | WAR |
| armorer | `armorer.gold` | G | `minecraft:gold_ingot` | 6-12 |
| armorer | `armorer.leather` | G | `minecraft:leather` | 8-16 |
| weaponsmith | `weaponsmith.diamonds` | G | `minecraft:diamond` | 2-3 |
| weaponsmith | `weaponsmith.skeletons` | S | `#minecraft:skeletons` | 8-12 |
| toolsmith | `toolsmith.copper` | G | `minecraft:copper_ingot` | 12-24 |
| toolsmith | `toolsmith.sticks` | G | `minecraft:stick` | 24-48 |
| librarian | `librarian.leather` | G | `minecraft:leather` | 6-12 |
| librarian | `librarian.glow` | G | `minecraft:glow_ink_sac` | 4-8 |
| librarian | `librarian.scroll` | F | `minecraft:paper\|sealed_scroll` | LORE |
| cleric | `cleric.bandages` | G | `jaymospowers:linen_bandage` | 3-6 |
| cleric | `cleric.garlic` | G | `jaymoskingdoms:garlic` | 6-12 |
| cleric | `cleric.bleeders` | S | `jaymoskingdoms:night_bleeder` | 2-4 |
| cartographer | `cartographer.paper` | G | `minecraft:paper` | 12-24 |
| cartographer | `cartographer.amethyst` | G | `minecraft:amethyst_shard` | 4-8 |
| cartographer | `cartographer.log` | F | `minecraft:writable_book\|ships_log` | SEA |
| mason | `mason.andesite` | G | `minecraft:andesite` | 32-64 |
| mason | `mason.bricks` | G | `minecraft:brick` | 16-32 |
| builder | `builder.glass` | G | `minecraft:glass` | 16-32 |
| builder | `builder.planks` | G | `minecraft:oak_planks` | 32-64 |
| builder | `builder.creepers` | S | `minecraft:creeper` | 3-5 |
| miner | `miner.coal` | G | `minecraft:coal` | 16-32 |
| miner | `miner.ladders` | G | `minecraft:ladder` | 12-24 |
| woodcutter | `woodcutter.logs` | G | `minecraft:spruce_log` | 16-32 |
| woodcutter | `woodcutter.zombies` | S | `#minecraft:zombies` | 6-10 |
| woodcutter | `woodcutter.charcoal` | G | `minecraft:charcoal` | 12-24 |
| guard | `guard.creepers` | S | `minecraft:creeper` | 4-6 |
| guard | `guard.spiders` | S | `minecraft:spider` | 6-10 |
| guard | `guard.arrows` | G | `minecraft:arrow` | 32-64 |
| stablehand | `stablehand.hay` | G | `minecraft:hay_block` | 6-12 |
| stablehand | `stablehand.apples` | G | `minecraft:apple` | 8-16 |
| farrier | `farrier.nuggets` | G | `minecraft:iron_nugget` | 18-36 |
| farrier | `farrier.horseshoe` | F | `minecraft:iron_ingot\|lucky_horseshoe` | FIELDS |
| none | `none.mushrooms` | G | `minecraft:brown_mushroom` | 6-12 |
| none | `none.eggs` | G | `minecraft:egg` | 8-16 |
| **cook** (new) | `cook.sugar` | G | `minecraft:sugar` | 12-24 |
| cook | `cook.milk` | G | `minecraft:milk_bucket` | 2-3 |
| cook | `cook.potatoes` | G | `minecraft:potato` | 16-32 |
| **puller** (new) | `puller.leads` | G | `minecraft:lead` | 2-4 |
| puller | `puller.bread` | G | `minecraft:bread` | 8-16 |
| **changer** (new) | `changer.emeralds` | G | `minecraft:emerald` | 4-8 |

New rows' `pays`:
- `cook`: `minecraft:bread*8-16`, `minecraft:pumpkin_pie*4-8`, `minecraft:cake*1-1`, `minecraft:cookie*12-24`,
  `jaymoskitchen:beef_jerky*6-12`
- `puller`: `minecraft:leather*6-12`, `minecraft:lead*1-2`, `minecraft:bread*6-12`, `minecraft:saddle*1-1`
- `changer`: `minecraft:gold_nugget*8-16`, `minecraft:gold_ingot*1-3`, `jaymoskingdoms:copper_coin*10-20`

Before using them, check that these ids exist in 26.3: `armadillo_scute`, `glow_ink_sac`, `pufferfish`, `guardian`,
`fox`, and the entity tags `#minecraft:skeletons` and `#minecraft:zombies` (already in use). Also check our own ids:
`jaymoskingdoms:night_bleeder` (`Nightfall.java:115`), `jaymoskingdoms:hornkin` (`Nether.java:44`),
`jaymoskingdoms:garlic`, `jaymoskingdoms:whale_oil` and `whale_blubber` (`Whaling.java:78,94`), and
`jaymospowers:linen_bandage` and `splint` (Powers' `MedicineItems`). `cook.milk` and the drought water quests take
the whole bucket, the way `take()` already takes any GATHER item. That's fine: the giver keeps the bucket.

Five new keepsakes (FETCH `otherName` = `item|key`): `weavers_shuttle`, `fletchers_quiver`, `sealed_scroll`, `ships_log`,
`lucky_horseshoe`. Four new foes names (`quest.jaymoskingdoms.foes.<path>`, the path after `:`): `fox`, `guardian`,
`night_bleeder`, `hornkin`. The others the new SLAY quests use (`creeper`, `spider`, `skeletons`, `zombies`) exist.

## 4. Shared village projects (`TownProjects.java`)

### 4.1 The table

One `record Project(String id, List<Need> needs, int coin, int bonus, String builds)`. `Need(String what, int count,
int points)`: `what` is an item id or `#tag` (an item tag), and `points` is the share points for each one given.
`coin` is in emeralds' worth (`Money.worth`). `bonus` is the extra coin the town pays out, in emeralds' worth. `builds`
is a town catalogue entry id that its builders put up, or `""`.

| id | Needs (what × count, points each) | Coin | Bonus | Builds | Lasting effect |
|---|---|---|---|---|---|
| `well` | `#minecraft:stone_crafting_materials` × 96 (1), `minecraft:bucket` × 2 (8) | 10 | 6 | `well` | No drought trouble here. The plague needs `WELL_SICK_MORE` (1) more sick to start. |
| `granary` | `minecraft:hay_block` × 24 (4), `#minecraft:logs` × 48 (1) | 15 | 8 | `""` | `Economy.hasFood` needs `FOOD_RESERVE × GRANARY_RESERVE` (0.5). Famine starts below `FAMINE_FOOD × GRANARY_RESERVE`. `GRANARY_FOOD` (48) bread goes into its stores on completion. |
| `wall` | `#minecraft:stone_crafting_materials` × 256 (1), `#minecraft:logs` × 64 (1), `minecraft:iron_ingot` × 8 (6) | 30 | 12 | `""` | Raid chance on this town × `WALL_RAID` (0.75). A village (`WalledTowns`) gets `WALL_PURSE` (60) into its purse, which pays for its next wall tier. |
| `watchtower` | `#minecraft:logs` × 64 (1), `#minecraft:stone_crafting_materials` × 64 (1), `minecraft:torch` × 32 (1), `minecraft:spyglass` × 1 (20) | 20 | 10 | `""` | Raid chance × `TOWER_RAID` (0.85). When a raid starts on the town, players within `TOWER_WARN_RANGE` (200) are warned with the muster point. |
| `bridge` | `#minecraft:planks` × 128 (1), `#minecraft:logs` × 32 (1), `minecraft:lantern` × 4 (6) | 25 | 10 | `""` | The town gets `BRIDGE_LINKS` (1) more road link (`Roads.wanted`). The existing road machinery lays it, with plank bridges over water, and `Roads.hurry(server)` is called on completion. |
| `infirmary` | `#minecraft:wool` × 16 (2), `#minecraft:planks` × 64 (1), `minecraft:glass` × 16 (1) | 20 | 8 | `infirmary` | The plague needs `INFIRMARY_SICK_MORE` (2) more sick to start, and wounds need `INFIRMARY_WOUNDED_MORE` (2) more. |

Check that these item tags exist in 26.3 (`ItemTags`): `stone_crafting_materials`, `logs`, `planks`, `wool`. If one has
been renamed, use the new name. Do not fall back to a single item.

Every non-barbarian town that hasn't fallen has all six. A finished project stays finished and shows green on the
sheet. The bridge is listed only when `TownProjects.bridgeable(server, f)` is true: some town within
`Roads.LINK_RANGE` isn't already one of its `LINKS` nearest. Otherwise the extra link would do nothing.

### 4.2 Saved state

```java
/** (0.7.530) One project's progress in one town: given per need (by index), coin given (milli), share points by player, their names, the day it was finished (-1: open), and whether its building has been handed to the builders. */
record Progress(List<Integer> given, long coin, Map<String, Integer> points, Map<String, String> names, long done, boolean queued)
/** (0.7.530) A town's works: each project's progress by id, and what's owed to contributors who weren't on (by UUID string). */
record Works(Map<String, Progress> projects, Map<String, Owed> owed)
record Owed(long milli, int reputation)
/** (0.7.530) Faction id → its works. Kept on the Overworld. */
static final AttachmentType<Map<String, Works>> PROJECTS = AttachmentRegistry.create(JaymosKingdoms.id("town_projects"),
	builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Works.CODEC)));
```
Codecs use `optionalFieldOf` with defaults (lists and maps empty, `done` -1, `queued` false), as `Commissions.Commission` does. Read
and write the way `Commissions.of` and `put` do, on `Factions.level(server)`, with copies. When a town has fallen, its
entry is dropped on the next check.

### 4.3 The sheet and the screen

- **Sheet key** `works|<anchor long>` (`SHEET = "works|"`), with `is(key)`, `sheetKey(anchor)`, `boardOf(key)` and
  `sheet(key)`. These mirror `MerchantOrders` lines 196-224. The sheet's name is `project.jaymoskingdoms.sheet`.
- **Posting** (`Bounties.post`, after the orders sheet block at lines 1318-1328): when the orders sheet is already up
  (`up.contains(sheet)`) and the works sheet isn't (`!up.contains(works)`), then with chance `SHEET_CHANCE` (0.5) a
  `poster` clerk carries `TownProjects.sheet(works)` to a bare spot. Use the same `Errand` and `continue` as the orders
  sheet. The call-out is `talk.jaymoskingdoms.works.posting.<0..2>`.
- **Bounties checks:** wherever the code treats `MerchantOrders.is(key)` as "the board's own sheet, stays up", treat
  `TownProjects.is(key)` the same way: `setNotice` (line 699, `TOWN` face), the stale-notice pass (line 1272, over only
  when it's on another board), the town-notice count in the post loop (line 1308, don't count it), `errands` (lines
  1370-1374: `over` is false, the hand item is `TownNotices.ITEM`), and the call-out at line 1392. In `pull()` (after
  the orders branch at line 732): `if (key != null && TownProjects.is(key)) { TownProjects.open(level, player,
  BountyBoard.anchor(pos, state)); page sound; return; }`.
- **The town** is `Factions.around(server, level, anchor, Couriers.REALM_RANGE)`, as `MerchantOrders.town` finds it.
- **`open(level, player, anchor)`:** `READING.put(uuid, anchor)`. If there's no town, send
  `project.jaymoskingdoms.no_town`. If `Reputation.get(player, f.id()) <= Reputation.HOSTILE`, send
  `project.jaymoskingdoms.refused`. Otherwise pay what's owed to this player in any town (4.5). Then send
  `QuestNet.Open(QuestNet.PROJECTS, title "project.jaymoskingdoms.screen" (town name), views, note
  "project.jaymoskingdoms.none")`. Open projects come first, most nearly finished first, then finished ones.
- **One `QuestNet.View` per project** (`id` = project id):
  - `state`: `OFFER` while open, `READY` once done. `royal`: false.
  - `title`: `project.jaymoskingdoms.<id>.title` plus the percent done
    (`project.jaymoskingdoms.title`, "%s (%s%%)").
  - `text`: `project.jaymoskingdoms.<id>.text`, or `.done` (town name) once finished.
  - `objective`: the needs joined with `" · "`. Each need is `project.jaymoskingdoms.need` (name, given, count): the
    item's hover name, or for a tag the key `project.jaymoskingdoms.tag.<tag path>`. Then
    `project.jaymoskingdoms.coin` (`Money.text(f, given)`, `Money.text(f, total)`).
  - `rewards`: one icon `ItemStack` for each unmet need (count = remaining, capped at the item's max stack). For a tag
    the icon is the tag's first item (`BuiltInRegistries.ITEM.getTagOrEmpty`). The first coin stack of
    `Money.stacks(f, remaining milli)` is added if coin is still owed.
  - `extras`: `project.jaymoskingdoms.gives` (`.<id>.gives`), `project.jaymoskingdoms.share` (your percent, if you've
    given), and `project.jaymoskingdoms.givers` (how many have given).
- **`act(player, act)`** (from `Quests.act`, a new branch beside BOARD and ORDERS: `if (act.entityId() ==
  QuestNet.PROJECTS) { TownProjects.act(player, act); return; }`). CLOSE removes `READING`. Otherwise it needs
  `READING` and the player within `READ_RANGE` (8) of the anchor. The project must be open. Then:
  - `GIVE_GOODS`: see 4.4.
  - `GIVE_COIN` / `GIVE_COINS`: `n` = `COIN_SMALL` (1) or `COIN_BIG` (5) emeralds' worth, capped at what's owed.
    `Money.charge(player, f, milli)`. If that fails, say `project.jaymoskingdoms.poor` in red. On success, add to
    `coin` and add `n * COIN_POINTS` (10) points.
  - Then `complete()` if it's done, and re-open the screen.
- **Screen** (`QuestScreen.java`): `projects()` is `open.entityId() == QuestNet.PROJECTS`. In `init()`, a new first
  branch for `v != null && projects()`. While `v.state() == QuestNet.OFFER`, show three buttons along the bottom:
  `project.jaymoskingdoms.button.goods` (x+6, 90 wide, `GIVE_GOODS`), `project.jaymoskingdoms.button.coin` (x+100,
  70 wide, `GIVE_COIN`) and `project.jaymoskingdoms.button.coins` (x+174, 70 wide, `GIVE_COINS`). Done stays at
  `PANEL_W - 66`, so the four fit in the 320 panel. A finished project shows no buttons. `label()`: projects use
  `project.jaymoskingdoms.label.open` or `.label.done`. `act()` and `lastShown` work as they are.

### 4.4 Giving goods, and finishing

- **Giving goods** goes through each unmet need in turn and walks the player's inventory (`getInventory()`, main and
  offhand). It skips stacks that have `CUSTOM_DATA` (quest letters, keepsakes, notices) or `CUSTOM_NAME`. A stack
  matches a need by `stack.is(item)` or `stack.is(TagKey.create(Registries.ITEM, id))`. Take `min(matching,
  remaining)`. Then:
  - If the town has storage (`!f.holdings().storage().isEmpty()`), put what was taken in with `Storage.put(level, f,
    stack)`. Any leftover goes back to the player (`Quests.give`, or `player.getInventory().placeItemBackInInventory`)
    and doesn't count. If nothing fit, say `project.jaymoskingdoms.store_full`.
  - If the town has no storage, the goods are used for the work. They are counted and consumed.
  - Each item counted adds `need.points` share points to the player. Their name is kept in `names`.
  - With nothing taken, say `project.jaymoskingdoms.nothing`. Otherwise say `project.jaymoskingdoms.given` (count,
    project title) and play `VILLAGER_WORK_MASON`.
- **Finished** when every need is met and `coin >= Money.worth(project.coin)`. `complete(level, f, id)`:
  1. `done = Quests.day(level)`.
  2. **The structure**, when `builds` isn't empty: `Commissions.give(server, f, builds)`. This adds a `Commission(builds,
     Util.NIL_UUID, "", baseline = Works.of(server, f.id()).count(builds) + waiting of the same id)`. If
     `Blueprint.catalogue(f.type())` has no entry with that id, skip it and log at debug. The town's builders then
     put it up before anything else (`Planner.eligible`, line 160). `Commissions.finish` can't find a player for the
     nil UUID, so it thanks nobody, and `TownProjects` pays the contributors itself. `MAX_WAITING` still applies:
     `give` returns false when the list is full or the catalogue has no such entry. While it returns false because the
     list is full, `Progress.queued` stays false and the `CHECK_TICKS` (200) tick tries `give` again. Once it returns
     true, or the entry doesn't exist, `queued` is set to true.
  3. **The effect** (section 4.6): `granary` puts `GRANARY_FOOD` bread into storage with `Storage.put` (any leftover is
     dropped silently). `wall` calls `WalledTowns.fund(server, f, WALL_PURSE)`. `bridge` calls `Roads.hurry(server)`.
  4. **News**: `project.jaymoskingdoms.complete` (town, project title, number of contributors). It goes realm-wide with
     `KingdomEvents.announce` when `f.type().isRealm()`, else to players within `LOCAL_NEWS` (300) of the seat, as
     `DisasterRelief.local` does.
  5. **Payout**: `purse = coin × PAYBACK (0.25) + Money.worth(project.bonus)`, in milli. The rest of the coin
     (`coin - coin × PAYBACK`) goes to the treasury as worth: `Factions.update(..., withTreasury(treasury +
     rest / Money.worth(1)))`. Each contributor's share is their points over all points. They get `round(purse ×
     share)` milli and `REP_LEAST (10) + round(REP_POOL (90) × share)` reputation. An online player is paid now with
     `Money.pay(player, f, milli)` and `Reputation.change(player, f, rep)`, and told `project.jaymoskingdoms.paid`.
     Otherwise it's added to `owed`.

### 4.4a `Commissions` changes

```java
/** (0.7.530) A town's own shared project ({@link TownProjects}) asks its builders for catalogue building {@code entryId}: queued like a plan given, thanked by nobody. False if its list is full or it has no such building. */
static boolean give(MinecraftServer server, Faction f, String entryId)
```
In `next()` (line 119), change `Blueprint.Entry e = Designs.entry(c.design());` to
`Designs.isDesign(c.design()) ? Designs.entry(c.design()) : catalogueEntry(f, c.design())`. The new private
`catalogueEntry` returns the first `Blueprint.catalogue(f.type())` entry whose `id()` equals the id, or null.
`finish()` and `label()` work as they are for a catalogue id. The nil UUID is `net.minecraft.util.Util.NIL_UUID`.
Check its package in 26.3, and use `new UUID(0L, 0L)` if it isn't there.

### 4.5 Owed pay

On `ServerPlayConnectionEvents.JOIN`, and when the player opens any works sheet, every town's `owed` entry for
their UUID is paid and removed: `Money.pay`, `Reputation.change`, and `project.jaymoskingdoms.paid_late` (town, coin
text). If the town has fallen, the coin is still paid, in realm silver (`Money.pay(player, null, milli)`), with no
reputation.

### 4.6 Bonus reads (all in `TownProjects`, each one map lookup)

```java
static boolean done(MinecraftServer server, int faction, String id)
/** Raids: the chance on target, times WALL_RAID and/or TOWER_RAID (1 for null or none). */
static float raidFactor(MinecraftServer server, @Nullable Faction target)
/** Economy: the food a town wants in store (FOOD_RESERVE, or times GRANARY_RESERVE with a granary). */
static int foodReserve(MinecraftServer server, Faction f)
/** Roads: links on top of Roads.LINKS (BRIDGE_LINKS with a bridge). */
static int extraLinks(MinecraftServer server, Faction f)
/** Raids.start: the watchtower's warning to players near target. */
static void watch(ServerLevel level, Faction target, BlockPos muster)
/** TownTroubles: how many more sick or wounded it takes here (well, infirmary). */
static int sickMore(MinecraftServer server, Faction f), woundedMore(MinecraftServer server, Faction f)
```
Hooks:
- `Economy.hasFood` (line 86): `Storage.food(level, faction) >= TownProjects.foodReserve(level.getServer(), faction)`.
  This is the "more food" bonus: a granary town grows and counts as fed on half the reserve.
- `Raids.consider` (lines 218-219): the non-warpath `chance` is multiplied by `TownProjects.raidFactor(server,
  target)`. A warpath target keeps `HordeCity.RAID_CHANCE`, because a horde on the warpath isn't put off by a wall.
- `Raids.start` (after `RAIDED.put`, line 299): `TownProjects.watch(level, target, muster)`. It does nothing without
  a watchtower. With one, players within `TOWER_WARN_RANGE` of the seat get `project.jaymoskingdoms.watchtower.warn`
  (town, muster x, z) in gold, and the `BELL_RESONATE` sound.
- `Roads.wanted` (line 323): `.limit(LINKS + TownProjects.extraLinks(server, f))`.
- `WalledTowns.fund(MinecraftServer server, Faction f, long add)`: new, package-private. It does nothing unless
  `f.type() == FactionType.VILLAGE`. Otherwise `put(server, f.id(), town(server, f.id()).withPurse(purse + add))`.
- `TownTroubles` (round 2 adds these reads): the famine threshold `× GRANARY_RESERVE`, `PLAGUE_SICK +
  sickMore`, `WOUNDED_AT + woundedMore`, and no drought where `done(..., "well")`.

### 4.7 `TownProjects` constants

```java
static final float SHEET_CHANCE = 0.5F;
static final double READ_RANGE = 8.0;
static final int CHECK_TICKS = 200;
static final int COIN_SMALL = 1, COIN_BIG = 5, COIN_POINTS = 10;
static final float PAYBACK = 0.25F;
static final int REP_LEAST = 10, REP_POOL = 90;
static final double LOCAL_NEWS = 300.0;
static final float WALL_RAID = 0.75F, TOWER_RAID = 0.85F;
static final double TOWER_WARN_RANGE = 200.0;
static final float GRANARY_RESERVE = 0.5F;
static final int GRANARY_FOOD = 48;
static final int WALL_PURSE = 60;
static final int BRIDGE_LINKS = 1;
static final int WELL_SICK_MORE = 1, INFIRMARY_SICK_MORE = 2, INFIRMARY_WOUNDED_MORE = 2;
```

## 5. Town troubles (`TownTroubles.java`)

This follows the `DisasterRelief` model: a hook, a few villagers each offered one quest with `Quests.offerNow`, and
existing progress paths.

### 5.1 Kinds and what they read

| Kind | Starts when | Ends when | Read from (existing) |
|---|---|---|---|
| `famine` | The town has storage, all loaded (`Storage.loaded`), at least `FAMINE_FOLK` (6) villagers within `Economy.POPULATION_RANGE`, and `Storage.food < FAMINE_FOOD` (16) | `Storage.food >= TownProjects.foodReserve` (32, or 16 with a granary) | `Storage.food`, `Storage.loaded`, `Economy.FOOD_RESERVE` |
| `plague` | At least `PLAGUE_SICK` (2) grown villagers within `POPULATION_RANGE` of the seat `hasEffect(Nightfall.ATROPHY)` | None of them sick | `Nightfall.ATROPHY` |
| `wounds` | At least `WOUNDED_AT` (3) villagers with `PowersCompat.injuries(v).any()` (Powers' breaks, cuts, festering) | Fewer than 1 | `PowersCompat.injuries` (none without Powers) |
| `raid` | `Raids.end` for that target (camp raids), or a successful `PortalRaids.start` (no camp, data `-1`) | `RAID_DAYS` (2) days later | event; offered only while `!Raids.raided(f.id())` |
| `fire` | `FIRE_BLAZES` (10) fires lit (`Firefighting.lit`) in the town within `FIRE_WINDOW` (2400 ticks) | `FIRE_DAYS` (2) days later | event |
| `war` | A war party sacks the town (`WarParties` line ~416, where `Quests.sacked` is called) | `WAR_DAYS` (3) days later | event |
| `drought` | The town's climate is `PLAINS`, `SAVANNA`, `DESERT` or `BADLANDS` (`Climate.at`), it hasn't rained for `DROUGHT_DAYS` (5) days, and it has no well project | It rains | `DRY` attachment: the last game day the Overworld was raining (written each check while `level.isRaining()`) |
| `missing` | `Captives.take` puts in chains a villager from a town that isn't a haven (havens already have `haven.child`) | That captive is home (`Captives.arrive`), or `MISSING_DAYS` (6) days later | event; data `uuid|name|x|y|z|campId` |
| `feud` | A standing, non-barbarian neighbour within `FEUD_RANGE` (800) with a leader and `Relations.between(f, other)` of `RIVAL` or `WAR` | No such neighbour | `Relations.between` |

A town has at most `MOST_AT_ONCE` (2) troubles. When there are more, they're taken in this order: war, raid, fire,
missing, famine, plague, wounds, drought, feud. When one ends, the next can start at the next check.

### 5.2 State

```java
/** (0.7.530) A trouble in a town: its kind, the day it began, the day it's over (-1: while its condition holds), what it's about (a camp, a captive, a rival), who was asked, and the day they were last asked. */
record Trouble(String kind, long since, long until, String data, List<UUID> askers, long asked)
/** (0.7.530) "<faction id>|<kind>" → the trouble. On the Overworld. */
static final AttachmentType<Map<String, Trouble>> TROUBLES = AttachmentRegistry.create(JaymosKingdoms.id("town_troubles"), ...persistent(Codec.unboundedMap(Codec.STRING, Trouble.CODEC)));
/** (0.7.530) The last game day it rained (for drought). On the Overworld. */
static final AttachmentType<Long> DRY = AttachmentRegistry.create(JaymosKingdoms.id("town_dry"), builder -> builder.persistent(Codec.LONG));
```
Fire counts (`Map<Integer, int[]>` of town to count and window start) are transient. A restart forgets a half-counted
fire, which is harmless.

### 5.3 Flow

- `register()`: `END_SERVER_TICK` every `CHECK_TICKS` (600). `SERVER_STOPPED` clears the transient maps.
- **Each check**, on `Factions.level(server)`:
  1. Update `DRY` while it's raining.
  2. Drop troubles for towns that have fallen, and event troubles past `until`. Each one that ends goes through
     `end()`.
  3. For each standing non-barbarian town with `level.isPositionEntityTicking(f.seat())` and a player within
     `PLAYER_NEAR` (160) of the seat, test the condition kinds. Start the ones that now hold, and end the ones that no
     longer do.
  4. For each active trouble in such a town where `asked < today` (and, for a raid, where `!Raids.raided(f.id())`),
     run `ask()`.
- **`event(MinecraftServer server, int faction, String kind, String data)`** (public static, called by the hooks)
  starts or refreshes an event trouble with `until = today + days`. It doesn't need the town loaded: asking waits for
  the next check where it is.
- **`start()`** saves the trouble and tells players within `LOCAL_NEWS` (300) of the seat
  `trouble.jaymoskingdoms.<kind>.start` (town name) in gold. **`end()`** removes it, tells them
  `trouble.jaymoskingdoms.<kind>.end` in green, and calls `Quests.withdraw(v, "trouble." + kind + ".")` for each
  asker loaded on the level (`level.getEntity(uuid) instanceof LivingEntity`). An asker who isn't loaded loses the
  offer at their next day roll anyway, because `offerNow` lasts until tomorrow.
- **`ask(level, f, trouble)`**: the candidates are grown, living villagers within `ASK_RANGE` (48) of the seat for
  whom `Quests.settlement(level, v)` is `f` and `!Quests.offering(v)`. Shuffle them as `DisasterRelief.ask` does. Make
  the kind's quests (5.4), dropping any that can't be made (a missing item id, no camp, no rival). Take `n =
  min(ASKERS_MIN + random(ASKERS_MAX - ASKERS_MIN + 1), quests, candidates)`, with `ASKERS_MIN` 2 and `ASKERS_MAX` 4.
  Match quests to villagers. A villager whose job path matches the template's `job` hint goes first, then anyone. Each
  villager gets a different template through `Quests.offerNow(level, v, q)`. Record the askers and `asked = today`.
  Each day the trouble lasts, a fresh round of askers is asked. The day before's offers have run out by then.
- **`handedIn(ServerLevel level, Faction f, Quest q)`**: called from `Quests.turnIn`'s GATHER branch, right after
  `take(...)`. For templates in `STOCKED`, put `count` of the item into `f`'s stores with `Storage.put(level, f,
  item, count)`. Any leftover is dropped. `STOCKED` = `trouble.famine.` (prefix), `trouble.raid.stores`,
  `trouble.war.bread`, `trouble.war.iron`, `trouble.fire.timber`. This is how bringing food ends a famine.
- **`fire(MinecraftServer server, Faction f)`**: called from `Firefighting.lit`. Change line 115 to `Faction near =
  Factions.around(...); if (near != null) { FIRES.add(...); TownTroubles.fire(server.getServer(), near); }`. It
  counts fires inside the window and starts a `fire` event at `FIRE_BLAZES`.
- **`missing(ServerLevel level, Faction home, Villager v, BlockPos at)`**: called from `Captives.take` (after the
  `CAPTIVE` attachment is set) when `home != null && !Havens.is(server, home)`. The data is the uuid, name and
  position, plus the id of the nearest standing barbarian camp within `MISSING_CAMP_RANGE` (160) of `at` (-1 if
  none). **`found(MinecraftServer server, UUID who)`**: called from `Captives.arrive` next to `Quests.rescued`. It
  ends any `missing` trouble whose data starts with that uuid.
- **Taken quests when a trouble ends** stay in the log and pay as normal. Taking them back would cost players
  reputation through no fault of theirs. `Quests.died` (SLAY), `DisasterRelief.cleared` (RELIEF) and
  `Quests.rescued` (RESCUE) already move them forward. CHIEF uses the camp-chief path that `haven.camp` uses, and
  DELIVER uses the leader path that `mail.far` uses.

`Quests.withdraw` (new, next to `offerNow`):
```java
/** (0.7.530) {@code giver}'s offer, if it's one of a trouble's (its template starts with {@code prefix}) and not yet taken, is taken back. */
static void withdraw(LivingEntity giver, String prefix) {
	Offer offer = giver.getAttached(OFFER);
	if (offer != null && offer.quest().filter(q -> q.template().startsWith(prefix)).isPresent()) {
		giver.setAttached(OFFER, new Offer(offer.nextDay(), false, Optional.empty()));
		mark(giver, false);
	}
}
```

### 5.4 The 28 trouble quests

Templates are `trouble.<kind>.<key>`. Each is built directly the way `DisasterRelief.ask` builds its quests. The giver
is `Quest.Giver(v.uuid, name, f.id(), false, job path)`. The reward is `Quest.Reward(emeralds, List.of(), empty, empty,
TROUBLE_HEARTS, TROUBLE_REPUTATION, TROUBLE_STANDING)`, where `emeralds = TROUBLE_EMERALDS + random(TROUBLE_SPREAD+1)`
unless a row says otherwise. GATHER targets are checked against the item registry and skipped when missing, as
`QuestBoard.make` does.

| Template | Kind | Target / objective | Count | Job hint | Pay |
|---|---|---|---|---|---|
| `trouble.famine.bread` | GATHER | `minecraft:bread` | 12-20 | farmer | base |
| `trouble.famine.potatoes` | GATHER | `minecraft:potato` | 16-32 | farmer | base |
| `trouble.famine.fish` | GATHER | `minecraft:cod` | 10-16 | fisherman | base |
| `trouble.famine.meat` | GATHER | `minecraft:cooked_beef` | 8-12 | butcher | base |
| `trouble.plague.garlic` | GATHER | `jaymoskingdoms:garlic` | 6-10 | cleric | base |
| `trouble.plague.bleeders` | SLAY | `jaymoskingdoms:night_bleeder` | 2-4 | cleric | base + 3 |
| `trouble.plague.honey` | GATHER | `minecraft:honey_bottle` | 3-6 | any | base |
| `trouble.wounds.bandages` | GATHER | `jaymospowers:linen_bandage` | 3-6 | cleric | base |
| `trouble.wounds.splints` | GATHER | `jaymospowers:splint` | 2-4 | cleric | base |
| `trouble.raid.chief` | CHIEF | `camp:<id>` (only when the data has a standing camp with a leader). Objective as `haven.camp` builds it: pos = camp seat, other = leader, place = camp name | 1 | guard | `TROUBLE_CHIEF_EMERALDS` (14) + spread |
| `trouble.raid.hornkin` | SLAY | `jaymoskingdoms:hornkin` (portal raid only: data `-1`) | 3-5 | guard | base + 3 |
| `trouble.raid.stores` | GATHER | `minecraft:wheat` | 16-32 | farmer | base |
| `trouble.raid.rubble` | RELIEF | `rubble`, pos = seat, place = town name | 16-32 | builder | base |
| `trouble.raid.arrows` | GATHER | `minecraft:arrow` | 24-48 | fletcher | base |
| `trouble.fire.buckets` | GATHER | `minecraft:water_bucket` | 2-4 | any | base |
| `trouble.fire.timber` | GATHER | `minecraft:oak_log` | 16-32 | woodcutter | base |
| `trouble.fire.clear` | RELIEF | `rubble`, pos = seat, place = town (logs and planks count, `DisasterRelief.rubble`) | 16-32 | builder | base |
| `trouble.war.rubble` | RELIEF | `rubble`, pos = seat, place = town | 24-40 | mason | base |
| `trouble.war.iron` | GATHER | `minecraft:iron_ingot` | 8-16 | armorer | base |
| `trouble.war.bread` | GATHER | `minecraft:bread` | 12-20 | any | base |
| `trouble.drought.water` | GATHER | `minecraft:water_bucket` | 2-4 | farmer | base |
| `trouble.drought.seeds` | GATHER | `minecraft:wheat_seeds` | 16-32 | farmer | base |
| `trouble.drought.bonemeal` | GATHER | `minecraft:bone_meal` | 12-24 | farmer | base |
| `trouble.missing.rescue` | RESCUE | `captive:<uuid>`, pos = where held, other = uuid, otherName = name, place = the camp's name or "the wilds" (`trouble.jaymoskingdoms.missing.wilds`) | 1 | any | `TROUBLE_RESCUE_EMERALDS` (12) + spread |
| `trouble.missing.chief` | CHIEF | `camp:<campId>` (only when the data has a standing camp with a leader) | 1 | guard | `TROUBLE_CHIEF_EMERALDS` + spread |
| `trouble.feud.peace` | DELIVER | `leader:<rival id>:<king\|lord\|elder>` (as in `QuestBoard.mail`), pos = rival seat, place = rival name | 1 | any | `TROUBLE_LETTER_EMERALDS` (9) + spread |
| `trouble.feud.arrows` | GATHER | `minecraft:arrow` | 24-48 | fletcher | base |
| `trouble.feud.shields` | GATHER | `minecraft:shield` | 2-3 | armorer | base |

Each kind has 2 to 5 templates. A raid offers 4 of `chief/hornkin, stores, rubble, arrows`: never both chief and
hornkin. `DELIVER` gives the letter on accept as it does now (`Quests.accept`, `letter(q)`). The text args are the ones
`Quests.text` already passes for each kind (section 7 follows them).

### 5.5 `TownTroubles` constants

```java
static final int CHECK_TICKS = 600;
static final double PLAYER_NEAR = 160.0, ASK_RANGE = 48.0, LOCAL_NEWS = 300.0;
static final int ASKERS_MIN = 2, ASKERS_MAX = 4, MOST_AT_ONCE = 2;
static final int FAMINE_FOOD = 16, FAMINE_FOLK = 6;
static final int PLAGUE_SICK = 2, WOUNDED_AT = 3;
static final int FIRE_BLAZES = 10, FIRE_WINDOW = 2400;
static final int RAID_DAYS = 2, FIRE_DAYS = 2, WAR_DAYS = 3, MISSING_DAYS = 6, DROUGHT_DAYS = 5;
static final double FEUD_RANGE = 800.0, MISSING_CAMP_RANGE = 160.0;
static final int TROUBLE_EMERALDS = 5, TROUBLE_SPREAD = 4, TROUBLE_SLAY_MORE = 3;
static final int TROUBLE_CHIEF_EMERALDS = 14, TROUBLE_RESCUE_EMERALDS = 12, TROUBLE_LETTER_EMERALDS = 9;
static final int TROUBLE_HEARTS = 6, TROUBLE_REPUTATION = 25, TROUBLE_STANDING = 10;
static final Set<Climate> DROUGHT_LANDS = EnumSet.of(Climate.PLAINS, Climate.SAVANNA, Climate.DESERT, Climate.BADLANDS);
```

### 5.6 Hook lines

- `Raids.end` (line 599, after `ACTIVE.remove`): `TownTroubles.event(level.getServer(), raid.target,
  TownTroubles.RAID, Integer.toString(raid.camp));`
- `PortalRaids.start` (line 117), just before it returns true: `Faction town = Factions.around(level.getServer(),
  level, seat, Villages.VILLAGE_RADIUS); if (town != null) TownTroubles.event(server, town.id(), TownTroubles.RAID,
  "-1");`
- `WarParties` (line ~410, before the `Quests.sacked` loop): `TownTroubles.event(server, target.id(),
  TownTroubles.WAR, Integer.toString(party.faction));`
- `Firefighting.lit` (line 115): see 5.3.
- `Captives.take` (line ~227) and `Captives.arrive` (line 489): see 5.3.
- `Quests.turnIn` (line 485, GATHER branch, after `take`): `TownTroubles.handedIn(level, f, q);`

## 6. Op test commands (`QuestCommands.java`, under the existing `LEVEL_GAMEMASTERS` gate)

- `/kingdom quests trouble <kind>` starts that trouble in the town you stand in (`Quests.settlement`, the same as
  `Factions.around(..., Villages.VILLAGE_RADIUS)` or `Factions.at`) and asks at once. For `raid`, `missing` and
  `feud`, the data is the nearest camp, a fake captive (nil uuid, name "Test", your position; the RESCUE row is
  skipped) and the nearest neighbour. Answers `quest.jaymoskingdoms.command.trouble.started`, `.unknown` or
  `.nowhere`.
- `/kingdom quests trouble clear` ends every trouble in that town (`.cleared`).
- `/kingdom quests works fill <project>` fills that project's needs and coin with you as the only contributor and
  completes it (`.works`). Kinds and project ids are suggested with `SharedSuggestionProvider.suggest`.

These are for testing only. Players use the giver screen and the board.

## 7. Lang: `lang/parts/quests-more.json` (all new, unique across parts)

Text args follow `Quests.text` and `title`:
- GATHER, SLAY and LEAD: `%1$s` count, `%2$s` what.
- FETCH: keepsake, ruin, x, z.
- RELIEF: count, place.
- CHIEF: title place. Text place, x, z, chief.
- RESCUE: title name. Text name, place, x, z.
- DELIVER: title addressee, place. Text addressee, place, x, z.

```json
{
  "quest.jaymoskingdoms.farmer.pumpkins.title": "Pumpkins for the Fair",
  "quest.jaymoskingdoms.farmer.pumpkins.text": "The harvest fair's coming and my patch has rotted through. Bring me %1$s %2$s and I'll still have something to show.",
  "quest.jaymoskingdoms.farmer.bonemeal.title": "Tired Soil",
  "quest.jaymoskingdoms.farmer.bonemeal.text": "My fields are worn out and the wheat comes up thin. %1$s %2$s worked into the rows should bring them back.",
  "quest.jaymoskingdoms.farmer.cows.title": "A Cow or Two",
  "quest.jaymoskingdoms.farmer.cows.text": "My old cow's gone dry and there are mouths to feed. Bring %1$s %2$s to me on a lead and I'll pay you for them.",
  "quest.jaymoskingdoms.butcher.pigs.title": "Pigs for the Pen",
  "quest.jaymoskingdoms.butcher.pigs.text": "My pen's empty and the orders keep coming. Lead %1$s %2$s back here for me.",
  "quest.jaymoskingdoms.butcher.foxes.title": "Foxes at the Hens",
  "quest.jaymoskingdoms.butcher.foxes.text": "Something's been taking my hens every night, and I've found the tracks. Kill %1$s %2$s before there's none left.",
  "quest.jaymoskingdoms.shepherd.sheep.title": "Strays from the Flock",
  "quest.jaymoskingdoms.shepherd.sheep.text": "A storm scattered my flock. Bring %1$s %2$s back to me on a lead, any colour will do.",
  "quest.jaymoskingdoms.shepherd.dye.title": "A Tailor's Red",
  "quest.jaymoskingdoms.shepherd.dye.text": "The guards want red coats and I've no dye left for the cloth. Bring me %1$s %2$s.",
  "quest.jaymoskingdoms.shepherd.cloak.title": "My Mother's Shuttle",
  "quest.jaymoskingdoms.shepherd.cloak.text": "My mother wove every cloak in this town with %1$s. It was lost when we camped at the %2$s, near %3$s, %4$s. Would you look for it?",
  "quest.jaymoskingdoms.leatherworker.scutes.title": "Armadillo Scutes",
  "quest.jaymoskingdoms.leatherworker.scutes.text": "A knight wants a hide coat that turns a blade. %1$s %2$s sewn in will stiffen it.",
  "quest.jaymoskingdoms.leatherworker.blubber.title": "Blubber for the Hides",
  "quest.jaymoskingdoms.leatherworker.blubber.text": "Nothing works a hide softer than whale fat. Bring me %1$s %2$s from the whalers.",
  "quest.jaymoskingdoms.fisherman.whale_oil.title": "Oil for the Harbour Lamps",
  "quest.jaymoskingdoms.fisherman.whale_oil.text": "The harbour lamps are nearly dry and the whalers aren't back. Bring %1$s %2$s if you can find any.",
  "quest.jaymoskingdoms.fisherman.guardians.title": "Spines in the Deep",
  "quest.jaymoskingdoms.fisherman.guardians.text": "One-eyed spiny things are tearing my nets out by the old stones. Kill %1$s %2$s and I can fish there again.",
  "quest.jaymoskingdoms.fisherman.puffers.title": "Pufferfish for the Healer",
  "quest.jaymoskingdoms.fisherman.puffers.text": "The healer pays well for pufferfish and mine keep getting away. Catch %1$s %2$s for me, and mind the spines.",
  "quest.jaymoskingdoms.fletcher.string.title": "Bowstrings",
  "quest.jaymoskingdoms.fletcher.string.text": "I've a dozen bows waiting on strings. Bring me %1$s %2$s.",
  "quest.jaymoskingdoms.fletcher.quiver.title": "The Old Quiver",
  "quest.jaymoskingdoms.fletcher.quiver.text": "My father carried %1$s through the last war and lost it at the %2$s (%3$s, %4$s). I'd like it back on my wall.",
  "quest.jaymoskingdoms.armorer.gold.title": "Gilding for a Lord",
  "quest.jaymoskingdoms.armorer.gold.text": "A lord wants gold trim on his armour and I've none to give him. Bring me %1$s %2$s.",
  "quest.jaymoskingdoms.armorer.leather.title": "Straps and Linings",
  "quest.jaymoskingdoms.armorer.leather.text": "Plate's no good without straps to hold it on. I need %1$s %2$s for the linings.",
  "quest.jaymoskingdoms.weaponsmith.diamonds.title": "A Blade Worth the Name",
  "quest.jaymoskingdoms.weaponsmith.diamonds.text": "I've promised a captain a diamond edge. Find me %1$s %2$s and I'll make it.",
  "quest.jaymoskingdoms.weaponsmith.skeletons.title": "Steel on Bone",
  "quest.jaymoskingdoms.weaponsmith.skeletons.text": "I want to know what bone does to a good edge. Kill %1$s %2$s, then come and tell me how your blade held up.",
  "quest.jaymoskingdoms.toolsmith.copper.title": "Copper for Fittings",
  "quest.jaymoskingdoms.toolsmith.copper.text": "Copper fittings sell well and I'm right out. Bring %1$s %2$s.",
  "quest.jaymoskingdoms.toolsmith.sticks.title": "Handles",
  "quest.jaymoskingdoms.toolsmith.sticks.text": "I've a pile of tool heads and nothing to fit them on. Bring %1$s %2$s for handles.",
  "quest.jaymoskingdoms.librarian.leather.title": "Bindings",
  "quest.jaymoskingdoms.librarian.leather.text": "Half the library is falling out of its covers. %1$s %2$s would bind them again.",
  "quest.jaymoskingdoms.librarian.glow.title": "Ink That Shines",
  "quest.jaymoskingdoms.librarian.glow.text": "Glowing ink for the late hours, when the candles gutter. Bring me %1$s %2$s.",
  "quest.jaymoskingdoms.librarian.scroll.title": "The Sealed Scroll",
  "quest.jaymoskingdoms.librarian.scroll.text": "A traveller wrote of %1$s left in the %2$s near %3$s, %4$s, its seal unbroken. Bring it to me.",
  "quest.jaymoskingdoms.cleric.bandages.title": "Bandages for the Sick Room",
  "quest.jaymoskingdoms.cleric.bandages.text": "The sick room's full and the linen's all used. Bring %1$s %2$s.",
  "quest.jaymoskingdoms.cleric.garlic.title": "Garlic Against the Night",
  "quest.jaymoskingdoms.cleric.garlic.text": "Garlic over the doors keeps the night out, the old books say. Bring me %1$s %2$s.",
  "quest.jaymoskingdoms.cleric.bleeders.title": "Night Bleeders",
  "quest.jaymoskingdoms.cleric.bleeders.text": "Things that drink blood come out of the dark and leave the plague behind them. Kill %1$s %2$s and fewer will fall sick.",
  "quest.jaymoskingdoms.cartographer.paper.title": "Paper for Charts",
  "quest.jaymoskingdoms.cartographer.paper.text": "I'm charting the coast and I've run out of paper. %1$s %2$s will see me through.",
  "quest.jaymoskingdoms.cartographer.amethyst.title": "Lenses",
  "quest.jaymoskingdoms.cartographer.amethyst.text": "My spyglass is cracked. %1$s %2$s will grind a new lens and a spare.",
  "quest.jaymoskingdoms.cartographer.log.title": "The Ship's Log",
  "quest.jaymoskingdoms.cartographer.log.text": "A captain's log would show me waters no chart has. %1$s is said to lie in the %2$s near %3$s, %4$s. Bring it back.",
  "quest.jaymoskingdoms.mason.andesite.title": "Andesite for the Steps",
  "quest.jaymoskingdoms.mason.andesite.text": "The temple steps want facing in andesite. %1$s %2$s will do it.",
  "quest.jaymoskingdoms.mason.bricks.title": "Bricks for an Oven",
  "quest.jaymoskingdoms.mason.bricks.text": "The baker's oven is cracked right through. I need %1$s %2$s to build it again.",
  "quest.jaymoskingdoms.builder.glass.title": "Windows",
  "quest.jaymoskingdoms.builder.glass.text": "The new houses have holes where the windows go. Bring %1$s %2$s.",
  "quest.jaymoskingdoms.builder.planks.title": "Floorboards",
  "quest.jaymoskingdoms.builder.planks.text": "Floors, rafters, doors: it all wants planks. Bring %1$s %2$s.",
  "quest.jaymoskingdoms.builder.creepers.title": "Blown Scaffolds",
  "quest.jaymoskingdoms.builder.creepers.text": "Twice now a creeper's walked into my scaffold and brought it down. Kill %1$s %2$s round here so I can build in peace.",
  "quest.jaymoskingdoms.miner.coal.title": "Coal for the Forges",
  "quest.jaymoskingdoms.miner.coal.text": "The smiths want coal faster than my lads can dig it. Bring %1$s %2$s.",
  "quest.jaymoskingdoms.miner.ladders.title": "Ladders Down",
  "quest.jaymoskingdoms.miner.ladders.text": "The new shaft is deep and the old ladders are rotten. %1$s %2$s, please.",
  "quest.jaymoskingdoms.woodcutter.logs.title": "Spruce for the Masts",
  "quest.jaymoskingdoms.woodcutter.logs.text": "The harbour wants straight spruce for masts. Bring me %1$s %2$s.",
  "quest.jaymoskingdoms.woodcutter.zombies.title": "Dusk in the Woods",
  "quest.jaymoskingdoms.woodcutter.zombies.text": "The dead come out of the trees at dusk, and we've lost two axes running from them. Kill %1$s %2$s.",
  "quest.jaymoskingdoms.woodcutter.charcoal.title": "Charcoal by the Sack",
  "quest.jaymoskingdoms.woodcutter.charcoal.text": "The smith pays for charcoal by the sack. Burn me %1$s %2$s and I'll share his coin with you.",
  "quest.jaymoskingdoms.guard.creepers.title": "Creepers by the Wall",
  "quest.jaymoskingdoms.guard.creepers.text": "Creepers keep getting up to the wall at night. Kill %1$s %2$s before one gets in.",
  "quest.jaymoskingdoms.guard.spiders.title": "Spiders on the Roofs",
  "quest.jaymoskingdoms.guard.spiders.text": "Spiders climb the roofs and drop on the watch. Kill %1$s %2$s.",
  "quest.jaymoskingdoms.guard.arrows.title": "Arrows for the Watch",
  "quest.jaymoskingdoms.guard.arrows.text": "The watch has more bows than arrows. Bring %1$s %2$s.",
  "quest.jaymoskingdoms.stablehand.hay.title": "Hay for Winter",
  "quest.jaymoskingdoms.stablehand.hay.text": "Winter's coming and the loft is half empty. Bring %1$s %2$s.",
  "quest.jaymoskingdoms.stablehand.apples.title": "Apples for the Horses",
  "quest.jaymoskingdoms.stablehand.apples.text": "The horses work better for a treat. Bring me %1$s %2$s.",
  "quest.jaymoskingdoms.farrier.nuggets.title": "Nails for the Shoes",
  "quest.jaymoskingdoms.farrier.nuggets.text": "I'm out of nails for the shoes. Bring %1$s %2$s and I'll draw my own.",
  "quest.jaymoskingdoms.farrier.horseshoe.title": "The Lucky Horseshoe",
  "quest.jaymoskingdoms.farrier.horseshoe.text": "Our old champion's horse threw %1$s at the %2$s near %3$s, %4$s. It hung over this forge for years. Bring it home.",
  "quest.jaymoskingdoms.none.mushrooms.title": "Mushrooms for the Pot",
  "quest.jaymoskingdoms.none.mushrooms.text": "There's nothing like mushroom stew on a cold night. Bring me %1$s %2$s.",
  "quest.jaymoskingdoms.none.eggs.title": "Eggs for a Cake",
  "quest.jaymoskingdoms.none.eggs.text": "It's my daughter's name day and I want to bake her a cake. Could you bring %1$s %2$s?",
  "quest.jaymoskingdoms.cook.sugar.title": "Something Sweet",
  "quest.jaymoskingdoms.cook.sugar.text": "The tavern wants pies and I'm out of sugar. Bring %1$s %2$s.",
  "quest.jaymoskingdoms.cook.milk.title": "Milk for the Kitchen",
  "quest.jaymoskingdoms.cook.milk.text": "Cream, cheese, porridge: it all wants milk. Bring me %1$s %2$s.",
  "quest.jaymoskingdoms.cook.potatoes.title": "A Sack of Potatoes",
  "quest.jaymoskingdoms.cook.potatoes.text": "Feeding a whole town takes potatoes, and lots of them. Bring %1$s %2$s.",
  "quest.jaymoskingdoms.puller.leads.title": "New Traces",
  "quest.jaymoskingdoms.puller.leads.text": "My traces snapped on the hill road. %1$s %2$s will get me pulling again.",
  "quest.jaymoskingdoms.puller.bread.title": "Bread for the Long Road",
  "quest.jaymoskingdoms.puller.bread.text": "The long hauls leave me starving halfway. Bring %1$s %2$s for the road.",
  "quest.jaymoskingdoms.changer.emeralds.title": "Emeralds for the Strongbox",
  "quest.jaymoskingdoms.changer.emeralds.text": "Folk bring me coin and want gems for it, and I'm short. Bring me %1$s %2$s and you'll be paid fairly.",

  "quest.jaymoskingdoms.trouble.famine.bread.title": "Bread for the Hungry",
  "quest.jaymoskingdoms.trouble.famine.bread.text": "The stores are nearly empty and the children are hungry. Bring %1$s %2$s. It goes straight into the town's stores.",
  "quest.jaymoskingdoms.trouble.famine.potatoes.title": "Potatoes for the Pot",
  "quest.jaymoskingdoms.trouble.famine.potatoes.text": "We're boiling nettles for soup. %1$s %2$s would see us to the harvest. It all goes into the stores.",
  "quest.jaymoskingdoms.trouble.famine.fish.title": "Fish for the Table",
  "quest.jaymoskingdoms.trouble.famine.fish.text": "The fields have failed us, but the water hasn't. Bring %1$s %2$s for the stores.",
  "quest.jaymoskingdoms.trouble.famine.meat.title": "Meat for the Stores",
  "quest.jaymoskingdoms.trouble.famine.meat.text": "We've eaten the last of the salted meat. Bring %1$s %2$s to the stores and we'll get by.",
  "quest.jaymoskingdoms.trouble.plague.garlic.title": "Garlic for the Doors",
  "quest.jaymoskingdoms.trouble.plague.garlic.text": "The plague is in the houses. The old cure is garlic over every door. Bring %1$s %2$s.",
  "quest.jaymoskingdoms.trouble.plague.bleeders.title": "What Brought the Plague",
  "quest.jaymoskingdoms.trouble.plague.bleeders.text": "Night Bleeders brought this sickness. Kill %1$s %2$s before they bring more.",
  "quest.jaymoskingdoms.trouble.plague.honey.title": "Honey for the Fevers",
  "quest.jaymoskingdoms.trouble.plague.honey.text": "Honey eases a fever and soothes the throat. Bring %1$s %2$s for the sick.",
  "quest.jaymoskingdoms.trouble.wounds.bandages.title": "Bandages, Quickly",
  "quest.jaymoskingdoms.trouble.wounds.bandages.text": "We've more hurt than we can tend. Bring %1$s %2$s.",
  "quest.jaymoskingdoms.trouble.wounds.splints.title": "Splints for the Broken",
  "quest.jaymoskingdoms.trouble.wounds.splints.text": "There are broken bones all over town. Bring %1$s %2$s so they set straight.",
  "quest.jaymoskingdoms.trouble.raid.chief.title": "Pay Back %1$s",
  "quest.jaymoskingdoms.trouble.raid.chief.text": "The raiders from %1$s burned our barns and took what they wanted. Their chief sits in his camp near %2$s, %3$s. Kill him and they won't come back.",
  "quest.jaymoskingdoms.trouble.raid.hornkin.title": "Hornkin in the Streets",
  "quest.jaymoskingdoms.trouble.raid.hornkin.text": "Horned things came through the portal, and some of them are still about. Kill %1$s %2$s.",
  "quest.jaymoskingdoms.trouble.raid.stores.title": "Restock the Stores",
  "quest.jaymoskingdoms.trouble.raid.stores.text": "The raiders emptied the granary. Bring %1$s %2$s to the stores.",
  "quest.jaymoskingdoms.trouble.raid.rubble.title": "After the Raid",
  "quest.jaymoskingdoms.trouble.raid.rubble.text": "The raid left half of %2$s in ruins. Clear %1$s blocks of wreckage round the square.",
  "quest.jaymoskingdoms.trouble.raid.arrows.title": "Arrows for Next Time",
  "quest.jaymoskingdoms.trouble.raid.arrows.text": "If they come again, we'll be ready. Bring %1$s %2$s for the walls.",
  "quest.jaymoskingdoms.trouble.fire.buckets.title": "Buckets of Water",
  "quest.jaymoskingdoms.trouble.fire.buckets.text": "Embers are still smouldering under the wreckage. Bring %1$s %2$s before it catches again.",
  "quest.jaymoskingdoms.trouble.fire.timber.title": "Timber to Rebuild",
  "quest.jaymoskingdoms.trouble.fire.timber.text": "The fire took whole houses. Bring %1$s %2$s to the stores so we can build them again.",
  "quest.jaymoskingdoms.trouble.fire.clear.title": "The Burned Houses",
  "quest.jaymoskingdoms.trouble.fire.clear.text": "Charred beams hang all over %2$s. Pull down %1$s blocks of it before it falls on someone.",
  "quest.jaymoskingdoms.trouble.war.rubble.title": "After the Sack",
  "quest.jaymoskingdoms.trouble.war.rubble.text": "The soldiers wrecked %2$s on their way out. Clear %1$s blocks of rubble round the square.",
  "quest.jaymoskingdoms.trouble.war.iron.title": "Iron for New Locks",
  "quest.jaymoskingdoms.trouble.war.iron.text": "They broke every lock and gate in town. Bring %1$s %2$s for the smith.",
  "quest.jaymoskingdoms.trouble.war.bread.title": "Bread after the Sack",
  "quest.jaymoskingdoms.trouble.war.bread.text": "They took our food along with everything else. Bring %1$s %2$s to the stores.",
  "quest.jaymoskingdoms.trouble.drought.water.title": "Water for the Seedlings",
  "quest.jaymoskingdoms.trouble.drought.water.text": "It hasn't rained in days and the well is dropping. Bring %1$s %2$s for the seedlings.",
  "quest.jaymoskingdoms.trouble.drought.seeds.title": "Seed to Sow Again",
  "quest.jaymoskingdoms.trouble.drought.seeds.text": "The crops dried up in the ground. Bring %1$s %2$s so we can sow again when the rain comes.",
  "quest.jaymoskingdoms.trouble.drought.bonemeal.title": "Bone Meal for the Dry Fields",
  "quest.jaymoskingdoms.trouble.drought.bonemeal.text": "Bone meal might save what's left of the fields. Bring %1$s %2$s.",
  "quest.jaymoskingdoms.trouble.missing.rescue.title": "%1$s Is Missing",
  "quest.jaymoskingdoms.trouble.missing.rescue.text": "%1$s went out and never came home. A hunter saw them in chains at %2$s, near %3$s, %4$s. Please bring them home.",
  "quest.jaymoskingdoms.trouble.missing.chief.title": "The Camp at %1$s",
  "quest.jaymoskingdoms.trouble.missing.chief.text": "The ones from %1$s took one of ours. Their chief's camp is near %2$s, %3$s. Kill him, and they'll think twice next time.",
  "quest.jaymoskingdoms.trouble.feud.peace.title": "A Letter of Peace to %2$s",
  "quest.jaymoskingdoms.trouble.feud.peace.text": "We and %2$s are at each other's throats, and it's the farmers who'll pay for it. Take this letter to %1$s at %3$s, %4$s. It might cool things down.",
  "quest.jaymoskingdoms.trouble.feud.arrows.title": "Arrows for the Border",
  "quest.jaymoskingdoms.trouble.feud.arrows.text": "Our neighbours are spoiling for a fight and the watch is short of arrows. Bring %1$s %2$s.",
  "quest.jaymoskingdoms.trouble.feud.shields.title": "Shields for the Watch",
  "quest.jaymoskingdoms.trouble.feud.shields.text": "If it comes to blows, the watch will want shields. Bring %1$s %2$s.",

  "quest.jaymoskingdoms.foes.fox": "Foxes",
  "quest.jaymoskingdoms.foes.guardian": "Guardians",
  "quest.jaymoskingdoms.foes.night_bleeder": "Night Bleeders",
  "quest.jaymoskingdoms.foes.hornkin": "Hornkin",
  "quest.jaymoskingdoms.keepsake.weavers_shuttle": "The Weaver's Shuttle",
  "quest.jaymoskingdoms.keepsake.fletchers_quiver": "The Fletcher's Quiver",
  "quest.jaymoskingdoms.keepsake.sealed_scroll": "The Sealed Scroll",
  "quest.jaymoskingdoms.keepsake.ships_log": "The Ship's Log",
  "quest.jaymoskingdoms.keepsake.lucky_horseshoe": "The Lucky Horseshoe",

  "trouble.jaymoskingdoms.famine.start": "%s is going hungry. Its stores are nearly bare.",
  "trouble.jaymoskingdoms.famine.end": "%s has food in its stores again.",
  "trouble.jaymoskingdoms.plague.start": "Plague has come to %s.",
  "trouble.jaymoskingdoms.plague.end": "The plague has left %s.",
  "trouble.jaymoskingdoms.wounds.start": "Many in %s are hurt and need tending.",
  "trouble.jaymoskingdoms.wounds.end": "The hurt in %s are mending.",
  "trouble.jaymoskingdoms.raid.start": "%s is counting what the raiders took.",
  "trouble.jaymoskingdoms.raid.end": "%s has put the raid behind it.",
  "trouble.jaymoskingdoms.fire.start": "Fire has swept through %s.",
  "trouble.jaymoskingdoms.fire.end": "%s has cleared away the ashes.",
  "trouble.jaymoskingdoms.war.start": "%s was sacked and lies in ruins.",
  "trouble.jaymoskingdoms.war.end": "%s is back on its feet after the sack.",
  "trouble.jaymoskingdoms.drought.start": "No rain has fallen on %s for days. The fields are drying out.",
  "trouble.jaymoskingdoms.drought.end": "Rain at last on %s.",
  "trouble.jaymoskingdoms.missing.start": "One of %s's own has gone missing.",
  "trouble.jaymoskingdoms.missing.end": "%s has stopped looking for its missing.",
  "trouble.jaymoskingdoms.missing.wilds": "the wilds",
  "trouble.jaymoskingdoms.feud.start": "%s is feuding with its neighbours.",
  "trouble.jaymoskingdoms.feud.end": "The feud round %s has cooled.",

  "project.jaymoskingdoms.sheet": "Town works",
  "project.jaymoskingdoms.screen": "Town works: %s",
  "project.jaymoskingdoms.none": "%s has nothing being built together just now.",
  "project.jaymoskingdoms.no_town": "This board belongs to no town.",
  "project.jaymoskingdoms.refused": "%s won't take your help.",
  "project.jaymoskingdoms.title": "%s (%s%%)",
  "project.jaymoskingdoms.need": "%s %s/%s",
  "project.jaymoskingdoms.coin": "Coin %s/%s",
  "project.jaymoskingdoms.share": "Your share: %s%%",
  "project.jaymoskingdoms.givers": "Given to by %s",
  "project.jaymoskingdoms.gives": "When it's done: %s",
  "project.jaymoskingdoms.button.goods": "Give goods",
  "project.jaymoskingdoms.button.coin": "Give coin",
  "project.jaymoskingdoms.button.coins": "Give 5 coins",
  "project.jaymoskingdoms.label.open": "Wanted",
  "project.jaymoskingdoms.label.done": "Built",
  "project.jaymoskingdoms.given": "You give %s toward the %s.",
  "project.jaymoskingdoms.nothing": "You carry nothing it still needs.",
  "project.jaymoskingdoms.poor": "You haven't the coin.",
  "project.jaymoskingdoms.store_full": "The town's stores are full. Nothing more fits.",
  "project.jaymoskingdoms.complete": "%s has finished its %s, with the help of %s hands.",
  "project.jaymoskingdoms.paid": "%s pays you %s for your share in the %s, and thinks better of you.",
  "project.jaymoskingdoms.paid_late": "%s paid you %s for your share in its works while you were away.",
  "project.jaymoskingdoms.watchtower.warn": "The watch on %s's tower spots raiders gathering near %s, %s!",
  "project.jaymoskingdoms.tag.stone_crafting_materials": "Cobblestone",
  "project.jaymoskingdoms.tag.logs": "Logs",
  "project.jaymoskingdoms.tag.planks": "Planks",
  "project.jaymoskingdoms.tag.wool": "Wool",
  "project.jaymoskingdoms.well.title": "A New Well",
  "project.jaymoskingdoms.well.text": "A deep well in the square, so nobody has to carry water from the river.",
  "project.jaymoskingdoms.well.gives": "the builders raise a well, and dry spells and sickness come harder.",
  "project.jaymoskingdoms.well.done": "%s draws its water from its new well.",
  "project.jaymoskingdoms.granary.title": "The Granary",
  "project.jaymoskingdoms.granary.text": "A dry, raised store for grain, so a bad year doesn't mean a hungry one.",
  "project.jaymoskingdoms.granary.gives": "food in store for lean times, and the town gets by on half as much.",
  "project.jaymoskingdoms.granary.done": "%s's granary is full and dry.",
  "project.jaymoskingdoms.wall.title": "Strong Walls",
  "project.jaymoskingdoms.wall.text": "Stone and timber to shore up the walls and gates.",
  "project.jaymoskingdoms.wall.gives": "raiders come less often, and a village saves toward its next wall.",
  "project.jaymoskingdoms.wall.done": "%s stands behind stronger walls.",
  "project.jaymoskingdoms.watchtower.title": "A Watchtower",
  "project.jaymoskingdoms.watchtower.text": "A tall tower with a spyglass and a fire, to see trouble coming.",
  "project.jaymoskingdoms.watchtower.gives": "fewer raids, and warning when one musters.",
  "project.jaymoskingdoms.watchtower.done": "%s keeps a watch on its tower day and night.",
  "project.jaymoskingdoms.bridge.title": "The Bridge Road",
  "project.jaymoskingdoms.bridge.text": "Timber for a new road out of town, bridged wherever it meets water.",
  "project.jaymoskingdoms.bridge.gives": "a road and its bridges to one more neighbour.",
  "project.jaymoskingdoms.bridge.done": "%s has a new road and bridge to its neighbours.",
  "project.jaymoskingdoms.infirmary.title": "The Infirmary",
  "project.jaymoskingdoms.infirmary.text": "Beds, linen and light for a proper house of healing.",
  "project.jaymoskingdoms.infirmary.gives": "the builders raise an infirmary, and sickness and wounds spread less.",
  "project.jaymoskingdoms.infirmary.done": "%s tends its sick in its new infirmary.",
  "talk.jaymoskingdoms.works.posting.0": "The town works are up! Lend a hand if you can.",
  "talk.jaymoskingdoms.works.posting.1": "We're building together. Anything helps!",
  "talk.jaymoskingdoms.works.posting.2": "Goods or coin for the town works, friends!",

  "quest.jaymoskingdoms.command.trouble.started": "Trouble (%s) started in %s.",
  "quest.jaymoskingdoms.command.trouble.cleared": "Cleared %s trouble(s) in %s.",
  "quest.jaymoskingdoms.command.trouble.unknown": "No such trouble: %s.",
  "quest.jaymoskingdoms.command.trouble.nowhere": "You're not in a town.",
  "quest.jaymoskingdoms.command.works": "Finished %s in %s.",

  "gui.jaymoskingdoms.story.hint.96": "When a town is in trouble (hungry, sick, burned, raided or feuding), its folk ask for help with it. Their asks go when the trouble does.",
  "gui.jaymoskingdoms.story.hint.97": "A town board's Town works sheet lists what the town is building together. Anyone can give goods or coin, and when it's done everyone who gave is paid by their share."
}
```

The `project.jaymoskingdoms.need` line uses the item's hover name for single items and
`project.jaymoskingdoms.tag.<path>` for tags.

## 8. Order of work (tracks)

Two editor tracks at a time, each in its own worktree. No two tracks in a round touch the same file.

- **Round 1, track A (trades and text):** `QuestBoard.java` (section 3), `lang/parts/quests-more.json` (all of
  section 7, written once here so no other track touches lang), and `client/StoryHoldScreen.java` (`HINTS = 97`).
- **Round 1, track B (troubles):** `TownTroubles.java` (section 5, without the 4.6 bonus reads), `Quests.java`
  (`withdraw`, the `handedIn` call), `Raids.java` (`end` hook only), `nether/PortalRaids.java`, `WarParties.java`,
  `Firefighting.java`, `Captives.java`, `QuestCommands.java` (`trouble`), `JaymosKingdoms.java` (`TownTroubles`
  register and debug).
- **Round 2, track C (projects), on the merged A+B:** `TownProjects.java`, `QuestNet.java`, `client/QuestScreen.java`,
  `Bounties.java`, `Commissions.java`, `Economy.java`, `Roads.java`, `WalledTowns.java`, `Raids.java` (chance and
  `start`), `Quests.java` (`act` branch), `QuestCommands.java` (`works`), `JaymosKingdoms.java` (`TownProjects`),
  and the bonus reads in `TownTroubles.java` (4.6).
- **Integrator:** versions, both MODLOGs, `docs/codemap/kingdoms.md` (`tools/codemap.py`), and `kingdoms/gradlew
  build` plus root `./gradlew build`. The Kingdoms build fails on any duplicate lang key across parts, which catches
  clashes. Afterwards, count the titles again: `cat lang/parts/quest*.json lang/parts/quests-more.json | grep -o
  '"quest\.jaymoskingdoms\.[^"]*\.title"' | sort -u | wc -l` should give 272.

Each finding from review gets an independent verifier before it's reported. A fix loop stops after two rounds with no
progress.

## 9. MODLOG and versions

`kingdoms/gradle.properties`: `version=0.7.530`. `gradle.properties`: `version=0.20.33`.

`kingdoms/MODLOG.md` (append at the end):
```markdown
## 0.7.530: quests to 272, town troubles and town works (pack 0.20.33)
Patch 9. Andy picked the idea of the MCA: Quests add-on and asked for "260+ ... get close"; our own version, none of
its code. Plan: `docs/Quests-Plan.md`. There were 189 quest titles (187 quest templates); now there are 272.
- **55 more trade quests** (`QuestBoard`): two or three more for every trade, from farmers to farriers, the tailor's
  and healer's (shepherd, cleric) and the whalers' (whale oil and blubber at the harbour). Cooks, pullers and
  money-changers give their own quests now. Some ask you to lead cows, pigs or sheep home on a lead.
- **Town troubles** (`TownTroubles`, 28 quests): while a town is hungry (stores nearly bare), sick (the Nightfall
  plague, or many wounded), raided (a camp's raid or a portal raid), burned, sacked in war, in drought, missing one of
  its own (taken captive) or feuding with a neighbour, 2-4 of its villagers ask for help with that trouble. Food,
  wheat, timber and iron brought for it go into the town's stores. When the trouble ends, the asks not taken go too.
  Taken ones still pay. Players near the town hear when it starts and ends.
- **Town works** (`TownProjects`): a Town works sheet on every town board lists six shared projects (well, granary,
  wall, watchtower, bridge, infirmary). Anyone gives goods or coin. Progress is shared and saved per town. When it's
  done the town gets it for real: the builders raise the well or infirmary, a granary keeps it fed on half the food, a
  wall and a watchtower mean fewer raids (the tower warns of one mustering), and a bridge lays a new road with bridges
  to one more neighbour. Everyone who gave is paid coin and reputation by their share, and paid on joining if they
  were away.
- Ops: `/kingdom quests trouble <kind>|clear`, `/kingdom quests works fill <project>`.
- Loading hints 96-97. Not played; compile-checked with `kingdoms/gradlew build`.
```

`MODLOG.md` (Powers code is unchanged):
```markdown
- **0.20.33**: no Powers change; pack bump for Kingdoms 0.7.530 (quests to 272: more for every trade, town troubles,
  and town works that players build together).
```

## 10. Open questions for Andy

1. Kingdoms' own Folk workers (the hired guards, archers, miners, woodcutters, farmers, stablehands and farriers)
   still don't give quests. The villagers with the same jobs do. Letting the Folk give them too means changes in five
   places (`Quests.settlement`, `hasWork`, `offered`, the tick scan, and `Folk.mobInteract`). Do you want that in a
   later patch?
2. Should a trouble's taken quests fail when the trouble ends, instead of still paying? This spec has them still pay.
3. Should projects that are already finished come back (a second well, a higher wall) once a town grows? This spec
   builds each one once per town.
