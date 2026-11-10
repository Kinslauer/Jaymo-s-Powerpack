---
status: claimed
for: hermes
from: tiv (Discord, Tasks thread, 2026-11-08)
asked: 2026-11-08
thread: 1569170499680870560
claimed_by: hermes-fixer
pr: 1218
suggested_owns: [kingdoms/src/main/resources/data/jaymoskingdoms/structure/, kingdoms/src/main/resources/data/jaymoskingdoms/template_pool/, kingdoms/src/main/resources/data/jaymoskingdoms/worldgen/]
text_pass: 
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
- 2026-11-09 23:38 Andy skipped the text pass (2026-11-09); ready for review.
- 2026-11-09 23:42 Review (head aad23cc): changes needed. 1. The new template pools can't change generation: StormbornTomb.java:89-95 doesn't use pools at all; it places each piece directly with StructurePoolElement.single("jaymoskingdoms:stormborn_tomb/<part>") at fixed offsets (LAYOUT). So both symptoms are still there. 2. The pools' "name" field is ignored in 26.3 (a pool's id is its file path), so stormborn_tomb/stormborn_tomb_main.json is jaymoskingdoms:stormborn_tomb/stormborn_tomb_main, not stormborn_tomb/main; the "slash fix" changes nothing either. 3. The pieces' jigsaws already target jaymoskingdoms:stormborn_tomb/... pools and turn into air (final_state minecraft:air); no nordic_structure or trial_chambers ids remain in the .nbt files. 4. Find the real causes: (a) stone fill: check whether placing a piece leaves terrain where the template has air (the piece's place settings/processors, or pieces cut off by chunk bounding boxes for a 270-block layout), using /place template on a test world underground; (b) trial-chamber look: main.nbt and boss.nbt contain chiseled_deepslate, so the screenshot at 3293/-16/5071 may be the tomb's own masonry, or a vanilla trial chamber overlapping (check its structure set spacing against minecraft:trial_chambers there with /locate). Drop the unused pool files unless something uses them.
