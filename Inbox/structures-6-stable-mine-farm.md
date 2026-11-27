---
status: in-review
for: bacchus
from: coordinator
asked: 2026-11-24
thread: Hermes Training (cloud vault coordinator)
claimed_by: 
pr: 2217
suggested_owns:
  - kingdoms data/jaymoskingdoms/structure (new nbt), worldgen pools
  - kingdoms Stables.java, Mines.java hooks
writer: hermes-writer
text_pass: 
---
# Structures 6: new stable, mine and farm structures

**Plan:** section of "Structure placement and factions plan" (https://claude.ai/artifact/4foeFXysu1Vg9KJBADDZab). Andy's rule: every generated structure needs a
reason to be where it is, tied to the climate heatmap and the economic/political climate. Worlds stay procedural.
Build 2 placement tracks at a time; tracks 1 and 2 first.

**Andy's words:** "The second picture makes for another great stable. A good mine." Screenshots: a chimney house with a horse (stable) and a fenced open quarry (mine); the farm screenshot from the same day.

Build the three structures (original builds, not copies) and place them by track 2's rules. Track 2 is merged as #2085; use PlacementReasons. The three requested original builds remain. Recover the reference pictures before finalizing their designs.

## Notes
- 2026-11-25 23:20 YOUTH delegated this to bacchus (Claude approved): self-contained worldgen structures; track 2 is done (PR #2085)
- 2026-11-26 04:05 Blocked: PR #2217 CI 'Build Powers' check is red due to Modrinth HTTP 429 rate limiting while downloading dependencies (shared-advancements, betterping, item-highlighter) plus a pre-existing libs/jaymos-kitchen jar version warning. Unrelated to my data-only Kingdoms change (stable/mine/farm nbt + worldgen + PlacementReasons tag entries + change note). Local sh ./gradlew build passes and tools/pr_checks.py passes. PR is pushed claude/structures-6-stable-mine-farm -> master, ready to hand in once CI checks clear; will retry vault.py --set status=in-review pr=2217.
- 2026-11-26 04:09 hermes-b's last text pass stopped before finishing; handed back so it can carry on.
- 2026-11-26 04:28 Text pass: rewrote changes/kingdoms/structures-6-stable-mine-farm.md — dropped internal rule names (PlacementReasons, stables_by_towns, mines_by_buyers, farms_by_homes); it now says three new small worldgen structures generate in the wild: a horse stable by towns, a fenced open quarry near buyers, farmland beside homes, each in the biomes it suits. Kept "Built; not played." No lang, dialogue or hint strings were touched by this PR. Kingdoms sh ./gradlew build passes; tools/pr_checks.py passes; commit a284501e44 pushed to PR branch claude/structures-6-stable-mine-farm (PR #2217).
- 2026-11-26 04:43 Text pass: reviewed PR #2217 structures-6-stable-mine-farm. Player-visible strings: only the change note (changes/kingdoms/structures-6-stable-mine-farm.md) — already player-facing: three new small worldgen structures (stable by towns, fenced quarry by buyers, farmland beside homes), each in the biomes it suits, plus 'Built; not played.' per change-note house style. No lang parts, dialogue, hints, screens or chat lines were added; the worldgen json/nbt files and tools/small_designs/stable_mine_farm.py carry no player text. No rewrite needed.
- 2026-11-26 05:15 hermes-writer's claim had no sign of work for 30 min: handed back as in-review.
- 2026-11-26 06:11 Text pass: reviewed PR #2217 (claude/structures-6-stable-mine-farm). Player-visible text: only changes/kingdoms/structures-6-stable-mine-farm.md — matches house style (# title line, player-facing description of the stable/mine/farm structures, 'Built; not played.' closer, no version number), no rewrite needed. No lang parts, dialogue, hints, screens or chat lines were added; the worldgen JSONs, structure tags and NBTs carry no player-visible text (the stable, mine and farm designs use doors, lanterns, hay, cauldrons and fences only — no signs or books). No files were changed.
- 2026-11-27 02:57 Review (head a284501), cloud coordinator for Andy: changes needed (checked by decoding the .nbt files). 1) tools/small_designs/stable_mine_farm.py farm(): crops are put at y=1, the same cell as the farmland, so 21 crops replace their farmland and sit on dirt (they pop off on the first update); put crops at y=2 and make the grid 4 high. 2) farm(): Grid(15, 3, 15) is 3 high, so the shed roof at y=3 is silently dropped; the shed has no roof. 3) stable(): the roof skips (3,6,3) but the chimney starts at y=7, leaving a hole in the roof and a floating brick column; run the bricks from y=6 (or from the floor up through the inside). 4) stable(): nothing inside the walls is cleared (0 air blocks at y3-5), and unlisted cells keep the world's blocks, so on any slope the stable is full of dirt and grass; clear the inside. Same for the farm shed. 5) Andy's words were 'a chimney house with a horse': no horse spawns. Add entity support to tools/small_structures.py template() (entities list) and put a horse in a stall, or a processor that spawns one. 6) Optional: a ladder into the quarry so it can be walked into. Placement tags (stables_by_towns, mines_by_buyers, farms_by_homes) are right. CI is green.
- 2026-11-26 23:54 hermes-b's claim had no sign of work for 30 min: handed back as changes.
