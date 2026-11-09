---
status: open
for: hermes
from: tiv (Discord, Tasks thread, 2026-11-08)
asked: 2026-11-08
thread: 1569170499680870560
claimed_by:
pr:
suggested_owns: [kingdoms/src/main/resources/data/jaymoskingdoms/structure/, kingdoms/src/main/resources/data/jaymoskingdoms/template_pool/, kingdoms/src/main/resources/data/jaymoskingdoms/worldgen/]
---

# The new dungeons come out filled with stone, and with trial chamber rooms

Some of the generated dungeon is solid stone, and parts of it are vanilla trial chambers instead of our own build.

## What's wanted

Tiv, in the Tasks thread: "also some of the new generated dungeon is filled with stone and or trial chambers." Two screenshots, kept with this task:

- `E:/Jaymo Vault Live/Assets/dungeon-stone-fill-1.png` — a stone brick hall with a timber walkway and a raised platform, coordinates 3269 / -2 / 5216. Whole stretches read as plain fill.
- `E:/Jaymo Vault Live/Assets/dungeon-stone-fill-2.png` — deepslate brick pillars and chiseled deepslate, cobwebs, coordinates 3293 / -16 / 5071: that is vanilla trial chamber masonry, inside a dungeon of ours.

## Notes

- The new dungeon is the Stormborn tomb port that merged as PR #1137, so start there: its jigsaw pieces, template pools and structure files.
- Stone fill in a jigsaw build usually means the pieces carry their `structure_block`/structure-void data wrong — the void blocks turn into stone, or a piece generates without the interior carved out. Check the pieces as they are placed with `/place structure`, not just in the .nbt.
- Trial chamber masonry inside it usually means the structure or pool is resolving to a vanilla `minecraft:trial_chambers` piece (a wrong pool id, a fallback, or a start pool that points at the wrong namespace). The round-1 review on #1137 flagged exactly this class of problem: the .nbt jigsaws still target `nordic_structure:` pools while the pools were moved under `jaymoskingdoms`.
- Check both worlds: a dungeon generated before the fix keeps its damage, so say whether old ones need regenerating or whether only new generation is fixed.
- Also check whether vanilla trial chambers are simply generating into the same spot — that would be a structure set/spacing problem, not a piece problem.
- Fix what is actually broken, one cause at a time, and say in the PR which of the two faults was which.
