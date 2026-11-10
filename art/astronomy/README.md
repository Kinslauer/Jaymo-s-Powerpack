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

- `telescope.bbmodel`: editable 64×64 GeckoLib block model, centred with its feet at y=0. A fixed `base` carries the
  tripod; `azimuth` yaws the mount and the child `elevation` joint tilts the complete walnut barrel and optics around
  the brass axle. Includes editable `idle`, `azimuth`, `elevation` and combined `aim` animations. Its long walnut tube
  has stepped octagonal sides, brass binding rings, a glass objective, an eyepiece and a focus screw. Three jointed
  legs meet at a timber head; the mount carries an engraved altitude scale.
- `telescope_item.bbmodel`: editable source for the original 16×16 inventory icon. The icon uses one hatch direction and only suggests the tripod at pixel scale.
- `telescope_preview.png`: front, right side, back and left side renders of the placed model.
- `telescope_rig_preview.png`: direct-front checks of idle, 90° azimuth, raised elevation and combined aim.
- `telescope_item_preview.png`: matching renders of the icon source.
- `generate_telescope.py`: deterministic source for the PNGs, `.bbmodel` files, static fallback and GeckoLib block JSON.
- `render_previews.py`: exact front/right/back/left checks using the repository renderer; the flat item's two sides are separated to avoid the renderer overprinting them.

GeckoLib 5 reads `geckolib/models/block/telescope.geo.json` and `geckolib/animations/block/telescope.animation.json`,
with the existing `textures/block/telescope.png`. The old Java block JSON remains only as a static fallback while
registration is being wired. One telescope is enough: the Altazimuth Mount can use this same rig and its physical
scales, rather than adding basic/mounted/brass tiers that duplicate the instrument. Registration, saved aim, skill
checks, blockstates and observation behaviour deliberately remain with the gameplay implementation; this change
contains no gameplay Java.

## Scholar journal deliverable

- `generate_journal.py`: deterministic source for the Astronomer's Journal art. It writes
  `textures/gui/astronomy/journal_spread.png` (384×240, the open book the journal screen draws 1:1),
  `textures/item/astronomers_journal.png` (16×16) and `journal_preview.png` (both, enlarged, for inspection).
- The spread: warm study paper (lifted a little from `#c2a77a` so the screen's ink text reads), the walnut boards and
  stacked page edges, the fold drawn with broken parallel hatching, never a gradient. Faint construction studies sit
  under the text: on the left the celestial sphere seen from latitude 45° (horizon, the pole axis at 45°, the equator,
  a star's daily circle, the latitude angle, a 10° scale, the bounding square and its diagonals) with a quadrant; on
  the right nested armillary rings on a stand and a right triangle with its angle. A working hand of illegible notes
  fills the margins, with a few numerals in rubric red.
- The icon: a walnut-bound journal with a brass armillary ring on its board, cream page edges, one hatch direction.
- Original art; nothing was copied from the reference image or from the Astronomy mod.
