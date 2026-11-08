---
status: in-review
task: fishing-fight-track-a
branch: claude/fishing-fight-track-a
with: fishing-realism-tideborn (#1132, stacked on it)
pr: 1133
mods: Powers
versions: 0.21.62
started: 2026-11-08
owns:
  - src/main/java/com/jaymospowers/fishing/FishBite.java
  - src/main/java/com/jaymospowers/fishing/FishCast.java
  - src/main/java/com/jaymospowers/fishing/FishRun.java
  - src/main/java/com/jaymospowers/fishing/mixin/FishingRodItemMixin.java
  - src/client/java/com/jaymospowers/client/fishing/
  - src/main/resources/assets/jaymospowers/lang/parts/fishing_fight.json
---
# Fishing Track A: the fight

Nibbles, then a strike window. Also: a charged cast, drag (1-5 on the rod), fish runs and jumps, the rod bending with tension, a creaking line, scroll to reel, and fish spook while you wade near the hook.

**Done:** built, PR checks pass, not played.
**Left:** rebase onto #1132's bite rewrite once it's pushed. Their `FishBites.tick` will call `FishingReal.fishAtHook` instead of `startFight`, and the `FishBite.tick` check goes before their `FishBites` calls in the mixin.
