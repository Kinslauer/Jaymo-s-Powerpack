---
status: in-review
task: Clean kills and skinning in strokes
branch: claude/project-thread-tjqmp0
with: cloud thread "Improve butchering from Carcass Craft"
pr: 1486
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
- PR #1486 (Kitchen). Mauled: half the skins lost; burnt: no hide; battered (fall, blast, fists, bruising_weapons tag): a third of the meat lost. First tool click on such a body only says what happened. Skin comes off in 3 pulls (small 2, big 5, flint/bone knife +2).
- Gradle compile clean; not played.
