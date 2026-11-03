# Excalibur style for the whole pack

Andy (2026-11-03): "I'd like a different artstyle from vanilla entirely", pointing at Excalibur with its Fresh
Animations patch. The pack ships those two unchanged and grades our own textures toward their look.

## What's shipped (unchanged)
- **Excalibur** by Matt "Maffhew" Dillow, CC BY-NC-ND 3.0 (its README): ships unchanged, never edited, built on or
  copied from, with a link to its official page (https://www.curseforge.com/minecraft/texture-packs/excalibur or
  https://modrinth.com/resourcepack/excal) on the pack's description page. Version 26.3_01 from Modrinth. It goes at
  the bottom of the pack order (`PackResourcePacks`, `BASE`), so every other pack draws over it.
- **Excalibur | Fresh Animations** by Ruseki and GrandPappyJay, all rights reserved, but its page lets modpacks include
  it ("just don't reupload or redistribute"). Version 1.10.5 Hotfix 1 for 26.3 from Modrinth, placed just above Fresh
  Animations. It's made against Fresh Animations 1.10.5; we ship 1.11.1. The mobs it covers bring their own models, so
  they still match. If a mob looks wrong in game, that version gap is the first suspect.
- Both are switched on once per install, like the other packs (delete `config/jaymospowers-excalibur-v1` or
  `-excalibur-fa-v1` to have it done again).

## Our textures: the grade
Measured over 784 items and 1,136 blocks against vanilla 26.3, Excalibur comes out about 16% darker and 35 to 40% less
saturated. Its greys are warmer, its item silhouettes are slimmer and smaller (they fill 44% of the square against 52%),
and its outlines are softer.

- `tools/excalibur_style/fit.py` fits `grade.json` from paired textures. It keeps statistics only: for each of 12 hue
  bands and the greys, 21 lightness and 21 saturation quantiles from each pack, plus a small hue nudge. None of
  Excalibur's pixels or textures are kept.
- `tools/excalibur_style/grade.py` grades every PNG under `textures/{item,block,entity,clothing}` of Powers, Kingdoms
  and Kitchen, and the vanilla_style pack's own redraws, **in place**. `graded.txt` holds each graded file's SHA-1, so
  a file is never graded twice.
- Left alone: glows, effects and lights (`SKIP_WORDS`: glow, emissive, fx, aura, beam, flame, halo, eyes, wisp and the
  like; mob fire layers), textures where more than a quarter of the visible pixels are see-through (ice, glass,
  aerclouds, haze wings), and GUI, particle and mob-effect art.

**After any new or redrawn texture, run `python -I tools/excalibur_style/grade.py` from the repository root.** That
covers a `gen_*.py` rerun, another PR's art, or a binary merge conflict resolved by taking theirs. It grades only what
is new or changed. `--check` lists those files without changing anything.

## Next: hand repaints
The grade sets the mood, but shapes stay as drawn. Hero items get redrawn by hand in the Excalibur direction, our own
pixels only: slimmer blades with a fuller line, darker plate, muted metals and fewer hard outlines. Samples and a
preview are in the project's `art-overhaul/excalibur-style/` folder (Durandal, rustic bread, paladin chestplate).
