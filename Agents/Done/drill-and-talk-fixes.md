---
status: done
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
- 2026-11-22 00:07 PR #1974 is merged into master; moved to Agents/Done.
- #1974 merged 2026-11-22.
- Same session, 2026-11-22: PR #1975 (roll clipping fixed, prone Sit key wall-sits only, staged struggle up the wall, water dive on the Dodge key) merged; PR #1959 finished in five parkour tracks (see Inbox/parkour-animations) and merged at Andy's word. Not played in game.
- Slip: merging the old (already squashed) branch tip into the restarted branch brought #1974's change notes back, so #1975's release logged them again: duplicate entries Powers 0.26.26 and Kingdoms 0.11.55, each version one step extra. Code unaffected; MODLOG edits need Andy's say (PRs may not touch MODLOGs).
- 2026-11-22: Andy said remove the duplicates; Powers 0.26.26 and Kingdoms 0.11.55 entries removed on master (0f505d2). Version numbers left as they are.
