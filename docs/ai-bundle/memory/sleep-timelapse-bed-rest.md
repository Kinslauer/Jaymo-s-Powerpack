---
name: sleep-timelapse-bed-rest
description: Sleeping Overhaul 2 (Forge only) remade in Kingdoms as Timelapse + BedRest; PR #156 (pack 0.10.37 / Kingdoms 0.7.5)
metadata:
  type: project
  modified: 2026-10-14T09:17:53.173Z
---
2026-10-14 Andy asked for Sleeping Overhaul 2 (modrinth sleeping-overhaul-2). It is Forge/NeoForge only, up to 1.21.1 (LGPL-3.0), so Andy chose "Remake it" on a decision card.
Built in Kingdoms: `Timelapse` (Andy chose "whole world": server tick rate 100 + overworld clock rate 6 via ServerClockManager.setRate, so villagers visibly work; drag the mouse in bed to look around while enough are deep asleep, stops at the wake_up_from_sleep marker, hands the skipped hours to `Nights.pass`) and `BedRest` (use a bed by day/blood moon/sneaking = rest awake, not counted as a sleeper, immune to poison/wither, Sleep button at night). Mixins live in mixin/sleep and client/mixin/sleep.
PR #156 MERGED 2026-10-14 14:56Z as pack 0.10.37 / Kingdoms 0.7.5 (master 0.10.34; TaCZ #158 = 0.10.35, sailing #155 = 0.10.36 / Kingdoms 0.7.4). Not yet tested in game. Related: [[villager-days]].
