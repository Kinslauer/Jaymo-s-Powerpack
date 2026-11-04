# Coins replace emeralds: the design as built (Kingdoms 0.7.498, Powers 0.20.0, Kitchen 0.5.56)

Andy's asks, in order: "Coins replace emeralds" (emeralds become a gem sold to a money changer); "Diversify the
currencies, it can change based on the prominent trading hub's currency"; "some you come across will still trade in
emeralds"; "they must speak about this and answer you if asked"; "people who use coin generally look down upon emerald
traders as outdated luddites". When both patches met he chose **"Combine: culture coins replace emeralds"**.

Result: master's culture coins (`Currency`, `Currencies`) are the money everywhere. Emeralds are a gem, except with the
keepers. Our purses (belt slot, hip, theft), `MoneyTalk`, the keepers and Kitchen's butcher pay are kept, reworked onto
the culture coins. `docs/Coins-Merge-Plan.md` has the merge findings and the order the work was done in.

Defaults (Andy can change them): one universal small coin, the copper bit; a foreign realm's coin is taken at a 10% cut
outside the trade screen; the changer buys emeralds at 90%; no Coin Press.

## 0. Decisions in one place
- **Kingdoms owns the economy.** Powers and Kitchen reach it only through a `KingdomsMoney` compat class each (a
  MethodHandles bridge to `com.jaymoskingdoms.money.Money`). Without Kingdoms they fall back to emeralds one for one.
- **The unit of account is the milli**, a thousandth of an emerald. `Money.worth(n)` is n emeralds' worth (n*1000) and
  `Money.UNIT` is 1000. A price written as "N emeralds" in old data or code means `worth(N)`, paid in the town's coin.
  `Faction.treasury` is counted in emeralds' worth as before, so there is no treasury migration.
- **Powers keeps the belt slot and draws the purse on the hip.** Kingdoms adds its purse to Powers' belt tag with a data
  file only; the tags merge. Kingdoms reads and writes the worn purse through `PowersCompat` handles.

## 1. The coins
| Coin | Worth (milli) | Used by |
|---|---|---|
| Emerald | 1000 | the unit of account, price notation, the keepers' coin; a gem otherwise |
| Realm Silver | 500 | the Common realms (plains) |
| Hacksilver | 250 | the North (taiga, snow) |
| Gold Dinar | 2000 | the South (savanna, desert, badlands) |
| Shell String | 250 | the Wildwood (jungle, swamp) |
| Mon | 200 | the Blossom land |
| Bent Copper | 500 | the greenskins only |
| Gold | 1800 | the pirates only |
| **Copper Bit** | 100 | every town: the change for any price (`Currency.BIT`, item `jaymoskingdoms:copper_coin`) |

- Predicates on `Currency`: `human()` (every town's coin, the emerald and the bit too), `coin()` (a realm's coin: human,
  not the emerald, not the bit), `money()` (what a purse holds and `Money.charge` takes: human, not the emerald).
  Loops that mean "the realms' coins" (the going rate's mean, the changer's counter, a storehouse report) use `coin()`.
- A town's coin is `Currencies.of(server, faction)`: its own land's coin, or a trading hub's when a much richer kingdom
  is near (`hubName` says which). A vassal takes its overlord's. With no settlement (wild villages, wanderers, Kitchen
  shops in the wilds) it is `Currencies.at(level, pos)`: the settlement within 160 blocks, else realm silver.
- **What a payer takes:** the settlement's own coin at par, bits at par, any other realm's coin at the going rate less
  `FOREIGN_CUT = 0.10` (switch `FOREIGN_TAKEN`). Never emeralds. Change is made in the town's coin and bits.
- A price over 64 of the town's coin is asked in `BIG_COIN` (the dinar) with the rest in the town's coin.

## 2. Emeralds as a gem
- Master's money changer (`Professions.CHANGER`, counter `Currencies.changes`) has gem rows: an emerald is bought at
  `EMERALD_BUY = 0.90` of its worth in the local coin and sold at `EMERALD_SELL = 1.12`; an emerald block is nine; a
  diamond is bought at `DIAMOND_WORTH = 4` and not sold; bits trade for the local coin at par.
- **Keepers** (`Currencies.keeper`): 15% of villagers and 40% of wandering traders, fixed by their UUID, only real
  traders (not jobless, nitwit, MCA's can't-trade guards and workers, pedlars, Ignis Vael, changers). A keeper's offers
  are priced in emeralds when they open; each offer remembers its true worth, so a keeper on an older save goes back to
  emeralds on the next open. Merchant orders skip keepers.
- Emeralds in loot become coin of the land (`LootSwap`); mined ore still drops emeralds. Enchanting, trims, beacons,
  tinkering's emerald modifier and caravan gem goods are unchanged.

## 3. Prices on the trade screen
- A trader's offers are made coins when the screen opens: `VillagerPriceMixin` calls `Currencies.localize` after vanilla
  updates special prices; wandering traders (no special prices) are localized from the `setTradingPlayer` hook in
  `VillagerCoinMixin`. Each offer keeps its worth in a `Base` attachment, so a price never drifts with rounding.
- A price that won't land on whole town coins has its goods scaled, or the rest asked in copper bits in slot B.
- The trade screen stays strict: a slot needs the exact coin. `MerchantMenuCoinMixin` and `Coinage.fill` fill the
  slot from purses and bundles with the exact coin asked. Another realm's coin is the changer's work; `Money.charge`
  (fees, fines, tolls, wages) takes foreign coin at the cut.

