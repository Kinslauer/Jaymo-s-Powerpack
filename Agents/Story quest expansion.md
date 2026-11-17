---
status: stalled
task: Embellish the 19 tales and add 17 new ones, with a real medieval feel (Andy's 48 picks, "Build!")
branch: claude/project-thread-kk4cf1
with: cloud
pr: 
mods: Kingdoms
versions: 
started: 2026-11-17
owns:
  - kingdoms/src/main/java/com/jaymoskingdoms/TaleWays.java
  - kingdoms/src/main/java/com/jaymoskingdoms/Tales.java
  - kingdoms/src/main/java/com/jaymoskingdoms/Tale.java
  - kingdoms/src/main/java/com/jaymoskingdoms/TaleCast.java
  - kingdoms/src/main/java/com/jaymoskingdoms/KeepingCells.java
  - kingdoms/src/main/resources/data/jaymoskingdoms/tales/
  - tools/tale_check.py
  - docs/StoryQuests-Plan.md
---
# Story quest expansion

**Task:** Andy (2026-11-17): "embellish the existing and add more story quests, use inspiration from witcher 3 baldurs
gate and blood of the dawnwalker and kingdom come deliverance for a real medieval feel". Pick page
https://claude.ai/artifact/4iKFTPa4trA4LV6DDrLv1s : all 48 rows picked, "Build!".

## Plan
- 12 engine rows (deadlines, hours, who-you-are conditions, tries, naming, beast prep, companion voices, aftermath,
  unreadable letters, lies, notices, arcs): `TaleWays.java`, format in `docs/StoryQuests-Plan.md` section 7.
- One addition to each of the 19 tales; 17 new tales (The Black Candle is three linked files).
- Lore placements as `(new)` paragraphs in `Lore.md`.

## Notes
- Inspiration only: our own stories, names and people.
- Small pieces added for rows that needed them: a throw of the dice (`"how": "dice"`), a black candle set in the
  tavern window (`"candle": true`), a wolfsbane item, cast creatures that lie still (`"lying": true`).

## Done
(to fill when the PR is up)
- 2026-11-17 23:56 No sign of work for 30 min: marked stalled (whoever ran it stopped).
