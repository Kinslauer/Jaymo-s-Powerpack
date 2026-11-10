# Skulk golem moves to GeckoLib, with an idle, waking, a flinch and a death

The skulk golem keeps its stats, statue guard, darkness blow, sounds, scale and shadow, but is now drawn by GeckoLib with new animations as well as its model's own walk, statue pose and blow. Awake and standing it breathes, its antennae twitching and its arms held forward as in its walk (before, it stood in its rest pose with its fists sunk into the floor). Woken, it shudders in the stone, rears up with its arms flung out and drops into its stance; after a while with nobody about it bows back down into its statue pose. It flinches when hit, and dies by reeling back and toppling onto its face instead of falling over sideways. Its head still turns toward what it's looking at.

The animations are written by `kingdoms/tools/gen_skulk_golem_geo.py` into the editable `kingdoms/art/skulk_golem.bbmodel`, with its CC BY 4.0 licence, credit and side-by-side renders of old and new poses. Original model, texture and animations: "Skulk golem" by elma_taburlas (https://blockbenchworkshop.com/model/elma_taburlas/skulk-golem).

Not seen in game: timing, blends and the death fall still need a playtest.