## 4. Purse and `Money`
- **Coin purse:** `PurseContents` is a record around an immutable `EnumMap<Currency, Integer>` (only `money()` coins),
  at most 640 coins. Codec: an unbounded map of coin id to count; the dev-format `{copper, silver, gold}` still reads
  (copper as bits, silver as two realm silver, gold as five dinars). `value()` is the standard worth, `fullness()` the
  coin count for the model. Recipe: leather, string, one copper bit.
- **`Money` API:** `count(player)` (all money at standard worth, for display), `count(player, faction)` (what that realm
  takes), `canPay`, `charge` (all or nothing; order: exact local coin, bits, foreign coins; if short it breaks the
  smallest coin that covers the rest and puts the change back in the source it came from), `chargeUpTo` (how much was
  taken), `chargeCoin(player, coin, n)` (exactly n of one coin, from the pack, purses and bundles), `pay` / `deposit`
  (worn purse first, then the pack, then the ground; coins a purse can't hold go to the pack), `stacks`, `text`
  (a price as the town asks it) and `textPaid` (a payout), `coin(player)` (one coin of the land, for Kitchen's screen).
  Creative players always pass. Master's `Currencies.fine`, `give`, `canPay`, `asked` and `purse` delegate to it.
- **Sources** are loose stacks, the worn belt purse, purses in the pack and bundles; all sources commit at the end.

## 5. What was dropped from the first coinage
The silver and gold coin items, the three ten-coin stacks and their pile blocks, the Coin Press (block, menu, screen,
mint cap), the Counting Table and our own money changer, `Coinage.convert` and `VillagerTradeCoinMixin`. Entity loot
files that had emeralds swapped for silver coins went back to master's (`LootSwap` converts at runtime).
`tools/gen_coins.py` keeps only the copper bit and purse art.

## 6. Systems on the new money
- **Crooks** go for a visible belt purse first (`PURSE_LIFT_CHANCE`); a mugging takes a handful of whatever you carry
  (`chargeUpTo`), owed back as coin stacks of the land. Old debts in emeralds are returned as the emeralds taken.
- **Pickpocket:** the victim's settlement coin in a small purse, or a copper bit; guards and merchants carry double.
- **Careers:** wages are paid in the town's coin out of the storehouse's coin and bits; its emeralds and other realms'
  coin are a reserve counted at worth and struck again as the town's coin; a poor town still pays the floor wage. Stalls
  take the town's coin. Pilot fares and prospector bonuses are paid in the coin of the land.
- **Quests:** a reward is paid in the giver's coin; the codec field `emeralds` reads as worth and `copper` as bits.
- **Merchant orders:** a ware is asked in the town's coin (bits when it won't land, none over 64), taken with
  `chargeCoin`, put in the storehouse and refunded as the coin paid (an old emerald order as the town's coin, worth for
  worth). Off-world wants pay the town's coin.
- **Life story:** start purses are in the birthplace's coin. **Lockpicking** counts coin by worth.
- **Approaches, Bookies, Restoration, Drifters, Retaliation:** gates and payments use the master helpers
  (`canPay(player, approach, price)`, `Currencies.fine`, `Money.pay(player, faction, milli)`).
- **Garlic and werewolves:** realm silver and hacksilver are coins; they are not the metal their names say.
- **Powers:** `core/KingdomsMoney` (unit 1000, `worth`, `text(player, milli)`, `isMoney` by exact id), belt and hip
  render, Z Fighter prizes and prices, the costume tailor, the priest; the melting gap log skips coins.
- **Kitchen:** `KingdomsMoney` the same; `OpenButcher` carries the price as a server-made Component and one coin as an
  ItemStack (plus the cost and what you carry in milli), and `ButcherScreen` draws those.

## 7. Talk (`MoneyTalk`)
- Interact has Ask about Money; Ask the Way has Who Takes Emeralds and A Money Changer.
- A keeper says so. The changer quotes his emerald rate in the town's coin. Anyone else names the town's coin and why
  (the trading hub's name when resolving picked a hub, the land otherwise), and says foreign coin is the changer's job.
- Unasked, at most once every 5 minutes to the same player: a keeper on opening trades, a coin trader when you come
  with emeralds and no coin. MCA's chat prompt describes the local coin, hub and keepers.
- Coin folk sneer at emerald traders as relics; keepers know it and don't care.

## 8. Tuning
All in `static final` constants: `Currencies.FOREIGN_CUT`, `FOREIGN_TAKEN`, `EMERALD_BUY`, `EMERALD_SELL`,
`DIAMOND_WORTH`, `BIG_COIN`, `KEEPER_VILLAGERS`, `KEEPER_WANDERERS`, `AT_RANGE`, `PAY_SLACK`, `ROUND_SLACK`;
`PurseContents.PURSE_MOST`; `Crooks.PURSE_LIFT_CHANCE`; `Careers.WAGES`, `WAGE_FLOOR`; `MerchantOrders.ORDER_MOST_COINS`.

## 9. Open questions for Andy
- Is the 10% foreign-coin cut right, or should foreign coin be refused outside the changer?
- Should the changer buy emeralds at 90% (and sell at 112%), or at par?
- A Coin Press was cut. Should a mint come back later, tied to a realm's treasury?
- Wagers, bribes and bargains in talk lines still say "emeralds" for the price number (master's wording); toll, fines and
  trade prices are shown in coin. Say if the rest should follow.

## 10. Risks
Big prices (`BIG_COIN`); wanderer hook order; strict slots in towns under 8 folk with no changer (`Coinage.fill` takes
only the exact coin); the atomic mixed-coin charge; the bit leaking into `values()` loops (use `coin()`); coin ids that
contain "silver", "gold" or "copper" (Garlic, Werewolf, the melting gap log; exclude through `Currency.byItem` or
`KingdomsMoney.isMoney`); `OpenButcher` protocol change (Kingdoms and Kitchen must match); `of(null)` is now silver.
