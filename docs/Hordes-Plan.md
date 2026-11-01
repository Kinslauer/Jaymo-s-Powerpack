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
| 2 | Warhold | Warren | 36 | 20 | inner list built, folk at cap, 10 days old |
| 3 | Horde town | Goblin town | 52 | 25 | tier 2 filled, 25 days old |
| 4 | Horde kingdom | Goblin kingdom | 72 | 30 | tier 3 filled, the tribe's king seat, 50 days old |

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
1. **Money:** greenskins get their own coin: orc war teeth and goblin bent coppers. Each needs an item and our own art,
   with exchange at their markets (Patch 3).
2. **Caps:** top out near 30: tiers 2–4 hold 20, 25 and 30 folk (the table above).
3. **Joining:** half-orc and orc players can swear to a greenskin kingdom, and so can any other player who builds a good
   enough reputation with the tribe (Patch 4).
