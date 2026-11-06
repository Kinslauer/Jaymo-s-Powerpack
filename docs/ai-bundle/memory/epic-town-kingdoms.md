---
name: epic-town-kingdoms
description: PR #1033 (Kingdoms 0.7.598, merged 2026-11-05): kingdoms only in Epic towns, Grim castles fief-only, villages ally nearest kingdom
metadata:
  type: project
  modified: 2026-11-05T17:42:27.102Z
---
2026-11-05 Andy (Winterhold citadel screenshot): Grim structures "should only spawn as fiefs or smaller villages allied to larger kingdoms that are always from epic villages".
PR #1033 MERGED ~17:45Z as Kingdoms 0.7.598. Built, not played.
- Rule: only `minecraft:village_*` (Epic Structures redraws them; `Seats.epic`) found kingdoms. Other mods' villages are villages; new villages ally nearest kingdom within 1000 (`Villages.ally`).
- GrandSeats has no Grim castles anymore (fief holds only, GrimSites.HOLDS); snowy uses ATi woodland keep.
- Kingdoms from nothing (story capital, second town, whaler shore) go through `KingdomEvents.foundTown` -> `EpicTowns` places a land-matching village (/place-style, registered in chunk refs), castle only as fallback.
- Existing kingdoms keep old seats; new map shows it.
