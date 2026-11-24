# Structures need a reason: mines by buyers, stables by towns, watchtowers on borders, ruins with heirs
- Andy: "remember the generation needs a reason" (track 2 of the structure placement plan). The farms rule from #2073
  is now one of five placement rules (`PlacementReasons`, mixin `PlacementReasonsMixin`); a structure whose reason
  doesn't hold isn't generated there. Only new chunks change.
- **Farms** (`farms_by_homes`): as before, within 6 chunks of a village or a lone home.
- **Mines** (`mines_by_buyers`: ATi's quarry, Dungeons and Taverns' badlands miner outpost, Homesteads' prospector
  camps, Moog's mine with campsite): a village to sell to within about 400 blocks, but none within about 150.
- **Stables** (`stables_by_towns`: Moog's horse campsite): a village within about 300 blocks.
- **Watchtowers** (`towers_on_borders`: Structory's fire towers, Dungeons and Taverns' firewatch towers): on a realm's
  edge, its village about 100 to 400 blocks off.
- **Ruins** (`ruins_with_heirs`: castle ruins, ruined towns and remnants from Moog's, ATi, Dungeons and Taverns and
  Structory): a living village within about 300 blocks that is their heir, or alone in land two or more steps harsh
  (the heat map), where the cold or heat drove their people out.
- Settlements are `#minecraft:village` (every mod's villages) and Dungeons and Taverns' hamlet (tag `settlements`).
- Built in CI; not played. How much rarer each kind gets wasn't measured in a world.
