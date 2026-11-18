# Oversized drops split into full stacks

- Fixes: a dropped stack bigger than its item's stack size (Droppable Villager Trades can drop up to 320 of a dead villager's trade at once, 152 bricks on the Deck) couldn't be saved and was lost when its chunk unloaded. Such a drop is now split into full stacks as it lands (`OversizedDropMixin`).
- Built by CI; not played.
