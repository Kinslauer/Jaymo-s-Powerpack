# chain axe swings like the flail, with its own 3D model
- Andy: "the chain axe and lasso should operate similar to the [whip] and flail, therefore they need blockbench models".
- **Chain Axe** is swung as the flail is (`armory/Lash`, `Lash.isFlail` now takes it): hold right click and the axe head
  whirls round you on its chain, let go and it comes down where you look; a charged left click swipes it round in front;
  the flail's finisher is its finisher. It no longer throws out nine blocks in a puff of chain particles, and it no
  longer hooks and drags what it hits.
- **The axe stays in.** Andy: "no hooking, a throw embeds the axe, and you have to remove it, either by closing the
  distance, or ripping it out." The head stays buried in whoever the slam hits (`Lash.EMBED`, drawn in the side of them
  that faces you with the chain running back to the haft), and the haft's own blows do nothing until it is out. Right
  click beside them (`ChainAxeItem.FREE_RANGE`, 3 blocks) to work it free; right click from further off to rip it out by
  the chain: a second wound (`RIP_SHARE`, 60% of the axe's blow), a point of wear and a longer wait (`RIP_WAIT`, 2 s).
  Past 9 blocks (`CHAIN`) it tears out by itself; if what it is in dies it drops free. Switching to another slot also
  takes it back, with no wound.
- **Model**: in the hand it is a 3D haft (`chain_axe_handle`), the chain is the flail's physics chain leaving the staple
  on the haft's top (`LashSockets.CHAIN_AXE_TIP`), and the head on the chain's end is a 3D bearded axe head
  (`chain_axe_swung`, drawn by `LashRenderer.axeHead`, `AXE_HEAD_SCALE`). Dropped or in a frame it is the whole axe in 3D
  (`chain_axe_whole`); the inventory icon is the painted one as before.
- Blockbench project `art/chain_axe.bbmodel`; all of it is written by `tools/gen_chain_weapons.py`
  (`docs/Chain-Weapons-Model-Spec.md`).
- Compiled; not played. To look at in game: where the haft sits in the fist, that the chain meets its top in first and
  third person, and the head's size on the chain.
