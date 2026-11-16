---
status: in-review
for: hermes
from: tiv (Discord, Questions thread, 2026-11-16)
asked: 2026-11-16
thread: 1569189294512537671
claimed_by: hermes-b
pr: 1649
suggested_owns: [kingdoms/src/client/java/com/jaymoskingdoms/client/HandshakeCinematic.java, kingdoms/src/main/java/com/jaymoskingdoms/mixin/mca/McaDialogueMixin.java, kingdoms/src/main/java/com/jaymoskingdoms/Questioning.java]
writer: hermes-writer
text_pass: done
---

# Letterbox cutscene + POV swap on NPC-player dialogue

Tiv, in the Questions thread: "I want to change dialogue between NPCs and players where there's a bit of a letterbox cutscene anytime y'all start the interaction and it changes POV or perspective from your character to the villager every time it changes who's saying what."

## What's wanted

- When a player starts talking to an NPC/villager, a letterbox cutscene starts (black bars, HUD clears, camera cuts).
- Every time the speaker changes (player ↔ villager), the POV/perspective swaps — from the player's character to the villager and back.
- Applies to dialogue between NPCs and players.

Added (same thread): Witcher 3 / Baldur's Gate 3 style conversation system — a list of dialogue options, stemming from a Fallout 4 style wheel with sections based off temperament. The camera cuts back and forth, breaking the 180 most of the time.

- A Fallout 4 style dialogue wheel: options laid out in sections, each section tied to a temperament (kindhearted, cheerful, devout, dutiful, brave, proud, greedy, suspicious, timid, hotheaded, spiteful, gloomy).
- The camera cuts back and forth between speakers, breaking the 180-degree rule most of the time.

## What exists

- `HandshakeCinematic` — an existing short-film system: black bars slide in, HUD clears, camera cuts through shots. A model for how a letterbox + camera cutscene is done in this pack.
- `HandshakeCameraMixin` — moves and turns the camera during a handshake cinematic.
- `McaDialogueMixin` — mixes into MCA's dialogue for chatting with newcomers/immigrants.
- `Questioning.java` — may be the NPC questioning/dialogue system.
- Temperaments already exist: `PlayerTemperaments` sets a player's temperament from their life story; `Temperaments` has the twelve temperaments with colours and hover text. Villagers and mobs also carry temperaments.

## Notes

- This is mod work (code + possibly assets), routed to hermes (builders). Not a model, art, or writing task itself.
- The dialogue wheel needs a UI design: wheel sections by temperament, how options are selected, how the list stems from the wheel.
- Camera work: back-and-forth cuts, breaking the 180 — needs specific shot/reverse-shot framing per speaker.

Settled by Tiv: the new system replaces MCA's dialogue UI completely. Design philosophy: extravagant emergent gameplay with very little hand-holding — whatever information is there should be more like a hint and less like an answer. Learning everything MCA gives you outright is counter-intuitive to that vision.
- 2026-11-16 12:47 hermes-b's last run stopped before finishing; handed back as open so it can carry on.
- 2026-11-16 12:58 Text pass: removed code-internals line from changes/kingdoms/dialogue-letterbox-pov.md (mentioned payload id, DialogueCue and HandshakeCameraMixin class names; lines 2-3 already describe what the player sees: letterbox bars, HUD clear, camera/POV swap per speaker)
