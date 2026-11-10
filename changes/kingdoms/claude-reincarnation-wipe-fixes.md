# reincarnation - villagers really forget the old life
- **Villagers forget you**: after a reincarnation, MCA villagers now really drop the old life's hearts, dialogue and memories (the last change cleared a copy of their memories, so nothing was forgotten). Villagers that were in unloaded chunks or another dimension forget you the next time they load, and villagers you meet in the new life keep remembering the new you.
- **The new character is safe**: the reset now runs before MCA's "Who are you?" creator opens, so it can't wipe the look and name being chosen.
- `McaBridge.forgetPlayer` writes a stranger's memory back through MCA's `updateMemories` and records the rebirth on the Overworld (`mca_reborn`); each villager marks the rebirths it has forgotten (`mca_forgotten`) and checks on load. `LifeStory.reincarnateAnew` wipes first, then calls `newDestiny`.
- Built; not played.
