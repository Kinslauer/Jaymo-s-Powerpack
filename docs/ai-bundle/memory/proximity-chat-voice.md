---
name: proximity-chat-voice
description: Proximity text chat (Kingdoms ProximityChat) + Simple Voice Chat/Voice Physics/Voice Changer in pack, VoiceMasks; PR #652 merged 2026-10-29 02:4xZ (Kingdoms 0.7.330 / pack 0.17.157)
metadata:
  type: project
  modified: 2026-10-29T01:46:09.257Z
---
Andy 2026-10-29: "I also want to add proximity chat" (links: Voice Physics, Proximity Text Chat, Simple Voice Voice Changer).
- PR #652 (merged 2026-10-29, Kingdoms 0.7.330 / pack 0.17.157): Kingdoms `ProximityChat` cancels vanilla chat broadcast (Fabric ALLOW_CHAT_MESSAGE): talk 24/40 blocks with dropped words, sneak = whisper 4/8, `!` shout 64/96, `*` act, walls x1.6, downed whisper only, Stranger names, beasts growl, bubbles, `/ooc` global. MCA chat AI unaffected (its mixin is before the broadcast).
- Pack mods: Simple Voice Chat (CF 416089), Voice Physics (CF 1712556, curse.maven, MIT), Simple Voice Voice Changer 1.2.5 (CF 1658503 file 9030770; CC BY-NC-SA). Proximity Text Chat is ARR: idea only.
- `VoiceMasks` -> client `VoiceChangerCompat` (voice changer API override): werewolf/Great Ape forced; goblin disguise/full mask follow player's J toggle.
- Deck server needs a playit **UDP** tunnel to 24454 + `voice_host` in config/voicechat/voicechat-server.properties (deck/README.md). Tunnel made 2026-10-29: voice_host=malls-harrington.tun.ply.gg:44082 ; voice chat confirmed working on the Deck by Andy 2026-10-29 04:17Z.
**How to apply:** tuning constants at top of ProximityChat / VoiceChangerCompat. See [[character-names]], [[vampires-werewolves]].
