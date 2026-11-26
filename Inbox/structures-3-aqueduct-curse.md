---
status: blocked
for: apollo
from: coordinator
asked: 2026-11-24
thread: Hermes Training (cloud vault coordinator)
claimed_by: hermes-a
pr: 
suggested_owns:
  - kingdoms new SettlementCurse.java, Quests.java hooks
  - kingdoms lang/parts/aqueduct-curse.json
---
# Structures 3: aqueduct curse quests

**Plan:** section of "Structure placement and factions plan" (https://claude.ai/artifact/4foeFXysu1Vg9KJBADDZab). Andy's rule: every generated structure needs a
reason to be where it is, tied to the climate heatmap and the economic/political climate. Worlds stay procedural.
Build 2 placement tracks at a time; tracks 1 and 2 first.

**Andy's words:** the village near the aqueduct dungeon "makes sense as an old feature of their civilization that was taken over by the skeletons/evil magic. there should be quests that come from that dynamic, tasking the player to clear it, and lift the curse from the village."

First find which mod places the Overworld aqueduct (ours, Veradexian, is Aether only). Then: a village within 300 blocks of a taken ruin is cursed (signs per plan section 5), four quests from its board clear the ruin, and the lifted state changes the village.

## Notes
- 2026-11-25 23:01 2026-11-25 approved cleanup: Approved cleanup: #2085 explicitly excludes the curse. Identify the Overworld aqueduct and recover full section 5/four quest specifications before implementation; do not invent the missing plan.
- 2026-11-26 00:31 YOUTH delegated this to Apollo (Claude approved): draft the four aqueduct-curse quests and the cursed-village lift from Lore.md; Andy's yes before any building.
- 2026-11-26 02:38 hermes-a's last run stopped before finishing; handed back as open so it can carry on.
- 2026-11-26 07:21 Design draft complete: Overworld aqueduct structure, SettlementCurse system, 4 board quests (Stem the Flow, Clear the Pillars, Purify the Stones, Break the Anchor), and lifted village state. Awaiting Andy's approval before implementation. See Inbox/structures-3-aqueduct-curse-design.md for full specification.
