# Astronomy art: the working study

Original artwork for Jaymo's Kingdoms. Andy's supplied `astronomy-reference.png` is the visual direction only; no pixels, model geometry or other assets were copied from it or from the Astronomy mod.

## Shared palette

| Use | Colour |
| --- | --- |
| Brown-black ink | `#2b211a` |
| Faint construction line | `#8d7351` |
| Warm study paper | `#c2a77a` |
| Walnut | `#755239` |
| Walnut cut edge | `#a07852` |
| Aged brass | `#a47c36` |
| Brass edge / rivet | `#d0ab5a` |
| Optical glass | `#345d61` |
| Glass reflection | `#8aaca1` |

Use dark sepia outlines and hard-edged parallel hatching to describe volume. Keep circles, centre points and guide lines visible on scales and study pages. Do not introduce gradients, soft glow, glossy metal or floating assemblies. Later telescope, armillary, sun/moon and scholar-journal assets should reuse this palette.

## Telescope deliverable

- `telescope.bbmodel`: editable 64×64 java-block model, with named tripod, mount, barrel and optics groups. Its long walnut tube has stepped octagonal sides, brass binding rings, a glass objective, an eyepiece and a focus screw. Three jointed legs meet at a timber head; the mount carries an engraved altitude scale.
- `telescope_item.bbmodel`: editable source for the original 16×16 inventory icon. The icon uses one hatch direction and only suggests the tripod at pixel scale.
- `telescope_preview.png`: front, right side, back and left side renders of the placed model.
- `telescope_item_preview.png`: matching renders of the icon source.
- `generate_telescope.py`: deterministic source for the PNGs, `.bbmodel` files and exported Minecraft JSON.
- `render_previews.py`: exact front/right/back/left checks using the repository renderer; the flat item's two sides are separated to avoid the renderer overprinting them.

The exported block model uses only Minecraft's permitted single-axis 22.5° rotations. Registration, blockstates, aiming and observation behaviour deliberately remain with `astronomy-telescope`; this change contains no gameplay Java.
