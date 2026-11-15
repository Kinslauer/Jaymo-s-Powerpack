# GT New Horizons picks, patch 3 - water you can't copy and cactus water
- **Water you can't copy** (`climate/FiniteWater`, `FiniteWaterMixin`): two water sources make a third only in
  rivers, oceans and swamps (`#jaymoskitchen:renews_water`: `#minecraft:is_river`, `#minecraft:is_ocean`, swamp,
  mangrove swamp and `#c:is_swamp`), or where the new source is open to the sky while it rains there. Anywhere else a
  pool holds the water put in it, so a pond drunk dry by bottles and waterskins stays dry and a 2x2 pool is no
  longer an endless bucket. Dynamic Waters' carved rivers carry the river biome, so they keep giving. The game rule
  `waterSourceConversion` still switches conversion off everywhere; lava is untouched.
- **Cactus water** (`climate/Climate`, `climate/Thirst`): eight cacti round an empty bucket make a Bucket of Bitter
  Water. Drunk straight from the bucket it gives 8 water but counts as unpurified (the same 35% parasite chance as a
  pond bottle). Used on any fire that boils a bottle (campfire, firepit, bonfire, hearth, hot cooking pot or pan) it
  becomes Boiled Cactus Water (a clearer bucket): clean, and it cools you like Clean Water. The bucket comes back.
- JEI: the recipe shows as a crafting recipe; the bucket has an info page.
- Built; not played.
