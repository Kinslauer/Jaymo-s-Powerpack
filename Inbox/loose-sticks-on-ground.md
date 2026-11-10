--- 
status: in-review
for: hermes
from: tiv (Discord, Tasks thread, 2026-11-08)
asked: 2026-11-08
thread: 1569170499680870560
pr: 1248
suggested_owns: [src/main/java/com/jaymospowers/ground/, src/main/resources/data/jaymospowers/worldgen/, src/main/resources/assets/jaymospowers/models/item/, src/main/resources/assets/jaymospowers/textures/item/]
writer: hermes-writer
claimed_by: hermes-b
text_pass: done
---

# Loose sticks on the ground, like pebbles

Sticks scattered on the ground so starting out doesn't need a tree.

## What's wanted

Tiv, in the Tasks thread: "this mod has sticks on the ground you can grab. alongside the pebbles. we need the same so starting is more manageable."

Reference: the "Surviving the Alaskan Wilderness in Minecraft" video (Chano) shows sticks lying on the ground to pick up — that's the feel.

## Notes

- Powers already has pebbles and loose rocks as ground features (`PebblePilesFeature`, `loose_rocks`, `ground/OrePebbleBlock`) — this is the same thing but sticks: a single stick block/item on the ground you break and grab.
- Don't change pebbles or loose rocks themselves; add sticks alongside them.
- Say where they generate (grass, dirt, leaf litter — not inside stone) and how common.
- A stick on the ground is one stick you break and pick up; random amounts per pile, same as pebbles.
- Don't touch the saplings-to-sticks recipe (that's done, PR #1213) — this is a separate, complementary source.
- 2026-11-10 05:15 Cloud agent session sesn_016CYDN8UPNUuUk9p8APUqFS opened PR #1244.
- 2026-11-10 05:28 Text pass: reviewed ground.json lang entry ('Loose Sticks') and changes/powers/loose-sticks-on-ground.md change note — both clear and in voice, no rewrites needed.
- 2026-11-10 07:09 Text pass: ground.json 'Loose Sticks' and changes/powers/loose-sticks-on-ground.md — both clear and in voice, no rewrites needed
