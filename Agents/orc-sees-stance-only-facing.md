---
status: in-review
task: orc-sees-stance-only-facing
branch: orc-sees-stance-only-facing
with: 
pr: 
mods: kingdoms
versions: 
started: 2026-11-23
owns:
  - kingdoms/src/main/java/com/jaymoskingdoms/Menace.java
  - changes/kingdoms/orc-sees-stance-only-facing.md
---
# Andy's three clips: what hit him, how the orc saw him

**Task:** Watch "(un)fair fight", "stop hitting me" and "back turned to blade" and answer Andy's questions.

## Done
- Back turned to blade: `Menace` warned on line of sight with no view cone. Fixed (cone or within 2 blocks).
- Stop hitting me: no attacker; server logged the downing as environment. About 1.5 HP every 2 s while in
  heatstroke (Weakness, red edges, "Heatstroke!" line). Heatstroke's code hurts 1 HP every 4 s, so not confirmed.
- (Un)fair fight: log says downed by Sir Edmund Rot-Knight (zombie), final. Andy's four points asked about, not fixed.
