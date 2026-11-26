---
status: writing
for: apollo
from: coordinator
asked: 2026-11-24
thread: Hermes Training (cloud vault coordinator)
claimed_by: hermes-a
pr: 2209
suggested_owns:
  - kingdoms Camps.java, Sieges.java, SiegeTrain.java
  - kingdoms lang/parts/siege-camps.json
writer: hermes-writer
---
# Structures 1: siege camps and real sieges

**Plan:** section of "Structure placement and factions plan" (https://claude.ai/artifact/4foeFXysu1Vg9KJBADDZab). Andy's rule: every generated structure needs a
reason to be where it is, tied to the climate heatmap and the economic/political climate. Worlds stay procedural.
Build 2 placement tracks at a time; tracks 1 and 2 first.

**Andy's words:** camps "should come in groups, 3s and 4s and around a Kingdom or castle, this way it looks like a quickly thrown up raid shelter, to prepare to siege, and the siege should actually happen, supplied with the raiders stationed nearby, if they win they move in like now, and vice versa, the losers are routed or killed and their resources go to the victor."

Build: place 3-4 camps around a kingdom or castle seat; a muster period; the siege runs, drawing supplies from the camps (a camp cleared mid-siege weakens it); winners take the site, losers routed or killed, their storage goes to the victor. Today a cleared camp's land and storage are wiped (Camps.java, chief kill) instead of going to the victor.

## Notes
- 2026-11-25 23:01 2026-11-25 approved cleanup: Approved cleanup: retain grouped camps, supply-dependent sieges and victor resource transfer. Camps still clears storage on fall. #2195 fixes NPC crowding, not this task. Do not edit HollowCaptains.java, Captains.java, Rivals.java or Ladders.java while their separate PR owns them.
- 2026-11-26 02:04 hermes-a's last text pass stopped before finishing; handed back so it can carry on.
