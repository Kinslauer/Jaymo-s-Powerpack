---
status: done
task: Portable Mods picks, patch 1: the rest of The Aether II
branch: claude/project-thread-vb3yg2
with: Claude (cloud thread "Port picked pieces from other mods")
pr: 1491
mods: powers
versions: 
started: 2026-11-13
owns:
  - src/client/java/com/jaymospowers/client/aether/mob/
  - src/client/java/com/jaymospowers/client/aether/dungeon/
  - src/client/java/com/jaymospowers/client/aether/anim/
  - src/main/java/com/jaymospowers/aether/mob/Moa.java
  - src/main/java/com/jaymospowers/aether/mob/CloudSkiff.java
  - src/main/java/com/jaymospowers/aether/mob/AetherAnimals.java
  - tools/gen_aether2_skins.py
  - tools/gen_aether_animals.py
---
# Aether II: the rest

**Task:** Andy ticked all 35 rows of the Portable Mods picks. This is patch 1 of 7: the seven Aether II rows (baby models,
layers, saddlebags, cloud skiff, mob behaviour, sentry golem idles, world pieces).

## Plan
- Baby models + animations (moa, kirrid x3, burrukai, taegore), skins painted
- Layers: glows (insects, slider, sentries, golem), aerbunny collar, moa keratin colours
- Moa saddlebag (18 slots, sneak-click)
- Cloud skiff: flies, only the just (Karma.just) may ride
- Sentry golem look-around / check-self idles
- Mob behaviour and world: compare with theirs, take what is better

## Notes
- Cloud skiff placed in Lore.md as a (new) paragraph after "The skiffs".

## Done
- PR #1491 up 2026-11-14: young, glows/keratin/collar, saddlebag, golem fidgets, cloud skiff, zephyr campfire fear, slider grudge. Compiles, 38 models bake; not played.
- World pieces not built: ~420 Aether II block ids we lack; Andy asked which groups.
- Merged 2026-11-13 (PR #1491). Lore paragraphs approved by Andy.
