---
status: done
task: 
branch: claude/nervous-kapitsa-f030d7
with: Claude (side session of the coordinator thread)
pr: 2064
mods: kingdoms
versions: 
started: 2026-11-23
owns:
  - kingdoms/src/main/java/com/jaymoskingdoms/LifeAims.java
  - kingdoms/src/client/java/com/jaymoskingdoms/client/AimPageClient.java
  - kingdoms/src/client/java/com/jaymoskingdoms/client/ScrapScreen.java
  - kingdoms/src/main/resources/assets/jaymoskingdoms/lang/parts/aim-scrap.json
---
# Aim page as a torn scrap

- Task: Andy's "starting journal entry should resemble a scrap of paper ... only ever a book if you have a start within a town".
- Built: the life aim page (LifeAims.Page -> AimPageClient) opens as ScrapScreen (own texture textures/gui/journal/scrap.png) on every road but CITY, VILLAGE, SWORN (LifeAims.BOOK_ROADS), which keep the book page. Both headed "Who You Are" + "You are <name>, a <class> of <heritage> heritage.", then the aim.
- Not an item (Andy removed the aim book from the pack 2026-11-19, PR #1833). Offered as a follow-up.
- Files owned: LifeAims.java, client/AimPageClient.java, client/ScrapScreen.java, lang/parts/aim-scrap.json, changes/kingdoms/claude-nervous-kapitsa-f030d7.md.
- Not played; built by CI.

## Notes
- 2026-11-23 21:53 PR #2064 is merged into master; moved to Agents/Done.
