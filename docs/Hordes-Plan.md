# Greenskin hordes: camps that grow into kingdoms (plan)

Andy (2026-11-01): "the orc camp i found in the second picture looks great, they should all build similarly and expand
further, making it possible to find a large goblin camp the size of a kingdom even, with their own economy".

Part 1 is PR #849: ogres live in and build with the orc camps, Jade names greenskins correctly, half-orc looks with a
skin slider, and the idle "My weapon!" loop is fixed.

## How camps work now
Orc camps and goblin villages are built once inside a 22-block palisade. After that they raise one building a day from a
fixed list of 7, and add folk up to 15 orcs or 16 goblins. Their daily income is a flat amount of logs, hides, meat and
so on, whatever happens to the camp. Once the list is done, they stop growing.

## Patch 2: camps grow in tiers
| Tier | Orc name | Goblin name | Wall radius | Folk cap | Unlocked when |
|---|---|---|---|---|---|
| 1 | War camp | Village | 22 | 15 / 16 | founded |
| 2 | Warhold | Warren | 36 | 25 | inner list built, folk at cap, 10 days old |
| 3 | Horde town | Goblin town | 52 | 32 | tier 2 filled, 25 days old |
| 4 | Horde kingdom | Goblin kingdom | 72 | 40 | tier 3 filled, the tribe's king seat, 50 days old |

- Moving up a tier, the camp builds a new outer palisade one arc a day (ogres double the speed). Gates are cut in the old
  ring, and roads of coarse dirt run from gate to gate.
- New buildings go into the band between the two walls. They're placed by an angle-slot planner, so each camp lays out
  differently. They use the existing designs (huts, tents, forges, taverns, lookouts, pens, mushroom patches) plus new
  ones: a market square, an ogre den, a smelter, a brewery (goblins), war-beast pens, and a great hall that replaces the
  longhouse or great hut at tier 4.
- A tier 4 camp becomes a real greenskin realm. It claims land like a kingdom, its chief takes a title (Goblin King,
  Warlord), it shows on maps and the news, and smaller camps of its tribe nearby count as its vassals.
