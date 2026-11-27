---
status: blocked
task: gore-1-no-arms-no-attack
branch: claude/gore-1-no-arms-no-attack
with: 
pr: 
mods: 
versions: 
started: 2026-11-27
owns:
  - modelling-check
---
# gore-1-no-arms-no-attack

**Task:** A mob or NPC that has lost both arms can't make a hand or weapon attack any more. First check whether it already can't (the coordinator didn't find a rule for it in Dismember; say what you found). If it still attacks, stop it. Biters (zombies) may still bite and anyone with legs may still kick, at low damage. One arm left: attacks as now.

**Plan:**
1. Check Dismember.java for existing rules about armless mobs not attacking
2. Report findings - if no rule exists, note that gameplay Java changes are needed
3. Since this task requires gameplay Java code changes, which is outside the hermes-modeller scope (models, textures, animations only), set task to blocked and ask Andy for clarification

**Notes:**
- Dismember.java was examined (1033 lines in src/main/java/com/jaymospowers/core/Dismember.java)
- The code handles limb severing, bleeding, staggering, and tracks one-armed players in ONE_ARMED list
- When an arm is severed, emptyHand() is called to drop the held item
- **No existing rule prevents attacks when both arms are missing** - this is the gap the task addresses
- Preventing attacks would require adding checks in the mob's AI/melee goal or in Dismember's attack routing

**Done:**
- Examined Dismember.java for armless-attack prevention rules
- Found no existing rule - the code does not stop attacks when both arms are missing
- Task requires gameplay Java changes outside hermes-modeller scope

**Not tested in game:** True (requires Java code changes not within modeller capabilities)