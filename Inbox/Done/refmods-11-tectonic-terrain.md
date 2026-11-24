---
status: done
for: any
from: coordinator
asked: 2026-11-24
thread: Build Reference Mods pick list (cloud); queued by Hermes Training
claimed_by: 
pr: 2120
suggested_owns: []
---
# Reference Mods Patch 11: Land: Tectonic trial and terrain features

Andy ticked these rows on the Reference Mods pick list (https://claude.ai/artifact/Fu1jDwywXNKSGj6mmU65fg, db picks/andy).
Full plan: Picklists/ReferenceMods-Plan.md, section "## Patch 11". Other mods' code and art are never copied
(ARR = remake ourselves). Any structure states where it generates and why (structure placement plan,
https://claude.ai/artifact/4foeFXysu1Vg9KJBADDZab). Change note per mod under changes/<mod>/<branch>.md.

## Patch 11: Land: Tectonic trial and terrain features
Owns: build.gradle pack mods; kingdoms continents.
Andy (L6) wants Tectonic tried and swapped back if it is worse. Tectonic 3.0.32 Fabric 26.3 (MIT, needs Lithostitched + Fabric API, both in the pack). It rewrites the same overworld noise our continents/shape (PR #1592) rewrites, so the trial PR adds Tectonic and switches our continent shape off (keep the climate map), and is judged on a NEW world. Reverting = revert the PR; worlds made on Tectonic keep Tectonic terrain in generated chunks.

- **L1 Rivers that run under mountains** (Tectonic, size M). A river meets a range and carries on into a cave you can boat through. We have: Continental world gen (#1592).
- **L2 Old smugglers' lanterns underground** (Tectonic, size S). A few underground rivers have old lanterns hung by smugglers running goods to the speakeasies. We have: Speakeasies.
- **L3 Stone pillars in the jungle** (Tectonic, size S). Rare jungle pillars with flat tops, nests and ruins on top.
- **L4 Volcanic islands** (Tectonic, size M). Volcanic islands in warm seas with lava, ash beaches and black sand.
- **L5 Lava tunnels at the bottom** (Tectonic, size S). Same, lit and dangerous, near deep ores.
- **L6 Ship Tectonic as it is** (Tectonic, size L). Its Fabric build in the pack. We have: Your call: it would fight our continental world gen and William Wythers' Overhauled Overworld. I'd take ideas only.
  - Andy's note: "which one is more stable? we seem to have alot of world gen issues right now, can we test this then swap back if not?"

## Notes
- 2026-11-24 22:40 project-thread-ojz7uj's claim had no sign of work for 30 min: handed back as open.
- 2026-11-24 23:54 PR #2120 is merged into master; moved to Inbox/Done.