- Lag guard: folk only spawn and act near players, as now. The caps above are tuning constants, kept near 30 at the top
  (Andy's answer): a big camp looks big through its buildings and walls, not crowds.

## Patch 3: their own economy
- **Jobs from their folk:** hunters, foragers and mushroom farmers, woodcutters, miners, smiths, tanners, brewers and
  traders. Daily income comes from these jobs, replacing the flat amounts. Everyone eats each day.
- **Hunger and plenty:** a camp short of food stops growing and raids the nearest realm more often. A camp with surplus
  grows faster and trades.
- **Market:** greenskin merchants (MCA villagers with their own offers, like the havens' wares) sell the camp's surplus.
  Prices rise and fall with its stock. Only people the camp spares can trade there: half-orcs, those raised by goblins,
  orc friends, and anyone in a goblin mask.
- **Caravans:** greenskin carts carry surplus between a tribe's camps along their roads. Players can raid them, which
  raises the tribe's grudge.
- **Money:** the tribute havens already pay, plus their trading, goes into the chief's hoard. The hoard pays for walls and
  new tiers.

## Patch 4: the greenskin kingdom
- The tribe's king seat can reach tier 4 and becomes a city: a throne hall, an arena, a market district, ogre dens, and
  shaman towers.
- Its king sends war bands as a realm does, makes pacts (the TribePacts favour already exists), and gets his own bounty
  board line.

## Andy's answers (2026-11-01)
1. **Money:** ~~orc war teeth and goblin bent coppers~~ replaced on 2026-11-03: see below.
2. **Caps:** ~~top out near 30~~ raised to 40 on 2026-11-03.
3. **Joining:** half-orc and orc players can swear to a greenskin kingdom, and so can any other player who builds a good
   enough reputation with the tribe (Patch 4).

## Andy's answers (2026-11-03)
4. **Speech:** orcs and goblins speak their own dialects of the same broken English ("we wuz bustin hedz"). Orcs are
   loud, blunt and brutal; goblins are quicker, greedier and a little cleverer. Today's orc lines sound too smart.
5. **Money:** bent ingots, a bent copper and a bent iron, simple items. Greenskins take only bent ones and are dumb
   enough to refuse the same ingot once it is straightened.
6. **Orcs:** they get by on force and brutality, toward everyone and each other. Orc camps brawl inside and war with
   each other all the time.
7. **Goblins:** goblins who stay in orc lands are slaves or "toothpicks" (thrown, worked, used up). The rest branch out
   into their own territory, more cleverly built, elaborate and close to human towns.
8. **Living together:** orcs and goblins can live among humans, more easily among the other heritages, and every
   people has some distaste for the others at its own strength.
9. **Caps:** 40 for now (tiers 2-4 hold 25, 32, 40), cut back if it lags. Buildings get elaborate; Andy has more
   modpacks to help generate their structures.
10. **Every settlement:** all kinds of settlements should work this way too (tiers, growth, economy).
11. Mods to check: MCA Crime, MCA Mob Compat, MCA Kingdoms, Plant Fibers, MCA Quests, MCA Reputation, MCA Conversations.

## Patch 5: every settlement (Andy's answers 2026-11-03)
1. **Kingdoms and fiefdoms** keep their open-ended growth (no tiers, no cap).
2. **Villages and blossom villages** tier up into walled towns: log palisades, stone walls for the villages of richer
   kingdoms.
3. **Pirate coves, illager camps, ogre holds** grow as long as they hold territory and resources, so they plunder when
   they fight or raid (loot goes home into their stores). Pirates need better cannons: better modelled and destructive to
   all terrain, not just ships. Andy saw pirates shelling orcs throwing rocks from the shore; the blasts should have
   torn the coast down to the stone. (Pirate Cannons! on CurseForge is All Rights Reserved, Forge only: ideas only.)
   Andy (2026-11-04): he meant the shots should explode and damage blocks, not just boats: they break blocks by blast
   resistance like TNT. Terrain damage is spread over ticks, as the meteor fix does, to keep it from lagging.
4. **Money:** currencies diversify. A settlement uses the currency of its prominent trading hub, or of another factor
   such as its superior (overlord) realm. Greenskins keep bent ingots.
   Built (settle/currency, 0.7.490): `Currency`/`Currencies`. Emeralds stay the common coin and the treasury's unit of
   account (old saves hold). Coins by culture: plains Realm Silver, taiga/snow Hacksilver, savanna/desert/badlands
   Gold Dinar, jungle/swamp Shell String, cherry Mon; greenskins bent copper, pirates gold (dinars in their coves).
   A vassal uses its overlord's; a kingdom its land's unless a far richer kingdom nearby is the hub; villages and havens
   the hub's. Villager prices, merchant orders, bounties, fines, career wages and stall sales use the local coin;
   a Money-changer villager (towns of 8+ folk) swaps any coin for any at a rate that moves with the realms' wealth.
   `/kingdom currency` shows the coin and the rates.
5. **Caravans:** everyone trades in their own way, down to handcarts if that's all they have.
6. **Havens** only trade, but expand massively to make the most of their output, and keep fierce fighters sworn to
   defend them.
7. **Caps:** each kind sets its own.

## Checklist
- [x] Patch 1: ogres in orc camps, Jade names, half-orc skin slider, idle weapon loop (PR #849)
- [x] Patch 2: camps grow in tiers with outer walls and band buildings (PR #851)
- [ ] Dialects: every orc and goblin line rewritten in its own broken English
- [ ] Orc brutality: camp brawls, chief challenges, orc camps raiding each other
- [ ] Goblin slaves in orc camps (chained work, thrown at foes) and runaways founding their own villages
- [ ] Bent copper and bent iron: items, art, greenskins refuse straightened ingots
- [ ] Patch 3 economy: jobs, food and hunger, market priced in bent ingots, caravans
- [ ] Goblin towns: elaborate, human-adjacent buildings for goblin tiers; caps 40
- [ ] Living together: greenskins in human and heritage settlements, a distaste table between peoples
- [x] Patch 4: the greenskin kingdom city (0.7.491): arena, shaman tower, warpath with vassals, wanted notice, tribute pacts, horde oath (`HordeCity`)
- [ ] Patch 5: walled towns, plundering coves/camps/holds, terrain-wrecking pirate cannons, currencies, caravans for all, haven growth
  - [x] caravans for all (branch settle/caravans): TradeRoutes, handcarts, skiffs, escorts, deals, robbery by kind
- [x] Structures from Andy's other modpacks (0.19.83): Rabbit's DawnCraft checked (Forge 1.18.2); added Dungeons and Taverns, Structory, Structory: Towers, Homesteads. Waiting on 26.3: YUNG's suite, The Lost Castle (LGPL, 26.1.2), Dungeon Crawl (GPL, 26.2)
- [ ] Check the 7 MCA add-ons and Plant Fibers
- [x] Patch 5, plundering coves, camps and holds (0.7.490): growth paced by stores with per-kind caps (pirate stages, illager outlying buildings, ogre hold stages), raiders break into loose chests and carry off storage and coin, ogre raids, pirate prize cargo, shore bombardment and spread-out cannon craters, re-modelled ship gun
