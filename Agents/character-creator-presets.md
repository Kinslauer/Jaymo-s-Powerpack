---
status: done
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
  - kingdoms/src/client/java/com/jaymoskingdoms/client/CreatorTab.java
  - kingdoms/src/client/java/com/jaymoskingdoms/client/mixin/mca/McaCreatorTabMixin.java
  - kingdoms/src/main/resources/assets/jaymoskingdoms/lang/parts/character-creator.json
---
# Character creator presets

**Task:** Andy: "people who skip the story go to a new character creator that they can choose everything explicitly except their power (only affinity) and this can also be saved as a preset like the costumes work now."

## Notes
- Andy (2026-11-14), after the first version: "if they hit skip the story, their start is randomized and ALL creation options are moved to a tab within the MCA creator".
- Skip closes the story screen; MCA's creator opens with the story untold (`McaDestinyWaitMixin`) and a Life tab (`CreatorTab`, `McaCreatorTabMixin`). Sealed once at the creator's finish (`LifeStory.Skipped`); the server then takes the "let fate decide" road (`LifeStory.SKIPPED`).
- Affinity row = the "Then one day..." answer, never naming the power (Claude's reading of "only affinity").
- Presets are a client file, `config/jaymoskingdoms-characters.json`. MCA's face, body and name are not in them.
- Fallback: with no MCA creator to come, the same rows are a page of the story screen.

## Done
Skip the story, then make the character on the Life tab of MCA's creator; start is fate's. Built with Gradle; not played, tab layout not seen in game.
