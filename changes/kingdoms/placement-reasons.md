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
- **Lone homesteads** (`homesteads_by_towns`: Structory's outcast villager barns, Homesteads' homesteads, Moog's lone
  houses): a town they trade with within about 500 blocks, a day's ride.
- **Old cities** (`old_cities`: our old age's eight ruins, the consul's city among them): never on top of anything lived
  in (`lived_in`: villages, homes, farms, stables, mines), none within about 64 blocks. On Andy's Deck world the Pillared
  ruins had come up through an outcast villager's horse barn.
- At start-up the log lists, by mod, every structure with no placement reason yet (no rule, not lived in, and not given
  a part by our code: `own_reason` holds Grim Kingdoms' holds and camps). That's the list to give roles to next.
- Settlements are `#minecraft:village` (every mod's villages) and Dungeons and Taverns' hamlet (tag `settlements`).
- Built in CI; not played. How much rarer each kind gets wasn't measured in a world.
