# Excalibur look (optional, off by default)

Andy (2026-11-03): "I'd like a different artstyle from vanilla entirely", pointing at Excalibur with its Fresh
Animations patch. 0.19.65 switched it on for everyone; the same evening Andy asked for it to be optional: "make
excalibur and all it's additions from here optional, and off by default. but keep it in the pack." Since 0.19.72:

- **`/excalibur on`** switches the look on: Excalibur at the bottom of the pack order, its Fresh Animations patch just
  above Fresh Animations, and our built-in `excalibur_style` pack on top. **`/excalibur off`** takes all three off;
  plain `/excalibur` says which it is (`client/ExcaliburLook`). They can also be picked by hand in Resource Packs, but
  then the order is up to you.
- **Recrafted** (MR_CHOO) is a paid early-access pack with no licence to redistribute, so it's never shipped or
  fetched. If a player has it in their own resourcepacks/, `/excalibur on` switches it on too, just above Excalibur.
  It's made for 1.21.1-1.21.8, not 26.3, so some of it may not load.
- Our mods' own textures are the originals again. The graded copies live only in `excalibur_style`.
- Installs that 0.19.65 had switched on get the look switched off once, on the first launch with 0.19.72.

## What's shipped (unchanged)
- **Excalibur** by Matt "Maffhew" Dillow, CC BY-NC-ND 3.0 (its README): ships unchanged, never edited, built on or
  copied from, with a link to its official page (https://www.curseforge.com/minecraft/texture-packs/excalibur or
  https://modrinth.com/resourcepack/excal) on the pack's description page. Version 26.3_01 from Modrinth. It goes at
  the bottom of the pack order (`PackResourcePacks`, `BASE`), so every other pack draws over it.
- **Excalibur | Fresh Animations** by Ruseki and GrandPappyJay, all rights reserved, but its page lets modpacks include
  it ("just don't reupload or redistribute"). Version 1.10.5 Hotfix 1 for 26.3 from Modrinth, placed just above Fresh
  Animations. It's made against Fresh Animations 1.10.5; we ship 1.11.1. The mobs it covers bring their own models, so
  they still match. If a mob looks wrong in game, that version gap is the first suspect.
- Both are downloaded into resourcepacks/ on first launch (from Modrinth, checked by SHA-1) but never switched on by us.

## Our textures: the grade (built-in pack `excalibur_style`)
Measured over 784 items and 1,136 blocks against vanilla 26.3, Excalibur comes out about 16% darker and 35 to 40% less
saturated. Its greys are warmer, its item silhouettes are slimmer and smaller (they fill 44% of the square against 52%),
and its outlines are softer.

- `tools/excalibur_style/fit.py` fits `grade.json` from paired textures. It keeps statistics only: for each of 12 hue
  bands and the greys, 21 lightness and 21 saturation quantiles from each pack, plus a small hue nudge. None of
  Excalibur's pixels or textures are kept.
- `tools/excalibur_style/grade.py` grades every PNG under `textures/{item,block,entity,clothing}` of Powers, Kingdoms
  and Kitchen, and the vanilla_style pack's redraws, **into the pack** at the same assets path. vanilla_style's
  version wins where both exist, and animation `.mcmeta` files are copied too. The sources are never changed. It
  takes about a minute.
- Left out, so the mod's own version shows: glows, effects and lights (`SKIP_WORDS`: glow, emissive, fx, aura, beam,
  flame, halo, eyes, wisp and the like; mob fire layers), textures where more than a quarter of the visible pixels are
  see-through (ice, glass, aerclouds, haze wings), and GUI, particle and mob-effect art.

**Rerun `python -I tools/excalibur_style/grade.py` before a release when textures have changed**, so the optional
look keeps up. Forgetting it isn't harmful: a texture without a graded copy just shows its normal colours under the
look. It adds about 43 MB to the Powers jar.

**0.19.65 to 0.19.70 graded the sources in place.** 0.19.72 put all 7,713 back. Each restored file was checked:
grading it again gives the graded file exactly. The 10 xenoform skins whose originals were never committed (#868 graded
them before committing) were regenerated with `tools/gen_xenoform.py`. If an old branch still carries graded textures,
take master's version.

## Next: hand repaints
The grade sets the mood, but shapes stay as drawn. Hero items get redrawn by hand in the Excalibur direction, our own
pixels only: slimmer blades with a fuller line, darker plate, muted metals and fewer hard outlines. Samples and a
preview are in the project's `art-overhaul/excalibur-style/` folder (Durandal, rustic bread, paladin chestplate).

## Leaves (in `excalibur_style`)
Excalibur's leaf models hang a 6-pixel fringe off each side of every leaf block and put crossed planes on top. Leaf
blocks never hide their neighbours' faces, so deep inside a canopy those fringes repeat block after block (Andy: "it
looks like a hall of mirrors"). The `excalibur_style` pack puts vanilla's plain leaf cubes back for the 11 vanilla
leaves. Excalibur's leaf textures still show, and each block is turned at random (4 turns, upright or flipped) so the
canopy doesn't tile.
