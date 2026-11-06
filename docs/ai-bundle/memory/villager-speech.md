---
name: villager-speech
description: Kingdoms 0.5.17 (PR #43 merged 2026-10-12 06:51Z): villager talk (MCA + Kingdoms) floats over the speaker's head instead of chat
metadata:
  type: project
  modified: 2026-10-12T06:30:29.266Z
---
2026-10-12 Andy: "conversations between villagers should pop up in world as text, not in the chat".
PR #43 (branch claude/villager-speech-bubbles-3npzvn, Kingdoms 0.5.17): `Speech.say(level, entity, words, color, range)`
sends a `jaymoskingdoms:said` payload; client `SpeechBubbles` draws fading lines via an EntityRenderDispatcher mixin.
MCA's chat (ClientHandlerImpl.handleVillagerMessage) is redirected into bubbles by a client mixin gated on MCA; it
still goes to chat while MCA's InteractScreen is open. Any new "NPC says X" line should use Speech.say, not chat.
Playtest note: MCA villagers summoned with NoAI render invisible; summon a vanilla villager (MCA converts it) instead.
Test jar/screens in /mnt/project-files/villager-speech/. See [[kingdoms-workers-mca]], [[mca-in-pack]].
