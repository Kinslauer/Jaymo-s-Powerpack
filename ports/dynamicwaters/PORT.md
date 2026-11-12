# Dynamic Waters, ported to 26.3 for Jaymo's Powers

**Dynamic Waters** is by **AdamNew** (https://github.com/AdamNewGames/dynamicwaters-public), licensed under the
**Mozilla Public License 2.0** (see `LICENSE`). This folder is a port of its `main` branch at commit `3e83b3a`
(Dynamic Waters 13.1.1, the Fabric 1.21.1 target of its Stonecutter build) to Minecraft 26.3 Fabric, made because there
is no official 26.3 build. It ships in Jaymo's Powers' free modpack with this credit. Under the MPL every file here
stays under the MPL and its source stays public in this repository.

Some files are adapted from other projects and keep their own licences inside this folder and inside the jar (the jar
carries `LICENSE`, `LICENSES/`, `THIRD_PARTY_NOTICES.md` and this file):
- **TerraFirmaCraft** (EUPL-1.2, `LICENSES/EUPL-1.2.txt`): `FastConcurrentCache` and the `river/` files listed in
  `THIRD_PARTY_NOTICES.md`.
- **Dynamic Waters Async Rivers** by erutobusiness (MIT): `async/MountainRegionStore`, `async/MountainRiverScheduler`.

Once AdamNew releases for 26.3, the pack switches to the official jar and this folder goes.

## Changes from the original

### Build
- Stonecutter resolved for Fabric 1.21.1 (its `//?` blocks kept as the Fabric 1.21.1 branch, the others dropped);
  the Forge and NeoForge platform code and data (`platform/forge`, `platform/neoforge`, `data/dynamicwaters/neoforge`,
  the 1.20.1 access widener and Forge `.cfg` files) left out.
- Plain Loom build like `ports/trmt`: Minecraft 26.3, Fabric Loader 0.19.5, Fabric API 0.162.0+26.3, Loom
  1.18-SNAPSHOT, Forge Config API Port 26.3.1 (the pack already ships it), Mod Menu compile-only; version
  `13.1.1-26.3-jaymo.1`; build output on E:\ on the home PC; Gradle wrapper from `ports/trmt`.
- `fabric.mod.json` written by hand (the original is generated): depends on `minecraft ~26.3`, `fabricloader >=0.19.5`,
  Fabric API and Forge Config API Port; its source link points here.
- Access widener: `accessWidener v2 official` (26.3 has no remapping), plus three lines that open the 26.3 material
  rule context (below). Mixin configs: `JAVA_25`, no refmap.
- Pack formats: resource 97, data 121.

### Code (26.3 API changes)
- Names: `ResourceLocation` → `Identifier`; `ChunkPos` is a record (`x()`, `z()`, `pack()`); `getMinBuildHeight` →
  `getMinY`, `getMaxBuildHeight` → `getMaxY() + 1`; registry lookups; `isClientSide()`; `getDeltaTracker()`;
  coloured terracotta from `Blocks.DYED_TERRACOTTA`; `LilyPadBlock`; `coldEnoughToSnow`/`warmEnoughToRain` take the sea
  level; `isSolidRender()` takes no arguments; `markPosForPostProcessing`.
- Registration: blocks, items and the ice floe get their id on their properties (`setId`); the flow wand has an item
  model definition in `assets/dynamicwaters/items/`.
- Saved flow zones (`FlowZones`) are a codec `SavedDataType`, with the same field names, so the saved file reads the
  same.
- Ice floes: `hurtServer`/`hurtClient`, `ValueInput`/`ValueOutput` saves, `snapTo`; the renderer fills a render
  state and submits its two ice plates through the 26.3 submit pipeline.
- Flow wand: `use` returns an `InteractionResult`, cooldowns go by stack, its chime is a sound packet to the one player,
  tooltips go through the 26.3 tooltip consumer.
- Spawning: river fish come from the biome's `NATURAL_MOB_SPAWNS` attribute (`WeightedList`).
- Climate and biomes: 26.3 has no `RandomState.sampler()` or `BiomeSource.getNoiseBiome(...)`; the new `ClimateAccess`
  keeps one climate sampler per world and asks the biome source's resolver. `MultiNoiseBiomeSourceMixin` now wraps the
  resolvers (`createResolver`, `createResolverForChunk`) with the same river and gorge biome rules.
- Density functions: 26.3 compiles density functions to samplers. `FractalRiverFunction`, `VanillaRiverFlow`,
  `BasinTile`, `CoarseHeightField` and `RandomStateMixin` sample through `DensitySampler`; the hand-made cell
  interpolation is gone because 26.3 interpolates exactly. `NoiseGeneratorSettingsMixin` builds the 8-field 26.3
  `NoiseRouter` (`chunkSurfaceLevel` replaces the old initial density) and caches it, since 26.3 asks for it every chunk.
- Terrain hooks (`NoiseChunkGeneratorMixin`): 26.3 builds noise, surface and carvers in one task. The river prefetch
  moved from `fillFromNoise` to `buildTerrain`, which also leaves the region and structure manager for the chunk; the
  river carving still runs at the head of `buildSurface` (it now gets the noise chunk handed in), and the save-heights
  and repair steps run around `generateCarvers` (one carving pass in 26.3 instead of air and liquid).
- Surface rules are material rules in 26.3: `SurfaceGround` opens `MaterialRuleContext` through the access widener
  instead of reflection; `SurfaceSystemAccessor` targets `MaterialSystem`.
- Carvers only mark a mask in 26.3: `WorldCarverMixin` lays the river protection over `applyCarvingMask` as a filter
  instead of stopping each carved block; `CanyonCarverMixin` takes the new `carve` signature.
- Features take `(level, generator, random, origin)`: `TreeFeatureMixin`, `SpringFeatureMixin`.
- Client: the river bubble is a `SingleQuadParticle`; flow zone outlines are drawn in `LevelRenderEvents.COLLECT_SUBMITS`;
  section redraws go through `levelExtractor`.
- GUI: every screen moved to 26.3's `GuiGraphicsExtractor` (text colours now carry alpha), new mouse events, selection
  list entries, `CycleButton` builders, `Blaze3D.openPath` for "open folder", and block swatches read 26.3 block
  models and tint sources.
- Panorama: 26.3 screens only pass the panorama's spin and the GUI renderer owns the one cube map, so
  `client/PanoramaMixin` keeps a second cube map with the river panorama and draws it in place of vanilla's on the
  Dynamic Waters config screen, and on the title and config screens when "custom main menu panorama" is on (as
  before). `TitleScreenMixin` is gone (PanoramaMixin does its job); `ConfigurationScreenMixin` hooks `extractBackground`.

- Fluid pushing: 26.3 moved the current out of `Entity.updateFluidHeightAndDoFluidPushing` into
  `EntityFluidInteraction.update`, so the per-entity push context is set there (`EntityFluidInteractionMixin`).
  `StructureMixin`, `BiomeSourceMixin` and `FlowingFluidMixin.isWaterHole` take the 26.3 signatures.
- **One Dimension** (in the pack; CustomChunks stacks the Overworld and the Nether into one level, each band with its
  own generator): new `compat/OneDimension` reads CustomChunks' bands by reflection. In a stacked world Dynamic Waters
  works its rivers out with the Overworld band's generator and random state, and only that band's generator carves
  rivers, lays river logs, keeps trees and springs out of rivers and protects rivers from carvers, so the Nether band
  under it gets none of that. CustomChunks builds a band's terrain with no region handed in, so the river hooks take the
  band's region from its structure manager (`StructureManagerAccessor`). Tested on a dedicated server with One
  Dimension 5.0.3 stacking the Overworld over the Nether: the Overworld band keeps its own heights (Y -64 to 319), its
  rivers are carved, the Nether band's generator never carves. Without CustomChunks nothing changes.

### Data
- Density functions in 26.3 form: `flat_cache`/`cache_2d`/`cache_once` → `cache`, `shifted_noise` → `noise`, binary
  `argument1`/`argument2` → `left`/`right`, unary `argument` → `input` (all built-in packs, compat packs included).
- Tag folders `blocks`/`fluids` → `block`/`fluid`; the flow wand recipe in 26.3 form; the river water blockstate uses
  vanilla's water model.

## Left out or changed, and why
- **Moderner Beta compat** (`ModernBetaChunkGeneratorMixin`) left out: Moderner Beta isn't in the pack and has no 26.3
  build, and its hooks were the old 1.21 signatures.
- **The config screen's panorama fade-in**: 26.3's panorama has no fade parameter, so it shows at once.
- **Compat packs for Tectonic, Larion, Lithosphere and Abridged** are converted to the 26.3 density function format
  but untested: none of those mods are in the pack, and the packs only load when their mod is there.
- The mod's own admin commands (`/dynamicwaters ...`, `/locateriverterrain`) are kept as they were; they are for
  operators, not players.
