# tree debris only when a player brought the tree down
- Falling trees leave drops only when a player caused the fall: they cut it, a tree they cut (ours or Felled's) knocked it over, or they've taken an axe to it since (`FallingTree.byPlayer`, saved). Trees brought down by storms, fire, giants, tornadoes, loose wood or a woodcutter's stray knock leave nothing: no saplings, sticks or apples from their leaves, nothing from the leaves and blocks they smash on the way down. A woodcutter's own tree still goes into his bag.
- A player's fallen tree still drops its leaves a few at a time as it lies, but those only give drops while a player is within 32 blocks (`DEBRIS_WATCH`); otherwise they wither to nothing.
- Loose bare logs falling like sand no longer turn into item drops when they can't land as a block.
- Fixes villages and forests filling with hundreds of item drops after a storm (lag). Built; not played.
