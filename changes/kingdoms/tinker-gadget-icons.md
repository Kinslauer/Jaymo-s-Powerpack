# Tinkerer's gadgets: 19 hand-drawn icons
- **Art only.** The Tinkerer's 19 mimic gadgets had placeholder icons drawn by a script. Each now has its own
  hand-painted 16x16 icon in the vanilla style (light from the top left, no black outlines), so they can be told
  apart in the hotbar and on the Tinkerer's Notebook screen. Every one is workshop work in brass, copper, iron,
  leather and glass, with one hint of what it copies: a shulker-shell lens on the Lift Rig, a sculk-teal ring in the
  Sonic Horn's bell, a guardian's eye in the Beam Lens, bone fangs on the Jaw Rake, a creeper-green Hissing Charge
  with a lit fuse.
- Lift Rig and Jaw Rake are drawn on the diagonal like vanilla tools; the other 17 as objects held in the hand.
- `tools/tinker_gadget_icons.py` holds the 19 pixel grids and repaints the PNGs. Don't rerun the texture step of
  `tools/gen_tinker_mimic.py`: it would put the placeholders back.
- Checked side by side at hotbar size on light and dark backgrounds; not seen in game.
