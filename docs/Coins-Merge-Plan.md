# Coins onto master's Currencies (Andy chose "Combine: culture coins replace emeralds", 2026-11-04)

Andy's asks, in order: "Coins replace emeralds" (emeralds become a gem sold to a money changer); "Diversify the
currencies, it can change based on the prominent trading hub's currency"; "some you come across will still trade in
emeralds"; "they must speak about this and answer you if asked"; "people who use coin generally look down upon
emerald traders as outdated luddites".

Result: master's culture coins (Currency/Currencies) are the money everywhere; emeralds are a gem except with the
keepers; our purses (belt slot, hip, theft), MoneyTalk, keepers and Kitchen pay are kept and reworked onto master.

Defaults (tell Andy, he can change): one universal small coin, the copper bit; foreign realm coin taken at a 10% cut
outside the trade screen; changer buys emeralds at 90%; no Coin Press for now.

Versions: Kingdoms 0.7.498, Powers 0.20.0, Kitchen 0.5.56 (renumber our "(0.7.487)", "(0.19.74)", "(0.5.54)" tags).

## Findings
- Conflicts (git merge origin/master): MODLOG.md, gradle.properties, kingdoms/MODLOG.md, kingdoms/gradle.properties,
  kitchen/MODLOG.md, kitchen/gradle.properties, kingdoms lang en_us.json, loot_table/entities/gorgon.json, and Java in
  kingdoms/src/main/java/com/jaymoskingdoms/: Approaches, Bounties, Captains, Careers, GoblinBuildings, GoblinVillages,
  IllagerCamps, Manacles, MerchantOrders, OgreHolds, OrcBuildings, OrcCamps, Pedlars, PirateCoves, Raids, Remedies,
  Treasuries, TribePacts, Tribes, Wars, whaling/WhaleTrade.
- Master went faction-aware on every money line: Currencies.of(server, f), payEmeralds, give, fine, asked; greenskins
  on BentIngots/HordeEconomy; Raids/Wars on Plunder. Ours went to Money.silver(n)/Coin.SILVER.
- Master has a money-changer already: Professions.CHANGER, Currencies.staff, counter Currencies.changes. Our Changer,
  MoneyChangers, Counting Table are redundant.
- Master prices trades on open: VillagerPriceMixin on Villager.updateSpecialPrices calls Currencies.localize, keeping
  each offer's worth in a Base attachment (thousandths of an emerald, "milli") with the coin first priced in. So our
  VillagerTradeCoinMixin and Coinage.convert are not needed.
- Master's treasury unit (emerald worth) equals ours (1 silver = 1 old emerald): no treasury migration.
- Master uses emeralds as universal change: Pay(coin, coins, emeralds) and fallbacks in asked/paid. Needs a replacement.

## Design
- Unit of account stays master's milli. `Money.silver(n)` -> `Money.worth(n)` (n*1000); `Money.SILVER` -> `Money.UNIT`
  (1000). Mechanical sed across Kingdoms, Powers, Kitchen.
- Money is master's Currency enum (SILVER, HACKSILVER, DINAR, SHELLS, MON for towns; BENT greenskins; GOLD pirates).
  Add `BIT(100)` at the end: the copper bit, universal small change replacing emerald change. Reuse our
  jaymoskingdoms:copper_coin item/model/texture, renamed "Copper Bit". HACKSILVER/SHELLS half-bit leftovers are absorbed
  by master's PAY_SLACK and rounding.
