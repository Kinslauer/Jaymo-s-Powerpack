---
status: done
task: orc-sees-stance-only-facing
branch: orc-sees-stance-only-facing
with: 
pr: 2053, 2054, 2056, 2057, 2059, 2061, 2062
mods: kingdoms, powers, kitchen
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
- Andy's follow-ups: sneaking right behind a mob goes unnoticed (#2053), "Hurt: <cause>" line (#2054), downed die on
  the 3rd weaker blow (#2056), thermometer flames and frost (#2057). All built on CI, none played.
- "(un)fair fight" rest: finite blood on hits (#2059), near blast knocks out (#2061), adrenaline once incl. going down (#2062); downed mobs on 3 blows too (#2056).

## Notes
- 2026-11-23 20:54 PR #2053 is merged into master; moved to Agents/Done.
