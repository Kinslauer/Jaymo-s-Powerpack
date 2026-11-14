---
status: running
task: Clean kills and skinning in strokes
branch: claude/project-thread-tjqmp0
with: cloud thread "Improve butchering from Carcass Craft"
pr: 
mods: kitchen
versions: 
started: 2026-11-13
owns:
  - kitchen/src/main/java/com/jaymoskitchen/corpse/Hanging.java
  - kitchen/src/main/java/com/jaymoskitchen/corpse/CleanKill.java
---
# Clean kills and skinning in strokes

**Task:** Andy picked two ideas from the "Carcass Craft cleanroom" zip (nothing ported from it): how an animal died changes what its carcass gives, and skinning takes several knife strokes.

## Plan
- CleanKill: read the killing blow when the body is made, trim its loot (mauled: torn hide; burnt: no hide; battered: bruised meat).
- Hanging.skin: several strokes, more with a poor knife.

## Notes
- Small edits also in Corpses.spawnCorpse and CorpseEntity (two saved fields).

## Done
