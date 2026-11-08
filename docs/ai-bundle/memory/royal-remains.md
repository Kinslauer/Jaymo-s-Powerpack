---
name: royal-remains
description: Dead rulers are fetched, stripped into realm stores, given cleric rites and buried with candle + big mourning; PR #959 MERGED 2026-11-05 00:16Z (Kingdoms 0.7.546)
metadata:
  type: project
  modified: 2026-11-05T00:10:46.982Z
---
2026-11-05 Andy (video: a dead king in diamonds left outside his walls, a player stripped it): royals must never be left dead unless nobody is left in the kingdom; someone should get the body to safety, remove the armour, bury/mourn or bring it to the cleric.
PR #959 (MERGED 00:16Z, Kingdoms 0.7.546, built, not played). New `RoyalRemains`: a guard drops its fight and runs the body home at once (Andy 00:12Z); townsfolk only once no foe is within 8 blocks; anyone within 96 blocks fetches the body, carries it to the cleric (or else the seat), strips it into Storage, the cleric says the rites, then `Bearers.entrustRoyal` buries it (candle on the headstone, up to 8 mourners). With no grave ground it lies in state. In a realm in ruin, villagers still come. Andy's exception: no one left means it stays.
Claude's choices, not Andy's words: armour goes to realm stores; the cleric's rites happen before the burial.
See [[battlefield-dead]], [[town-trades-butcher-healer]].
