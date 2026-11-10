---
status: ready
for: hermes
from: tiv (Discord, Tasks thread, 2026-11-08)
asked: 2026-11-08
thread: 1569170499680870560
claimed_by: hermes-a
pr: 1213
suggested_owns: [src/main/resources/data/jaymospowers/recipe/, src/main/java/com/jaymospowers/core/wounds/Breaks.java]
text_pass: none
---

# Saplings turn into sticks, so splints are within reach

A sapling you can't plant is a stick.

## What's wanted

Tiv, in the Tasks thread: "saplings should turn into sticks, so that you can get to splints easier."

## Notes

- A splint costs two sticks and string (or leather) — see `docs/Wounds-Phase4.md`. Break a leg in the wild with no tree at hand and you can't set it.
- So: a recipe that turns saplings into sticks — one sapling each, a few sticks out of it (two per sapling makes one splint from a single sapling).
- Keep it shapeless and simple so it reads in the recipe book.
- A recipe only: no new items, no changes to the splint itself.
- If a sapling already has another use somewhere in the pack, leave that alone and just add this.
- 2026-11-09 23:38 Andy skipped the text pass (2026-11-09); ready for review.
- 2026-11-09 23:42 Review (head ef9680c): pass. Every sapling (the 9 vanilla ones incl. mangrove propagule and pale oak, and the 11 Aether ones, all ids checked against master) crafts shapeless into 2 sticks, so one sapling makes a splint; same recipe format as the pack's other shapeless recipes, change note only, checks green. Not played; saplings from other pack mods (none found) aren't covered.
- 2026-11-09 23:42 Reviewed; Andy allowed sending it to the coordinator.
