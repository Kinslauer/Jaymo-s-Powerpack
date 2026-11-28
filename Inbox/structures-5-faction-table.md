---
status: cancelled
for: apollo
from: coordinator
asked: 2026-11-24
thread: Hermes Training (cloud vault coordinator)
claimed_by: hermes-a
pr: 2216
suggested_owns:
  - kingdoms FactionType.java, Relation.java
  - kingdoms lang/parts/factions.json
writer: hermes-writer
text_pass: done
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
- 2026-11-26 03:12 Text pass: rewrote changes/kingdoms/structures-5-faction-table.md body to player-facing wording (structures belong to factions; factions feel fixed ways about each other; Greenskins vs Pillagers never ally). Left factions.json faction names untouched; FactionAllegiance.java is code, not touched.
- 2026-11-27 02:56 Review (head 00325eb), cloud coordinator for Andy: changes needed. FactionAllegiance.java is added but nothing reads it: no Faction or structure is given an allegiance, placement and sieges never ask it, and neverAllies() (line 59) is never called, so Greenskins and Pillagers can still ally or make peace through Relations. 1) Store an allegiance on each faction (derive it from what the faction is: orc camps and goblin villages Greenskin, illager camps Pillager, pirate coves Pirate, bandit camps Bandit, kingdoms Human or Nonhuman by their people, barbarians by each tribe's own people per Andy's 11-26 answer, Hollow/Pallid armies Pallid), saved, with FactionType keeping its meaning. 2) Enforce the sworn-enemy rule where relations change (Relations.set and every peace/alliance path): a Greenskin-Pillager pair can never go above war. 3) Seed starting relations from baseRelation when factions are founded. 4) The change note claims structures already belong to factions; make it true or reword it. CI is green.
- 2026-11-27 02:34 Text pass: reworded changes/kingdoms/structures-5-faction-table.md to player-facing wording (factions belong to a people; starting stance set by those peoples; Greenskins and Pillagers never ally or make peace). factions.json faction names left untouched — standalone names from Andy's faction list, no code reads the keys yet.

- 2026-11-28: cancelled (Andy: "Cancel all youth/hermes tasks"); was in-review.
