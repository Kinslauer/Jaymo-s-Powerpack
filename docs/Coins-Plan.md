# Coins replace emeralds: implementation plan

## 0. Decisions in one place
- **Jaymo's Kingdoms owns the economy.** It owns the coin items, the purse, the mint, the money changer and the `Money` API. Kingdoms already runs with no other mods, and about 85% of the emerald sites are in Kingdoms.
- **Powers and Kitchen reach Kingdoms only through a compat class**, the same way `PowersCompat` works in the other direction. Each gets a `KingdomsMoney` class that is a MethodHandle bridge to `com.jaymoskingdoms.money.Money`. If Kingdoms isn't loaded, it falls back to emeralds, so Z Fighter prizes, the hermit, the teller, the priest and the butcher still work.
- **Powers keeps the belt slot and draws the purse on the hip.** Kingdoms adds its purse to Powers' belt tag with a data file only (`kingdoms/src/main/resources/data/jaymospowers/tags/item/baubles/belt.json`, `replace:false`). The tags merge, so neither mod needs code from the other. Kingdoms reads and writes the worn purse through new `PowersCompat` handles.
- **1 emerald = 1 silver coin.** Every existing price constant keeps its number. A constant that meant "N emeralds" now means "N silver". `Faction.treasury` stays a `long` counted in silver, so there is **no treasury migration** and `Wealth`, `Cavalry`, `Muskets`, `Javelins`, `Cabs` and `Carriages` need no change.

