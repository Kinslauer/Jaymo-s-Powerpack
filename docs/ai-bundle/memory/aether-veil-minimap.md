---
name: aether-veil-minimap
description: Aether hidden from Overworld sky (AetherVeil, 0.20.60) still reported by Jung; Xaero minimap band fix PR #1040 merged (Powers 0.21.2)
metadata:
  type: project
  modified: 2026-11-05T18:09:17.781Z
---
2026-11-05 18:20Z: Andy "players can still look up and see the aether" + "the minimap shows the aether instead of the overworld".
- Minimap: PR #1040 (Powers 0.21.2 after master merge) wraps Xaero MinimapWriter getMaxY/getMinY to the player's band (AetherVeil.mapTop/mapBottom). MERGED 2026-11-05 18:15Z, not played; Jung to check the new release.
- Veil (client/aether/AetherVeil + SodiumVeil, 0.20.60) checked against CustomChunks 1.0.1 / Sodium / 26.3 bytecode, no bug found. 0.21.2 logs "Aether veil: band floors ..." once per world. Asked Andy: below clouds? shaders (Iris + Complementary default)? F3 screenshot or latest.log.
**How to apply:** if Andy sends the log, read the "Aether veil:" line first (no layout = client sync issue; veil off = Aether floor under cloudTop).
- Andy 18:10Z: it was Jung (own singleplayer instance), below clouds, shaders on. Iris shadow pass checked: also wrapped. Asked for Jung's Powers version (needs >= 0.20.60).
