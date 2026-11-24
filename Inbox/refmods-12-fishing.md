---
status: claimed
for: claude-cloud
from: coordinator
asked: 2026-11-24
thread: cmsg_017LSt6rWz6CzxSGvzDd41XWRihgeu7KNw14feoHe7EEoR
claimed_by: claude-cloud
pr: 2092
suggested_owns: []
---
# Reference Mods Patch 12: Fishing extras

Andy ticked these rows on the Reference Mods pick list (https://claude.ai/artifact/Fu1jDwywXNKSGj6mmU65fg, db picks/andy).
Full plan: Picklists/ReferenceMods-Plan.md, section "## Patch 12". Other mods' code and art are never copied
(ARR = remake ourselves). Any structure states where it generates and why (structure placement plan,
https://claude.ai/artifact/4foeFXysu1Vg9KJBADDZab). Change note per mod under changes/<mod>/<branch>.md.

## Patch 12: Fishing extras
Owns: powers/kitchen fishing (docs/Plan-fishing.md).

- **F1 Don't stand on the hook** (Better Fishing, size S). Stand close or splash and the fish stay away; a soft warning ripple shows you're too close. We have: Fishing fight (#1114).
- **F2 A solid hook or a light one** (Better Fishing, size S). The strike window grades the hook: strike fast and the hook holds through runs; late and it slips if tension drops. We have: Strike window.
- **F3 The leap** (Better Fishing, size S). Big fish jump clear of the water mid-fight; keep reeling and the line snaps. We have: Runs left and right.
- **F4 Turn against the run** (Better Fishing, size S). Pull your rod (look) the opposite way to a run to tire it faster.
- **F5 An angler's log of bests** (Better Fishing, size S). A log book that keeps your heaviest of each kind and how cleanly you landed it, and a trophy plaque to hang the record fish. We have: Weights per catch.
- **F6 Line that hangs like rope** (Enchanted Fishing Line, size M). Slack line sags into the water and drapes over branches and rails; under tension it straightens. Snagged on a branch, it can break. We have: Line origin fix (first click).

## Notes
- 2026-11-24 18:00 2026-11-24 Andy: "finish here": cloud Claude finishes this in thread cmsg_017LSt6rWz6CzxSGvzDd41XWRihgeu7KNw14feoHe7EEoR; bots leave it.
- 2026-11-24 19:01 2026-11-24 PR #2092: F1-F6 built (compiles; not played).
