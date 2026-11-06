---
name: talk-out-of-play
description: PR #1027 (Powers 0.20.109 / Kingdoms 0.7.591, 2026-11-05): villagers don't approach players who are down/carried/waking/reborning/Chrono-held/tethered/filmed; TalkScreen waits for open screens
metadata:
  type: project
  modified: 2026-11-05T15:43:10.225Z
---
2026-11-05 Andy: "make sure all the new features work wth all the old ... chat interactions and sevtech style world generating".
PR #1027 MERGED 2026-11-05 15:45Z; master then Powers 0.20.109 / Kingdoms 0.7.591 / Kitchen 0.5.76. Not played.
- Powers `core/OutOfPlay.is(Entity)` = downed/out cold, carried, Mortality.waking, REBORNING, ChronoCheatDeath.held. Kingdoms `PowersCompat.outOfPlay` (reflection) + `Approaches.unreachable` (also Shackles.TETHER, Captains.filming). Used by Approaches and Handshakes.
- TalkScreen used to setScreen unconditionally (could replace the death screen in Chrono's window); now waits for screen()==null, 20 s.
- GroundChanges.tell: veins grown on a tier step get ore pebbles (blocks looked up by id jaymospowers:<ore>_pebble / _nugget_pebble).
- Ore gating finding: vanilla ores exist from Hearth (GroundChanges only ADDS veins), so pebbles can't leak a higher tier.
**How to apply:** any new "NPC walks up / opens a screen on the player" feature should check `Approaches.unreachable` and not setScreen over an open screen. See [[ore-pebbles]], [[villager-memory]].
