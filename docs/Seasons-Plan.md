# Seasons

Andy's ask (2026-10-21): "can seasons work in in anyway?" (pointing at Ecliptic Seasons). Options doc:
https://claude.ai/artifact/KDZcSQ3GMzQq6jTpDA2mpm. Andy picked **build our own**.

## Why not Ecliptic Seasons
- Ecliptic Seasons (NeoForge, joe-vettek): code BSD-3, art CC BY-NC-SA 4.0, and the authors ask people not to port it.
- Ecliptic Seasons: Fabricated: All Rights Reserved, no 26.3 build (Modrinth lists 26.3 as in development).
- So this is a clean-room build, following REA-Prompts' "clone one feature" method: only the behaviour was looked at
  (their public feature list), never their code or art. Ours is simpler on purpose. The differences are listed below.

## Spec (built in Kitchen 0.5.6, `kitchen/.../season/`)
| Rule | Value | Where |
|---|---|---|
| Year | 4 seasons x early/mid/late x 7 days = 84 days, from the overworld clock (sleep and /time move it) | `Seasons` |
| Who has seasons | biomes with base temperature 0.55 to 0.9 (plains, forests, birch, dark forest, swamps, beaches). Not rivers or oceans (0.5), taiga, jungle, desert | `Seasons.TEMPERATE_*` |
| Chill | spring -0.25, summer 0, autumn -0.3, winter -0.8 off the biome temperature; under 0.15 it snows and still water freezes | `Season.chill`, `BiomeSeasonMixin` |
| Thaw | out of winter, snow layers and ice open to the sky in temperate lands melt on random ticks (35% / 20%) | `SeasonalWeather` |
| Crops | in a `jaymoskitchen:seasons/<season>` tag: grow only in those seasons; untagged: all but winter; out of season they wait | `SeasonalCrops`, `CropSeasonMixin` |
| Harsh winter (Andy) | frost kills crops in the open (25% a random tick; berries and cocoa lose their fruit); out under the sky in a temperate land, away from fire or lava, 0.025 exhaustion a second (Frostborn spared) | `SeasonalCrops.frost`, `WinterCold` |
| Shelter | first block above that's glass (`jaymoskitchen:greenhouse_roof`) or solid: grows all year | `SeasonalCrops.sheltered` |
| Colours | leaves and grass mixed toward a seasonal tint, per part of the season | client `SeasonTints` |
| Telling | each new part announced in chat; holding a clock shows the date; `/season`; ops `/season set <season> [early\|mid\|late]` | `Seasons` |
| For other mods | `Seasons.seasonName(Level)`, `Seasons.dayOfYear(Level)` (by reflection) | |

Default crop seasons: spring wheat, carrots, potatoes, beetroots, torchflower; summer wheat, melon, pumpkin, sweet
berries, cocoa, torchflower, pitcher; autumn wheat, carrots, potatoes, beetroots, pumpkin, sweet berries; winter none.

## Differences from Ecliptic (on purpose, or not built yet)
- 3 parts a season, not 24 solar terms. No humidity. No day-length change. No seasonal weather odds yet.
- No growth bonus in a crop's best season (just grows or waits).
- No seasonal sounds, particles or block models. No animal-breeding seasons.
- Player-placed snow or ice in the open in a temperate land melts out of winter too.

## Next (track 2)
- Kingdoms (through its `KitchenCompat`): harvest stock and farmer prices by season, fewer raids in deep winter,
  a festival at each season change. Heritages: Frostborn strong in winter, Emberborn in summer, Verdant/Sylvan in
  spring. Powers: light flavour only.
- Andy's answers (2026-10-21): 84-day year is right; winter very harsh; powers: "all of em" (built in Powers 0.15.28, see
  `core/Seasons` and the root MODLOG).
