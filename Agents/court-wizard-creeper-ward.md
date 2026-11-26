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
# Court Wizard Creeper Ward

**Task:** Court wizards should be able to cast a spell around their kingdom's plot to prevent or rewind any creeper explosions, and weaken them within the field. Some feel the disturbance and are repelled by the magic, not entering in the first place.

## Plan
- [x] Understand the court wizard system, kingdom plot bounds, and explosion handling
- [x] Andy's picks received 2026-11-26 09:56: upkeep always on while wizard lives and is home, no cost. Breaks on wizard death, absence from plot, OR any siege. Prevention scales with wizard strength, all blasts that get through are rewound. Weakened: 30% slower, blast half size. Repelled: ~half at edge. Reach: whole kingdom plot.
- [ ] Design the ward system: persistent area effect over kingdom claims
- [ ] Implement creeper explosion prevention/rewind using ServerExplosionMixin
- [ ] Implement creeper weakening inside the ward
- [ ] Implement creeper repulsion at ward edge
- [ ] Add visual effects using Fx (sigil/ring/wave)
- [ ] Build and test

## Notes
- Andy's picks 2026-11-26 09:56: upkeep: always on while the court wizard lives and is home, no cost (suggested, kept). Breaks: the wizard's death, absence from the plot, OR any siege. Stop vs undo: fizzle while the wizard is fresh, rewind any that go off; Andy's note: 'stronger wizards prevent more blasts, but they all rewind within the ward. players have to learn it too' (so prevention chance scales with wizard strength, every blast that gets through is rewound, and players can learn the ward spell). Weakened inside: 30% slower, blast half size (suggested, kept). Repelled at the edge: about half (Andy's earlier Kanban-cleanup answer, Decisions 2026-11-26; the page default was left untouched). Reach: the whole kingdom plot (suggested, kept). Ready to build.
- Ward visual effects through Kingdoms Fx (sigil/ring/wave), never recoloured redstone dust. Keep it cheap on the Deck server: creepers checked only near warded plots, not every creeper every tick.
- Rewinding a blast: record the blocks an explosion inside the ward would destroy (ExplosionEvent / the detonate hook in 26.3; check the sources) and restore them over a few seconds. Never restore into a block a player has since placed.

## Done
What a player will see, and what wasn't tested.