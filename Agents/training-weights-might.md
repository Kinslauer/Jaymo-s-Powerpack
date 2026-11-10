---
status: running
task: training-weights-might
branch: claude/training-weights-might
pr:
mods: powers, kingdoms
versions:
started: 2026-11-09
owns:
  - src/main/java/com/jaymospowers/core/Meditate.java
  - src/main/java/com/jaymospowers/core/Training.java
  - src/main/java/com/jaymospowers/power/zfighter/ZFighterItems.java
  - kingdoms/src/main/java/com/jaymoskingdoms/Stats.java
  - changes/powers/training-weights-might.md
  - changes/kingdoms/training-weights-might.md
---
# Training weights build Might for everyone, and meditating in them is shadowboxing

**Task:** Wearing training weights turns the Meditate key into shadowboxing for every player; shadowboxing in them builds Kingdoms' Might (full rate for Z Fighters/Gravity, 1/10th for others, about one point an hour). Two linked PRs: Powers half + Kingdoms half.

## Plan
- Kingdoms: add Stats.giveMight() to hand Might points directly (respecting tier cap)
- Powers: Training.wearingWeights() checks both light and heavy ankle weights
- Powers: Meditate.kind() lets anyone in weights shadowbox, not just ZF/Gravity
- Powers: Meditate.tick() awards Might while shadowboxing in weights via Kingdoms reflection
- Changes notes for both mods

## Notes
- Cross-mod reach uses reflection like KitchenCompat (Kingdoms needs no Powers jar, Powers needs no Kingdoms jar)

## Done
What a player will see, and what wasn't tested.
