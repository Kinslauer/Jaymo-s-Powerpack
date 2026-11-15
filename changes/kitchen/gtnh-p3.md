# GT New Horizons picks, patch 3 - water you can't copy and cactus water
- **Water you can't copy** (`climate/FiniteWater`, `FiniteWaterMixin`): two water sources make a third only in
  rivers, oceans and swamps (`#jaymoskitchen:renews_water`: `#minecraft:is_river`, `#minecraft:is_ocean`, beaches,
  swamp, mangrove swamp and `#c:is_swamp`, counted up to 6 blocks out so river banks and shores keep giving), or where the new source is open to the sky while it rains there. Anywhere else a
  pool holds the water put in it, so a pond drunk dry by bottles and waterskins stays dry and a 2x2 pool is no
  longer an endless bucket. Rain wakes a pond that settled in dry weather, so open pools refill while it rains.
  Dynamic Waters paints the river biome down the middle of its rivers; the 6-block reach covers their banks. The game rule
  `waterSourceConversion` still switches conversion off everywhere; lava is untouched.
- **Cactus water** (`climate/Climate`, `climate/Thirst`): eight cacti round an empty bucket make a Bucket of Bitter
  Water. Drunk straight from the bucket it gives 8 water but counts as unpurified (the same 35% parasite chance as a
  pond bottle). Used on any fire that boils a bottle (campfire, firepit, bonfire, hearth, hot cooking pot or pan) it
  becomes Boiled Cactus Water (a clearer bucket): clean, and it cools you like Clean Water. The bucket comes back.
- JEI: the recipe shows as a crafting recipe; the bucket has an info page.
- Built; not played.
