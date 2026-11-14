---
status: in-review
task: Character creator for players who skip the life story, with presets
branch: claude/project-thread-byu4io
with: Andy
pr: 1544
mods: kingdoms
versions: 
started: 2026-11-14
owns:
  - kingdoms/src/client/java/com/jaymoskingdoms/client/LifeStoryScreen.java
  - kingdoms/src/client/java/com/jaymoskingdoms/client/CharacterPresets.java
  - kingdoms/src/main/resources/assets/jaymoskingdoms/lang/parts/character-creator.json
---
# Character creator presets

**Task:** Andy: "people who skip the story go to a new character creator that they can choose everything explicitly except their power (only affinity) and this can also be saved as a preset like the costumes work now."

## Notes
- The creator is one more page of `LifeStoryScreen` (`Draft.explicit`), sending the story's own `Live` packet: no server change.
- Affinity row = the "Then one day..." answer, never naming the power (Claude's reading of "only affinity").
- Presets are a client file, `config/jaymoskingdoms-characters.json`, kept by key and name. MCA's face, body and name are not in them.

## Done
Skipping the story opens a one-page creator with a row per choice, Randomize, and Save/Load/Delete preset. Built with Gradle; not played.
