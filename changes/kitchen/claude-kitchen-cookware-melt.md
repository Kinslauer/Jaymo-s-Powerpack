# cutting board - split a seared-bricks block back into bricks

- **Brick split**: lay a seared-bricks block on the cutting board and right-click it with a pickaxe; it comes apart into the four seared bricks it was built from, so a smeltery wall can be taken down and rebuilt. A knife still does nothing to it, and food is still knife-only.
- Kitchen names the block by id (`jaymospowers:seared_bricks`), so Kitchen keeps no compile dependency on Powers; with Powers absent the board simply has nothing to split. The table of sturdy blocks lives in `Chopping.split` and the tool choice in `CuttingBoardBlock.cut`.
- Cooking pot, frying pan and saucepan already melt back to their iron at the smeltery — no change needed there, the melting table derives them from their crafting recipes.
- Not tested in game.
