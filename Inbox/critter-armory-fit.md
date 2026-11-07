---
status: open
for: any
from: coordinator
asked: 2026-11-07
thread: Claude project thread "Fix new armour mapping on mobs" (never started, thread ran out of usage)
claimed_by: 
pr: 
suggested_owns:
  - 
---
# Critter Armory armour sits wrong on animals

**Andy's words (2026-11-07):** "the new armor isnt mapped properly to the mobs".

**What's wanted:** Critter Armory's animal armour (added unchanged in Powers 0.20.85) fits pigs, sheep and horses the
way it does in plain Minecraft. Andy's two clips (in the "Fewer water mobs" request) show it misplaced on all three.

**Where to start:** find out first whether our own animal models or render changes (Kitchen corpses/ragdolls, any
remodelled pig/sheep/horse, Fresh-Animations-style packs in the pack) move the body parts the armour layer copies.
Critter Armory is ARR unless its licence says otherwise, so fix it from our side (compat class, or our own models'
part names/poses), not by copying its code.

## Notes
- Cause not known yet.
