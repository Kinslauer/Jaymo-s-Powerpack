---
status: working
task: One combined character menu on H (idea from Kageborne's Shinobi Menu): Character, Powers, Skills, Journal & Quests, Faction, Map & Waypoints, Party
branch: claude/lucid-cohen-0470d8
with: Claude Code session (side session of the coordinator)
pr:
mods: Powers, Kingdoms
versions: Powers 0.21.60, Kingdoms 0.7.649
started: 2026-11-07
owns:
  - src/client/java/com/jaymospowers/client/menu/ (new: MenuTabs, CharacterScreen, MapScreen)
  - src/client/java/com/jaymospowers/client/PowerScreen.java (its tabs moved to the top edge)
  - kingdoms/src/client/java/com/jaymoskingdoms/client/{MenuCompat,FactionScreen,PartyScreen}.java (new)
  - kingdoms/src/main/java/com/jaymoskingdoms/MenuNet.java (new)
  - lang/parts/menu.json in both mods
  - small hooks: Journal (journal for everyone), LifeStory (kit), Careers.title public, SkillScreen/JournalScreen corner(), PartyClient getters
---
# Character menu

**Task:** one screen to manage your character (tabs), reusing the existing screens as tabs. Kingdoms tabs only when it's loaded.

## Notes
- Mockup: https://claude.ai/artifact/Hht2DA6QNDspyQq5bMjZLx (Andy: "Yes, build it").
- The tabs are drawn over each page by Fabric screen events, so the existing screens stay as they are.
