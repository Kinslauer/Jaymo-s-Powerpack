# Ash piles fixed

Ash piles left by burnt corpses now look and feel like their height: you sink into them like snow instead of
standing on an invisible full block, a single layer doesn't stop you, and the blocks next to a pile no longer show
holes. Breaking one drops it (its loot table was in the old folder the game no longer reads), and it has a proper
inventory icon.

- `com.jaymoskitchen.corpse.AshPileBlock`: shape and collision per layer, default state of 1 layer.
- `items/ash_pile.json` added; the item model is the 1-layer pile. Loot table moved to `loot_table/blocks/`.
- `Corpses`: registration indentation fixed. Not built locally (CI checks it); not played in game.
