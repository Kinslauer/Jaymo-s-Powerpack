---
status: claimed
for: hermes
from: tiv (Discord, Questions thread, 2026-11-16)
asked: 2026-11-16
thread: 1569189294512537671
claimed_by: hermes-b
pr:
suggested_owns: [kingdoms/src/client/java/com/jaymoskingdoms/client/HandshakeCinematic.java, kingdoms/src/main/java/com/jaymoskingdoms/mixin/mca/McaDialogueMixin.java, kingdoms/src/main/java/com/jaymoskingdoms/Questioning.java]
---

# Letterbox cutscene + POV swap on NPC-player dialogue

Tiv, in the Questions thread: "I want to change dialogue between NPCs and players where there's a bit of a letterbox cutscene anytime y'all start the interaction and it changes POV or perspective from your character to the villager every time it changes who's saying what."

## What's wanted

- When a player starts talking to an NPC/villager, a letterbox cutscene starts (black bars, HUD clears, camera cuts).
- Every time the speaker changes (player ↔ villager), the POV/perspective swaps — from the player's character to the villager and back.
- Applies to dialogue between NPCs and players.

## What exists

- `HandshakeCinematic` — an existing short-film system: black bars slide in, HUD clears, camera cuts through shots. A model for how a letterbox + camera cutscene is done in this pack.
- `HandshakeCameraMixin` — moves and turns the camera during a handshake cinematic.
- `McaDialogueMixin` — mixes into MCA's dialogue for chatting with newcomers/immigrants.
- `Questioning.java` — may be the NPC questioning/dialogue system.

## Notes

- This is mod work (code + possibly assets), routed to hermes (builders). Not a model, art, or writing task itself.
- "Changes POV every time the speaker changes" needs a decision: does the camera snap to the villager's face, or orbit, or go third-person on the villager? And how long does each speaker's shot hold before cutting back?
- Ask Tiv whether this should wrap all NPC dialogue or only certain conversations (e.g. masters, quest-givers, court wizards).
