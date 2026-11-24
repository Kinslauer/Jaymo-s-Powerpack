---
status: claimed
for: claude-cloud
from: coordinator
asked: 2026-11-24
thread: cmsg_017LSt6rWz6CzxSGvzDd41XWRihgeu7KNw14feoHe7EEoR
claimed_by: claude-cloud
pr: 2094
suggested_owns: []
---
# Reference Mods Patch 14: Sky, light and air

Andy ticked these rows on the Reference Mods pick list (https://claude.ai/artifact/Fu1jDwywXNKSGj6mmU65fg, db picks/andy).
Full plan: Picklists/ReferenceMods-Plan.md, section "## Patch 14". Other mods' code and art are never copied
(ARR = remake ourselves). Any structure states where it generates and why (structure placement plan,
https://claude.ai/artifact/4foeFXysu1Vg9KJBADDZab). Change note per mod under changes/<mod>/<branch>.md.

## Patch 14: Sky, light and air
Owns: client particles/effects.

- **X2 Leaves drift on water** (ImmersiveVFX, size S). Fallen leaves float on ponds under trees and pile at the edges in autumn. We have: Seasons.
- **X3 Fire with smoke, embers and scorch** (ImmersiveVFX, size S). Fires throw embers and smoke lit from below, and leave scorch marks on what they touch. We have: Arson, the forest fire pack.
- **X4 Fog in the hollows** (ImmersiveVFX, size S). Morning mist lying in valleys and over rivers.
- **X5 Northern lights** (ImmersiveVFX, size S). Over the frost lands on clear nights.
- **X6 Falling stars** (ImmersiveVFX, size S). Shooting stars, and now and then a meteor shower; a rare one lands and leaves sky-iron. We have: Inbox: astronomy-scholar-journal.
- **X7 Fireworks over a far city** (ImmersiveVFX, size S). When a realm celebrates (a wedding, a tourney, a victory), fireworks over its city. We have: Tourneys, crowns.
- **X8 Sand on the wind** (ImmersiveVFX, size S). Drifting sand and sandstorms in harsh deserts. We have: Harsh lands.
- **X9 Lava crust** (ImmersiveVFX, size S). Lava edges cool to a cracked glowing crust.
- **X10 Light in its block's colour** (ImmersiveVFX, size M). Soul fire lights blue, redstone red, lanterns warm.
- **X11 Blocks settle into place** (ImmersiveVFX, size S). A placed block settles in with a tiny grow.

## Notes
- 2026-11-24 18:00 2026-11-24 Andy: "finish here": cloud Claude finishes this in thread cmsg_017LSt6rWz6CzxSGvzDd41XWRihgeu7KNw14feoHe7EEoR; bots leave it.
- 2026-11-24 19:24 PR #2094: X2, X3, X6, X7, X9, X10, X11 built (X4, X5, X8 were already in); compiles, not played.
- 2026-11-24 19:29 X11 left out of #2094: the pack's A Good Place (MIT) already grows placed blocks into place.
