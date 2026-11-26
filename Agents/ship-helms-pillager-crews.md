---
status: done
task: Helms on every ship at generation; the long ship crewed by pillagers with working cannons
branch: claude/project-thread-hbvaa4
with: cloud thread "Add helms and crews to ships"
pr: 
mods: kingdoms
versions: 
started: 2026-11-26
owns:
  - kingdoms/src/main/java/com/jaymoskingdoms/SeaSites.java
  - kingdoms/src/main/java/com/jaymoskingdoms/DeckGuns.java
---
# Ship helms and pillager long ships

**Task:** Andy spawned on Moog's long ship, which had no helm; he wants every ship to have one, and the long ship manned by pillagers, cannons and all (else pirates).

## Notes
- Moog's long ship templates spawn their own crew through jigsaw mob pools (mos:mobs/raider = pillager/vindicator/evoker, mos:mobs/captain = vindicator "Ship Captain" with a Jolly Roger banner). That was the stray pillager.
- The helm used to wait for IllagerSites.settle (no player within ~73, tier open). Now SeaSites.rig runs on chunk load once all of a boat's chunks are made.

## Done
See changes/kingdoms/claude-project-thread-hbvaa4.md. Built; not played, no dev server run.
