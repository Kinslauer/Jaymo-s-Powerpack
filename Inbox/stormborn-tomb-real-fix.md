---
status: done
for: hephaestus
from: Andy (vault thread, 2026-11-10: reopen the #1218 follow-up)
asked: 2026-11-10
claimed_by: 
pr: 1254
suggested_owns: [kingdoms/src/main/java/com/jaymoskingdoms/dungeon/StormbornTomb.java, kingdoms/src/main/resources/data/jaymoskingdoms/structure/stormborn_tomb/, kingdoms/src/main/resources/data/jaymoskingdoms/template_pool/stormborn_tomb/, kingdoms/src/main/resources/data/jaymoskingdoms/worldgen/]
writer: hermes-writer
text_pass: none
---

# Stormborn Tomb: fix the stone fill and the trial-chamber look for real

PR #1218 (dungeon-stone-and-chambers) merged before its review was addressed, so neither of Tiv's symptoms is fixed yet: whole stretches of the tomb come out as solid stone, and parts look like vanilla trial chambers.

## What's wanted

Find and fix the real causes, one at a time, and say in the PR which fault was which. Andy chose to reopen this on 2026-11-10.

## What the #1218 review found (head aad23cc), still true on master

1. The template pools #1218 added can't change generation: `StormbornTomb.java` (around lines 89-95) doesn't use pools at all. It places every piece directly with `StructurePoolElement.single("jaymoskingdoms:stormborn_tomb/<part>")` at fixed offsets (`LAYOUT`).
2. A pool's `"name"` field is ignored in 26.3 (its id is its file path), so the "slash fix" changed nothing.
3. The pieces' jigsaws already target `jaymoskingdoms:stormborn_tomb/...` pools and turn into air; no `nordic_structure` or `trial_chambers` ids remain in the .nbt files.

## Where to look

- **Stone fill:** check whether placing a piece leaves terrain where the template has air (the piece's place settings or processors, terrain adaptation, or pieces cut off by chunk bounding boxes on a ~270-block layout). Test with `/place template` underground on a test world.
- **Trial-chamber look:** `main.nbt` and `boss.nbt` contain chiseled deepslate, so the screenshot at 3293 / -16 / 5071 may be the tomb's own masonry, or a real vanilla trial chamber overlapping. Check the structure set spacing against `minecraft:trial_chambers` there (`/locate`).
- Either make `LAYOUT` use the pools, or drop the unused pool files #1218 added. Don't leave both.
- Screenshots: `Assets/dungeon-stone-fill-1.png`, `Assets/dungeon-stone-fill-2.png`. Original task: `Inbox/Done/dungeon-stone-and-chambers.md`.
- Say whether tombs generated before the fix keep their damage.

## Notes
- 2026-11-10 19:06 hermes-fixer's claim had no sign of work for 30 min: handed back as open.
- 2026-11-10 20:28 hermes-fixer's claim had no sign of work for 30 min: handed back as open.
