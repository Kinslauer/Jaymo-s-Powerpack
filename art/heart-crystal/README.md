# Heart crystal ore art

Original geometry and textures by Prometheus for Jaymo's Powers. Tiv's two supplied amethyst-heart photographs are visual references only; no pixels or geometry were taken from them or from any third-party art pack. No outside licence applies.

## Design

- One large stepped amethyst crystal stands in the middle of each vertical face.
- Nine smaller crystals form two upper lobes, sloping sides and a lower point around it.
- Five quieter crystals crown the top; the buried bottom remains plain rock.
- The stone and deepslate hosts are full opaque cubes. Crystals intersect the host surface, so there are no cut-out holes, neighbour-face culling surprises, extra blockstates or special item-model variants. The normal one-block collision shape is unchanged by this art.
- Deep violet, purple, lilac and pale highlight facets make the crystals read bright like vanilla amethyst. This art does **not** emit light and adds no gameplay light level.

This replaces the flat three-red-hearts `cube_all` art from PR #1167. The separate heart-crystal item sprite is unchanged.

## Rebuild and render

From the repository root, with Python 3.12 and Pillow:

```sh
python art/heart-crystal/generate_heart_crystal_ore.py
python tools/mesh_to_cubes/bbrender.py art/heart-crystal/heart_crystal_ore.bbmodel art/heart-crystal/heart_crystal_ore-render.png
python tools/mesh_to_cubes/bbrender.py art/heart-crystal/heart_crystal_ore_deepslate.bbmodel art/heart-crystal/heart_crystal_ore_deepslate-render.png
```

The generator writes both editable `.bbmodel` files, both game block models, the two rock textures and the shared opaque `heart_crystal_facets.png` atlas. The four-view PNGs are actual `bbrender.py` output; the orthogonal sheets show front, side, back and opposite side at yaw 0/90/180/270 degrees.
