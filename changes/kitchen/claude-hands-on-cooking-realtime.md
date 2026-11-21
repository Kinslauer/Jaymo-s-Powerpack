# Hands-On Cooking / Realtime Minigames (Phase 1+2)

Adds in-world, hands-on cooking interactions for CHOP (cutting board) and STIR (cooking pot), inspired by Hands-On Delight. No menus — players interact directly with blocks.

## What a player will see
- **Cutting board (CHOP):** Place a vegetable on the board, hold a knife, and left-click to chop as the vegetable slides under the blade. Perfect/good/ragged feedback shown in-world. 8 cuts to finish.
- **Cooking pot (STIR):** Place a heated cooking pot over fire, hold a spoon, and move the mouse in a circle around the pot to stir. Speed gauge shows optimal range; boil-ups require faster stirring. Stick/slops tracked in-world.
- **New spoon item** in the Tools & Utilities creative tab.
- **Config toggle** `handsOnCooking.enabled` (default true for testing) to fall back to screen-based cooking.
- Existing screen-based cooking (CookingScreen) still works for recipe selection and other methods (GRILL, OVEN, SEAR, SMOKE, ROAST, SEQUENCE, PREP).

## Not tested in game
- Multiplayer: multiple players at same station type (different blocks)
- Multiplayer: two players at same block (double stove)
- Walk-away abandonment burn/char judgment
- Disconnect mid-cook session cleanup
- Resource pack compatibility with custom pot/board models
- Performance with 20+ simultaneous cooking players