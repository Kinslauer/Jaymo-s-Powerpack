---
status: handed-over
task: no-lift-cooking-blocks
branch: no-lift-cooking-blocks
with:
pr: 2035
mods: [powers]
versions:
started: 2026-11-23
owns:
  - src/main/resources/data/jaymospowers/tags/block/carry_on_no_lift.json
  - docs/carry.md
  - changes/powers/no-lift-cooking-blocks.md
---

# Sneak-click on cooking blocks opens them, not Carry On

**Task:** Andy: "Shift clicking should not pickup cooking blocks, as it is right now i cant use any of the cooking guis
because shift clicking picks up the block, even campfires."

## Done
- The lifting is Carry On. Added campfires, furnace, smoker, blast furnace, CfB ovens/counters/cooking tables/cutting
  board and Kitchen's cooking and brewing stations to `#jaymospowers:carry_on_no_lift`. Carry On passes a refused lift on
  to the block's own use (checked in its 2.12.1 bytecode).
- These blocks can't be carried with contents by Carry On any more (Andy's call if he wants another way).
- Built in CI only; not played.

## Handed over (2026-11-23)
Andy asked for a sneak-click choice box (Use (verb) / Lift) in PR 2035, then handed it to another bot. Notes: side-session-notes/local_2e1ecf8a-.../block-choice-box-handover.md. No code written for it.
