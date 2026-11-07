# mesh_to_cubes

Turns a realistic triangle-mesh model into a Blockbench cube model (`.bbmodel`, free format, per-face UV) with a
1 texel = 1 pixel texture, so it sits with the rest of the pack's cuboid art.

```
pip install numpy scipy trimesh pillow pygltflib
python3 tools/mesh_to_cubes/mesh2bb.py creature.glb creature.bbmodel --height 16 --preview creature.png
```

- Input: GLB/GLTF (best: keeps the skeleton and texture), OBJ, PLY. From Blender: File > Export > glTF 2.0 (.glb).
- Rigged models are split into Blockbench groups by joint (head, neck, body, tail, left/right arm and leg, wings),
  with each group's pivot where it meets its neighbour, ready to animate. `--parts '{"Jaw":"head"}'` adds rules.
- Unrigged models come out as one `body` group.
- `--height` sets the size in pixels, `--pitch 2` gives chunkier blocks, `--max-cubes` and `--fill` trade cube
  count against shape accuracy, `--colours` sets the palette size.
- `--preview` writes a before/after sheet: the mesh on top, the cubes below (`bbrender.py`, same camera).

The output is a starting point: open it in Blockbench and hand-tidy faces, eyes and small details, the same way the
deer-horse models were finished.
