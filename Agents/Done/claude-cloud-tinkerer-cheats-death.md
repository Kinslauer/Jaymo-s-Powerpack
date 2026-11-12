---
status: done
task: tinkerer-cheats-death
branch: claude/project-thread-ekfmqj
with: Claude (cloud project thread)
pr: 1336
mods: powers
versions:
started: 2026-11-11
owns:
  - src/main/java/com/jaymospowers/power/zfighter/TinkererEchoes.java
  - src/main/java/com/jaymospowers/power/zfighter/ZFighterAndroid.java
  - src/main/java/com/jaymospowers/power/zfighter/ZFighterAndroidStart.java
  - src/main/resources/assets/jaymospowers/lang/parts/zfighter_tinkerer_echoes.json
---
# The Tinkerer cheats death with his echoes

**Task:** killing the Mad Tinkerer doesn't end him: a chrono echo steps in and carries on; he's in several places at once.

## Plan
- He becomes killable (he was permanently invulnerable). On death, 3 s later an echo of him spawns where he fell with
  the Chrono echo's arrival fx, keeps his tags (leaving time), takes over an operation in progress, says a line.
- Every other living Tinkerer mutters an aside to players near him.
- New talk option: ask about the androids that turned; he deflects (never admits the bug).

## Done
- PR #1336. Tinkerer killable; echo steps in 3 s later where he fell and carries on (operation included); other Tinkerers mutter asides; new deflecting talk about the turned androids.
- Built; not played. Lore.md has a (new) paragraph for Andy.

## Notes
- 2026-11-12 01:45 PR #1336 is merged into master; moved to Agents/Done.
