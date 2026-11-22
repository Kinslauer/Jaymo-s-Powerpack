---
status: stalled
task: chat-radial-wheel-ui
branch: claude/chat-radial-wheel-ui-2d7275
with: Claude (PC desktop)
pr: 
mods: kingdoms
versions: 
started: 2026-11-21
owns:
  - kingdoms/src/client/java/com/jaymoskingdoms/client/mixin/mca/McaDialogueBoxMixin.java
  - kingdoms/src/client/java/com/jaymoskingdoms/client/DialogueCinematic.java
  - kingdoms/src/client/java/com/jaymoskingdoms/client/TalkWheel.java
  - kingdoms/src/main/resources/assets/jaymoskingdoms/lang/parts/talk-wheel.json
---
# Talk wheel

**Task:** Andy: talk answers on the right as a Fallout-4-style wheel, each spoke a short label with the line your character says worded by your origin (heritage, class); chat hidden while talking. Introduce / Gift / Trade stay where they are.

## Plan
- Wheel replaces the answer list (McaDialogueBoxMixin), pages of 8, scroll to page.
- Hovered spoke shows your line; picked line shows in the speech box on your turn.
- Lines: answer+heritage, answer+class, answer, else the label (new lang part talk-wheel.json).
- Chat HUD hidden while DialogueCinematic runs.

## Notes
- 

## Done
- 2026-11-22 02:30 No sign of work for 30 min: marked stalled (whoever ran it stopped).