## 1. Coins, values, mint, changer
| Item (kingdoms id) | Value | Struck from (Coin Press) |
|---|---|---|
| `copper_coin` | 1 cp | 1 copper ingot → 2 coins |
| `silver_coin` | 10 cp (= 1 old emerald) | 1 `#c:ingots/silver` → 3 coins (Powers' `tinkering` silver ingot; tag it `c:ingots/silver` in Powers data) |
| `gold_coin` | 100 cp | 9 gold nuggets → 1 coin ("gold-nugget coin") |
| `copper_coin_stack`, `silver_coin_stack`, `gold_coin_stack` | 10 coins each | 10 coins ⇄ 1 stack (shapeless recipes). Placeable layered pile block, used for hoards and big withdrawals |
| `coin_purse` | holds coins | leather + string + 1 copper coin (crafting table) |

- **The money unit is the copper piece (cp).** The API works in cp (`long`). Helper `Money.silver(n) = n*10` converts old emerald constants. `Meals.BITS` (16 per emerald today) becomes `10` cp. The eatery till pays whole silver into the treasury.
- **Coin Press** (`jaymoskingdoms:coin_press`, block + menu, all tuning in constants at the top):
  - It only strikes inside a settlement whose realm has a mint. Founded and claimed realms get one with their town hall.
  - Each strike pays seigniorage into the realm treasury: `MINT_TAX_PERCENT = 10`.
  - Daily cap: `MINT_PER_DAY = 32 + 8*fiefs` coins. This stops piglin-gold and copper farms from printing money.
  - Outside a realm, the press refuses with "no die".
- **Money changer**, a villager profession `jaymoskingdoms:money_changer`:
  - Workstation: Counting Table, a new block registered as a POI.
  - It spawns in every capital and in towns at Wealth ≥ PROSPEROUS. Pedlars also make change, at a worse rate.
  - It uses vanilla MerchantOffers, so it needs no new screen. Its trades:
    - buys 1 emerald → 9 cp
    - buys an emerald block → 8 silver
    - sells 1 emerald for 1 silver + 2 cp (for enchanting and tinkering)
    - buys a diamond → 4 silver
    - changes 10 copper ⇄ 1 silver and 10 silver ⇄ 1 gold free
  - `Changer.java` holds the rates as constants.

## 2. Central Money API (Kingdoms: `com.jaymoskingdoms.money`)
- **`Coin`**: an enum with COPPER(1), SILVER(10) and GOLD(100), mapping each to its item, stack item and value. It has `valueOf(ItemStack)` (stacks ×10) and `coins(long cp) → List<ItemStack>` (greedy, ≤64 per stack).
- **`PurseContents`**: a data component `jaymoskingdoms:coins`, a record `(int copper, int silver, int gold)` with a Codec and a StreamCodec.
  - Cap: `PURSE_MOST = 640` coins in total.
  - It sets `DataComponents.CUSTOM_MODEL_DATA` floats[0] = fullness 0..1, read by a `range_dispatch` on `custom_model_data` in `items/coin_purse.json`. `CustomModelDataProperty` exists in 26.3 client `renderer/item/properties/numeric`.
- **`Money`**, server side and purse-aware. It replaces `Purse`.
  - `long count(ServerPlayer)`: the total cp across loose coins, coin stacks, purses in the inventory, bundles, and the worn belt purse (`PowersCompat.belt`).
  - `boolean canPay(p, cp)`.
  - `boolean charge(p, cp)`: all or nothing. It takes from loose coins first, then from purses, smallest coins first, and gives change back into the purse it took from (otherwise the inventory, otherwise dropped).
  - `long chargeUpTo(p, cp)`.
  - `void pay(p, cp)`: into the worn purse if it has room, otherwise the inventory, otherwise dropped.
  - `List<ItemStack> stacks(cp)` is for loot, chests and `Storage`. `Component text(cp)` shows "3 s 4 c".
  - Every call ignores emeralds. The creative bypass is `charge` returning true when `hasInfiniteMaterials()`, so the sites no longer each handle creative their own way.
- **Rewrites:**
  - `Purse.java` becomes a thin wrapper over `Money`, kept so old call sites compile during the batches. `Purse.count(p, Items.EMERALD)` gets a deprecation log.
  - `Approaches.emeralds/take` become `Money.count` and `Money.charge`.
- **Compat both ways:**
  - Kingdoms `PowersCompat` gains `ItemStack belt(Player)` and `void setBelt(ServerPlayer, ItemStack)`. They need Powers to add a public `Baubles.belt(Player)` and `Baubles.setBelt(ServerPlayer, ItemStack)` (BELT_SLOT 3 goes through `setWorn`).
  - Powers and Kitchen `KingdomsMoney`: `count`, `charge`, `pay` as MethodHandles to `Money`, with emeralds as the fallback. The coin icon is looked up with `BuiltInRegistries.ITEM.getOptional(Identifier "jaymoskingdoms:silver_coin")`.
  - Kitchen has no Powers dependency, so it gets its own small copy of `KingdomsMoney`.

## 3. Batches (disjoint files; run 2 at a time, one editor per worktree)
**B0, foundation (serial, first):**
- Kingdoms: `money/Coin`, `PurseContents`, `Money`, `CoinPurseItem`, `CoinPress` and its menu, `Changer`, `CountingTable`, `MoneyItems` (registration), `PowersCompat`, `Purse`, `JaymosKingdoms` (init and attachments), `lang/en_us.json`, recipes, models, the belt tag data file.
- Powers: `Baubles.java` (`belt`/`setBelt`, plus `SHOWN` also carrying a hip item tagged `jaymospowers:hip_shown`), `KingdomsMoney`, the `c:ingots/silver` tag.
- Gate: build all three projects, then a verifier pass.

**B1, Kingdoms talk and services:** each site's price check and take becomes `Money.canPay/charge(Money.silver(X))`.
- `Approaches`:
  - Option gating at 1098/1106/1113/1115/1261/1440 uses `canPay`.
  - The takes at 1201/1263/1266/1324/1357/1369/1387/1399/1410/1416/1449 use `charge`/`chargeUpTo`.
  - The bout win at 1445 uses `pay`. The GIFTS entry at 267 becomes a silver coin.
  - `toTreasury` (1502) is unchanged, since it counts silver.
- `Classes` (478/517/525), `Skills` (524/538/541), `Restoration` (175 accepts coins or a purse in hand; 649/652/702/720/728/733), `Healing` (constants only).
- `Temperaments` (228/297/380: its loose-only helper is deleted), `Tempers` (228/297/380; the tithe at 316 is unchanged), `Tailors` (104/124), `TailorCostumes` (59/63/82).
- `Drifters` (241: a coin or purse in the main hand; 245/249), `Retinue` (505/510), `Retaliation` (95: coins or a purse in hand; payoff through `charge`).
- `StarFolk` (177/190/220/236/250), `Captains` (1752-1772, which also fixes the bundle bug), `StoryHard` (319/321/371), `Tribes` (340-358), `client/TalkScreen` (doc only).

**B2, Kingdoms rewards and quests:**
- `Quest.Reward`: the codec key `emeralds` is kept and read as silver, and an optional `copper` field is added. `Quests.rewards` (616-639) pays through `Money.pay`.
- `QuestBoard`, `TownNotices`, `Grudges` and `DisasterRelief`: constants only. Rename `*_EMERALDS` to `*_SILVER`.
- `Bounties` (509 stack list → `Money.stacks`; 799/802/849 pay; 862/864 `chargeUpTo`; 874).
- `Manacles` 169, `Captives` 499, `Assassins` 938 (pay) and 964 (drops `Money.stacks(fee)`), `Trials` 515, `Deeds` 80/377, `Neighbours` 458.
- `SharedHistory` 365, `LifeStory` (the start purses at 1740-1830 become a filled `coin_purse`; the answer icon at 402 becomes a coin), `forms/Bouts` 274/287, `Staredowns` 310.
- `Careers` (816 `Storage.take` of silver coins; 827/894/900/1221/1223), `client/StoryFigure` (props hold coins).

**B3, Kingdoms shops, fares and transport:**
- `HavenWares` 173/181, `Underworld` 273/820/877/905/922/928/991, `Pedlars` 274, `Remedies` 115 and 124 (`isOurs` matches `Coin`).
- `RoamingCaravans` 584: the payment becomes coins. The caravan goods at 515/521/524 stay emerald, as a gem good.
- `Meals` 87 (`BITS = 10`), 713-724 (offers priced in coins), 229/781/788 unchanged.
- `MerchantOrders` 262/338/489/521/526/552, `OffWorldGoods` (field rename), `whaling/WhaleTrade` 99/104, `nether/CinderForge` 308/337, `nightfall/Callings` 115/143/147/184.
- `Stables` 608/612/623/786/991/1238/1285, `Rickshaws` 691/695/735, `Cabs` 319, `Milestones` 433/437, `Journeys` 242.
- `Bookies` 181/184 (the stake is coins or a purse in hand, read with `Coin.valueOf`), `GreatWars` 784, `FightPit`.
- `Treasuries.traded` (58-65): coin value replaces emerald counting. The EMERALD_BLOCK case is dropped.
- The new mixins from section 4.

**B4, Kingdoms crime, treasury and commands:**
- `Crooks` 343/644-647/694-699/728 and `Pickpocket` (section 5), `Theft`.
- `KingdomCommands` 45/112/318-333/349-358 (deposit and withdraw take a silver amount and pay through `Money`), `Crowns` 342-359.
- `WarParties` 444/594 (`Money.stacks`), `Wars` 906, `Raids` 499 (valuables: coins first), `Crewmen` 541, `Whalers` 810, `SwornCaptains` 574, `TribePacts` 246/255 (the hoard holds coin stacks).
- `Storage` seeders (`Camps` 330, `OrcCamps` 102, `GoblinVillages` 101, `IllagerCamps` 106, `PirateCoves` 202/515) put silver coins. `Lockpicking` 487-491 adds coin piece values.

**B5, Kingdoms loot:**
- Hoards → `Money.stacks`: `CampBuildings` 564, `IllagerBuildings` 525, `OgreHolds` 444/449, `OrcBuildings` 588, `GoblinBuildings` 510, `QuestFoes` 259, `TreasureWrecks` 191, `Corsairs` 752-793, `SkeletonLord` 273.
- `lootgoblins/LootGoblins` 62/97: the ids become `jaymoskingdoms:silver_coin`.
- Edit the generators, not the JSON: `tools/gen_beasts.py`, `tools/gen_nightfall.py` and their outputs (blightwing, cave_ogre, gorgon, the giants, night_bleeder, ogre).
- New `LootSwap.java`: `LootTableEvents.MODIFY_DROPS` (fabric-loot-api-v3 4.0.9; the class is in the jar, the callback signature still needs checking). It turns emeralds into silver coins **only** for loot keys under `chests/`, `entities/` and `gameplay/hero_of_the_village/`. Mined ore drops stay emerald.

**B6, Powers:**
- `ZFighterTournaments` 644-651 and `ZFighterSagas.give` (485) pay through `KingdomsMoney.pay(silver(n))`. `ZFighterRaids` 177-182, `ZFighterInvasions` 733/760, `ZFighterWishes` 394 and `ZFighterSpaceMissions` 635 do too. The 105/639 Glacia emerald loot stays a gem.
- `ZFighterSites` 429-487 and `ZFighterAfterlife` 673/731-788 use `KingdomsMoney`. `CostumeTailoring` 131-188 is replaced by `KingdomsMoney`. `Fractures` 84-89 accepts coins or a purse in hand.
- Client: `CostumeScreen` 447/451 (coin icon), plus the hip purse (section 5).
- Data: no trade JSON edits (section 4 converts them at runtime). `WebSymbioteCollect` 255: add coins and purses to the valuables.

**B7, Kitchen:** `Butchers` 395-401 and `ButcherScreen` 50/99 use Kitchen's `KingdomsMoney`. Lang lines 350/366/367 change from "emeralds" to `%s` money text. The trade JSON stays (converted at runtime).

## 4. Villager trades (vanilla, MCA, mod JSON), checked in 26.3 sources
Trades in 26.3 are data. `VillagerTrade` has the codec fields `wants`, `additional_wants`, `gives`, `max_uses`, `xp`, `reputation_discount` and others. `TradeCost(Holder<Item>, ...)` becomes an `ItemCost` through `TradeCost.toItemCost(LootContext, int)`. Every trade is built in **`VillagerTrade.getOffer(LootContext)`**, which returns a `MerchantOffer` or null. It is reached from `AbstractVillager.addOffersFromTradeSet(ServerLevel, MerchantOffers, ResourceKey<TradeSet>)`, which `Villager.updateTrades` and `WanderingTrader.updateTrades` both call. That one point covers vanilla, MCA's `data/mca/villager_trade/*`, and the Kitchen, Powers and Kingdoms trade JSON, with **no JSON edits** and no hard reference from Kitchen or Powers to Kingdoms items.
- **`mixin/VillagerTradeCoinMixin`** on `VillagerTrade`: `@Inject(method="getOffer", at=@At("RETURN"), cancellable=true)`. It replaces the result with `Coinage.convert(offer)`, built with `new MerchantOffer(ItemCost baseCostA, Optional<ItemCost> costB, ItemStack result, int uses, int maxUses, int xp, float priceMultiplier, int demand)`. Conversion rules:
  - An EMERALD cost of n ≤ 64 becomes `silver_coin` n.
  - n > 64 becomes `gold_coin` n/10, and the remainder goes into costB as silver if costB is free; otherwise it rounds up to gold.
  - An EMERALD_BLOCK cost becomes 9 silver.
  - An EMERALD result becomes the same number of `silver_coin`.
  - The money changer's own offers are tagged with a marker and skipped.
  - `ADDITIONAL_TRADE_COST` (the enchanted-book surcharge) is already folded in before RETURN.
- **Old villagers in old saves:** `@Inject(method="readAdditionalSaveData", at=@At("TAIL"))` on `AbstractVillager`, with `@Shadow MerchantOffers offers`, maps `Coinage.convert` over each offer once. It is idempotent: there's nothing left to convert after the first pass.
- **Paying from the purse in the trade screen:** `@Inject(method="moveFromInventoryToPaymentSlot", at=@At("TAIL"))` on `MerchantMenu` (private, 26.3 signature `(int paymentSlot, ItemCost cost)`). Server only, through `trader.getTradingPlayer() instanceof ServerPlayer`. If the slot is short of the coin `cost` asks for, it breaks larger coins from the worn or carried purse and makes change into it. `tryMoveItems(int)` is the caller.
- `Treasuries.traded` (via the existing `VillagerTradeMixin.notifyTrade`) counts coin value instead of emeralds.
- **MCA:** its `hire.json` conditions (emeralds 5 and 10) are not covered by the mixin. Leave MCA's Hire alone, since `Drifters` is our own hire. Add `jaymoskingdoms:silver_coin` to MCA's `taxesMap` through `McaCompat` at runtime, not by editing `run/config`.

## 5. Purse on the hip; pickpockets
- **Hip render (Powers).** `Baubles.show` today sends `{hip lantern, pole}`. Change the first entry to "hip item": a lantern **or** an item in the new tag `jaymospowers:hip_shown`, which Kingdoms fills with `coin_purse`.
  - Client: in `WornLanterns.Layer`, add a branch so a non-lantern hip item is drawn with the item renderer, scaled to 0.35, on the right hip. It swings with the same sway, and its model shows fullness through `custom_model_data`.
  - `LdlWornLanternMixin` must skip non-lanterns.
  - A purse in the belt slot pays first and its pickpocket chance is higher. A purse in the pack is safer but slower: `Money` takes from pack purses only after the belt purse.
- **`Crooks.lift`, a crook robbing a player (694-699):**
  - If the player wears a belt purse, with `PURSE_LIFT_CHANCE = 0.35` (×1.5 at night or in a crowd) the **whole purse** is taken through `PowersCompat.setBelt(EMPTY)` and becomes the debt `owe(crook, ItemStack purse)`. It comes back if the crook dies within RETURN_RANGE.
  - Otherwise a handful is taken: `Money.chargeUpTo(1 + rand(PICK_SILVER=4)) silver`, which is owed back as coin stacks.
  - Crooks pick marks who wear a visible purse first (`MARK_PURSE_WEIGHT = 3`).
  - Muggings (644-647) and `scammed` (343) owe `Money.stacks(cp)`.
  - The fallback purse at 728 is a small filled `coin_purse` (PURSE_MIN..MAX silver).
- **`Pickpocket.loot`, the player robbing NPCs (POCKETS at 90):**
  - Replace the emerald pocket with a filled purse: 4..30 cp, weight 6. Guards and merchants get ×2 cp.
  - Add a copper coins pocket (1..6, weight 3). Keep the gold and iron nuggets.
  - `Skills.pocketTries` and `CARRIED_SHARE` are unchanged.
  - Optional for Andy: player-on-player lifting of a worn purse, which reuses `Reach`.

## 6. What stays emerald
- Ore and worldgen (VeiledOreBlock, FarWorlds, cinder_emerald, moon_plains).
- Enchant ingredients (`EnchantRecipes` 60-70) and the tinkering `Modifier.EMERALD`.
- Alchemy essences, trim colours (`Outfits` 477, `GlowingTrimMixin`), `BeaconWatch` emerald blocks.
- `QuietOne` ore checks, `Moons` 514 and `SpiderKinds` 211 gem drops, Glacia planet loot.
- Caravan gem goods (`RoamingCaravans` 515-524), `Rickshaws` 463 (not money).
- Vanilla mined drops, and the MCA recipes (civil_registry, book_supporters).
- Emeralds are now sold to the changer (9 cp) or used as ingredients.

## 7. Risks and migration
1. **Saved emerald stacks:** players' chests and inventories, `Storage` storehouse stocks (wages are paid from `Storage.take(EMERALD)`), Crooks debts (`owe` ItemStacks), MCA villager packs (Crewmen lays), mailed `Post` parcels.
   - Mitigation: `Storage` converts EMERALD to silver_coin 1:1 on load.
   - `Crooks` converts EMERALD debts when it pays them out.
   - Player-held emeralds stay gems. For `PAR_DAYS = 14` after first load of the new version (a world flag in Kingdoms saved data), the changer buys them at par (10 cp) so nobody is robbed by the patch.
2. **Numbers, not items, survive unchanged:** `Faction.treasury`, `GreatWars` Ledger debts, `MerchantOrders.paid`, `Journeys.fare`, `Quest.Reward.emeralds` and Bounties prices are all in silver now, at the same value, so they need no conversion. Keep the codec key `emeralds`; renaming it would wipe saved quests.
3. **Old villager offers:** handled by the `readAdditionalSaveData` hook. Wandering traders already spawned are covered by the same hook. Offers built in code (Haven, Underworld, Remedies, Meals) are changed at source in B3, and the hook catches any saved copies.
4. **Price above 64** in one trade slot: handled with gold in costA and silver in costB. Check that `MerchantOffer.getCostA()` reputation and special-price math on a gold count doesn't round a 65-silver trade to 0 or 1 gold. Clamp to ≥1 and re-split.
5. **Mint inflation:** gold farms and the 100 cp gold coin. Daily caps and the realm-only press are the guard. The constants should be tuned in play.
6. **Garlic:** `Garlic.java:87` treats any id containing "silver" as anti-vampire, so silver coins (and purses held? no, only coins) hurt vampires. Keep it as a feature or add an exclusion. Andy's call.
7. **One mod without the other:** without Kingdoms, Powers and Kitchen pay emeralds through the fallback. Without Powers, the purse works from the pack but nothing is drawn on the hip. Kitchen and Powers trade JSON must never name Kingdoms ids (that's why the conversion is a mixin).
8. **Bundles:** the API counts bundles, which also fixes the loose-only inconsistency the scan found at about 12 sites.
9. **Versions:** bump the version of all three mods. This adds a new data component, packets and a menu, so old clients will break.
10. **Open questions for Andy:**
    - Are the coin ratios right: 10 cp to a silver, and 1 silver to an emerald?
    - Should the Coin Press be realm-only?
    - Should players be able to pickpocket another player's purse?
    - Should coins hurt vampires (the Garlic rule)?
    - Should the 14-day par grace for old emeralds go in?
## Defaults taken (Andy can overrule)
- Ratios as above: 10 copper = 1 silver = 1 old emerald; 10 silver = 1 gold.
- Coin Press only strikes inside a realm's settlement (tuning in constants).
- No player-on-player purse lifting yet.
- Coins don't count as silver for Garlic's anti-vampire rule (excluded).
- The 14-day par grace for old emeralds goes in.
