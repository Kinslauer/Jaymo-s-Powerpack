---
status: done
task: band-sleep
branch: fix/band-sleep
with: codex (gpt-6-astra) builds, PC Claude Code coordinates and reviews
pr: 2246
mods: powers
versions:
started: 2026-11-26
owns:
  - src/main/java/com/jaymospowers/compat/BandSleep.java
  - src/main/java/com/jaymospowers/mixin/BandSleep*Mixin.java
  - src/main/java/com/jaymospowers/aether/mob/AetherAnimals.java (lizardFromLeaves only)
  - changes/powers/band-sleep.md
---
# Bands sleep until a player is near

**Task:** Andy: skyroot lizards only spawn in the Aether, and the Aether band stays unloaded until you approach (spark profile of the Deck, 2026-11-26).

## Plan
- BandSleep: a band other than the Overworld's sleeps with no player within 144 blocks (entity ticks, random ticks, block entity ticks).
- Lizards from leaves only in the Aether realm.

## Notes
- Incendium force-loads chunk 0,0 (forceload add 0 0), keeping the spawn column awake.
- 58 leaf drops above spawn cleared by rcon at 1:25 PM.

## Done
Merged as PR #2246 at 1:57 PM on Andy's "merge" (CI builds passed; the "Pack mods start together" boot check was still running). In a stacked world the bands above and below the Overworld stop ticking entities, random ticks and block entities with no player within 144 blocks; lizards only from Aether leaves. Not tested in game: needs a pbdr, then check that the Aether above spawn stays still while a player is on the ground.
