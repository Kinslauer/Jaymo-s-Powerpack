# GT New Horizons picks, patch 2 - farms, herds, the food journal and the lunch box
- **Farmland only near water** (`farm/DryFields`): a hoe makes farmland only within 4 blocks of water (vanilla's
  hydration reach). The plow follows the same rule (`horse/CartEntity.tillOne`).
- **Seeds from dry grass** (`farm/DryFields`): grass and ferns drop no seeds any more (Kitchen's hemp and cabbage seed
  pools on short and tall grass are gone). A hoe used on a grass block more than 4 blocks from water forages instead:
  1 in 4 times a seed pops out, by biome (wheat and hemp anywhere; cabbage and beetroot on plains; pumpkin in forests
  and taiga; tomato in hot lands; pmeh in jungles and savannas; melon in jungles; rice in swamps), and each try costs
  1 durability.
- **Farms grow at half speed** (`farm/SlowFarms`, `SlowGrowthMixin`): everything in `#jaymoskitchen:slow_growing`
  (crops, saplings, cane, cactus, cocoa, nether wart, sweet berries, glow berries, Kitchen's crops, Powers' orange tree
  and berry bushes) takes half its random growth ticks, on top of the season rule. Other mods' fruit trees can join the tag.
- **Slow herds** (`farm/Herds`, `HerdAgeMixin`): young animals take twice as long to grow up and the wait to breed
  again is twice as long; hens lay half as often (`farm/LayingHens`); a cow, mooshroom or goat gives milk once every
  20 minutes, and a bucket on one milked since gives nothing.
- **Weaker bone meal** (`CropBoneMealMixin`, `StemBoneMealMixin`, the tomatoes): bone meal pushes a crop or stem one
  stage, never 2 to 5.
- **Hoes wear out fast**: tilling costs 5 durability (the vanilla hoe block transformer, overridden).
- **Crowded pens** (`farm/Herds`, `CrowdedPenMixin`): more than 12 adult farm animals of a kind within 8 blocks
  won't breed, and now and then butt one another for half a heart.
- **Kill clean or lose the meat** (`corpse/CleanKill`, `corpse/Gibs`): a farm animal killed by fist, blunt weapon or
  blast leaves a bruised corpse: half the meat and a torn hide. Farm animals never burst any more. A knife, cleaver or
  blade kill gives the full yield, as before.
- **Eat widely, grow stronger** (`stomach/Variety`): each kind of food eaten the first time adds points equal to its
  hunger value; every 50 points is a lasting extra heart, up to 10, kept through death. The Food Journal (book,
  feather and wheat) opens on two pages: the foods eaten and those not yet tried.
- **Lunch box** (`stomach/LunchBox`): a hemp-cloth pouch (string and five hemp cloth) holding 9 foods. Carried
  anywhere in the inventory, it feeds you the biggest food in it that your hunger has room for, or, once you're down
  to 3 shanks, the smallest even if it won't fit; bowls and bottles come back to you. Not while it's open.
- JEI: a Foraging category shows each hoe forage seed with where it grows and the odds; the food journal and the
  lunch box show through their recipes.
- Time Bloom (Jaymo's Powers) reaches all of these clocks: `SlowFarms.hurry` and `Herds.bloom` are its hooks.
- Built; not played.
