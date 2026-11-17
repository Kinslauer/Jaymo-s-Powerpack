# Plan: hands-on cooking picks (2026-11-17)

Source: the vault spec `Agents/hands-on-cooking-realtime-SPEC.md` (hermes-b's research on Hands-On Delight; ideas only).
Its header says MC 1.21.4 / Java 21: wrong, the Kitchen is 26.3 Fabric on JDK 25. Pick page:
https://claude.ai/artifact/GCdmEEZx1zdiumU4T69KLz (db doc `picks/andy`). Andy ticked 18 of 19 rows.

Not picked: **Each player cooks their own food** (`hc-q3-shared`). Andy's note on it: "players should be able to help
each other finish stirring or flipping or overall cook together." So a station holds **one shared dish**: anyone with
the right tool can step in and stir, flip, turn or fan it, and the dish is judged once, for the pot, with what everyone
did. The spec's per-player sessions (R11) become one session per station that several players can join.

## Patches (Kitchen only, a commit per patch)
1. **At the block**: `hc-base` (no screen, one shared session per station that others join), `hc-walkaway` (judged as
   left once the last cook is 8 blocks off or logged out), `hc-same` (same judging), `hc-classic` (server config falls back
   to the screens), `hc-q2-tools` (reuse Kitchen's knife, spoon, tongs; add fan, spit and peel only if missing).
2. **Board and pot**: `hc-chop` with `hc-q6-chop` (left click chops), `hc-stir` with `hc-q7-stir` (circle the pointer round
   the pot's centre in the world), `hc-prep` (pour, knead, grind, sprinkle and roll by hand).
3. **Fire**: `hc-roast` (turn the spit), `hc-grill` (tongs on the campfire, peel in the oven), `hc-sear` (marker over the
   furnace), `hc-smoke` (fan the smoker).
4. **No screens left**: `hc-sequence` (step recipes done at the block, each step with its tool), `hc-recipepick` (the
   station reads the ingredients put on it; sneak and scroll picks between dishes that match).
5. **New work at the board and new stations**: `hc-sandwich` (stack bread and fillings on the board), `hc-newstations`
   (keg brewing, kettle tea, sushi mat, all by hand).

## As built
(filled in per patch)
