---
status: in-review
task: drill-and-talk-fixes
branch: claude/sleepy-wozniak-h42ef1
with: cloud Claude
pr: 1974
mods: kingdoms, powers
versions: 
started: 2026-11-21
owns:
  - kingdoms/src/main/java/com/jaymoskingdoms/forms/GuardDrills.java
  - kingdoms/src/client/java/com/jaymoskingdoms/client/DialogueCinematic.java
  - kingdoms/src/client/java/com/jaymoskingdoms/client/SpeechBubbles.java
  - kingdoms/src/client/java/com/jaymoskingdoms/client/mixin/mca/McaDialogueBoxMixin.java
  - kingdoms/src/client/java/com/jaymoskingdoms/client/mixin/mca/McaButtonColumnMixin.java
  - src/main/java/com/jaymospowers/core/BlastGore.java
  - kingdoms/src/client/java/com/jaymoskingdoms/client/mixin/tweaks/FilmHidesViewerMixin.java
---
# Drill and talk fixes (Andy's clip, 2026-11-21)

**Task:** fix what Andy's clip showed: the talk window covers the villager's words; a drill starts before you can draw;
a spar turned into a brawl with bodies burst by a sword.

## Plan
- Talk box on the left, answers on the right after the line (camera unchanged, Andy: "keep the framing").
- Spar: force a draw, first blow waits for a weapon; a short grace after a spar ends so a late swing is no crime.
- BlastGore: swords and axes are never crushing blows.

## Notes
- Unconfirmed: what started the brawl (asked Andy whether he hit anyone but Amanda, or after the spar ended).

## Done
- Both mods build; nothing played in game.
- Added after Andy's go-ahead: spar leads to clear ground (no one within 6 blocks); talk camera hides the player on the villager's lines, side-on shots on the player's, eased between shots (Andy: "let me see that, if we dont like it, you take it out").
- PR #1974. Both mods build; not played.
