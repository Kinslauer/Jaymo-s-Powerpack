---
status: done
for: hermes
from: tiv (Discord, Tasks thread, 2026-11-08)
asked: 2026-11-08
thread: 1569170499680870560
claimed_by: claude-cloud
pr: 1227
suggested_owns: [kingdoms/src/main/resources/data/jaymoskingdoms/structure/, kingdoms/src/main/resources/data/jaymoskingdoms/worldgen/, src/main/java/com/jaymospowers/ground/]
---

# Structure buttons become real debris: our rocks and pebbles

Structures are peppered with buttons pretending to be rubble. We have actual rubble now.

## What's wanted

Tiv, in the Tasks thread: "Alot of buttons are placed in structures, maybe for aesthetics? They can all be removed in place of random amounts of our rocks and pebbles. They all are trying to go for a debris effect, but now we have real debris."

## Notes

- The buttons are there as scattered litter — an old trick before the pack had anything better to scatter. Our own loose rocks and pebbles (`loose_rocks`, `pebble_piles`, the ground features in Powers) do that job properly now.
- **Scope is known now: they're in the Stormborn tomb** (the dungeon ported in #1137). It's ours, so its templates can be edited directly — no generation-time strip needed, and nothing to worry about with vanilla or other mods' structures.
- Tiv's own note: it's a small detail. If the pieces turn out to be awkward to edit, leave them rather than spending a PR on it — and say so rather than half-doing it.
- Replace them with random amounts: a handful in a room, not a neat one-to-one swap — some corners get two or three, most get none. Rocks belong on the floor of a room or corridor; don't leave one stuck to a wall or floating where the button was.
- **Check first that no button does something**: a button on a door, a hidden piston, a secret panel is not decoration. Only the ones that do nothing go.
- Whatever is done, old worlds keep their old buttons: say whether this only fixes new generation.
- No new blocks or items — this is a swap to what already exists.
- 2026-11-09 23:59 Cloud agent session sesn_01CsHsTossr1hmRgAU8FgGJu opened PR #1227.
- 2026-11-10 04:31 PR #1227 is merged into master; moved to Inbox/Done.
