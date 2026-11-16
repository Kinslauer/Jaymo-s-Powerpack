---
status: stalled
task: faith-page-champion
branch: claude/faith-page-champion
with:
pr:
mods: kingdoms
versions:
started: 2026-11-16
owns:
  - kingdoms/src/client/java/com/jaymoskingdoms/client/LifeStoryScreen.java
  - kingdoms/src/client/java/com/jaymoskingdoms/client/KiGhost.java
  - changes/kingdoms/faith-page-champion.md
---
# Faith page: show the faith's champion, sigil behind it

**Task:** on the life story's faith page ("Who did you grow up praying to?"), the left preview shows the
selected faith's champion instead of the player figure, changing as the selection changes; The Truth (no
champion) is drawn as a bright yellow-white ki ghost; the faith's sigil is drawn large and semi-transparent
behind the figure and stays behind the preview on every later creator page.

**Plan**
- LifeStoryScreen.figure(): on the Question.FAITH page draw the answered faith's champion (PatronChampion via
  ChampionRenderer/ChampionModel, existing textures) or the Truth as a KiGhost-style bright yellow-white ghost;
  draw the picked faith's shrine glyph large and semi-transparent behind the figure on this and every later
  page.
- KiGhost.draw: overload with colours (road.fate keeps the original call).
- Change note changes/kingdoms/faith-page-champion.md.

**Notes**
- Champion textures and shrine glyphs for all eight patrons already exist; no new art.
- Built; not played in game.

**Done**
- (none yet)

## Notes
- 2026-11-16 05:51 No sign of work for 30 min: marked stalled (whoever ran it stopped).
