---
status: in-review
for: bacchus
from: coordinator
asked: 2026-11-24
thread: Hermes Training (cloud vault coordinator)
claimed_by: hermes-b
pr: 2217
suggested_owns:
  - kingdoms data/jaymoskingdoms/structure (new nbt), worldgen pools
  - kingdoms Stables.java, Mines.java hooks
writer: hermes-writer
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
