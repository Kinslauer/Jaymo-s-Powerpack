# Building library plan

Andy picked all 88 rows of the medieval schematics pick list (https://claude.ai/artifact/2aRanYCUbFqu48XJwVW9Sz,
db `picks/andy`, saved 2026-11-19 18:02 UTC), with this note: "builders should draw from this library as well whenever
their kingdom needs a structure", and on the two castles too big for the mason's table: "raise the limit" / "raise it!".
Thread: "Find medieval building schematics online". Research files: `/mnt/project-files/schematics-study/` (the pick
list, its data, and `nbtlib.py`/`scan.py`/`render.py`, which read .nbt files and draw the previews).

So this is one library of ready-made buildings with three homes, used in two places: the mason's table (you build it)
and the town builders' planner (a settlement builds it when it needs that kind of building).

## Where the buildings come from (licence decides the home)

| Home | Rows | Licence | How we get the file |
|---|---|---|---|
| **In the pack** | Towns and Towers (12 rows), Moog's Voyager Structures (8) | CC BY-NC-ND / MIT-LGPL, already shipped unchanged | Read from the installed mod in the game, never copied (as `CtovHalls` and `DntBuildings` do) |
| **Shipped** | Cinch's Abandoned Houses (3), Lone Houses, Towny and Village (2), Berezka's castle, Castles and Outposts, Battle Castles, The Lost Castle, Little Structures, Sohatuy's (13 rows) | MIT, Apache-2.0, AFL-3.0, LGPL-3.0 | Copied into Kingdoms' resources with a credits file, by a script that pulls the pinned Modrinth version |
| **World only** | mc-mod.net (46), Planet Minecraft tavern, Ivoryfalls, windmill, Medieval Build Bundle; Medieval Buildings' fort (licence unclear) | No licence to hand on | Andy downloads them; they go in the server world's own library folder, never in git |
| **Ship once confirmed** | Kella's bundle, Medieval Building Pack (19) | CC BY-NC-SA 3.0 per search results | Andy downloads; if the page confirms CC BY-NC-SA and the files are .schem/.litematic/.nbt, they move to Shipped (with the licence notice); else World only |

Every building keeps its source, author and licence in the library index, and the mason's table shows the author.

## Patches

### 1. The library and the builders (Kingdoms)
- `BuildingLibrary`: an index of entries, each `{id, source, kinds, lands, biomes?, author, licence}`. `source` is a
  mod template id (`kaisyn:...`, `mvs:...`), a shipped file (`jaymoskingdoms:library/...`) or a world file. The shipped
  and in-pack entries are listed in `data/jaymoskingdoms/library/*.json`; world entries come from patch 3.
- `kinds` are the catalogue ids it may stand in for: `house`, `farmhouse`, `smithy`, `tavern`, `stall`/`market`,
  `stable`, `sawmill`, `tower`/`firewatch_tower`, `barracks`, `graveyard`, `well`, `mason`, `butcher`, `fishery`, plus
  new `chapel` (village churches, bell tower) and `windmill`/`barn` where the catalogue has no such building yet
  (they join the village and realm lists with small caps).
- `lands` are our `Palette` lands; `biomes` narrows a style to its own biome (birch woods, meadow, old-growth taiga) so
  a town in a birch wood gets the birch village's houses, a meadow town the chalets, and so on.
