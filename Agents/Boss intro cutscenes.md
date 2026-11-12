---
status: in-review
task: 
branch: claude/project-thread-b8b4vz
with: Claude (cloud thread)
pr: 1345
mods: kingdoms
versions: 
started: 2026-11-12
owns:
  - kingdoms/src/main/java/com/jaymoskingdoms/films/BossIntros.java
  - kingdoms/src/client/java/com/jaymoskingdoms/client/films/BossIntroClient.java
  - kingdoms/src/main/resources/data/jaymoskingdoms/tags/entity_type/boss_intro.json
---
# Boss intro cutscenes

**Task:** Andy: bosses get an intro cutscene on a player's first encounter (Ignivorus first, passive great mobs too).

## Plan
- Server finds an unmet great creature in sight, offers the film; client plays three shots through the shared camera mixin.

## Notes
- Small edits in BossBars (met), HandshakeCameraMixin, ClashInputMixin, both initialisers.

## Done
Compiled clean; not played.
