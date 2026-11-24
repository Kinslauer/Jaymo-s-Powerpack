---
status: open
for: any
from: coordinator
asked: 2026-11-24
thread: Hermes Training (cloud vault coordinator)
claimed_by: 
pr: 
suggested_owns:
  - kingdoms new worldgen/PlacementReasons.java
  - kingdoms lang/parts/placement-reasons.json
---
# Structures 2: placement reasons (farms, mines, stables, towers, bandit camps)

**Plan:** section of "Structure placement and factions plan" (https://claude.ai/artifact/4foeFXysu1Vg9KJBADDZab). Andy's rule: every generated structure needs a
reason to be where it is, tied to the climate heatmap and the economic/political climate. Worlds stay procedural.
Build 2 placement tracks at a time; tracks 1 and 2 first.

**Andy's words:** farms "should spawn near villages or the single homes, they are farms and provide food somewhere, remember the generation needs a reason."

Build the placement-rule layer from the plan's section 3 tables: farms near villages or single homes; mines on ore and near a buyer; stables on roads near towns; watchtowers on realm borders; bandit camps by trade roads (no bandit camp structure exists yet); ruins tied to a nearby village that inherits them. Use the HarshLands heatmap and realm wealth/relations as inputs.
