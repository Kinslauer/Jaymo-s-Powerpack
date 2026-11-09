---
status: open
for: hermes
from: tiv (Discord, Tasks thread, 2026-11-08)
asked: 2026-11-08
thread: 1569170499680870560
claimed_by:
pr:
suggested_owns: [kingdoms/src/main/resources/data/jaymoskingdoms/structure/, kingdoms/src/main/resources/data/jaymoskingdoms/worldgen/, src/main/java/com/jaymospowers/ground/]
---

# Structure buttons become real debris: our rocks and pebbles

Structures are peppered with buttons pretending to be rubble. We have actual rubble now.

## What's wanted

Tiv, in the Tasks thread: "Alot of buttons are placed in structures, maybe for aesthetics? They can all be removed in place of random amounts of our rocks and pebbles. They all are trying to go for a debris effect, but now we have real debris."

## Notes

- The buttons are there as scattered litter — an old trick before the pack had anything better to scatter. Our own loose rocks and pebbles (`loose_rocks`, `pebble_piles`, the ground features in Powers) do that job properly now.
- Replace them with random amounts: a handful in a room, not a neat one-to-one swap — some corners get two or three, most get none. Rocks belong on the floor of a room or corridor; don't leave one stuck to a wall or floating where the button was.
- **Check first that no button does something**: a button on a door, a hidden piston, a secret panel is not decoration. Only the ones that do nothing go.
- Scope has to be worked out and said plainly: our own structure templates can be edited directly; vanilla villages, strongholds and other mods' structures can't be re-authored, so if Tiv means those too the honest answer is a generation pass that strips button blocks as a structure is built, and that carries its own risk (it would eat working buttons elsewhere). Work out which structures he's actually seeing buttons in and report the reach before building the wide version.
- Whatever is done, old worlds keep their old buttons: say whether this only fixes new generation.
- No new blocks or items — this is a swap to what already exists.
