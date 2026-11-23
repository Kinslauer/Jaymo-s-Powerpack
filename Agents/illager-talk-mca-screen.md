---
status: done
task: illager-talk-mca-screen
branch: illager-talk-mca-screen
with: Claude Code (Opus 5.5), side session of local_2e1ecf8a
pr: 2036
mods: kingdoms
versions:
started: 2026-11-23
owns:
  - kingdoms/src/main/java/com/jaymoskingdoms/IllagerTalk.java
  - kingdoms/src/main/java/com/jaymoskingdoms/IllagerCard.java
  - kingdoms/src/main/java/com/jaymoskingdoms/Menus.java
  - kingdoms/src/client/java/com/jaymoskingdoms/client/IllagerTalkScreen.java
  - kingdoms/src/client/java/com/jaymoskingdoms/client/DialogueCinematic.java
  - kingdoms/src/client/java/com/jaymoskingdoms/client/SpeechBubbles.java
  - kingdoms/src/main/resources/assets/jaymoskingdoms/lang/parts/illager-tongue.json
---
# Illager talk looks and works like MCA's

**Task:** Andy (screenshot of a pillager's talk card, "Zarkul · Weary · Wary · Sullen"): "these pillager interactions
should look and work just like MCA".

## Plan
- MCA's real screen can't open for illagers (it needs an MCA villager and MCA's server data), so ours is rebuilt to
  match MCA's interact screen as Kingdoms shapes it: info labels top left, hearts icon, button column on the right in
  the dark purple panel, back arrow, words box low left, letterbox camera (when MCA is loaded).
- Pages as MCA: main (Talk, Interact, Trade), Talk (ask name, chat, what they want, their hold, goodbye), Interact
  (Gift in MCA's gift mode: scroll the hotbar, click to give; Talk Them Round; buy off the posters).
- Hostile parley, trade and Talk Them Round lists show in the column; the screen stays open across picks.

## Notes
-

## Done
PR #2036. Illager talk opens an MCA-style screen (info lines, heart, right column, << back, Talk/Interact/Trade pages, MCA gift mode, stays open across answers, talk camera with MCA). Trade stays a buy/sell list; Talk answers are a column, not the wheel. Built by CI only; not played.
