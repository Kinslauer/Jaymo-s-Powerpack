---
status: done
task: Barbarian camps sound the alarm with a horn
branch: claude/project-thread-vsat21
with: cloud thread "Make barbarian camps sound the alarm"
pr: 1846
mods: kingdoms
versions: 
started: 2026-11-19
owns:
  - kingdoms/src/main/java/com/jaymoskingdoms/CampAlarm.java
  - kingdoms/src/main/resources/assets/jaymoskingdoms/lang/parts/camp-alarm.json
---
# Camp alarm horn

**Task:** Andy (2026-11-19): a raided barbarian camp ignored it; anyone attacked in the area runs back and blows a horn, then the camp patrols and defends until safe.

## Done
CampAlarm (barbarian, goblin, orc camps): runner to the fire, goat horn call, fighters defend/patrol, others keep to the fire, stand down after 30 s quiet. Lore paragraph "The camp horn" (new) awaits Andy. Compiled with Gradle; not played.

## Notes
- 2026-11-19 16:15 PR #1846 is merged into master; moved to Agents/Done.
