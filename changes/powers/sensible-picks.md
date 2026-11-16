# Sensible MC picks: 68 mods and 10 resource packs join the pack
- **Andy's picks from Sensible MC** (docs/checklists/Sensible MC pick-list.md): 68 mods (and 8 libraries) in build.gradle's
  `packModsNoCf`, grouped by batch. They reach the server, modpack/mods and your CurseForge profile, but not yet the
  CurseForge import zip (their CurseForge ids still need looking up from the home PC).
  - **Performance**: Entity Culling, ImmediatelyFast, ModernFix-mVUS, ScalableLux, Structure Layout Optimizer, Krypton,
    BadOptimizations, Cull Leaves, Particle Core, Optimized Block Entities, Let Me Despawn, Packet Fixer, Remove
    Reloading Screen.
  - **Looks and sounds**: Cirrus clouds and sky, Visuality, Cool Rain, Windy, Effectual, Grassier Grass, Continuity,
    Particle Interactions, Make Bubbles Pop, Handful, Stupid Block Animations, Telepistons, wind chimes.
  - **Moving and sitting**: Take a Seat!, Better Climbing, Cut Through, Punchy! and Punchy! Moves.
  - **Creatures**: Armored Foes, Direbats, Earth & Water (the bore and the brine), Ambient Creatures, VO: Better Dogs,
    Enhanced Cats, Herd Mentality, Hostile Mobs Improve Over Time, Brainier Bees, Luna Slimes.
  - **Mounts and travel**: Ultimate Horseshoes, Seaworthy Boats, Stretchy Leash, Craftable Horse Armor.
  - **Villages**: Shifting Wares, Doors Closed, Room For Two, Village Masquerade, Droppable Villager Trades, Villager
    Converting.
  - **Building and world**: Stoneworks, Scholar, Animal Weights, Unloaded Activity, NoWeatherSkip, Comfortable
    Campfires, Block Runner, Immersive Snow, Taller Forests, Tiered Shields, Passive Shield.
  - **Map and HUD**: Xaero's World Map, AppleSkin, Item Highlighter, Legendary Tooltips, Polymorph+, Chat Heads.
  - Libraries they need: Resourceful Config, Almanac, TLib, Dasik Library, Prickle, FrozenLib, Iceberg, Prism Lib.
- **Resource packs** (client/PackResourcePacks, fetched unchanged from Modrinth and switched on once per install): Fresh
  Animations' Details and Creepers add-ons, Armored Legacy, 3D Ladders, Remodeled Doors 3D, Fabulous Fences, Fancy
  Crops, Enchanted Books: Re-covered, KSPACK (Tiered Shields' textures) and Fluid & Foolish (Punchy! Moves' moves).
- **Gold armour is rare outside the Nether** (Andy: "gold is only realistically used in the nether"): our
  `data/armoredfoes` copies of Armored Foes' natural and raider armour tables (MPL-2.0) put gold at the top of the
  ladder, about 1 in 1700 armoured overworld mobs and 1 in 45 armoured illagers. Every other step keeps its old place.
  Piglins keep their gold.
- **Herd Mentality for hostile mobs only** (`core/HerdPreset`): once per install its neutral-mobs option is switched
  off.
- Left out: Lithosphere (it reshapes terrain, so it waits for a new world), the extra shaders (CurseForge only), and
  five that we already have our own of (Crawl, Visual Workbench, SleepWarp, Explosive Enhancement, The Fletching Table
  Mod).
- Not built or played here: no mod jar could be downloaded in the cloud, so no mod's fabric.mod.json was read. Fabric
  Loader 0.19.5 and our library versions are unchecked against them. Punchy! is the likeliest to clash (Better Combat,
  First-person Model, Shoulder Surfing and our poses).
