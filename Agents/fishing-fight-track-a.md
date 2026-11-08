---
status: in-review
task: fishing-fight-track-a
branch: claude/fishing-fight-track-a
with: fishing-realism-tideborn (#1132, stacked on it)
pr: 1133
mods: Powers
versions: 0.21.66
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
**Left:** merge the new #1132 push (Powers 0.21.64, Kitchen 0.5.92) and master again, build, push. Merge order: #1132, #1136, #1133.
