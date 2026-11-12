# the tundra - bare old rock and the lichen woodland
- **Two new Overworld biomes** (Andy picked all of Klei_Wright's tundra concept; `docs/Tundra-Plan.md`). Not a
  dimension: they take the driest part of what was Snowy Plains, in new chunks only.
  - **Tundra**: no stone or soil. Deepslate runs from bedrock to the surface under thin grass, yellow moss and gravel.
    **Greenstone belts** (strips and patches of smooth basalt) cross it from the surface down, with pockets of
    **banded iron ore** in them, some round a heart of raw iron blocks; iron ore is also far commoner underground here
    than anywhere else. Boulders, lichen on the bare rock, low red, orange and yellow shrubs. No trees. It never
    snows or rains, but snow motes drift under a pale sky, and its water freezes to white ice. Polar bears, foxes, rabbits.
  - **Lichen woodland**: the belt between the tundra and everything else, with a grass colour halfway between, yellow
    moss patches, and **dwarfed spruce** no taller than a man.
- **Spruce saplings grow dwarfed** in both biomes (there is no dwarf sapling).
- How: `tundra/world/TundraWorld` splits Snowy Plains' dry cells as the Overworld biome list is built
  (`TundraPlacementMixin`); the ground is the material rule `jaymospowers:tundra`, run from our copy of
  `minecraft:overworld` (one added line: another pack replacing that file would switch the tundra's rock off);
  `TundraFeatures` has the boulders, veins, lichen, shrubs, moss, white ice and dwarfed spruce.
- Compiled; not played. Numbers that are guesses until seen in a world: belt width and spacing, vein count, how much of
  the surface is grass, and how much of Snowy Plains the two biomes take.
- **Klei_Wright's own art** for the tundra's blocks, ground and hanging lichen and the phosphor icon ships as its own
  resource pack inside the mod, `resourcepacks/klei_tundra` (CC BY-NC-SA 4.0, his files unchanged, credited in its
  `CREDITS.txt`), on by default over our own art; switch it off in Resource Packs to see ours. His muskox art is not
  used: it fits his model, not ours. Not seen in game: whether each of his textures sits right on our models.
