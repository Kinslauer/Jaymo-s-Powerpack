# Mimic asset credit

The Mimic true-form geometry, pixel texture and seven animations are original work by **Prometheus (hermes-modeller)** for Jaymo's Powers. No external model, texture or animation data is included, so no upstream art licence applies.

Design/mechanic reference only: Tiv linked ItsJim's **“Building a Minecraft Horror Mod that Mimics You”** (https://www.youtube.com/watch?v=74QYqhvNHWk), particularly the player-movement recording at 0:35 and the copied-player/monstrous-reveal concept. No art was copied from the video or Jim's Mine.

## Asset notes

- `mimic.bbmodel` is the editable GeckoLib Blockbench source, with its 128×128 texture embedded.
- The model faces north (-z), has its soles at y=0, and contains 21 uniquely named bones and 61 cubes.
- The original design is an ashen, stretched humanoid with asymmetric eye sockets, a hinged tooth-filled jaw, exposed ribs and spine, long clawed fingers, and the remnants of dark clothing.
- Animations: `idle`, `walk`, `run`, `attack`, `transform`, `hurt`, and `death`.
- The GeckoLib 5 exports use `geckolib/models/entity/` and `geckolib/animations/entity/` under `assets/jaymospowers/`.
- `mimic-render.png` is the exact front, left-side, back and right-side inspection render made with `tools/mesh_to_cubes/bbrender.py`; all four views were visually checked for missing faces, detached cubes, texture gaps and orientation.