- The planner: a third tagger after `CtovHalls.tag` and `DntBuildings.tag` (its own mark in the variant's top bits), so
  some tries of an entry draw a library building for that land and the rest stay ours, CTOV's or D&T's. Older projects
  and standing buildings keep drawing as they were. Drawn through `CtovHalls.convert` (jigsaw to its final block,
  entrance turned to our door side), within a crew's size (32 across, 40 high) unless the entry allows more.
- Abandoned buildings (Cinch's, Lone Houses, Berezka's ruins) go up whole: cobwebs, vines and broken bits are left out
  as they are converted (as `DntBuildings` does for D&T's ruins).
- Pillager outpost towers (Towns and Towers) lose their ominous banner and cage; the realm's own banner goes up.
- Check first: the mod id and namespace of the shipped Towns and Towers jar (1.13.12; its datapack build uses `kaisyn:`).

### 2. The shipped files (Kingdoms resources + a tool)
- `tools/library_import.py`: downloads each pinned Modrinth version, takes only the picked `.nbt` files, and writes them
  under `kingdoms/src/main/resources/data/jaymoskingdoms/structure/library/<source>/`, plus `LIBRARY-CREDITS.md`
  (author, licence, link, what was changed) and the licence texts the licences ask for.
- Sohatuy's buildings use Quark, Macaw's Furniture, Supplementaries, More Villagers and Farmer's Delight blocks: the
  tool swaps each for a vanilla stand-in (vertical slab to slab, Quark chest to chest, furniture left out or a
  vanilla block) and notes the change, as Apache-2.0 allows.
- Towny and Village's town houses come in parts (shell, facades, roof in 12 colours, rooms) joined by jigsaw: the tool
  joins them into whole houses (one per roof colour and facade mix we pick), MIT allows it.
- The Lost Castle is nine jigsaw pieces: the tool joins them into one design if the whole fits the new limit (patch 4;
  about 133 across by its piece sizes, to be measured), else it stays nine designs.
- The Medieval Buildings fort stays out (its page says All Rights Reserved; the jar says CC0) until its author answers.

### 3. The world library (Kingdoms)
- A folder in the world, `<world>/jaymoskingdoms_library/`, read at start and on `/kingdom library reload`.
- A shipped list of the picked world-only builds by file name (the names mc-mod.net and Planet Minecraft give them)
  with their kind and land, so a dropped-in file is sorted on its own. Any other file is listed as unsorted, usable at
  the mason's table, until `/kingdom library tag <file> <kind> [land]` sorts it for the builders.
- A download list for Andy: `docs/building-library-downloads.md`, one link per picked world-only row, and which folder
  to put them in. Files that fail to read (old .schematic, Bedrock .mcstructure) are reported by name.

### 4. The mason's table (Kingdoms)
- `Designs.PLAN_SIDE` from 96 to 128 (the 101 and 128 across castles), `PLAN_HEIGHT` stays 128, `PLAN_BLOCKS`
  120,000 to 160,000, and the upload limit (`DesignUploads`, 64 pieces of 30,000 bytes) raised to match if the two
  castles' files are bigger than it.
- A second tab on the table's file list, "Pattern books": every library building (in the pack, shipped, and the world's),
  with its land, size and author. Picking one makes it a plan like any upload.
- Library castles (mc-mod.net: the small castles, the castle base, the two big ones; Berezka's, Battle Castles) also
  stand as a **castle town's castle** where its land has no Grim or ATi castle near. Open question 2.

### 5. Credits and lore
- Credits on the pack page and in `LIBRARY-CREDITS.md`; the world-only builds' authors listed in the mason's table only.
- Lore placement below, for Andy's approval.

## Lore placement (new, for Andy's approval)

**The masons' pattern books** (building library thread, 2026-11-19). Every land's masons keep pattern books: how
their people raise a house, a smithy, a tavern, a chapel or a tower in that land's own wood and stone, steep timber in
the plains, chalets in the high meadows, low log halls in the old taiga, whitewash and tile in the warm hills. A realm's
builders work from the books of the land they stand in, and from the plans taken from fallen holds, so a town looks like
its country and not like its king. The books also keep the houses of those who left: cottages found standing empty
in the wild are drawn into them, and builders put them up again whole, with a fire in the hearth.

## Open questions

1. **World-only files**: Andy downloads them (mc-mod.net and Planet Minecraft gate their downloads behind ad pages and
   block the cloud). Recommended: download into `C:\Users\toopo\Downloads\building-library\`; the PC reviewer copies them
   into the server world's `jaymoskingdoms_library/` with the next pbdr.
2. **Castles from the library**: let builders put up library castles as castle-town castles (up to 128 across), or
   keep them mason's-table only. Recommended: castle towns, where the land has no Grim or ATi castle.
3. **The Lost Castle** if it ends up over 128 across: nine designs, or limit 136. Recommended: nine designs.
