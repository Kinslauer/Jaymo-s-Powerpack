---
status: running
task: Court wizards ward their kingdom against creepers - prevent/rewind creeper explosions, weaken creepers inside, repel some at the edge
branch: claude/court-wizard-creeper-ward
with:
pr:
mods: kingdoms
versions:
started: 2026-11-26
owns:
  - kingdoms/src/main/java/com/jaymoskingdoms/CourtWizards.java
  - kingdoms/src/main/java/com/jaymoskingdoms/Sorcery.java
  - kingdoms/src/main/java/com/jaymoskingdoms/Factions.java
  - kingdoms/src/main/java/com/jaymoskingdoms/Faction.java
  - kingdoms/src/main/java/com/jaymoskingdoms/BlastMend.java
  - kingdoms/src/main/java/com/jaymoskingdoms/Fx.java
  - kingdoms/src/main/java/com/jaymoskingdoms/mixin/equestrian/ServerExplosionMixin.java
---
# Court wizards ward their kingdom against creepers

**Task:** Court wizards should be able to cast a spell around their kingdom's plot to prevent or rewind any creeper explosions, and weaken them within the field, some feel the disturbance and are repelled by the magic, not entering in the first place.

## Plan
- [ ] Understand the court wizard system, kingdom plot bounds, and explosion handling
- [ ] Ask Andy clarifying questions about ward upkeep/break conditions and tuning
- [ ] Design the ward system: persistent area effect over kingdom claims
- [ ] Implement creeper explosion prevention/rewind using ServerExplosionMixin
- [ ] Implement creeper weakening inside the ward
- [ ] Implement creeper repulsion at ward edge
- [ ] Add visual effects using Fx (sigil/ring/wave)
- [ ] Build and test

## Notes
- Need to clarify: How is the ward kept up? (Always on while wizard lives and is home? A cost? A cooldown?)
- Need to clarify: What breaks the ward? (Wizard killed, away, siege?)
- Need to clarify: Prevention vs rewind - which for which case?
- Need to clarify: Weakening numbers (slower, smaller blast)
- Need to clarify: Repulsion share/chance and visual effect

## Done
What a player will see, and what wasn't tested.