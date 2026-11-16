---
status: in-review
task: dialogue-letterbox-pov
branch: claude/dialogue-letterbox-pov
with:
pr: 
mods: kingdoms
versions: 
started: 2026-11-16
owns:
  - kingdoms/src/client/java/com/jaymoskingdoms/client/DialogueCinematic.java
  - kingdoms/src/main/java/com/jaymoskingdoms/DialogueCue.java
  - kingdoms/src/main/java/com/jaymoskingdoms/McaDialogueMixin.java
  - kingdoms/src/main/java/com/jaymoskingdoms/McaBridge.java
  - kingdoms/src/main/java/com/jaymoskingdoms/JaymosKingdoms.java
  - kingdoms/src/client/java/com/jaymoskingdoms/client/JaymosKingdomsClient.java
  - kingdoms/src/client/java/com/jaymoskingdoms/client/mixin/tweaks/HandshakeCameraMixin.java
---
# Letterbox cutscene + POV swap on NPC-player dialogue

**Task:** Tiv wants dialogue with an NPC to open a letterbox cutscene (black bars, HUD cleared) and swap POV/perspective between the player's character and the villager every time the speaker changes.

**Plan**
- New `DialogueCue` server->client payload (`jaymoskingdoms:dialogue_cue`) carrying the talking villager and whose turn it is (PLAYER/VILLAGER).
- New client `DialogueCinematic`: letterbox bars + HUD cleared while a talk screen is open; camera behind the player looking at the villager while the player speaks, at the villager's eyes looking at the player while they speak; ends when the talk screen closes.
- Server sends the cue at each kingdoms-handled dialogue opening and at each answer/villager reply in `McaDialogueMixin.selectAnswer`; client detects talk-screen open/close itself (covers MCA's own openings).
- Extend `HandshakeCameraMixin`'s view chain with `DialogueCinematic.view`.

**Notes**
- Tiv's open questions (camera snap vs orbit, shot hold time) decided: shot holds until the next speaker change; camera is the speaker's perspective looking at the listener.
- `changes/kingdoms/dialogue-letterbox-pov.md` added; no version/MODLOG/codemap edits by hand (codemap regenerated).

**Done**
Not built or played yet.
