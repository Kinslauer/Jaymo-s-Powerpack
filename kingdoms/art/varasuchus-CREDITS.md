# Varasuchus asset credit

Varasuchus's geometry, 256×256 UV layout, hides, marking sheets and motion design are **Jaymo's Kingdoms project art**: the body and paint from `tools/gen_varasuchus.py`, the motion from the former `VarasuchusDragonModel.setupAnim`. No outside model, texture or animation data is in it.

Prometheus made the editable Blockbench model and its GeckoLib keyframes with `kingdoms/tools/gen_varasuchus_geo.py`, which ports the old pose code line for line and samples it.

Saint's Dragons by Leon Saint is a **reference for gameplay and motion only**. Its code is MIT; its art is All Rights Reserved. None of its art, models, textures, sounds or keyframes were copied.

## Asset notes

- `varasuchus.bbmodel` has 75 bones, 75 cubes (one each, with the same box-UV faces, mirroring included) and 33 animations.
- Loops: four idle fidgets (hiss, head shake, tail swish, sniff; each a 15 s cycle), walk, run (gallop), stand and stand_walk (raging, upright), sit (basking), crash, swim.
- Moves: claw, horn, dash, barrage, shift, guard, parry, rage, bite and sweep, each also as `<move>_up` for when it's upright; then hurt and a held death. It doesn't fly, so there is no fly, glide or land.
- `varasuchus-render.png`: the rest pose, with the optional horns and the tack left out.
- `varasuchus-comparisons.png`: 34 pairs, five views each, with the old pose (the ported equations through `tools/bbmodel_preview.py`'s `POSE`) above the new animation.
- GeckoLib (Tslat and contributors, MIT) is a runtime library. None of its code or art is included.