- Currency.EMERALD stays (unit of account, price notation of trade data, keepers' coin, ids in saved Base/Order). Add
  predicates beside human(): `coin()` = realm coin (human, not EMERALD, not BIT); `money()` = what a purse holds and
  Money.charge takes (human, not EMERALD). Every master loop `x.human() && x != Currency.EMERALD` becomes `x.coin()`
  (rate() mean must not count BIT, stored(), report(), Careers fallback loop).
- What a payer takes: settlement's own coin at par, bits at par, any other realm coin at going rate less
  `FOREIGN_CUT = 0.10` (switch `FOREIGN_TAKEN`). Never emeralds. Change in local coin and bits.
- Trade screen stays strict: a slot needs the exact coin. The changer has a real job; MoneyTalk explains it.
- No settlement (wild villages, wanderers, Z Fighter worlds, Kitchen outside towns): `Currencies.at(level, pos)` =
  faction around (160-block range like report), else SILVER. `of(server, null)` becomes SILVER after auditing null
  callers (Bounties court main one). Non-greenskin non-pirate barbarian camps keep EMERALD only for hoard seeding;
  payments fall back to SILVER.
- Keepers: `Coinage.keepsEmeralds` moves to `Currencies.keeper(Entity)` (same UUID lot: 15% villagers, 40% wanderers,
  only real traders: not NONE/NITWIT, not MCA CAN_NOT_TRADE, not pedlars/Ignis). In localize a keeper is priced with
  Currency.EMERALD; Base remembers true worth so a keeper on a master save goes back to emeralds next open. A keeper
  never becomes a changer; keepers excluded from MerchantOrders pricing.
- Widen localize to AbstractVillager; coin = keeper ? EMERALD : f != null ? of(f) : at(level, pos). Wanderers have no
  updateSpecialPrices: localize them from our AbstractVillager.setTradingPlayer HEAD hook (VillagerCoinMixin), only for
  non-Villager merchants. Confirm in 26.3 sources that offers are sent after setTradingPlayer.
- Emerald as a gem: master's changer counter gets gem rows: `EMERALD_BUY = 0.90` (emerald -> local coin),
  `EMERALD_SELL = 1.12`, emerald block = nine emeralds, `DIAMOND_WORTH = 4` (buy only), bits <-> local coin at par.
- Purses hold mixed coins: PurseContents = record around immutable EnumMap<Currency,Integer>, only money() currencies.
  Codec: unbounded map Currency (gains StringRepresentable by id()) -> NON_NEGATIVE_INT, keep the 640-coin cap,
  `Codec.withAlternative` for our dev format {copper,silver,gold} (copper -> 1 BIT, silver -> 2 SILVER, gold -> 5 DINAR).
  Stream codec ByteBufCodecs.map over ordinal and VAR_INT. value() = standard worth, fullness() = coin count; model
  fullness, hip render, belt slot unchanged. Tooltip lists each coin via Currencies.amount. CoinPurseItem gather/fill
  key on Currency.byItem(...).money().

## Money API over Currencies
- count(player): all money() carried at standard worth (display, Kitchen "have"). count(player, f): what f takes.
  Master's Currencies.purse delegates to it (master canPay/asked become purse- and bundle-aware).
- canPay(player, milli) = canPay(player, at(player), milli), plus an f overload.
- charge(player, f, milli): all-or-nothing, our Source model (loose, belt purse, pack purses, bundles) keyed by
  Currency. Order: exact local coin, bits, foreign coins. If short, overpay with the smallest coin covering the rest;
  change back as Currencies.pay(local, over) into its source. Commit all sources at the end.
- chargeUpTo(player, f, milli) returns milli taken. Master's Currencies.fine becomes a wrapper returning the
  emerald-rounded count so master callers (Approaches, Bounties) work unchanged.
- pay(player, f, milli) = Currencies.pay(of(f), milli) deposited worn purse first, then inventory, then dropped. Coins
  a purse can't hold (BENT, GOLD) go straight to inventory. Master's Currencies.give(player, Pay) and give(player, c, n)
  reroute through the same deposit.
- stacks(f, milli) = Currencies.pay(c, milli).stacks(); text(f, milli) = Currencies.amount(owe(...)) for prices,
  amount(pay(...)) for payouts. Faction-less overloads stay (resolve coin by position).
- Master's Pay: field emeralds -> bits; milli() = coins*base + bits*100; stacks() emits copper bits; split/splitError
  use 100. In localize the change in empty slot B and the asked/paid fallback use BIT: town coin, then BIT, then first
  coin, never EMERALD unless the town coin is EMERALD.
- Prices over 64 local coins: ask in the next coin up by worth (`BIG_COIN`, in practice DINAR) with local coin in
  slot B. Check vanilla discount math (getCostA with special price and demand) never rounds to 0 or 1.
- Coinage keeps held(stack) (purse or coin stack worth held in hand: Bookies, Restoration, Drifters, Retaliation,
  Powers Fractures) and fill() (trade-screen auto-fill, only the exact asked Currency from purses and bundles).
  convert is deleted.

## Drop from our branch
- money/Coin; money/Changer and MoneyChangers; Coin Press (CoinPress, CoinPressBlock, CoinPressMenu,
  client/money/CoinPressScreen, press part of MoneyClient, MINTED attachment); CoinPileBlock; Coinage.convert and
  mixin/VillagerTradeCoinMixin (and its entry in jaymoskingdoms.villagers.mixins.json); the readAdditionalSaveData
  injection in VillagerCoinMixin (keep its setTradingPlayer hook).
- Items/blocks/data: silver_coin, gold_coin, the three *_coin_stack items and pile blocks, coin_press, counting_table,
  all their blockstates, models, textures, loot tables, recipes (stack/unstack too), the acquirable_job_site POI tag
  file, their mineable tag entries. tools/gen_coins.py keeps only the copper bit and purse outputs.
- Revert our emerald -> silver_coin edits in entity loot JSONs (blightwing, cave_ogre, gorgon, highfield_giant,
  irradiated_giant, night_bleeder, ogre) and tools/gen_beasts.py, gen_nightfall.py to master. LootSwap converts at runtime.

## Keep, reworked
- money/Money, PurseContents, CoinPurseItem, MoneyItems (copper bit, purse, COINS component). Purse recipe: leather,
  string, one copper bit.
- PowersCompat belt handles, belt and hip_shown tags, purse textures.
- Crooks (purse lift; handfuls via chargeUpTo owed back as stacks), Pickpocket (victim's settlement coin in purse).
  Old emerald debts in Crooks returned as the emeralds taken.
- LootSwap: an emerald becomes Money.stacks(at(level, pos), n*1000), else SILVER; same folder filter; mined ore stays.
- MoneyTalk: changer() -> Currencies.changer, keeper() -> Currencies.keeper; changer quotes emerald rate in local coin;
  coin folk say which coin the town uses and why (hub's name when resolve chose a hub; have resolve record hub id in
  RESOLVED; land otherwise); new remark "foreign coin, see the changer"; keep "no_emeralds". MCA prompt describes local
  coin, hub and keepers; sneer lines stay.
- InteractActions.MONEY and Directions entries stay, changer predicate on master's profession.
- Bookies, Restoration, Drifters, Retaliation, Lockpicking (piece worth via Currency.byItem), LifeStory start purses
  (birthplace coin), Quests (Reward.emeralds read as worth; our copper field read as bits).
- Powers: Baubles belt/setBelt, hip render (WornLanterns, LdlWornLanternMixin), core/KingdomsMoney (unit 1000;
  without Kingdoms milli/1000 emeralds; icon id jaymoskingdoms:realm_silver; add text(ServerPlayer,long)).
- Kitchen: KingdomsMoney same; Butchers OpenButcher carries price as server-made Component plus coin stack
  (ItemStack.STREAM_CODEC) instead of int silver; ButcherScreen draws those; lang %s.

## Steps
1. Merge origin/master; resolve the conflicts: version lines master's then bump (see Versions); append our MODLOG
   entries; take master wholesale for GoblinBuildings, GoblinVillages, OrcBuildings, OrcCamps, OgreHolds, IllagerCamps,
   Pedlars, Captains, Tribes, TribePacts, PirateCoves, Raids, Wars, Remedies, WhaleTrade, Treasuries, Manacles,
   Bounties, Careers, gorgon.json. Approaches: master's canPay/asked/fine helpers; leftover gates
   `emeralds(player) >= a.price` -> canPay(player, a, price); bout payout Money.pay(player, f, worth(price)); delete our
   payUpTo/silver/emeralds helpers and fix callers. MerchantOrders: master, with its loose-only pay(player, coin, price)
   replaced by new Money.chargeCoin(player, Currency, n) (purses and bundles too); refunds via Money.give; EMERALD
   orders refunded as Currencies.pay(local, paid*1000). lang: master's file plus only our new keys (money.*, coin
   purse, copper bit, MoneyTalk, Interact/Directions, Kitchen-facing %s strings); drop our emerald->silver rewordings
   where master's string takes a %s amount.
2. Currency/Currencies as above.
3. Money package as above; delete dropped classes and resources; JaymosKingdoms registration.
4. Kingdoms sites: sed; fix everything naming Coin.*, MoneyItems stacks, Changer.*, Coinage.*: Bookies, Camps, Deeds,
   HavenWares, LifeStory, Lockpicking, LootSwap, Meals, MoneyTalk, Neighbours, Pickpocket, RoamingCaravans,
   SharedHistory, Underworld, nether/CinderForge, nightfall/Callings, client/StoryFigure, the two mixins. Offers our
   branch built in Coin.SILVER (HavenWares, Underworld, Meals, Remedies, CinderForge, Callings) go back to master's
   emerald notation for localize. Meals BITS stays 10. Camps and storehouse seeders back to master. Pass faction where known.
5. Careers: storehouse emeralds counted as town reserve at worth, paid in local coin and bits.
6. Powers and Kitchen bridges. Build all three.
7. docs/Coins-Plan.md rewritten to this design; renumber tags.
8. Review: grep Items.EMERALD used as money (master's Purse.count/take(EMERALD) sites), Coin., silver_coin.

## Risks
Big prices (BIG_COIN); wanderer hook order; strict slots with no changer in towns under 8 folk (Coinage.fill may swap
foreign coin at FOREIGN_CUT); atomic mixed-coin charge; BIT leaking into values() loops (rate mean, changer counter,
report); the lang merge; coin ids containing silver/gold/copper (Powers MeltingRecipes.METAL_WORDS, Garlic.java:87
silver hurts vampires, TierGate "silver") so exclude coins via Currency.byItem; Kitchen OpenButcher protocol change;
of(null) shift at barbarian camps.
