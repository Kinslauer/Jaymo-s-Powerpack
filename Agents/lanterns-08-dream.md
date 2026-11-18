---
status: in-review
task: lanterns-08-dream
branch: claude/lanterns-08-dream
with: Claude Code (PC)
pr: 1769
mods: powers
versions: 
started: 2026-11-18
owns:
  - src/main/java/com/jaymospowers/core/DreamCall.java (new)
  - src/client/java/com/jaymospowers/client/DreamView.java (new)
  - src/client/java/com/jaymospowers/client/mixin/DreamCameraMixin.java (new)
  - src/client/resources/jaymospowers.client.mixins.json (one line)
  - src/main/java/com/jaymospowers/JaymosPowers.java (init + attachment list lines)
  - src/client/java/com/jaymospowers/client/JaymosPowersClient.java (init line)
  - src/main/resources/assets/jaymospowers/lang/parts/dream_call.json
  - changes/powers/lanterns-08-dream.md
---
# lanterns-08-dream

**Task:** Lanterns patch 8: called in a dream; a peaceful dream wakes your power next morning, a nightmare lived through wakes it corrupted.

## Notes
- Andy (2026-11-18): dream arena in place, not a new dimension.
- Lore: "Called in a dream" (new) paragraph after "How a power comes".

## Done
A powerless sleeper may be called: the view tilts up, a dream wakes their power next morning, a nightmare lived through (blind, phantoms, never lethal) wakes it corrupted. Compiled on the PC, CI passes; not played.
