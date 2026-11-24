---
status: done
task: context keys by the crosshair (stealth kill, bodies, greeting, pickpocketing)
branch: context-controls
with: Claude (cloud)
pr: 2030
mods: powers, kingdoms
versions: 
started: 2026-11-23
owns:
  - src/client/java/com/jaymospowers/client/hud/ContextHints.java
  - kingdoms/src/main/java/com/jaymoskingdoms/ContextLines.java
---
# Context keys by the crosshair

**Task:** Andy: a low-profile floating menu with every control that applies in context (corpse lifting and looting, villager greetings, pickpocketing, stealth kills).

## Plan
- Powers draws one list (INSPECT zone, row 40, MoveHints' look); Kingdoms adds its lines through the object share `jaymos:context_hints`.

## Done
- PR #2030. Not built in the cloud sandbox (cdn.modrinth.com blocked); CI builds it. Not played.
