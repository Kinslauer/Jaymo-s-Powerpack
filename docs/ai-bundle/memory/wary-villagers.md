---
name: wary-villagers
description: PR #1034 (Kingdoms 0.7.597, merged 2026-11-05): Introductions.wary villagers stay cold from the first word; speech bubbles show through walls
metadata:
  type: project
  modified: 2026-11-05T17:40:08.713Z
---
2026-11-05 Andy: "this villager talked to me then acted this way when i introduced myself, the interactions should be consistent".
PR #1034 MERGED 2026-11-05 ~17:41Z as Kingdoms 0.7.597. Built, not played.
- Rule: anything a villager says/does toward a player must respect `Introductions.wary(player, villager)` (minds blood via Prejudice, realm rep < WELCOME with < 20 hearts, or cold Recollections). New friendly villager-initiated features should check it.
- MCA menu "root" on a wary villager -> `McaBridge.waryOpening` (same line as name/hand refusal). `Introductions.waryLine(villager, player)` is stable per villager+player+day.
- `Approaches.pick`: wary adults only ACCUSE / QUEST_CHECK.
- MCA Chat/Joke replies left as MCA's (that's how hearts are earned to prove yourself).
- SpeechBubbles: see-through faint pass (like name tags) + identical visible line isn't restacked.
Related: [[villager-approaches]], [[villager-memory]], [[shared-history-talk]].
