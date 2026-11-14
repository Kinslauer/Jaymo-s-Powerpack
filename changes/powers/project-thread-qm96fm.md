# chain axe swings like the flail, with its own 3D model
- Andy: "the chain axe and lasso should operate similar to the [whip] and flail, therefore they need blockbench models".
- **Chain Axe** is whirled as the flail is (`armory/Lash`, `Lash.isFlail` now takes it): hold right click and the axe
  head goes round you on its chain, clipping what it passes; a charged left click swipes it round in front; the flail's
  finisher is its finisher. The old instant throw drawn in chain particles, and its hook and drag, are gone.
- **The throw.** Andy: "after letting go of the right click hold the axe flies toward the cursor, up to 9 blocks". Let
  go of the whirl and the head flies at what is under the crosshair (`Lash.THROW`, `ChainAxeItem.CHAIN` 9 blocks,
  `THROW_SPEED` 1.5 blocks a tick), hitting harder the faster it was whirling. Off a wall, a raised shield or nothing it
  comes back.
- **The axe stays in.** Andy: "no hooking, a throw embeds the axe, and you have to remove it, either by closing the
  distance, or ripping it out." The head stays buried in whoever the throw hits (`Lash.EMBED`, the new item component
  `jaymospowers:buried_in` on the axe), drawn in the side of them that faces you with the chain running back to the
  haft, and the haft's own blows do nothing until it is out. Right click beside them (`FREE_RANGE`, 3 blocks) to work it
  free; right click from further off to rip it out by the chain: a second wound (`RIP_SHARE`, 60% of the axe's blow), a
  point of wear and a longer wait (`RIP_WAIT`, 2 s). Past 9 blocks it tears out by itself; if what it is in dies it
  drops free.
- **The dropped haft.** Andy: "swapping with the head still embedded should drop the haft on the ground, still attached
  and rendered in world. you would have to pick it up and remove it to continue." Switch away from the buried axe and the
  haft falls where you stand (it never despawns while buried), the chain drawn from it to the head
  (`LashRenderer.droppedHafts`). Take it up in your hand and it is buried as before, to be worked free or ripped out.
  If what the head is in walks off past the chain's length, the haft is dragged after it (`DRAG`).
- **Model**: in the hand it is a 3D haft (`chain_axe_handle`), the chain is the flail's physics chain leaving the staple
  on the haft's top (`LashSockets.CHAIN_AXE_TIP`), and the head on the chain's end is a 3D bearded axe head
  (`chain_axe_swung`, drawn by `LashRenderer.axeHead`, `AXE_HEAD_SCALE`). Dropped or in a frame it is the whole axe in 3D
  (`chain_axe_whole`); the inventory icon is the painted one as before.
- Blockbench project `art/chain_axe.bbmodel`; all of it is written by `tools/gen_chain_weapons.py`
  (`docs/Chain-Weapons-Model-Spec.md`).
- Compiled; not played. To look at in game: where the haft sits in the fist, that the chain meets its top in first and
  third person, and the head's size on the chain, how the buried head sits in a mob, and the dropped haft being dragged.
