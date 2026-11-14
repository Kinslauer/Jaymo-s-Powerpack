# Farmer's Delight: the other knives, Backstabbing, home comfort, hot cocoa
Andy ticked all nine Farmer's Delight rows of the Portable Mods picks. Seven were already in Kitchen (crops and wild
crops, rich soil and compost, skillet, stove, cabinets/crates/bags/baskets, canvas and tatami, village compost heaps and
trader seeds, and the dishes). This adds what was missing (MIT, by vectorwing; LICENSE_farmersdelight).
- **Knives**: Flint, Copper, Golden, Diamond and Netherite Knives beside the Iron Knife (`delight/Delight`), with
  Farmer's Delight's art. Andy: "They need the same properties as other knives", so every one skins, butchers, cuts on
  the board, cuts straw from wheat and ham from a pig (tags `knives`, `butchering_tools`, `c:tools/knife`), and all six
  now also count as swords and as a thief's light blades, as the primitive and bone knives do.
- **Making them**: flint and a stick in the grid. The metal ones are forged when Jaymo's Powers is loaded (its change
  note); without it they keep a grid recipe like the iron knife's. Netherite is a smithing table upgrade of the diamond one.
- **Backstabbing** (`delight/Backstabbing`, `mixin/BackstabMixin`): a knife enchantment, I to III, from the enchanting
  table. A blow struck from behind the victim does 1.4x, 1.6x, 1.8x.
- **Home comfort** (`delight/HomeComfort`, Andy: "We can implement home comfort too"): wake at dawn in a bed with at
  least 4 homely things within 5 blocks (cabinets, baskets, crates, bags and bales, rugs, tatami, canvas signs, a hearth,
  carpets, candles, flower pots, banners, lanterns, bookshelves: block tag `jaymoskitchen:home_comfort`) and you get
  Comfort (the cold can't touch you), 30 seconds per thing up to 8 minutes. A bare room does nothing.
- The same storage and decor now count toward a cafe's comfort in Kingdoms (added to its `cafe_comfort` tag).
- **Hot Cocoa**: stirred in the pot from a glass bottle, a milk bottle, two cocoa beans and sugar. Gives Comfort like a hot bowl.
- Not added: Farmer's Delight's Beef Stew (our Hearty Stew is it), Apple Cider (we brew Cider) and its straw bed (26.3 has one).
- Built with Gradle; not played.
