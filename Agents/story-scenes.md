---
status: done
task: Story cutscenes and mission flow like Endless Lantern Corps; spoken Warlock spells
branch: claude/project-thread-fqmg1o
with: Claude (cloud thread "Build story cutscenes like Endless Lantern Corps")
pr: 2204
mods: kingdoms
versions: 
started: 2026-11-26
owns:
  - kingdoms/src/main/java/com/jaymoskingdoms/StoryScenes.java
  - kingdoms/src/main/java/com/jaymoskingdoms/MidnightBargain.java
  - kingdoms/src/client/java/com/jaymoskingdoms/client/scenes/
  - kingdoms/src/main/resources/data/jaymoskingdoms/story_scenes/
  - kingdoms/src/main/resources/assets/jaymoskingdoms/lang/parts/story-scenes.json
---
# Story scenes (Endless Lantern Corps study)

**Task:** Andy asked to study Endless Lantern Corps' cutscenes and ring acquisition and build something similar for our story missions, plus voice recognition for Warlock spellcasting.

## Plan
- Story scene engine (data-driven scenes: shots, replies, a spoken yes/no, a kneel-and-speak rite, gifts).
- Windows speech (System.Speech via PowerShell), off until switched on.
- First mission: the Warlock's midnight bargain at a shrine; spoken spells after it.

## Notes
- Endless Lantern Corps: ChubOnYt, Forge 1.20.1, All Rights Reserved. Ideas only. Its flow: crashed ship structure, dying ring-bearer with reply dialogue in framed shots, items received, "Are you afraid?" question (say/type), oath spoken at the lantern (Windows speech, hold right-click fallback), willpower tree.

## Done
Merged as PR #2204 (2026-11-26). Not played. Lore paragraph "The midnight bargain" awaits Andy.
