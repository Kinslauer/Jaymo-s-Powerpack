---
status: running
task: journal-refresh
branch: claude/journal-refresh
with:
pr:
mods: kingdoms
versions:
started: 2026-11-08
owns:
  - kingdoms/src/client/java/com/jaymoskingdoms/client/JournalScreen.java
  - kingdoms/src/main/resources/assets/jaymoskingdoms/lang/parts/journal.json
  - kingdoms/src/main/resources/assets/jaymoskingdoms/textures/gui/journal/
  - kingdoms/art/journal/
  - changes/kingdoms/journal-refresh.md
---
# Traveller's journal refresh

**Task:** Bring the traveller's journal screen in line with the pack's current UI while keeping its quests/orders/promises/captains, map/roads and reputation content unchanged.

## Plan
- Compare the journal with SkillScreen, ClassScreen, LifeStoryScreen and Ui/UiStyle.
- Refresh the frame, custom tabs, content wells, hover states and responsive layout; keep all journal content and actions.
- Add only original journal GUI art with editable Blockbench sources and inspected renders.
- Build Kingdoms, run PR checks, open the PR and hand the task to review.

## Notes
- Original art only; no outside assets.
- JournalClient and Journal gameplay/data code stay unchanged.

## Done
Work in progress; not tested in game.
