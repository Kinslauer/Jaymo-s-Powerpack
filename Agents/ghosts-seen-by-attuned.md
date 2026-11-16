---
status: running
task: Only the attuned and Death Sorcerers see ki ghosts; ghosts get wispy tails
branch: claude/project-thread-0gcpku
with: 
pr: 1633
mods: powers, kingdoms
versions: 
started: 2026-11-16
owns:
  - kingdoms/src/main/java/com/jaymoskingdoms/GraveGhost.java
  - kingdoms/src/main/java/com/jaymoskingdoms/VoiceMasks.java
  - kingdoms/src/main/java/com/jaymoskingdoms/VoiceRange.java
  - kingdoms/src/main/resources/assets/jaymoskingdoms/bbmodels/grave_ghost.json
  - kingdoms/src/client/java/com/jaymoskingdoms/client/beasts/GraveGhostTail.java
  - src/main/java/com/jaymospowers/core/wounds/PlayerGhost.java
  - src/main/java/com/jaymospowers/core/wounds/OutOfBody.java
  - src/client/java/com/jaymospowers/client/wounds/PlayerGhostRenderer.java
---
# Ghosts seen by the attuned

**Task:** Andy: "only the attuned and death magicians should ever see the ki ghosts, also the ghosts should have wispy tails like wisp".

## Plan
- Mana.seesGhosts (attuned or Death Sorcerer); GraveGhost and PlayerGhost broadcastToPlayer; motes per viewer; ghost voice only to seers.
- Legs replaced by an 8-link tapering tail on both ghost models.

## Notes
- Lore.md "(new) Who sees the dead" awaits Andy.

## Done
PR #1633: grave + player ghosts sent only to attuned / Death Sorcerers, ghost voices too; tails on both. Compiled, not played.
