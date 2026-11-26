---
status: open
for: apollo
from: coordinator
asked: 2026-11-24
thread: Hermes Training (cloud vault coordinator)
claimed_by: 
pr: 
suggested_owns:
  - kingdoms FactionType.java, Relation.java
  - kingdoms lang/parts/factions.json
---
# Structures 5: faction table and relations

**Plan:** section of "Structure placement and factions plan" (https://claude.ai/artifact/4foeFXysu1Vg9KJBADDZab). Andy's rule: every generated structure needs a
reason to be where it is, tied to the climate heatmap and the economic/political climate. Worlds stay procedural.
Build 2 placement tracks at a time; tracks 1 and 2 first.

**Andy's words:** factions are Earthlings and Hearthlanders and the like (Humans), Nonhumans, Bandits, Pirates, Greenskins, Pillagers, Demons, Avatars. "the only sworn enemies are Greenskins/Pillagers, they will never get along."

Build the table (plan section 2) so placement and sieges can read it. Greenskins vs Pillagers can never ally or make peace. Open question: are Barbarians a ninth faction?

## Notes
- 2026-11-25 23:01 2026-11-25 approved cleanup: Question sent to Andy: barbarians separate, Humans, or per tribe? Account for later Pallids-as-faction ruling (Decisions.md). FactionType currently describes settlement form; preserve its saved meaning rather than conflating it with peoples/allegiances.
- 2026-11-26 00:35 Unblocked 2026-11-26: Andy answered both open questions — barbarians are classed by each tribe's own people (not a ninth faction), and the Pallid ruling stands (Pallids are the intelligent Hollow, lead armies of skeletons/zombies as their own faction, Decisions.md 2026-11-25). Build the faction table + relations (FactionType, Relation); Greenskins vs Pillagers never ally; add Pallids as a faction. YOUTH delegated this to Apollo (Claude approved).
