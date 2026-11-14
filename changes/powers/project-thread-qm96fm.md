# chain axe swings like the flail, with its own 3D model
- Andy: "the chain axe and lasso should operate similar to the [whip] and flail, therefore they need blockbench models".
- **Chain Axe** is swung as the flail is (`armory/Lash`, `Lash.isFlail` now takes it): hold right click and the axe head
  whirls round you on its chain, let go and it comes down where you look; a charged left click swipes it round in front;
  the flail's finisher is its finisher. It no longer throws out nine blocks in a puff of chain particles. Still its own:
  whoever the head comes down on is hooked and tugged toward you, dragged all the way in if you sneak
  (`ChainAxeItem.haul`, `TUG` / `HAUL`).
- **Model**: in the hand it is a 3D haft (`chain_axe_handle`), the chain is the flail's physics chain leaving the staple
  on the haft's top (`LashSockets.CHAIN_AXE_TIP`), and the head on the chain's end is a 3D bearded axe head
  (`chain_axe_swung`, drawn by `LashRenderer.axeHead`, `AXE_HEAD_SCALE`). Dropped or in a frame it is the whole axe in 3D
  (`chain_axe_whole`); the inventory icon is the painted one as before.
- Blockbench project `art/chain_axe.bbmodel`; all of it is written by `tools/gen_chain_weapons.py`
  (`docs/Chain-Weapons-Model-Spec.md`).
- Compiled; not played. To look at in game: where the haft sits in the fist, that the chain meets its top in first and
  third person, and the head's size on the chain.
