---
status: open
for: any
from: coordinator
asked: 2026-11-24
thread: Build Reference Mods pick list (cloud); queued by Hermes Training
claimed_by: 
pr: 
suggested_owns: []
---
# Reference Mods Patch 13: Body animation and station displays

Andy ticked these rows on the Reference Mods pick list (https://claude.ai/artifact/Fu1jDwywXNKSGj6mmU65fg, db picks/andy).
Full plan: Picklists/ReferenceMods-Plan.md, section "## Patch 13". Other mods' code and art are never copied
(ARR = remake ourselves). Any structure states where it generates and why (structure placement plan,
https://claude.ai/artifact/4foeFXysu1Vg9KJBADDZab). Change note per mod under changes/<mod>/<branch>.md.

## Patch 13: Body animation and station displays
Owns: powers client animation; C3/C4 block renderers.

- **C3 See what's cooking in the furnace** (Hands-On Vanilla, size S). The ore or food inside shows through the furnace mouth, glowing as it heats.
- **C4 Real books on lecterns and tables** (Hands-On Vanilla, size S). A lectern book opens big in the world as you read it.
- **B1 Feet turn before the body** (Punchy Moves, size M). Standing, look far left and your feet shuffle round under you. We have: Prone, sitting and crouched bodies keep facing (#1950).
- **B2 Lean into turns** (Punchy Moves, size S). Sprinting round a corner leans you in; same on horseback.
- **B3 Wading** (Punchy Moves, size S). Waist-deep water slows you and your arms come up.
- **B4 Balance on fences and beams** (Punchy Moves, size S). Arms out on fence tops and narrow beams. We have: Parkour moves.
- **B5 Hand over hand on ladders** (Punchy Moves, size S). Hands and feet on the rungs as you climb. We have: Climbing for everyone.
- **B6 Faces that move** (Punchy Moves, size M). Eyes blink, faces wince when hit and grin during emotes.
  - Andy's note: "mostly for MCA"
- **B7 The shield arm takes the hit** (Punchy Moves, size S). A blocked blow jolts the shield arm back. We have: Shield bangs.
- **B8 Hands on the reins** (Punchy Moves, size S). Riders sway with the gait and hold the reins.

## Notes
- 2026-11-24 17:21 project-thread-ojz7uj's claim had no sign of work for 30 min: handed back as open.
